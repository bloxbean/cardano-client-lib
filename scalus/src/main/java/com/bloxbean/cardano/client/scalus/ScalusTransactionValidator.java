package com.bloxbean.cardano.client.scalus;

import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionValidator;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.model.ValidationError;
import com.bloxbean.cardano.client.api.model.ValidationResult;
import com.bloxbean.cardano.client.quicktx.VerifierException;
import com.bloxbean.cardano.client.scalus.bridge.LedgerBridge;
import com.bloxbean.cardano.client.scalus.bridge.SlotConfigBridge;
import com.bloxbean.cardano.client.scalus.bridge.SlotConfigHandle;
import com.bloxbean.cardano.client.scalus.bridge.TransitResult;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * A {@link TransactionValidator} implementation that validates transactions against Cardano's
 * full UTxO ledger rules using the Scalus library.
 * <p>
 * Returns structured {@link ValidationResult} with per-rule error information instead of throwing exceptions.
 * This validator can be used with {@code QuickTxBuilder.withTxValidator()} which passes resolved UTxOs
 * directly from the build context, avoiding redundant UTXO fetches.
 * <p>
 * Usage:
 * <pre>
 * var validator = ScalusTransactionValidator.builder()
 *     .protocolParams(protocolParams)
 *     .slotConfig(SlotConfigBridge.preview())
 *     .build();
 *
 * var result = quickTxBuilder.compose(tx)
 *     .withSigner(signer)
 *     .withTxValidator(validator)
 *     .completeAndWait();
 * </pre>
 */
@Slf4j
@Builder
public class ScalusTransactionValidator implements TransactionValidator {

    private final ProtocolParamsSupplier protocolParamsSupplier;
    private final UtxoSupplier utxoSupplier;
    private final SlotConfigHandle slotConfig;
    private final scalus.bloxbean.ScriptSupplier scriptSupplier;
    @Builder.Default
    private final int networkId = 0; // 0 = testnet, 1 = mainnet

    @Override
    public ValidationResult validateTx(Transaction transaction, Set<Utxo> inputUtxos) {
        try {
            byte[] txCbor = transaction.serialize();

            ProtocolParams protocolParams = protocolParamsSupplier.getProtocolParams();

            // Determine current slot from tx validity interval
            long currentSlot = 0;
            if (transaction.getBody().getValidityStartInterval() > 0) {
                currentSlot = transaction.getBody().getValidityStartInterval();
            }

            SlotConfigHandle sc = slotConfig != null ? slotConfig : SlotConfigBridge.preview();

            TransitResult result = LedgerBridge.validate(
                    txCbor, protocolParams, inputUtxos, currentSlot, sc, networkId, scriptSupplier);

            if (!result.isSuccess()) {
                ValidationError validationError = mapError(result);
                log.debug("Transaction validation failed: {} - {}", validationError.getRule(), validationError.getMessage());
                return ValidationResult.failure(validationError);
            }

            log.debug("Transaction passed all ledger rule validations");
            return ValidationResult.success();
        } catch (Exception e) {
            ValidationError error = ValidationError.builder()
                    .rule("InternalError")
                    .message("Validation error: " + e.getMessage())
                    .phase(ValidationError.Phase.PHASE_1)
                    .build();
            return ValidationResult.failure(error);
        }
    }

    private ValidationError mapError(TransitResult result) {
        String className = result.errorClassName() != null ? result.errorClassName() : "Unknown";
        String message = result.errorMessage();

        ValidationError.Phase phase = className.contains("PlutusScript") || className.contains("Script")
                ? ValidationError.Phase.PHASE_2
                : ValidationError.Phase.PHASE_1;

        String rule = className.replace("Exception", "").replace("$", "");

        return ValidationError.builder()
                .rule(rule)
                .message(message)
                .phase(phase)
                .build();
    }

    private Set<Utxo> resolveUtxos(Transaction transaction) {
        Set<Utxo> utxos = new HashSet<>();

        if (transaction.getBody().getInputs() != null) {
            for (TransactionInput input : transaction.getBody().getInputs()) {
                utxoSupplier.getTxOutput(input.getTransactionId(), input.getIndex())
                        .ifPresent(utxos::add);
            }
        }

        if (transaction.getBody().getReferenceInputs() != null) {
            for (TransactionInput input : transaction.getBody().getReferenceInputs()) {
                utxoSupplier.getTxOutput(input.getTransactionId(), input.getIndex())
                        .ifPresent(utxos::add);
            }
        }

        if (transaction.getBody().getCollateral() != null) {
            for (TransactionInput input : transaction.getBody().getCollateral()) {
                utxoSupplier.getTxOutput(input.getTransactionId(), input.getIndex())
                        .ifPresent(utxos::add);
            }
        }

        return utxos;
    }
}
