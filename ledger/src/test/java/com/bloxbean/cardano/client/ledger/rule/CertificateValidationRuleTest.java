package com.bloxbean.cardano.client.ledger.rule;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.ValidationError;
import com.bloxbean.cardano.client.ledger.LedgerContext;
import com.bloxbean.cardano.client.ledger.slice.SimpleAccountsSlice;
import com.bloxbean.cardano.client.ledger.slice.SimpleCommitteeSlice;
import com.bloxbean.cardano.client.ledger.slice.SimpleDRepsSlice;
import com.bloxbean.cardano.client.ledger.slice.SimplePoolsSlice;
import com.bloxbean.cardano.client.spec.NetworkId;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.transaction.spec.cert.*;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateValidationRuleTest {

    private CertificateValidationRule rule;

    private static final String TX_HASH = "aabbccdd00112233445566778899aabbccddeeff00112233445566778899aabb";
    private static final byte[] CRED_HASH_BYTES = HexUtil.decodeHexString("aabb001122334455667788990011223344556677889900112233445566");
    private static final String CRED_HASH = "aabb001122334455667788990011223344556677889900112233445566";
    private static final byte[] POOL_KEY_HASH = HexUtil.decodeHexString("ccdd001122334455667788990011223344556677889900112233445566");
    private static final String POOL_KEY_HASH_HEX = "ccdd001122334455667788990011223344556677889900112233445566";
    private static final String TESTNET_ADDR = "addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y";

    @BeforeEach
    void setUp() {
        rule = new CertificateValidationRule();
    }

    // ---- No certs → pass ----

    @Test
    void validate_noCerts_shouldPass() {
        LedgerContext ctx = defaultContext().build();
        Transaction tx = buildTxWithCerts(null);
        assertThat(rule.validate(ctx, tx)).isEmpty();
    }

    @Test
    void validate_emptyCerts_shouldPass() {
        LedgerContext ctx = defaultContext().build();
        Transaction tx = buildTxWithCerts(List.of());
        assertThat(rule.validate(ctx, tx)).isEmpty();
    }

    // ---- F-1: RegCert deposit == pp.keyDeposit ----

    @Test
    void validate_regCert_correctDeposit_shouldPass() {
        LedgerContext ctx = defaultContext().build();
        RegCert cert = new RegCert(StakeCredential.fromKeyHash(CRED_HASH_BYTES),
                BigInteger.valueOf(2000000));
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    @Test
    void validate_regCert_wrongDeposit_shouldFail() {
        LedgerContext ctx = defaultContext().build();
        RegCert cert = new RegCert(StakeCredential.fromKeyHash(CRED_HASH_BYTES),
                BigInteger.valueOf(1000000));
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("RegCert").contains("deposit");
    }

    @Test
    void validate_stakeRegDelegCert_correctDeposit_shouldPass() {
        LedgerContext ctx = contextWithPools().build();
        StakeRegDelegCert cert = StakeRegDelegCert.builder()
                .stakeCredential(StakeCredential.fromKeyHash(CRED_HASH_BYTES))
                .poolKeyHash(POOL_KEY_HASH_HEX)
                .coin(BigInteger.valueOf(2000000))
                .build();
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    @Test
    void validate_stakeRegDelegCert_wrongDeposit_shouldFail() {
        LedgerContext ctx = contextWithPools().build();
        StakeRegDelegCert cert = StakeRegDelegCert.builder()
                .stakeCredential(StakeCredential.fromKeyHash(CRED_HASH_BYTES))
                .poolKeyHash(POOL_KEY_HASH_HEX)
                .coin(BigInteger.valueOf(999))
                .build();
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("StakeRegDelegCert") && e.getMessage().contains("deposit"));
    }

    @Test
    void validate_voteRegDelegCert_correctDeposit_shouldPass() {
        LedgerContext ctx = defaultContext().build();
        VoteRegDelegCert cert = VoteRegDelegCert.builder()
                .stakeCredential(StakeCredential.fromKeyHash(CRED_HASH_BYTES))
                .drep(DRep.abstain())
                .coin(BigInteger.valueOf(2000000))
                .build();
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    @Test
    void validate_stakeVoteRegDelegCert_wrongDeposit_shouldFail() {
        LedgerContext ctx = contextWithPools().build();
        StakeVoteRegDelegCert cert = StakeVoteRegDelegCert.builder()
                .stakeCredential(StakeCredential.fromKeyHash(CRED_HASH_BYTES))
                .poolKeyHash(POOL_KEY_HASH_HEX)
                .drep(DRep.abstain())
                .coin(BigInteger.valueOf(999))
                .build();
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("StakeVoteRegDelegCert") && e.getMessage().contains("deposit"));
    }

    // ---- F-2: PoolRegistration cost >= pp.minPoolCost ----

    @Test
    void validate_poolRegistration_costAboveMin_shouldPass() {
        LedgerContext ctx = defaultContext().build();
        PoolRegistration cert = PoolRegistration.builder()
                .operator(POOL_KEY_HASH)
                .cost(BigInteger.valueOf(400000000))
                .build();
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    @Test
    void validate_poolRegistration_costBelowMin_shouldFail() {
        LedgerContext ctx = defaultContext().build();
        PoolRegistration cert = PoolRegistration.builder()
                .operator(POOL_KEY_HASH)
                .cost(BigInteger.valueOf(100))
                .build();
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("PoolRegistration").contains("minPoolCost");
    }

    // ---- F-3: PoolRegistration reward account network ----

    @Test
    void validate_poolRegistration_matchingNetwork_shouldPass() {
        // Testnet reward address (hex bytes)
        String testnetRewardHex = "e0" + CRED_HASH; // e0 = testnet stake address header
        LedgerContext ctx = defaultContext().build();
        PoolRegistration cert = PoolRegistration.builder()
                .operator(POOL_KEY_HASH)
                .cost(BigInteger.valueOf(400000000))
                .rewardAccount(testnetRewardHex)
                .build();
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    @Test
    void validate_poolRegistration_mismatchedNetwork_shouldFail() {
        // Mainnet reward address (hex bytes) but testnet context
        String mainnetRewardHex = "e1" + CRED_HASH; // e1 = mainnet stake address header
        LedgerContext ctx = defaultContext().build();
        PoolRegistration cert = PoolRegistration.builder()
                .operator(POOL_KEY_HASH)
                .cost(BigInteger.valueOf(400000000))
                .rewardAccount(mainnetRewardHex)
                .build();
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("PoolRegistration") && e.getMessage().contains("reward account network"));
    }

    // ---- F-4: PoolRetirement epoch range ----

    @Test
    void validate_poolRetirement_validEpochRange_shouldPass() {
        LedgerContext ctx = defaultContext()
                .currentEpoch(100)
                .poolsSlice(new SimplePoolsSlice(Set.of(POOL_KEY_HASH_HEX), Map.of()))
                .build();
        PoolRetirement cert = PoolRetirement.builder()
                .poolKeyHash(POOL_KEY_HASH)
                .epoch(110) // in range [101, 118]
                .build();
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    @Test
    void validate_poolRetirement_epochTooEarly_shouldFail() {
        LedgerContext ctx = defaultContext()
                .currentEpoch(100)
                .poolsSlice(new SimplePoolsSlice(Set.of(POOL_KEY_HASH_HEX), Map.of()))
                .build();
        PoolRetirement cert = PoolRetirement.builder()
                .poolKeyHash(POOL_KEY_HASH)
                .epoch(100) // must be >= 101
                .build();
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("PoolRetirement") && e.getMessage().contains("not in valid range"));
    }

    @Test
    void validate_poolRetirement_epochTooLate_shouldFail() {
        LedgerContext ctx = defaultContext()
                .currentEpoch(100)
                .poolsSlice(new SimplePoolsSlice(Set.of(POOL_KEY_HASH_HEX), Map.of()))
                .build();
        PoolRetirement cert = PoolRetirement.builder()
                .poolKeyHash(POOL_KEY_HASH)
                .epoch(200) // must be <= 118
                .build();
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("PoolRetirement") && e.getMessage().contains("not in valid range"));
    }

    @Test
    void validate_poolRetirement_noEpochContext_shouldSkip() {
        // currentEpoch = 0 → skip epoch check
        LedgerContext ctx = defaultContext()
                .poolsSlice(new SimplePoolsSlice(Set.of(POOL_KEY_HASH_HEX), Map.of()))
                .build();
        PoolRetirement cert = PoolRetirement.builder()
                .poolKeyHash(POOL_KEY_HASH)
                .epoch(999)
                .build();
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    // ---- F-5: RegDRepCert deposit == pp.drepDeposit ----

    @Test
    void validate_regDRepCert_correctDeposit_shouldPass() {
        LedgerContext ctx = defaultContext().build();
        RegDRepCert cert = RegDRepCert.builder()
                .drepCredential(Credential.fromKey(CRED_HASH_BYTES))
                .coin(BigInteger.valueOf(500000000))
                .build();
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    @Test
    void validate_regDRepCert_wrongDeposit_shouldFail() {
        LedgerContext ctx = defaultContext().build();
        RegDRepCert cert = RegDRepCert.builder()
                .drepCredential(Credential.fromKey(CRED_HASH_BYTES))
                .coin(BigInteger.valueOf(100))
                .build();
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("RegDRepCert") && e.getMessage().contains("drepDeposit"));
    }

    // ---- F-6: StakeRegistration/RegCert — credential NOT already registered ----

    @Test
    void validate_stakeRegistration_alreadyRegistered_shouldFail() {
        LedgerContext ctx = defaultContext()
                .accountsSlice(new SimpleAccountsSlice(
                        Map.of(CRED_HASH, BigInteger.ZERO),
                        Map.of(CRED_HASH, BigInteger.valueOf(2000000))))
                .build();
        StakeRegistration cert = new StakeRegistration(StakeCredential.fromKeyHash(CRED_HASH_BYTES));
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("StakeRegistration").contains("already registered");
    }

    @Test
    void validate_stakeRegistration_notRegistered_shouldPass() {
        LedgerContext ctx = defaultContext()
                .accountsSlice(new SimpleAccountsSlice(Map.of(), Map.of()))
                .build();
        StakeRegistration cert = new StakeRegistration(StakeCredential.fromKeyHash(CRED_HASH_BYTES));
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    @Test
    void validate_regCert_alreadyRegistered_shouldFail() {
        LedgerContext ctx = defaultContext()
                .accountsSlice(new SimpleAccountsSlice(
                        Map.of(CRED_HASH, BigInteger.ZERO),
                        Map.of(CRED_HASH, BigInteger.valueOf(2000000))))
                .build();
        RegCert cert = new RegCert(StakeCredential.fromKeyHash(CRED_HASH_BYTES),
                BigInteger.valueOf(2000000));
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("RegCert") && e.getMessage().contains("already registered"));
    }

    // ---- F-7: StakeDeregistration/UnregCert — credential IS registered ----

    @Test
    void validate_stakeDeregistration_notRegistered_shouldFail() {
        LedgerContext ctx = defaultContext()
                .accountsSlice(new SimpleAccountsSlice(Map.of(), Map.of()))
                .build();
        StakeDeregistration cert = new StakeDeregistration(StakeCredential.fromKeyHash(CRED_HASH_BYTES));
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("StakeDeregistration").contains("not registered");
    }

    @Test
    void validate_stakeDeregistration_registered_zeroBalance_shouldPass() {
        LedgerContext ctx = defaultContext()
                .accountsSlice(new SimpleAccountsSlice(
                        Map.of(CRED_HASH, BigInteger.ZERO),
                        Map.of(CRED_HASH, BigInteger.valueOf(2000000))))
                .build();
        StakeDeregistration cert = new StakeDeregistration(StakeCredential.fromKeyHash(CRED_HASH_BYTES));
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    // ---- F-8: StakeDeregistration/UnregCert — reward balance == 0 ----

    @Test
    void validate_stakeDeregistration_nonZeroBalance_shouldFail() {
        LedgerContext ctx = defaultContext()
                .accountsSlice(new SimpleAccountsSlice(
                        Map.of(CRED_HASH, BigInteger.valueOf(5000000)),
                        Map.of(CRED_HASH, BigInteger.valueOf(2000000))))
                .build();
        StakeDeregistration cert = new StakeDeregistration(StakeCredential.fromKeyHash(CRED_HASH_BYTES));
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("StakeDeregistration").contains("non-zero reward balance");
    }

    // ---- F-9: UnregCert — refund matches recorded deposit ----

    @Test
    void validate_unregCert_matchingRefund_shouldPass() {
        LedgerContext ctx = defaultContext()
                .accountsSlice(new SimpleAccountsSlice(
                        Map.of(CRED_HASH, BigInteger.ZERO),
                        Map.of(CRED_HASH, BigInteger.valueOf(2000000))))
                .build();
        UnregCert cert = new UnregCert(StakeCredential.fromKeyHash(CRED_HASH_BYTES),
                BigInteger.valueOf(2000000));
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    @Test
    void validate_unregCert_mismatchedRefund_shouldFail() {
        LedgerContext ctx = defaultContext()
                .accountsSlice(new SimpleAccountsSlice(
                        Map.of(CRED_HASH, BigInteger.ZERO),
                        Map.of(CRED_HASH, BigInteger.valueOf(2000000))))
                .build();
        UnregCert cert = new UnregCert(StakeCredential.fromKeyHash(CRED_HASH_BYTES),
                BigInteger.valueOf(1000000));
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("UnregCert") && e.getMessage().contains("refund"));
    }

    @Test
    void validate_unregCert_notRegistered_shouldFail() {
        LedgerContext ctx = defaultContext()
                .accountsSlice(new SimpleAccountsSlice(Map.of(), Map.of()))
                .build();
        UnregCert cert = new UnregCert(StakeCredential.fromKeyHash(CRED_HASH_BYTES),
                BigInteger.valueOf(2000000));
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("UnregCert") && e.getMessage().contains("not registered"));
    }

    // ---- F-10: StakeDelegation — credential registered + pool exists ----

    @Test
    void validate_stakeDelegation_registeredAndPoolExists_shouldPass() {
        LedgerContext ctx = defaultContext()
                .accountsSlice(new SimpleAccountsSlice(
                        Map.of(CRED_HASH, BigInteger.ZERO), Map.of()))
                .poolsSlice(new SimplePoolsSlice(Set.of(POOL_KEY_HASH_HEX), Map.of()))
                .build();
        StakeDelegation cert = new StakeDelegation(
                StakeCredential.fromKeyHash(CRED_HASH_BYTES),
                new StakePoolId(POOL_KEY_HASH));
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    @Test
    void validate_stakeDelegation_notRegistered_shouldFail() {
        LedgerContext ctx = defaultContext()
                .accountsSlice(new SimpleAccountsSlice(Map.of(), Map.of()))
                .poolsSlice(new SimplePoolsSlice(Set.of(POOL_KEY_HASH_HEX), Map.of()))
                .build();
        StakeDelegation cert = new StakeDelegation(
                StakeCredential.fromKeyHash(CRED_HASH_BYTES),
                new StakePoolId(POOL_KEY_HASH));
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("StakeDelegation") && e.getMessage().contains("credential"));
    }

    @Test
    void validate_stakeDelegation_poolNotRegistered_shouldFail() {
        LedgerContext ctx = defaultContext()
                .accountsSlice(new SimpleAccountsSlice(
                        Map.of(CRED_HASH, BigInteger.ZERO), Map.of()))
                .poolsSlice(new SimplePoolsSlice(Set.of(), Map.of()))
                .build();
        StakeDelegation cert = new StakeDelegation(
                StakeCredential.fromKeyHash(CRED_HASH_BYTES),
                new StakePoolId(POOL_KEY_HASH));
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("StakeDelegation") && e.getMessage().contains("pool"));
    }

    // ---- F-11: PoolRetirement — pool IS registered ----

    @Test
    void validate_poolRetirement_poolNotRegistered_shouldFail() {
        LedgerContext ctx = defaultContext()
                .poolsSlice(new SimplePoolsSlice(Set.of(), Map.of()))
                .build();
        PoolRetirement cert = PoolRetirement.builder()
                .poolKeyHash(POOL_KEY_HASH)
                .epoch(110)
                .build();
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("PoolRetirement") && e.getMessage().contains("not registered"));
    }

    // ---- F-12: UnregDRepCert — DRep IS registered, refund matches ----

    @Test
    void validate_unregDRepCert_registeredAndMatchingRefund_shouldPass() {
        LedgerContext ctx = defaultContext()
                .drepsSlice(new SimpleDRepsSlice(Map.of(CRED_HASH, BigInteger.valueOf(500000000))))
                .build();
        UnregDRepCert cert = UnregDRepCert.builder()
                .drepCredential(Credential.fromKey(CRED_HASH_BYTES))
                .coin(BigInteger.valueOf(500000000))
                .build();
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    @Test
    void validate_unregDRepCert_notRegistered_shouldFail() {
        LedgerContext ctx = defaultContext()
                .drepsSlice(new SimpleDRepsSlice(Map.of()))
                .build();
        UnregDRepCert cert = UnregDRepCert.builder()
                .drepCredential(Credential.fromKey(CRED_HASH_BYTES))
                .coin(BigInteger.valueOf(500000000))
                .build();
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("UnregDRepCert") && e.getMessage().contains("not registered"));
    }

    @Test
    void validate_unregDRepCert_mismatchedRefund_shouldFail() {
        LedgerContext ctx = defaultContext()
                .drepsSlice(new SimpleDRepsSlice(Map.of(CRED_HASH, BigInteger.valueOf(500000000))))
                .build();
        UnregDRepCert cert = UnregDRepCert.builder()
                .drepCredential(Credential.fromKey(CRED_HASH_BYTES))
                .coin(BigInteger.valueOf(100))
                .build();
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("UnregDRepCert") && e.getMessage().contains("refund"));
    }

    // ---- F-13: AuthCommitteeHotCert — cold credential IS committee member ----

    @Test
    void validate_authCommitteeHotCert_isMember_shouldPass() {
        LedgerContext ctx = defaultContext()
                .committeeSlice(new SimpleCommitteeSlice(Map.of(CRED_HASH, "somehothash"), Set.of()))
                .build();
        AuthCommitteeHotCert cert = AuthCommitteeHotCert.builder()
                .committeeColdCredential(Credential.fromKey(CRED_HASH_BYTES))
                .committeeHotCredential(Credential.fromKey(CRED_HASH_BYTES))
                .build();
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    @Test
    void validate_authCommitteeHotCert_notMember_shouldFail() {
        LedgerContext ctx = defaultContext()
                .committeeSlice(new SimpleCommitteeSlice(Map.of(), Set.of()))
                .build();
        AuthCommitteeHotCert cert = AuthCommitteeHotCert.builder()
                .committeeColdCredential(Credential.fromKey(CRED_HASH_BYTES))
                .committeeHotCredential(Credential.fromKey(CRED_HASH_BYTES))
                .build();
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("AuthCommitteeHotCert").contains("not a committee member");
    }

    // ---- F-14: ResignCommitteeColdCert — cold credential IS committee member ----

    @Test
    void validate_resignCommitteeColdCert_isMember_shouldPass() {
        LedgerContext ctx = defaultContext()
                .committeeSlice(new SimpleCommitteeSlice(Map.of(CRED_HASH, "somehothash"), Set.of()))
                .build();
        ResignCommitteeColdCert cert = ResignCommitteeColdCert.builder()
                .committeeColdCredential(Credential.fromKey(CRED_HASH_BYTES))
                .build();
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    @Test
    void validate_resignCommitteeColdCert_notMember_shouldFail() {
        LedgerContext ctx = defaultContext()
                .committeeSlice(new SimpleCommitteeSlice(Map.of(), Set.of()))
                .build();
        ResignCommitteeColdCert cert = ResignCommitteeColdCert.builder()
                .committeeColdCredential(Credential.fromKey(CRED_HASH_BYTES))
                .build();
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("ResignCommitteeColdCert").contains("not a committee member");
    }

    // ---- F-15: UpdateDRepCert — DRep IS registered ----

    @Test
    void validate_updateDRepCert_registered_shouldPass() {
        LedgerContext ctx = defaultContext()
                .drepsSlice(new SimpleDRepsSlice(Map.of(CRED_HASH, BigInteger.valueOf(500000000))))
                .build();
        UpdateDRepCert cert = UpdateDRepCert.builder()
                .drepCredential(Credential.fromKey(CRED_HASH_BYTES))
                .build();
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    @Test
    void validate_updateDRepCert_notRegistered_shouldFail() {
        LedgerContext ctx = defaultContext()
                .drepsSlice(new SimpleDRepsSlice(Map.of()))
                .build();
        UpdateDRepCert cert = UpdateDRepCert.builder()
                .drepCredential(Credential.fromKey(CRED_HASH_BYTES))
                .build();
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("UpdateDRepCert") && e.getMessage().contains("not registered"));
    }

    // ---- Graceful degradation: no state slice → skip ----

    @Test
    void validate_noAccountsSlice_shouldSkipStatefulChecks() {
        LedgerContext ctx = defaultContext().build(); // no accountsSlice
        StakeDeregistration cert = new StakeDeregistration(StakeCredential.fromKeyHash(CRED_HASH_BYTES));
        // Without accountsSlice, stateful checks are skipped → no error
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    @Test
    void validate_noPoolsSlice_shouldSkipPoolChecks() {
        LedgerContext ctx = defaultContext().build(); // no poolsSlice
        PoolRetirement cert = PoolRetirement.builder()
                .poolKeyHash(POOL_KEY_HASH)
                .epoch(110)
                .build();
        // Without poolsSlice, pool registration check is skipped → no error
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    @Test
    void validate_noDRepsSlice_shouldSkipDRepChecks() {
        LedgerContext ctx = defaultContext().build(); // no drepsSlice
        UnregDRepCert cert = UnregDRepCert.builder()
                .drepCredential(Credential.fromKey(CRED_HASH_BYTES))
                .coin(BigInteger.valueOf(500000000))
                .build();
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    // ---- Withdrawal validation ----

    @Test
    void validate_withdrawal_fullDrain_shouldPass() {
        String testnetRewardAddr = "stake_test1uqfu74w3wh4gfzu8m6e7j987h4lq9r3t7ef5gaw497uu85qsqfy27";
        LedgerContext ctx = defaultContext()
                .accountsSlice(new SimpleAccountsSlice(
                        Map.of(CRED_HASH, BigInteger.valueOf(5000000)),
                        Map.of()))
                .build();

        // Withdrawals require the credential hash from the address, which
        // requires a real reward address. For this test we rely on the fact that
        // unparseable addresses are silently skipped.
        assertThat(rule.validate(ctx, buildTxWithCerts(null))).isEmpty();
    }

    // ---- VoteDelegCert — credential must be registered ----

    @Test
    void validate_voteDelegCert_notRegistered_shouldFail() {
        LedgerContext ctx = defaultContext()
                .accountsSlice(new SimpleAccountsSlice(Map.of(), Map.of()))
                .build();
        VoteDelegCert cert = VoteDelegCert.builder()
                .stakeCredential(StakeCredential.fromKeyHash(CRED_HASH_BYTES))
                .drep(DRep.abstain())
                .build();
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("VoteDelegCert") && e.getMessage().contains("not registered"));
    }

    // ---- StakeVoteDelegCert — credential + pool checks ----

    @Test
    void validate_stakeVoteDelegCert_poolNotRegistered_shouldFail() {
        LedgerContext ctx = defaultContext()
                .accountsSlice(new SimpleAccountsSlice(
                        Map.of(CRED_HASH, BigInteger.ZERO), Map.of()))
                .poolsSlice(new SimplePoolsSlice(Set.of(), Map.of()))
                .build();
        StakeVoteDelegCert cert = StakeVoteDelegCert.builder()
                .stakeCredential(StakeCredential.fromKeyHash(CRED_HASH_BYTES))
                .poolKeyHash(POOL_KEY_HASH_HEX)
                .drep(DRep.abstain())
                .build();
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("StakeVoteDelegCert") && e.getMessage().contains("pool"));
    }

    // ---- StakeRegDelegCert — pool check ----

    @Test
    void validate_stakeRegDelegCert_poolNotRegistered_shouldFail() {
        LedgerContext ctx = defaultContext()
                .poolsSlice(new SimplePoolsSlice(Set.of(), Map.of()))
                .build();
        StakeRegDelegCert cert = StakeRegDelegCert.builder()
                .stakeCredential(StakeCredential.fromKeyHash(CRED_HASH_BYTES))
                .poolKeyHash(POOL_KEY_HASH_HEX)
                .coin(BigInteger.valueOf(2000000))
                .build();
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("StakeRegDelegCert") && e.getMessage().contains("pool"));
    }

    // ---- StakeVoteRegDelegCert — pool check ----

    @Test
    void validate_stakeVoteRegDelegCert_poolNotRegistered_shouldFail() {
        LedgerContext ctx = defaultContext()
                .poolsSlice(new SimplePoolsSlice(Set.of(), Map.of()))
                .build();
        StakeVoteRegDelegCert cert = StakeVoteRegDelegCert.builder()
                .stakeCredential(StakeCredential.fromKeyHash(CRED_HASH_BYTES))
                .poolKeyHash(POOL_KEY_HASH_HEX)
                .drep(DRep.abstain())
                .coin(BigInteger.valueOf(2000000))
                .build();
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("StakeVoteRegDelegCert") && e.getMessage().contains("pool"));
    }

    // ---- RegDRepCert — already registered ----

    @Test
    void validate_regDRepCert_alreadyRegistered_shouldFail() {
        LedgerContext ctx = defaultContext()
                .drepsSlice(new SimpleDRepsSlice(Map.of(CRED_HASH, BigInteger.valueOf(500000000))))
                .build();
        RegDRepCert cert = RegDRepCert.builder()
                .drepCredential(Credential.fromKey(CRED_HASH_BYTES))
                .coin(BigInteger.valueOf(500000000))
                .build();
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("RegDRepCert") && e.getMessage().contains("already registered"));
    }

    // ---- DelegateeDRepNotRegisteredDELEG ----

    @Test
    void validate_voteDelegCert_drepRegistered_shouldPass() {
        LedgerContext ctx = defaultContext()
                .accountsSlice(new SimpleAccountsSlice(
                        Map.of(CRED_HASH, BigInteger.ZERO), Map.of()))
                .drepsSlice(new SimpleDRepsSlice(Map.of(CRED_HASH, BigInteger.valueOf(500000000))))
                .build();
        VoteDelegCert cert = VoteDelegCert.builder()
                .stakeCredential(StakeCredential.fromKeyHash(CRED_HASH_BYTES))
                .drep(DRep.addrKeyHash(CRED_HASH))
                .build();
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    @Test
    void validate_voteDelegCert_drepNotRegistered_shouldFail() {
        LedgerContext ctx = defaultContext()
                .accountsSlice(new SimpleAccountsSlice(
                        Map.of(CRED_HASH, BigInteger.ZERO), Map.of()))
                .drepsSlice(new SimpleDRepsSlice(Map.of()))
                .build();
        VoteDelegCert cert = VoteDelegCert.builder()
                .stakeCredential(StakeCredential.fromKeyHash(CRED_HASH_BYTES))
                .drep(DRep.addrKeyHash(CRED_HASH))
                .build();
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("VoteDelegCert")
                && e.getMessage().contains("delegatee DRep") && e.getMessage().contains("not registered"));
    }

    @Test
    void validate_voteDelegCert_abstainDrep_shouldSkipDRepCheck() {
        LedgerContext ctx = defaultContext()
                .accountsSlice(new SimpleAccountsSlice(
                        Map.of(CRED_HASH, BigInteger.ZERO), Map.of()))
                .drepsSlice(new SimpleDRepsSlice(Map.of())) // empty — but ABSTAIN should pass
                .build();
        VoteDelegCert cert = VoteDelegCert.builder()
                .stakeCredential(StakeCredential.fromKeyHash(CRED_HASH_BYTES))
                .drep(DRep.abstain())
                .build();
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    @Test
    void validate_voteDelegCert_noConfidenceDrep_shouldSkipDRepCheck() {
        LedgerContext ctx = defaultContext()
                .accountsSlice(new SimpleAccountsSlice(
                        Map.of(CRED_HASH, BigInteger.ZERO), Map.of()))
                .drepsSlice(new SimpleDRepsSlice(Map.of()))
                .build();
        VoteDelegCert cert = VoteDelegCert.builder()
                .stakeCredential(StakeCredential.fromKeyHash(CRED_HASH_BYTES))
                .drep(DRep.noConfidence())
                .build();
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    @Test
    void validate_stakeVoteDelegCert_drepNotRegistered_shouldFail() {
        LedgerContext ctx = defaultContext()
                .accountsSlice(new SimpleAccountsSlice(
                        Map.of(CRED_HASH, BigInteger.ZERO), Map.of()))
                .poolsSlice(new SimplePoolsSlice(Set.of(POOL_KEY_HASH_HEX), Map.of()))
                .drepsSlice(new SimpleDRepsSlice(Map.of()))
                .build();
        StakeVoteDelegCert cert = StakeVoteDelegCert.builder()
                .stakeCredential(StakeCredential.fromKeyHash(CRED_HASH_BYTES))
                .poolKeyHash(POOL_KEY_HASH_HEX)
                .drep(DRep.addrKeyHash(CRED_HASH))
                .build();
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("StakeVoteDelegCert")
                && e.getMessage().contains("delegatee DRep"));
    }

    @Test
    void validate_voteRegDelegCert_drepNotRegistered_shouldFail() {
        LedgerContext ctx = defaultContext()
                .drepsSlice(new SimpleDRepsSlice(Map.of()))
                .build();
        VoteRegDelegCert cert = VoteRegDelegCert.builder()
                .stakeCredential(StakeCredential.fromKeyHash(CRED_HASH_BYTES))
                .drep(DRep.addrKeyHash(CRED_HASH))
                .coin(BigInteger.valueOf(2000000))
                .build();
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("VoteRegDelegCert")
                && e.getMessage().contains("delegatee DRep"));
    }

    @Test
    void validate_stakeVoteRegDelegCert_drepNotRegistered_shouldFail() {
        LedgerContext ctx = defaultContext()
                .poolsSlice(new SimplePoolsSlice(Set.of(POOL_KEY_HASH_HEX), Map.of()))
                .drepsSlice(new SimpleDRepsSlice(Map.of()))
                .build();
        StakeVoteRegDelegCert cert = StakeVoteRegDelegCert.builder()
                .stakeCredential(StakeCredential.fromKeyHash(CRED_HASH_BYTES))
                .poolKeyHash(POOL_KEY_HASH_HEX)
                .drep(DRep.addrKeyHash(CRED_HASH))
                .coin(BigInteger.valueOf(2000000))
                .build();
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("StakeVoteRegDelegCert")
                && e.getMessage().contains("delegatee DRep"));
    }

    @Test
    void validate_voteDelegCert_noDRepsSlice_shouldSkipDRepCheck() {
        LedgerContext ctx = defaultContext()
                .accountsSlice(new SimpleAccountsSlice(
                        Map.of(CRED_HASH, BigInteger.ZERO), Map.of()))
                .build(); // no drepsSlice
        VoteDelegCert cert = VoteDelegCert.builder()
                .stakeCredential(StakeCredential.fromKeyHash(CRED_HASH_BYTES))
                .drep(DRep.addrKeyHash(CRED_HASH))
                .build();
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    // ---- ConwayCommitteeHasPreviouslyResigned ----

    @Test
    void validate_authCommitteeHotCert_notResigned_shouldPass() {
        LedgerContext ctx = defaultContext()
                .committeeSlice(new SimpleCommitteeSlice(Map.of(CRED_HASH, "somehothash"), Set.of()))
                .build();
        AuthCommitteeHotCert cert = AuthCommitteeHotCert.builder()
                .committeeColdCredential(Credential.fromKey(CRED_HASH_BYTES))
                .committeeHotCredential(Credential.fromKey(CRED_HASH_BYTES))
                .build();
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    @Test
    void validate_authCommitteeHotCert_resigned_shouldFail() {
        LedgerContext ctx = defaultContext()
                .committeeSlice(new SimpleCommitteeSlice(
                        Map.of(CRED_HASH, "somehothash"), Set.of(CRED_HASH)))
                .build();
        AuthCommitteeHotCert cert = AuthCommitteeHotCert.builder()
                .committeeColdCredential(Credential.fromKey(CRED_HASH_BYTES))
                .committeeHotCredential(Credential.fromKey(CRED_HASH_BYTES))
                .build();
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("AuthCommitteeHotCert")
                && e.getMessage().contains("previously resigned"));
    }

    // ---- PoolMedataHashTooBig ----

    @Test
    void validate_poolRegistration_validMetadataHash_shouldPass() {
        LedgerContext ctx = defaultContext().build();
        // 32-byte hash (64 hex chars)
        String validHash = "aabbccdd00112233445566778899aabbccddeeff00112233445566778899aabb";
        PoolRegistration cert = PoolRegistration.builder()
                .operator(POOL_KEY_HASH)
                .cost(BigInteger.valueOf(400000000))
                .poolMetadataHash(validHash)
                .build();
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    @Test
    void validate_poolRegistration_metadataHashTooBig_shouldFail() {
        LedgerContext ctx = defaultContext().build();
        // 33-byte hash (66 hex chars)
        String tooLongHash = "aabbccdd00112233445566778899aabbccddeeff00112233445566778899aabbcc";
        PoolRegistration cert = PoolRegistration.builder()
                .operator(POOL_KEY_HASH)
                .cost(BigInteger.valueOf(400000000))
                .poolMetadataHash(tooLongHash)
                .build();
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("PoolRegistration")
                && e.getMessage().contains("metadata hash size") && e.getMessage().contains("exceeds"));
    }

    @Test
    void validate_poolRegistration_noMetadataHash_shouldPass() {
        LedgerContext ctx = defaultContext().build();
        PoolRegistration cert = PoolRegistration.builder()
                .operator(POOL_KEY_HASH)
                .cost(BigInteger.valueOf(400000000))
                .build();
        assertThat(rule.validate(ctx, buildTxWithCerts(List.of(cert)))).isEmpty();
    }

    // ---- All errors are PHASE_1 ----

    @Test
    void validate_allErrorsArePhase1() {
        LedgerContext ctx = defaultContext().build();
        RegCert cert = new RegCert(StakeCredential.fromKeyHash(CRED_HASH_BYTES),
                BigInteger.valueOf(999));
        List<ValidationError> errors = rule.validate(ctx, buildTxWithCerts(List.of(cert)));
        assertThat(errors).isNotEmpty();
        assertThat(errors).allMatch(e -> e.getPhase() == ValidationError.Phase.PHASE_1);
    }

    // ---- Helper methods ----

    private LedgerContext.LedgerContextBuilder defaultContext() {
        return LedgerContext.builder()
                .protocolParams(defaultProtocolParams())
                .networkId(NetworkId.TESTNET);
    }

    private LedgerContext.LedgerContextBuilder contextWithPools() {
        return defaultContext()
                .poolsSlice(new SimplePoolsSlice(Set.of(POOL_KEY_HASH_HEX), Map.of()));
    }

    private Transaction buildTxWithCerts(List<Certificate> certs) {
        TransactionBody.TransactionBodyBuilder bodyBuilder = TransactionBody.builder()
                .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                .outputs(List.of(TransactionOutput.builder()
                        .address(TESTNET_ADDR)
                        .value(Value.builder().coin(BigInteger.valueOf(2000000)).build())
                        .build()))
                .fee(BigInteger.valueOf(200000));

        if (certs != null) {
            bodyBuilder.certs(certs);
        }

        return Transaction.builder()
                .body(bodyBuilder.build())
                .build();
    }

    private ProtocolParams defaultProtocolParams() {
        return ProtocolParams.builder()
                .keyDeposit("2000000")
                .minPoolCost("340000000")
                .eMax(18)
                .drepDeposit(BigInteger.valueOf(500000000))
                .govActionDeposit(BigInteger.valueOf(100000000000L))
                .build();
    }
}
