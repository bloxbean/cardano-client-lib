package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.backend.api.address.model.AddressUtxo;
import adlabs.nexus.client.backend.api.address.model.AssetBalance;
import adlabs.nexus.client.util.Network;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.UtxoService;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

/**
 * Nexus has no dedicated utxo endpoint; utxos are synthesized from the SDK address service's utxo listing.
 */
public class NexusUtxoService implements UtxoService {

    private final adlabs.nexus.client.backend.api.address.AddressService addressService;
    private final NexusTransactionService transactionService;
    private final Network network;

    public NexusUtxoService(adlabs.nexus.client.backend.api.address.AddressService addressService,
                             NexusTransactionService transactionService, Network network) {
        this.addressService = addressService;
        this.transactionService = transactionService;
        this.network = network;
    }

    @Override
    public Result<List<Utxo>> getUtxos(String address, int count, int page) throws ApiException {
        try {
            return NexusResultMapper.map(addressService.getAddressUtxos(network, address, page, count),
                    utxos -> toUtxos(address, utxos));
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    // Nexus has no order param; delegate as-is.
    @Override
    public Result<List<Utxo>> getUtxos(String address, int count, int page, OrderEnum order) throws ApiException {
        return getUtxos(address, count, page);
    }

    @Override
    public Result<List<Utxo>> getUtxos(String address, String unit, int count, int page) throws ApiException {
        try {
            return NexusResultMapper.map(addressService.getAddressUtxosByAsset(network, address, unit, page, count),
                    utxos -> toUtxos(address, utxos));
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    // Nexus has no order param; delegate as-is.
    @Override
    public Result<List<Utxo>> getUtxos(String address, String unit, int count, int page, OrderEnum order) throws ApiException {
        return getUtxos(address, unit, count, page);
    }

    @Override
    public Result<Utxo> getTxOutput(String txHash, int outputIndex) throws ApiException {
        return transactionService.getTransactionOutput(txHash, outputIndex);
    }

    private List<Utxo> toUtxos(String address, List<AddressUtxo> addressUtxos) {
        List<Utxo> utxos = new ArrayList<>();
        for (AddressUtxo addressUtxo : addressUtxos) {
            utxos.add(toUtxo(address, addressUtxo));
        }
        return utxos;
    }

    private Utxo toUtxo(String address, AddressUtxo addressUtxo) {
        Utxo utxo = new Utxo();
        utxo.setTxHash(addressUtxo.getTxHash());
        utxo.setOutputIndex(addressUtxo.getTxIndex() == null ? 0 : addressUtxo.getTxIndex());
        utxo.setAddress(addressUtxo.getAddress() != null ? addressUtxo.getAddress() : address);
        utxo.setDataHash(addressUtxo.getDatumHash());
        if (addressUtxo.getInlineDatum() != null) {
            utxo.setInlineDatum(addressUtxo.getInlineDatum().getBytes());
        }
        if (addressUtxo.getReferenceScript() != null) {
            utxo.setReferenceScriptHash(addressUtxo.getReferenceScript().getHash());
        }
        utxo.setAmount(toAmounts(addressUtxo));
        return utxo;
    }

    private List<Amount> toAmounts(AddressUtxo addressUtxo) {
        List<Amount> amounts = new ArrayList<>();
        amounts.add(new Amount(LOVELACE, new BigInteger(addressUtxo.getValue())));
        if (addressUtxo.getAssets() != null) {
            for (AssetBalance asset : addressUtxo.getAssets()) {
                String unit = asset.getUnit() != null ? asset.getUnit() : asset.getPolicyId() + asset.getAssetName();
                amounts.add(new Amount(unit, new BigInteger(asset.getQuantity())));
            }
        }
        return amounts;
    }
}
