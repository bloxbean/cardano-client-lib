package com.bloxbean.cardano.client.backend.ogmios.model.base;

import com.bloxbean.cardano.client.api.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.backend.ogmios.model.query.request.QueryType;
import com.bloxbean.cardano.client.backend.ogmios.model.query.response.QueryResponse;
import com.bloxbean.cardano.client.backend.ogmios.util.JsonHelper;
import com.bloxbean.cardano.client.backend.ogmios.model.tx.response.EvaluateTxResponse;
import com.bloxbean.cardano.client.backend.ogmios.model.tx.response.SubmitTxResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

@Data
@AllArgsConstructor
@Slf4j
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {
    private long msgId;

    public static Message deserialize(String messageJson) {
        try {
            RawResponse rawResponse = JsonHelper.toObject(messageJson, RawResponse.class);

            if (rawResponse == null)
                throw new ApiRuntimeException("Could not parse response");

            long msgId = 0;
            if (rawResponse.getReflection() != null) {
                msgId = rawResponse.getReflection().get("msg_id").asLong();
            }

            if (rawResponse.getType().equals("jsonwsp/fault")) {
                Error error = new Error(msgId);
                error.setFault(rawResponse.getFault());
            } else {
                MethodType methodType = MethodType.convert(rawResponse.getMethodname());
                switch (Objects.requireNonNull(methodType)) {
                    case SUBMIT_TX:
                        return SubmitTxResponse.deserialize(msgId, rawResponse.getResult());
                    case EVALUATE_TX:
                        return EvaluateTxResponse.deserialize(msgId, rawResponse.getResult());
                    case QUERY: {
                        QueryType queryType = QueryType.convert(rawResponse.getReflection().get("object").asText());
                        return QueryResponse.parse(queryType, msgId, rawResponse.getResult());
                    }
                }
            }

        } catch (JsonProcessingException e) {
            log.warn("Cannot deserialize message. Message does not contain \"reflection\" parameter", e);
        }
        return null;
    }
}
