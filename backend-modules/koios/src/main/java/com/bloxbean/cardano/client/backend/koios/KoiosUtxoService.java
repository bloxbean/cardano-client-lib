package com.bloxbean.cardano.client.backend.koios;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import rest.koios.client.backend.api.address.AddressService;
import rest.koios.client.backend.api.address.model.AddressInfo;
import rest.koios.client.backend.api.address.model.AddressUtxo;
import rest.koios.client.backend.api.base.common.Asset;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

/**
 * Koios Utxo Service
 */
public class KoiosUtxoService implements UtxoService {

    /**
     * Address Service
     */
    private final AddressService addressService;
    /**
     * Transaction Service
     */
    private final TransactionService transactionService;

    /**
     * KoiosUtxoService Constructor
     *
     * @param addressService     addressService
     * @param transactionService transactionService
     */
    public KoiosUtxoService(AddressService addressService, TransactionService transactionService) {
        this.addressService = addressService;
        this.transactionService = transactionService;
    }

    @Override
    public Result<List<Utxo>> getUtxos(String address, int count, int page) throws ApiException {
        return getUtxos(address, count, page, OrderEnum.desc);
    }

    @Override
    public Result<List<Utxo>> getUtxos(String address, int count, int page, OrderEnum order) throws ApiException {
        try {
            if (page < 1) {
                return Result.success("OK").withValue(Collections.emptyList()).code(200);
            }
            rest.koios.client.backend.api.base.Result<AddressInfo> addressInformationResult = addressService.getAddressInformation(address);
            if (!addressInformationResult.isSuccessful()) {
                return Result.error(addressInformationResult.getResponse()).withValue(Collections.emptyList()).code(addressInformationResult.getCode());
            }
            List<AddressUtxo> addressUtxos = addressInformationResult.getValue().getUtxoSet().stream().sorted(Comparator.comparingInt(AddressUtxo::getBlockTime).thenComparingInt(AddressUtxo::getTxIndex)).collect(Collectors.toList());
            if (order == OrderEnum.desc) {
                Collections.reverse(addressUtxos);
            }
            return convertToUTxOs(addressInformationResult.getValue().getAddress(), getSubListByPage(addressUtxos, page, count));
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<Utxo>> getUtxos(String address, String unit, int count, int page) throws ApiException {
        return getUtxos(address, unit, count, page, OrderEnum.asc);
    }

    @Override
    public Result<List<Utxo>> getUtxos(String address, String unit, int count, int page, OrderEnum order) throws ApiException {
        Result<List<Utxo>> resultUtxos = getUtxos(address, count, page, order);
        if (!resultUtxos.isSuccessful())
            return resultUtxos;

        List<Utxo> utxos = resultUtxos.getValue();
        if (utxos == null || utxos.isEmpty())
            return resultUtxos;

        if (unit != null && !unit.isEmpty()) {
            utxos = utxos.stream().filter(utxo ->
                            utxo.getAmount().stream().anyMatch(amount -> amount.getUnit().equals(unit)))
                    .collect(Collectors.toList());
        }

        if (!utxos.isEmpty())
            return Result.success("OK").withValue(utxos).code(200);
        else
            return Result.error("Not Found").withValue(Collections.emptyList()).code(404);
    }

    @Override
    public Result<Utxo> getTxOutput(String txHash, int outputIndex) throws ApiException {
        return transactionService.getTransactionOutput(txHash, outputIndex);
    }

    private Result<List<Utxo>> convertToUTxOs(String address, List<AddressUtxo> utxos) {
        List<Utxo> utxoList = new ArrayList<>();
        for (AddressUtxo addressUtxo : utxos) {
            Utxo utxo = new Utxo();
            utxo.setAddress(address);
            utxo.setTxHash(addressUtxo.getTxHash());
            utxo.setOutputIndex(addressUtxo.getTxIndex());
            utxo.setDataHash(addressUtxo.getDatumHash());
            if (addressUtxo.getInlineDatum() != null) {
                utxo.setInlineDatum(addressUtxo.getInlineDatum().getBytes());
            }
            if (addressUtxo.getReferenceScript() != null) {
                utxo.setReferenceScriptHash(addressUtxo.getReferenceScript().getHash());
            }
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

    /**
     * Returns sublist of a page. If a page is empty and emptyList will be returned.
     * @param list
     * @param pageNumber
     * @param pageSize
     * @return
     */
    public static List<AddressUtxo> getSubListByPage(List<AddressUtxo> list, int pageNumber, int pageSize) {
        int start = 0;
        int end;

        if (pageNumber > 0) {
            start = pageSize * (pageNumber - 1);
        } else if(pageNumber <= 0) {
            return Collections.emptyList();
        }
        if (pageSize > 0) {
            end = start + pageSize;
        } else {
            end = start;
        }
        if (list.size() < end + 1) {
            end = list.size();
        }

        if(end < start) {
            return Collections.emptyList();
        } else {
            return list.subList(start, end);
        }
    }
}
