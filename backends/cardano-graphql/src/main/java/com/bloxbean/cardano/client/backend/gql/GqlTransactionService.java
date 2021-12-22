package com.bloxbean.cardano.client.backend.gql;

import com.apollographql.apollo.exception.ApolloException;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.*;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.gql.SubmitTxMutation;
import com.bloxbean.cardano.gql.TransactionQuery;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

public class GqlTransactionService extends BaseGqlService implements TransactionService {
    private final static Logger logger = LoggerFactory.getLogger(GqlTransactionService.class);

    public GqlTransactionService(String gqlUrl) {
        super(gqlUrl);
    }

    public GqlTransactionService(String gqlUrl, Map<String, String> headers) {
        super(gqlUrl, headers);
    }

    public GqlTransactionService(String gqlUrl, OkHttpClient okHttpClient) {
        super(gqlUrl, okHttpClient);
    }

    @Override
    public Result<String> submitTransaction(byte[] cborData) throws ApiException {
        String signedTx = HexUtil.encodeHexString(cborData);
        SubmitTxMutation submitTxMutation = new SubmitTxMutation(signedTx);
        SubmitTxMutation.Data data = null;
        try {
            data = executeMutatation(submitTxMutation);
        } catch (ApolloException e) {
            if(logger.isDebugEnabled()) {
                logger.error("Transaction submission error", e);
            }
            return Result.error(e.getMessage()).withValue(e);
        }

        if(data == null)
            return Result.error("Error in transaction submission");

        String hash = data.submitTransaction().hash();
        return processSuccessResult(hash);
    }

    @Override
    public Result<TransactionContent> getTransaction(String txnHash) throws ApiException {
        TransactionQuery.Transaction transaction = getTransactionDetils(txnHash);
        if(transaction == null)
            return Result.error("Transaction not found for txnhash: " + txnHash);

        TransactionContent transactionContent = convertGqlTransactionToTransactionContent(transaction);

        return processSuccessResult(transactionContent);
    }

    private TransactionQuery.Transaction getTransactionDetils(String txnHash) throws ApiException {
        TransactionQuery query = new TransactionQuery(txnHash);
        TransactionQuery.Data data = execute(query);
        if(data == null)
            return null;

        List<TransactionQuery.Transaction> transactions = data.transactions();
        if(transactions == null || transactions.size() == 0)
            return null;

        TransactionQuery.Transaction transaction = transactions.get(0);
        return transaction;
    }

    private TransactionContent convertGqlTransactionToTransactionContent(TransactionQuery.Transaction transaction) {
        TransactionContent txnContent = new TransactionContent();
        txnContent.setBlock(transaction.block().hash().toString());
        txnContent.setBlockHeight(transaction.block().number());
        txnContent.setSlot(transaction.block().slotNo());
        txnContent.setIndex(transaction.blockIndex());

        txnContent.setOutputAmount(createOutputAmounts(transaction.outputs()));

        txnContent.setFees(String.valueOf(transaction.fee()));
        txnContent.setDeposit(String.valueOf(transaction.deposit()));
        txnContent.setSize(((BigDecimal)transaction.size()).intValue());
        txnContent.setInvalidBefore(transaction.invalidBefore());
        txnContent.setInvalidHereafter(transaction.invalidHereafter());

        //TODO
        //txnContent.setUtxoCount();

        try {
            txnContent.setWithdrawalCount(Integer.parseInt(transaction.withdrawals_aggregate().aggregate().count()));
        } catch (Exception e) {}

        //TODO other fields. but not required for cardano-client-lib
        return txnContent;
    }

    private List<TxOutputAmount> createOutputAmounts(List<TransactionQuery.Output> outputs) {
        List<TxOutputAmount> txOutputAmounts = new ArrayList<>();
        TxOutputAmount lovelaceOutput = new TxOutputAmount();
        lovelaceOutput.setUnit(LOVELACE);

        for(TransactionQuery.Output output: outputs) {
            if(!"0".equals(output.value())) {
                TxOutputAmount txOutputAmount = TxOutputAmount.builder()
                        .unit(LOVELACE)
                        .quantity(output.value())
                        .build();
                txOutputAmounts.add(txOutputAmount);
            }

            List<TransactionQuery.Token> tokens = output.tokens();
            if(tokens != null && tokens.size() > 0) {
                for(TransactionQuery.Token token: tokens) {
                    try {
                        txOutputAmounts.add(TxOutputAmount.builder()
                                .unit(String.valueOf(token.asset().assetId()))
                                .quantity(token.quantity()).build()
                        );
                    } catch (Exception e) {}
                }
            }
        }

        return txOutputAmounts;
    }

