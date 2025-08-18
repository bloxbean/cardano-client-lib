package com.bloxbean.cardano.client.quicktx.helper;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test suite for ScriptPredicates helper class.
 * Tests various UTXO predicate patterns used in ScriptTx transactions.
 */
public class ScriptPredicatesTest {

    private static final String SCRIPT_ADDRESS_1 = "addr_test1wqag3rt979nep9g2wtdwu8mr4gz6m4kjdpp37wx8pnh8dqqz8p8jd";
    private static final String SCRIPT_ADDRESS_2 = "addr_test1wz3rt979nep9g2wtdwu8mr4gz6m4kjdpp37wx8pnh8dqqz9p9kl";
    private static final String USER_ADDRESS = "addr_test1qz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3n0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgs68faae";

    @Test
    void testAtAddress_matchesCorrectAddress() {
        // Arrange
        Utxo utxo1 = createUtxo(SCRIPT_ADDRESS_1, BigInteger.valueOf(10_000_000));
        Utxo utxo2 = createUtxo(SCRIPT_ADDRESS_2, BigInteger.valueOf(20_000_000));
        Utxo utxo3 = createUtxo(USER_ADDRESS, BigInteger.valueOf(30_000_000));

        // Act
        Predicate<Utxo> predicate = ScriptPredicates.atAddress(SCRIPT_ADDRESS_1);

        // Assert
        assertThat(predicate.test(utxo1)).isTrue();
        assertThat(predicate.test(utxo2)).isFalse();
        assertThat(predicate.test(utxo3)).isFalse();
    }

    @Test
    void testWithInlineDatum_matchesCorrectDatum() {
        // Arrange
        PlutusData datum1 = BigIntPlutusData.of(42);
        PlutusData datum2 = BigIntPlutusData.of(100);
        PlutusData datum3 = BytesPlutusData.of("test");

        Utxo utxoWithDatum1 = createUtxoWithInlineDatum(SCRIPT_ADDRESS_1, datum1);
        Utxo utxoWithDatum2 = createUtxoWithInlineDatum(SCRIPT_ADDRESS_1, datum2);
        Utxo utxoWithDatum3 = createUtxoWithInlineDatum(SCRIPT_ADDRESS_1, datum3);
        Utxo utxoNoDatum = createUtxo(SCRIPT_ADDRESS_1, BigInteger.valueOf(10_000_000));

        // Act
        Predicate<Utxo> predicate = ScriptPredicates.withInlineDatum(datum1);

        // Assert
        assertThat(predicate.test(utxoWithDatum1)).isTrue();
        assertThat(predicate.test(utxoWithDatum2)).isFalse();
        assertThat(predicate.test(utxoWithDatum3)).isFalse();
        assertThat(predicate.test(utxoNoDatum)).isFalse();
    }

    @Test
    void testWithDatumHash_matchesCorrectHash() {
        // Arrange
        String datumHash1 = "9e1199a988ba72ffd6e9c269cadb3b25b8e4acff2e3dce4aef3793110255fc104";
        String datumHash2 = "8e1199a988ba72ffd6e9c269cadb3b25b8e4acff2e3dce4aef3793110255fc105";

        Utxo utxoWithHash1 = createUtxoWithDatumHash(SCRIPT_ADDRESS_1, datumHash1);
        Utxo utxoWithHash2 = createUtxoWithDatumHash(SCRIPT_ADDRESS_1, datumHash2);
        Utxo utxoNoHash = createUtxo(SCRIPT_ADDRESS_1, BigInteger.valueOf(10_000_000));

        // Act
        Predicate<Utxo> predicate = ScriptPredicates.withDatumHash(datumHash1);

        // Assert
        assertThat(predicate.test(utxoWithHash1)).isTrue();
        assertThat(predicate.test(utxoWithHash2)).isFalse();
        assertThat(predicate.test(utxoNoHash)).isFalse();
    }

