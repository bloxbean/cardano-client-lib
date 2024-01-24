package com.bloxbean.cardano.client.backend.kupo;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.supplier.kupo.KupoUtxoSupplier;
import com.bloxbean.cardano.client.supplier.kupo.http.MatchesApi;
import com.bloxbean.cardano.client.supplier.kupo.model.KupoDatum;
import com.bloxbean.cardano.client.supplier.kupo.model.KupoUtxo;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

@Slf4j
public class KupoUtxoService implements UtxoService {

    private final KupoUtxoSupplier kupoUtxoSupplier;
    public KupoUtxoService(String kupoBaseUrl) {
        kupoUtxoSupplier = new KupoUtxoSupplier(kupoBaseUrl);
    }

    @Override
    public Result<List<Utxo>> getUtxos(String address, int count, int page) throws ApiException {
        List<Utxo> all = kupoUtxoSupplier.getPage(address, count, page, OrderEnum.asc);
        return Result.success("OK").withValue(all).code(200);
    }

    @Override
    public Result<List<Utxo>> getUtxos(String address, int count, int page, OrderEnum order) throws ApiException {
        return getUtxos(address, count, page);
    }

    @Override
    public Result<List<Utxo>> getUtxos(String address, String unit, int count, int page) throws ApiException {
        Result<List<Utxo>> resultUtxos = getUtxos(address, count, page);
        if (!resultUtxos.isSuccessful())
            return resultUtxos;

        List<Utxo> utxos = resultUtxos.getValue();
        if (utxos == null || utxos.isEmpty())
            return resultUtxos;

        List<Utxo> assetUtxos = utxos.stream().filter(utxo ->
                        utxo.getAmount().stream().filter(amount -> amount.getUnit().equals(unit)).findFirst().isPresent())
                .collect(Collectors.toList());

        if (!assetUtxos.isEmpty())
            return Result.success("OK").withValue(assetUtxos).code(200);
        else
            return Result.error("Not Found").withValue(Collections.emptyList()).code(404);
    }

    @Override
    public Result<List<Utxo>> getUtxos(String address, String unit, int count, int page, OrderEnum order) throws ApiException {
        return getUtxos(address, unit, count, page);
    }

    @Override
    public Result<Utxo> getTxOutput(String txHash, int outputIndex) throws ApiException {
        Optional<Utxo> txOutput = kupoUtxoSupplier.getTxOutput(txHash, outputIndex);
        if(txOutput.isPresent()) {
            return Result.success("OK").withValue(txOutput.get()).code(200);
        } else {
            return Result.error("Error getting utxo").withValue(null).code(500);
        }
    }




}
