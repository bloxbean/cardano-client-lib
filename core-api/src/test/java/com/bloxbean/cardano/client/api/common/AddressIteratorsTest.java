package com.bloxbean.cardano.client.api.common;

import com.bloxbean.cardano.client.address.Address;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AddressIteratorsTest {

    @Test
    void hasNext_singleAddrIter() {
        Address address = new Address("addr1v8v3auqmw0eszza3ww29ea2pwftuqrqqyu26zvzjq9dt2ncydzvs5");

        var addrIter = AddressIterators.of(address);
        assertThat(addrIter.hasNext()).isTrue();
        assertThat(addrIter.next()).isEqualTo(address);
        assertThat(addrIter.hasNext()).isFalse();
    }

    @Test
    void reset_singleAddrIter() {
        Address address = new Address("addr1v8v3auqmw0eszza3ww29ea2pwftuqrqqyu26zvzjq9dt2ncydzvs5");

        var addrIter = AddressIterators.of(address);
        addrIter.next();
        assertThat(addrIter.hasNext()).isFalse();

        addrIter.reset();
        assertThat(addrIter.hasNext()).isTrue();
        assertThat(addrIter.next()).isEqualTo(address);
    }

}
