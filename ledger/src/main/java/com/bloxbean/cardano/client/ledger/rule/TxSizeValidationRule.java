package com.bloxbean.cardano.client.ledger.rule;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.ValidationError;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.ledger.LedgerContext;
import com.bloxbean.cardano.client.transaction.spec.Transaction;

import java.util.Collections;
import java.util.List;

/**
 * Validates that the serialized transaction size does not exceed maxTxSize.
 */
public class TxSizeValidationRule implements LedgerRule {

    private static final String RULE_NAME = "TxSizeValidation";

    @Override
    public List<ValidationError> validate(LedgerContext context, Transaction transaction) {
        ProtocolParams pp = context.getProtocolParams();
        if (pp == null || pp.getMaxTxSize() == null) {
            return Collections.emptyList();
        }

        int maxTxSize = pp.getMaxTxSize();
        int txSize;
        try {
            txSize = transaction.serialize().length;
        } catch (CborSerializationException e) {
            throw new CborRuntimeException("Failed to serialize transaction for size check", e);
        }

        if (txSize > maxTxSize) {
            return List.of(ValidationError.builder()
                    .rule(RULE_NAME)
                    .message("Transaction size " + txSize + " bytes exceeds maxTxSize " + maxTxSize + " bytes")
                    .phase(ValidationError.Phase.PHASE_1)
                    .build());
        }

        return Collections.emptyList();
    }
}
