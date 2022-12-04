package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.AddressContent;
import com.bloxbean.cardano.client.backend.model.AddressTransactionContent;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class AddressServiceIT extends BaseITTest {

    BackendService backendService;
    AddressService addressService;

    @BeforeEach
    public void setup() {
        backendService = getBackendService();
        addressService = backendService.getAddressService();
    }

    @Test
    public void testGetAddressInfo() throws ApiException {
        Result<AddressContent> result = addressService.getAddressInfo("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");
        System.out.println(JsonUtil.getPrettyJson(result.getValue()));

        assertTrue(result.isSuccessful());
        assertTrue(result.getValue().getAmount().size() > 0);
    }

    @Test
    public void testGetTransactions() throws ApiException {
        String address = "addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y";
        List<AddressTransactionContent> txns = addressService.getTransactions(address, 50, 1).getValue();

        System.out.println(txns);
        assertTrue(txns.size() > 2);
        assertEquals("0d79289c5d11cc5e8d8e0fc234ea654abfec62b1a9643e1f33cd3da2b513d19e", txns.get(0).getTxHash());
        assertTrue(txns.get(0).getBlockHeight() != 0);
        assertTrue(txns.get(0).getBlockTime() != 0);
    }

    @Test
    public void testGetTransactionsWithOrder() throws ApiException {
        String address = "addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y";
        List<AddressTransactionContent> txns = addressService.getTransactions(address, 50, 1, OrderEnum.desc).getValue();

        System.out.println(txns);
        assertTrue(txns.size() > 2);
        assertNotEquals("0d79289c5d11cc5e8d8e0fc234ea654abfec62b1a9643e1f33cd3da2b513d19e", txns.get(0).getTxHash());
        assertTrue(txns.get(0).getBlockHeight() != 0);
        assertTrue(txns.get(0).getBlockTime() != 0);
    }

    @Test
    public void testGetTransactionsWithOrder_whenFromAndToBlocksProvided() throws ApiException {
        String address = "addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y";
        List<AddressTransactionContent> txns = addressService.getTransactions(address, 50, 1, OrderEnum.desc, "357475", "357479").getValue();

        System.out.println(txns);
        assertThat(txns.size()).isEqualTo(3);
        assertEquals("002dbdb2d294a61c03ec7b0876bc5d40ec3ae07ef5b72d08c107cce7566c4f96", txns.get(0).getTxHash());
        assertTrue(txns.get(0).getBlockHeight() != 0);
        assertTrue(txns.get(0).getBlockTime() != 0);
    }
}

