package com.bloxbean.cardano.client.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AddressUtilTest {

    @Test
    public void testValidShelleyAddress() {
        boolean isValid = AddressUtil.isValidAddress("addr1qxkeutm43mhc8jpqg6sk4cqtypzy3ez6z8k7qlfwa97h2acz7xprvuysll04e5gaa65vavyj0wvd0v99lhpntm7c03us8wk6xc");

        assertTrue(isValid);
    }

    @Test
    public void testInvalidShelleyAddr() {
        boolean isValid = AddressUtil.isValidAddress("addr1qxkeutm43mhc8jpqg6sk4cqtypzy3ez6z8k7qlfwa97h2acz7xprvuysll04e5gaa65vavyj0wvd0v99lhpntm7c03us8wk6xa");

        assertFalse(isValid);
    }

    @Test
    public void testInvalidJunk() {
        boolean isValid = AddressUtil.isValidAddress("some_text");

        assertFalse(isValid);
    }

    @Test
    public void testValidByronAddr() {
        boolean isValid = AddressUtil.isValidAddress("DdzFFzCqrhszg6cqZvDhEwUX7cZyNzdycAVpm4Uo2vjKMgTLrVqiVKi3MBt2tFAtDe7NkptK6TAhVkiYzhavmKV5hE79CWwJnPCJTREK");

        assertTrue(isValid);
    }

    @Test
    public void testInvalidByronAddr() {
        boolean isValid = AddressUtil.isValidAddress("DdzFFzCqrhszg6cqZvDhEwUX7cZyNzdycAVpm4Uo2vjKMgTLrVqiVKi3MBt2tFAtDe7NkptK6TAhVkiYzhavmKV5hE79CWwJnPCJTREk");

        assertFalse(isValid);
    }

}
