package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.util.HexUtil;
import jdk.jshell.execution.Util;
import org.assertj.core.api.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.List;

class TransactionBodyTest {
    final Account account = new Account(Networks.testnet(),
            "poem assume dial tomato garage prize protect garment mansion panel oppose artwork bounce van evoke hair poet ask remind employ document boil legend scale");
    final String address = "addr_test1vrw6vsvwwe9vwupyfkkeweh23ztd6n0vfydwk823esdz6pc4xqcd5";

    @Test
    void calcTxHash() throws Exception {
        BigInteger fee = BigInteger.valueOf(200000);
        BigInteger inputAmount = BigInteger.valueOf(99600000);
        var bodyBuilder = new TransactionBody.TransactionBodyBuilder()
                .ttl(66000000)
                .inputs(List.of(new TransactionInput.TransactionInputBuilder()
                        .transactionId("607452ed1beaafd69df00cb3667dd393cea655a58686442c2d74914066486a20")
                        .index(0)
                        .build()))
                .outputs(List.of(new TransactionOutput.TransactionOutputBuilder()
                        .address(address)
                        .value(Value.builder()
                                .coin(inputAmount.subtract(fee))
                                .build())
                        .build()))
                .fee(fee);
        Transaction tx = Transaction.builder()
                .body(bodyBuilder.build())
                .auxiliaryData(AuxiliaryData.builder()
                        .metadata(new CBORMetadata().put(BigInteger.valueOf(1924), "hello world"))
                        .build())
                .build();
        tx = account.sign(tx);
        System.out.println(tx.getBody().getHash());
        Assertions.assertTrue(tx.getBody().getHash().equals("ed274c6e445d3b173956d788bd33acfe1fcafa18d7a2b9c0d89c2ffd465888b6"));
        System.out.println(tx.serializeToHex());
    }



}
