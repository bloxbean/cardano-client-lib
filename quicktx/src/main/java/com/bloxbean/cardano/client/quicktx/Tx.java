package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.function.Output;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.TxSigner;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.AuxDataProviders;
import com.bloxbean.cardano.client.function.helper.InputBuilders;
import com.bloxbean.cardano.client.function.helper.MintCreators;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.spec.Script;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.util.Tuple;
import lombok.NonNull;

import java.util.List;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

/**
 * Tx class to build transaction
 */
public class Tx {
    protected TxBuilder txBuilder;
    protected TxOutputBuilder txOutputBuilder;
    private TxSigner txSigner;
    private String sender;
    private Account senderAccount;

    private int additionalSigner = 0;

    public Tx() {
        txBuilder = (context, txn) -> {
        };
        txOutputBuilder = (context, txn) -> {
        };
    }

    public Tx withSender(String sender) {
        if (senderAccount != null)
            throw new IllegalStateException("Sender and senderAccount cannot be set at the same time");

        this.txBuilder = txOutputBuilder.buildInputs(InputBuilders
                .createFromSender(sender, sender));

        this.sender = sender;
        return this;
    }

    public Tx withSender(Account account) {
        if (sender != null)
            throw new IllegalStateException("Sender and senderAccount cannot be set at the same time");

        this.txBuilder = txBuilder.andThen(txOutputBuilder.buildInputs(InputBuilders
                .createFromSender(account.baseAddress(), account.baseAddress())));
        this.senderAccount = account;

        if (txSigner == null)
            this.txSigner = SignerProviders.signerFrom(account);
        else
            this.txSigner = txSigner.andThen(SignerProviders.signerFrom(account));
        return this;
    }

    public Tx withSigner(@NonNull TxSigner signer) {
        additionalSigner++;
        if (this.txSigner == null)
            this.txSigner = signer;
        else
            this.txSigner = this.txSigner.andThen(signer);
        return this;
    }

    public Tx payToAddress(String address, Amount amount) {
        return payToAddress(address, List.of(amount), false);
    }

    public Tx payToAddress(String address, Amount amount, boolean mintOutput) {
        return payToAddress(address, List.of(amount), mintOutput);
    }

    public Tx payToAddress(String address, List<Amount> amounts) {
        return payToAddress(address, amounts, false);
    }

    public Tx payToAddress(String address, List<Amount> amounts, boolean mintOutput) {
        for (Amount amount : amounts) {
            String unit = amount.getUnit();
            Output output;
            if (unit.equals(LOVELACE)) {
                output = Output.builder()
                        .address(address)
                        .assetName(LOVELACE)
                        .qty(amount.getQuantity())
                        .build();
            } else {
                Tuple<String, String> policyAssetName = AssetUtil.getPolicyIdAndAssetName(unit);
                output = Output.builder()
                        .address(address)
                        .policyId(policyAssetName._1)
                        .assetName(policyAssetName._2)
                        .qty(amount.getQuantity())
                        .build();
            }

            if (mintOutput)
                txOutputBuilder = txOutputBuilder.and(output.mintOutputBuilder());
            else
                txOutputBuilder = txOutputBuilder.and(output.outputBuilder());
        }

        return this;
    }

    public Tx attachMetadata(Metadata metadata) {
        this.txBuilder = this.txBuilder.andThen(AuxDataProviders.metadataProvider(metadata));
        return this;
    }

    public Tx mintAssets(@NonNull Script script, Asset asset) {
        return mintAssets(script, List.of(asset), null);
    }

    public Tx mintAssets(@NonNull Script script, Asset asset, String receiver) {
        return mintAssets(script, List.of(asset), receiver);
    }

    public Tx mintAssets(@NonNull Script script, List<Asset> assets) {
        return mintAssets(script, assets, null);
    }

    public Tx mintAssets(@NonNull Script script, List<Asset> assets, String receiver) {
        try {
            String policyId = script.getPolicyId();

            if (receiver != null) { //If receiver address is defined
                assets.forEach(asset -> {
                    payToAddress(receiver,
                            List.of(new Amount(AssetUtil.getUnit(policyId, asset), asset.getValue())), true);
                });
            }

            MultiAsset multiAsset = MultiAsset.builder()
                    .policyId(policyId)
                    .assets(assets)
                    .build();
            this.txBuilder = txBuilder.andThen(MintCreators.mintCreator(script, multiAsset));
        } catch (Exception e) {
            throw new TxBuildException(e);
        }

        return this;
    }

    TxBuilder txBuilder() {
        return txBuilder;
    }

    TxSigner txSigner() {
        return txSigner;
    }

    int additionalSigner() {
        return additionalSigner;
    }

    String sender() {
        if (sender != null)
            return sender;
        else if (senderAccount != null)
            return senderAccount.baseAddress();
        else
            return null;
    }
}
