package com.bloxbean.cardano.client.ledger;

import com.bloxbean.cardano.client.api.TransactionValidator;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.model.ValidationError;
import com.bloxbean.cardano.client.api.model.ValidationResult;
import com.bloxbean.cardano.client.ledger.rule.*;
import com.bloxbean.cardano.client.ledger.slice.*;
import com.bloxbean.cardano.client.ledger.util.UtxoUtil;
import com.bloxbean.cardano.client.plutus.spec.CostMdls;
import com.bloxbean.cardano.client.spec.NetworkId;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import lombok.Builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pure-Java implementation of Cardano Conway-era transaction validation.
 * <p>
 * Implements {@link TransactionValidator} by running a set of {@link LedgerRule}s
 * and collecting all validation errors. By default, includes all Phase 1 rules
 * (basic checks, output validation, fee validation, etc.).
 * <p>
 * <b>Basic usage (backward compatible):</b>
 * <pre>
 * LedgerStateValidator validator = LedgerStateValidator.builder()
 *     .protocolParams(pp)
 *     .currentSlot(slot)
 *     .networkId(NetworkId.TESTNET)
 *     .build();
 *
 * ValidationResult result = validator.validateTx(transaction, inputUtxos);
 * </pre>
 *
 * <b>Scenario-based validation with state slices:</b>
 * <pre>
 * // Define offline state: "stake key X is registered"
 * LedgerStateValidator validator = LedgerStateValidator.builder()
 *     .protocolParams(pp)
 *     .currentSlot(slot)
 *     .networkId(NetworkId.TESTNET)
 *     .accountsSlice(new SimpleAccountsSlice(
 *         Map.of(stakeKeyHash, BigInteger.ZERO),
 *         Map.of(stakeKeyHash, BigInteger.valueOf(2000000))))
 *     .build();
 *
 * // Full governance state
 * LedgerStateValidator validator = LedgerStateValidator.builder()
 *     .protocolParams(pp)
 *     .currentSlot(slot)
 *     .currentEpoch(100)
 *     .networkId(NetworkId.TESTNET)
 *     .accountsSlice(accountsSlice)
 *     .poolsSlice(poolsSlice)
 *     .drepsSlice(drepsSlice)
 *     .committeeSlice(committeeSlice)
 *     .proposalsSlice(proposalsSlice)
 *     .build();
 * </pre>
 *
 * When state slices are not provided, stateful checks in
 * {@link CertificateValidationRule} and {@link GovernanceValidationRule}
 * are silently skipped (degraded mode).
 */
public class LedgerStateValidator implements TransactionValidator {

    private final ProtocolParams protocolParams;
    private final long currentSlot;
    private final long currentEpoch;
    private final NetworkId networkId;
    private final CostMdls costMdls;
    private final AccountsSlice accountsSlice;
    private final PoolsSlice poolsSlice;
    private final DRepsSlice drepsSlice;
    private final CommitteeSlice committeeSlice;
    private final ProposalsSlice proposalsSlice;
    private final List<LedgerRule> rules;

    @Builder
    public LedgerStateValidator(ProtocolParams protocolParams, long currentSlot, long currentEpoch,
                                NetworkId networkId, CostMdls costMdls,
                                AccountsSlice accountsSlice, PoolsSlice poolsSlice,
                                DRepsSlice drepsSlice, CommitteeSlice committeeSlice,
                                ProposalsSlice proposalsSlice, List<LedgerRule> customRules) {
        this.protocolParams = protocolParams;
        this.currentSlot = currentSlot;
        this.currentEpoch = currentEpoch;
        this.networkId = networkId;
        this.costMdls = costMdls;
        this.accountsSlice = accountsSlice;
        this.poolsSlice = poolsSlice;
        this.drepsSlice = drepsSlice;
        this.committeeSlice = committeeSlice;
        this.proposalsSlice = proposalsSlice;
        this.rules = customRules != null ? customRules : defaultRules();
    }

    @Override
    public ValidationResult validateTx(Transaction transaction, Set<Utxo> inputUtxos) {
        // Build context with resolved UTxO slice and any configured state slices
        SimpleUtxoSlice utxoSlice = new SimpleUtxoSlice(UtxoUtil.toUtxoMap(inputUtxos));
        LedgerContext context = LedgerContext.builder()
                .protocolParams(protocolParams)
                .currentSlot(currentSlot)
                .currentEpoch(currentEpoch)
                .networkId(networkId)
                .costMdls(costMdls)
                .utxoSlice(utxoSlice)
                .accountsSlice(accountsSlice)
                .poolsSlice(poolsSlice)
                .drepsSlice(drepsSlice)
                .committeeSlice(committeeSlice)
                .proposalsSlice(proposalsSlice)
                .build();

        return validate(context, transaction);
    }

    /**
     * Validate a transaction with a pre-built context.
     * Useful for Yaci or other callers who manage their own slices.
     *
     * @param context     the pre-built ledger context with slices
     * @param transaction the transaction to validate
     * @return validation result
     */
    public ValidationResult validate(LedgerContext context, Transaction transaction) {
        List<ValidationError> allErrors = new ArrayList<>();

        for (LedgerRule rule : rules) {
            List<ValidationError> errors = rule.validate(context, transaction);
            if (errors != null && !errors.isEmpty()) {
                allErrors.addAll(errors);
            }
        }

        if (allErrors.isEmpty()) {
            return ValidationResult.success();
        }
        return ValidationResult.failure(allErrors);
    }

    private static List<LedgerRule> defaultRules() {
        return List.of(
                new InputValidationRule(),
                new TxSizeValidationRule(),
                new ValidityIntervalRule(),
                new NetworkIdValidationRule(),
                new OutputValidationRule(),
                new FeeAndCollateralRule(),
                new ValueConservationRule(),
                new WitnessValidationRule(),
                new CertificateValidationRule(),
                new GovernanceValidationRule()
        );
    }
}
