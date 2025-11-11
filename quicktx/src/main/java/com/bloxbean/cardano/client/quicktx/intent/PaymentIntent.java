package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.OutputBuilders;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.quicktx.serialization.PlutusDataYamlUtil;
import com.bloxbean.cardano.client.quicktx.serialization.VariableResolver;
import com.bloxbean.cardano.client.spec.Script;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.SneakyThrows;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.Tuple;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

/**
 * Unified intention for all payment operations (payToAddress and payToContract).
 * Supports optional fields for scripts, datum, and other payment variants.
 * <p>
 * This intention can represent:
 * - Simple payments to addresses
 * - Payments with attached reference scripts
 * - Contract payments with inline datum or datum hash
 * - Multi-asset payments
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
public class PaymentIntent implements TxIntent {

    /**
     * Destination address for the payment.
     * Can be a regular address or contract address.
     */
    @JsonProperty("address")
    private String address;

    /**
     * Amounts to send in this payment.
     * Supports multiple assets in a single output.
     */
    @JsonProperty("amounts")
    private List<Amount> amounts;

    // Runtime fields - original objects preserved

    /**
     * Reference script to attach to the output (runtime object).
     */
    @JsonIgnore
    private Script script;

    /**
     * Script reference bytes for direct attachment (runtime object).
     */
    @JsonIgnore
    private byte[] scriptRefBytes;

    /**
     * Inline datum for contract payments (runtime object).
     */
    @JsonIgnore
    private PlutusData datum;

    /**
     * Datum hash for contract payments (runtime bytes).
     */
    @JsonIgnore
    private byte[] datumHashBytes;

    /**
     * Reference script for contract payments (runtime object).
     */
    @JsonIgnore
    private Script refScript;

    // Serialization fields - computed from runtime objects

    /**
     * Script reference bytes as hex for serialization.
     */
    @JsonProperty("script_ref_bytes")
    private String scriptRefBytesHex;

    /**
     * Datum hex for serialization.
     */
    @JsonProperty("datum_hex")
    private String datumHex;

    /**
     * Datum hash for serialization.
     */
    @JsonProperty("datum_hash")
    private String datumHash;

    /**
     * Structured datum format for YAML
     * Supports optional @name annotations and variable resolution.
     */
    @JsonProperty("datum")
    private JsonNode datumStructured;

    /**
     * Get script reference bytes as hex for serialization.
     */
    @JsonProperty("script_ref_bytes")
    public String getScriptRefBytesHex() {
        if (scriptRefBytes != null) {
            return HexUtil.encodeHexString(scriptRefBytes);
        }
        return scriptRefBytesHex;
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
     * Get datum hash for serialization.
     */
    @JsonProperty("datum_hash")
    public String getDatumHash() {
        if (datumHashBytes != null) {
            return HexUtil.encodeHexString(datumHashBytes);
        }
        return datumHash;
    }

    /**
     * Get structured datum format for YAML serialization.
     * Precedence: datum_hex &gt; datum_hash &gt; runtime object &gt; structured format.
     * Note: @name annotations are NOT preserved (write-only).
     */
    @JsonProperty("datum")
    public JsonNode getDatumStructured() {
        if (datumHex != null && !datumHex.isEmpty()) {
            return null;
        }
        if (datumHash != null && !datumHash.isEmpty()) {
            return null;
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
        return "payment";
    }

    @Override
    public void validate() {
        if (address == null || address.isEmpty()) {
            throw new IllegalStateException("Payment address is required");
        }
        if (amounts == null || amounts.isEmpty()) {
            throw new IllegalStateException("Payment amounts are required");
        }
        // Check that only one datum representation is used (runtime or serialized)
        int datumCount = 0;
        if (datum != null) datumCount++;
        if (datumHex != null) datumCount++;
        if (datumHashBytes != null) datumCount++;
        if (datumHash != null) datumCount++;
        if (datumStructured != null) datumCount++;

        if (datumCount > 1) {
            // Issue warnings for precedence conflicts
            if ((datumHex != null && !datumHex.isEmpty()) && datumStructured != null) {
                log.warn("WARNING: Both datum_hex and datum (structured) are present. " +
                        "Using datum_hex (takes precedence). Remove one to avoid confusion.");
            }
            if ((datumHash != null && !datumHash.isEmpty()) && datumStructured != null) {
                log.warn("WARNING: Both datum_hash and datum (structured) are present. " +
                        "Using datum_hash (takes precedence). Remove one to avoid confusion.");
            }
            if ((datumHex != null && datumHash != null) ||
                (datum != null && (datumHex != null || datumHash != null || datumHashBytes != null))) {
                throw new IllegalStateException("Cannot have multiple datum representations (choose one: datum, datum_hex, or datum_hash)");
            }
        }
    }

    @Override
    @SneakyThrows
    public TxIntent resolveVariables(java.util.Map<String, Object> variables) {
        if (variables == null) {
            variables = new java.util.HashMap<>();
        }

        String resolvedAddress = VariableResolver.resolve(address, variables);

        // Process DATUM structured format if present
        PlutusData resolvedDatum = datum;
        if (datumStructured != null && datum == null) {
            // Apply 3-step pipeline: Strip @name → Resolve vars → Build PlutusData
            resolvedDatum = PlutusDataYamlUtil.fromYamlNode(datumStructured, variables);
        }

        // Check if anything changed
        if (!Objects.equals(resolvedAddress, address)
                || !Objects.equals(resolvedDatum, datum)) {
            return this.toBuilder()
                    .address(resolvedAddress)
                    .datum(resolvedDatum)
                    .build();
        }

        return this;
    }

    @Override
    public TxOutputBuilder outputBuilder(IntentContext context) {
        try {
            // Validate address is not null/empty
            if (address == null || address.trim().isEmpty()) {
                throw new TxBuildException("Payment address is required after variable resolution");
            }

            // Create transaction output
            TransactionOutput output = createTransactionOutput(address);

            // Return TxOutputBuilder using OutputBuilders
            return OutputBuilders.createFromOutput(output);

        } catch (Exception e) {
            throw new TxBuildException("Failed to create output builder for PaymentIntention: " + e.getMessage(), e);
        }
    }

    @Override
    public TxBuilder apply(IntentContext context) {
        return (ctx, txn) -> {
            // No-op: output creation handled in outputBuilder() phase
        };
    }

    /**
     * Create a TransactionOutput from this intention's data.
     * Handles all payment variants including scripts, datum, and multi-assets.
     */
    private TransactionOutput createTransactionOutput(String resolvedAddress) {
        try {
            // Build the transaction output
            TransactionOutput.TransactionOutputBuilder builder = TransactionOutput.builder()
                    .address(resolvedAddress)
                    .value(createValueFromAmounts());

            // Set datum if present (priority: runtime objects > serialized hex)
            if (datum != null) {
                // Use runtime PlutusData directly
                builder.inlineDatum(datum);
            } else if (datumHashBytes != null) {
                // Use runtime datum hash bytes
                builder.datumHash(datumHashBytes);
            } else if (datumHex != null && !datumHex.isEmpty()) {
                // Fall back to deserializing hex string for inline datum
                PlutusData deserializedDatum = PlutusData.deserialize(HexUtil.decodeHexString(datumHex));
                builder.inlineDatum(deserializedDatum);
            } else if (datumHash != null && !datumHash.isEmpty()) {
                // Fall back to deserializing hex string for datum hash
                builder.datumHash(HexUtil.decodeHexString(datumHash));
            }

            // Set script reference if present (priority: runtime objects > serialized hex)
            if (scriptRefBytes != null) {
                // Use runtime byte array directly
                builder.scriptRef(scriptRefBytes);
            } else if (refScript != null) {
                // Use runtime Script object's scriptRefBytes
                builder.scriptRef(refScript.scriptRefBytes());
            } else if (script != null) {
                // Use runtime Script object's scriptRefBytes
                builder.scriptRef(script.scriptRefBytes());
            } else if (scriptRefBytesHex != null && !scriptRefBytesHex.isEmpty()) {
                // Fall back to deserializing hex string
                byte[] scriptRefBytesFromHex = HexUtil.decodeHexString(scriptRefBytesHex);
                builder.scriptRef(scriptRefBytesFromHex);
            }

            return builder.build();

        } catch (Exception e) {
            throw new TxBuildException("Failed to create TransactionOutput: " + e.getMessage(), e);
        }
    }

    /**
     * Create a Value object from the amounts list.
     * Handles both ADA (lovelace) and native assets.
     */
    private Value createValueFromAmounts() {
        Value.ValueBuilder valueBuilder = Value.builder()
                .coin(BigInteger.ZERO);

        // Process each amount
        for (Amount amount : amounts) {
            String unit = amount.getUnit();
            BigInteger quantity = amount.getQuantity();

            if (LOVELACE.equals(unit)) {
                // Set ADA amount
                valueBuilder.coin(quantity);
            } else {
                // Handle native assets
                Tuple<String, String> policyAssetName = AssetUtil.getPolicyIdAndAssetName(unit);
                String policyId = policyAssetName._1;
                String assetName = policyAssetName._2;

                // Create asset and multiasset
                Asset asset = new Asset(assetName, quantity);
                MultiAsset multiAsset = new MultiAsset(policyId, List.of(asset));

                // Add to value
                if (valueBuilder.build().getMultiAssets() == null) {
                    valueBuilder.multiAssets(List.of(multiAsset));
                } else {
                    // Merge with existing multiassets
                    Value currentValue = valueBuilder.build();
                    Value newValue = currentValue.add(new Value(BigInteger.ZERO, List.of(multiAsset)));
                    valueBuilder = Value.builder()
                            .coin(newValue.getCoin())
                            .multiAssets(newValue.getMultiAssets());
                }
            }
        }

        return valueBuilder.build();
    }
}
