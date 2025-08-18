package com.bloxbean.cardano.client.quicktx.helper;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple test to demonstrate ListPredicates functionality works correctly.
 * This is a focused TDD test for core functionality.
 */
public class SimpleListPredicatesTest {

    @Test
    void testSelectTop_worksCorrectly() {
        // Arrange
        List<Utxo> utxos = Arrays.asList(
            createUtxo(BigInteger.valueOf(30_000_000), 0), // Highest
            createUtxo(BigInteger.valueOf(10_000_000), 1), // Lowest
            createUtxo(BigInteger.valueOf(20_000_000), 2)  // Middle
        );

        // Act
        ListPredicates.SelectingPredicate<List<Utxo>> predicate = ListPredicates.selectTop(2, 
            Comparator.comparing(this::getLovelaceAmount).reversed());
        List<Utxo> result = predicate.select(utxos);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(getLovelaceAmount(result.get(0))).isEqualTo(BigInteger.valueOf(30_000_000));
        assertThat(getLovelaceAmount(result.get(1))).isEqualTo(BigInteger.valueOf(20_000_000));
    }

    @Test
    void testSelectByTotalValue_worksCorrectly() {
        // Arrange
        List<Utxo> utxos = Arrays.asList(
            createUtxo(BigInteger.valueOf(10_000_000), 0),
            createUtxo(BigInteger.valueOf(15_000_000), 1),
            createUtxo(BigInteger.valueOf(20_000_000), 2)
        );

        // Act
        ListPredicates.SelectingPredicate<List<Utxo>> predicate = ListPredicates.selectByTotalValue(
            BigInteger.valueOf(25_000_000)
        );
        List<Utxo> result = predicate.select(utxos);

        // Assert
        assertThat(result).hasSize(2); // First two UTXOs total 25_000_000
        BigInteger totalValue = result.stream()
            .map(this::getLovelaceAmount)
            .reduce(BigInteger.ZERO, BigInteger::add);
        assertThat(totalValue).isEqualTo(BigInteger.valueOf(25_000_000));
    }

    @Test
    void testSelectAll_worksCorrectly() {
        // Arrange
        List<Utxo> utxos = Arrays.asList(
            createUtxo(BigInteger.valueOf(10_000_000), 0),
            createUtxo(BigInteger.valueOf(20_000_000), 1)
        );

        // Act
        ListPredicates.SelectingPredicate<List<Utxo>> predicate = ListPredicates.selectAll();
        List<Utxo> result = predicate.select(utxos);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyElementsOf(utxos);
    }

    @Test
    void testPredicateTestMethod_worksCorrectly() {
        // Arrange
        List<Utxo> utxos = Arrays.asList(
            createUtxo(BigInteger.valueOf(10_000_000), 0),
            createUtxo(BigInteger.valueOf(20_000_000), 1)
        );

        // Act & Assert - test the boolean predicate functionality
        ListPredicates.SelectingPredicate<List<Utxo>> selectTop = ListPredicates.selectTop(1, 
            Comparator.comparing(this::getLovelaceAmount));
        assertThat(selectTop.test(utxos)).isTrue(); // Should return true for non-empty list
        assertThat(selectTop.test(List.of())).isFalse(); // Should return false for empty list

        ListPredicates.SelectingPredicate<List<Utxo>> selectAll = ListPredicates.selectAll();
        assertThat(selectAll.test(utxos)).isTrue(); // selectAll should always return true
        assertThat(selectAll.test(List.of())).isTrue(); // Even for empty lists
    }

    // Helper methods
    private Utxo createUtxo(BigInteger lovelace, int outputIndex) {
        return Utxo.builder()
                .address("addr_test1wqag3rt979nep9g2wtdwu8mr4gz6m4kjdpp37wx8pnh8dqqz8p8jd")
                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                .outputIndex(outputIndex)
                .amount(List.of(Amount.lovelace(lovelace)))
                .build();
    }

    private BigInteger getLovelaceAmount(Utxo utxo) {
        return utxo.getAmount().stream()
                .filter(amount -> "lovelace".equals(amount.getUnit()))
                .map(Amount::getQuantity)
                .findFirst()
                .orElse(BigInteger.ZERO);
    }
}