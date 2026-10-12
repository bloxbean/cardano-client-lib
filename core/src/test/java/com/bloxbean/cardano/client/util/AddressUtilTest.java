package com.bloxbean.cardano.client.util;

import com.bloxbean.cardano.client.address.util.AddressUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AddressUtilTest {

    @Test
    public void testValidShelleyAddress() {
        boolean isValid = AddressUtil.isValidAddress("addr1qxkeutm43mhc8jpqg6sk4cqtypzy3ez6z8k7qlfwa97h2acz7xprvuysll04e5gaa65vavyj0wvd0v99lhpntm7c03us8wk6xc");

        assertTrue(isValid);
    }

    @Test
    public void testInvalidShelleyAddr() {
        assertFalse(AddressUtil.isValidAddress("addr1qxkeutm43mhc8jpqg6sk4cqtypzy3ez6z8k7qlfwa97h2acz7xprvuysll04e5gaa65vavyj0wvd0v99lhpntm7c03us8wk6xa"));
    }

    @Test
    public void testInvalidJunk() {
        assertFalse(AddressUtil.isValidAddress("some_text"));
    }

    @Test
    public void testValidByronAddr() {
        boolean isValid = AddressUtil.isValidAddress("DdzFFzCqrhszg6cqZvDhEwUX7cZyNzdycAVpm4Uo2vjKMgTLrVqiVKi3MBt2tFAtDe7NkptK6TAhVkiYzhavmKV5hE79CWwJnPCJTREK");

        assertTrue(isValid);
    }

    @Test
    public void testInvalidByronAddr() {
        assertFalse(AddressUtil.isValidAddress("ExxxFFzCqrhszg6cqZvDhEwUX7cZyNzdycAVpm4Uo2vjKMgTLrVqiVKi3MBt2tFAtDe7NkptK6TAhVkiYzhavmKV5hE79CWwJnPCJTREk"));
    }

}
