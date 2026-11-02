package com.bloxbean.cardano.client.tools.chainparse;

import com.bloxbean.cardano.client.plutus.spec.CostMdls;
import com.bloxbean.cardano.client.plutus.spec.CostModel;
import com.bloxbean.cardano.client.plutus.spec.Language;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Per-epoch Plutus cost models from Koios {@code epoch_params} (read-only, paged, at most 1 request/s), cached in the
 * run directory. An epoch missing from the cache (the chain moved on) triggers a refetch at most once per minute.
 */
final class CostModels {
    private static final Logger log = LoggerFactory.getLogger(CostModels.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final String koios;
    private final Path cache;
    private final Map<Integer, Map<Language, long[]>> byEpoch = new HashMap<>();
    private final Map<String, byte[]> viewCache = new HashMap<>();
    private long lastRefresh;

    CostModels(String koios, Path cache) throws Exception {
        this.koios = koios;
        this.cache = cache;
        if (Files.exists(cache))
            load(JSON.readTree(cache.toFile()));
        else
            refresh();
    }

    private void refresh() throws Exception {
        lastRefresh = System.currentTimeMillis();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        ArrayNode all = JSON.createArrayNode();
        for (int offset = 0; ; offset += 1000) {
            String url = koios + "/epoch_params?select=epoch_no,cost_models&cost_models=not.is.null&order=epoch_no.asc&offset=" + offset;
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/json").GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 != 2)
                throw new IllegalStateException("Koios " + r.statusCode() + ": " + r.body());
            JsonNode page = JSON.readTree(r.body());
            page.forEach(all::add);
            Thread.sleep(1100);
            if (page.size() < 1000)
                break;
        }
        Files.writeString(cache, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(all));
        load(all);
    }

    private void load(JsonNode rows) {
        for (JsonNode row : rows) {
            JsonNode cm = row.get("cost_models");
            if (cm == null || cm.isNull())
                continue;
            Map<Language, long[]> m = new TreeMap<>();
            put(m, Language.PLUTUS_V1, cm.get("PlutusV1"));
            put(m, Language.PLUTUS_V2, cm.get("PlutusV2"));
            put(m, Language.PLUTUS_V3, cm.get("PlutusV3"));
            byEpoch.put(row.get("epoch_no").asInt(), m);
        }
    }

    private static void put(Map<Language, long[]> m, Language l, JsonNode costs) {
        if (costs == null || costs.isNull())
            return;
        List<Long> values = new ArrayList<>();
        if (costs.isArray()) {
            costs.forEach(v -> values.add(v.asLong()));
        } else { // named form: the ledger orders parameters by name
            Map<String, Long> named = new TreeMap<>();
            costs.properties().forEach(e -> named.put(e.getKey(), e.getValue().asLong()));
            values.addAll(named.values());
        }
        m.put(l, values.stream().mapToLong(Long::longValue).toArray());
    }

    /** @return the cost models of an epoch, or null if unknown */
    synchronized Map<Language, long[]> epoch(int epoch) {
        Map<Language, long[]> m = byEpoch.get(epoch);
        if (m == null && System.currentTimeMillis() - lastRefresh > 60_000) {
            try {
                refresh();
            } catch (Exception e) {
                log.warn("Koios refresh failed: {}", e.toString());
            }
            m = byEpoch.get(epoch);
        }
        return m;
    }

    /** Language views encoding for exactly these languages in an epoch (cached). */
    synchronized byte[] languageViews(int epoch, Map<Language, long[]> models, List<Language> langs) {
        String key = epoch + ":" + langs;
        return viewCache.computeIfAbsent(key, k -> {
            CostMdls mdls = new CostMdls();
            for (Language l : langs)
                mdls.add(new CostModel(l, models.get(l)));
            return mdls.getLanguageViewEncoding();
        });
    }
}
