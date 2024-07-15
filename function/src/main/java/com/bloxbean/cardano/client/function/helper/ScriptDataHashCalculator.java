package com.bloxbean.cardano.client.function.helper;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.plutus.spec.CostMdls;
import com.bloxbean.cardano.client.plutus.spec.CostModel;
import com.bloxbean.cardano.client.plutus.spec.Language;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.util.CostModelUtil;
import com.bloxbean.cardano.client.plutus.util.ScriptDataHashGenerator;

import java.util.Optional;

import static com.bloxbean.cardano.client.transaction.util.CostModelUtil.*;

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
                || (transaction.getWitnessSet().getPlutusV3Scripts() != null
                && transaction.getWitnessSet().getPlutusV3Scripts().size() > 0)
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
                Optional<CostModel>  costModel = CostModelUtil.getCostModelFromProtocolParams(ctx.getProtocolParams(), Language.PLUTUS_V1);
                costMdls.add(costModel.orElse(PlutusV1CostModel));
            }

            if (transaction.getWitnessSet().getPlutusV2Scripts() != null
                    && transaction.getWitnessSet().getPlutusV2Scripts().size() > 0) {
                Optional<CostModel>  costModel = CostModelUtil.getCostModelFromProtocolParams(ctx.getProtocolParams(), Language.PLUTUS_V2);
                costMdls.add(costModel.orElse(PlutusV2CostModel));
            }

            if (transaction.getWitnessSet().getPlutusV3Scripts() != null
                    && transaction.getWitnessSet().getPlutusV3Scripts().size() > 0) {
                Optional<CostModel>  costModel = CostModelUtil.getCostModelFromProtocolParams(ctx.getProtocolParams(), Language.PLUTUS_V3);
                costMdls.add(costModel.orElse(PlutusV3CostModel));
            }

            if (costMdls.isEmpty()) { //Check if costmodel can be decided from other fields //TODO Impl : Check what to do for v3 reference scripts
                if (transaction.getBody().getReferenceInputs() != null
                        && transaction.getBody().getReferenceInputs().size() > 0) { //If reference input is there, then plutus v2
                    var refInputLanguage = ctx.getReferenceInputLanguage();
                    Optional<CostModel> costModel = CostModelUtil.getCostModelFromProtocolParams(ctx.getProtocolParams(), refInputLanguage);
                    costMdls.add(costModel.orElse(PlutusV2CostModel));
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
