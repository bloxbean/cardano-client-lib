package com.bloxbean.cardano.client.ledger.rule;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.model.ValidationError;
import com.bloxbean.cardano.client.ledger.LedgerContext;
import com.bloxbean.cardano.client.spec.NetworkId;
import com.bloxbean.cardano.client.transaction.spec.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates network ID consistency across the transaction:
 * <ul>
 *   <li>If the tx body specifies a network ID, it must match the expected network</li>
 *   <li>All output addresses must be for the expected network</li>
 *   <li>All withdrawal (reward) addresses must be for the expected network</li>
 * </ul>
 */
public class NetworkIdValidationRule implements LedgerRule {

    private static final String RULE_NAME = "NetworkIdValidation";

    @Override
    public List<ValidationError> validate(LedgerContext context, Transaction transaction) {
        List<ValidationError> errors = new ArrayList<>();
        TransactionBody body = transaction.getBody();
        NetworkId expectedNetwork = context.getNetworkId();

        if (expectedNetwork == null) {
            return errors; // Cannot validate without expected network
        }

        int expectedNetworkInt = expectedNetwork == NetworkId.MAINNET ? 1 : 0;

        // 1. Check tx body network ID field (if set)
        NetworkId txNetworkId = body.getNetworkId();
        if (txNetworkId != null && txNetworkId != expectedNetwork) {
            errors.add(error("Transaction body network ID " + txNetworkId
                    + " does not match expected " + expectedNetwork));
        }

        // 2. Check all output addresses
        List<TransactionOutput> outputs = body.getOutputs();
        if (outputs != null) {
            for (int i = 0; i < outputs.size(); i++) {
                TransactionOutput output = outputs.get(i);
                if (output.getAddress() != null) {
                    try {
                        Address addr = new Address(output.getAddress());
                        if (addr.getNetwork() != null && addr.getNetwork().getNetworkId() != expectedNetworkInt) {
                            errors.add(error("Output[" + i + "] address has network ID "
                                    + addr.getNetwork().getNetworkId()
                                    + ", expected " + expectedNetworkInt));
                        }
                    } catch (Exception e) {
                        // Address parsing failure — skip network check for this output
                        // (could be a Byron address or malformed; other rules will catch it)
                    }
                }
            }
        }

        // 3. Check all withdrawal addresses
        List<Withdrawal> withdrawals = body.getWithdrawals();
        if (withdrawals != null) {
            for (Withdrawal withdrawal : withdrawals) {
                if (withdrawal.getRewardAddress() != null) {
                    try {
                        Address addr = new Address(withdrawal.getRewardAddress());
                        if (addr.getNetwork() != null && addr.getNetwork().getNetworkId() != expectedNetworkInt) {
                            errors.add(error("Withdrawal address " + withdrawal.getRewardAddress()
                                    + " has network ID " + addr.getNetwork().getNetworkId()
                                    + ", expected " + expectedNetworkInt));
                        }
                    } catch (Exception e) {
                        // Skip network check for unparseable addresses
                    }
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
