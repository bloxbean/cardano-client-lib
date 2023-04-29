package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedeemerUtilTest {

    @Test
    void getScriptInputFromRedeemer() {
        List<TransactionInput> inputs = new ArrayList<>();
        inputs.add(new TransactionInput("3c3dbdb4730a1fe656baec6e54d1d4171b290135b560a39435bc3015fba11800", 1));
        inputs.add(new TransactionInput("d0adf1a04a9d88f0f2460bd70b5713f5208f46dcc591fd484c271adea4d0be1e", 4));
        inputs.add(new TransactionInput("d0adf1a04a9d88f0f2460bd70b5713f5208f46dcc591fd484c271adea4d0be1e", 2));
        inputs.add(new TransactionInput("d0adf1a04a9d88f0f2460bd70b5713f5208f46dcc591fd484c271adea4d0be1e", 1));
        inputs.add(new TransactionInput("f00da3e2a83552ba42ad74b41ab2250ddaaf4aedc1b6a0d2b5d9cf01dd9e06a7", 6));
        inputs.add(new TransactionInput("d0adf1a04a9d88f0f2460bd70b5713f5208f46dcc591fd484c271adea4d0be1e", 7));
        inputs.add(new TransactionInput("f00da3e2a83552ba42ad74b41ab2250ddaaf4aedc1b6a0d2b5d9cf01dd9e06a7", 11));
        inputs.add(new TransactionInput("f00da3e2a83552ba42ad74b41ab2250ddaaf4aedc1b6a0d2b5d9cf01dd9e06a7", 2));

        Transaction transaction = Transaction
                .builder()
                .body(TransactionBody.builder()
                        .inputs(inputs).build()
                ).build();

        Redeemer redeemer = Redeemer.builder()
                .index(BigInteger.valueOf(7)).build();

        TransactionInput input = RedeemerUtil.getScriptInputFromRedeemer(redeemer, transaction);

        assertThat(input.getTransactionId()).isEqualTo("f00da3e2a83552ba42ad74b41ab2250ddaaf4aedc1b6a0d2b5d9cf01dd9e06a7");
        assertThat(input.getIndex()).isEqualTo(11);
    }

    @Test
    void getScriptInputIndex_utxo() {
        List<TransactionInput> inputs = new ArrayList<>();
        inputs.add(new TransactionInput("3c3dbdb4730a1fe656baec6e54d1d4171b290135b560a39435bc3015fba11800", 1));
        inputs.add(new TransactionInput("d0adf1a04a9d88f0f2460bd70b5713f5208f46dcc591fd484c271adea4d0be1e", 4));
        inputs.add(new TransactionInput("d0adf1a04a9d88f0f2460bd70b5713f5208f46dcc591fd484c271adea4d0be1e", 2));
        inputs.add(new TransactionInput("d0adf1a04a9d88f0f2460bd70b5713f5208f46dcc591fd484c271adea4d0be1e", 1));
        inputs.add(new TransactionInput("f00da3e2a83552ba42ad74b41ab2250ddaaf4aedc1b6a0d2b5d9cf01dd9e06a7", 6));
        inputs.add(new TransactionInput("d0adf1a04a9d88f0f2460bd70b5713f5208f46dcc591fd484c271adea4d0be1e", 7));
        inputs.add(new TransactionInput("f00da3e2a83552ba42ad74b41ab2250ddaaf4aedc1b6a0d2b5d9cf01dd9e06a7", 11));
        inputs.add(new TransactionInput("f00da3e2a83552ba42ad74b41ab2250ddaaf4aedc1b6a0d2b5d9cf01dd9e06a7", 2));

        Transaction transaction = Transaction
                .builder()
                .body(TransactionBody.builder()
                        .inputs(inputs).build()
                ).build();

        Utxo utxo = Utxo.builder()
                        .txHash("f00da3e2a83552ba42ad74b41ab2250ddaaf4aedc1b6a0d2b5d9cf01dd9e06a7")
                                .outputIndex(6).build();
        int index1 = RedeemerUtil.getScriptInputIndex(utxo, transaction);

        utxo = Utxo.builder()
                .txHash("f00da3e2a83552ba42ad74b41ab2250ddaaf4aedc1b6a0d2b5d9cf01dd9e06a7")
                .outputIndex(11).build();
        int index2 = RedeemerUtil.getScriptInputIndex(utxo, transaction);

        utxo = Utxo.builder()
                .txHash("3c3dbdb4730a1fe656baec6e54d1d4171b290135b560a39435bc3015fba11800")
                .outputIndex(1).build();
        int index3 = RedeemerUtil.getScriptInputIndex(utxo, transaction);

        utxo = Utxo.builder()
                .txHash("d0adf1a04a9d88f0f2460bd70b5713f5208f46dcc591fd484c271adea4d0be1e")
                .outputIndex(4).build();
        int index4 = RedeemerUtil.getScriptInputIndex(utxo, transaction);

        assertThat(index1).isEqualTo(6);
        assertThat(index2).isEqualTo(7);
        assertThat(index3).isEqualTo(0);
        assertThat(index4).isEqualTo(3);
    }

    @Test
    void getScriptInputIndex_txInput() {
        List<TransactionInput> inputs = new ArrayList<>();
        inputs.add(new TransactionInput("3c3dbdb4730a1fe656baec6e54d1d4171b290135b560a39435bc3015fba11800", 1));
        inputs.add(new TransactionInput("d0adf1a04a9d88f0f2460bd70b5713f5208f46dcc591fd484c271adea4d0be1e", 4));
        inputs.add(new TransactionInput("d0adf1a04a9d88f0f2460bd70b5713f5208f46dcc591fd484c271adea4d0be1e", 2));
        inputs.add(new TransactionInput("d0adf1a04a9d88f0f2460bd70b5713f5208f46dcc591fd484c271adea4d0be1e", 1));
        inputs.add(new TransactionInput("f00da3e2a83552ba42ad74b41ab2250ddaaf4aedc1b6a0d2b5d9cf01dd9e06a7", 6));
        inputs.add(new TransactionInput("d0adf1a04a9d88f0f2460bd70b5713f5208f46dcc591fd484c271adea4d0be1e", 7));
        inputs.add(new TransactionInput("f00da3e2a83552ba42ad74b41ab2250ddaaf4aedc1b6a0d2b5d9cf01dd9e06a7", 11));
        inputs.add(new TransactionInput("f00da3e2a83552ba42ad74b41ab2250ddaaf4aedc1b6a0d2b5d9cf01dd9e06a7", 2));

        Transaction transaction = Transaction
                .builder()
                .body(TransactionBody.builder()
                        .inputs(inputs).build()
                ).build();

        TransactionInput input = TransactionInput.builder()
                .transactionId("f00da3e2a83552ba42ad74b41ab2250ddaaf4aedc1b6a0d2b5d9cf01dd9e06a7")
                .index(6).build();
        int index1 = RedeemerUtil.getScriptInputIndex(input, transaction);

        input = TransactionInput.builder()
                .transactionId("f00da3e2a83552ba42ad74b41ab2250ddaaf4aedc1b6a0d2b5d9cf01dd9e06a7")
                .index(11).build();
        int index2 = RedeemerUtil.getScriptInputIndex(input, transaction);

        input = TransactionInput.builder()
                .transactionId("3c3dbdb4730a1fe656baec6e54d1d4171b290135b560a39435bc3015fba11800")
                .index(1).build();
        int index3 = RedeemerUtil.getScriptInputIndex(input, transaction);

        input = TransactionInput.builder()
                .transactionId("d0adf1a04a9d88f0f2460bd70b5713f5208f46dcc591fd484c271adea4d0be1e")
                .index(4).build();
        int index4 = RedeemerUtil.getScriptInputIndex(input, transaction);

        assertThat(index1).isEqualTo(6);
        assertThat(index2).isEqualTo(7);
        assertThat(index3).isEqualTo(0);
        assertThat(index4).isEqualTo(3);
    }
}
