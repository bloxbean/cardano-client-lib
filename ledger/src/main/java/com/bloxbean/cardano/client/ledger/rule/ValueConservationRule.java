package com.bloxbean.cardano.client.ledger.rule;

import com.bloxbean.cardano.client.api.model.ValidationError;
import com.bloxbean.cardano.client.ledger.LedgerContext;
import com.bloxbean.cardano.client.ledger.util.TxBalanceCalculator;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.Value;

import java.util.Collections;
import java.util.List;

/**
 * Validates the value conservation equation (Category B):
 * <p>
 * consumed(tx) == produced(tx)
 * <p>
 * Where:
 * <ul>
 *   <li>consumed = inputs + positive_mint + withdrawals + stake_refunds + drep_refunds</li>
 *   <li>produced = outputs + burned_tokens + fee + deposits + proposal_deposits + donation</li>
 * </ul>
 * <p>
 * Reference: Scalus ValueNotConservedUTxOValidator, Haskell Shelley.validateValueNotConservedUTxO
 */
public class ValueConservationRule implements LedgerRule {

    private static final String RULE_NAME = "ValueConservation";

    @Override
    public List<ValidationError> validate(LedgerContext context, Transaction transaction) {
        if (context.getProtocolParams() == null || context.getUtxoSlice() == null) {
            return Collections.emptyList();
        }

        Value consumed = TxBalanceCalculator.consumed(context.getUtxoSlice(), transaction, context.getProtocolParams());
        if (consumed == null) {
            // Missing UTxO input — this is already caught by InputValidationRule
            return Collections.emptyList();
        }

        Value produced = TxBalanceCalculator.produced(transaction, context.getProtocolParams());

        if (!valuesEqual(consumed, produced)) {
            return List.of(ValidationError.builder()
                    .rule(RULE_NAME)
                    .message("Value not conserved. Consumed: " + formatValue(consumed)
                            + ", Produced: " + formatValue(produced))
                    .phase(ValidationError.Phase.PHASE_1)
                    .build());
        }

        return Collections.emptyList();
    }

    /**
     * Compare two values for equality (coin + all multi-assets).
     */
    private boolean valuesEqual(Value a, Value b) {
        // Compare coins
        var coinA = a.getCoin() != null ? a.getCoin() : java.math.BigInteger.ZERO;
        var coinB = b.getCoin() != null ? b.getCoin() : java.math.BigInteger.ZERO;
        if (coinA.compareTo(coinB) != 0) return false;

        // Compare multi-assets by computing a - b and checking all are zero
        Value diff = a.minus(b);
        if (diff.getMultiAssets() != null) {
            return diff.getMultiAssets().stream()
                    .allMatch(ma -> ma.getAssets().stream()
                            .allMatch(asset -> asset.getValue() == null
                                    || asset.getValue().signum() == 0));
        }
        return true;
    }

    private String formatValue(Value v) {
        StringBuilder sb = new StringBuilder();
        sb.append(v.getCoin() != null ? v.getCoin() : "0").append(" lovelace");
        if (v.getMultiAssets() != null && !v.getMultiAssets().isEmpty()) {
            sb.append(" + ").append(v.getMultiAssets().size()).append(" policy(ies)");
        }
        return sb.toString();
    }
}
