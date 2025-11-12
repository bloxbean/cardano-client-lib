package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.MintUtil;
import com.bloxbean.cardano.client.function.helper.OutputBuilders;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.quicktx.serialization.PlutusDataYamlUtil;
import com.bloxbean.cardano.client.quicktx.serialization.VariableResolver;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.fasterxml.jackson.databind.JsonNode;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.annotation.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
public class ScriptMintingIntent implements TxIntent {

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

    @JsonProperty("redeemer_hex")
    private String redeemerHex;

    @JsonProperty("output_datum_hex")
    private String outputDatumHex;

    /**
     * Structured redeemer format for YAML
     * Supports optional @name annotations and variable resolution.
     */
    @JsonProperty("redeemer")
    private JsonNode redeemerStructured;

    /**
     * Structured output datum format for YAML
     * Supports optional @name annotations and variable resolution.
     */
    @JsonProperty("output_datum")
    private JsonNode outputDatumStructured;

    @Override
    public String getType() {
        return "script_minting";
    }

    @JsonProperty("redeemer_hex")
    public String getRedeemerHex() {
        // Don't serialize both hex and structured - prefer structured for readability
        if (redeemer != null) {
            return null;
        }
        return redeemerHex;
    }

    @JsonProperty("output_datum_hex")
    public String getOutputDatumHex() {
        // Don't serialize both hex and structured - prefer structured for readability
        if (outputDatum != null) {
            return null;
        }
        return outputDatumHex;
    }

    /**
     * Get structured redeemer format for YAML serialization.
     * Precedence: redeemer_hex &gt; runtime object &gt; structured format.
     * Note: @name annotations are NOT preserved (write-only).
     */
    @JsonProperty("redeemer")
    public JsonNode getRedeemerStructured() {
        if (redeemerHex != null && !redeemerHex.isEmpty()) {
            return null; // hex takes precedence
        }
        if (redeemer != null) {
            return PlutusDataYamlUtil.toYamlNode(redeemer);
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
     * Get structured output datum format for YAML serialization.
     * Precedence: output_datum_hex &gt; runtime object &gt; structured format.
     * Note: @name annotations are NOT preserved (write-only).
     */
    @JsonProperty("output_datum")
    public JsonNode getOutputDatumStructured() {
        if (outputDatumHex != null && !outputDatumHex.isEmpty()) {
            return null; // hex takes precedence
        }
        if (outputDatum != null) {
            return PlutusDataYamlUtil.toYamlNode(outputDatum);
        }
        return outputDatumStructured;
    }

    /**
     * Set structured output datum format for YAML deserialization.
     */
    @JsonProperty("output_datum")
    public void setOutputDatumStructured(JsonNode node) {
        this.outputDatumStructured = node;
    }

    @Override
    public void validate() {
        if (assets == null || assets.isEmpty()) {
            throw new IllegalStateException("ScriptMintingIntention requires assets");
        }

        if (receiver != null && receiver.isBlank()) {
            throw new IllegalStateException("Receiver must not be blank");
        }

        // Precedence warnings: hex takes priority over structured
        if (redeemerHex != null && !redeemerHex.isEmpty() && redeemerStructured != null) {
            log.warn("Both redeemer_hex and redeemer (structured) are present. " +
                    "Using redeemer_hex (takes precedence). Remove one to avoid confusion.");
        }

        if (outputDatumHex != null && !outputDatumHex.isEmpty() && outputDatumStructured != null) {
            log.warn("Both output_datum_hex and output_datum (structured) are present. " +
                    "Using output_datum_hex (takes precedence). Remove one to avoid confusion.");
        }
    }

    @Override
    @SneakyThrows
    public TxIntent resolveVariables(java.util.Map<String, Object> variables) {
        if (variables == null) {
            variables = new java.util.HashMap<>();
        }

        String resolvedPolicyId = VariableResolver.resolve(policyId, variables);
        String resolvedReceiver = VariableResolver.resolve(receiver, variables);
        String resolvedRedeemerHex = VariableResolver.resolve(redeemerHex, variables);
        String resolvedOutputDatumHex = VariableResolver.resolve(outputDatumHex, variables);

        // Process REDEEMER structured format if present
        PlutusData resolvedRedeemer = redeemer;
        if (redeemerStructured != null && redeemer == null) {
            // Apply 3-step pipeline: Strip @name → Resolve vars → Build PlutusData
            resolvedRedeemer = PlutusDataYamlUtil.fromYamlNode(redeemerStructured, variables);
        }

        // Process OUTPUT_DATUM structured format if present
        PlutusData resolvedOutputDatum = outputDatum;
        if (outputDatumStructured != null && outputDatum == null) {
            // Apply 3-step pipeline (same as redeemer)
            resolvedOutputDatum = PlutusDataYamlUtil.fromYamlNode(outputDatumStructured, variables);
        }

        if (!Objects.equals(resolvedReceiver, receiver)
                || !Objects.equals(resolvedPolicyId, policyId)
                || !java.util.Objects.equals(resolvedRedeemerHex, redeemerHex)
                || !Objects.equals(resolvedOutputDatumHex, outputDatumHex)
                || !Objects.equals(resolvedRedeemer, redeemer)
                || !Objects.equals(resolvedOutputDatum, outputDatum)) {
            return this.toBuilder()
                    .policyId(resolvedPolicyId)
                    .receiver(resolvedReceiver)
                    .redeemerHex(resolvedRedeemerHex)
                    .outputDatumHex(resolvedOutputDatumHex)
                    .redeemer(resolvedRedeemer)
                    .outputDatum(resolvedOutputDatum)
                    .build();
        }

        return this;
    }

    @Override
    public TxOutputBuilder outputBuilder(IntentContext ic) {
        try {
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
            try {
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
                //TODO:- Sort may happen multiple times based on no of minting policies
                List<MultiAsset> multiAssets = MintUtil.getSortedMultiAssets(txn.getBody().getMint());
                txn.getBody().setMint(multiAssets);

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
    public static ScriptMintingIntent of(String policyId, List<Asset> assets, PlutusData redeemer, String receiver, PlutusData outputDatum) {
        return ScriptMintingIntent.builder()
                .policyId(policyId)
                .assets(assets)
                .redeemer(redeemer)
                .receiver(receiver)
                .outputDatum(outputDatum)
                .build();
    }
}
