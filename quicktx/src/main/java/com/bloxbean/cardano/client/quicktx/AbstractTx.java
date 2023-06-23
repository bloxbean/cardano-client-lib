package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.AssetUtil;
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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

public abstract class AbstractTx<T> {
    protected List<TransactionOutput> outputs;
    protected List<TransactionOutput> mintOutputs;
    protected List<Tuple<Script, MultiAsset>> multiAssets;
    protected Metadata txMetadata;
    //custom change address
    protected String changeAddress;
    protected List<Utxo> inputUtxos;

    //Required for script
    protected PlutusData changeData;
    protected String changeDatahash;


    /**
     * Add an output to the transaction. This method can be called multiple times to add multiple outputs.
     *
     * @param address Address to send the output
     * @param amount  Amount to send
     * @return T
     */
    public T payToAddress(String address, Amount amount) {
        return payToAddress(address, List.of(amount), false);
    }

    /**
     * Add an output to the transaction. This method can be called multiple times to add multiple outputs.
     * This method is useful for newly minted asset in the transaction.
     *
     * @param address    Address to send the output
     * @param amount     Amount to send
     * @param mintOutput If the asset in the output will be minted in this transaction, set this to true, otherwise false
     * @return T
     */
    public T payToAddress(String address, Amount amount, boolean mintOutput) {
        return payToAddress(address, List.of(amount), mintOutput);
    }

    /**
     * Add an output to the transaction. This method can be called multiple times to add multiple outputs.
     *
     * @param address Address to send the output
     * @param amounts List of Amount to send
     * @return T
     */
    public T payToAddress(String address, List<Amount> amounts) {
        return payToAddress(address, amounts, false);
    }

    /**
     * Add an output to the transaction. This method can be called multiple times to add multiple outputs.
     * This method is useful for newly minted assets in the transaction.
     *
     * @param address    address
     * @param amounts    List of Amount to send
     * @param mintOutput If the assets in the output will be minted in this transaction, set this to true, otherwise false
     * @return T
     */
    public T payToAddress(String address, List<Amount> amounts, boolean mintOutput) {
        return payToAddress(address, amounts, null, null, null, null, mintOutput);
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
        return payToAddress(address, amounts, null, null, script, null, false);
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
        return payToAddress(address, amounts, null, null, null, scriptRefBytes, false);
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
        return payToAddress(address, List.of(amount), null, datum, null, null, false);
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
        return payToAddress(address, amounts, null, datum, null, null, false);
    }

    /**
     * Add an output at contract address with amount and inline datum.
     * This method is useful for newly minted assets in the transaction.
     *
     * @param address    contract address
     * @param amounts    list of amounts
     * @param datum      inline datum
     * @param mintOutput If the assets in the output will be minted in this transaction, set this to true, otherwise false
     * @return T
     */
    public T payToContract(String address, List<Amount> amounts, PlutusData datum, boolean mintOutput) {
        return payToAddress(address, amounts, null, datum, null, null, mintOutput);
    }

    /**
     * Add an output at contract address with amounts and datum hash.
     * This method is useful for newly minted assets in the transaction.
     *
     * @param address    contract address
     * @param amounts    list of amounts
     * @param datumHash  datum hash
     * @param mintOutput If the assets in the output will be minted in this transaction, set this to true, otherwise false
     * @return T
     */
    public T payToContract(String address, List<Amount> amounts, String datumHash, boolean mintOutput) {
        return payToAddress(address, amounts, HexUtil.decodeHexString(datumHash), null, null, null, mintOutput);
    }

    /**
     * Add an output at contract address with amounts, datum hash and reference script.
     * This method is useful for newly minted assets in the transaction.
     *
     * @param address    address
     * @param amounts    List of Amount to send
     * @param datum      Plutus data
     * @param refScript  Reference Script
     * @param mintOutput If the asset in the output will be minted in this transaction, set this to true, otherwise false
     * @return T
     */
    public T payToContract(String address, List<Amount> amounts, PlutusData datum, Script refScript, boolean mintOutput) {
        return payToAddress(address, amounts, null, datum, refScript, null, mintOutput);
    }

