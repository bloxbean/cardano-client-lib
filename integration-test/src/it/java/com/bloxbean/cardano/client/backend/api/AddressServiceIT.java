package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.backend.common.OrderEnum;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.AddressContent;
import com.bloxbean.cardano.client.backend.model.AddressTransactionContent;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        assertEquals("4123d70f66414cc921f6ffc29a899aafc7137a99a0fd453d6b200863ef5702d6", txns.get(0).getTxHash());
        assertTrue(txns.get(0).getBlockHeight() != 0);
        assertTrue(txns.get(0).getBlockTime() != 0);
    }

    @Test
    public void testGetTransactionsWithOrder() throws ApiException {
        String address = "addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y";
        List<AddressTransactionContent> txns = addressService.getTransactions(address, 50, 1, OrderEnum.desc).getValue();

        System.out.println(txns);
        assertTrue(txns.size() > 2);
        assertNotEquals("4123d70f66414cc921f6ffc29a899aafc7137a99a0fd453d6b200863ef5702d6", txns.get(0).getTxHash());
        assertTrue(txns.get(0).getBlockHeight() != 0);
        assertTrue(txns.get(0).getBlockTime() != 0);
    }

    @Test
    public void testGetTransactionsWithOrder_whenFromAndToBlocksProvided() throws ApiException {
        String address = "addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y";
        List<AddressTransactionContent> txns = addressService.getTransactions(address, 50, 1, OrderEnum.desc, "3168087", "3168097").getValue();

        System.out.println(txns);
        assertTrue(txns.size() == 8);
        assertEquals("389867cb7a962b370764c1c255c54e72abc9073983e9b17d5aabcb3fe043b8c3", txns.get(0).getTxHash());
        assertTrue(txns.get(0).getBlockHeight() != 0);
        assertTrue(txns.get(0).getBlockTime() != 0);
    }
}

