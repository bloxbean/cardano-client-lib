package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
public class DefaultUtxoSupplier implements UtxoSupplier {
    private final UtxoService utxoService;
    private boolean searchByAddressVkh;

    public DefaultUtxoSupplier(UtxoService utxoService){
        this.utxoService = utxoService;
    }

    @Override
    public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
        String searchKey = address;
        if (searchByAddressVkh) {
            Address addr = new Address(address);
            if (addr != null) {
                searchKey = addr.getBech32VerificationKeyHash().orElse(null);
            } else {
                log.warn("searchByAddressVkh is true, but no payment credential found for the address {}.", address);
            }
        }

        try{
            var result = utxoService.getUtxos(searchKey, nrOfItems != null ? nrOfItems : UtxoSupplier.DEFAULT_NR_OF_ITEMS_TO_FETCH, page != null ? page + 1 : 1, order);
            return result != null && result.getValue() != null ? result.getValue() : Collections.emptyList();
        } catch (ApiException e) {
            throw new ApiRuntimeException(e);
        }
    }

    @Override
    public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {
        try {
            var result = utxoService.getTxOutput(txHash, outputIndex);
            return result != null && result.getValue() != null ? Optional.of(result.getValue()) : Optional.empty();
        } catch (ApiException e) {
            throw new ApiRuntimeException(e);
        }
    }

    @Override
    public boolean isUsedAddress(Address address) {
        try {
            return utxoService.isUsedAddress(address.toBech32());
        } catch (ApiException e) {
            throw new ApiRuntimeException(e);
        }
    }

    @Override
    public void setSearchByAddressVkh(boolean flag) {
        this.searchByAddressVkh = flag;
    }
}
