package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressType;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.WithdrawalUtil;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.quicktx.serialization.VariableResolver;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.spec.Withdrawal;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.ArrayList;

/**
 * Intention for withdrawing rewards from a stake address.
 * Captures the reward address, amount to withdraw, and optional receiver.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class StakeWithdrawalIntent implements TxIntent {

    /**
     * Reward address to withdraw from.
     * Must be a reward address (stake address).
     */
    @JsonProperty("reward_address")
    private String rewardAddress;

    /**
     * Amount to withdraw in lovelace.
     */
    @JsonProperty("amount")
    private BigInteger amount;

    /**
     * Optional receiver address for the withdrawal.
     * If not specified, rewards go to the change address.
     */
    @JsonProperty("receiver")
    private String receiver;

    // Optional redeemer support for script-based withdrawal
    @JsonIgnore
    private PlutusData redeemer;

    @JsonProperty("redeemer_hex")
    private String redeemerHex;

    @JsonProperty("redeemer_hex")
    public String getRedeemerHex() {
        if (redeemer != null) {
            try { return redeemer.serializeToHex(); } catch (Exception e) { /* ignore */ }
        }
        return redeemerHex;
    }

    @Override
    public String getType() {
        return "stake_withdrawal";
    }


    @Override
    public void validate() {
        TxIntent.super.validate();
        if (rewardAddress == null || rewardAddress.isEmpty()) {
            throw new IllegalStateException("Reward address is required for stake withdrawal");
        }

        Address addr = new Address(rewardAddress);
        if (AddressType.Reward != addr.getAddressType()) {
            throw new TxBuildException("Invalid address type. Only reward address can be used for withdrawal");
        }

        if (amount == null || amount.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalStateException("Withdrawal amount must be non-negative");
        }

        if (redeemerHex != null && !redeemerHex.isEmpty()) {
            try { HexUtil.decodeHexString(redeemerHex); } catch (Exception e) {
                throw new IllegalStateException("Invalid redeemer hex format");
            }
        }
    }

    @Override
    public TxIntent resolveVariables(java.util.Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return this;
        }

        String resolvedRewardAddress = VariableResolver.resolve(rewardAddress, variables);
        String resolvedReceiver = VariableResolver.resolve(receiver, variables);
        String resolvedRedeemerHex = VariableResolver.resolve(redeemerHex, variables);

        // Check if any variables were resolved
        if (!java.util.Objects.equals(resolvedRewardAddress, rewardAddress) ||
            !java.util.Objects.equals(resolvedReceiver, receiver) ||
            !java.util.Objects.equals(resolvedRedeemerHex, redeemerHex)) {
            return this.toBuilder()
                .rewardAddress(resolvedRewardAddress)
                .receiver(resolvedReceiver)
                .redeemerHex(resolvedRedeemerHex)
                .build();
        }

        return this;
    }

    /**
     * Create a stake withdrawal intention.
     */
    public static StakeWithdrawalIntent withdraw(String rewardAddress, BigInteger amount) {
        return StakeWithdrawalIntent.builder()
            .rewardAddress(rewardAddress)
            .amount(amount)
            .build();
    }

    /**
     * Create a stake withdrawal intention with receiver.
     */
    public static StakeWithdrawalIntent withdraw(String rewardAddress, BigInteger amount, String receiver) {
        return StakeWithdrawalIntent.builder()
            .rewardAddress(rewardAddress)
            .amount(amount)
            .receiver(receiver)
            .build();
    }

    @Override
    public TxBuilder preApply(IntentContext ic) {
        return (ctx, txn) -> {
            // Context-specific check only
            if (ic.getFromAddress() == null || ic.getFromAddress().isBlank()) {
                throw new TxBuildException("From address is required for stake withdrawal");
            }

            // Add withdrawal entry here so central sort (between preApply and apply) sees it
            if (txn.getBody().getWithdrawals() == null) {
                txn.getBody().setWithdrawals(new ArrayList<>());
            }
            txn.getBody().getWithdrawals().add(new Withdrawal(rewardAddress, amount));
        };
    }

    @Override
    public TxBuilder apply(IntentContext ic) {
        return (ctx, txn) -> {
            String targetReceiver = (receiver != null && !receiver.isBlank())
                    ? receiver
                    : ic.getChangeAddress();

            //Add withdrawn amount to receiver output
            //Should we consider mergeOutputs = false scenario here ?
            //What if we keep a separate withdrawal output for mergeOutputs = false, but what happens when the newly generated output has
            //less than the mininum UTxO value, then the transaction will fail. For exp: withdraw zero trick.
            //So, we may need another transaction balancing step.
            //To avoid this complexity, for now, we just add the withdrawn amount to the receiver output if any or create a new one if not exists.
            //We will consider the mergeOutputs = false scenario in future if there is a demand for it.
            txn.getBody().getOutputs().stream()
                    .filter(to -> to.getAddress().equals(targetReceiver))
                    .findFirst()
                    .ifPresentOrElse(to -> {
                        to.getValue().setCoin(to.getValue().getCoin().add(amount));
                    }, () -> {
                        txn.getBody().getOutputs().add(new TransactionOutput(targetReceiver,
                                Value.builder().coin(amount).build()));
                    });

            // Add reward redeemer if provided — look up position in the now-sorted withdrawal list
            PlutusData rdData = redeemer;
            if (rdData == null && redeemerHex != null && !redeemerHex.isEmpty()) {
                try {
                    rdData = PlutusData.deserialize(HexUtil.decodeHexString(redeemerHex));
                } catch (Exception e) {
                    throw new TxBuildException("Failed to deserialize redeemer hex", e);
                }
            }
            if (rdData != null) {
                if (txn.getWitnessSet() == null)
                    txn.setWitnessSet(new TransactionWitnessSet());

                int sortedIndex = WithdrawalUtil.getIndexByRewardAddress(txn.getBody().getWithdrawals(), rewardAddress);
                if (sortedIndex < 0)
                    throw new TxBuildException("Reward address not found in withdrawal list: " + rewardAddress);

                Redeemer rd = Redeemer.builder()
                        .tag(RedeemerTag.Reward)
                        .data(rdData)
                        .index(BigInteger.valueOf(sortedIndex))
                        .exUnits(ExUnits.builder()
                                .mem(BigInteger.valueOf(10000))
                                .steps(BigInteger.valueOf(1000))
                                .build())
                        .build();
                txn.getWitnessSet().getRedeemers().add(rd);
            }
        };
    }

    @Override
    public boolean hasRedeemer() {
        return redeemer != null || (redeemerHex != null && !redeemerHex.isEmpty());
    }
}
