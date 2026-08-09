package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.backend.api.address.model.AddressInfo;
import adlabs.nexus.client.backend.api.address.model.AddressTransaction;
import adlabs.nexus.client.backend.api.address.model.AssetBalance;
import adlabs.nexus.client.backend.api.address.model.Pagination;
import adlabs.nexus.client.backend.api.address.model.TransactionHistoryItem;
import adlabs.nexus.client.backend.api.address.model.TransactionHistoryResponse;
import adlabs.nexus.client.util.Network;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.AddressContent;
import com.bloxbean.cardano.client.backend.model.AddressDetails;
import com.bloxbean.cardano.client.backend.model.AddressTransactionContent;
import com.bloxbean.cardano.client.backend.model.TxContentOutputAmount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

/**
 * Nexus Address Service
 */
public class NexusAddressService implements com.bloxbean.cardano.client.backend.api.AddressService {

    private static final Logger log = LoggerFactory.getLogger(NexusAddressService.class);

    // Client-side defaults (not SDK-mandated): page size for the history fetch,
    // and a hard safety cap bounding the page-loop below.
    private static final int ALL_TRANSACTIONS_PAGE_SIZE = 100;
    private static final int ALL_TRANSACTIONS_MAX_PAGES = 1000;

    private final adlabs.nexus.client.backend.api.address.AddressService addressService;
    private final Network network;

    public NexusAddressService(adlabs.nexus.client.backend.api.address.AddressService addressService, Network network) {
        this.addressService = addressService;
        this.network = network;
    }

