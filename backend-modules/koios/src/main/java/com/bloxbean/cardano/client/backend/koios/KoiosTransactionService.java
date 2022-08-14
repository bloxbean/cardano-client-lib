package com.bloxbean.cardano.client.backend.koios;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.model.*;
import rest.koios.client.backend.api.transactions.TransactionsService;
import rest.koios.client.backend.api.transactions.model.*;

import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

/**
 * Koios Transaction Service
 */
public class KoiosTransactionService implements TransactionService {

    /**
     * Transaction Service
     */
    private final TransactionsService transactionsService;

    public KoiosTransactionService(TransactionsService transactionsService) {
        this.transactionsService = transactionsService;
    }

    @Override
    public Result<String> submitTransaction(byte[] cborData) throws ApiException {
        try {
            rest.koios.client.backend.api.base.Result<String> txResult = transactionsService.submitTx(cborData);
            if (!txResult.isSuccessful()) {
                return Result.error(txResult.getResponse()).withValue(txResult.getResponse()).code(txResult.getCode());
            }
            return Result.success("OK").withValue(txResult.getValue()).code(200);
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<TransactionContent> getTransaction(String txnHash) throws ApiException {
        try {
            rest.koios.client.backend.api.base.Result<List<TxInfo>> txInfoResult = transactionsService.getTransactionInformation(List.of(txnHash), null);
            if (!txInfoResult.isSuccessful()) {
                return Result.error(txInfoResult.getResponse()).code(txInfoResult.getCode());
            }

            if (txInfoResult.getValue().size() != 0) {
                return convertToTransactionContent(txInfoResult.getValue().get(0));
            } else {
                return Result.error("Not Found").code(404);
            }
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private Result<TransactionContent> convertToTransactionContent(TxInfo txInfo) {
        TransactionContent transactionContent = new TransactionContent();
        transactionContent.setHash(txInfo.getTxHash());
        transactionContent.setBlock(txInfo.getBlockHash());
        transactionContent.setBlockHeight(txInfo.getBlockHeight());
        transactionContent.setBlockTime(txInfo.getTxTimestamp());
        transactionContent.setSlot(txInfo.getAbsoluteSlot());
        transactionContent.setIndex(txInfo.getTxBlockIndex());
        List<TxOutputAmount> txOutputAmountList = new ArrayList<>();
        txOutputAmountList.add(new TxOutputAmount(LOVELACE, txInfo.getTotalOutput()));
        for (TxIO txIO : txInfo.getOutputs()) {
            for (TxAsset txAsset : txIO.getAssetList()) {
                txOutputAmountList.add(new TxOutputAmount(txAsset.getPolicyId() + txAsset.getAssetName(), txAsset.getQuantity()));
            }
        }
        transactionContent.setOutputAmount(txOutputAmountList);
        transactionContent.setFees(txInfo.getFee());
        transactionContent.setDeposit(txInfo.getDeposit());
        transactionContent.setSize(txInfo.getTxSize());
        if (txInfo.getInvalidBefore() != null) {
            transactionContent.setInvalidBefore(String.valueOf(txInfo.getInvalidBefore()));
        }
        if (txInfo.getInvalidAfter() != null) {
            transactionContent.setInvalidHereafter(String.valueOf(txInfo.getInvalidAfter()));
        }

        transactionContent.setUtxoCount(txInfo.getOutputs().size() + txInfo.getInputs().size());
        transactionContent.setWithdrawalCount(txInfo.getWithdrawals().size());
        int mirCerts = 0;
        int delegations = 0;
        int stakeCerts = 0;
        int poolUpdateCerts = 0;
        int poolRetires = 0;
        if (!txInfo.getCertificates().isEmpty()) {
            for (TxCertificate txCertificate : txInfo.getCertificates()) {
                switch (txCertificate.getType()) {
                    case "reserve_MIR":
                    case "treasury_MIR":
                        mirCerts++;
                        break;
                    case "delegation":
                        delegations++;
                        break;
                    case "pool_retire":
                        poolRetires++;
                        break;
                    case "pool_update":
                        poolUpdateCerts++;
                    case "stake_registration":
                    case "stake_deregistration":
                        stakeCerts++;
                }
            }
        }
        transactionContent.setMirCertCount(mirCerts);
        transactionContent.setDelegationCount(delegations);
        transactionContent.setStakeCertCount(stakeCerts);
        transactionContent.setPoolUpdateCount(poolUpdateCerts);
        transactionContent.setPoolRetireCount(poolRetires);

        int assetMintBurnCount = 0;
        if (!txInfo.getAssetsMinted().isEmpty()) {
            for (TxAsset txAsset : txInfo.getAssetsMinted()) {
                assetMintBurnCount += Integer.parseInt(txAsset.getQuantity().replace("-", ""));
            }
        }
        transactionContent.setAssetMintOrBurnCount(assetMintBurnCount);

        int redeemerCount = 0;
        boolean validContract = true;
        if (txInfo.getPlutusContracts() != null && !txInfo.getPlutusContracts().isEmpty()) {
            for (TxPlutusContract txPlutusContract : txInfo.getPlutusContracts()) {
                validContract = txPlutusContract.getValidContract();
                if (txPlutusContract.getInput().getRedeemer() != null) {
                    redeemerCount++;
                }
            }
        }
        transactionContent.setValidContract(validContract);
        transactionContent.setRedeemerCount(redeemerCount);
        return Result.success("OK").withValue(transactionContent).code(200);
    }

    @Override
    public Result<TxContentUtxo> getTransactionUtxos(String txnHash) throws ApiException {
        try {
            rest.koios.client.backend.api.base.Result<List<TxUtxo>> txUtxosResult = transactionsService.getTransactionUTxOs(List.of(txnHash), null);
            if (!txUtxosResult.isSuccessful()) {
                return Result.error(txUtxosResult.getResponse()).code(txUtxosResult.getCode());
            }
            return convertToTxContentUtxo(txUtxosResult.getValue().get(0));
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private Result<TxContentUtxo> convertToTxContentUtxo(TxUtxo txUtxo) {
        TxContentUtxo txContentUtxo = new TxContentUtxo();
        //Inputs
        List<TxContentUtxoInputs> inputs = new ArrayList<>();
        for (TxIO txIO : txUtxo.getInputs()) {
            List<TxContentOutputAmount> txContentOutputAmountList = new ArrayList<>();
            if (txIO.getValue() != null && !txIO.getValue().isEmpty()) {
                txContentOutputAmountList.add(new TxContentOutputAmount(LOVELACE, txIO.getValue()));
            }
            for (TxAsset txAsset : txIO.getAssetList()) {
                txContentOutputAmountList.add(new TxContentOutputAmount(txAsset.getPolicyId() + txAsset.getAssetName(), txAsset.getQuantity()));
            }
            inputs.add(new TxContentUtxoInputs(txIO.getPaymentAddr().getBech32(), txContentOutputAmountList));
        }
        if (!inputs.isEmpty()) {
            txContentUtxo.setInputs(inputs);
        }
        //Outputs
        List<TxContentUtxoOutputs> outputs = new ArrayList<>();
        for (TxIO txIO : txUtxo.getOutputs()) {
            List<TxContentOutputAmount> txContentOutputAmountList = new ArrayList<>();
            if (txIO.getValue() != null && !txIO.getValue().isEmpty()) {
                txContentOutputAmountList.add(new TxContentOutputAmount(LOVELACE, txIO.getValue()));
            }
            for (TxAsset txAsset : txIO.getAssetList()) {
                txContentOutputAmountList.add(new TxContentOutputAmount(txAsset.getPolicyId() + txAsset.getAssetName(), txAsset.getQuantity()));
            }
            outputs.add(new TxContentUtxoOutputs(txIO.getPaymentAddr().getBech32(), txContentOutputAmountList));
        }
        txContentUtxo.setOutputs(outputs);
        return Result.success("OK").withValue(txContentUtxo).code(200);
    }
}
