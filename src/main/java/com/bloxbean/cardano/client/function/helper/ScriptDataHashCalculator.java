package com.bloxbean.cardano.client.function.helper;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.transaction.spec.CostMdls;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.util.ScriptDataHashGenerator;

import static com.bloxbean.cardano.client.transaction.util.CostModelUtil.PlutusV1CostModel;
import static com.bloxbean.cardano.client.transaction.util.CostModelUtil.PlutusV2CostModel;

//TODO tests pending
public class ScriptDataHashCalculator {

    public static TxBuilder calculateScriptDataHash() {
        return (context, txn) -> {
            calculateScriptDataHash(context, txn);
        };
    }

    public static void calculateScriptDataHash(TxBuilderContext ctx, Transaction transaction) {
        boolean containsPlutusScript = false;
        //check if plutusscript exists
        if ((transaction.getWitnessSet().getPlutusV1Scripts() != null
                && transaction.getWitnessSet().getPlutusV1Scripts().size() > 0)
                || (transaction.getWitnessSet().getPlutusV2Scripts() != null
                && transaction.getWitnessSet().getPlutusV2Scripts().size() > 0)
                || (transaction.getWitnessSet().getRedeemers() != null
                && transaction.getWitnessSet().getRedeemers().size() > 0)
        ) {
            containsPlutusScript = true;
        }

        CostMdls costMdls = ctx.getCostMdls();
        if (costMdls == null) {
            costMdls = new CostMdls();
            if (transaction.getWitnessSet().getPlutusV1Scripts() != null
                    && transaction.getWitnessSet().getPlutusV1Scripts().size() > 0) {
                costMdls.add(PlutusV1CostModel);
            }

            if (transaction.getWitnessSet().getPlutusV2Scripts() != null
                    && transaction.getWitnessSet().getPlutusV2Scripts().size() > 0) {
                costMdls.add(PlutusV2CostModel);
            }

            if (costMdls.isEmpty()) { //Check if costmodel can be decided from other fields
                if (transaction.getBody().getReferenceInputs() != null
                        && transaction.getBody().getReferenceInputs().size() > 0) { //If reference input is there, then plutus v2
                    costMdls.add(PlutusV2CostModel);
                }
            }
        }

        if (containsPlutusScript) {
            //Script dataHash
            byte[] scriptDataHash;
            try {
                scriptDataHash = ScriptDataHashGenerator.generate(transaction.getWitnessSet().getRedeemers(),
                        transaction.getWitnessSet().getPlutusDataList(), costMdls.getLanguageViewEncoding());
            } catch (CborSerializationException | CborException e) {
                throw new CborRuntimeException(e);
            }

            transaction.getBody().setScriptDataHash(scriptDataHash);
        }
    }
}
