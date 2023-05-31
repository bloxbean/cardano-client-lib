package com.bloxbean.cardano.client.supplier.hydra;

import com.bloxbean.cardano.client.api.model.Utxo;
import org.cardanofoundation.hydra.core.model.UTXO;
import org.cardanofoundation.hydra.core.store.InMemoryUTxOStore;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.bloxbean.cardano.client.api.common.OrderEnum.asc;
import static org.assertj.core.api.Assertions.assertThat;

class HydraUtxoStoreUTxOSupplierTest {

    // empty UtxoStore should result in empty utxo output
    @Test
    public void testEmptyTxOutput() {
        var store = new InMemoryUTxOStore();

        var supplier = new HydraUtxoStoreUTxOSupplier(store);

        Optional<Utxo> maybeUtxo = supplier.getTxOutput("b9f48dd61b739c7deb55a55bc8fe8097165379efcfa918010fec75de6c6b8f64", 0);

        assertThat(maybeUtxo).isNotPresent();
    }

    // empty UtxoStore should result in empty utxo output
    @Test
    public void testTxOutputRetrieval() {
        var utxo1 = UTXO.builder()
                .address("addr_test1vqg9ywrpx6e50uam03nlu0ewunh3yrscxmjayurmkp52lfskgkq5k")
                .value(Map.of("lovelace", BigInteger.valueOf(10000000L)))
                .build();

        var utxo2 = UTXO.builder()
                .address("addr_test1vru2drx33ev6dt8gfq245r5k0tmy7ngqe79va69de9dxkrg09c7d3")
                .value(Map.of("lovelace", BigInteger.valueOf(989834587L)))
                .build();

        var utxo3 = UTXO.builder()
                .address("addr_test1vqg9ywrpx6e50uam03nlu0ewunh3yrscxmjayurmkp52lfskgkq5k")
                .value(Map.of("lovelace", BigInteger.valueOf(500000000L)))
                .build();

        var store = new InMemoryUTxOStore(Map.of(
                "b9f48dd61b739c7deb55a55bc8fe8097165379efcfa918010fec75de6c6b8f64#0", utxo1
                , "b9f48dd61b739c7deb55a55bc8fe8097165379efcfa918010fec75de6c6b8f64#1", utxo2
                , "db982e0b69fb742188e45feedfd631bbce6738884d266356868efb9907e10cf9#0", utxo3
        ));
        var supplier = new HydraUtxoStoreUTxOSupplier(store);

        Optional<Utxo> maybeUtxo = supplier.getTxOutput("b9f48dd61b739c7deb55a55bc8fe8097165379efcfa918010fec75de6c6b8f64", 1);

        assertThat(maybeUtxo).isPresent();
        assertThat(maybeUtxo).contains(HydraUtxoConverter.convert("b9f48dd61b739c7deb55a55bc8fe8097165379efcfa918010fec75de6c6b8f64", 1, utxo2));
    }

    // Here are looking up something that doesn't exist based on output index, so obviously it needs to fail
    @Test
    public void testTxOutputRetrievalFails() {
        var utxo1 = UTXO.builder()
                .address("addr_test1vqg9ywrpx6e50uam03nlu0ewunh3yrscxmjayurmkp52lfskgkq5k")
                .value(Map.of("lovelace", BigInteger.valueOf(10000000L)))
                .build();

        var utxo2 = UTXO.builder()
                .address("addr_test1vru2drx33ev6dt8gfq245r5k0tmy7ngqe79va69de9dxkrg09c7d3")
                .value(Map.of("lovelace", BigInteger.valueOf(989834587L)))
                .build();

        var utxo3 = UTXO.builder()
                .address("addr_test1vqg9ywrpx6e50uam03nlu0ewunh3yrscxmjayurmkp52lfskgkq5k")
                .value(Map.of("lovelace", BigInteger.valueOf(500000000L)))
                .build();

        var store = new InMemoryUTxOStore(Map.of(
                "b9f48dd61b739c7deb55a55bc8fe8097165379efcfa918010fec75de6c6b8f64#0", utxo1
                , "b9f48dd61b739c7deb55a55bc8fe8097165379efcfa918010fec75de6c6b8f64#1", utxo2
                , "db982e0b69fb742188e45feedfd631bbce6738884d266356868efb9907e10cf9#0", utxo3
        ));
        var supplier = new HydraUtxoStoreUTxOSupplier(store);

        Optional<Utxo> maybeUtxo = supplier.getTxOutput("b9f48dd61b739c7deb55a55bc8fe8097165379efcfa918010fec75de6c6b8f64", 2);

        assertThat(maybeUtxo).isNotPresent();
    }

    // empty UtxoStore should result in empty list of utxos
    @Test
    public void testEmpty() {
        var store = new InMemoryUTxOStore();

        var supplier = new HydraUtxoStoreUTxOSupplier(store);

        List<Utxo> utxos = supplier.getPage("addr_test1vqg9ywrpx6e50uam03nlu0ewunh3yrscxmjayurmkp52lfskgkq5k", 100, 0, asc);

        assertThat(utxos).isEmpty();
    }

