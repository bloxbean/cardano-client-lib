package com.bloxbean.cardano.client.txflow.exec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class ConfirmationOutcomeTest {

    @Test
    void recoveryRequiredPreservesTypedReconciliationFailure() {
        ReconciliationUncertainException uncertainty =
                new ReconciliationUncertainException("tx1");
        ConfirmationResult result = ConfirmationResult.builder()
                .txHash("tx1")
                .status(ConfirmationStatus.SUBMITTED)
                .confirmationDepth(-1)
                .error(uncertainty)
                .build();

        ConfirmationOutcome outcome = ConfirmationOutcome.recoveryRequired("tx1", result);

        assertSame(uncertainty, outcome.getError());
    }
}
