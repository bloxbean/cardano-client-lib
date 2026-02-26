package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.UtxoUtil;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.quicktx.intent.*;
import com.bloxbean.cardano.client.transaction.spec.ChangeOutput;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.Value;

import java.math.BigInteger;
import java.util.*;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

/**
 * Static helpers for Phase 4 deposit resolution.
 * <p>
 * Determines how to fund protocol deposits (stake registration, pool registration,
 * DRep registration, governance proposals) by deducting from existing outputs or
 * selecting new UTXOs.
 * <p>
 * Follows the naming convention of {@code InputBuilders}, {@code FeeCalculators}, etc.
 */
class DepositResolvers {

    private DepositResolvers() { /* static utility */ }

    /**
     * Returns a {@link TxBuilder} that resolves deposits for the given intentions.
     * If no deposit intents are present, returns a no-op builder.
     *
     * @param intentions          the transaction intents
     * @param depositPayerAddress explicit deposit payer (may be null)
     * @param fromAddress         fallback sender address
     * @param mode                deposit resolution mode
     * @return TxBuilder that resolves deposits (no-op when nothing to resolve)
     */
    public static TxBuilder resolveDeposits(List<TxIntent> intentions,
                                            String depositPayerAddress,
                                            String fromAddress,
                                            DepositMode mode) {
        if (!hasDepositIntents(intentions)) {
            return (ctx, txn) -> { /* no-op */ };
        }

        String depositAddr = depositPayerAddress != null ? depositPayerAddress : fromAddress;
        if (depositAddr == null) {
            return (ctx, txn) -> { /* no-op — no address to resolve against */ };
        }

        return (context, txn) -> {
            BigInteger totalDeposits = calculateTotalDeposits(intentions, context.getProtocolParams());
            if (totalDeposits.compareTo(BigInteger.ZERO) <= 0) return;

            switch (mode) {
                case AUTO:
                    resolveAuto(context, txn, depositAddr, totalDeposits);
                    break;

                case CHANGE_OUTPUT:
                    TransactionOutput changeOut = findChangeOutput(txn, depositAddr, totalDeposits);
                    if (changeOut == null)
                        throw new TxBuildException("No suitable change output at " + depositAddr
                                + " to cover deposit of " + totalDeposits + " lovelace");
                    deductDeposit(changeOut, totalDeposits);
                    break;

                case ANY_OUTPUT:
                    TransactionOutput anyOut = findAnyOutput(txn, depositAddr, totalDeposits);
                    if (anyOut == null)
                        throw new TxBuildException("No output at " + depositAddr
                                + " to cover deposit of " + totalDeposits + " lovelace");
                    deductDeposit(anyOut, totalDeposits);
                    break;

                case NEW_UTXO_SELECTION:
                    selectNewUtxosForDeposit(context, txn, depositAddr, totalDeposits);
                    break;
            }
        };
    }

    // ========== Package-private (testable from same package) ==========

    /**
     * Returns {@code true} if the intentions list contains any intent that requires a deposit.
     */
    static boolean hasDepositIntents(List<TxIntent> intentions) {
        if (intentions == null || intentions.isEmpty()) return false;
        return intentions.stream().anyMatch(intent ->
                intent instanceof StakeRegistrationIntent ||
                        (intent instanceof PoolRegistrationIntent && !((PoolRegistrationIntent) intent).isUpdate()) ||
                        intent instanceof DRepRegistrationIntent ||
                        intent instanceof GovernanceProposalIntent
        );
    }

    /**
     * Calculates the total deposit amount across all deposit-bearing intents.
     */
    static BigInteger calculateTotalDeposits(List<TxIntent> intentions, ProtocolParams protocolParams) {
        BigInteger total = BigInteger.ZERO;
        if (intentions == null) return total;
        for (TxIntent intent : intentions) {
            if (intent instanceof StakeRegistrationIntent) {
                total = total.add(DepositHelper.getDepositAmount(protocolParams,
                        DepositHelper.DepositType.STAKE_KEY_REGISTRATION));
            } else if (intent instanceof PoolRegistrationIntent
                    && !((PoolRegistrationIntent) intent).isUpdate()) {
                total = total.add(DepositHelper.getDepositAmount(protocolParams,
                        DepositHelper.DepositType.POOL_REGISTRATION));
            } else if (intent instanceof DRepRegistrationIntent) {
                BigInteger custom = ((DRepRegistrationIntent) intent).getDeposit();
                total = total.add(custom != null ? custom :
                        DepositHelper.getDepositAmount(protocolParams, DepositHelper.DepositType.DREP_REGISTRATION));
            } else if (intent instanceof GovernanceProposalIntent) {
                BigInteger custom = ((GovernanceProposalIntent) intent).getDeposit();
                total = total.add(custom != null ? custom :
                        DepositHelper.getDepositAmount(protocolParams, DepositHelper.DepositType.GOV_ACTION_PROPOSAL));
            }
        }
        return total;
    }

