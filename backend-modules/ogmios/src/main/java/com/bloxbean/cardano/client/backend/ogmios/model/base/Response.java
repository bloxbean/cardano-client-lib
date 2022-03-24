package com.bloxbean.cardano.client.backend.ogmios.model.base;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Response extends Message {
    private JsonNode fault;

    public Response(long msgId) {
        super(msgId);
    }

}
