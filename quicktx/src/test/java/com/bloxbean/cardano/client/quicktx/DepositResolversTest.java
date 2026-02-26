package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.quicktx.intent.*;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DepositResolvers} static helper methods.
 * Tests package-private methods directly for deposit detection and calculation.
 */
class DepositResolversTest {

    // ========== hasDepositIntents ==========

    @Test
    void hasDepositIntents_nullList_returnsFalse() {
        assertThat(DepositResolvers.hasDepositIntents(null)).isFalse();
    }

    @Test
    void hasDepositIntents_emptyList_returnsFalse() {
        assertThat(DepositResolvers.hasDepositIntents(Collections.emptyList())).isFalse();
    }

    @Test
    void hasDepositIntents_paymentOnly_returnsFalse() {
        List<TxIntent> intents = List.of(
                PaymentIntent.builder().address("addr_test1...").build()
        );
        assertThat(DepositResolvers.hasDepositIntents(intents)).isFalse();
    }

    @Test
    void hasDepositIntents_stakeRegistration_returnsTrue() {
        List<TxIntent> intents = List.of(
                StakeRegistrationIntent.builder().stakeAddress("stake_test1...").build()
        );
        assertThat(DepositResolvers.hasDepositIntents(intents)).isTrue();
    }

    @Test
    void hasDepositIntents_poolRegistration_returnsTrue() {
        List<TxIntent> intents = List.of(
                PoolRegistrationIntent.builder().isUpdate(false).build()
        );
        assertThat(DepositResolvers.hasDepositIntents(intents)).isTrue();
    }

    @Test
    void hasDepositIntents_poolUpdate_returnsFalse() {
        List<TxIntent> intents = List.of(
                PoolRegistrationIntent.builder().isUpdate(true).build()
        );
        assertThat(DepositResolvers.hasDepositIntents(intents)).isFalse();
    }

    @Test
    void hasDepositIntents_drepRegistration_returnsTrue() {
        List<TxIntent> intents = List.of(
                DRepRegistrationIntent.builder().build()
        );
        assertThat(DepositResolvers.hasDepositIntents(intents)).isTrue();
    }

    @Test
    void hasDepositIntents_governanceProposal_returnsTrue() {
        List<TxIntent> intents = List.of(
                GovernanceProposalIntent.builder().build()
        );
        assertThat(DepositResolvers.hasDepositIntents(intents)).isTrue();
    }

    @Test
    void hasDepositIntents_mixedWithDeposit_returnsTrue() {
        List<TxIntent> intents = List.of(
                PaymentIntent.builder().address("addr_test1...").build(),
                StakeRegistrationIntent.builder().stakeAddress("stake_test1...").build()
        );
        assertThat(DepositResolvers.hasDepositIntents(intents)).isTrue();
    }

    // ========== calculateTotalDeposits ==========

    @Test
    void calculateTotalDeposits_nullIntentions_returnsZero() {
        ProtocolParams pp = protocolParams();
        assertThat(DepositResolvers.calculateTotalDeposits(null, pp)).isEqualTo(BigInteger.ZERO);
    }

    @Test
    void calculateTotalDeposits_noDepositIntents_returnsZero() {
        ProtocolParams pp = protocolParams();
        List<TxIntent> intents = List.of(
                PaymentIntent.builder().address("addr_test1...").build()
        );
        assertThat(DepositResolvers.calculateTotalDeposits(intents, pp)).isEqualTo(BigInteger.ZERO);
    }

    @Test
    void calculateTotalDeposits_singleStakeRegistration() {
        ProtocolParams pp = protocolParams();
        List<TxIntent> intents = List.of(
                StakeRegistrationIntent.builder().stakeAddress("stake_test1...").build()
        );
        assertThat(DepositResolvers.calculateTotalDeposits(intents, pp))
                .isEqualTo(new BigInteger("2000000"));
    }

    @Test
    void calculateTotalDeposits_multipleDeposits_summed() {
        ProtocolParams pp = protocolParams();
        List<TxIntent> intents = List.of(
                StakeRegistrationIntent.builder().stakeAddress("stake_test1...").build(),
                DRepRegistrationIntent.builder().build()
        );
        // 2 ADA (stake) + 2 ADA (drep) = 4 ADA
        assertThat(DepositResolvers.calculateTotalDeposits(intents, pp))
                .isEqualTo(new BigInteger("4000000"));
    }

    @Test
    void calculateTotalDeposits_customDrepDeposit() {
        ProtocolParams pp = protocolParams();
        BigInteger custom = new BigInteger("5000000");
        List<TxIntent> intents = List.of(
                DRepRegistrationIntent.builder().deposit(custom).build()
        );
        assertThat(DepositResolvers.calculateTotalDeposits(intents, pp)).isEqualTo(custom);
    }

    @Test
    void calculateTotalDeposits_customGovProposalDeposit() {
        ProtocolParams pp = protocolParams();
        BigInteger custom = new BigInteger("10000000");
        List<TxIntent> intents = List.of(
                GovernanceProposalIntent.builder().deposit(custom).build()
        );
        assertThat(DepositResolvers.calculateTotalDeposits(intents, pp)).isEqualTo(custom);
    }

    @Test
    void calculateTotalDeposits_poolUpdate_excluded() {
        ProtocolParams pp = protocolParams();
        List<TxIntent> intents = List.of(
                PoolRegistrationIntent.builder().isUpdate(true).build()
        );
        assertThat(DepositResolvers.calculateTotalDeposits(intents, pp)).isEqualTo(BigInteger.ZERO);
    }

    @Test
    void calculateTotalDeposits_poolRegistration_included() {
        ProtocolParams pp = protocolParams();
        List<TxIntent> intents = List.of(
                PoolRegistrationIntent.builder().isUpdate(false).build()
        );
        assertThat(DepositResolvers.calculateTotalDeposits(intents, pp))
                .isEqualTo(new BigInteger("500000000"));
    }

    // ========== resolveDeposits — no-op cases ==========

    @Test
    void resolveDeposits_noDepositIntents_returnsNoOpBuilder() {
        List<TxIntent> intents = List.of(
                PaymentIntent.builder().address("addr_test1...").build()
        );
        var builder = DepositResolvers.resolveDeposits(intents, null, "addr_test1...", DepositMode.AUTO);
        assertThat(builder).isNotNull();
        // no-op builder should not throw when applied (we can't easily test without a full context,
        // but asserting it returns non-null is the key check)
    }

    @Test
    void resolveDeposits_emptyIntents_returnsNoOpBuilder() {
        var builder = DepositResolvers.resolveDeposits(new ArrayList<>(), null, "addr", DepositMode.AUTO);
        assertThat(builder).isNotNull();
    }

    @Test
    void resolveDeposits_nullIntents_returnsNoOpBuilder() {
        var builder = DepositResolvers.resolveDeposits(null, null, "addr", DepositMode.AUTO);
        assertThat(builder).isNotNull();
    }

    // ========== Helpers ==========

    private ProtocolParams protocolParams() {
        ProtocolParams pp = new ProtocolParams();
        pp.setKeyDeposit("2000000");
        pp.setPoolDeposit("500000000");
        pp.setDrepDeposit(new BigInteger("2000000"));
        pp.setGovActionDeposit(new BigInteger("100000000000"));
        return pp;
    }
}
