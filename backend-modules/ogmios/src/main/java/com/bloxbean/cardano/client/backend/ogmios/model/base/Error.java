package com.bloxbean.cardano.client.backend.ogmios.model.base;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = true)
public class Error extends Response {
    String errorMsg;

    public Error(long msgId) {
        super(msgId);
    }

}
