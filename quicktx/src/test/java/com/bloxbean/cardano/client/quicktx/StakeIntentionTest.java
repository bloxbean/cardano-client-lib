package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.quicktx.intent.*;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for stake intention recording and processing functionality.
 */
public class StakeIntentionTest {

    @Test
    public void testStakeRegistrationIntentionRecording() {
        // Create a Tx and enable intention recording
        Tx tx = new Tx();
        tx.enableIntentionRecording();

        // Register a stake address (using a valid test stake address)
        String stakeAddress = "stake_test1uqfu74w3wh4gfzu8m6e7j987h4lq9r3t7ef5gaw497uu85qsqfy27";
        tx.registerStakeAddress(stakeAddress);

        // Check that the intention was recorded
        List<TxIntent> intentions = tx.getIntentions();
        assertFalse(intentions.isEmpty());

        StakeRegistrationIntent registrationIntention = (StakeRegistrationIntent) intentions.stream()
            .filter(i -> i instanceof StakeRegistrationIntent)
            .findFirst()
            .orElse(null);

        assertNotNull(registrationIntention);
        assertEquals("stake_registration", registrationIntention.getType());
        assertEquals(stakeAddress, registrationIntention.getStakeAddress());
    }

    @Test
    public void testStakeDelegationIntentionRecording() {
        // Create a Tx and enable intention recording
        Tx tx = new Tx();
        tx.enableIntentionRecording();

        // Delegate a stake address to a pool
        String stakeAddress = "stake_test1uqfu74w3wh4gfzu8m6e7j987h4lq9r3t7ef5gaw497uu85qsqfy27";
        String poolId = "pool1pu5jlj4q9w9jlxeu370a3c9myx47md5j5m2str0naunn2q3lkdy";
        tx.delegateTo(stakeAddress, poolId);

        // Check that the intention was recorded
        List<TxIntent> intentions = tx.getIntentions();
        assertFalse(intentions.isEmpty());

        StakeDelegationIntent delegationIntention = (StakeDelegationIntent) intentions.stream()
            .filter(i -> i instanceof StakeDelegationIntent)
            .findFirst()
            .orElse(null);

        assertNotNull(delegationIntention);
        assertEquals("stake_delegation", delegationIntention.getType());
        assertEquals(stakeAddress, delegationIntention.getStakeAddress());
        assertEquals(poolId, delegationIntention.getPoolId());
    }

    @Test
    public void testStakeWithdrawalIntentionRecording() {
        // Create a Tx and enable intention recording
        Tx tx = new Tx();
        tx.enableIntentionRecording();

        // Withdraw rewards
        String rewardAddress = "stake_test1uqfu74w3wh4gfzu8m6e7j987h4lq9r3t7ef5gaw497uu85qsqfy27";
        BigInteger amount = BigInteger.valueOf(1000000); // 1 ADA in lovelace
        tx.withdraw(rewardAddress, amount);

        // Check that the intention was recorded
        List<TxIntent> intentions = tx.getIntentions();
        assertFalse(intentions.isEmpty());

        StakeWithdrawalIntent withdrawalIntention = (StakeWithdrawalIntent) intentions.stream()
            .filter(i -> i instanceof StakeWithdrawalIntent)
            .findFirst()
            .orElse(null);

        assertNotNull(withdrawalIntention);
        assertEquals("stake_withdrawal", withdrawalIntention.getType());
        assertEquals(rewardAddress, withdrawalIntention.getRewardAddress());
        assertEquals(amount, withdrawalIntention.getAmount());
        assertNull(withdrawalIntention.getReceiver()); // No specific receiver
    }

