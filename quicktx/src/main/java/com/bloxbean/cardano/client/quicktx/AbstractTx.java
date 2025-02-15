package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.AuxDataProviders;
import com.bloxbean.cardano.client.function.helper.InputBuilders;
import com.bloxbean.cardano.client.function.helper.MintCreators;
import com.bloxbean.cardano.client.function.helper.OutputBuilders;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.spec.Script;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.Tuple;
import lombok.AllArgsConstructor;
import lombok.Getter;
import com.bloxbean.cardano.hdwallet.Wallet;
import lombok.NonNull;
import lombok.SneakyThrows;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

public abstract class AbstractTx<T> {
    public static final String DUMMY_TREASURY_ADDRESS = "_TREASURY_ADDRESS_";
    protected List<TransactionOutput> outputs;
    protected List<Tuple<Script, MultiAsset>> multiAssets;
    protected Metadata txMetadata;
    //custom change address
    protected String changeAddress;
    protected List<Utxo> inputUtxos;

    //Required for script
    protected PlutusData changeData;
    protected String changeDatahash;

    protected List<DepositRefundContext> depositRefundContexts;
    protected DonationContext donationContext;

    /**
     * Add an output to the transaction. This method can be called multiple times to add multiple outputs.
     *
     * @param address Address to send the output
     * @param amount  Amount to send
     * @return T
     */
    public T payToAddress(String address, Amount amount) {
        return payToAddress(address, List.of(amount), null, null, null, null);
    }

    /**
     * Add an output to the transaction. This method can be called multiple times to add multiple outputs.
     *
     * @param address Address to send the output
     * @param amounts List of Amount to send
     * @return T
     */
    public T payToAddress(String address, List<Amount> amounts) {
        return payToAddress(address, amounts, null, null, null, null);
    }

    /**
     * Add an output to the transaction. This method can be called multiple times to add multiple outputs.
     *
     * @param address address
     * @param amounts List of Amount to send
     * @param script  Reference Script
     * @return T
     */
    public T payToAddress(String address, List<Amount> amounts, Script script) {
        return payToAddress(address, amounts, null, null, script, null);
    }

    /**
     * Add an output to the transaction. This method can be called multiple times to add multiple outputs.
     *
     * @param address address
     * @param amount  Amount to send
     * @param script  Reference Script
     * @return T
     */
    public T payToAddress(String address, Amount amount, Script script) {
        return payToAddress(address, List.of(amount), null, null, script, null);
    }

    /**
     * Add an output to the transaction. This method can be called multiple times to add multiple outputs.
     *
     * @param address        address
     * @param amounts        List of Amount to send
     * @param scriptRefBytes Reference Script bytes
     * @return T
     */
    public T payToAddress(String address, List<Amount> amounts, byte[] scriptRefBytes) {
        return payToAddress(address, amounts, null, null, null, scriptRefBytes);
    }

    /**
     * Add an output at contract address with amount and inline datum.
     *
     * @param address contract address
     * @param amount  amount
     * @param datum   inline datum
     * @return T
     */
    public T payToContract(String address, Amount amount, PlutusData datum) {
        return payToAddress(address, List.of(amount), null, datum, null, null);
    }

    /**
     * Add an output at contract address with amounts and inline datum.
     *
     * @param address contract address
     * @param amounts amounts
     * @param datum   inline datum
     * @return T
     */
    public T payToContract(String address, List<Amount> amounts, PlutusData datum) {
        return payToAddress(address, amounts, null, datum, null, null);
    }

    /**
     * Add an output at contract address with amount and datum hash.
     *
     * @param address   contract address
     * @param amount    amount
     * @param datumHash datum hash
     * @return T
     */
    public T payToContract(String address, Amount amount, String datumHash) {
        return payToAddress(address, List.of(amount), HexUtil.decodeHexString(datumHash), null, null, null);
    }

    /**
     * Add an output at contract address with amount and datum hash.
     *
     * @param address   contract address
     * @param amounts   list of amounts
     * @param datumHash datum hash
     * @return T
     */
    public T payToContract(String address, List<Amount> amounts, String datumHash) {
        return payToAddress(address, amounts, HexUtil.decodeHexString(datumHash), null, null, null);
    }

