package com.bloxbean.cardano.client.backend.kupo;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.kupo.http.MatchesApi;
import com.bloxbean.cardano.client.backend.kupo.model.KupoUtxo;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

public class KupoUtxoService extends KupoBaseService implements UtxoService {

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
                    Utxo utxo = new Utxo();
                    utxo.setTxHash(kupoUtxo.getTransactionId());
                    utxo.setOutputIndex(kupoUtxo.getOutputIndex());
                    utxo.setAddress(kupoUtxo.getAddress());
                    utxo.setDataHash(kupoUtxo.getDataHash());
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
}
