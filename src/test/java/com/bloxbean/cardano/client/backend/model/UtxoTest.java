package com.bloxbean.cardano.client.backend.model;

import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Value;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class UtxoTest {

    @Test
    void toValue() {
        Utxo utxo = Utxo.builder()
                .txHash("735262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9")
                .outputIndex(0)
                .amount(Arrays.asList(
                        new Amount("lovelace", BigInteger.valueOf(140000)),
                        new Amount("6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7", BigInteger.valueOf(222)),
                        new Amount("5acc52d5696e52345aec108468050d9d743eb21d6e41305bbc23a27b616263", BigInteger.valueOf(5000)), //abc
                        new Amount("5acc52d5696e52345aec108468050d9d743eb21d6e41305bbc23a27b746f6b656e31", BigInteger.valueOf(5000)), //token1
                        new Amount("5acc52d5696e52345aec108468050d9d743eb21d6e41305bbc23a27b746f6b656e32", BigInteger.valueOf(6000)), //token2
                        new Amount("ba37e7c25e5b425025929bfcb8d03cec0d758654a844109c3283b338616263", BigInteger.valueOf(1000)), //abc
                        new Amount("ba37e7c25e5b425025929bfcb8d03cec0d758654a844109c3283b33878797a", BigInteger.valueOf(2000)) //xyz
                )).build();

        Value value = utxo.toValue();

        MultiAsset ma1 = value.getMultiAssets().stream()
                .filter(ma -> ma.getPolicyId().equals("6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7"))
                .findFirst().get();

        MultiAsset ma2 = value.getMultiAssets().stream()
                .filter(ma -> ma.getPolicyId().equals("5acc52d5696e52345aec108468050d9d743eb21d6e41305bbc23a27b"))
                .findFirst().get();

        MultiAsset ma3 = value.getMultiAssets().stream()
                .filter(ma -> ma.getPolicyId().equals("ba37e7c25e5b425025929bfcb8d03cec0d758654a844109c3283b338"))
                .findFirst().get();

        System.out.println(value);
        assertThat(value.getMultiAssets()).hasSize(3);
        assertThat(value.getCoin()).isEqualTo(BigInteger.valueOf(140000));

        assertThat(ma1.getAssets()).hasSize(1);
        assertThat(ma1.getAssets().get(0)).isEqualTo(new Asset("0x", BigInteger.valueOf(222)));

        assertThat(ma2.getAssets()).hasSize(3);
        assertThat(ma2.getAssets()).contains(
                new Asset("0x616263", BigInteger.valueOf(5000)),
                new Asset("0x746f6b656e31", BigInteger.valueOf(5000)),
                new Asset("0x746f6b656e32", BigInteger.valueOf(6000))
        );

        assertThat(ma3.getAssets()).hasSize(2);
        assertThat(ma3.getAssets()).contains(
                new Asset("0x616263", BigInteger.valueOf(1000)),
                new Asset("0x78797a", BigInteger.valueOf(2000))
        );

    }

    @Test
    void toValue_whenNoMultiAsset() {
        Utxo utxo = Utxo.builder()
                .txHash("735262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9")
                .outputIndex(0)
                .amount(Arrays.asList(
                        new Amount("lovelace", BigInteger.valueOf(140000))
                )).build();

        Value value = utxo.toValue();

        System.out.println(value);
        assertThat(value.getCoin()).isEqualTo(BigInteger.valueOf(140000));
        assertThat(value.getMultiAssets()).hasSize(0);
    }

    @Test
    void toValue_whenOneMultiAsset() {
        Utxo utxo = Utxo.builder()
                .txHash("735262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9")
                .outputIndex(0)
                .amount(Arrays.asList(
                        new Amount("lovelace", BigInteger.valueOf(140000)),
                        new Amount("5acc52d5696e52345aec108468050d9d743eb21d6e41305bbc23a27b616263", BigInteger.valueOf(5000))
                )).build();

        Value value = utxo.toValue();

        System.out.println(value);
        assertThat(value.getCoin()).isEqualTo(BigInteger.valueOf(140000));
        assertThat(value.getMultiAssets()).hasSize(1);
        assertThat(value.getMultiAssets().get(0).getPolicyId()).isEqualTo("5acc52d5696e52345aec108468050d9d743eb21d6e41305bbc23a27b");
        assertThat(value.getMultiAssets().get(0).getAssets()).contains(
                new Asset("0x616263", BigInteger.valueOf(5000))
        );
    }
}
