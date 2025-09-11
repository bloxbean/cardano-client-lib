package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.RedeemerUtil;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
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
import lombok.*;

import java.math.BigInteger;
import java.util.List;
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
public class ScriptCollectFromIntention implements TxInputIntention {

    /**
     * Original UTXOs for runtime use (preserves all UTXO data).
     */
    @JsonIgnore
    private List<Utxo> utxos;

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

    // Fields for deserialization from YAML/JSON
    @JsonProperty("utxo_refs")
    private List<UtxoRef> utxoRefs;

    @JsonProperty("redeemer_hex")
    private String redeemerHex;

    @JsonProperty("datum_hex")
    private String datumHex;

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
     * Computed from original redeemer when serializing.
     */
    @JsonProperty("redeemer_hex")
    public String getRedeemerHex() {
        if (redeemerData != null) {
            try {
                return redeemerData.serializeToHex();
            } catch (Exception e) {
                // Log error and return stored hex
            }
        }
        return redeemerHex;
    }

    /**
     * Get datum hex for serialization.
     * Computed from original datum when serializing.
     */
    @JsonProperty("datum_hex")
    public String getDatumHex() {
        if (datum != null) {
            try {
                return datum.serializeToHex();
            } catch (Exception e) {
                // Log error and return stored hex
            }
        }
        return datumHex;
    }

    @Override
    public String getType() {
        return "script_collect_from";
    }


    @Override
    public void validate() {
        TxInputIntention.super.validate();

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
                if (utxoRef.getOutputIndex() == null) {
                    throw new IllegalStateException("UTXO output index is required");
                }
                if (utxoRef.getOutputIndex() < 0) {
                    throw new IllegalStateException("UTXO output index must be non-negative");
                }

                // Basic validation for tx hash format
                if (!utxoRef.getTxHash().startsWith("${") && utxoRef.getTxHash().length() != 64) {
                    throw new IllegalStateException("Invalid transaction hash format: " + utxoRef.getTxHash());
                }
            }
        } else {
            throw new IllegalStateException("UTXOs are required for script collection");
        }

        // Validate hex strings if provided
        if (redeemerHex != null && !redeemerHex.isEmpty() && !redeemerHex.startsWith("${")) {
            try {
                HexUtil.decodeHexString(redeemerHex);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid redeemer hex format: " + redeemerHex);
            }
        }

        if (datumHex != null && !datumHex.isEmpty() && !datumHex.startsWith("${")) {
            try {
                HexUtil.decodeHexString(datumHex);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid datum hex format: " + datumHex);
            }
        }
    }

    @Override
    public TxIntention resolveVariables(java.util.Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return this;
        }

        String resolvedRedeemerHex = VariableResolver.resolve(redeemerHex, variables);
        String resolvedDatumHex = VariableResolver.resolve(datumHex, variables);

        // Check if any variables were resolved
        if (!java.util.Objects.equals(resolvedRedeemerHex, redeemerHex) || !java.util.Objects.equals(resolvedDatumHex, datumHex)) {
            return this.toBuilder()
                .redeemerHex(resolvedRedeemerHex)
                .datumHex(resolvedDatumHex)
                .build();
        }

        return this;
    }

    // Factory methods

    /**
     * Create a script collection intention with original objects.
     * Single factory method to eliminate duplication.
     */
    public static ScriptCollectFromIntention collectFrom(List<Utxo> utxos, PlutusData redeemerData, PlutusData datum) {
        if (utxos == null || utxos.isEmpty()) {
            throw new IllegalArgumentException("UTXOs cannot be null or empty");
        }
        return ScriptCollectFromIntention.builder()
                .utxos(utxos)
                .redeemerData(redeemerData)
                .datum(datum)
                .build();
    }

    /**
     * Create a script collection intention for single UTXO with redeemer and datum.
     */
    public static ScriptCollectFromIntention collectFrom(Utxo utxo, PlutusData redeemerData, PlutusData datum) {
        return collectFrom(List.of(utxo), redeemerData, datum);
    }

    /**
     * Create a script collection intention for single UTXO with redeemer only.
     */
    public static ScriptCollectFromIntention collectFrom(Utxo utxo, PlutusData redeemerData) {
        return collectFrom(List.of(utxo), redeemerData, null);
    }

    /**
     * Create a script collection intention for single UTXO without redeemer/datum.
     */
    public static ScriptCollectFromIntention collectFrom(Utxo utxo) {
        return collectFrom(List.of(utxo), null, null);
    }

    /**
     * Create a script collection intention for multiple UTXOs with redeemer only.
     */
    public static ScriptCollectFromIntention collectFrom(List<Utxo> utxos, PlutusData redeemerData) {
        return collectFrom(utxos, redeemerData, null);
    }

    /**
     * Create a script collection intention from UTXO references with hex strings.
     * This is used during deserialization from YAML/JSON.
     */
    public static ScriptCollectFromIntention collectFromHex(List<UtxoRef> utxoRefs, String redeemerHex, String datumHex) {
        return ScriptCollectFromIntention.builder()
                .utxoRefs(utxoRefs)
                .redeemerHex(redeemerHex)
                .datumHex(datumHex)
                .build();
    }

    /**
     * Check if this intention has runtime objects available.
     */
    @JsonIgnore
    public boolean hasRuntimeObjects() {
        return utxos != null && !utxos.isEmpty();
    }

    /**
     * Check if this intention needs UTXO resolution from blockchain.
     */
    @JsonIgnore
    public boolean needsUtxoResolution() {
        return !hasRuntimeObjects() && utxoRefs != null && !utxoRefs.isEmpty();
    }

    @Override
    @SneakyThrows
    public LazyUtxoStrategy utxoStrategy() {
        if (redeemerData == null && redeemerHex != null)
            redeemerData = PlutusData.deserialize(HexUtil.decodeHexString(redeemerHex));

        if (datum == null && datumHex != null)
            datum = PlutusData.deserialize(HexUtil.decodeHexString(datumHex));

        if (!hasRuntimeObjects()) {
            var txInputs = utxoRefs.stream()
                    .map(utxoRef -> new TransactionInput(utxoRef.getTxHash(), utxoRef.getOutputIndex()))
                    .collect(Collectors.toList());
            return new FixedUtxoRefStrategy(txInputs, redeemerData, datum);
        } else {
            return new FixedUtxoStrategy(utxos, redeemerData, datum);
        }
    }

    @Override
    public com.bloxbean.cardano.client.function.TxBuilder preApply(com.bloxbean.cardano.client.quicktx.IntentContext context) {
        return (ctx, txn) -> {
            // Basic validation only; input selection is prepared in ScriptTx.complete()
            validate();
        };
    }

    @Override
    public com.bloxbean.cardano.client.function.TxBuilder apply(com.bloxbean.cardano.client.quicktx.IntentContext context) {
        // No-op: collection is materialized prior to input selection in ScriptTx.complete()
        return (ctx, transaction) -> {

            if (!hasRuntimeObjects()) {
                // Resolve UTXOs if not already done
                utxos = utxoStrategy().resolve(ctx.getUtxoSupplier());
            }

            for (Utxo utxo : utxos) {
                if (transaction.getWitnessSet() == null) {
                    transaction.setWitnessSet(new TransactionWitnessSet());
                }
                if (datum != null) {
                    if (!transaction.getWitnessSet().getPlutusDataList().contains(datum))
                        transaction.getWitnessSet().getPlutusDataList().add(datum);
                }

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
            }
        };
    }
}
