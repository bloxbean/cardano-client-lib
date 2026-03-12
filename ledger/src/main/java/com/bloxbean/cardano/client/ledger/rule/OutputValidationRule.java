package com.bloxbean.cardano.client.ledger.rule;

import com.bloxbean.cardano.client.api.MinAdaCalculator;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.ValidationError;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.ledger.LedgerContext;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Validates transaction outputs against Cardano Conway-era rules:
 * <ul>
 *   <li>Each output must contain at least minUTxO lovelace</li>
 *   <li>Each output's serialized value size must not exceed maxValueSize</li>
 *   <li>Output value must not contain negative quantities</li>
 * </ul>
 */
public class OutputValidationRule implements LedgerRule {

    private static final String RULE_NAME = "OutputValidation";

    @Override
    public List<ValidationError> validate(LedgerContext context, Transaction transaction) {
        ProtocolParams pp = context.getProtocolParams();
        if (pp == null) {
            return Collections.emptyList();
        }

        List<TransactionOutput> outputs = transaction.getBody().getOutputs();
        if (outputs == null || outputs.isEmpty()) {
            return List.of(error("Transaction has no outputs"));
        }

        List<ValidationError> errors = new ArrayList<>();
        MinAdaCalculator minAdaCalculator = new MinAdaCalculator(pp);

        for (int i = 0; i < outputs.size(); i++) {
            TransactionOutput output = outputs.get(i);
            Value value = output.getValue();

            if (value == null) {
                errors.add(error("Output[" + i + "] has no value"));
                continue;
            }

            // 1. Check coin is non-negative
            BigInteger coin = value.getCoin();
            if (coin != null && coin.signum() < 0) {
                errors.add(error("Output[" + i + "] has negative coin value: " + coin));
            }

            // 2. Check minUTxO
            if (pp.getCoinsPerUtxoSize() != null && !pp.getCoinsPerUtxoSize().isEmpty()) {
                try {
                    BigInteger minAda = minAdaCalculator.calculateMinAda(output);
                    if (coin != null && coin.compareTo(minAda) < 0) {
                        errors.add(error("Output[" + i + "] coin " + coin
                                + " is below minUTxO " + minAda));
                    }
                } catch (CborRuntimeException e) {
                    errors.add(error("Output[" + i + "] failed to calculate minUTxO: " + e.getMessage()));
                }
            }

            // 3. Check max value size
            if (pp.getMaxValSize() != null && !pp.getMaxValSize().isEmpty()) {
                long maxValSize = Long.parseLong(pp.getMaxValSize());
                try {
                    byte[] serializedValue = CborSerializationUtil.serialize(value.serialize());
                    if (serializedValue.length > maxValSize) {
                        errors.add(error("Output[" + i + "] value size " + serializedValue.length
                                + " bytes exceeds maxValueSize " + maxValSize + " bytes"));
                    }
                } catch (Exception e) {
                    errors.add(error("Output[" + i + "] failed to serialize value for size check: " + e.getMessage()));
                }
            }
        }

        return errors;
    }

    private ValidationError error(String message) {
        return ValidationError.builder()
                .rule(RULE_NAME)
                .message(message)
                .phase(ValidationError.Phase.PHASE_1)
                .build();
    }
}
