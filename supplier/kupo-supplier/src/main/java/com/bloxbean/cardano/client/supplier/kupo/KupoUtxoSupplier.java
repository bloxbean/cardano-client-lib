package com.bloxbean.cardano.client.supplier.kupo;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
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

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

@Slf4j
public class KupoUtxoSupplier extends KupoBaseService implements UtxoSupplier {
    public static final String DATUM_TYPE_INLINE = "inline";
    private final MatchesApi matchesApi;

    public KupoUtxoSupplier(String baseUrl) {
        super(baseUrl);
        this.matchesApi = getRetrofit().create(MatchesApi.class);
    }

    @Override
    public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
        try {
            Result result = getUtxos(address, page);
            if(result.isSuccessful())
                return (List<Utxo>) result.getValue();
            else
                return Collections.emptyList();

        } catch (ApiException e) {
            log.error("Error getting utxos for address: " + address, e);
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {
        Call<List<KupoUtxo>> utxosCall = matchesApi.getMatches(outputIndex + "@" + txHash);

        try {
            Response<List<KupoUtxo>> response = utxosCall.execute();
            if (response.isSuccessful()) {
                List<KupoUtxo> kupoUtxos = response.body();
                if (kupoUtxos != null && kupoUtxos.size() > 0) {
                    KupoUtxo kupoUtxo = kupoUtxos.get(0);
                    return Optional.of(convertToUtxo(kupoUtxo));

                } else {
                    return Optional.empty();
                }
            } else {
                return Optional.empty();
            }
        } catch (IOException e) {
            log.error("Error getting utxo for txHash: " + txHash + ", outputIndex: " + outputIndex, e);
        }
        return Optional.empty();
    }


    @Override
    public List<Utxo> getAll(String address) {
        try {
            Result result = getUtxos(address, 1);
            if(result.isSuccessful())
                return (List<Utxo>) result.getValue();
            else
                return Collections.emptyList();

        } catch (ApiException e) {
            log.error("Error getting utxos for address: " + address, e);
            return Collections.emptyList();
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
                return Result.error(response.errorBody() != null?response.errorBody().string(): null).code(response.code());
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
