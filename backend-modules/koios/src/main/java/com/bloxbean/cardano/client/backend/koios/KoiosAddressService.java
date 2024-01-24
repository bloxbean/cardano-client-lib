package com.bloxbean.cardano.client.backend.koios;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.AddressContent;
import com.bloxbean.cardano.client.backend.model.AddressDetails;
import com.bloxbean.cardano.client.backend.model.AddressTransactionContent;
import com.bloxbean.cardano.client.backend.model.TxContentOutputAmount;
import rest.koios.client.backend.api.address.AddressService;
import rest.koios.client.backend.api.address.model.AddressInfo;
import rest.koios.client.backend.api.address.model.AddressUtxo;
import rest.koios.client.backend.api.base.common.Asset;
import rest.koios.client.backend.api.base.common.UTxO;
import rest.koios.client.backend.factory.options.*;
import rest.koios.client.backend.factory.options.filters.Filter;
import rest.koios.client.backend.factory.options.filters.FilterType;

import java.text.ParseException;
import java.util.*;

/**
 * Koios Address Service
 */
public class KoiosAddressService implements com.bloxbean.cardano.client.backend.api.AddressService {

    /**
     * Address Service
     */
    private final AddressService addressService;

    /**
     * KoiosAddressService Constructor
     *
     * @param addressService addressService
     */
    public KoiosAddressService(AddressService addressService) {
        this.addressService = addressService;
    }

    @Override
    public Result<AddressContent> getAddressInfo(String address) throws ApiException {
        try {
            rest.koios.client.backend.api.base.Result<AddressInfo> addressInformationResult = addressService.getAddressInformation(address);
            if (!addressInformationResult.isSuccessful()) {
                return Result.error(addressInformationResult.getResponse()).code(addressInformationResult.getCode());
            }
            return convertToAddressContent(addressInformationResult.getValue());
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<AddressDetails> getAddressDetails(String address) throws ApiException {
        throw new UnsupportedOperationException("Not yet supported");
    }

    private Result<AddressContent> convertToAddressContent(AddressInfo addressInfo) {
        AddressContent addressContent = new AddressContent();
        addressContent.setStakeAddress(addressInfo.getStakeAddress());
        if (addressContent.getStakeAddress() == null || addressContent.getStakeAddress().isEmpty()) {
            addressContent.setType(AddressContent.TypeEnum.BYRON);
        } else {
            addressContent.setType(AddressContent.TypeEnum.SHELLEY);
        }
        Map<String, TxContentOutputAmount> assetMap = new HashMap<>();
        for (AddressUtxo addressUtxo : addressInfo.getUtxoSet()) {
            for (Asset asset : addressUtxo.getAssetList()) {
                String key = asset.getPolicyId() + asset.getAssetName();
                if (assetMap.containsKey(key)) {
                    assetMap.get(key).setQuantity(String.valueOf(Long.parseLong(assetMap.get(key).getQuantity()) + Long.parseLong(asset.getQuantity())));
                } else {
                    assetMap.put(key, new TxContentOutputAmount(key, asset.getQuantity()));
                }
            }
        }
        List<TxContentOutputAmount> txContentOutputAmountList = new ArrayList<>(assetMap.values());
        txContentOutputAmountList.sort(Comparator.comparing(TxContentOutputAmount::getUnit));
        txContentOutputAmountList.add(0, new TxContentOutputAmount("lovelace", addressInfo.getBalance()));
        addressContent.setAmount(txContentOutputAmountList);
        addressContent.setScript(addressInfo.getScriptAddress());

        return Result.success("OK").withValue(addressContent).code(200);
    }

    @Override
    public Result<List<AddressTransactionContent>> getTransactions(String address, int count, int page) throws ApiException {
        return getTransactions(address, count, page, OrderEnum.asc);
    }

    @Override
    public Result<List<AddressTransactionContent>> getTransactions(String address, int count, int page, OrderEnum order) throws ApiException {
        return getTransactions(address, count, page, order, null, null);
    }

    @Override
    public Result<List<AddressTransactionContent>> getTransactions(String address, int count, int page, OrderEnum order, String from, String to) throws ApiException {
        try {
            Option ordering = Order.by("block_time", SortType.ASC);
            if (order == OrderEnum.desc) {
                ordering = Order.by("block_time", SortType.DESC);
            }
            Options options = Options.builder()
                    .option(Limit.of(count))
                    .option(Offset.of((long) (page - 1) * count))
                    .option(ordering).build();
            if (from != null && !from.isEmpty()) {
                options.getOptionList().add(Filter.of("block_height", FilterType.GTE, from));
            }
            if (to != null && !to.isEmpty()) {
                options.getOptionList().add(Filter.of("block_height", FilterType.LTE, to));
            }
            rest.koios.client.backend.api.base.Result<List<UTxO>> addressUTxOsResult = addressService.getAddressUTxOs(List.of(address), true, options);
            if (!addressUTxOsResult.isSuccessful()) {
                return Result.error(addressUTxOsResult.getResponse()).code(addressUTxOsResult.getCode());
            }
            return convertToAddressTransactionContent(addressUTxOsResult.getValue());
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        } catch (ParseException e) {
            throw new ApiException("Failed to Parse Date: " + e.getMessage(), e);
        }
    }

    private Result<List<AddressTransactionContent>> convertToAddressTransactionContent(List<UTxO> utxos) throws ParseException {
        List<AddressTransactionContent> addressTransactionContents = new ArrayList<>();
        for (UTxO utxo : utxos) {
            AddressTransactionContent addressTransactionContent = new AddressTransactionContent();
            addressTransactionContent.setTxHash(utxo.getTxHash());
            addressTransactionContent.setTxIndex(utxo.getTxIndex());
            addressTransactionContent.setBlockHeight(utxo.getBlockHeight());
            addressTransactionContent.setBlockTime(utxo.getBlockTime());
            addressTransactionContents.add(addressTransactionContent);
        }
        return Result.success("OK").withValue(addressTransactionContents).code(200);
    }
}
