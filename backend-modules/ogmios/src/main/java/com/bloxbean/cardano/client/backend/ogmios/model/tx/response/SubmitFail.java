package com.bloxbean.cardano.client.backend.ogmios.model.tx.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@AllArgsConstructor
@ToString
public class SubmitFail {
    String error;
}
