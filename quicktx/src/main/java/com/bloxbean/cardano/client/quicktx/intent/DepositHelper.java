package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;

import java.math.BigInteger;
import java.util.Comparator;

/**
 * Helper class for handling deposits and refunds in governance and staking transactions.
 * Provides utilities for creating deposit outputs in the outputBuilder phase
 * and deducting deposits from outputs in the apply phase.
 */
public class DepositHelper {

    /**
     * Enum representing different types of deposits in Cardano
     */
    public enum DepositType {
        STAKE_KEY_REGISTRATION,
        POOL_REGISTRATION,
        DREP_REGISTRATION,
        GOV_ACTION_PROPOSAL
    }

    /**
     * Get the deposit amount for a specific deposit type from protocol parameters.
     * 
     * @param protocolParams The protocol parameters
     * @param depositType The type of deposit
     * @return The deposit amount in lovelace
     */
    public static BigInteger getDepositAmount(ProtocolParams protocolParams, DepositType depositType) {
        if (protocolParams == null) {
            throw new TxBuildException("Protocol parameters are required to get deposit amount");
        }

        switch (depositType) {
            case STAKE_KEY_REGISTRATION:
                String keyDeposit = protocolParams.getKeyDeposit();
                if (keyDeposit == null || keyDeposit.isEmpty()) {
                    throw new TxBuildException("Key deposit not available in protocol parameters");
                }
                return new BigInteger(keyDeposit);
                
            case POOL_REGISTRATION:
                String poolDeposit = protocolParams.getPoolDeposit();
                if (poolDeposit == null || poolDeposit.isEmpty()) {
                    throw new TxBuildException("Pool deposit not available in protocol parameters");
                }
                return new BigInteger(poolDeposit);
                
            case DREP_REGISTRATION:
                BigInteger drepDeposit = protocolParams.getDrepDeposit();
                if (drepDeposit == null) {
                    throw new TxBuildException("DRep deposit not available in protocol parameters");
                }
                return drepDeposit;
                
            case GOV_ACTION_PROPOSAL:
                BigInteger govActionDeposit = protocolParams.getGovActionDeposit();
                if (govActionDeposit == null) {
                    throw new TxBuildException("Gov action deposit not available in protocol parameters");
                }
                return govActionDeposit;
                
            default:
                throw new TxBuildException("Unknown deposit type: " + depositType);
        }
    }

    /**
     * Create a TxOutputBuilder that adds a deposit output to trigger input selection.
     * This is used in the outputBuilder phase of intentions.
     * 
     * @param fromAddress The address paying the deposit
     * @param depositType The type of deposit
     * @return TxOutputBuilder that adds the deposit output
     */
    public static TxOutputBuilder createDepositOutputBuilder(String fromAddress, DepositType depositType) {
        return createDepositOutputBuilder(fromAddress, depositType, null);
    }

    /**
     * Create a TxOutputBuilder that adds a deposit output to trigger input selection.
     * This is used in the outputBuilder phase of intentions.
     * 
     * @param fromAddress The address paying the deposit
     * @param depositType The type of deposit
     * @param customAmount Optional custom deposit amount (null to use protocol params)
     * @return TxOutputBuilder that adds the deposit output
     */
    public static TxOutputBuilder createDepositOutputBuilder(String fromAddress, DepositType depositType, BigInteger customAmount) {
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new TxBuildException("From address is required for deposit");
        }