    @Override
    public Result<TxContentUtxo> getTransactionUtxos(String txnHash) throws ApiException {
        TransactionQuery.Transaction transaction = getTransactionDetils(txnHash);
        if(transaction == null)
            return Result.error("Transaction not found for txnhash: " + txnHash);

        TxContentUtxo txContentUtxo = createTxContentUtxo(transaction);
        return processSuccessResult(txContentUtxo);
    }

    private TxContentUtxo createTxContentUtxo(TransactionQuery.Transaction transaction) {
        TxContentUtxo txContentUtxo = new TxContentUtxo();
        txContentUtxo.setInputs(new ArrayList<>());
        txContentUtxo.setOutputs(new ArrayList<>());

        txContentUtxo.setOutputs(getUtxoOutputs(transaction));
        txContentUtxo.setInputs(getUtxoInputs(transaction));

        return txContentUtxo;
    }

    private List<TxContentUtxoInputs> getUtxoInputs(TransactionQuery.Transaction transaction) {
        Map<String, List<TxContentOutputAmount>> inputMaps = new HashMap<>();
        List<TransactionQuery.Input> inputs = transaction.inputs();
        for(TransactionQuery.Input input: inputs) {
            String address = input.address();
            List<TxContentOutputAmount> outputAmounts =  inputMaps.get(address);
            if(outputAmounts == null) {
                outputAmounts = new ArrayList<>();
                inputMaps.put(address, outputAmounts);
            }

            if(!"0".equals(input.value())) {
                TxContentOutputAmount txOutputAmount = TxContentOutputAmount.builder()
                        .unit(LOVELACE)
                        .quantity(input.value())
                        .build();
                outputAmounts.add(txOutputAmount);
            }

            List<TransactionQuery.Token1> tokens = input.tokens();
            if(tokens != null && tokens.size() > 0) {
                for(TransactionQuery.Token1 token: tokens) {
                    try {
                        TxContentOutputAmount txOutputAmount = TxContentOutputAmount.builder()
                                .unit(String.valueOf(token.asset().assetId()))
                                .quantity(token.quantity()).build();
                        outputAmounts.add(txOutputAmount);
                    } catch (Exception e) {}
                }
            }
        }

        List<TxContentUtxoInputs> utxoInputs = new ArrayList<>();
        for(String address: inputMaps.keySet()) {
            TxContentUtxoInputs addrUtxoInputs = new TxContentUtxoInputs();
            addrUtxoInputs.setAddress(address);
            addrUtxoInputs.setAmount(inputMaps.get(address));

            utxoInputs.add(addrUtxoInputs);
        }

        return utxoInputs;
    }

    private List<TxContentUtxoOutputs> getUtxoOutputs(TransactionQuery.Transaction transaction) {
        Map<String, List<TxContentOutputAmount>> outputsMap = new HashMap<>();
        List<TransactionQuery.Output> outputs = transaction.outputs();
        for(TransactionQuery.Output output: outputs) {
            String address = output.address();
            List<TxContentOutputAmount> outputAmounts =  outputsMap.get(address);
            if(outputAmounts == null) {
                outputAmounts = new ArrayList<>();
                outputsMap.put(address, outputAmounts);
            }

            if(!"0".equals(output.value())) {
                TxContentOutputAmount txOutputAmount = TxContentOutputAmount.builder()
                        .unit(LOVELACE)
                        .quantity(output.value())
                        .build();
                outputAmounts.add(txOutputAmount);
            }

            List<TransactionQuery.Token> tokens = output.tokens();
            if(tokens != null && tokens.size() > 0) {
                for(TransactionQuery.Token token: tokens) {
                    try {
                        TxContentOutputAmount txOutputAmount = TxContentOutputAmount.builder()
                                .unit(String.valueOf(token.asset().assetId()))
                                .quantity(token.quantity()).build();
                        outputAmounts.add(txOutputAmount);
                    } catch (Exception e) {}
                }
            }
        }

        List<TxContentUtxoOutputs> utxoOutputs = new ArrayList<>();
        for(String address: outputsMap.keySet()) {
            TxContentUtxoOutputs addrUtxoOutputs = new TxContentUtxoOutputs();
            addrUtxoOutputs.setAddress(address);
            addrUtxoOutputs.setAmount(outputsMap.get(address));

            utxoOutputs.add(addrUtxoOutputs);
        }

        return utxoOutputs;
    }
}
