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

    private int additionalSignersCount = 0;
    private boolean senderAdded = false;

    /**
     * Create Tx
     */
    public Tx() {
        txBuilder = (context, txn) -> {
        };
        txOutputBuilder = (context, txn) -> {
        };
    }

    /**
     * Create Tx with a sender address. The application needs to provide the signer for this sender address.
     * A Tx object can have only one sender. This method should be called after all outputs are defined.
     * @param sender
     * @return Tx
     */
    public Tx from(String sender) {
        verifySenderExists();
        if (senderAccount != null)
            throw new IllegalStateException("Sender and senderAccount cannot be set at the same time");

        this.txBuilder = txOutputBuilder.buildInputs(InputBuilders
                .createFromSender(sender, sender));

        this.sender = sender;
        return this;
    }

    /**
     * Create Tx with a sender account. The builder will automatically use the signer from the account.
     * A Tx object can have only one sender. This method should be called after all outputs are defined.
     * @param account
     * @return Tx
     */
    public Tx from(Account account) {
        verifySenderExists();
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

    private void verifySenderExists() {
        if (senderAdded)
            throw new TxBuildException("Sender already added. Cannot add additional sender");
    }

    /**
     * Add a signer to the transaction. This method can be called multiple times to add multiple signers.
     * @param signer TxSigner
     * @return Tx
     */
    public Tx withSigner(@NonNull TxSigner signer) {
        additionalSignersCount++;
        if (this.txSigner == null)
            this.txSigner = signer;
        else
            this.txSigner = this.txSigner.andThen(signer);
        return this;
    }

    /**
     * This is an optional method to set additional signers count. This is useful when you have multiple additional composite signers and calculating
     * total additional signers count is not possible automatically by the builder.
     * <br>
     * For example, if you have added 1 additional signer with two TxSigner instance composed together,
     * you can set the additional signers count to 2.
     * @return Tx
     */
    public Tx additionalSignersCount(int additionalSigners) {
        this.additionalSignersCount = this.additionalSignersCount;
        return this;
    }

    /**
     * Add an output to the transaction. This method can be called multiple times to add multiple outputs.
     * @param address Address to send the output
     * @param amount Amount to send
     * @return Tx
     */
    public Tx payToAddress(String address, Amount amount) {
        return payToAddress(address, List.of(amount), false);
    }

    /**
     * Add an output to the transaction. This method can be called multiple times to add multiple outputs.
     * This method is useful for newly minted asset in the transaction.
     *
     * @param address Address to send the output
     * @param amount Amount to send
     * @param mintOutput If the asset in the output will be minted in this transaction, set this to true, otherwise false
     * @return Tx
     */
    public Tx payToAddress(String address, Amount amount, boolean mintOutput) {
        return payToAddress(address, List.of(amount), mintOutput);
    }

    /**
     * Add an output to the transaction. This method can be called multiple times to add multiple outputs.
     * @param address Address to send the output
     * @param amounts List of Amount to send
     * @return Tx
     */
    public Tx payToAddress(String address, List<Amount> amounts) {
        return payToAddress(address, amounts, false);
    }

    /**
     * Add an output to the transaction. This method can be called multiple times to add multiple outputs.
     * This method is useful for newly minted assets in the transaction.
     * @param address address
     * @param amounts List of Amount to send
     * @param mintOutput If the assets in the output will be minted in this transaction, set this to true, otherwise false
     * @return
     */
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

    /**
     * Add metadata to the transaction.
     * @param metadata
     * @return Tx
     */
    public Tx attachMetadata(Metadata metadata) {
        this.txBuilder = this.txBuilder.andThen(AuxDataProviders.metadataProvider(metadata));
        return this;
    }

    /**
     * Add a mint asset to the transaction. The newly minted asset will be transferred to the defined receivers in payToAddress methods.
     * @param script Policy script
     * @param asset Asset to mint
     * @return Tx
     */
    public Tx mintAssets(@NonNull Script script, Asset asset) {
        return mintAssets(script, List.of(asset), null);
    }

    /**
     * Add a mint asset to the transaction. The newly minted asset will be transferred to the receiver address.
     * @param script Policy script
     * @param asset Asset to mint
     * @param receiver Receiver address
     * @return Tx
     */
    public Tx mintAssets(@NonNull Script script, Asset asset, String receiver) {
        return mintAssets(script, List.of(asset), receiver);
    }

    /**
     * Add mint assets to the transaction. The newly minted assets will be transferred to the defined receivers in payToAddress methods.
     * @param script Policy script
     * @param assets List of assets to mint
     * @return Tx
     */
    public Tx mintAssets(@NonNull Script script, List<Asset> assets) {
        return mintAssets(script, assets, null);
    }

    /**
     * Add mint assets to the transaction. The newly minted assets will be transferred to the receiver address.
     * @param script Policy script
     * @param assets List of assets to mint
     * @param receiver Receiver address
     * @return Tx
     */
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

    /**
     * TxBuilder instance
     * @return
     */
    TxBuilder txBuilder() {
        return txBuilder;
    }

    /**
     * Final TxSigner instance
     * @return
     */
    TxSigner txSigner() {
        return txSigner;
    }

    /**
     * Total no of additional signers defined in this Tx
     * @return int
     */
    int additionalSignersCount() {
        return additionalSignersCount;
    }

    /**
     * Sender address
     * @return String
     */
    String sender() {
        if (sender != null)
            return sender;
        else if (senderAccount != null)
            return senderAccount.baseAddress();
        else
            return null;
    }
}