    // for the moment we support no pagination in the supplier (only page 0), (this is something to consider in the future)
    @Test
    public void testEmptyWithPageMoreThan1() {
        var store = new InMemoryUTxOStore();

        var supplier = new HydraUtxoStoreUTxOSupplier(store);

        List<Utxo> utxos = supplier.getPage("addr_test1vqg9ywrpx6e50uam03nlu0ewunh3yrscxmjayurmkp52lfskgkq5k", 100, 1, asc);

        assertThat(utxos).isEmpty();
    }

    // negative pagination makes no sense whatsoever
    @Test
    public void testEmptyWithNegativePage() {
        var store = new InMemoryUTxOStore();

        var supplier = new HydraUtxoStoreUTxOSupplier(store);

        List<Utxo> utxos = supplier.getPage("addr_test1vqg9ywrpx6e50uam03nlu0ewunh3yrscxmjayurmkp52lfskgkq5k", 100, -1, asc);

        assertThat(utxos).isEmpty();
    }

    // we will demonstrate querying for page 0 with page size of 100
    @Test
    public void testSimpleUtxOEntriesWith100ItemsPerPage() {
        var utxo1 = UTXO.builder()
                .address("addr_test1vqg9ywrpx6e50uam03nlu0ewunh3yrscxmjayurmkp52lfskgkq5k")
                .value(Map.of("lovelace", BigInteger.valueOf(10000000L)))
                .build();

        var utxo2 = UTXO.builder()
                .address("addr_test1vru2drx33ev6dt8gfq245r5k0tmy7ngqe79va69de9dxkrg09c7d3")
                .value(Map.of("lovelace", BigInteger.valueOf(989834587L)))
                .build();

        var utxo3 = UTXO.builder()
                .address("addr_test1vqg9ywrpx6e50uam03nlu0ewunh3yrscxmjayurmkp52lfskgkq5k")
                .value(Map.of("lovelace", BigInteger.valueOf(500000000L)))
                .build();

        var store = new InMemoryUTxOStore(Map.of(
                "b9f48dd61b739c7deb55a55bc8fe8097165379efcfa918010fec75de6c6b8f64#0", utxo1
                , "b9f48dd61b739c7deb55a55bc8fe8097165379efcfa918010fec75de6c6b8f64#1", utxo2
                , "db982e0b69fb742188e45feedfd631bbce6738884d266356868efb9907e10cf9#0", utxo3
        ));

        var supplier = new HydraUtxoStoreUTxOSupplier(store);

        List<Utxo> utxos = supplier.getPage("addr_test1vqg9ywrpx6e50uam03nlu0ewunh3yrscxmjayurmkp52lfskgkq5k", 100, 0, asc);

        assertThat(utxos).hasSize(2);

        assertThat(utxos).contains(HydraUtxoConverter.convert("db982e0b69fb742188e45feedfd631bbce6738884d266356868efb9907e10cf9", 0, utxo3));
        assertThat(utxos).contains(HydraUtxoConverter.convert("b9f48dd61b739c7deb55a55bc8fe8097165379efcfa918010fec75de6c6b8f64", 0, utxo1));
    }

    // we will demonstrate querying for page 0 with null page size
    @Test
    public void testSimpleUtxOEntries() {
        var utxo1 = UTXO.builder()
                .address("addr_test1vqg9ywrpx6e50uam03nlu0ewunh3yrscxmjayurmkp52lfskgkq5k")
                .value(Map.of("lovelace", BigInteger.valueOf(10000000L)))
                .build();

        var utxo2 = UTXO.builder()
                .address("addr_test1vru2drx33ev6dt8gfq245r5k0tmy7ngqe79va69de9dxkrg09c7d3")
                .value(Map.of("lovelace", BigInteger.valueOf(989834587L)))
                .build();

        var utxo3 = UTXO.builder()
                .address("addr_test1vqg9ywrpx6e50uam03nlu0ewunh3yrscxmjayurmkp52lfskgkq5k")
                .value(Map.of("lovelace", BigInteger.valueOf(500000000L)))
                .build();

        var store = new InMemoryUTxOStore(Map.of(
                "b9f48dd61b739c7deb55a55bc8fe8097165379efcfa918010fec75de6c6b8f64#0", utxo1
                , "b9f48dd61b739c7deb55a55bc8fe8097165379efcfa918010fec75de6c6b8f64#1", utxo2
                , "db982e0b69fb742188e45feedfd631bbce6738884d266356868efb9907e10cf9#0", utxo3
        ));

        var supplier = new HydraUtxoStoreUTxOSupplier(store);

        List<Utxo> utxos = supplier.getPage("addr_test1vqg9ywrpx6e50uam03nlu0ewunh3yrscxmjayurmkp52lfskgkq5k", null, 0, asc);

        assertThat(utxos).hasSize(2);

        assertThat(utxos).contains(HydraUtxoConverter.convert("db982e0b69fb742188e45feedfd631bbce6738884d266356868efb9907e10cf9", 0, utxo3));
        assertThat(utxos).contains(HydraUtxoConverter.convert("b9f48dd61b739c7deb55a55bc8fe8097165379efcfa918010fec75de6c6b8f64", 0, utxo1));
    }

}