    // ========== Private helpers ==========

    private static void resolveAuto(TxBuilderContext context, Transaction txn,
                                    String depositAddr, BigInteger totalDeposits) {
        if (!context.isMergeOutputs()) {
            // mergeOutputs=false: ChangeOutput → UTXO selection → any output
            TransactionOutput changeOut = findChangeOutput(txn, depositAddr, totalDeposits);
            if (changeOut != null) {
                deductDeposit(changeOut, totalDeposits);
                return;
            }
            try {
                selectNewUtxosForDeposit(context, txn, depositAddr, totalDeposits);
                return;
            } catch (Exception e) { /* fall through */ }
            TransactionOutput anyOut = findAnyOutput(txn, depositAddr, totalDeposits);
            if (anyOut != null) {
                deductDeposit(anyOut, totalDeposits);
                return;
            }
        } else {
            // mergeOutputs=true: no ChangeOutput → any output → UTXO selection
            TransactionOutput anyOut = findAnyOutput(txn, depositAddr, totalDeposits);
            if (anyOut != null) {
                deductDeposit(anyOut, totalDeposits);
                return;
            }
            try {
                selectNewUtxosForDeposit(context, txn, depositAddr, totalDeposits);
                return;
            } catch (Exception e) { /* fall through */ }
        }
        throw new TxBuildException("Cannot resolve deposit of " + totalDeposits
                + " lovelace from address: " + depositAddr);
    }

    private static TransactionOutput findChangeOutput(Transaction txn, String addr, BigInteger minCoin) {
        return txn.getBody().getOutputs().stream()
                .filter(o -> o instanceof ChangeOutput
                        && o.getAddress().equals(addr)
                        && o.getValue() != null
                        && o.getValue().getCoin() != null
                        && o.getValue().getCoin().compareTo(minCoin) >= 0)
                .findFirst().orElse(null);
    }

    private static TransactionOutput findAnyOutput(Transaction txn, String addr, BigInteger minCoin) {
        return txn.getBody().getOutputs().stream()
                .filter(o -> o.getAddress().equals(addr)
                        && o.getValue() != null
                        && o.getValue().getCoin() != null
                        && o.getValue().getCoin().compareTo(minCoin) >= 0)
                .findFirst().orElse(null);
    }

    private static void deductDeposit(TransactionOutput output, BigInteger amount) {
        output.getValue().setCoin(output.getValue().getCoin().subtract(amount));
    }

    private static void selectNewUtxosForDeposit(TxBuilderContext context, Transaction txn,
                                                 String depositAddr, BigInteger totalDeposits) {
        Set<Utxo> excludeSet = new HashSet<>(context.getUtxos());
        Set<Utxo> depositUtxos = context.getUtxoSelectionStrategy()
                .select(depositAddr, new Amount(LOVELACE, totalDeposits), excludeSet);

        // Add selected UTXOs as inputs
        for (Utxo utxo : depositUtxos) {
            txn.getBody().getInputs().add(new TransactionInput(utxo.getTxHash(), utxo.getOutputIndex()));
            context.addUtxo(utxo);
        }

        // Create change output for remaining value
        ChangeOutput depositChangeOutput = new ChangeOutput(depositAddr,
                Value.builder().coin(BigInteger.ZERO).multiAssets(new ArrayList<>()).build());
        for (Utxo utxo : depositUtxos) {
            UtxoUtil.copyUtxoValuesToOutput(depositChangeOutput, utxo);
        }
        depositChangeOutput.getValue().setCoin(
                depositChangeOutput.getValue().getCoin().subtract(totalDeposits));

        // Only add change output if it has value
        BigInteger remainingCoin = depositChangeOutput.getValue().getCoin();
        boolean hasMultiAssets = depositChangeOutput.getValue().getMultiAssets() != null
                && !depositChangeOutput.getValue().getMultiAssets().isEmpty();

        if (remainingCoin.compareTo(BigInteger.ZERO) > 0 || hasMultiAssets) {
            if (context.isMergeOutputs()) {
                // Merge into existing output at depositAddr if one exists
                Optional<TransactionOutput> existingOutput = txn.getBody().getOutputs().stream()
                        .filter(o -> o.getAddress().equals(depositAddr))
                        .findFirst();
                if (existingOutput.isPresent()) {
                    TransactionOutput existing = existingOutput.get();
                    existing.setValue(existing.getValue().plus(depositChangeOutput.getValue()));
                } else {
                    txn.getBody().getOutputs().add(depositChangeOutput);
                }
            } else {
                txn.getBody().getOutputs().add(depositChangeOutput);
            }
        }
    }
}
