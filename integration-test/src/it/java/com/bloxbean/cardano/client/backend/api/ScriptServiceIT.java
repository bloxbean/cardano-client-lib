package com.bloxbean.cardano.client.backend.api;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFTransactionService;
import com.bloxbean.cardano.client.backend.model.ScriptDatum;
import com.bloxbean.cardano.client.backend.model.ScriptDatumCbor;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.backend.model.TxContentRedeemers;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScriptServiceIT extends BaseITTest {

    ScriptService scriptService;

    @BeforeEach
    public void setup() {
        scriptService = getBackendService().getScriptService();
    }


    @Test
    void testGetDatumValue() throws Exception {
        String txnHash = "f6e8eb483f0341a8b16afce3b957eb5de1aee47376671ae29de733b11856ce0b";
        Result<ScriptDatum> result = scriptService.getScriptDatum(txnHash);

        assertNotNull(result.getValue());
        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
    }

    @Test
    void testGetDatumValueCbor() throws Exception {
        String txnHash = "f6e8eb483f0341a8b16afce3b957eb5de1aee47376671ae29de733b11856ce0b";
        Result<ScriptDatumCbor> result = scriptService.getScriptDatumCbor(txnHash);

        assertNotNull(result.getValue());
        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
    }

}
