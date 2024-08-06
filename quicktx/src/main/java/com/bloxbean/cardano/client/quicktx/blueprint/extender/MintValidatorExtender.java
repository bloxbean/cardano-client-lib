package com.bloxbean.cardano.client.quicktx.blueprint.extender;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.blueprint.model.Data;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.plutus.spec.PlutusV3Script;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.MintAsset;
import com.bloxbean.cardano.client.transaction.spec.Asset;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Extender for minting assets using Plutus scripts
 * @param <T>
 */
public interface MintValidatorExtender<T> extends DeployValidatorExtender {

    //-- mintTx methods

    /**
     * Create a {@link ScriptTx} to mint the assets. This will create a {@link ScriptTx} which can be composed with other Tx to create the
     * final transaction through {@link QuickTxBuilder#compose}}
     * @param redeemer Redeemer data
     * @param mintReceivers Mint assets and receivers details
     * @return ScriptTx
     */
    default ScriptTx mintTx(Data redeemer, MintAsset... mintReceivers) {
        List<Asset> assets = Arrays.stream(mintReceivers)
                .map(mintReceiver -> new Asset(mintReceiver.getAssetName(), mintReceiver.getQuantity()))
                .collect(Collectors.toList());

        List<Asset> mintAssets = assets.stream()
                .collect(Collectors.groupingBy(Asset::getName,
                        Collectors.mapping(
                                asset -> asset,
                                Collectors.reducing((a, b) ->
                                        new Asset(a.getName(), a.getValue().add(b.getValue()))
                                )
                        )
                ))
                .values()
                .stream()
                .map(Optional::get)
                .collect(Collectors.toList());

        var scriptTx = new ScriptTx()
                .mintAsset(getPlutusScript(), mintAssets, redeemer.toPlutusData());

        for (MintAsset receiver : mintReceivers) {
            String unit = AssetUtil.getUnit(getPolicyId(), receiver.getAssetName());
            var amount = Amount.asset(unit, receiver.getQuantity());
            if (receiver.getReceiverDatum() != null) {
                scriptTx.payToContract(receiver.getReceiver(), amount, receiver.getReceiverDatum());
            } else {
                scriptTx.payToAddress(receiver.getReceiver(), amount);
            }
        }

        if (getReferenceTxInput() != null) {
            scriptTx.readFrom(getReferenceTxInput()._1, getReferenceTxInput()._2);
        }

        return scriptTx;
    }

    /**
     * Create a {@link ScriptTx} to mint the asset and send to a receiver address.
     * This will create a {@link ScriptTx} which can be composed with other Tx to create the final transaction
     * through {@link QuickTxBuilder#compose}
     *
     * @param redeemer Redeemer data
     * @param asset Asset to mint
     * @param receiver Receiver address
     * @return ScriptTx
     */
    default ScriptTx mintToAddressTx(Data redeemer, Asset asset, String receiver) {
        return mintToAddressTx(redeemer, List.of(asset), receiver);
    }

    /**
     * Create a {@link ScriptTx} to mint the assets and send to a receiver address.
     * This will create a {@link ScriptTx} which can be composed with other Tx to create the final transaction
     * through {@link QuickTxBuilder#compose}
     *
     * @param redeemer Redeemer data
     * @param assets Assets to mint
     * @param receiver Receiver address
     * @return ScriptTx
     */
    default ScriptTx mintToAddressTx(Data redeemer, List<Asset> assets, String receiver) {
        var mintAssets = assets.stream().map(asset -> MintAsset.builder()
                .assetName(asset.getName())
                .quantity(asset.getValue())
                .receiver(receiver).build())
                .toArray(MintAsset[]::new);

        return mintTx(redeemer, mintAssets);
    }

    /**
     * Create a {@link ScriptTx} to mint the assets and send to a receiver script address.
     * This will create a {@link ScriptTx} which can be composed with other Tx to create the final transaction
     * through {@link QuickTxBuilder#compose}
     *
     * @param redeemer Redeemer data
     * @param asset Asset to mint
     * @param receiverContractAddress Receiver script address
     * @param outputDatum Output datum
     * @return ScriptTx
     */
    default ScriptTx mintToContractTx(Data redeemer, Asset asset, String receiverContractAddress, Data outputDatum) {
        return mintToContractTx(redeemer, List.of(asset), receiverContractAddress, outputDatum);
    }

