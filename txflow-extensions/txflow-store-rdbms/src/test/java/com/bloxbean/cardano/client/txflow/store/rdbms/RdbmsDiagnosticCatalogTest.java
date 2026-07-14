package com.bloxbean.cardano.client.txflow.store.rdbms;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RdbmsDiagnosticCatalogTest {
    private static final Pattern DIAGNOSTIC_CODE =
            Pattern.compile("TXFLOW_[A-Z0-9_]+|EVENTS_COMPACTED");
    private static final Pattern CATALOG_ENTRY = Pattern.compile(
            "\"(TXFLOW_[A-Z0-9_]+|EVENTS_COMPACTED)\"\\s*:");

    @Test
    void catalogContainsEveryStableCodeUsedByRdbmsProductionSources() throws Exception {
        Set<String> documented = documentedCodes();
        Set<String> used;
        try (var files = Files.walk(Path.of("src/main/java"))) {
            used = files.filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> {
                        try {
                            return DIAGNOSTIC_CODE.matcher(Files.readString(path)).results()
                                    .map(result -> result.group()).toList().stream();
                        } catch (Exception failure) {
                            throw new RuntimeException(failure);
                        }
                    })
                    .collect(Collectors.toSet());
        }

        assertTrue(documented.containsAll(used),
                () -> "Undocumented RDBMS diagnostic codes: " + used.stream()
                        .filter(code -> !documented.contains(code)).sorted().toList());
    }

    private Set<String> documentedCodes() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/txflow-diagnostic-codes.json")) {
            assertNotNull(stream, "txflow diagnostic catalog");
            String catalog = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            Set<String> documented = new HashSet<>();
            CATALOG_ENTRY.matcher(catalog).results()
                    .map(result -> result.group(1))
                    .forEach(documented::add);
            return documented;
        }
    }
}
