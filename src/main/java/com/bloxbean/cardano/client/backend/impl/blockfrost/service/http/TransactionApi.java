package com.bloxbean.cardano.client.backend.impl.blockfrost.service.http;

import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface TransactionApi {
    @Headers("Content-Type: application/cbor")
    @POST("tx/submit")
    Call<String> submit(@Header("project_id") String projectId, @Body RequestBody signedTxn);

    @GET("txs/{hash}")
    Call<TransactionContent> getTransaction(@Header("project_id")  String projectId, @Path("hash") String txnHash);

    @GET("txs/{hash}/utxos")
    Call<TxContentUtxo> getTransactionUtxos(@Header("project_id")  String projectId, @Path("hash") String txnHash);
}
