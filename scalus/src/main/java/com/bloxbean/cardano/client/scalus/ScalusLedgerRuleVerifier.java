package com.bloxbean.cardano.client.scalus;

import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.quicktx.Verifier;
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
 * A {@link Verifier} implementation that validates transactions against Cardano's
 * full UTxO ledger rules using the Scalus library.
 * <p>
 * This verifier performs Phase 1 (structural) and Phase 2 (script execution) validation
 * offline, without requiring a running Cardano node.
 * <p>
 * Usage:
 * <pre>
 * var verifier = ScalusLedgerRuleVerifier.builder()
 *     .protocolParamsSupplier(protocolParamsSupplier)
 *     .utxoSupplier(utxoSupplier)
 *     .slotConfig(SlotConfigBridge.preview())
 *     .build();
 *
 * var result = quickTxBuilder.compose(tx)
 *     .withSigner(signer)
 *     .withVerifier(verifier)
 *     .completeAndWait();
 * </pre>
 */
@Slf4j
@Builder
public class ScalusLedgerRuleVerifier implements Verifier {

    private final ProtocolParamsSupplier protocolParamsSupplier;
    private final UtxoSupplier utxoSupplier;
    private final SlotConfigHandle slotConfig;
    @Builder.Default
    private final int networkId = 0; // 0 = testnet, 1 = mainnet

    @Override
    public void verify(Transaction transaction) throws VerifierException {
        try {
            byte[] txCbor = transaction.serialize();

            ProtocolParams protocolParams = protocolParamsSupplier.getProtocolParams();

            // Resolve input UTxOs
            Set<Utxo> inputUtxos = resolveUtxos(transaction);

            // Determine current slot from tx validity interval
            long currentSlot = 0;
            if (transaction.getBody().getValidityStartInterval() > 0) {
                currentSlot = transaction.getBody().getValidityStartInterval();
            }

            SlotConfigHandle sc = slotConfig != null ? slotConfig : SlotConfigBridge.preview();

            TransitResult result = LedgerBridge.validate(
                    txCbor, protocolParams, inputUtxos, currentSlot, sc, networkId);

            if (!result.isSuccess()) {
                throw new VerifierException("Ledger rule validation failed: " + result.errorMessage());
            }

            log.debug("Transaction passed all ledger rule validations");

        } catch (VerifierException e) {
            throw e;
        } catch (Exception e) {
            throw new VerifierException("Transaction validation error: " + e.getMessage());
        }
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
