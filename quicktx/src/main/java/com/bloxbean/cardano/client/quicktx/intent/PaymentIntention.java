package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.OutputBuilders;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.quicktx.serialization.VariableResolver;
import com.bloxbean.cardano.client.spec.Script;
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

import java.math.BigInteger;
import java.util.List;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

/**
 * Unified intention for all payment operations (payToAddress and payToContract).
 * Supports optional fields for scripts, datum, and other payment variants.
 *
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
public class PaymentIntention implements TxIntention {

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

    // Optional fields for payment variants

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
     * Get script as JSON (needs custom serialization logic).
     */
    @JsonProperty("script")
    public Object getScriptForSerialization() {
        // TODO: Implement proper Script serialization
        return null;
    }

    /**
     * Get reference script as JSON (needs custom serialization logic).
     */
    @JsonProperty("ref_script")
    public Object getRefScriptForSerialization() {
        // TODO: Implement proper Script serialization
        return null;
    }

    // Legacy constructors for compatibility
    public PaymentIntention(String address, Amount amount) {
        this.address = address;
        this.amounts = List.of(amount);
    }

    public PaymentIntention(String address, List<Amount> amounts) {
        this.address = address;
        this.amounts = amounts;
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

        if (datumCount > 1) {
            throw new IllegalStateException("Cannot have multiple datum representations");
        }
    }

    @Override
    public TxIntention resolveVariables(java.util.Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return this;
        }

        String resolvedAddress = VariableResolver.resolve(address, variables);
        
        // For now, only resolve the address field since it's the most common variable field
        // Other fields like scriptRefBytesHex, datumHex, datumHash could also contain variables
        // but are less common and typically contain encoded data
        if (!java.util.Objects.equals(resolvedAddress, address)) {
            return this.toBuilder()
                .address(resolvedAddress)
                .build();
        }
        
        return this;
    }

    // Factory methods for clean API

    /**
     * Create a simple payment intention to an address.
     */
    public static PaymentIntention toAddress(String address, Amount amount) {
        return new PaymentIntention(address, amount);
    }

    /**
     * Create a simple payment intention to an address with multiple amounts.
     */
    public static PaymentIntention toAddress(String address, List<Amount> amounts) {
        return new PaymentIntention(address, amounts);
    }

    /**
     * Create a contract payment intention with datum.
     */
    public static PaymentIntention toContract(String address, Amount amount, PlutusData datum) {
        return PaymentIntention.builder()
            .address(address)
            .amounts(List.of(amount))
            .datum(datum)
            .build();
    }

    /**
     * Create a contract payment intention with datum hash bytes.
     */
    public static PaymentIntention toContract(String address, Amount amount, byte[] datumHashBytes) {
        return PaymentIntention.builder()
            .address(address)
            .amounts(List.of(amount))
            .datumHashBytes(datumHashBytes)
            .build();
    }

    /**
     * Create a contract payment intention with datum hash string.
     */
    public static PaymentIntention toContract(String address, Amount amount, String datumHashHex) {
        return PaymentIntention.builder()
            .address(address)
            .amounts(List.of(amount))
            .datumHash(datumHashHex)
            .build();
    }

    // Convenience methods

    /**
     * Set datum from PlutusData object.
     */
    public PaymentIntention withDatum(PlutusData datum) {
        this.datum = datum;
        return this;
    }

    /**
     * Set datum hash from bytes.
     */
    public PaymentIntention withDatumHash(byte[] datumHashBytes) {
        this.datumHashBytes = datumHashBytes;
        return this;
    }

    /**
     * Check if this is a contract payment.
     */
    @JsonIgnore
    public boolean isContractPayment() {
        return datum != null || datumHex != null || datumHashBytes != null || datumHash != null;
    }

    /**
     * Check if this has a script attachment.
     */
    @JsonIgnore
    public boolean hasScript() {
        return script != null || scriptRefBytes != null || scriptRefBytesHex != null || refScript != null;
    }

    /**
     * Check if this intention has runtime objects available.
     */
    @JsonIgnore
    public boolean hasRuntimeObjects() {
        return datum != null || datumHashBytes != null || script != null || scriptRefBytes != null || refScript != null;
    }

    /**
     * Check if this intention needs deserialization from hex data.
     */
    @JsonIgnore
    public boolean needsDeserialization() {
        return !hasRuntimeObjects() && (datumHex != null || datumHash != null || scriptRefBytesHex != null);
    }

    // Self-processing methods for functional TxBuilder architecture

    @Override
    public TxOutputBuilder outputBuilder(IntentContext context) {
        try {
            // Phase 1: Create transaction output (address already resolved during YAML parsing)
            
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
    public TxBuilder preApply(IntentContext context) {
        return (ctx, txn) -> {
            // Pre-processing: validate (address already resolved during YAML parsing)
            
            // Validate address is not null/empty
            if (address == null || address.trim().isEmpty()) {
                throw new TxBuildException("Payment address is required");
            }

            // Validate amounts
            if (amounts == null || amounts.isEmpty()) {
                throw new TxBuildException("Payment amounts are required");
            }

            // Perform standard validation
            validate();
        };
    }

    @Override
    public TxBuilder apply(IntentContext context) {
        // Phase 3: No additional transformations needed for basic payments
        // Output was already handled in Phase 1 (outputBuilder)
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
