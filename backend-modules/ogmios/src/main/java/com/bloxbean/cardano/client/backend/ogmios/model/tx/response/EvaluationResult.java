package com.bloxbean.cardano.client.backend.ogmios.model.tx.response;

import com.bloxbean.cardano.client.transaction.spec.ExUnits;
import com.bloxbean.cardano.client.transaction.spec.RedeemerTag;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class EvaluationResult {
    private RedeemerTag redeemerTag;
    private int index;
    private ExUnits exUnits;
}
