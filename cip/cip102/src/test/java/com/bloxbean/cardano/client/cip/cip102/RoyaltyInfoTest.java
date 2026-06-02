package com.bloxbean.cardano.client.cip.cip102;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RoyaltyInfoTest {

    // A real mainnet base address for testing
    private static final String TEST_ADDRESS_MAINNET =
            "addr1qx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3n0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgse35a3x";

    @Test
    void testRoundTrip_singleRecipient() throws Exception {
        Address addr = new Address(TEST_ADDRESS_MAINNET);
        ConstrPlutusData addressData = RoyaltyAddressUtil.toPlutusData(addr);

        RoyaltyRecipient recipient = RoyaltyRecipient.builder()
                .address(addressData)
                .fee(RoyaltyFeeUtil.toChainFee(2.0))  // 2%
                .minFee(Optional.of(BigInteger.valueOf(1_000_000)))   // 1 ADA
                .maxFee(Optional.of(BigInteger.valueOf(50_000_000)))  // 50 ADA
                .build();

        RoyaltyInfo info = RoyaltyInfo.builder()
                .recipients(List.of(recipient))
                .version(RoyaltyInfo.VERSION_1)
                .build();

        ConstrPlutusData serialized = info.asPlutusData();
        assertThat(serialized.getAlternative()).isEqualTo(0);

        RoyaltyInfo deserialized = RoyaltyInfo.fromPlutusData(serialized);

        assertThat(deserialized.getVersion()).isEqualTo(1);
        assertThat(deserialized.getRecipients()).hasSize(1);

        RoyaltyRecipient r = deserialized.getRecipients().get(0);
        assertThat(r.getFee()).isEqualTo(BigInteger.valueOf(500)); // floor(1000/2) = 500
        assertThat(r.getMinFee()).isEqualTo(Optional.of(BigInteger.valueOf(1_000_000)));
        assertThat(r.getMaxFee()).isEqualTo(Optional.of(BigInteger.valueOf(50_000_000)));
    }

    @Test
    void testRoundTrip_multipleRecipients_noMinMax() throws Exception {
        Address addr = new Address(TEST_ADDRESS_MAINNET);
        ConstrPlutusData addressData = RoyaltyAddressUtil.toPlutusData(addr);

        RoyaltyRecipient r1 = RoyaltyRecipient.builder()
                .address(addressData)
                .fee(RoyaltyFeeUtil.toChainFee(1.6))
                .build();

        RoyaltyRecipient r2 = RoyaltyRecipient.builder()
                .address(addressData)
                .fee(RoyaltyFeeUtil.toChainFee(3.0))
                .build();

        RoyaltyInfo info = RoyaltyInfo.builder()
                .recipients(List.of(r1, r2))
                .version(RoyaltyInfo.VERSION_1)
                .build();

        RoyaltyInfo deserialized = RoyaltyInfo.fromPlutusData(info.asPlutusData());

        assertThat(deserialized.getRecipients()).hasSize(2);
        assertThat(deserialized.getRecipients().get(0).getMinFee()).isEmpty();
        assertThat(deserialized.getRecipients().get(0).getMaxFee()).isEmpty();
        assertThat(deserialized.getRecipients().get(1).getFee()).isEqualTo(BigInteger.valueOf(333)); // floor(1000/3)
    }

    @Test
    void testRoundTrip_version2_withExtra() throws Exception {
        Address addr = new Address(TEST_ADDRESS_MAINNET);
        ConstrPlutusData addressData = RoyaltyAddressUtil.toPlutusData(addr);

        RoyaltyRecipient recipient = RoyaltyRecipient.builder()
                .address(addressData)
                .fee(RoyaltyFeeUtil.toChainFee(5.0))
                .build();

        // extra contains royalty_included = 2 (points to (500)Royalty2)
        MapPlutusData extraMap = new MapPlutusData();
        extraMap.put(BytesPlutusData.of("royalty_included"), BigIntPlutusData.of(2));

        RoyaltyInfo info = RoyaltyInfo.builder()
                .recipients(List.of(recipient))
                .version(RoyaltyInfo.VERSION_2)
                .extra(extraMap)
                .build();

        RoyaltyInfo deserialized = RoyaltyInfo.fromPlutusData(info.asPlutusData());

        assertThat(deserialized.getVersion()).isEqualTo(2);
        assertThat(deserialized.getExtra()).isInstanceOf(MapPlutusData.class);
    }

    @Test
    void testFromCbor_roundTrip() throws Exception {
        Address addr = new Address(TEST_ADDRESS_MAINNET);
        ConstrPlutusData addressData = RoyaltyAddressUtil.toPlutusData(addr);

        RoyaltyRecipient recipient = RoyaltyRecipient.builder()
                .address(addressData)
                .fee(RoyaltyFeeUtil.toChainFee(2.5))
                .build();

        RoyaltyInfo info = RoyaltyInfo.builder()
                .recipients(List.of(recipient))
                .build();

        byte[] cborBytes = info.toCbor();
        RoyaltyInfo fromCbor = RoyaltyInfo.fromCbor(cborBytes);

        assertThat(fromCbor.getVersion()).isEqualTo(2);
        assertThat(fromCbor.getRecipients()).hasSize(1);
        assertThat(fromCbor.getRecipients().get(0).getFee()).isEqualTo(BigInteger.valueOf(400)); // floor(1000/2.5)
    }

    @Test
    void testFromPlutusData_wrongAlternative_throws() {
        ConstrPlutusData bad = ConstrPlutusData.of(1,
                ListPlutusData.of(),
                BigIntPlutusData.of(1),
                PlutusData.unit());

        assertThatThrownBy(() -> RoyaltyInfo.fromPlutusData(bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alternative 0");
    }
}