    @Test
    void testWithMinLovelace_filtersCorrectly() {
        // Arrange
        BigInteger threshold = BigInteger.valueOf(15_000_000);
        
        Utxo utxoBelow = createUtxo(SCRIPT_ADDRESS_1, BigInteger.valueOf(10_000_000));
        Utxo utxoExact = createUtxo(SCRIPT_ADDRESS_1, threshold);
        Utxo utxoAbove = createUtxo(SCRIPT_ADDRESS_1, BigInteger.valueOf(20_000_000));
        Utxo utxoZero = createUtxo(SCRIPT_ADDRESS_1, BigInteger.ZERO);

        // Act
        Predicate<Utxo> predicate = ScriptPredicates.withMinLovelace(threshold);

        // Assert
        assertThat(predicate.test(utxoBelow)).isFalse();
        assertThat(predicate.test(utxoExact)).isTrue();  // >= threshold
        assertThat(predicate.test(utxoAbove)).isTrue();
        assertThat(predicate.test(utxoZero)).isFalse();
    }

    @Test
    void testWithAsset_matchesPolicyAndName() {
        // Arrange
        String policyId = "d5e6bf0500378d4f0da4e8dde6becec7621cd8cbf5cbb9b87013d4cc";
        String assetName = "TestToken";
        String otherPolicyId = "e5e6bf0500378d4f0da4e8dde6becec7621cd8cbf5cbb9b87013d4dd";
        String otherAssetName = "OtherToken";

        Utxo utxoWithAsset = createUtxoWithAsset(SCRIPT_ADDRESS_1, policyId, assetName, BigInteger.valueOf(100));
        Utxo utxoWrongPolicy = createUtxoWithAsset(SCRIPT_ADDRESS_1, otherPolicyId, assetName, BigInteger.valueOf(100));
        Utxo utxoWrongName = createUtxoWithAsset(SCRIPT_ADDRESS_1, policyId, otherAssetName, BigInteger.valueOf(100));
        Utxo utxoNoAsset = createUtxo(SCRIPT_ADDRESS_1, BigInteger.valueOf(10_000_000));

        // Act
        Predicate<Utxo> predicate = ScriptPredicates.withAsset(policyId, assetName);

        // Assert
        assertThat(predicate.test(utxoWithAsset)).isTrue();
        assertThat(predicate.test(utxoWrongPolicy)).isFalse();
        assertThat(predicate.test(utxoWrongName)).isFalse();
        assertThat(predicate.test(utxoNoAsset)).isFalse();
    }

    @Test
    void testWithAsset_matchesPolicyOnly() {
        // Arrange
        String policyId = "d5e6bf0500378d4f0da4e8dde6becec7621cd8cbf5cbb9b87013d4cc";
        String assetName1 = "TestToken1";
        String assetName2 = "TestToken2";
        String otherPolicyId = "e5e6bf0500378d4f0da4e8dde6becec7621cd8cbf5cbb9b87013d4dd";

        Utxo utxoWithAsset1 = createUtxoWithAsset(SCRIPT_ADDRESS_1, policyId, assetName1, BigInteger.valueOf(100));
        Utxo utxoWithAsset2 = createUtxoWithAsset(SCRIPT_ADDRESS_1, policyId, assetName2, BigInteger.valueOf(200));
        Utxo utxoWrongPolicy = createUtxoWithAsset(SCRIPT_ADDRESS_1, otherPolicyId, assetName1, BigInteger.valueOf(100));

        // Act - null asset name means match any asset under the policy
        Predicate<Utxo> predicate = ScriptPredicates.withAsset(policyId, null);

        // Assert
        assertThat(predicate.test(utxoWithAsset1)).isTrue();
        assertThat(predicate.test(utxoWithAsset2)).isTrue();
        assertThat(predicate.test(utxoWrongPolicy)).isFalse();
    }

