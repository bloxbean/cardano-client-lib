package com.bloxbean.cardano.hdwallet.supplier;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.cardano.client.crypto.cip1852.Segment;
import com.bloxbean.cardano.hdwallet.Wallet;
import com.bloxbean.cardano.hdwallet.WalletException;
import com.bloxbean.cardano.client.api.model.WalletUtxo;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class DefaultWalletUtxoSupplier implements WalletUtxoSupplier {
    private final UtxoService utxoService;
    @Setter
    private Wallet wallet;

    public DefaultWalletUtxoSupplier(UtxoService utxoService, Wallet wallet) {
        this.utxoService = utxoService;
        this.wallet = wallet;
    }

    @Override
    public List<Utxo> getPage(Iterator<String> addressIteraotr, Integer nrOfItems, Integer page, OrderEnum order) {
        return null;
    }

    @Override
    public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
        //All utxos should be fetched at page=0
        if (page == 0)
            return getAll(address); // todo get Page of utxo over multipe addresses - find a good way to aktually do something with page, nrOfItems and order
        else
            return Collections.emptyList();
    }

    @Override
    public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {
        try {
            var result = utxoService.getTxOutput(txHash, outputIndex);
            return result != null && result.getValue() != null
                    ? Optional.of(WalletUtxo.from(result.getValue()))
                    : Optional.empty();
        } catch (ApiException e) {
            throw new ApiRuntimeException(e);
        }
    }

    @Override
    public List<Utxo> getAll(String address) {
        checkIfWalletIsSet();
        return new ArrayList<>(getAll());
    }

    @Override
    public List<WalletUtxo> getAll() {
        List<WalletUtxo> utxos = new ArrayList<>();

        if (wallet.getIndexesToScan() == null || wallet.getIndexesToScan().length == 0) {
            int index = 0;
            int noUtxoFound = 0;

            while (noUtxoFound < wallet.getGapLimit()) {
                List<WalletUtxo> utxoFromIndex = getUtxosForAccountAndIndex(wallet.getAccountNo(), index);

                utxos.addAll(utxoFromIndex);
                noUtxoFound = utxoFromIndex.isEmpty() ? noUtxoFound + 1 : 0;

                index++; // increasing search index
            }
        } else {
            for (int idx: wallet.getIndexesToScan()) {
                List<WalletUtxo> utxoFromIndex = getUtxosForAccountAndIndex(wallet.getAccountNo(), idx);
                utxos.addAll(utxoFromIndex);
            }
        }
        return utxos;
    }

    @Override
    public List<WalletUtxo> getUtxosForAccountAndIndex(int account, int index) {
        checkIfWalletIsSet();

        Address address = getBaseAddress(account, index);

        if (log.isDebugEnabled())
            log.debug("Scanning address for account: {}, index: {}", account, index);

        String addrStr = address.getAddress();
        String addrVkh =  address.getBech32VerificationKeyHash().orElse(null);

        List<WalletUtxo> utxos = new ArrayList<>();
        int page = 1;
        while(true) {
            Result<List<Utxo>> result;

            String searchKey;
            if (wallet.isSearchUtxoByAddrVkh())
                searchKey = addrVkh;
            else
                searchKey = addrStr;

            try {
                result = utxoService.getUtxos(searchKey, UtxoSupplier.DEFAULT_NR_OF_ITEMS_TO_FETCH, page, OrderEnum.asc);
            } catch (ApiException e) {
                throw new ApiRuntimeException(e);
            }
            List<Utxo> utxoPage = result != null && result.getValue() != null ? result.getValue() : Collections.emptyList();

            DerivationPath derivationPath = DerivationPath.createExternalAddressDerivationPathForAccount(account);
            derivationPath.setIndex(Segment.builder().value(index).build());

            var utxoList = utxoPage.stream().map(utxo -> {
                var walletUtxo = WalletUtxo.from(utxo);
                walletUtxo.setDerivationPath(derivationPath);
                return walletUtxo;
            }).collect(Collectors.toList());

            utxos.addAll(utxoList);
            if(utxoPage.size() < DEFAULT_NR_OF_ITEMS_TO_FETCH)
                break;
            page++;
        }
        return utxos;
    }

    private Address getBaseAddress(int account, int index) {
        return wallet.getBaseAddress(account, index);
    }

    private void checkIfWalletIsSet() {
        if(this.wallet == null)
            throw new WalletException("Wallet has to be provided!");
    }
}
