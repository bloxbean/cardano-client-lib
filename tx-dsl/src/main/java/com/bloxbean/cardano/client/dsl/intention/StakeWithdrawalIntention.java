package com.bloxbean.cardano.client.dsl.intention;

import com.bloxbean.cardano.client.quicktx.Tx;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.Map;

/**
 * Intention for withdrawing rewards from a stake address.
 * Captures the reward address, amount to withdraw, and optional receiver.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("stake_withdrawal")
public class StakeWithdrawalIntention implements TxIntention {
    
    @JsonProperty("reward_address")
    private String rewardAddress;
    
    @JsonProperty("amount")
    private BigInteger amount;
    
    @JsonProperty("receiver")
    private String receiver; // Optional receiver address
    
    public StakeWithdrawalIntention(String rewardAddress, BigInteger amount) {
        this.rewardAddress = rewardAddress;
        this.amount = amount;
        this.receiver = null;
    }
    
    @Override
    public String getType() {
        return "stake_withdrawal";
    }
    
    @Override
    public void apply(Tx tx, Map<String, Object> variables) {
        // Resolve addresses from variables if they're variables
        String resolvedRewardAddress = IntentionHelper.resolveVariable(rewardAddress, variables);
        String resolvedReceiver = receiver != null ? 
            IntentionHelper.resolveVariable(receiver, variables) : null;
        
        // Note: amount could potentially be resolved from variables too in the future
        
        // Apply stake withdrawal directly to Tx
        if (resolvedReceiver != null) {
            tx.withdraw(resolvedRewardAddress, amount, resolvedReceiver);
        } else {
            tx.withdraw(resolvedRewardAddress, amount);
        }
    }
    
    /**
     * Factory method for creating stake withdrawal intention.
     */
    public static StakeWithdrawalIntention withdraw(String rewardAddress, BigInteger amount) {
        return new StakeWithdrawalIntention(rewardAddress, amount);
    }
    
    /**
     * Factory method for creating stake withdrawal intention with receiver.
     */
    public static StakeWithdrawalIntention withdraw(String rewardAddress, BigInteger amount, String receiver) {
        return new StakeWithdrawalIntention(rewardAddress, amount, receiver);
    }
}