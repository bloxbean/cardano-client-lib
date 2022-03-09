package com.bloxbean.cardano.client.backend.koios;

import com.bloxbean.cardano.client.backend.common.OrderEnum;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.AddressContent;
import com.bloxbean.cardano.client.backend.model.AddressTransactionContent;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.TxContentOutputAmount;
import rest.koios.client.backend.api.TxHash;
import rest.koios.client.backend.api.address.AddressService;
import rest.koios.client.backend.api.address.model.AddressInfo;
import rest.koios.client.backend.api.address.model.AddressUtxo;
import rest.koios.client.backend.api.address.model.Asset;
import rest.koios.client.backend.factory.options.*;
import rest.koios.client.backend.factory.options.filters.Filter;
import rest.koios.client.backend.factory.options.filters.FilterType;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class KoiosAddressService implements com.bloxbean.cardano.client.backend.api.AddressService {

    private final AddressService addressService;
    private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

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
            Option ordering = Order.by("block_height", SortType.ASC);
            if (order == OrderEnum.desc) {
                ordering = Order.by("block_height", SortType.DESC);
            }
            Options options = Options.builder()
                    .option(Limit.of(count))
                    .option(Offset.of((long) (page - 1) * count))
                    .option(ordering).build();
            if (from != null && !from.isEmpty()) {
                options.getOptions().add(Filter.of("block_height", FilterType.GTE, from));
            }
            if (to !=null && !to.isEmpty()) {
                options.getOptions().add(Filter.of("block_height", FilterType.LTE, to));
            }
            rest.koios.client.backend.api.base.Result<List<TxHash>> transactionsResult = addressService.getAddressTransactions(List.of(address), options);
            if (!transactionsResult.isSuccessful()) {
                return Result.error(transactionsResult.getResponse()).code(transactionsResult.getCode());
            }
            return convertToAddressTransactionContent(transactionsResult.getValue());
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        } catch (ParseException e) {
            throw new ApiException("Failed to Parse Date: " + e.getMessage(), e);
        }
    }

    private Result<List<AddressTransactionContent>> convertToAddressTransactionContent(List<TxHash> transactions) throws ParseException {
        List<AddressTransactionContent> addressTransactionContents = new ArrayList<>();
        for (TxHash txHash : transactions) {
            AddressTransactionContent addressTransactionContent = new AddressTransactionContent();
            addressTransactionContent.setTxHash(txHash.getTxHash());
//            addressTransactionContent.setTxIndex(); TODO
            addressTransactionContent.setBlockHeight(txHash.getBlockHeight());
            addressTransactionContent.setBlockTime(simpleDateFormat.parse(txHash.getBlockTime()).getTime() / 1000);
            addressTransactionContents.add(addressTransactionContent);
        }
        return Result.success("OK").withValue(addressTransactionContents).code(200);
    }
}
