package com.bloxbean.cardano.client.backend.gql;

import com.bloxbean.cardano.client.backend.api.AddressService;
import com.bloxbean.cardano.client.backend.common.OrderEnum;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.gql.util.DateUtil;
import com.bloxbean.cardano.client.backend.model.AddressContent;
import com.bloxbean.cardano.client.backend.model.AddressTransactionContent;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.TxContentOutputAmount;
import com.bloxbean.cardano.gql.AddressSummaryQuery;
import com.bloxbean.cardano.gql.AddressTransactionsByInputsQuery;
import com.bloxbean.cardano.gql.AddressTransactionsByOutputsQuery;
import com.bloxbean.cardano.gql.type.Order_by;
import okhttp3.OkHttpClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

public class GqlAddressService extends BaseGqlService implements AddressService {

    public GqlAddressService(String gqlUrl) {
        super(gqlUrl);
    }

    public GqlAddressService(String gqlUrl, Map<String, String> headers) {
        super(gqlUrl, headers);
    }

    public GqlAddressService(String gqlUrl, OkHttpClient okHttpClient) {
        super(gqlUrl, okHttpClient);
    }

    @Override
    public Result<AddressContent> getAddressInfo(String address) throws ApiException {
        if(address == null || address.length() == 0)
            return Result.error("Empty address");

        AddressSummaryQuery query = new AddressSummaryQuery(Arrays.asList(address));
        AddressSummaryQuery.Data data = execute(query);
        if(data == null || data.paymentAddresses() == null || data.paymentAddresses().size() == 0)
            return Result.error("Unable to find summary for address: " + address);

        List<AddressContent> addressContents = data.paymentAddresses().stream()
                .map(paymentAddress -> {
                    AddressContent addressContent = new AddressContent();
                    AddressSummaryQuery.Summary summary = paymentAddress.summary();
                    if(summary != null && summary.assetBalances() != null) {
                        summary.assetBalances().forEach(assetBalance -> {
                            TxContentOutputAmount txContentOutputAmount = new TxContentOutputAmount();
                            if("ada".equals(assetBalance.asset().assetId())) { //GraphQL returns unit as ada for lovelace
                                txContentOutputAmount.setUnit(LOVELACE);
                            } else {
                                txContentOutputAmount.setUnit(String.valueOf(assetBalance.asset().assetId()));
                            }
                            txContentOutputAmount.setQuantity(assetBalance.quantity());
                            addressContent.getAmount().add(txContentOutputAmount);
                        });
                    }

                    return addressContent;
                }).collect(Collectors.toList());

        if(addressContents == null || addressContents.size() == 0)
            return Result.error("Address summary not found for address: " + address);

        return processSuccessResult(addressContents.get(0));
    }

    @Override
    public Result<List<AddressTransactionContent>> getTransactions(String address, int count, int page) throws ApiException {
        return getTransactions(address, count, page, OrderEnum.asc);
    }

    @Override
    public Result<List<AddressTransactionContent>> getTransactions(String address, int count, int page, OrderEnum order) throws ApiException {
        //TODO :- What happens if the transaction is not there outpus
        if(address == null || address.length() == 0)
            throw new ApiException("Empty address");

        if(page == 0)
            page = 1;

        page = page - 1;
        int offset = count * page;

        Order_by orderBy = convertOrderEnum(order);
        List<AddressTransactionContent> txnList = getTransactionsByOutput(address, count, offset, orderBy);
//        List<String> inputTxnList = getTransactionsByInput(address, count, offset, order);
//
//        for(String hash: inputTxnList) {
//            if(!txnList.contains(hash)) {
//                txnList.add(hash);
//            }
//        }

        return processSuccessResult(txnList);
    }

    private List<AddressTransactionContent> getTransactionsByOutput(String address, int count, int offset, Order_by order) throws ApiException {
        AddressTransactionsByOutputsQuery query = new AddressTransactionsByOutputsQuery(address, count, offset, order);
        AddressTransactionsByOutputsQuery.Data data = execute(query);

        if(data == null)
            throw new ApiException("Unable to find output transaction for the address: " + address);

        List<AddressTransactionContent> txnList = null;
        List<AddressTransactionsByOutputsQuery.Transaction> transactions = data.transactions();
        txnList = transactions.stream().map(transaction -> {
            AddressTransactionContent transactionContent = AddressTransactionContent.builder()
                    .txHash((String) transaction.hash())
                    .txIndex(transaction.blockIndex())
                    .blockHeight(transaction.block().number())
                    .build();
            try {
                transactionContent.setBlockTime(DateUtil.convertDateTimeToLong((String)transaction.includedAt()));
            } catch (Exception e) {
            }

            return transactionContent;
        }).collect(Collectors.toList());

        return txnList;
    }

    private List<AddressTransactionContent> getTransactionsByInput(String address, int count, int offset, Order_by order) throws ApiException {
        AddressTransactionsByInputsQuery query = new AddressTransactionsByInputsQuery(address, count, offset, order);
        AddressTransactionsByInputsQuery.Data data = execute(query);

        if(data == null)
            throw new ApiException("Unable to find input transaction for the address: " + address);

        List<AddressTransactionContent> txnList = new ArrayList<>();
        List<AddressTransactionsByInputsQuery.Transaction> transactions = data.transactions();
        txnList = transactions.stream().map(transaction -> {
            AddressTransactionContent transactionContent = AddressTransactionContent.builder()
                    .txHash((String) transaction.hash())
                    .txIndex(transaction.blockIndex())
                    .blockHeight(transaction.block().number())
                    .build();
            try {
                transactionContent.setBlockTime(DateUtil.convertDateTimeToLong((String)transaction.includedAt()));
            } catch (Exception e) {
            }

            return transactionContent;
        }).collect(Collectors.toList());

        return txnList;
    }

    private Order_by convertOrderEnum(OrderEnum orderEnum) {
        if(orderEnum == null)
            return Order_by.ASC;
        else if(orderEnum == OrderEnum.asc)
            return Order_by.ASC;
        else
            return Order_by.DESC;
    }
}
