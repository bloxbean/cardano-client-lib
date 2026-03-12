package com.bloxbean.cardano.client.ledger.rule;

import com.bloxbean.cardano.client.api.model.ValidationError;
import com.bloxbean.cardano.client.ledger.LedgerContext;
import com.bloxbean.cardano.client.transaction.spec.Transaction;

import java.util.List;

/**
 * A single ledger validation rule.
 * <p>
 * Each rule checks one aspect of a transaction against the Cardano Conway-era
 * ledger specification and returns a list of validation errors (empty if valid).
 */
public interface LedgerRule {

    /**
     * Validate the transaction against this rule.
     *
     * @param context     ledger context (protocol params, slot, network, state slices)
     * @param transaction the transaction to validate
     * @return list of validation errors; empty list means the rule passed
     */
    List<ValidationError> validate(LedgerContext context, Transaction transaction);
}