    @Test
    public void testStakeDeregistrationIntentionRecording() {
        // Create a Tx and enable intention recording
        Tx tx = new Tx();
        tx.enableIntentionRecording();

        // Deregister a stake address with refund
        String stakeAddress = "stake_test1uqfu74w3wh4gfzu8m6e7j987h4lq9r3t7ef5gaw497uu85qsqfy27";
        String refundAddress = "addr_test1qpubfqkh8m8kqyts6dvyeahjfw7mhg4gvkp2nqhfv8qg4zx3k4w4l3qmx8y0rmhkw8c4l5pms3lwhpz5zd2gqfs8xfxhxkxmh";
        tx.deregisterStakeAddress(stakeAddress, refundAddress);

        // Check that the intention was recorded
        List<TxIntent> intentions = tx.getIntentions();
        assertFalse(intentions.isEmpty());

        StakeDeregistrationIntent deregistrationIntention = (StakeDeregistrationIntent) intentions.stream()
            .filter(i -> i instanceof StakeDeregistrationIntent)
            .findFirst()
            .orElse(null);

        assertNotNull(deregistrationIntention);
        assertEquals("stake_deregistration", deregistrationIntention.getType());
        assertEquals(stakeAddress, deregistrationIntention.getStakeAddress());
        assertEquals(refundAddress, deregistrationIntention.getRefundAddress());
    }

    @Test
    public void testMultipleStakeIntentions() {
        // Create a Tx and enable intention recording
        Tx tx = new Tx();
        tx.enableIntentionRecording();

        // Perform multiple stake operations
        String stakeAddress = "stake_test1uqfu74w3wh4gfzu8m6e7j987h4lq9r3t7ef5gaw497uu85qsqfy27";
        String poolId = "pool1pu5jlj4q9w9jlxeu370a3c9myx47md5j5m2str0naunn2q3lkdy";
        BigInteger amount = BigInteger.valueOf(2000000); // 2 ADA

        tx.registerStakeAddress(stakeAddress)
          .delegateTo(stakeAddress, poolId)
          .withdraw(stakeAddress, amount);

        // Check that all intentions were recorded
        List<TxIntent> intentions = tx.getIntentions();
        assertEquals(3, intentions.size());

        // Verify each intention type exists
        assertTrue(intentions.stream().anyMatch(i -> i instanceof StakeRegistrationIntent));
        assertTrue(intentions.stream().anyMatch(i -> i instanceof StakeDelegationIntent));
        assertTrue(intentions.stream().anyMatch(i -> i instanceof StakeWithdrawalIntent));
    }

    @Test
    public void testIntentionValidation() {
        // Test that intentions validate their required fields
        StakeRegistrationIntent registration = new StakeRegistrationIntent();

        // Should fail validation without stake address
        assertThrows(IllegalStateException.class, registration::validate);

        // Should pass validation with stake address
        registration.setStakeAddress("stake_test1uqfu74w3wh4gfzu8m6e7j987h4lq9r3t7ef5gaw497uu85qsqfy27");
        assertDoesNotThrow(registration::validate);
    }

    @Test
    public void testIntentionFactoryMethods() {
        // Test factory methods create properly configured intentions
        String stakeAddress = "stake_test1uqfu74w3wh4gfzu8m6e7j987h4lq9r3t7ef5gaw497uu85qsqfy27";
        String poolId = "pool1pu5jlj4q9w9jlxeu370a3c9myx47md5j5m2str0naunn2q3lkdy";

        StakeRegistrationIntent registration = StakeRegistrationIntent.register(stakeAddress);
        assertEquals(stakeAddress, registration.getStakeAddress());
        assertEquals("stake_registration", registration.getType());

        StakeDelegationIntent delegation = StakeDelegationIntent.delegateTo(stakeAddress, poolId);
        assertEquals(stakeAddress, delegation.getStakeAddress());
        assertEquals(poolId, delegation.getPoolId());
        assertEquals("stake_delegation", delegation.getType());

        BigInteger amount = BigInteger.valueOf(1000000);
        StakeWithdrawalIntent withdrawal = StakeWithdrawalIntent.withdraw(stakeAddress, amount);
        assertEquals(stakeAddress, withdrawal.getRewardAddress());
        assertEquals(amount, withdrawal.getAmount());
        assertEquals("stake_withdrawal", withdrawal.getType());
    }
}