    /**
     * Add an output at contract address with amounts, inline datum and reference script.
     *
     * @param address   address
     * @param amounts   List of Amount to send
     * @param datum     Plutus data
     * @param refScript Reference Script
     * @return T
     */
    public T payToContract(String address, List<Amount> amounts, PlutusData datum, Script refScript) {
        return payToAddress(address, amounts, null, datum, refScript, null);
    }

    /**
     * Add an output at a contract address with specified amount, inline datum, and a reference script.
     *
     * @param address   the contract address to which the amount will be sent
     * @param amount    the amount to be sent to the contract address
     * @param datum     Plutus data
     * @param refScript Reference Script
     * @return T
     */
    public T payToContract(String address, Amount amount, PlutusData datum, Script refScript) {
        return payToAddress(address, List.of(amount), null, datum, refScript, null);
    }

    /**
     * Add an output at contract address with amounts, inline datum and reference script bytes.
     *
     * @param address        address
     * @param amounts        List of Amount to send
     * @param datum          Plutus data
     * @param scriptRefBytes Reference Script bytes
     * @return T
     */
    public T payToContract(String address, List<Amount> amounts, PlutusData datum, byte[] scriptRefBytes) {
        return payToAddress(address, amounts, null, datum, null, scriptRefBytes);
    }

    /**
     * Add an output to the transaction.
     *
     * @param address        address
     * @param amounts        List of Amount to send
     * @param datumHash      datum hash
     * @param datum          inline datum
     * @param scriptRef      Reference Script
     * @param scriptRefBytes Reference Script bytes
     * @return T
     */
    protected T payToAddress(String address, List<Amount> amounts, byte[] datumHash, PlutusData datum, Script scriptRef, byte[] scriptRefBytes) {
        if (scriptRef != null && scriptRefBytes != null && scriptRefBytes.length > 0)
            throw new TxBuildException("Both scriptRef and scriptRefBytes cannot be set. Only one of them can be set");

        if (datumHash != null && datumHash.length > 0 && datum != null)
            throw new TxBuildException("Both datumHash and datum cannot be set. Only one of them can be set");

        TransactionOutput transactionOutput = TransactionOutput.builder()
                .address(address)
                .value(Value.builder().coin(BigInteger.ZERO).build())
                .build();

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

        //set datum
        if (datum != null) {
            transactionOutput.setInlineDatum(datum);
        } else if (datumHash != null) {
            transactionOutput.setDatumHash(datumHash);
        }

        if (scriptRef != null) {
            transactionOutput.setScriptRef(scriptRef);
        } else if (scriptRefBytes != null)
            transactionOutput.setScriptRef(scriptRefBytes);

        if (outputs == null)
            outputs = new ArrayList<>();
        outputs.add(transactionOutput);

        return (T) this;
    }

    /**
     * Donate to treasury
     * @param currentTreasuryValue current treasury value
     * @param donationAmount donation amount in lovelace
     * @return T
     */
    public T donateToTreasury(@NonNull BigInteger currentTreasuryValue, @NonNull BigInteger donationAmount) {
        if (donationContext != null)
            throw new TxBuildException("Can't donate to treasury multiple times in a single transaction");
        donationContext = new DonationContext(currentTreasuryValue, donationAmount);

        return (T) this;
    }

    /**
     * This is an optional method. By default, the change address is same as the sender address.<br>
     * This method is used to set a different change address.
     * <br><br>
     * By default, if there is a single Tx during a transaction with a custom change address, the default fee payer is set to the
     * custom change address in Tx. So that the fee is deducted from the change output.
     * <br><br>
     * But for a custom change address in Tx and a custom fee payer, make sure feePayer address (which is set through {@link QuickTxBuilder})
     * has enough balance to pay the fee after all outputs .
     *
     * @param changeAddress
     * @return T
     */
    public T withChangeAddress(String changeAddress) {
        this.changeAddress = changeAddress;
        return (T) this;
    }

