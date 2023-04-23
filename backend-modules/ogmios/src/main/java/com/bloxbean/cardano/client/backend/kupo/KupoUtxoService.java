package com.bloxbean.cardano.client.backend.kupo;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.kupo.http.MatchesApi;
import com.bloxbean.cardano.client.backend.kupo.model.KupoDatum;
import com.bloxbean.cardano.client.backend.kupo.model.KupoUtxo;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

@Slf4j
public class KupoUtxoService extends KupoBaseService implements UtxoService {

    public static final String DATUM_TYPE_INLINE = "inline";
    private MatchesApi matchesApi;

    public KupoUtxoService(String kupoBaseUrl) {
        super(kupoBaseUrl);
        this.matchesApi = getRetrofit().create(MatchesApi.class);
    }

    @Override
    public Result<List<Utxo>> getUtxos(String address, int count, int page) throws ApiException {
        return getUtxos(address, page);
    }

    @Override
    public Result<List<Utxo>> getUtxos(String address, int count, int page, OrderEnum order) throws ApiException {
        return getUtxos(address, page);
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

        List<Utxo> assetUtxos = utxos.stream().filter(utxo ->
                        utxo.getAmount().stream().filter(amount -> amount.getUnit().equals(unit)).findFirst().isPresent())
                .collect(Collectors.toList());

        if (!assetUtxos.isEmpty())
            return Result.success("OK").withValue(assetUtxos).code(200);
        else
            return Result.error("Not Found").withValue(Collections.emptyList()).code(404);
    }

    @Override
    public Result<Utxo> getTxOutput(String txHash, int outputIndex) throws ApiException {
        Call<List<KupoUtxo>> utxosCall = matchesApi.getMatches(outputIndex + "@" + txHash);

        try {
            Response<List<KupoUtxo>> response = utxosCall.execute();
            if (response.isSuccessful()) {
                List<KupoUtxo> kupoUtxos = response.body();
                if (kupoUtxos != null && kupoUtxos.size() > 0) {
                    KupoUtxo kupoUtxo = kupoUtxos.get(0);
                    Utxo utxo = convertToUtxo(kupoUtxo);
                    return Result.success("OK").withValue(utxo).code(200);
                } else {
                    return Result.error("Not Found").withValue(null).code(404);
                }
            } else {
                return Result.error(response.errorBody().string()).code(response.code());
            }
        } catch (IOException e) {
            throw new ApiException(e.getMessage());
        }
    }

    private Result getUtxos(String address, int page) throws ApiException {
        if (page != 1)
            return Result.success("OK").withValue(Collections.emptyList()).code(200);

        Call<List<KupoUtxo>> utxosCall = matchesApi.getUnspentMatches(address);

        try {
            Response<List<KupoUtxo>> response = utxosCall.execute();
            if (response.isSuccessful()) {
                List<Utxo> utxos = new ArrayList<>();

                List<KupoUtxo> kupoUtxos = response.body();
                kupoUtxos.forEach(kupoUtxo -> {
                    Utxo utxo = convertToUtxo(kupoUtxo);
                    utxos.add(utxo);
                });
                return Result.success("OK").withValue(utxos).code(200);
            } else {
                return Result.error(response.errorBody().string()).code(response.code());
            }
        } catch (IOException e) {
            throw new ApiException("Error getting utxos", e);
        }
    }

    @NotNull
    private Utxo convertToUtxo(KupoUtxo kupoUtxo) {
        Utxo utxo = new Utxo();
        utxo.setTxHash(kupoUtxo.getTransactionId());
        utxo.setOutputIndex(kupoUtxo.getOutputIndex());
        utxo.setAddress(kupoUtxo.getAddress());
        utxo.setDataHash(kupoUtxo.getDatumHash());
        //If inline datum type, then get the actual dataum value
        if (kupoUtxo.getDatumHash() != null && kupoUtxo.getDatumType().equals(DATUM_TYPE_INLINE)) {
            utxo.setInlineDatum(getDatum(kupoUtxo.getDatumHash()));
        }
        utxo.setReferenceScriptHash(kupoUtxo.getScriptHash());
        List<Amount> amountList = new ArrayList<>();
        amountList.add(new Amount(LOVELACE, kupoUtxo.getValue().getCoins()));

        Map<String, BigInteger> assets = kupoUtxo.getValue().getAssets();
        assets.forEach((unit, value) -> {
            //replace . in kupo utxo
            unit = unit.replace(".", "");
            Amount amount = new Amount(unit, value);
            amountList.add(amount);
        });

        utxo.setAmount(amountList);
        return utxo;
    }

    private String getDatum(String datumHash) {
        try {
            Call<KupoDatum> datumCall = matchesApi.getDatum(datumHash);
            Response<KupoDatum> datumResponse = datumCall.execute();
            if (datumResponse.isSuccessful())
                return datumResponse.body().getDatum();
            else
                return null;
        } catch (Exception e) {
            log.error("Datum not found for datum hash: " + datumHash);
            log.error(e.getMessage(), e);
            return null;
        }
    }
}
