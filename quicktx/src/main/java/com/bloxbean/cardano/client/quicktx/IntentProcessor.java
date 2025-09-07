package com.bloxbean.cardano.client.quicktx;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.quicktx.intent.*;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.spec.Script;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.Tuple;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

/**
 * Processes transaction intentions and resolves them to internal transaction state.
 * This is the core of the intent-based architecture.
 *
 * Uses specialized processors for different domains to avoid monolithic complexity.
 */
public class IntentProcessor {

    // Specialized processors for different domains
    private static final StakeIntentProcessor stakeProcessor = new StakeIntentProcessor();

    /**
     * Process all intentions for a transaction and populate internal state.
     *
     * @param tx The transaction to populate
     * @param intentions List of intentions to process
     */
    public static void processIntentions(AbstractTx<?> tx, List<TxIntention> intentions) {
        processIntentions(tx, intentions, null);
    }

    /**
     * Process all intentions for a transaction and populate internal state with variable resolution.
     *
     * @param tx The transaction to populate
     * @param intentions List of intentions to process
     * @param variables Variables map for resolving variable references (may be null)
     */
    public static void processIntentions(AbstractTx<?> tx, List<TxIntention> intentions, Map<String, Object> variables) {
        for (TxIntention intention : intentions) {
            String intentionType = intention.getType();

            // Route to specialized processors first
            if (stakeProcessor.canProcess(intentionType)) {
                stakeProcessor.process(intention, tx, variables);
            } else {
                // Handle core intentions directly
                // TODO: Update these processors to handle variable resolution in future phases
                switch (intentionType) {
                    case "payment":
                        processPaymentIntention((PaymentIntention) intention, tx);
                        break;
                    case "donation":
                        processDonationIntention((DonationIntention) intention, tx);
                        break;
                    case "minting":
                        processMintingIntention((MintingIntention) intention, tx);
                        break;
                    case "script_collect_from":
                        processScriptCollectIntention((ScriptCollectFromIntention) intention, tx);
                        break;
                    case "metadata":
                        processMetadataIntention((MetadataIntention) intention, tx);
                        break;

                    // Governance intentions - delegate to specialized processor
                    case "drep_registration":
                    case "drep_deregistration":
                    case "drep_update":
                    case "governance_proposal":
                    case "voting":
                    case "voting_delegation":
                        GovernanceIntentProcessor.process(intention, tx, null);
                        break;

                    default:
                        throw new TxBuildException("Unknown intention type: " + intentionType);
                }
            }
        }
    }

