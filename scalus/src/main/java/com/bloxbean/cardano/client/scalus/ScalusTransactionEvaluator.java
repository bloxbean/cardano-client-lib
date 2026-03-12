package com.bloxbean.cardano.client.scalus;

import com.bloxbean.cardano.client.api.TransactionEvaluator;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.model.SlotConfig;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.scalus.bridge.EvaluationEntry;
import com.bloxbean.cardano.client.scalus.bridge.LedgerBridge;
import com.bloxbean.cardano.client.scalus.bridge.SlotConfigBridge;
import com.bloxbean.cardano.client.scalus.bridge.SlotConfigHandle;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A {@link TransactionEvaluator} implementation that computes Plutus script execution costs
 * (ExUnits: memory + CPU steps) using the Scalus library offline.
 * <p>
 * Uses {@code EvaluatorMode.EvaluateAndComputeCost} to actually run scripts and measure costs,
 * unlike {@link ScalusTransactionValidator} which only validates.
 * <p>
 * Usage:
 * <pre>
 * var evaluator = ScalusTransactionEvaluator.builder()
 *     .protocolParams(protocolParams)
 *     .slotConfig(new SlotConfig(1000, 0, 1666656000000L))
 *     .build();
 *
 * // Standalone
 * Result&lt;List&lt;EvaluationResult&gt;&gt; result = evaluator.evaluateTx(txCbor, inputUtxos);
 *
 * // With QuickTxBuilder
 * quickTxBuilder.compose(scriptTx)
 *     .withTxEvaluator(evaluator)
 *     .withSigner(signer)
 *     .completeAndWait();
 * </pre>
 */
@Slf4j
@Builder
public class ScalusTransactionEvaluator implements TransactionEvaluator {

    private final ProtocolParams protocolParams;
    private final SlotConfig slotConfig;
    @Builder.Default
    private final int networkId = 0; // 0 = testnet, 1 = mainnet
    @Builder.Default
    private final long currentSlot = -1; // -1 = auto-detect from tx

    @Override
    public Result<List<EvaluationResult>> evaluateTx(byte[] cbor, Set<Utxo> inputUtxos) {
        try {
            long slot = resolveSlot(cbor);

            SlotConfigHandle slotConfigHandle = convertSlotConfig();

            List<EvaluationEntry> entries = LedgerBridge.evaluate(
                    cbor, protocolParams, inputUtxos, slot, slotConfigHandle, networkId);

            List<EvaluationResult> results = entries.stream()
                    .map(ScalusTransactionEvaluator::toEvaluationResult)
                    .collect(Collectors.toList());

            log.debug("Script evaluation completed: {} redeemer(s) evaluated", results.size());
            return Result.success("Script evaluation successful").withValue(results);

        } catch (Exception e) {
            log.debug("Script evaluation failed: {}", e.getMessage());
            return Result.error("Script evaluation failed: " + e.getMessage());
        }
    }

    @Override
    public Result<List<EvaluationResult>> evaluateTx(Transaction transaction, Set<Utxo> inputUtxos) {
        try {
            byte[] cbor = transaction.serialize();
            return evaluateTx(cbor, inputUtxos);
        } catch (Exception e) {
            return Result.error("Failed to serialize transaction: " + e.getMessage());
        }
    }

    private long resolveSlot(byte[] cbor) {
        if (currentSlot >= 0) {
            return currentSlot;
        }
        try {
            Transaction tx = Transaction.deserialize(cbor);
            long validityStart = tx.getBody().getValidityStartInterval();
            return validityStart > 0 ? validityStart : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private SlotConfigHandle convertSlotConfig() {
        if (slotConfig == null) {
            return SlotConfigBridge.preview();
        }
        return SlotConfigBridge.custom(
                slotConfig.getZeroTime(),
                slotConfig.getZeroSlot(),
                slotConfig.getSlotLength()
        );
    }

    private static EvaluationResult toEvaluationResult(EvaluationEntry entry) {
        return EvaluationResult.builder()
                .redeemerTag(RedeemerTag.convert(entry.tag()))
                .index(entry.index())
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(entry.memory()))
                        .steps(BigInteger.valueOf(entry.steps()))
                        .build())
                .build();
    }
}
