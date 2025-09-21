package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.MintCreators;
import com.bloxbean.cardano.client.function.helper.OutputBuilders;
import com.bloxbean.cardano.client.plutus.spec.PlutusV1Script;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.plutus.spec.PlutusV3Script;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.quicktx.serialization.VariableResolver;
import com.bloxbean.cardano.client.spec.Script;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.util.HexUtil;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigInteger;
import java.util.List;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

/**
 * Intention for minting assets with an optional receiver address.
 * This captures the mintAssets operation from Tx class.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MintingIntent implements TxIntent {

    // Runtime fields - original objects preserved

    /**
     * The original minting policy script (runtime object).
     */
    @JsonIgnore
    private Script script;

    /**
     * List of assets to mint (can have negative quantities for burning).
     */
    @JsonProperty("assets")
    private List<Asset> assets;

    /**
     * Optional receiver address for minted assets.
     * If null, assets will be distributed to addresses defined in payToAddress methods.
     */
    @JsonProperty("receiver")
    private String receiver;

    // Serialization fields - computed from runtime objects or set during deserialization

    /**
     * Script hex for serialization.
     */
    @JsonProperty("script_hex")
    private String scriptHex;

    /**
     * Script type for proper deserialization.
     * 0 = NativeScript, 1 = PlutusV1Script, 2 = PlutusV2Script, 3 = PlutusV3Script
     */
    @JsonProperty("script_type")
    private Integer scriptType;

    @Override
    public String getType() {
        return "minting";
    }

    /**
     * Get script hex for serialization.
     * Computed from original script when serializing.
     */
    @JsonProperty("script_hex")
    public String getScriptHex() {
        if (script != null) {
            try {
                return HexUtil.encodeHexString(script.serializeScriptBody());
            } catch (Exception e) {
                // Log error and return stored hex
            }
        }
        return scriptHex;
    }

    /**
     * Get script type for serialization.
     * Computed from original script when serializing.
     */
    @JsonProperty("script_type")
    public Integer getScriptType() {
        if (script != null) {
            return script.getScriptType();
        }
        return scriptType;
    }

    /**
     * Factory method to create MintingIntention from Script object.
     */
    public static MintingIntent from(Script script, List<Asset> assets, String receiver) {
        return MintingIntent.builder()
            .script(script)
            .assets(assets)
            .receiver(receiver)
            .build();
    }

    /**
     * Factory method to create MintingIntention from script hex and type.
     * This is used during deserialization from YAML/JSON.
     */
    public static MintingIntent fromHex(String scriptHex, Integer scriptType, List<Asset> assets, String receiver) {
        return MintingIntent.builder()
            .scriptHex(scriptHex)
            .scriptType(scriptType)
            .assets(assets)
            .receiver(receiver)
            .build();
    }


    /**
     * Check if this has a specific receiver address.
     */
    public boolean hasReceiver() {
        return receiver != null && !receiver.isEmpty();
    }



    @Override
    public void validate() {
        if (assets == null || assets.isEmpty()) {
            throw new IllegalStateException("Minting intention requires assets");
        }

        // Check that we have either runtime script or serialized data
        if (script == null && (scriptHex == null || scriptHex.isEmpty() || scriptType == null)) {
            throw new IllegalStateException("Minting intention requires either runtime script or script hex with type");
        }
    }

    @Override
    public TxIntent resolveVariables(java.util.Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return this;
        }

        String resolvedReceiver = VariableResolver.resolve(receiver, variables);
        String resolvedScriptHex = VariableResolver.resolve(scriptHex, variables);

        // Check if any variables were resolved
        if (!java.util.Objects.equals(resolvedReceiver, receiver) || !java.util.Objects.equals(resolvedScriptHex, scriptHex)) {
            return this.toBuilder()
                .receiver(resolvedReceiver)
                .scriptHex(resolvedScriptHex)
                .build();
        }

        return this;
    }

    // Self-processing methods for functional TxBuilder architecture

    @Override
    @SneakyThrows
    public TxOutputBuilder outputBuilder(IntentContext context) {
        // Phase 1: Create output for receiver if specified

        TxOutputBuilder txOutputBuilder = null;
        if (hasReceiver()) {
            try {
                String resolvedReceiver = receiver;

                // Validate resolved receiver
                if (resolvedReceiver == null || resolvedReceiver.trim().isEmpty()) {
                    throw new TxBuildException("Receiver address is required after variable resolution");
                }

                // Get the script to calculate policy ID
                Script resolvedScript = resolveScript();
                String policyId = resolvedScript.getPolicyId();

                // Convert assets to amounts
                List<Amount> assetAmounts = convertAssetsToAmounts(policyId);

                // Create transaction output for the receiver
                TransactionOutput.TransactionOutputBuilder builder = TransactionOutput.builder()
                    .address(resolvedReceiver)
                    .value(createValueFromAmounts(assetAmounts));

                TransactionOutput receiverOutput = builder.build();

                // Return TxOutputBuilder using OutputBuilders
                txOutputBuilder = OutputBuilders.createFromOutput(receiverOutput);

            } catch (Exception e) {
                throw new TxBuildException("Failed to create output builder for MintingIntention: " + e.getMessage(), e);
            }
        }

        //Add multi assets to tx builder context
        String policyId = resolveScript().getPolicyId();
        MultiAsset multiAsset = MultiAsset.builder()
                .policyId(policyId)
                .assets(assets)
                .build();

        if (txOutputBuilder == null)
            txOutputBuilder = (ctx, txn) -> {
            };

        txOutputBuilder = txOutputBuilder.and((ctx, txn) -> {
//            if (ctx.getMintMultiAssets() == null || ctx.getMintMultiAssets().isEmpty()) {
//                multiAssets.forEach(multiAssetTuple -> {
//                    context.addMintMultiAsset(multiAssetTuple._2);
//                });

                ctx.addMintMultiAsset(multiAsset);
//            }
        });

//        if (multiAssets != null && !multiAssets.isEmpty()) {
//            if (txOutputBuilder == null)
//                txOutputBuilder = (context, txn) -> {
//                };
//
//            txOutputBuilder = txOutputBuilder.and((context, txn) -> {
//                if (context.getMintMultiAssets() == null || context.getMintMultiAssets().isEmpty()) {
//                    multiAssets.forEach(multiAssetTuple -> {
//                        context.addMintMultiAsset(multiAssetTuple._2);
//                    });
//                }
//            });
//        }

        // No receiver = no outputs
        return txOutputBuilder;
    }

    @Override
    public TxBuilder preApply(IntentContext context) {
        return (ctx, txn) -> {
            // Pre-processing: validate script and assets
            if (assets == null || assets.isEmpty()) {
                throw new TxBuildException("Minting intention requires assets");
            }

            // Validate receiver if present
            if (hasReceiver()) {
                String resolvedReceiver = receiver;
                if (resolvedReceiver == null || resolvedReceiver.trim().isEmpty()) {
                    throw new TxBuildException("Receiver address is required after variable resolution");
                }
            }

            // Perform standard validation
            validate();
        };
    }

    @Override
    public TxBuilder apply(IntentContext context) {
        try {
            // Phase 3: Add minting to transaction (after UTXO selection)
            Script resolvedScript = resolveScript();
            MultiAsset multiAsset = MultiAsset.builder()
                .policyId(resolvedScript.getPolicyId())
                .assets(assets)
                .build();

            // Use MintCreators to add minting to transaction
            return MintCreators.mintCreator(resolvedScript, multiAsset);

        } catch (Exception e) {
            throw new TxBuildException("Failed to apply MintingIntention: " + e.getMessage(), e);
        }
    }

    // Helper methods

    /**
     * Check if this intention has runtime objects available.
     */
    @JsonIgnore
    public boolean hasRuntimeObjects() {
        return script != null;
    }

    /**
     * Check if this intention needs deserialization from hex data.
     */
    @JsonIgnore
    public boolean needsDeserialization() {
        return !hasRuntimeObjects() && scriptHex != null && !scriptHex.isEmpty() && scriptType != null;
    }

    /**
     * Resolve the script from runtime object or deserialized hex.
     */
    private Script resolveScript() throws Exception {
        if (hasRuntimeObjects()) {
            return script;
        } else if (needsDeserialization()) {
            // Deserialize script from hex
            script = deserializeScript(scriptHex, scriptType);
            return script;
        } else {
            throw new TxBuildException("No script available for minting");
        }
    }

    /**
     * Deserialize script from hex string and type.
     */
    private Script deserializeScript(String hex, Integer type) throws Exception {
        byte[] scriptBytes = HexUtil.decodeHexString(hex);

        switch (type) {
            case 0: // NativeScript
                var array = (Array) CborSerializationUtil.deserialize(scriptBytes);
                return NativeScript.deserialize(array);
            case 1: // PlutusV1Script
                var byteString1 = (ByteString) CborSerializationUtil.deserialize(scriptBytes);
                return PlutusV1Script.deserialize(byteString1);
            case 2: // PlutusV2Script
                var byteString2 = (ByteString) CborSerializationUtil.deserialize(scriptBytes);
                return PlutusV2Script.deserialize(byteString2);
            case 3: // PlutusV3Script
                var byteString3 = (ByteString) CborSerializationUtil.deserialize(scriptBytes);
                return PlutusV3Script.deserialize(byteString3);
            default:
                throw new TxBuildException("Invalid script type for minting: " + type);
        }
    }

    /**
     * Convert Asset list to Amount list for output creation.
     */
    private List<Amount> convertAssetsToAmounts(String policyId) {
        return assets.stream()
            .map(asset -> new Amount(AssetUtil.getUnit(policyId, asset), asset.getValue()))
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Create a Value object from amounts list for the receiver output.
     */
    private Value createValueFromAmounts(List<Amount> amounts) {
        Value.ValueBuilder valueBuilder = Value.builder()
            .coin(BigInteger.ZERO); // No ADA in minted assets output

        for (Amount amount : amounts) {
            String unit = amount.getUnit();
            BigInteger quantity = amount.getQuantity();

            if (!LOVELACE.equals(unit)) {
                // Handle native assets
                var policyAssetName = AssetUtil.getPolicyIdAndAssetName(unit);
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