    /**
     * Add metadata to the transaction.
     *
     * @param metadata
     * @return Tx
     */
    public T attachMetadata(Metadata metadata) {
        if (this.txMetadata == null)
            this.txMetadata = metadata;
        else
            this.txMetadata = this.txMetadata.merge(metadata);
        return (T) this;
    }

    /**
     * Checks if the transaction has any multi-asset minting or burning.
     *
     * @return true if there are multi-assets to be minted; false otherwise
     */
    boolean hasMultiAssetMinting() {
        return multiAssets != null && !multiAssets.isEmpty();
    }

    TxBuilder complete() {
        TxOutputBuilder txOutputBuilder = null;

        if (depositRefundContexts != null && depositRefundContexts.size() > 0) {
            for (DepositRefundContext depositPaymentContext: depositRefundContexts) {
                if (txOutputBuilder == null)
                    txOutputBuilder = DepositRefundOutputBuilder.createFromDepositRefundContext(depositPaymentContext);
                else
                    txOutputBuilder = txOutputBuilder.and(DepositRefundOutputBuilder.createFromDepositRefundContext(depositPaymentContext));
            }
        }

        //Add donation dummy output to trigger input selection
        if (donationContext != null) {
            txOutputBuilder = txOutputBuilder == null ? buildDummyDonationTxOutBuilder()
                    : txOutputBuilder.and(buildDummyDonationTxOutBuilder());
        }

        //Define outputs
        if (outputs != null) {
            for (TransactionOutput output : outputs) {
                if (txOutputBuilder == null)
                    txOutputBuilder = OutputBuilders.createFromOutput(output);
                else
                    txOutputBuilder = txOutputBuilder.and(OutputBuilders.createFromOutput(output));
            }
        }

        //Add multi assets to tx builder context
        if (multiAssets != null && !multiAssets.isEmpty()) {
            if (txOutputBuilder == null)
                txOutputBuilder = (context, txn) -> {
                };

            txOutputBuilder = txOutputBuilder.and((context, txn) -> {
                if (context.getMintMultiAssets() == null || context.getMintMultiAssets().isEmpty()) {
                    multiAssets.forEach(multiAssetTuple -> {
                        context.addMintMultiAsset(multiAssetTuple._2);
                    });
                }
            });
        }

        TxBuilder txBuilder;
        if (txOutputBuilder == null) {
            txBuilder = (context, txn) -> {
            };
        } else {
            //Build inputs
            if (inputUtxos != null && !inputUtxos.isEmpty()) {
                txBuilder = buildInputBuildersFromUtxos(txOutputBuilder);
            } else {
                txBuilder = buildInputBuilders(txOutputBuilder);
            }
        }

        //Mint assets
        if (multiAssets != null && !multiAssets.isEmpty()) {
            for (Tuple<Script, MultiAsset> multiAssetTuple : multiAssets) {
                txBuilder = txBuilder
                        .andThen(MintCreators.mintCreator(multiAssetTuple._1, multiAssetTuple._2));
            }
        }

        //Add metadata
        if (txMetadata != null)
            txBuilder = txBuilder.andThen(AuxDataProviders.metadataProvider(txMetadata));

        //Remove donation dummy output if required
        if (donationContext != null) {
            txBuilder = txBuilder.andThen(buildDonatationTxBuilder());
        }

        return txBuilder;
    }

    private TxBuilder buildInputBuilders(TxOutputBuilder txOutputBuilder) {
        String _changeAddress = getChangeAddress();
        String _fromAddress = getFromAddress();
        Wallet _fromWallet = getFromWallet();
        TxBuilder txBuilder = null;

        if (_fromWallet != null) {
            txBuilder = txOutputBuilder.buildInputs(InputBuilders.createFromSender(_fromWallet, _changeAddress));
        } else {
            txBuilder = txOutputBuilder.buildInputs(InputBuilders.createFromSender(_fromAddress, _changeAddress));
        }

        return txBuilder;
    }

