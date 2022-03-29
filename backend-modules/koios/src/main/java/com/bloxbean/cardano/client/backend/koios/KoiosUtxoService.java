package com.bloxbean.cardano.client.backend.koios;

import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import rest.koios.client.backend.api.address.AddressService;
import rest.koios.client.backend.api.address.model.AddressInfo;
import rest.koios.client.backend.api.address.model.AddressUtxo;
import rest.koios.client.backend.api.address.model.Asset;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

public class KoiosUtxoService implements UtxoService {

    private final AddressService addressService;

    public KoiosUtxoService(AddressService addressService) {
        this.addressService = addressService;
    }

    @Override
    public Result<List<Utxo>> getUtxos(String address, int count, int page) throws ApiException {
        try {
            if (page!=1) {
                return Result.success("OK").withValue(Collections.emptyList()).code(200);
            }
            rest.koios.client.backend.api.base.Result<AddressInfo> addressInformationResult = addressService.getAddressInformation(address);
            if (!addressInformationResult.isSuccessful()) {
                return Result.error(addressInformationResult.getResponse()).code(addressInformationResult.getCode());
            }
            return convertToUTxOs(addressInformationResult.getValue());
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private Result<List<Utxo>> convertToUTxOs(AddressInfo addressInfo) {
        List<Utxo> utxoList = new ArrayList<>();
        for (AddressUtxo addressUtxo : addressInfo.getUtxoSet()) {
            Utxo utxo = new Utxo();
            utxo.setTxHash(addressUtxo.getTxHash());
            utxo.setOutputIndex(addressUtxo.getTxIndex());
            utxo.setDataHash(addressUtxo.getDatumHash());
            List<Amount> amountList = new ArrayList<>();
            amountList.add(new Amount(LOVELACE, new BigInteger(addressUtxo.getValue())));
            for (Asset asset : addressUtxo.getAssetList()) {
                String key = asset.getPolicyId() + asset.getAssetName();
                amountList.add(new Amount(key, new BigInteger(asset.getQuantity())));
            }
            utxo.setAmount(amountList);
            utxoList.add(utxo);
        }
        return Result.success("OK").withValue(utxoList).code(200);
    }

    @Override
    public Result<List<Utxo>> getUtxos(String address, int count, int page, OrderEnum order) throws ApiException {
        return getUtxos(address, count, page);
    }
}
