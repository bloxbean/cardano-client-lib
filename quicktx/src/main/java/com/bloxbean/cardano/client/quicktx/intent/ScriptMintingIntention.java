package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.MintUtil;
import com.bloxbean.cardano.client.function.helper.OutputBuilders;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.quicktx.serialization.VariableResolver;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.annotation.*;
import lombok.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Intention for minting/burning with Plutus script and optional receiver/output datum.
 * Captures ScriptTx.mintAsset(...) overloads that use PlutusScript and Redeemer.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScriptMintingIntention implements TxIntention {

    // Runtime objects
//    @JsonIgnore
//    private PlutusScript script;

    @JsonProperty
    private String policyId;

    @JsonProperty("assets")
    private List<Asset> assets;

    @JsonIgnore
    private PlutusData redeemer;

    @JsonProperty("receiver")
    private String receiver;

    @JsonIgnore
    private PlutusData outputDatum;

//    // Serialization fields
//    @JsonProperty("script_hex")
//    private String scriptHex;
//
//    // 1=V1, 2=V2, 3=V3
//    @JsonProperty("script_version")
//    private com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion scriptVersion;

    @JsonProperty("redeemer_hex")
    private String redeemerHex;

    @JsonProperty("output_datum_hex")
    private String outputDatumHex;

    @Override
    public String getType() {
        return "script_minting";
    }

//    @JsonProperty("script_hex")
//    public String getScriptHex() {
//        if (script != null) {
//            return script.getCborHex();
//        }
//        return scriptHex;
//    }

//    @JsonProperty("script_version")
//    public com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion getScriptVersion() {
//        if (script != null) {
//            if (script instanceof PlutusV1Script)
//                return com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion.v1;
//            if (script instanceof PlutusV2Script)
//                return com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion.v2;
//            if (script instanceof PlutusV3Script)
//                return com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion.v3;
//        }
//        return scriptVersion;
//    }

    @JsonProperty("redeemer_hex")
    public String getRedeemerHex() {
        if (redeemer != null) {
            try {
                return redeemer.serializeToHex();
            } catch (Exception e) {
            }
        }
        return redeemerHex;
    }

    @JsonProperty("output_datum_hex")
    public String getOutputDatumHex() {
        if (outputDatum != null) {
            try {
                return outputDatum.serializeToHex();
            } catch (Exception e) {
            }
        }
        return outputDatumHex;
    }

    @Override
    public void validate() {
//        if ((script == null) && (scriptHex == null || scriptHex.isEmpty() || scriptVersion == null)) {
//            throw new IllegalStateException("ScriptMintingIntention requires Plutus script or script_hex + script_version");
//        }
        if (assets == null || assets.isEmpty()) {
            throw new IllegalStateException("ScriptMintingIntention requires assets");
        }
    }

    @Override
    public TxIntention resolveVariables(java.util.Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return this;
        }

        String resolvedPolicyId = VariableResolver.resolve(policyId, variables);
        String resolvedReceiver = VariableResolver.resolve(receiver, variables);
//        String resolvedScriptHex = VariableResolver.resolve(scriptHex, variables);
        String resolvedRedeemerHex = VariableResolver.resolve(redeemerHex, variables);
        String resolvedOutputDatumHex = VariableResolver.resolve(outputDatumHex, variables);

        // Check if any variables were resolved
        if (!java.util.Objects.equals(resolvedReceiver, receiver) || !Objects.equals(resolvedPolicyId, policyId) || !java.util.Objects.equals(resolvedRedeemerHex, redeemerHex) || !java.util.Objects.equals(resolvedOutputDatumHex, outputDatumHex)) {
            return this.toBuilder()
                    .policyId(resolvedPolicyId)
                    .receiver(resolvedReceiver)
//                .scriptHex(resolvedScriptHex)
                    .redeemerHex(resolvedRedeemerHex)
                    .outputDatumHex(resolvedOutputDatumHex)
                    .build();
        }

        return this;
    }

    @Override
    public TxOutputBuilder outputBuilder(IntentContext ic) {
        try {
//            PlutusScript resolvedScript = resolveScript();
//            String policyId = resolvedScript.getPolicyId();

            // 1) Optionally create receiver output
            TxOutputBuilder txOutputBuilder = null;
            if (receiver != null && !receiver.isBlank()) {
                String resolvedReceiver = receiver;
                // Convert assets into amounts (no ADA)
                List<Amount> assetAmounts = assets.stream()
                        .map(asset -> new Amount(AssetUtil.getUnit(policyId, asset), asset.getValue()))
                        .collect(Collectors.toList());

                TransactionOutput.TransactionOutputBuilder builder = TransactionOutput.builder()
                        .address(resolvedReceiver)
                        .value(createValueFromAmounts(assetAmounts));

                if (outputDatum != null) {
                    builder.inlineDatum(outputDatum);
                } else if (outputDatumHex != null && !outputDatumHex.isEmpty()) {
                    builder.inlineDatum(PlutusData.deserialize(HexUtil.decodeHexString(outputDatumHex)));
                }

                txOutputBuilder = OutputBuilders.createFromOutput(builder.build());
            }

            // 2) Ensure mint multiassets are visible to input selection by adding to context
            MultiAsset multiAsset = MultiAsset.builder()
                    .policyId(policyId)
                    .assets(assets)
                    .build();

            if (txOutputBuilder == null)
                txOutputBuilder = (ctx, txn) -> {
                };

            TxOutputBuilder addMintToCtx = (ctx, txn) -> ctx.addMintMultiAsset(multiAsset);
            return txOutputBuilder.and(addMintToCtx);

        } catch (Exception e) {
            throw new TxBuildException("Failed to build outputBuilder for ScriptMintingIntention", e);
        }
    }

    @Override
    public TxBuilder preApply(IntentContext ic) {
        return (ctx, txn) -> {
            validate();
            // Minimal receiver validation
            if (receiver != null && receiver.isBlank()) {
                throw new TxBuildException("Receiver must resolve to a non-empty address");
            }

            try {
//                PlutusScript resolvedScript = resolveScript();
//                String policyId = resolvedScript.getPolicyId();

                MultiAsset newMa = MultiAsset.builder().policyId(policyId).assets(assets).build();
                if (txn.getBody().getMint() == null) {
                    txn.getBody().setMint(new java.util.ArrayList<>(List.of(newMa)));
                } else {
                    List<MultiAsset> mintList = txn.getBody().getMint();
                    MultiAsset existing = mintList.stream()
                            .filter(ma -> ma.getPolicyId().equals(policyId))
                            .findFirst().orElse(null);
                    if (existing != null) {
                        mintList.remove(existing);
                        mintList.add(existing.add(newMa));
                    } else {
                        mintList.add(newMa);
                    }
                }
            } catch (Exception e) {
                throw new TxBuildException("Failed to preApply ScriptMintingIntention", e);
            }
        };
    }

    @Override
    public TxBuilder apply(IntentContext ic) {
        return (ctx, txn) -> {
            try {
//                PlutusScript resolvedScript = resolveScript();
//                String policyId = resolvedScript.getPolicyId();

                // Ensure body mint list and merge assets by policy id
//                MultiAsset newMa = MultiAsset.builder().policyId(policyId).assets(assets).build();
//                if (txn.getBody().getMint() == null) {
//                    txn.getBody().setMint(new java.util.ArrayList<>(List.of(newMa)));
//                } else {
//                    List<MultiAsset> mintList = txn.getBody().getMint();
//                    MultiAsset existing = mintList.stream()
//                            .filter(ma -> ma.getPolicyId().equals(policyId))
//                            .findFirst().orElse(null);
//                    if (existing != null) {
//                        mintList.remove(existing);
//                        mintList.add(existing.add(newMa));
//                    } else {
//                        mintList.add(newMa);
//                    }
//                }

                //TODO:- Sort may happen multiple times based on no of minting policies
                List<MultiAsset> multiAssets = MintUtil.getSortedMultiAssets(txn.getBody().getMint());
                txn.getBody().setMint(multiAssets);

                // Ensure witness set and add script
//                if (txn.getWitnessSet() == null) txn.setWitnessSet(new TransactionWitnessSet());
//                if (resolvedScript instanceof PlutusV1Script) {
//                    if (!txn.getWitnessSet().getPlutusV1Scripts().contains(resolvedScript))
//                        txn.getWitnessSet().getPlutusV1Scripts().add((PlutusV1Script) resolvedScript);
//                } else if (resolvedScript instanceof PlutusV2Script) {
//                    if (!txn.getWitnessSet().getPlutusV2Scripts().contains(resolvedScript))
//                        txn.getWitnessSet().getPlutusV2Scripts().add((PlutusV2Script) resolvedScript);
//                } else if (resolvedScript instanceof PlutusV3Script) {
//                    if (!txn.getWitnessSet().getPlutusV3Scripts().contains(resolvedScript))
//                        txn.getWitnessSet().getPlutusV3Scripts().add((PlutusV3Script) resolvedScript);
//                }

                // Add mint redeemer
                PlutusData resolvedRedeemer = resolveRedeemer();
                if (resolvedRedeemer != null) {
                    Redeemer rd = Redeemer.builder()
                            .tag(RedeemerTag.Mint)
                            .data(resolvedRedeemer)
                            .exUnits(ExUnits.builder()
                                    .mem(BigInteger.valueOf(10000))
                                    .steps(BigInteger.valueOf(10000))
                                    .build())
                            .build();

                    // Set index based on policy position
                    int index = java.util.stream.IntStream.range(0, txn.getBody().getMint().size())
                            .filter(i -> policyId.equals(txn.getBody().getMint().get(i).getPolicyId()))
                            .findFirst().orElse(-1);
                    if (index < 0) throw new TxBuildException("Policy id not found in mint list");
                    rd.setIndex(index);

                    // Add if not already present
                    if (txn.getWitnessSet().getRedeemers().stream()
                            .noneMatch(r -> r.getTag() == RedeemerTag.Mint && r.getIndex() == rd.getIndex())) {
                        txn.getWitnessSet().getRedeemers().add(rd);
                    }
                }

            } catch (Exception e) {
                throw new TxBuildException("Failed to apply ScriptMintingIntention", e);
            }
        };
    }

    // Helpers
