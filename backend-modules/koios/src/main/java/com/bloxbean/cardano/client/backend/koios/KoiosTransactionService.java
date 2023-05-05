package com.bloxbean.cardano.client.backend.koios;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.model.*;
import org.apache.commons.collections4.ListUtils;
import rest.koios.client.backend.api.common.Asset;
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

    /**
     * KoiosTransactionService Constructor
     *
     * @param transactionsService transactionsService
     */
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
            if (!txInfoResult.getValue().isEmpty()) {
                return Result.success("OK").withValue(convertToTransactionContent(txInfoResult.getValue().get(0))).code(200);
            } else {
                return Result.error("Not Found").code(404);
            }
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<TransactionContent>> getTransactions(List<String> txnHashCollection) throws ApiException {
        List<TransactionContent> result = new ArrayList<>();
        List<List<String>> partitioned = ListUtils.partition(txnHashCollection, 1000);
        for (List<String> txIds : partitioned) {
            try {
                rest.koios.client.backend.api.base.Result<List<TxInfo>> txInfoResult = transactionsService.getTransactionInformation(txIds, null);
                if (!txInfoResult.isSuccessful()) {
                    return Result.error(txInfoResult.getResponse()).code(txInfoResult.getCode());
                }
                if (!txInfoResult.getValue().isEmpty()) {
                    txInfoResult.getValue().forEach(txInfo -> result.add(convertToTransactionContent(txInfo)));
                } else {
                    return Result.error("Not Found").code(404);
                }
            } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
                throw new ApiException(e.getMessage(), e);
            }
        }
        return Result.success("OK").withValue(result).code(200);
    }

    private TransactionContent convertToTransactionContent(TxInfo txInfo) {
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
            for (Asset txAsset : txIO.getAssetList()) {
                txOutputAmountList.add(new TxOutputAmount(txAsset.getPolicyId() + txAsset.getAssetName(), txAsset.getQuantity()));
            }
        }
        transactionContent.setOutputAmount(txOutputAmountList);
        transactionContent.setFees(txInfo.getFee());
        transactionContent.setDeposit(txInfo.getDeposit());
        transactionContent.setSize(txInfo.getTxSize());
        if (txInfo.getInvalidBefore() != null) {
            transactionContent.setInvalidBefore(txInfo.getInvalidBefore());
        }
        if (txInfo.getInvalidAfter() != null) {
            transactionContent.setInvalidHereafter(txInfo.getInvalidAfter());
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
                        break;
                    case "stake_registration":
                    case "stake_deregistration":
                        stakeCerts++;
                        break;
                    default:
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
            for (Asset txAsset : txInfo.getAssetsMinted()) {
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
        return transactionContent;
    }

    @Override
    public Result<TxContentUtxo> getTransactionUtxos(String txnHash) throws ApiException {
        try {
            rest.koios.client.backend.api.base.Result<TxInfo> txInfoResult = transactionsService.getTransactionInformation(txnHash);
            if (!txInfoResult.isSuccessful()) {
                return Result.error(txInfoResult.getResponse()).code(txInfoResult.getCode());
            }
            return convertToTxContentUtxo(txInfoResult.getValue());
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private Result<TxContentUtxo> convertToTxContentUtxo(TxInfo txInfo) {
        TxContentUtxo txContentUtxo = new TxContentUtxo();
        //Inputs
        List<TxContentUtxoInputs> inputs = new ArrayList<>();
        for (TxIO txIO : txInfo.getInputs()) {
            List<TxContentOutputAmount> txContentOutputAmountList = new ArrayList<>();
            if (txIO.getValue() != null && !txIO.getValue().isEmpty()) {
                txContentOutputAmountList.add(new TxContentOutputAmount(LOVELACE, txIO.getValue()));
            }
            for (Asset txAsset : txIO.getAssetList()) {
                txContentOutputAmountList.add(new TxContentOutputAmount(txAsset.getPolicyId() + txAsset.getAssetName(), txAsset.getQuantity()));
            }
            inputs.add(new TxContentUtxoInputs(txIO.getPaymentAddr().getBech32(), txContentOutputAmountList));
        }
        if (!inputs.isEmpty()) {
            txContentUtxo.setInputs(inputs);
        }
        //Outputs
        List<TxContentUtxoOutputs> outputs = new ArrayList<>();
        for (TxIO txIO : txInfo.getOutputs()) {
            List<TxContentOutputAmount> txContentOutputAmountList = new ArrayList<>();
            if (txIO.getValue() != null && !txIO.getValue().isEmpty()) {
                txContentOutputAmountList.add(new TxContentOutputAmount(LOVELACE, txIO.getValue()));
            }
            for (Asset txAsset : txIO.getAssetList()) {
                txContentOutputAmountList.add(new TxContentOutputAmount(txAsset.getPolicyId() + txAsset.getAssetName(), txAsset.getQuantity()));
            }

            TxContentUtxoOutputs txContentUtxoOutputs = TxContentUtxoOutputs.builder()
                    .address(txIO.getPaymentAddr().getBech32())
                    .amount(txContentOutputAmountList)
                    .dataHash(txIO.getDatumHash())
                    .outputIndex(txIO.getTxIndex())
                    .inlineDatum(txIO.getInlineDatum() != null? txIO.getInlineDatum().getBytes(): null)
                    .referenceScriptHash(txIO.getReferenceScript() != null? txIO.getReferenceScript().getHash(): null)
                    .build();
            outputs.add(txContentUtxoOutputs);
        }
        txContentUtxo.setOutputs(outputs);
        return Result.success("OK").withValue(txContentUtxo).code(200);
    }
}
