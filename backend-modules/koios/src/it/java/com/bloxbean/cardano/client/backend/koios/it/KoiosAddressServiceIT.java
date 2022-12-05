package com.bloxbean.cardano.client.backend.koios.it;

import com.bloxbean.cardano.client.backend.api.AddressService;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.AddressContent;
import com.bloxbean.cardano.client.backend.model.AddressTransactionContent;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        assertTrue(result.getValue().getAmount().size() > 0);
    }

    @Test
    void testGetTransactions() throws ApiException {
        String address = "addr_test1qzr0g2kvyknzhyez3aatyjwpaw5z5n65cwfxc5ctcqq28ed3hcc035r9r76tkxehlr9wdla9twe02dpv843nru6czj6qycpamy";
        List<AddressTransactionContent> txns = addressService.getTransactions(address, 50, 1).getValue();

        System.out.println(txns);
        assertTrue(txns.size() > 2);
        assertEquals("119a69ae496b03936335ed22416116c454a26c00c5c59b22f34535851ba3aa42", txns.get(0).getTxHash());
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
    void testGetTransactionsWithOrder_whenFromAndToBlocksProvided() throws ApiException {
        String address = "addr_test1qzr0g2kvyknzhyez3aatyjwpaw5z5n65cwfxc5ctcqq28ed3hcc035r9r76tkxehlr9wdla9twe02dpv843nru6czj6qycpamy";
        List<AddressTransactionContent> txns = addressService.getTransactions(address, 50, 1, OrderEnum.desc, "267756", "353403").getValue();

        System.out.println(txns);
        assertEquals(49, txns.size());
        assertEquals("a41125d2fcba854491e01d4ed0deba9094d88b947b0c753252799b3087bf3f50", txns.get(0).getTxHash());
        assertTrue(txns.get(0).getBlockHeight() != 0);
        assertTrue(txns.get(0).getBlockTime() != 0);
    }
}