    /**
     * Process a payment intention by creating a TransactionOutput.
     */
    private static void processPaymentIntention(PaymentIntention intention, AbstractTx<?> tx) {
        String address = intention.getAddress();
        List<Amount> amounts = intention.getAmounts();

        // Create transaction output
        TransactionOutput transactionOutput = TransactionOutput.builder()
            .address(address)
            .value(Value.builder().coin(BigInteger.ZERO).build())
            .build();

        // Process amounts
        for (Amount amount : amounts) {
            String unit = amount.getUnit();
            if (unit.equals(LOVELACE)) {
                transactionOutput.getValue().setCoin(amount.getQuantity());
            } else {
                Tuple<String, String> policyAssetName = AssetUtil.getPolicyIdAndAssetName(unit);
                Asset asset = new Asset(policyAssetName._2, amount.getQuantity());
                MultiAsset multiAsset = new MultiAsset(policyAssetName._1, List.of(asset));
                Value newValue = transactionOutput.getValue().add(new Value(BigInteger.ZERO, List.of(multiAsset)));
                transactionOutput.setValue(newValue);
            }
        }

        // Set datum if present (priority: runtime objects > serialized hex)
        if (intention.getDatum() != null) {
            // Use runtime PlutusData directly
            transactionOutput.setInlineDatum(intention.getDatum());
        } else if (intention.getDatumHashBytes() != null) {
            // Use runtime datum hash bytes
            transactionOutput.setDatumHash(intention.getDatumHashBytes());
        } else if (intention.getDatumHex() != null && !intention.getDatumHex().isEmpty()) {
            // Fall back to deserializing hex string for inline datum
            try {
                PlutusData datum = PlutusData.deserialize(HexUtil.decodeHexString(intention.getDatumHex()));
                transactionOutput.setInlineDatum(datum);
            } catch (Exception e) {
                throw new TxBuildException("Failed to deserialize datum hex", e);
            }
        } else if (intention.getDatumHash() != null && !intention.getDatumHash().isEmpty()) {
            // Fall back to deserializing hex string for datum hash
            transactionOutput.setDatumHash(HexUtil.decodeHexString(intention.getDatumHash()));
        }

        // Set script reference if present (priority: runtime objects > serialized hex)
        if (intention.getScriptRefBytes() != null) {
            // Use runtime byte array directly
            transactionOutput.setScriptRef(intention.getScriptRefBytes());
        } else if (intention.getRefScript() != null) {
            // Use runtime Script object's scriptRefBytes
            try {
                transactionOutput.setScriptRef(intention.getRefScript().scriptRefBytes());
            } catch (Exception e) {
                throw new TxBuildException("Failed to get script reference bytes from refScript", e);
            }
        } else if (intention.getScript() != null) {
            // Use runtime Script object's scriptRefBytes
            try {
                transactionOutput.setScriptRef(intention.getScript().scriptRefBytes());
            } catch (Exception e) {
                throw new TxBuildException("Failed to get script reference bytes from script", e);
            }
        } else if (intention.getScriptRefBytesHex() != null && !intention.getScriptRefBytesHex().isEmpty()) {
            // Fall back to deserializing hex string
            try {
                byte[] scriptRefBytes = HexUtil.decodeHexString(intention.getScriptRefBytesHex());
                transactionOutput.setScriptRef(scriptRefBytes);
            } catch (Exception e) {
                throw new TxBuildException("Failed to decode script reference hex", e);
            }
        }

        // Add to transaction outputs
        if (tx.outputs == null) {
            tx.outputs = new ArrayList<>();
        }
        tx.outputs.add(transactionOutput);
    }

    /**
     * Process a donation intention by setting donation context.
     */
    private static void processDonationIntention(DonationIntention intention, AbstractTx<?> tx) {
        if (tx.donationContext != null) {
            throw new TxBuildException("Can't donate to treasury multiple times in a single transaction");
        }

        tx.donationContext = new AbstractTx.DonationContext(
            intention.getCurrentTreasuryValueAsBigInt(),
            intention.getDonationAmountAsBigInt()
        );
    }

    /**
     * Process a minting intention by creating multiasset and optional payment output.
     */
    private static void processMintingIntention(MintingIntention intention, AbstractTx<?> tx) {
        try {
            // Deserialize script from hex
            int scriptType = intention.getScriptType();
            Script script = null;
            switch (scriptType) {
                case 0:
                    var array = (Array)CborSerializationUtil.deserialize(HexUtil.decodeHexString(intention.getScriptHex()));
                    script = NativeScript.deserialize(array);
                    break;
                case 1:
                    var byteString = (ByteString)CborSerializationUtil.deserialize(HexUtil.decodeHexString(intention.getScriptHex()));
                    script = PlutusV1Script.deserialize(byteString);
                    break;
                case 2:
                    var byteString2 = (ByteString)CborSerializationUtil.deserialize(HexUtil.decodeHexString(intention.getScriptHex()));
                    script = PlutusV2Script.deserialize(byteString2);
                    break;
                case 3:
                    var byteString3 = (ByteString)CborSerializationUtil.deserialize(HexUtil.decodeHexString(intention.getScriptHex()));
                    script = PlutusV3Script.deserialize(byteString3);
                    break;
                default:
                    throw new TxBuildException("Invalid script type for minting intention: " + scriptType);
            }
            String policyId = script.getPolicyId();

            // If receiver is specified, create outputs for minted assets
            if (intention.hasReceiver()) {
                for (Asset asset : intention.getAssets()) {
                    Amount amount = new Amount(AssetUtil.getUnit(policyId, asset), asset.getValue());

                    // Create a payment intention and process it
                    PaymentIntention paymentIntention = PaymentIntention.builder()
                        .address(intention.getReceiver())
                        .amounts(List.of(amount))
                        .build();
                    processPaymentIntention(paymentIntention, tx);
                }
            }

            // Add to multiAsset list for minting
            addToMultiAssetList(tx, script, intention.getAssets());

        } catch (Exception e) {
            throw new TxBuildException("Failed to process minting intention", e);
        }
    }

