package com.bloxbean.cardano.client.backend.koios.it;

import com.bloxbean.cardano.client.backend.api.AddressService;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.AddressContent;
import com.bloxbean.cardano.client.backend.model.AddressDetails;
import com.bloxbean.cardano.client.backend.model.AddressTransactionContent;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class KoiosAddressServiceIT extends KoiosBaseTest {

    private AddressService addressService;

    @BeforeEach
    public void setup() {
        addressService = backendService.getAddressService();
    }

    @Test
    void testGetAddressInfo() throws ApiException {
        Result<AddressContent> result = addressService.getAddressInfo("addr_test1qzr0g2kvyknzhyez3aatyjwpaw5z5n65cwfxc5ctcqq28ed3hcc035r9r76tkxehlr9wdla9twe02dpv843nru6czj6qycpamy");
        System.out.println(JsonUtil.getPrettyJson(result.getValue()));

        assertTrue(result.isSuccessful());
        assertFalse(result.getValue().getAmount().isEmpty());
    }

    @Test
    public void testGetAddressDetails() throws ApiException {
        Result<AddressDetails> result = addressService.getAddressDetails("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");
        System.out.println(JsonUtil.getPrettyJson(result.getValue()));

        assertTrue(result.isSuccessful());
        assertFalse(result.getValue().getReceivedSum().isEmpty());
    }

    @Test
    public void testGetTransactions() throws ApiException {
        String address = "addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y";
        List<AddressTransactionContent> txns = addressService.getTransactions(address, 50, 1).getValue();

        System.out.println(txns);
        assertTrue(txns.size() > 2);
        assertEquals("13e4f5a675e1e67f0c5b09a6a64e3eadf97cfe4e938c276541592e4a9c278468", txns.get(0).getTxHash());
        assertTrue(txns.get(0).getBlockHeight() != 0);
        assertTrue(txns.get(0).getBlockTime() != 0);
    }

    @Test
    void testGetTransactionsWithOrder() throws ApiException {
        String address = "addr_test1qzr0g2kvyknzhyez3aatyjwpaw5z5n65cwfxc5ctcqq28ed3hcc035r9r76tkxehlr9wdla9twe02dpv843nru6czj6qycpamy";
        List<AddressTransactionContent> txns = addressService.getTransactions(address, 50, 1, OrderEnum.desc).getValue();

        System.out.println(txns);
        assertTrue(txns.size() > 2);
        assertNotEquals("119a69ae496b03936335ed22416116c454a26c00c5c59b22f34535851ba3aa42", txns.get(0).getTxHash());
        assertTrue(txns.get(0).getBlockHeight() != 0);
        assertTrue(txns.get(0).getBlockTime() != 0);
    }

    @Test
    public void testGetTransactionsWithOrder_whenFromAndToBlocksProvided() throws ApiException {
        String address = "addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y";
        List<AddressTransactionContent> txns = addressService.getTransactions(address, 50, 1, OrderEnum.desc, "1504167", "1504167").getValue();

        System.out.println(txns);
        assertThat(txns.size()).isEqualTo(1);
        assertEquals("13e4f5a675e1e67f0c5b09a6a64e3eadf97cfe4e938c276541592e4a9c278468", txns.get(0).getTxHash());
        assertTrue(txns.get(0).getBlockHeight() != 0);
        assertTrue(txns.get(0).getBlockTime() != 0);
    }
}