    /**
     * Add an output at contract address with amounts, inline datum and reference script bytes.
     * This method is useful for newly minted assets in the transaction.
     *
     * @param address        address
     * @param amounts        List of Amount to send
     * @param datum          Plutus data
     * @param scriptRefBytes Reference Script bytes
     * @param mintOutput     If the asset in the output will be minted in this transaction, set this to true, otherwise false
     * @return T
     */
    public T payToContract(String address, List<Amount> amounts, PlutusData datum, byte[] scriptRefBytes, boolean mintOutput) {
        return payToAddress(address, amounts, null, datum, null, scriptRefBytes, mintOutput);
    }

    /**
     * Add an output to the transaction. This method can be called multiple times to add multiple outputs.
     *
     * @param address    address
     * @param amounts    List of Amount to send
     * @param script     Reference Script
     * @param mintOutput If the asset in the output will be minted in this transaction, set this to true, otherwise false
     * @return T
     */
    public T payToAddress(String address, List<Amount> amounts, Script script, boolean mintOutput) {
        return payToAddress(address, amounts, null, null, script, null, mintOutput);
    }

    /**
     * Add an output to the transaction. This method can be called multiple times to add multiple outputs.
     *
     * @param address        address
     * @param amounts        List of Amount to send
     * @param scriptRefBytes Reference Script bytes
     * @param mintOutput     If the asset in the output will be minted in this transaction, set this to true, otherwise false
     * @return T
     */
    public T payToAddress(String address, List<Amount> amounts, byte[] scriptRefBytes, boolean mintOutput) {
        return payToAddress(address, amounts, null, null, null, scriptRefBytes, mintOutput);
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
     * @param mintOutput     If the asset in the output will be minted in this transaction, set this to true, otherwise false
     * @return T
     */
    protected T payToAddress(String address, List<Amount> amounts, byte[] datumHash, PlutusData datum, Script scriptRef, byte[] scriptRefBytes, boolean mintOutput) {
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
                Value newValue = transactionOutput.getValue().plus(new Value(BigInteger.ZERO, List.of(multiAsset)));
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

        if (mintOutput) {
            if (mintOutputs == null)
                mintOutputs = new ArrayList<>();

            mintOutputs.add(transactionOutput);
        } else {
            if (outputs == null)
                outputs = new ArrayList<>();
            outputs.add(transactionOutput);
        }

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

    TxBuilder complete() {
        TxOutputBuilder txOutputBuilder = null;
        //Define outputs
        if (outputs != null) {
            for (TransactionOutput output : outputs) {
                if (txOutputBuilder == null)
                    txOutputBuilder = OutputBuilders.createFromOutput(output);
                else
                    txOutputBuilder = txOutputBuilder.and(OutputBuilders.createFromOutput(output));
            }
        }

        if (mintOutputs != null) {
            for (TransactionOutput mintOutput : mintOutputs) {
                if (txOutputBuilder == null)
                    txOutputBuilder = OutputBuilders.createFromMintOutput(mintOutput);
                else
                    txOutputBuilder = txOutputBuilder.and(OutputBuilders.createFromMintOutput(mintOutput));
            }
        }

        TxBuilder txBuilder;
        if (txOutputBuilder == null) {
            txBuilder = (context, txn) -> {};
        } else {
            //Build inputs
            if (inputUtxos != null && !inputUtxos.isEmpty()) {
                txBuilder = buildInputBuildersFromUtxos(txOutputBuilder);
            } else {
                txBuilder = buildInputBuilders(txOutputBuilder);
            }
        }

        //Mint assets
        if (multiAssets != null) {
            for (Tuple<Script, MultiAsset> multiAssetTuple : multiAssets) {
                txBuilder = txBuilder
                        .andThen(MintCreators.mintCreator(multiAssetTuple._1, multiAssetTuple._2));
            }
        }

        //Add metadata
        if (txMetadata != null)
            txBuilder = txBuilder.andThen(AuxDataProviders.metadataProvider(txMetadata));

        return txBuilder;
    }

    private TxBuilder buildInputBuilders(TxOutputBuilder txOutputBuilder) {
        String _changeAddress = getChangeAddress();
        String _fromAddress = getFromAddress();
        TxBuilder txBuilder = txOutputBuilder.buildInputs(InputBuilders.createFromSender(_fromAddress, _changeAddress));

        return txBuilder;
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

}