        return (ctx, outputs) -> {
            BigInteger depositAmount = customAmount != null ? 
                customAmount : getDepositAmount(ctx.getProtocolParams(), depositType);

            // Check if there's already an output to the same address we can merge with
            var existingOutput = outputs.stream()
                .filter(output -> output.getAddress().equals(fromAddress))
                .findFirst()
                .orElse(null);

            if (existingOutput != null) {
                // Merge with existing output
                Value updatedValue = existingOutput.getValue().add(
                    Value.builder().coin(depositAmount).build()
                );
                existingOutput.setValue(updatedValue);
            } else {
                // Create new output
                outputs.add(new TransactionOutput(fromAddress, 
                    Value.builder().coin(depositAmount).build()));
            }
        };
    }

    /**
     * Deduct a deposit amount from the outputs to a specific address.
     * This is used in the apply phase of intentions after certificates/proposals are added.
     * 
     * @param transaction The transaction being built
     * @param fromAddress The address to deduct the deposit from
     * @param depositAmount The amount to deduct
     */
    public static void deductDepositFromOutputs(Transaction transaction, String fromAddress, BigInteger depositAmount) {
        if (transaction == null || transaction.getBody() == null || transaction.getBody().getOutputs() == null) {
            throw new TxBuildException("Transaction outputs not available");
        }

        transaction.getBody().getOutputs().stream()
            .filter(output -> output.getAddress().equals(fromAddress) 
                    && output.getValue() != null 
                    && output.getValue().getCoin() != null
                    && output.getValue().getCoin().compareTo(depositAmount) >= 0)
            .sorted(Comparator.comparing(o -> o.getValue().getCoin(), Comparator.reverseOrder()))
            .findFirst()
            .ifPresentOrElse(output -> {
                // Deduct the deposit amount
                output.getValue().setCoin(output.getValue().getCoin().subtract(depositAmount));
                
                // Remove output if it becomes empty (no ADA and no multi-assets)
                var multiAssets = output.getValue().getMultiAssets();
                if (output.getValue().getCoin().equals(BigInteger.ZERO) 
                    && (multiAssets == null || multiAssets.isEmpty())) {
                    transaction.getBody().getOutputs().remove(output);
                }
            }, () -> {
                throw new TxBuildException("Output for from address not found to deduct deposit amount: " 
                    + fromAddress + " (deposit: " + depositAmount + " lovelace)");
            });
    }

    /**
     * Add a refund output for deregistration operations.
     * This is used when stake keys, DReps, or pools are being deregistered.
     * 
     * @param refundAddress The address to receive the refund
     * @param refundAmount The amount to refund
     * @return TxOutputBuilder that adds the refund output
     */
    public static TxOutputBuilder createRefundOutputBuilder(String refundAddress, BigInteger refundAmount) {
        if (refundAddress == null || refundAddress.isBlank()) {
            throw new TxBuildException("Refund address is required");
        }

        if (refundAmount == null || refundAmount.compareTo(BigInteger.ZERO) <= 0) {
            return null; // No refund needed
        }

        return (ctx, outputs) -> {
            // Check if there's already an output to the same address we can merge with
            var existingOutput = outputs.stream()
                .filter(output -> output.getAddress().equals(refundAddress))
                .findFirst()
                .orElse(null);

            if (existingOutput != null) {
                // Merge with existing output
                Value updatedValue = existingOutput.getValue().add(
                    Value.builder().coin(refundAmount).build()
                );
                existingOutput.setValue(updatedValue);
            } else {
                // Create new output
                outputs.add(new TransactionOutput(refundAddress, 
                    Value.builder().coin(refundAmount).build()));
            }
        };
    }

    /**
     * Add refund amount to transaction outputs during the apply phase.
     * This finds existing outputs to the refund address and adds the refund amount,
     * or creates a new output if none exists.
     * 
     * @param transaction The transaction being built
     * @param refundAddress The address to receive the refund
     * @param refundAmount The amount to refund
     */
    public static void addRefundToOutputs(Transaction transaction, String refundAddress, BigInteger refundAmount) {
        if (transaction == null || transaction.getBody() == null || transaction.getBody().getOutputs() == null) {
            throw new TxBuildException("Transaction outputs not available");
        }

        if (refundAddress == null || refundAddress.isBlank()) {
            throw new TxBuildException("Refund address is required");
        }

        if (refundAmount == null || refundAmount.compareTo(BigInteger.ZERO) <= 0) {
            return; // No refund needed
        }

        // Find existing output to refund address and add refund amount
        var existingOutput = transaction.getBody().getOutputs().stream()
            .filter(output -> output.getAddress().equals(refundAddress))
            .findFirst()
            .orElse(null);

        if (existingOutput != null) {
            // Add refund to existing output
            existingOutput.getValue().setCoin(
                existingOutput.getValue().getCoin().add(refundAmount)
            );
        } else {
            // Create new output for refund
            transaction.getBody().getOutputs().add(
                new TransactionOutput(refundAddress, Value.builder().coin(refundAmount).build())
            );
        }
    }

    /**
     * Create a TxOutputBuilder for dummy outputs that triggers input selection.
     * This is used by deregistration intentions that need to trigger input selection
     * for fee payment but don't inherently create outputs.
     * 
     * @param address The address for the dummy output (usually from address)
     * @param amount The dummy amount (usually 1 ADA)
     * @return TxOutputBuilder that creates or merges dummy output
     */
    public static TxOutputBuilder createDummyOutputBuilder(String address, BigInteger amount) {
        if (address == null || address.isBlank()) {
            throw new TxBuildException("Address is required for dummy output");
        }

        return (ctx, outputs) -> {
            // Check if there's already an output to the same address we can merge with
            var existingOutput = outputs.stream()
                .filter(output -> output.getAddress().equals(address))
                .findFirst()
                .orElse(null);

            if (existingOutput != null) {
                // Merge with existing output
                Value updatedValue = existingOutput.getValue().add(
                    Value.builder().coin(amount).build()
                );
                existingOutput.setValue(updatedValue);
            } else {
                // Create new output
                outputs.add(new TransactionOutput(address, 
                    Value.builder().coin(amount).build()));
            }
        };
    }
}