    /**
     * Build dummy donation output to trigger input selection
     * @return TxOutputBuilder
     */
    private TxOutputBuilder buildDummyDonationTxOutBuilder() {
        if (donationContext == null)
            return null;

        TxOutputBuilder dummyTxOutputBuilder = (context, outputs) -> {
            var dummyDonationOutput = new TransactionOutput(DUMMY_TREASURY_ADDRESS, Value.builder().coin(donationContext.donationAmount).build());
            outputs.add(dummyDonationOutput);
        };

        return dummyTxOutputBuilder;
    }

    /**
     * Build donation TxBuilder to set donation amount and current treasury value
     * Also to remove the dummy donation output
     * @return TxBuilder
     */
    private TxBuilder buildDonatationTxBuilder() {
        if (donationContext == null)
            return null;

        return (context, txn) -> {
            txn.getBody().getOutputs().removeIf(output -> output.getAddress().equals(DUMMY_TREASURY_ADDRESS));
            txn.getBody().setCurrentTreasuryValue(donationContext.currentTreasuryValue);
            txn.getBody().setDonation(donationContext.donationAmount);
        };
    }

    private TxBuilder buildInputBuildersFromUtxos(TxOutputBuilder txOutputBuilder) {
        String _changeAddr = getChangeAddress();

        TxBuilder txBuilder;
        if (changeData != null) {
            txBuilder = txOutputBuilder.buildInputs(InputBuilders.createFromUtxos(inputUtxos, _changeAddr, changeData));
        } else if (changeDatahash != null) {
            txBuilder = txOutputBuilder.buildInputs(InputBuilders.createFromUtxos(inputUtxos, _changeAddr, changeDatahash));
        } else {
            txBuilder = txOutputBuilder.buildInputs(InputBuilders.createFromUtxos(inputUtxos, _changeAddr));
        }

        return txBuilder;
    }

    @SneakyThrows
    protected void addToMultiAssetList(@NonNull Script script, List<Asset> assets) {
        String policyId = script.getPolicyId();
        MultiAsset multiAsset = MultiAsset.builder()
                .policyId(policyId)
                .assets(assets)
                .build();

        if (multiAssets == null)
            multiAssets = new ArrayList<>();

        //Check if multiasset already exists
        //If there is another mulitasset with same policy id, add the assets to that multiasset and use MultiAsset.plus method
        //to create a new multiasset
        multiAssets.stream().filter(ma -> {
            try {
                return ma._1.getPolicyId().equals(script.getPolicyId());
            } catch (CborSerializationException e) {
                throw new CborRuntimeException(e);
            }
        }).findFirst().ifPresentOrElse(ma -> {
            multiAssets.remove(ma);
            multiAssets.add(new Tuple<>(script, ma._2.add(multiAsset)));
        }, () -> {
            multiAssets.add(new Tuple<>(script, multiAsset));
        });
    }

    protected void addDepositRefundContext(List<DepositRefundContext> _depositRefundContexts) {
        if (this.depositRefundContexts == null)
            this.depositRefundContexts = new ArrayList<>();

        _depositRefundContexts.forEach(depositRefundContext -> {
            this.depositRefundContexts.add(depositRefundContext);
        });
    }

    /**
     * Return change address
     *
     * @return String
     */
    protected abstract String getChangeAddress();

    /**
     * Return from address
     *
     * @return String
     */
    protected abstract String getFromAddress();

    protected abstract Wallet getFromWallet();

    /**
     * Perform pre Tx evaluation action. This is called before Script evaluation if any
     * @param transaction
     */
    protected void preTxEvaluation(Transaction transaction) {

    }

    /**
     * Perform post balanceTx action
     *
     * @param transaction
     */
    protected abstract void postBalanceTx(Transaction transaction);

    /**
     * Verify if the data is valid
     */
    protected abstract void verifyData();

    /**
     * Return fee payer
     *
     * @return String
     */
    protected abstract String getFeePayer();

    @Getter
    @AllArgsConstructor
    static class DonationContext {
        private BigInteger currentTreasuryValue;
        private BigInteger donationAmount;
    }
}