    /**
     * Create a {@link ScriptTx} to mint the assets and send to a receiver script address.
     * This will create a {@link ScriptTx} which can be composed with other Tx to create the final transaction
     * through {@link QuickTxBuilder#compose}
     *
     * @param redeemer Redeemer data
     * @param asset Asset to mint
     * @param receiverContractAddress Contract address
     * @param outputDatum Output datum
     * @return ScriptTx
     */
    default ScriptTx mintToContractTx(Data redeemer, Asset asset, String receiverContractAddress, PlutusData outputDatum) {
        return mintToContractTx(redeemer, List.of(asset), receiverContractAddress, outputDatum);
    }

    /**
     * Create a {@link ScriptTx} to mint the assets and send to a receiver script address.
     * This will create a {@link ScriptTx} which can be composed with other Tx to create the final transaction
     * through {@link QuickTxBuilder#compose}
     *
     * @param redeemer Redeemer data
     * @param assets Assets to mint
     * @param receiverContractAddress Contract address
     * @param outputDatum Output datum
     * @return ScriptTx
     */
    default ScriptTx mintToContractTx(Data redeemer, List<Asset> assets, String receiverContractAddress, Data outputDatum) {
        return mintToContractTx(redeemer, assets, receiverContractAddress, outputDatum != null? outputDatum.toPlutusData(): null);
    }

    /**
     * Create a {@link ScriptTx} to mint the assets and send to a receiver script address.
     * This will create a {@link ScriptTx} which can be composed with other Tx to create the final transaction
     * through {@link QuickTxBuilder#compose}
     *
     * @param redeemer Redeemer data
     * @param assets Assets to mint
     * @param receiverContractAddress Contract address
     * @param outputDatum Output datum
     * @return ScriptTx
     */
    default ScriptTx mintToContractTx(Data redeemer, List<Asset> assets, String receiverContractAddress, PlutusData outputDatum) {
        var mintAssets = assets.stream().map(asset -> MintAsset.builder()
                .assetName(asset.getName())
                .quantity(asset.getValue())
                .receiver(receiverContractAddress)
                .receiverDatum(outputDatum)
                .build()).toArray(MintAsset[]::new);

        return mintTx(redeemer, mintAssets);
    }


    //-- mint methods

    /**
     * Create a {@link QuickTxBuilder.TxContext} to mint the assets. This will create a {@link QuickTxBuilder.TxContext}
     * which can be used to sign and submit the transaction.
     * @param redeemer Redeemer data
     * @param mintReceivers Mint assets and receivers details
     * @return QuickTxBuilder.TxContext
     */
    default QuickTxBuilder.TxContext mint(Data redeemer, MintAsset... mintReceivers) {
        requireSuppliersNullCheck();

        var scriptTx = mintTx(redeemer, mintReceivers);

        QuickTxBuilder quickTxBuilder =
                new QuickTxBuilder(getUtxoSupplier(), getProtocolParamsSupplier(), getTransactionProcessor());

        var txContext = quickTxBuilder.compose(scriptTx);

        if (getTransactionEvaluator() != null) {
            txContext.withTxEvaluator(getTransactionEvaluator());
        }

        if (getReferenceTxInput() != null) {
            var plutusScript = getPlutusScript();
            txContext.withReferenceScripts(plutusScript);

            //Remove the plutus script from the witness set as it is already included in the reference input
            //This should be done in QuickTx automatically. But doing it here as a workaround
            txContext.preBalanceTx((context, txn) -> {
                if (plutusScript instanceof PlutusV2Script)
                    txn.getWitnessSet().getPlutusV2Scripts().remove(plutusScript);
                else if (getPlutusScript() instanceof PlutusV3Script)
                    txn.getWitnessSet().getPlutusV3Scripts().remove(plutusScript);

            });
        }

        return txContext;
    }

    /**
     * Create a {@link QuickTxBuilder.TxContext} to mint the asset and send to a receiver address.
     * This will create a {@link QuickTxBuilder.TxContext} which can be used to sign and submit the transaction.
     *
     * @param redeemer Redeemer data
     * @param asset Asset to mint
     * @param receiver Receiver address
     * @return QuickTxBuilder.TxContext
     */
    default QuickTxBuilder.TxContext mintToAddress(Data redeemer, Asset asset, String receiver) {
        return mintToAddress(redeemer, List.of(asset), receiver);
    }