    /**
     * Process a script collect intention by adding UTXOs and redeemers.
     */
    private static void processScriptCollectIntention(ScriptCollectFromIntention intention, AbstractTx<?> tx) {
        if (!(tx instanceof ScriptTx)) {
            throw new TxBuildException("Script collect intention can only be used with ScriptTx");
        }

        ScriptTx scriptTx = (ScriptTx) tx;

        // Check if intention has runtime objects
        if (intention.hasRuntimeObjects()) {
            // Use original UTXOs directly
            List<Utxo> utxos = intention.getUtxos();
            if (scriptTx.inputUtxos == null) {
                scriptTx.inputUtxos = new ArrayList<>();
            }
            scriptTx.inputUtxos.addAll(utxos);

            // Process redeemer and datum if present
            if (intention.getRedeemerData() != null || intention.getDatum() != null) {
                // Create SpendingContext for each UTXO with redeemer/datum
                for (Utxo utxo : utxos) {
                    createSpendingContext(scriptTx, utxo, intention.getRedeemerData(), intention.getDatum());
                }
            }
        } else if (intention.needsUtxoResolution()) {
            // Handle deserialized intentions (from YAML/JSON)
            // For now, create partial UTXOs - full resolution will be handled in Phase 4
            List<ScriptCollectFromIntention.UtxoRef> utxoRefs = intention.getUtxoRefs();
            if (utxoRefs != null) {
                for (ScriptCollectFromIntention.UtxoRef utxoRef : utxoRefs) {
                    // TODO: In Phase 4, use UtxoResolver to fetch full UTXO data
                    // For now, create partial UTXO (without amount data)
                    Utxo utxo = createUtxoFromRef(utxoRef);

                    if (scriptTx.inputUtxos == null) {
                        scriptTx.inputUtxos = new ArrayList<>();
                    }
                    scriptTx.inputUtxos.add(utxo);

                    // Process hex-encoded redeemer and datum
                    PlutusData redeemerData = null;
                    PlutusData datum = null;

                    if (intention.getRedeemerHex() != null && !intention.getRedeemerHex().isEmpty()) {
                        try {
                            redeemerData = PlutusData.deserialize(HexUtil.decodeHexString(intention.getRedeemerHex()));
                        } catch (Exception e) {
                            throw new TxBuildException("Failed to deserialize redeemer data", e);
                        }
                    }

                    if (intention.getDatumHex() != null && !intention.getDatumHex().isEmpty()) {
                        try {
                            datum = PlutusData.deserialize(HexUtil.decodeHexString(intention.getDatumHex()));
                        } catch (Exception e) {
                            throw new TxBuildException("Failed to deserialize datum", e);
                        }
                    }

                    if (redeemerData != null || datum != null) {
                        createSpendingContext(scriptTx, utxo, redeemerData, datum);
                    }
                }
            }
        }
    }

