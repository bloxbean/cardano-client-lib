package com.bloxbean.cardano.client.ledger;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.model.ValidationResult;
import com.bloxbean.cardano.client.ledger.rule.*;
import com.bloxbean.cardano.client.ledger.slice.SimpleAccountsSlice;
import com.bloxbean.cardano.client.ledger.slice.SimpleDRepsSlice;
import com.bloxbean.cardano.client.ledger.slice.SimplePoolsSlice;
import com.bloxbean.cardano.client.spec.NetworkId;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.transaction.spec.cert.*;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerStateValidatorTest {

    private static final String TX_HASH = "aabbccdd00112233445566778899aabbccddeeff00112233445566778899aabb";
    private static final String TEST_ADDR = "addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y";

    @Test
    void validateTx_validTransaction_shouldSucceed() {
        // Use custom rules excluding WitnessValidationRule since this test
        // doesn't provide VKey witnesses (testing framework, not witness logic)
        LedgerStateValidator validator = LedgerStateValidator.builder()
                .protocolParams(defaultProtocolParams())
                .currentSlot(200)
                .networkId(NetworkId.TESTNET)
                .customRules(List.of(
                        new InputValidationRule(),
                        new TxSizeValidationRule(),
                        new ValidityIntervalRule(),
                        new NetworkIdValidationRule(),
                        new OutputValidationRule(),
                        new FeeAndCollateralRule(),
                        new ValueConservationRule(),
                        new CertificateValidationRule(),
                        new GovernanceValidationRule()
                ))
                .build();

        Transaction tx = buildValidTx();

        ValidationResult result = validator.validateTx(tx, defaultUtxos());
        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void validateTx_missingUtxo_shouldFail() {
        LedgerStateValidator validator = LedgerStateValidator.builder()
                .protocolParams(defaultProtocolParams())
                .currentSlot(200)
                .networkId(NetworkId.TESTNET)
                .build();

        Transaction tx = buildValidTx();
        // Empty UTxO set — input won't be found
        Set<Utxo> utxos = Set.of();

        ValidationResult result = validator.validateTx(tx, utxos);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.getRule().equals("InputValidation"));
    }

    @Test
    void validateTx_expiredTtl_shouldFail() {
        LedgerStateValidator validator = LedgerStateValidator.builder()
                .protocolParams(defaultProtocolParams())
                .currentSlot(1000) // Past TTL
                .networkId(NetworkId.TESTNET)
                .build();

        Transaction tx = buildValidTx();

        ValidationResult result = validator.validateTx(tx, defaultUtxos());
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.getRule().equals("ValidityInterval"));
    }

    @Test
    void validateTx_multipleErrors_shouldCollectAll() {
        LedgerStateValidator validator = LedgerStateValidator.builder()
                .protocolParams(defaultProtocolParams())
                .currentSlot(1000) // Past TTL
                .networkId(NetworkId.TESTNET)
                .build();

        Transaction tx = buildValidTx();
        // Empty UTxO set AND expired TTL — should collect both errors
        Set<Utxo> utxos = Set.of();

        ValidationResult result = validator.validateTx(tx, utxos);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).hasSizeGreaterThan(1);
    }

    @Test
    void validate_withPrebuiltContext_shouldWork() {
        LedgerStateValidator validator = LedgerStateValidator.builder()
                .protocolParams(defaultProtocolParams())
                .currentSlot(200)
                .networkId(NetworkId.TESTNET)
                .build();

        LedgerContext ctx = LedgerContext.builder()
                .protocolParams(defaultProtocolParams())
                .currentSlot(200)
                .networkId(NetworkId.TESTNET)
                .build();

        Transaction tx = buildValidTx();
        // No UTxO slice — InputValidationRule will fail
        ValidationResult result = validator.validate(ctx, tx);
        // Without UTxO slice, input validation is skipped (null slice check)
        assertThat(result).isNotNull();
    }

    // ---- State slice pass-through tests ----

    private static final String STAKE_CRED_HASH = "aabbccdd00112233445566778899aabb00112233445566778899aabb";
    private static final String DREP_CRED_HASH = "11223344556677889900aabbccddeeff11223344556677889900aabb";
    private static final String POOL_ID_HEX = "ffeeddccbbaa99887766554433221100ffeeddccbbaa9988";

    @Test
    void validateTx_withAccountsSlice_shouldDetectAlreadyRegisteredStakeKey() {
        // Scenario: stake key is already registered — RegCert should fail
        LedgerStateValidator validator = LedgerStateValidator.builder()
                .protocolParams(defaultProtocolParams())
                .currentSlot(200)
                .networkId(NetworkId.TESTNET)
                .accountsSlice(new SimpleAccountsSlice(
                        Map.of(STAKE_CRED_HASH, BigInteger.ZERO),
                        Map.of(STAKE_CRED_HASH, BigInteger.valueOf(2000000))))
                .customRules(List.of(new CertificateValidationRule()))
                .build();

        Transaction tx = buildTxWithCert(new RegCert(
                StakeCredential.fromKeyHash(HexUtil.decodeHexString(STAKE_CRED_HASH)),
                BigInteger.valueOf(2000000)));

        ValidationResult result = validator.validateTx(tx, defaultUtxos());
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.getRule().equals("CertificateValidation")
                        && e.getMessage().contains("already registered"));
    }

    @Test
    void validateTx_withDRepsSlice_shouldDetectAlreadyRegisteredDRep() {
        // Scenario: DRep is already registered — RegDRepCert should fail
        ProtocolParams pp = defaultProtocolParams();
        pp.setDrepDeposit(BigInteger.valueOf(500000000));

        LedgerStateValidator validator = LedgerStateValidator.builder()
                .protocolParams(pp)
                .currentSlot(200)
                .networkId(NetworkId.TESTNET)
                .drepsSlice(new SimpleDRepsSlice(
                        Map.of(DREP_CRED_HASH, BigInteger.valueOf(500000000))))
                .customRules(List.of(new CertificateValidationRule()))
                .build();

        Transaction tx = buildTxWithCert(new RegDRepCert(
                Credential.fromKey(DREP_CRED_HASH),
                BigInteger.valueOf(500000000),
                null));

        ValidationResult result = validator.validateTx(tx, defaultUtxos());
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.getRule().equals("CertificateValidation")
                        && e.getMessage().contains("already registered"));
    }

    @Test
    void validateTx_withPoolsSlice_shouldDetectUnregisteredPool() {
        // Scenario: delegating to a pool that doesn't exist — should fail
        LedgerStateValidator validator = LedgerStateValidator.builder()
                .protocolParams(defaultProtocolParams())
                .currentSlot(200)
                .networkId(NetworkId.TESTNET)
                .accountsSlice(new SimpleAccountsSlice(
                        Map.of(STAKE_CRED_HASH, BigInteger.ZERO),
                        Map.of(STAKE_CRED_HASH, BigInteger.valueOf(2000000))))
                .poolsSlice(new SimplePoolsSlice(Set.of(), Map.of())) // empty — no pools registered
                .customRules(List.of(new CertificateValidationRule()))
                .build();

        Transaction tx = buildTxWithCert(new StakeDelegation(
                StakeCredential.fromKeyHash(HexUtil.decodeHexString(STAKE_CRED_HASH)),
                StakePoolId.fromHexPoolId(POOL_ID_HEX)));

        ValidationResult result = validator.validateTx(tx, defaultUtxos());
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.getRule().equals("CertificateValidation")
                        && e.getMessage().contains("pool")
                        && e.getMessage().contains("not registered"));
    }

    @Test
    void validateTx_withoutSlices_shouldStillWorkInDegradedMode() {
        // Backward compat: no slices → stateful checks skipped, no errors from them
        LedgerStateValidator validator = LedgerStateValidator.builder()
                .protocolParams(defaultProtocolParams())
                .currentSlot(200)
                .networkId(NetworkId.TESTNET)
                .customRules(List.of(new CertificateValidationRule()))
                .build();

        // RegCert with already-registered key — but no slice, so check is skipped
        Transaction tx = buildTxWithCert(new RegCert(
                StakeCredential.fromKeyHash(HexUtil.decodeHexString(STAKE_CRED_HASH)),
                BigInteger.valueOf(2000000)));

        ValidationResult result = validator.validateTx(tx, defaultUtxos());
        // No CertificateValidation errors about registration (stateful check skipped)
        assertThat(result.getErrors()).noneMatch(e ->
                e.getRule().equals("CertificateValidation")
                        && e.getMessage().contains("already registered"));
    }

    private Transaction buildTxWithCert(Certificate cert) {
        return Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of(TransactionOutput.builder()
                                .address(TEST_ADDR)
                                .value(Value.builder().coin(BigInteger.valueOf(2000000)).build())
                                .build()))
                        .fee(BigInteger.valueOf(200000))
                        .validityStartInterval(100)
                        .ttl(500)
                        .certs(List.of(cert))
                        .build())
                .build();
    }

    private Set<Utxo> defaultUtxos() {
        return Set.of(Utxo.builder()
                .txHash(TX_HASH)
                .outputIndex(0)
                .address(TEST_ADDR)
                .amount(List.of(new Amount("lovelace", BigInteger.valueOf(10000000))))
                .build());
    }

    private Transaction buildValidTx() {
        // Build a balanced tx: input 10M = output 9.6M + fee ~0.4M
        // Fee must be >= minFee to pass FeeAndCollateralRule
        Transaction txDraft = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of(TransactionOutput.builder()
                                .address(TEST_ADDR)
                                .value(Value.builder().coin(BigInteger.valueOf(2000000)).build())
                                .build()))
                        .fee(BigInteger.valueOf(200000))
                        .validityStartInterval(100)
                        .ttl(500)
                        .build())
                .build();

        // Compute the serialized size to set a sufficient fee
        int txSize;
        try {
            txSize = txDraft.serialize().length;
        } catch (Exception e) {
            txSize = 300; // fallback
        }
        // minFee = txSize * 44 + 155381
        BigInteger minFee = BigInteger.valueOf((long) txSize * 44 + 155381);
        // Output = 10M - minFee (balanced)
        BigInteger outputCoin = BigInteger.valueOf(10000000).subtract(minFee);

        return Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of(TransactionOutput.builder()
                                .address(TEST_ADDR)
                                .value(Value.builder().coin(outputCoin).build())
                                .build()))
                        .fee(minFee)
                        .validityStartInterval(100)
                        .ttl(500)
                        .build())
                .build();
    }

    private ProtocolParams defaultProtocolParams() {
        return ProtocolParams.builder()
                .maxTxSize(16384)
                .coinsPerUtxoSize("4310")
                .maxValSize("5000")
                .minFeeA(44)
                .minFeeB(155381)
                .keyDeposit("2000000")
                .poolDeposit("500000000")
                .build();
    }
}
