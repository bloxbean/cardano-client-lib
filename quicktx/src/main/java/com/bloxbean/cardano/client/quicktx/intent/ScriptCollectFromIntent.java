package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.RedeemerUtil;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.quicktx.filter.runtime.UtxoFilterStrategy;
import com.bloxbean.cardano.client.quicktx.filter.yaml.UtxoFilterYaml;
import com.bloxbean.cardano.client.quicktx.serialization.PlutusDataYamlUtil;
import com.bloxbean.cardano.client.quicktx.serialization.VariableResolver;
import com.bloxbean.cardano.client.quicktx.utxostrategy.FixedUtxoRefStrategy;
import com.bloxbean.cardano.client.quicktx.utxostrategy.FixedUtxoStrategy;
import com.bloxbean.cardano.client.quicktx.utxostrategy.LazyUtxoStrategy;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.*;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Intention for collecting UTXOs from script addresses with redeemers.
 * This is specific to ScriptTx and supports script UTXO collection with witness data.
 * <p>
 * Maps to various ScriptTx.collectFrom(...) methods:
 * - collectFrom(Utxo, PlutusData, PlutusData)
 * - collectFrom(Utxo, PlutusData)
 * - collectFrom(List&lt;Utxo&gt;, PlutusData, PlutusData)
 * - collectFrom(List&lt;Utxo&gt;, PlutusData)
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScriptCollectFromIntent implements TxInputIntent {

    /**
     * Original UTXOs for runtime use (preserves all UTXO data).
     */
    @JsonIgnore
    private List<Utxo> utxos;

    @JsonIgnore
    private LazyUtxoStrategy lazyUtxoStrategy;

    /**
     * Original redeemer data for runtime use.
     */
    @JsonIgnore
    private PlutusData redeemerData;

    /**
     * Original datum for runtime use.
     */
    @JsonIgnore
    private PlutusData datum;

    @JsonProperty("utxo_refs")
    private List<UtxoRef> utxoRefs;

    @JsonProperty("redeemer_hex")
    private String redeemerHex;

    @JsonProperty("datum_hex")
    private String datumHex;

    /**
     * Structured redeemer format for YAML
     * Supports optional @name annotations and variable resolution.
     */
    @JsonProperty("redeemer")
    private JsonNode redeemerStructured;

    /**
     * Structured datum format for YAML
     * Supports optional @name annotations and variable resolution.
     */
    @JsonProperty("datum")
    private JsonNode datumStructured;

    @JsonProperty("address")
    private String address; // script address for lazy strategies

    @JsonProperty("utxo_filter")
    private JsonNode utxoFilter; // simplified YAML mapping

    // Internal use only - to keep track of spending context utxo <-> redeemer mapping
    @JsonIgnore
    private List<SpendingContext> spendingContexts;

    /**
     * Get UTXO references for serialization.
     * Computed from original UTXOs when serializing.
     */
    @JsonProperty("utxo_refs")
    public List<UtxoRef> getUtxoRefs() {
        if (utxos != null && !utxos.isEmpty()) {
            return utxos.stream()
                    .map(UtxoRef::fromUtxo)
                    .collect(java.util.stream.Collectors.toList());
        }
        return utxoRefs;
    }

    /**
     * Get redeemer hex for serialization.
     * Returns null if we have a runtime redeemer object (will be serialized as structured format instead).
     * Only serializes as hex if redeemerHex was explicitly provided.
     */
    @JsonProperty("redeemer_hex")
    public String getRedeemerHex() {
        // Don't serialize both hex and structured - prefer structured for readability
        if (redeemerData != null) {
            return null;
        }
        return redeemerHex;
    }

    /**
     * Get datum hex for serialization.
     * Returns null if we have a runtime datum object (will be serialized as structured format instead).
     * Only serializes as hex if datumHex was explicitly provided.
     */
    @JsonProperty("datum_hex")
    public String getDatumHex() {
        // Don't serialize both hex and structured - prefer structured for readability
        if (datum != null) {
            return null;
        }
        return datumHex;
    }

    /**
     * Get structured redeemer format for YAML serialization.
     * Precedence: redeemer_hex > runtime object > structured format.
     * Note: @name annotations are NOT preserved (write-only).
     */
    @JsonProperty("redeemer")
    public JsonNode getRedeemerStructured() {
        if (redeemerHex != null && !redeemerHex.isEmpty()) {
            return null; // hex takes precedence
        }
        if (redeemerData != null) {
            return PlutusDataYamlUtil.toYamlNode(redeemerData);
        }
        return redeemerStructured;
    }

    /**
     * Set structured redeemer format for YAML deserialization.
     */
    @JsonProperty("redeemer")
    public void setRedeemerStructured(JsonNode node) {
        this.redeemerStructured = node;
    }

    /**
     * Get structured datum format for YAML serialization.
     * Precedence: datum_hex > runtime object > structured format.
     * Note: @name annotations are NOT preserved (write-only).
     */
    @JsonProperty("datum")
    public JsonNode getDatumStructured() {
        if (datumHex != null && !datumHex.isEmpty()) {
            return null; // hex takes precedence
        }
        if (datum != null) {
            return PlutusDataYamlUtil.toYamlNode(datum);
        }
        return datumStructured;
    }

    /**
     * Set structured datum format for YAML deserialization.
     */
    @JsonProperty("datum")
    public void setDatumStructured(JsonNode node) {
        this.datumStructured = node;
    }

    @Override
    public String getType() {
        return "script_collect_from";
    }


    @Override
    public void validate() {
        TxInputIntent.super.validate();

        // Check runtime objects first
        if (utxos != null && !utxos.isEmpty()) {
            // Validate runtime UTXOs
            for (Utxo utxo : utxos) {
                if (utxo.getTxHash() == null || utxo.getTxHash().isEmpty()) {
                    throw new IllegalStateException("UTXO transaction hash is required");
                }
                if (utxo.getOutputIndex() < 0) {
                    throw new IllegalStateException("UTXO output index must be non-negative");
                }
            }
        } else if (utxoRefs != null && !utxoRefs.isEmpty()) {
            // Validate serialized UTXO references
            for (UtxoRef utxoRef : utxoRefs) {
                if (utxoRef.getTxHash() == null || utxoRef.getTxHash().isEmpty()) {
                    throw new IllegalStateException("UTXO transaction hash is required");
                }
                if (utxoRef.getOutputIndex() == null && !utxoRef.hasPlaceholderOutputIndex()) {
                    throw new IllegalStateException("UTXO output index is required");
                }
                if (utxoRef.getOutputIndex() != null && utxoRef.getOutputIndex() < 0) {
                    throw new IllegalStateException("UTXO output index must be non-negative");
                }

                // Basic validation for tx hash format
                if (utxoRef.getTxHash().length() != 64) {
                    throw new IllegalStateException("Invalid transaction hash format: " + utxoRef.getTxHash());
                }
            }
        } else if (lazyUtxoStrategy == null) {
            // If we have a utxo_filter, that is also acceptable; will be transformed to strategy later
            if (utxoFilter == null) {
                throw new IllegalStateException("UTXOs, UTXO filter, or LazyUtxoStrategy are required for script collection");
            }
        }

        // Validate hex strings if provided
        if (redeemerHex != null && !redeemerHex.isEmpty()) {
            try {
                HexUtil.decodeHexString(redeemerHex);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid redeemer hex format: " + redeemerHex);
            }
        }

        if (datumHex != null && !datumHex.isEmpty()) {
            try {
                HexUtil.decodeHexString(datumHex);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid datum hex format: " + datumHex);
            }
        }

        // Precedence warnings: hex takes priority over structured
        if (redeemerHex != null && !redeemerHex.isEmpty() && redeemerStructured != null) {
            System.err.println("WARNING: Both redeemer_hex and redeemer (structured) are present. " +
                    "Using redeemer_hex (takes precedence). Remove one to avoid confusion.");
        }

        if (datumHex != null && !datumHex.isEmpty() && datumStructured != null) {
            System.err.println("WARNING: Both datum_hex and datum (structured) are present. " +
                    "Using datum_hex (takes precedence). Remove one to avoid confusion.");
        }
    }

    @Override
    @SneakyThrows
    public TxIntent resolveVariables(java.util.Map<String, Object> variables) {
        if (variables == null) {
            variables = new HashMap<>();
        }

        String resolvedRedeemerHex = VariableResolver.resolve(redeemerHex, variables);
        String resolvedDatumHex = VariableResolver.resolve(datumHex, variables);

        String resolvedAddress = VariableResolver.resolve(address, variables);

        JsonNode resolvedFilter = resolveJsonNodeVariables(utxoFilter, variables);

        // Process DATUM structured format if present
        PlutusData resolvedDatum = datum;
        if (datumStructured != null && datum == null) {
            // Apply 3-step pipeline: Strip @name → Resolve vars → Build PlutusData
            resolvedDatum = PlutusDataYamlUtil.fromYamlNode(datumStructured, variables);
        }

        // Process REDEEMER structured format if present
        PlutusData resolvedRedeemer = redeemerData;
        if (redeemerStructured != null && redeemerData == null) {
            // Apply 3-step pipeline (same as datum)
            resolvedRedeemer = PlutusDataYamlUtil.fromYamlNode(redeemerStructured, variables);
        }

        // Resolve tx_hash inside utxoRefs if provided
        java.util.List<UtxoRef> resolvedRefs = utxoRefs;
        boolean refsChanged = false;
        if (utxoRefs != null && !utxoRefs.isEmpty()) {
            resolvedRefs = new java.util.ArrayList<>(utxoRefs.size());
            for (UtxoRef r : utxoRefs) {
                if (r == null) {
                    resolvedRefs.add(null);
                    continue;
                }
                UtxoRef resolved = r.resolveVariables(variables);
                if (resolved != r) {
                    refsChanged = true;
                    resolvedRefs.add(resolved);
                } else {
                    resolvedRefs.add(r);
                }
            }
        }

        // Check if any variables were resolved
        if (!java.util.Objects.equals(resolvedRedeemerHex, redeemerHex)
                || !java.util.Objects.equals(resolvedDatumHex, datumHex)
                || !java.util.Objects.equals(resolvedAddress, address)
                || !java.util.Objects.equals(resolvedFilter, utxoFilter)
                || !java.util.Objects.equals(resolvedDatum, datum)
                || !java.util.Objects.equals(resolvedRedeemer, redeemerData)
                || refsChanged) {
            return this.toBuilder()
                .redeemerHex(resolvedRedeemerHex)
                .datumHex(resolvedDatumHex)
                .datum(resolvedDatum)
                .redeemerData(resolvedRedeemer)
                .address(resolvedAddress)
                .utxoFilter(resolvedFilter)
                .utxoRefs(resolvedRefs)
                .build();
        }

        return this;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode resolveJsonNodeVariables(JsonNode node, Map<String, Object> variables) {
        if (node == null) return null;
        if (node.isTextual()) {
            String resolved = VariableResolver.resolve(node.asText(), variables);
            if (java.util.Objects.equals(resolved, node.asText())) return node; // unchanged
            return MAPPER.getNodeFactory().textNode(resolved);
        } else if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node.deepCopy();
            java.util.Iterator<String> names = obj.fieldNames();
            List<String> keys = new ArrayList<>();
            while (names.hasNext()) keys.add(names.next());
            for (String k : keys) {
                obj.set(k, resolveJsonNodeVariables(obj.get(k), variables));
            }
            return obj;
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node.deepCopy();
            for (int i = 0; i < arr.size(); i++) {
                arr.set(i, resolveJsonNodeVariables(arr.get(i), variables));
            }
            return arr;
        } else {
            return node; // numbers, booleans, null
        }
    }

    // Factory methods

    /**
     * Create a script collection intention with original objects.
     * Single factory method to eliminate duplication.
     */
    public static ScriptCollectFromIntent collectFrom(List<Utxo> utxos, PlutusData redeemerData, PlutusData datum) {
        if (utxos == null || utxos.isEmpty()) {
            throw new IllegalArgumentException("UTXOs cannot be null or empty");
        }
        return ScriptCollectFromIntent.builder()
                .utxos(utxos)
                .redeemerData(redeemerData)
                .datum(datum)
                .build();
    }

    /**
     * Create a script collection intention for single UTXO with redeemer and datum.
     */
    public static ScriptCollectFromIntent collectFrom(Utxo utxo, PlutusData redeemerData, PlutusData datum) {
        return collectFrom(List.of(utxo), redeemerData, datum);
    }

    /**
     * Create a script collection intention for single UTXO with redeemer only.
     */
    public static ScriptCollectFromIntent collectFrom(Utxo utxo, PlutusData redeemerData) {
        return collectFrom(List.of(utxo), redeemerData, null);
    }

    /**
     * Create a script collection intention for single UTXO without redeemer/datum.
     */
    public static ScriptCollectFromIntent collectFrom(Utxo utxo) {
        return collectFrom(List.of(utxo), null, null);
    }

    /**
     * Create a script collection intention for multiple UTXOs with redeemer only.
     */
    public static ScriptCollectFromIntent collectFrom(List<Utxo> utxos, PlutusData redeemerData) {
        return collectFrom(utxos, redeemerData, null);
    }

    public static ScriptCollectFromIntent collectFrom(LazyUtxoStrategy lazyUtxoStrategy, PlutusData redeemerData, PlutusData datum) {
        return ScriptCollectFromIntent.builder()
                .lazyUtxoStrategy(lazyUtxoStrategy)
                .redeemerData(redeemerData)
                .datum(datum)
                .build();
    }

    /**
     * Check if this intention has runtime objects available.
     */
    @JsonIgnore
    public boolean hasRuntimeObjects() {
        return utxos != null && !utxos.isEmpty();
    }

    @Override
    public void checkSerializable() {
        if (lazyUtxoStrategy != null && !lazyUtxoStrategy.isSerializable()) {
            throw new IllegalStateException(
                "Cannot serialize ScriptTx with predicate-based collectFrom(). " +
                "Predicate-based methods (collectFrom(address, Predicate, ...) and " +
                "collectFromList(address, Predicate, ...)) are runtime-only and cannot be serialized to YAML. " +
                "Use UtxoFilterSpec-based collectFrom(address, UtxoFilterSpec, ...) instead for serializable transactions."
            );
        }
    }

    @Override
    @SneakyThrows
    public LazyUtxoStrategy utxoStrategy() {
        if (redeemerData == null && redeemerHex != null)
            redeemerData = PlutusData.deserialize(HexUtil.decodeHexString(redeemerHex));

        if (datum == null && datumHex != null)
            datum = PlutusData.deserialize(HexUtil.decodeHexString(datumHex));

        if (lazyUtxoStrategy == null) {
            if (!hasRuntimeObjects()) {
                if (utxoFilter != null) {
                    // Build strategy from filter
                    var spec = UtxoFilterYaml.parseNode(utxoFilter);
                    if (address == null || address.isEmpty())
                        throw new IllegalStateException("address is required when utxo_filter is provided");

                    lazyUtxoStrategy = new UtxoFilterStrategy(address, spec, redeemerData, datum);
                    return lazyUtxoStrategy;
                } else {
                    var txInputs = utxoRefs.stream()
                            .map(utxoRef -> new TransactionInput(utxoRef.getTxHash(), utxoRef.asIntOutputIndex()))
                            .collect(Collectors.toList());
                    return new FixedUtxoRefStrategy(txInputs, redeemerData, datum);
                }
            } else {
                return new FixedUtxoStrategy(utxos, redeemerData, datum);
            }
        } else {
            return lazyUtxoStrategy;
        }
    }

    @Override
    public TxBuilder apply(IntentContext context) {
        // No-op: collection is materialized prior to input selection in ScriptTx.complete()
        return (ctx, transaction) -> {

            if (!hasRuntimeObjects()) {
                // Resolve UTXOs if not already done
                utxos = utxoStrategy().resolve(ctx.getUtxoSupplier());
            }

            if (spendingContexts == null)
                spendingContexts = new ArrayList<>();

            for (Utxo utxo : utxos) {
                if (transaction.getWitnessSet() == null) {
                    transaction.setWitnessSet(new TransactionWitnessSet());
                }
                if (datum != null) {
                    if (!transaction.getWitnessSet().getPlutusDataList().contains(datum))
                        transaction.getWitnessSet().getPlutusDataList().add(datum);
                }

                if (redeemerData != null) {
                    Redeemer redeemer = Redeemer.builder()
                            .tag(RedeemerTag.Spend)
                            .data(redeemerData)
                            .index(BigInteger.valueOf(1)) //dummy value
                            .exUnits(ExUnits.builder()
                                    .mem(BigInteger.valueOf(10000)) // Some dummy value
                                    .steps(BigInteger.valueOf(10000))
                                    .build())
                            .build();

                    int scriptInputIndex = RedeemerUtil.getScriptInputIndex(utxo, transaction);
                    if (scriptInputIndex == -1)
                        throw new TxBuildException("Script utxo is not found in transaction inputs : " + utxo.getTxHash());

                    //update script input index
                    redeemer.setIndex(scriptInputIndex);
                    transaction.getWitnessSet().getRedeemers().add(redeemer);

                    spendingContexts.add(new SpendingContext(utxo, redeemer));
                }
            }
        };
    }

    public Optional<Utxo> getUtxoForRedeemer(Redeemer redeemer) {
        return spendingContexts.stream()
                .filter(sc -> sc.getRedeemer() == redeemer)
                .findFirst()
                .map(SpendingContext::getUtxo);
    }



    @Data
    @AllArgsConstructor
    class SpendingContext {
        private Utxo utxo;
        private Redeemer redeemer;
    }
}
