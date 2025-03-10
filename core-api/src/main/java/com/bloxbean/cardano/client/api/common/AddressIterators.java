package com.bloxbean.cardano.client.api.common;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.AddressIterator;

import java.util.Iterator;
import java.util.List;

public class AddressIterators {

    public static AddressIterator of(Address address) {
        return new SingleAddressIterator(address);
    }

    public static AddressIterator of(String address) {
        return new SingleAddressIterator(new Address(address));
    }

    static class SingleAddressIterator implements AddressIterator {
        private Address address;
        private Iterator<Address> iterator;

        public SingleAddressIterator(Address address) {
            this.address = address;
            this.iterator = List.of(address).iterator();
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public Address next() {
            return iterator.next();
        }

        @Override
        public Address getFirst() {
            return address;
        }

        @Override
        public void reset() {
            this.iterator = List.of(address).iterator();
        }

        @Override
        public AddressIterator clone() {
            return new SingleAddressIterator(address);
        }
    }
}