    @Override
    public Result<AddressContent> getAddressInfo(String address) throws ApiException {
        try {
            return NexusResultMapper.map(addressService.getAddressInformation(network, address), this::toAddressContent);
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<AddressDetails> getAddressDetails(String address) throws ApiException {
        throw new UnsupportedOperationException("getAddressDetails not supported by Nexus");
    }

    @Override
    public Result<List<AddressTransactionContent>> getTransactions(String address, int count, int page) throws ApiException {
        try {
            return NexusResultMapper.map(addressService.getAddressTransactions(network, address, page, count),
                    this::toAddressTransactionContents);
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    // Nexus has no order param; delegate as-is.
    @Override
    public Result<List<AddressTransactionContent>> getTransactions(String address, int count, int page, OrderEnum order) throws ApiException {
        return getTransactions(address, count, page);
    }

    // Nexus has no block-range filter on the paged endpoint; fetch the full history (already
    // block-filtered + ordered by getAllTransactions), then page client-side to match Blockfrost.
    @Override
    public Result<List<AddressTransactionContent>> getTransactions(String address, int count, int page, OrderEnum order, String fromBlockHeight, String toBlockHeight) throws ApiException {
        Integer from = (fromBlockHeight == null || fromBlockHeight.isEmpty()) ? null : Integer.valueOf(fromBlockHeight);
        Integer to = (toBlockHeight == null || toBlockHeight.isEmpty()) ? null : Integer.valueOf(toBlockHeight);
        Result<List<AddressTransactionContent>> all = getAllTransactions(address, order, from, to);
        if (!all.isSuccessful()) {
            return all;
        }
        List<AddressTransactionContent> list = all.getValue();
        int fromIdx = Math.max(0, (page - 1) * count);
        if (fromIdx >= list.size()) {
            return Result.success("OK").withValue(new ArrayList<>()).code(200);
        }
        int toIdx = Math.min(list.size(), fromIdx + count);
        return Result.success("OK").withValue(new ArrayList<>(list.subList(fromIdx, toIdx))).code(200);
    }

    // Nexus history is paginated server-side; loop until hasNext is false, capped to avoid an infinite loop on a misbehaving flag.
    @Override
    public Result<List<AddressTransactionContent>> getAllTransactions(String address, OrderEnum order, Integer fromBlockHeight, Integer toBlockHeight) throws ApiException {
        List<AddressTransactionContent> all = new ArrayList<>();
        boolean truncatedByCap = false;
        try {
            int page = 1;
            while (page <= ALL_TRANSACTIONS_MAX_PAGES) {
                adlabs.nexus.client.backend.api.base.Result<TransactionHistoryResponse> pageResult =
                        addressService.getAddressTransactionHistory(network, address, page, ALL_TRANSACTIONS_PAGE_SIZE);
                if (!pageResult.isSuccessful()) {
                    return Result.error(pageResult.getResponse()).code(pageResult.getCode());
                }
                TransactionHistoryResponse body = pageResult.getValue();
                if (body != null && body.getTransactions() != null) {
                    all.addAll(toAddressTransactionContentsFromHistory(body.getTransactions()));
                }
                Pagination pagination = body == null ? null : body.getPagination();
                boolean hasNext = pagination != null && Boolean.TRUE.equals(pagination.getHasNext());
                if (!hasNext) {
                    break;
                }
                if (page == ALL_TRANSACTIONS_MAX_PAGES) {
                    truncatedByCap = true;
                }
                page++;
            }
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }

        if (truncatedByCap) {
            log.warn("getAllTransactions truncated at {} pages ({} txs) for address {}; more pages were available",
                    ALL_TRANSACTIONS_MAX_PAGES, ALL_TRANSACTIONS_MAX_PAGES * ALL_TRANSACTIONS_PAGE_SIZE, address);
        }

        List<AddressTransactionContent> filtered = filterByBlockHeight(all, fromBlockHeight, toBlockHeight);
        if (order == OrderEnum.desc) {
            Collections.reverse(filtered);
        }
        return Result.success("OK").withValue(filtered).code(200);
    }

    private List<AddressTransactionContent> filterByBlockHeight(List<AddressTransactionContent> txs, Integer fromBlockHeight, Integer toBlockHeight) {
        List<AddressTransactionContent> result = new ArrayList<>();
        for (AddressTransactionContent tx : txs) {
            if (fromBlockHeight != null && tx.getBlockHeight() < fromBlockHeight) continue;
            if (toBlockHeight != null && tx.getBlockHeight() > toBlockHeight) continue;
            result.add(tx);
        }
        return result;
    }

    private List<AddressTransactionContent> toAddressTransactionContentsFromHistory(List<TransactionHistoryItem> items) {
        List<AddressTransactionContent> result = new ArrayList<>();
        for (TransactionHistoryItem item : items) {
            result.add(AddressTransactionContent.builder()
                    .txHash(item.getTxHash())
                    .txIndex(0)
                    .blockHeight(item.getBlockHeight() == null ? 0L : item.getBlockHeight())
                    .blockTime(item.getTxTimestamp() == null ? 0L : item.getTxTimestamp())
                    .build());
        }
        return result;
    }

    private AddressContent toAddressContent(AddressInfo addressInfo) {
        AddressContent addressContent = new AddressContent();
        addressContent.setStakeAddress(addressInfo.getStakeAddress());
        addressContent.setScript(addressInfo.getScriptAddress());
        addressContent.setType("shelley".equalsIgnoreCase(addressInfo.getAddressType())
                ? AddressContent.TypeEnum.SHELLEY : AddressContent.TypeEnum.BYRON);

        List<TxContentOutputAmount> amount = new ArrayList<>();
        amount.add(new TxContentOutputAmount(LOVELACE, addressInfo.getBalance()));
        if (addressInfo.getAssets() != null) {
            for (AssetBalance asset : addressInfo.getAssets()) {
                String unit = asset.getUnit() != null ? asset.getUnit() : asset.getPolicyId() + asset.getAssetName();
                amount.add(new TxContentOutputAmount(unit, asset.getQuantity()));
            }
        }
        addressContent.setAmount(amount);
        return addressContent;
    }

    private List<AddressTransactionContent> toAddressTransactionContents(List<AddressTransaction> txs) {
        List<AddressTransactionContent> result = new ArrayList<>();
        for (AddressTransaction tx : txs) {
            result.add(AddressTransactionContent.builder()
                    .txHash(tx.getTxHash())
                    .txIndex(tx.getTxIndex() == null ? 0 : tx.getTxIndex())
                    .blockHeight(tx.getBlockHeight() == null ? 0L : tx.getBlockHeight())
                    .blockTime(tx.getBlockTime() == null ? 0L : tx.getBlockTime())
                    .build());
        }
        return result;
    }
}
