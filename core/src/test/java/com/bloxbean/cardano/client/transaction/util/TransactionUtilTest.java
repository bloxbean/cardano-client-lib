package com.bloxbean.cardano.client.transaction.util;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TransactionUtilTest {
    final Account account = new Account(Networks.testnet(),
            "poem assume dial tomato garage prize protect garment mansion panel oppose artwork bounce van evoke hair poet ask remind employ document boil legend scale");
    final String address = "addr_test1vrw6vsvwwe9vwupyfkkeweh23ztd6n0vfydwk823esdz6pc4xqcd5";

    /** This test will be enabled once we have tx builder level Era settings instead of current global Era settings */
    /**
    void calcTxHash_Alonzo() throws Exception {
        BigInteger fee = BigInteger.valueOf(200000);
        BigInteger inputAmount = BigInteger.valueOf(99200000);
        var bodyBuilder = TransactionBody.builder()
                .ttl(66000000)
                .inputs(List.of(TransactionInput.builder()
                        .transactionId("ac90bcc3d88536dea081603e7e7b65bba8eb68b78bc49ebf9a0ff3dbad9e55ac")
                        .index(0)
                        .build()))
                .outputs(List.of(TransactionOutput.builder()
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

        String txHash = TransactionUtil.getTxHash(tx);
        System.out.println(txHash);
        Assertions.assertTrue(txHash.equals("7b844a952d9d9bdcceabdf206ad24df1310460b7b4b421d6b05148b5a64283f2"));
        tx = account.sign(tx);
        System.out.println(HexUtil.encodeHexString(getRosettaBytes(tx.serializeToHex())));
    }
    **/

    @Test
    void calcTxHash() throws Exception {
        BigInteger fee = BigInteger.valueOf(200000);
        BigInteger inputAmount = BigInteger.valueOf(99200000);
        var bodyBuilder = TransactionBody.builder()
                .ttl(66000000)
                .inputs(List.of(TransactionInput.builder()
                        .transactionId("ac90bcc3d88536dea081603e7e7b65bba8eb68b78bc49ebf9a0ff3dbad9e55ac")
                        .index(0)
                        .build()))
                .outputs(List.of(TransactionOutput.builder()
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

        String txHash = TransactionUtil.getTxHash(tx);
        System.out.println(txHash);
        assertThat(txHash).isEqualTo("d3c45f0e07ec7ffa39ea90ec30c214c2c09344acc60d366d3094629e9954eacc");
        tx = account.sign(tx);
        System.out.println(HexUtil.encodeHexString(getRosettaBytes(tx.serializeToHex())));
    }

    public static byte[] getRosettaBytes(String signedTxn) throws CborException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new CborBuilder()
                .addArray()
                .add(signedTxn)
                .addMap()
                .addKey("operation")
                .value("")
                .end()
                .end()
                .build());
        return baos.toByteArray();
    }

}