//    private PlutusScript resolveScript() {
//        if (script != null) return script;
//        if (scriptVersion == PlutusVersion.v1)
//            return PlutusV1Script.builder()
//                    .cborHex(scriptHex)
//                    .build();
//        if (scriptVersion == PlutusVersion.v2)
//            return PlutusV2Script.builder()
//                    .cborHex(scriptHex)
//                    .build();
//        if (scriptVersion == PlutusVersion.v3)
//            return PlutusV3Script.builder()
//                    .cborHex(scriptHex)
//                    .build();
//
//        throw new IllegalStateException("Invalid script version: " + scriptVersion);
//    }

    private PlutusData resolveRedeemer() throws Exception {
        if (redeemer != null) return redeemer;
        if (redeemerHex != null && !redeemerHex.isEmpty()) {
            return PlutusData.deserialize(HexUtil.decodeHexString(redeemerHex));
        }
        return null;
    }

    private Value createValueFromAmounts(List<Amount> amounts) {
        Value.ValueBuilder vb = Value.builder().coin(BigInteger.ZERO);
        for (Amount amount : amounts) {
            String unit = amount.getUnit();
            if (!com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE.equals(unit)) {
                var pa = AssetUtil.getPolicyIdAndAssetName(unit);
                Asset asset = new Asset(pa._2, amount.getQuantity());
                MultiAsset ma = new MultiAsset(pa._1, List.of(asset));
                Value merged = (vb.build()).add(Value.builder().coin(BigInteger.ZERO).multiAssets(List.of(ma)).build());
                vb = Value.builder().coin(merged.getCoin()).multiAssets(merged.getMultiAssets());
            }
        }
        return vb.build();
    }

    // Factory helpers
    public static ScriptMintingIntention of(String policyId, List<Asset> assets, PlutusData redeemer, String receiver, PlutusData outputDatum) {
        return ScriptMintingIntention.builder()
                .policyId(policyId)
                .assets(assets)
                .redeemer(redeemer)
                .receiver(receiver)
                .outputDatum(outputDatum)
                .build();
    }
}
