package com.bloxbean.cardano.client.backend.ogmios.model.tx.response;

import com.bloxbean.cardano.client.backend.ogmios.model.base.Response;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = true)
public class SubmitTxResponse extends Response {

    private SubmitFail submitFail;

    public SubmitTxResponse(long msgId) {
        super(msgId);
    }

    public static SubmitTxResponse deserialize(long msgId, JsonNode resultNode) {
        SubmitTxResponse submitTxResponse = new SubmitTxResponse(msgId);
        if (resultNode.has("SubmitFail")) {
            submitTxResponse.setSubmitFail(new SubmitFail(resultNode.get("SubmitFail").toString()));
        }

        return submitTxResponse;
    }
}