    @Test
    void testAnd_combinesPredicatesCorrectly() {
        // Arrange
        PlutusData datum = BigIntPlutusData.of(42);
        BigInteger minLovelace = BigInteger.valueOf(10_000_000);

        Utxo matching = createUtxoWithInlineDatum(SCRIPT_ADDRESS_1, datum, BigInteger.valueOf(15_000_000));
        Utxo wrongAddress = createUtxoWithInlineDatum(SCRIPT_ADDRESS_2, datum, BigInteger.valueOf(15_000_000));
        Utxo wrongDatum = createUtxoWithInlineDatum(SCRIPT_ADDRESS_1, BigIntPlutusData.of(100), BigInteger.valueOf(15_000_000));
        Utxo insufficientLovelace = createUtxoWithInlineDatum(SCRIPT_ADDRESS_1, datum, BigInteger.valueOf(5_000_000));

        // Act
        Predicate<Utxo> predicate = ScriptPredicates.and(
            ScriptPredicates.atAddress(SCRIPT_ADDRESS_1),
            ScriptPredicates.withInlineDatum(datum),
            ScriptPredicates.withMinLovelace(minLovelace)
        );

        // Assert
        assertThat(predicate.test(matching)).isTrue();
        assertThat(predicate.test(wrongAddress)).isFalse();
        assertThat(predicate.test(wrongDatum)).isFalse();
        assertThat(predicate.test(insufficientLovelace)).isFalse();
    }

    @Test
    void testOr_combinesPredicatesCorrectly() {
        // Arrange
        PlutusData datum1 = BigIntPlutusData.of(42);
        PlutusData datum2 = BigIntPlutusData.of(100);

        Utxo withDatum1 = createUtxoWithInlineDatum(SCRIPT_ADDRESS_1, datum1);
        Utxo withDatum2 = createUtxoWithInlineDatum(SCRIPT_ADDRESS_1, datum2);
        Utxo atAddress2 = createUtxo(SCRIPT_ADDRESS_2, BigInteger.valueOf(10_000_000));
        Utxo noMatch = createUtxo(USER_ADDRESS, BigInteger.valueOf(10_000_000));

        // Act
        Predicate<Utxo> predicate = ScriptPredicates.or(
            ScriptPredicates.withInlineDatum(datum1),
            ScriptPredicates.withInlineDatum(datum2),
            ScriptPredicates.atAddress(SCRIPT_ADDRESS_2)
        );

        // Assert
        assertThat(predicate.test(withDatum1)).isTrue();
        assertThat(predicate.test(withDatum2)).isTrue();
        assertThat(predicate.test(atAddress2)).isTrue();
        assertThat(predicate.test(noMatch)).isFalse();
    }

    @Test
    void testNot_invertsPredicateLogic() {
        // Arrange
        Utxo scriptUtxo = createUtxo(SCRIPT_ADDRESS_1, BigInteger.valueOf(10_000_000));
        Utxo userUtxo = createUtxo(USER_ADDRESS, BigInteger.valueOf(10_000_000));

        // Act
        Predicate<Utxo> notScriptAddress = ScriptPredicates.not(
            ScriptPredicates.atAddress(SCRIPT_ADDRESS_1)
        );

        // Assert
        assertThat(notScriptAddress.test(scriptUtxo)).isFalse();
        assertThat(notScriptAddress.test(userUtxo)).isTrue();
    }

