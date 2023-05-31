package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionEvaluator;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.Result;

import java.util.List;

//TODO -- Implement this class
public class AikenTxEvaluator implements TransactionEvaluator {
    private UtxoSupplier utxoSupplier;
    private ProtocolParamsSupplier protocolParamsSupplier;

    public AikenTxEvaluator(UtxoSupplier utxoSupplier, ProtocolParamsSupplier protocolParamsSupplier) {
        this.utxoSupplier = utxoSupplier;
        this.protocolParamsSupplier = protocolParamsSupplier;
    }
    @Override
    public Result<List<EvaluationResult>> evaluateTx(byte[] cbor) throws ApiException {
//        try {
//            Transaction transaction = Transaction.deserialize(cbor);
//
//            Set<Utxo> utxos = new HashSet<>();
//            //inputs
//            for (TransactionInput input: transaction.getBody().getInputs()) {
//                Utxo utxo = utxoSupplier.getTxOutput(input.getTransactionId(), input.getIndex())
//                        .get();
//                utxos.add(utxo);
//            }
//
//            //reference inputs
//            for (TransactionInput input: transaction.getBody().getReferenceInputs()) {
//                Utxo utxo = utxoSupplier.getTxOutput(input.getTransactionId(), input.getIndex())
//                        .get();
//                utxos.add(utxo);
//            }
//
//            Language language = transaction.getWitnessSet().getPlutusV1Scripts().size() > 0? PLUTUS_V1 : PLUTUS_V2;
//            ProtocolParams protocolParams = protocolParamsSupplier.getProtocolParams();
//            Optional<CostModel> costModelOptional =
//                    CostModelUtil.getCostModelFromProtocolParams(protocolParams, language);
//            if(!costModelOptional.isPresent())
//                throw new ApiException("Cost model not found for language: " + language);
//
//            CostMdls costMdls = new CostMdls();
//            costMdls.add(costModelOptional.get());
//
//            TxEvaluator txEvaluator = new TxEvaluator();
//            txEvaluator.evaluateTx(transaction, utxos, costMdls)
//        } catch (Exception e) {
//            throw new ApiException("Error evaluating transaction", e);
//        }

        return null;

    }
}
