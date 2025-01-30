package com.bloxbean.cardano.hdwallet.util;

import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.hdwallet.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HDWalletAddressIteratorTest {

    @Mock
    private UtxoService utxoService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void next() throws Exception{
        Wallet wallet = new Wallet();
        var addr1 = wallet.getAccountAtIndex(3).baseAddress();
        var addr2 = wallet.getAccountAtIndex(7).baseAddress();
        var addr3 = wallet.getAccountAtIndex(24).baseAddress();
        var addr4 = wallet.getAccountAtIndex(50).baseAddress();

        HDWalletAddressIterator addrIterator = new HDWalletAddressIterator(wallet, new DefaultUtxoSupplier(utxoService));

        given(utxoService.isUsedAddress(anyString())).willReturn(false);

        given(utxoService.isUsedAddress(addr1))
                .willReturn(true);
        given(utxoService.isUsedAddress(addr2))
                .willReturn(true);
        given(utxoService.isUsedAddress(addr3))
                .willReturn(true);
        given(utxoService.isUsedAddress(addr4))
                .willReturn(true);

        List<String> addressList = new ArrayList<>();
        while(addrIterator.hasNext()) {
            String address = addrIterator.next().toBech32();
            addressList.add(address);
        }

        assertThat(addressList).contains(addr1, addr2, addr3);
        assertThat(addressList).doesNotContain(addr4);
        assertThat(addressList).hasSize(45); //25 + 20

    }

    @Test
    void next_noTx() throws Exception{
        Wallet wallet = new Wallet();
        wallet.setGapLimit(5);

        var addr1 = wallet.getAccountAtIndex(5).baseAddress();
        var addr2 = wallet.getAccountAtIndex(20).baseAddress();

        HDWalletAddressIterator addrIterator = new HDWalletAddressIterator(wallet, new DefaultUtxoSupplier(utxoService));

        given(utxoService.isUsedAddress(anyString())).willReturn(false);

        List<String> addressList = new ArrayList<>();
        while(addrIterator.hasNext()) {
            String address = addrIterator.next().toBech32();
            addressList.add(address);
        }

        assertThat(addressList).hasSize(5);
        assertThat(addressList).doesNotContain(addr1, addr2);
    }

}