    /**
     * Create a {@link QuickTxBuilder.TxContext} to mint the assets and send to a receiver address.
     * This will create a {@link QuickTxBuilder.TxContext} which can be used to sign and submit the transaction.
     *
     * @param redeemer Redeemer data
     * @param assets Assets to mint
     * @param receiver Receiver address
     * @return QuickTxBuilder.TxContext
     */
    default QuickTxBuilder.TxContext mintToAddress(Data redeemer, List<Asset> assets, String receiver) {
        var mintAssets = assets.stream().map(asset -> MintAsset.builder()
                .assetName(asset.getName())
                .quantity(asset.getValue())
                .receiver(receiver).build())
                .toArray(MintAsset[]::new);

        return mint(redeemer, mintAssets);
    }

    /**
     * Create a {@link QuickTxBuilder.TxContext} to mint the asset and send to a receiver script address.
     * This will create a {@link QuickTxBuilder.TxContext} which can be used to sign and submit the transaction.
     *
     * @param redeemer Redeemer data
     * @param asset Asset to mint
     * @param receiverScriptAddress Receiver script address
     * @param outputDatum Output datum
     * @return QuickTxBuilder.TxContext
     */
    default QuickTxBuilder.TxContext mintToContract(Data redeemer, Asset asset, String receiverScriptAddress, Data outputDatum) {
        return mintToContract(redeemer, List.of(asset), receiverScriptAddress, outputDatum != null? outputDatum.toPlutusData(): null);
    }

    /**
     * Create a {@link QuickTxBuilder.TxContext} to mint the asset and send to a receiver script address.
     * This will create a {@link QuickTxBuilder.TxContext} which can be used to sign and submit the transaction.
     *
     * @param redeemer Redeemer data
     * @param asset Asset to mint
     * @param receiverScriptAddress Receiver script address
     * @param outputDatum Output datum
     * @return QuickTxBuilder.TxContext
     */
    default QuickTxBuilder.TxContext mintToContract(Data redeemer, Asset asset, String receiverScriptAddress, PlutusData outputDatum) {
        return mintToContract(redeemer, List.of(asset), receiverScriptAddress, outputDatum);
    }

    /**
     * Create a {@link QuickTxBuilder.TxContext} to mint the assets and send to a receiver script address.
     * This will create a {@link QuickTxBuilder.TxContext} which can be used to sign and submit the transaction.
     *
     * @param redeemer Redeemer data
     * @param assets Assets to mint
     * @param receiverScriptAddress Receiver script address
     * @param outputDatum Output datum
     * @return QuickTxBuilder.TxContext
     */
    default QuickTxBuilder.TxContext mintToContract(Data redeemer, List<Asset> assets, String receiverScriptAddress, Data outputDatum) {
        return mintToContract(redeemer, assets, receiverScriptAddress, outputDatum != null? outputDatum.toPlutusData(): null);
    }

    /**
     * Create a {@link QuickTxBuilder.TxContext} to mint the assets and send to a receiver script address.
     * This will create a {@link QuickTxBuilder.TxContext} which can be used to sign and submit the transaction.
     *
     * @param redeemer Redeemer data
     * @param assets Assets to mint
     * @param receiverScriptAddress Receiver script address
     * @param outputDatum Output datum
     * @return QuickTxBuilder.TxContext
     */
    default QuickTxBuilder.TxContext mintToContract(Data redeemer, List<Asset> assets, String receiverScriptAddress, PlutusData outputDatum) {
        var mintAssets = assets.stream().map(asset -> MintAsset.builder()
                        .assetName(asset.getName())
                        .quantity(asset.getValue())
                        .receiver(receiverScriptAddress)
                        .receiverDatum(outputDatum)
                        .build()).toArray(MintAsset[]::new);

        return mint(redeemer, mintAssets);
    }

    /**
     * Get the policy id of the script
     * @return policy id
     * @throws CborRuntimeException
     */
    default String getPolicyId() {
        try {
            return getPlutusScript().getPolicyId();
        } catch (CborSerializationException e) {
            throw new CborRuntimeException(e.getMessage(), e);
        }
    }
}
