package com.bloxbean.cardano.client.supplier.hydra;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import org.cardanofoundation.hydra.core.model.UTXO;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UtxoConverterTest {

    // in this test we want to check if our conversion function from hydra specific UTXO to BloxBean's works correctly
    @Test
    public void testUtxSimpleUtxoConversion() {
        UTXO source = UTXO.builder()
                .address("addr_test1vqg9ywrpx6e50uam03nlu0ewunh3yrscxmjayurmkp52lfskgkq5k")
                .value(Map.of("lovelace", BigInteger.valueOf(10000000L)))
                .build();

        Utxo target = Utxo.builder()
                .txHash("db982e0b69fb742188e45feedfd631bbce6738884d266356868efb9907e10cf9")
                .outputIndex(0)
                .address("addr_test1vqg9ywrpx6e50uam03nlu0ewunh3yrscxmjayurmkp52lfskgkq5k")
                .amount(List.of(Amount.builder().unit("lovelace").quantity(BigInteger.valueOf(10000000L)).build()))
                .build();

        assertThat(target).isEqualTo(HydraUtxoConverter.convert("db982e0b69fb742188e45feedfd631bbce6738884d266356868efb9907e10cf9", 0, source));
    }

    // TODO add more complicated conversions, e.g. using Plutus inline datums

}