package com.bloxbean.cardano.client.backend.helper;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.request.PaymentTransaction;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.TransactionSerializationException;
import com.bloxbean.cardano.client.transaction.model.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;

public class TransactionHelperService {

    private UtxoService utxoService;
    private TransactionService transactionService;

    public TransactionHelperService(UtxoService utxoService, TransactionService transactionService) {
        this.utxoService = utxoService;
        this.transactionService = transactionService;
    }

    /**
     * Transfer fund
     * @param paymentTransaction
     * @param detailsParams
     * @return
     * @throws ApiException
     * @throws AddressExcepion
     * @throws TransactionSerializationException
     * @throws CborException
     */
    public Result transfer(PaymentTransaction paymentTransaction, TransactionDetailsParams detailsParams)
            throws ApiException, AddressExcepion, TransactionSerializationException, CborException {
        UtxoTransactionBuilder utxoTransactionBuilder = new UtxoTransactionBuilder(this.utxoService, this.transactionService);

        Transaction transaction = utxoTransactionBuilder.buildTransaction(Arrays.asList(paymentTransaction), detailsParams);

        System.out.println(JsonUtil.getPrettyJson(transaction));

        String signedTxn = paymentTransaction.getSender().sign(transaction);
        byte[] signedTxnBytes = HexUtil.decodeHexString(signedTxn);

        try {
            File outputFile = new File("/Users/satya/tx1.raw");
            try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                outputStream.write(signedTxnBytes);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Result<String> result = transactionService.submitTransaction(signedTxnBytes);

        return result;
    }
}