    /**
     * Create a SpendingContext and add it to ScriptTx.
     */
    private static void createSpendingContext(ScriptTx scriptTx, Utxo utxo, PlutusData redeemerData, PlutusData datum) {
        if (redeemerData != null) {
            // Create redeemer
            Redeemer redeemer = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .data(redeemerData)
                .index(BigInteger.valueOf(0)) // Will be adjusted later
                .exUnits(ExUnits.builder()
                    .mem(BigInteger.valueOf(10000)) // Dummy values, will be calculated
                    .steps(BigInteger.valueOf(10000))
                    .build())
                .build();

            // Add spending context
            ScriptTx.SpendingContext spendingContext = new ScriptTx.SpendingContext(utxo, redeemer, datum);
            if (scriptTx.spendingContexts == null) {
                scriptTx.spendingContexts = new ArrayList<>();
            }
            scriptTx.spendingContexts.add(spendingContext);
        }
    }

    /**
     * Create a Utxo object from UtxoRef.
     * Note: This creates a partial UTXO without amount data.
     * Full UTXO resolution should be done via UtxoResolver in Phase 4.
     */
    private static Utxo createUtxoFromRef(ScriptCollectFromIntention.UtxoRef utxoRef) {
        Utxo utxo = new Utxo();
        utxo.setTxHash(utxoRef.getTxHash());
        utxo.setOutputIndex(utxoRef.getOutputIndex());
        utxo.setAddress(utxoRef.getAddress());
        // TODO: Phase 4 - Resolve full UTXO data including amounts from blockchain
        return utxo;
    }

    /**
     * Add script and assets to the multiAsset list (similar to AbstractTx.addToMultiAssetList).
     */
    private static void addToMultiAssetList(AbstractTx<?> tx, Script script, List<Asset> assets) {
        try {
            String policyId = script.getPolicyId();
            MultiAsset multiAsset = MultiAsset.builder()
                .policyId(policyId)
                .assets(assets)
                .build();

            if (tx.multiAssets == null) {
                tx.multiAssets = new ArrayList<>();
            }

            // Check if multiasset already exists with same policy id
            tx.multiAssets.stream()
                .filter(ma -> {
                    try {
                        return ma._1.getPolicyId().equals(policyId);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .findFirst()
                .ifPresentOrElse(ma -> {
                    // Update existing multiasset
                    tx.multiAssets.remove(ma);
                    tx.multiAssets.add(new Tuple<>(script, ma._2.add(multiAsset)));
                }, () -> {
                    // Add new multiasset
                    tx.multiAssets.add(new Tuple<>(script, multiAsset));
                });

        } catch (Exception e) {
            throw new TxBuildException("Failed to add multiasset", e);
        }
    }

    /**
     * Process a metadata intention by merging metadata into the transaction.
     */
    private static void processMetadataIntention(MetadataIntention intention, AbstractTx<?> tx) {
        try {
            Metadata resolvedMetadata = null;

            // Use runtime object if available
            if (intention.hasRuntimeObjects()) {
                resolvedMetadata = intention.getMetadata();
            }
            // Priority 1: Deserialize from CBOR hex (lossless round-trip)
            else if (intention.hasCborHex()) {
                byte[] cborBytes = HexUtil.decodeHexString(intention.getMetadataCborHex());
                resolvedMetadata = com.bloxbean.cardano.client.metadata.MetadataBuilder.deserialize(cborBytes);
            }
            // Priority 2: Deserialize from JSON (fallback)
            else if (intention.hasJson()) {
                resolvedMetadata = com.bloxbean.cardano.client.metadata.MetadataBuilder.metadataFromJson(intention.getMetadataJson());
            }

            if (resolvedMetadata != null) {
                // Merge with existing metadata if present
                if (tx.txMetadata == null) {
                    tx.txMetadata = resolvedMetadata;
                } else {
                    tx.txMetadata = tx.txMetadata.merge(resolvedMetadata);
                }
            }

        } catch (Exception e) {
            throw new TxBuildException("Failed to process metadata intention", e);
        }
    }
}