    @Test
    void testComplexComposition() {
        // Arrange
        String policyId = "d5e6bf0500378d4f0da4e8dde6becec7621cd8cbf5cbb9b87013d4cc";
        String lpToken = "LPToken";
        PlutusData poolDatum = BigIntPlutusData.of(1000);
        BigInteger minLiquidity = BigInteger.valueOf(10_000_000);

        // Complex predicate: (has LP token AND min liquidity) OR (specific datum AND at script address)
        Predicate<Utxo> complexPredicate = ScriptPredicates.or(
            ScriptPredicates.and(
                ScriptPredicates.withAsset(policyId, lpToken),
                ScriptPredicates.withMinLovelace(minLiquidity)
            ),
            ScriptPredicates.and(
                ScriptPredicates.withInlineDatum(poolDatum),
                ScriptPredicates.atAddress(SCRIPT_ADDRESS_1)
            )
        );

        // Test cases
        Utxo lpTokenWithLiquidity = createUtxoWithAsset(USER_ADDRESS, policyId, lpToken, BigInteger.valueOf(100), BigInteger.valueOf(15_000_000));
        Utxo lpTokenLowLiquidity = createUtxoWithAsset(USER_ADDRESS, policyId, lpToken, BigInteger.valueOf(100), BigInteger.valueOf(5_000_000));
        Utxo datumAtScript = createUtxoWithInlineDatum(SCRIPT_ADDRESS_1, poolDatum);
        Utxo datumWrongAddress = createUtxoWithInlineDatum(USER_ADDRESS, poolDatum);
        Utxo noMatch = createUtxo(USER_ADDRESS, BigInteger.valueOf(10_000_000));

        // Assert
        assertThat(complexPredicate.test(lpTokenWithLiquidity)).isTrue();
        assertThat(complexPredicate.test(lpTokenLowLiquidity)).isFalse();
        assertThat(complexPredicate.test(datumAtScript)).isTrue();
        assertThat(complexPredicate.test(datumWrongAddress)).isFalse();
        assertThat(complexPredicate.test(noMatch)).isFalse();
    }

    @Test
    void testWithMinAssetQuantity_filtersCorrectly() {
        // Arrange
        String policyId = "d5e6bf0500378d4f0da4e8dde6becec7621cd8cbf5cbb9b87013d4cc";
        String assetName = "TestToken";
        BigInteger minQuantity = BigInteger.valueOf(50);

        Utxo utxoBelow = createUtxoWithAsset(SCRIPT_ADDRESS_1, policyId, assetName, BigInteger.valueOf(30));
        Utxo utxoExact = createUtxoWithAsset(SCRIPT_ADDRESS_1, policyId, assetName, minQuantity);
        Utxo utxoAbove = createUtxoWithAsset(SCRIPT_ADDRESS_1, policyId, assetName, BigInteger.valueOf(100));
        Utxo utxoNoAsset = createUtxo(SCRIPT_ADDRESS_1, BigInteger.valueOf(10_000_000));

        // Act
        Predicate<Utxo> predicate = ScriptPredicates.withMinAssetQuantity(policyId, assetName, minQuantity);

        // Assert
        assertThat(predicate.test(utxoBelow)).isFalse();
        assertThat(predicate.test(utxoExact)).isTrue();
        assertThat(predicate.test(utxoAbove)).isTrue();
        assertThat(predicate.test(utxoNoAsset)).isFalse();
    }

    // Helper methods to create test UTXOs

    private Utxo createUtxo(String address, BigInteger lovelace) {
        return Utxo.builder()
                .address(address)
                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                .outputIndex(0)
                .amount(List.of(Amount.lovelace(lovelace)))
                .build();
    }

    private Utxo createUtxoWithInlineDatum(String address, PlutusData datum) {
        return createUtxoWithInlineDatum(address, datum, BigInteger.valueOf(10_000_000));
    }

    private Utxo createUtxoWithInlineDatum(String address, PlutusData datum, BigInteger lovelace) {
        return Utxo.builder()
                .address(address)
                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                .outputIndex(0)
                .amount(List.of(Amount.lovelace(lovelace)))
                .inlineDatum(datum.serializeToHex())
                .build();
    }

    private Utxo createUtxoWithDatumHash(String address, String datumHash) {
        return Utxo.builder()
                .address(address)
                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                .outputIndex(0)
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(10_000_000))))
                .dataHash(datumHash)
                .build();
    }

    private Utxo createUtxoWithAsset(String address, String policyId, String assetName, BigInteger quantity) {
        return createUtxoWithAsset(address, policyId, assetName, quantity, BigInteger.valueOf(10_000_000));
    }

    private Utxo createUtxoWithAsset(String address, String policyId, String assetName, BigInteger quantity, BigInteger lovelace) {
        Asset asset = new Asset(assetName, quantity);
        return Utxo.builder()
                .address(address)
                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                .outputIndex(0)
                .amount(Arrays.asList(
                        Amount.lovelace(lovelace),
                        Amount.asset(policyId, assetName, quantity)
                ))
                .build();
    }
}