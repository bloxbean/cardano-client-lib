package com.bloxbean.cardano.client.backend.api.helper;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.api.BlockService;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.impl.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.impl.blockfrost.service.BFBaseTest;
import com.bloxbean.cardano.client.backend.impl.blockfrost.service.BFBlockService;
import com.bloxbean.cardano.client.backend.impl.blockfrost.service.BFTransactionService;
import com.bloxbean.cardano.client.backend.impl.blockfrost.service.BFUtxoService;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.bloxbean.cardano.client.metadata.helper.JsonNoSchemaToMetadataConverter;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.model.MintTransaction;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.*;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.TransactionSerializationException;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.script.*;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.bloxbean.cardano.client.util.Tuple;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class TransactionHelperServiceIT extends BFBaseTest {
    UtxoService utxoService;
    TransactionService transactionService;
    TransactionHelperService transactionHelperService;
    BlockService blockService;

    String dataFile = "json-metadata.json";

    @BeforeEach
    public void setup() {
        utxoService = new BFUtxoService(Constants.BLOCKFROST_TESTNET_URL, projectId);
        transactionService = new BFTransactionService(Constants.BLOCKFROST_TESTNET_URL, projectId);
        transactionHelperService = new TransactionHelperService(utxoService, transactionService);
        blockService = new BFBlockService(Constants.BLOCKFROST_TESTNET_URL, projectId);
    }

    @Test
    void transfer() throws TransactionSerializationException, CborException, AddressExcepion, ApiException {

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        PaymentTransaction paymentTransaction =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .amount(BigInteger.valueOf(1500000))
                        .fee(BigInteger.valueOf(230000))
                        .unit("lovelace")
                        .build();

        Result<String> result = transactionHelperService.transfer(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build());

        if(result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        waitForTransaction(result);

        assertThat(result.isSuccessful(), is(true));
    }

    @Test
    void transferMultiAsset() throws TransactionSerializationException, CborException, AddressExcepion, ApiException {
        //Sender address : addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y
        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        PaymentTransaction paymentTransaction =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .amount(BigInteger.valueOf(12))
                        .fee(BigInteger.valueOf(210000))
                        .unit("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e")
                        .build();

        Result<String> result = transactionHelperService.transfer(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build());

        if(result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        System.out.println(result);
        waitForTransaction(result);
        assertThat(result.isSuccessful(), is(true));
    }

    @Test
    void transferMultiAssetMultiPayments() throws TransactionSerializationException, CborException, AddressExcepion, ApiException {
        //Sender address : addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y
        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);

        //addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82
        String senderMnemonic2 = "mixture peasant wood unhappy usage hero great elder emotion picnic talent fantasy program clean patch wheel drip disorder bullet cushion bulk infant balance address";
        Account sender2 = new Account(Networks.testnet(), senderMnemonic2);

        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        String receiver2 = "addr_test1qz7r5eu2jg0hx470mmf79vpgueaggh22pmayry8xrre5grtpyy9s8u2heru58a4r68wysmdw9v40zznttmwrg0a6v9tq36pjak";

        String receiver3 = "addr_test1qrp6x6aq2m28xhvxhqzufl0ff7x8gmzjejssrk29mx0q829dsty3hzmrl2k8jhwzghgxuzfjatgxlhg9wtl6ecv0v3cqf92rnh";

        PaymentTransaction paymentTransaction1 =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .amount(BigInteger.valueOf(14))
                        .fee(BigInteger.valueOf(210000))
                        .unit("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e")
                        .build();

        PaymentTransaction paymentTransaction2 =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver2)
                        .amount(BigInteger.valueOf(33))
                        .fee(BigInteger.valueOf(210000))
                        .unit("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e")
                        .build();

        PaymentTransaction paymentTransaction3 =
                PaymentTransaction.builder()
                        .sender(sender2)
                        .receiver(receiver3)
                        .amount(BigInteger.valueOf(3110000))
                        .fee(BigInteger.valueOf(210000))
                        .unit(LOVELACE)
                        .build();

        Result<String> result = transactionHelperService.transfer(Arrays.asList(paymentTransaction1, paymentTransaction2, paymentTransaction3),
                TransactionDetailsParams.builder().ttl(getTtl()).build());

        if(result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        System.out.println(result);
        waitForTransaction(result);
        assertThat(result.isSuccessful(), is(true));
    }

    @Test
    void mintToken() throws TransactionSerializationException, CborException, AddressExcepion, ApiException {

        Keys keys = KeyGenUtil.generateKey();
        VerificationKey vkey = keys.getVkey();
        SecretKey skey = keys.getSkey();

        ScriptPubkey scriptPubkey = ScriptPubkey.create(vkey);
        String policyId = scriptPubkey.getPolicyId();

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policyId);
        Asset asset = new Asset("mycoin", BigInteger.valueOf(250000));
        multiAsset.getAssets().add(asset);

        MintTransaction paymentTransaction =
                MintTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .fee(BigInteger.valueOf(230000))
                        .mintAssets(Arrays.asList(multiAsset))
                        .policyScript(scriptPubkey)
                        .policyKeys(Arrays.asList(skey))
                        .build();

        Result<String> result = transactionHelperService.mintToken(paymentTransaction,
                TransactionDetailsParams.builder().ttl(getTtl()).build());

        System.out.println("Request: \n" + JsonUtil.getPrettyJson(paymentTransaction));
        if(result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        waitForTransaction(result);

        assertThat(result.isSuccessful(), is(true));
    }

    @Test
    void mintTokenWithScriptAtLeast() throws TransactionSerializationException, CborException, AddressExcepion, ApiException {
       Tuple<ScriptPubkey, Keys> tuple1 = ScriptPubkey.createWithNewKey();
       ScriptPubkey scriptPubkey1 = tuple1._1;
       SecretKey sk1 = tuple1._2.getSkey();

        Tuple<ScriptPubkey, Keys> tuple2 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey2 = tuple2._1;
        SecretKey sk2 = tuple2._2.getSkey();

        Tuple<ScriptPubkey, Keys> tuple3 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey3 = tuple3._1;
        SecretKey sk3 = tuple3._2.getSkey();

        ScriptAtLeast scriptAtLeast = new ScriptAtLeast(2)
                .addScript(scriptPubkey1)
                .addScript(scriptPubkey2)
                .addScript(scriptPubkey3);

        String policyId = scriptAtLeast.getPolicyId();

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policyId);
        Asset asset = new Asset(HexUtil.encodeHexString("selftoken1".getBytes(StandardCharsets.UTF_8)), BigInteger.valueOf(250000));
        multiAsset.getAssets().add(asset);

        MintTransaction paymentTransaction =
                MintTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .fee(BigInteger.valueOf(230000))
                        .mintAssets(Arrays.asList(multiAsset))
                        .policyScript(scriptAtLeast)
                        .policyKeys(Arrays.asList(sk2, sk3))
                        .build();

        Result<String> result = transactionHelperService.mintToken(paymentTransaction,
                TransactionDetailsParams.builder().ttl(getTtl()).build());

        System.out.println("Request: \n" + JsonUtil.getPrettyJson(paymentTransaction));
        System.out.println(result);
        if(result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        assertThat(result.isSuccessful(), is(true));
        waitForTransaction(result);
    }

    @Test
    void mintTokenWithScriptAtLeastButNotSufficientKeys() throws TransactionSerializationException, CborException, AddressExcepion, ApiException {
        Tuple<ScriptPubkey, Keys> tuple1 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey1 = tuple1._1;
        SecretKey sk1 = tuple1._2.getSkey();

        Tuple<ScriptPubkey, Keys> tuple2 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey2 = tuple2._1;
        SecretKey sk2 = tuple2._2.getSkey();

        Tuple<ScriptPubkey, Keys> tuple3 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey3 = tuple3._1;
        SecretKey sk3 = tuple3._2.getSkey();

        ScriptAtLeast scriptAtLeast = new ScriptAtLeast(2)
                .addScript(scriptPubkey1)
                .addScript(scriptPubkey2)
                .addScript(scriptPubkey3);

        String policyId = scriptAtLeast.getPolicyId();

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policyId);
        Asset asset = new Asset(HexUtil.encodeHexString("selftoken1".getBytes(StandardCharsets.UTF_8)), BigInteger.valueOf(250000));
        multiAsset.getAssets().add(asset);

        MintTransaction mintTransaction =
                MintTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .fee(BigInteger.valueOf(230000))
                        .mintAssets(Arrays.asList(multiAsset))
                        .policyScript(scriptAtLeast)
                        .policyKeys(Arrays.asList(sk2))
                        .build();

        System.out.println(JsonUtil.getPrettyJson(mintTransaction));

        Result<String> result = transactionHelperService.mintToken(mintTransaction,
                TransactionDetailsParams.builder().ttl(getTtl()).build());

        System.out.println("Request: \n" + JsonUtil.getPrettyJson(mintTransaction));
        if(result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        assertThat(result.isSuccessful(), is(false));
        waitForTransaction(result);
    }

    @Test
    void mintTokenWithScriptAll() throws TransactionSerializationException, CborException, AddressExcepion, ApiException {
        Tuple<ScriptPubkey, Keys> tuple1 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey1 = tuple1._1;
        SecretKey sk1 = tuple1._2.getSkey();

        Tuple<ScriptPubkey, Keys> tuple2 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey2 = tuple2._1;
        SecretKey sk2 = tuple2._2.getSkey();

        Tuple<ScriptPubkey, Keys> tuple3 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey3 = tuple3._1;
        SecretKey sk3 = tuple3._2.getSkey();

        ScriptAll scriptAll = new ScriptAll()
                .addScript(scriptPubkey1)
                .addScript(scriptPubkey2)
                .addScript(scriptPubkey3);

        String policyId = scriptAll.getPolicyId();

        System.out.println(">> Policy Id: " + policyId);

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policyId);
        Asset asset = new Asset(HexUtil.encodeHexString("selftoken1".getBytes(StandardCharsets.UTF_8)), BigInteger.valueOf(250000));
        multiAsset.getAssets().add(asset);

        MintTransaction mintTransaction =
                MintTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .fee(BigInteger.valueOf(230000))
                        .mintAssets(Arrays.asList(multiAsset))
                        .policyScript(scriptAll)
                        .policyKeys(Arrays.asList(sk1, sk2, sk3))
                        .build();

        System.out.println(JsonUtil.getPrettyJson(mintTransaction));

        Result<String> result = transactionHelperService.mintToken(mintTransaction,
                TransactionDetailsParams.builder().ttl(getTtl()).build());

        System.out.println("Request: \n" + JsonUtil.getPrettyJson(mintTransaction));
        System.out.println(result);
        if(result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        assertThat(result.isSuccessful(), is(true));
        waitForTransaction(result);
    }

    @Test
    void mintTokenWithScriptAllButNotSufficientKeys() throws TransactionSerializationException, CborException, AddressExcepion, ApiException {
        Tuple<ScriptPubkey, Keys> tuple1 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey1 = tuple1._1;
        SecretKey sk1 = tuple1._2.getSkey();

        Tuple<ScriptPubkey, Keys> tuple2 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey2 = tuple2._1;
        SecretKey sk2 = tuple2._2.getSkey();

        Tuple<ScriptPubkey, Keys> tuple3 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey3 = tuple3._1;
        SecretKey sk3 = tuple3._2.getSkey();

        ScriptAll scriptAll = new ScriptAll()
                .addScript(scriptPubkey1)
                .addScript(scriptPubkey2)
                .addScript(scriptPubkey3);

        String policyId = scriptAll.getPolicyId();

        System.out.println(">> Policy Id: " + policyId);

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policyId);
        Asset asset = new Asset(HexUtil.encodeHexString("selftoken1".getBytes(StandardCharsets.UTF_8)), BigInteger.valueOf(250000));
        multiAsset.getAssets().add(asset);

        MintTransaction mintTransaction =
                MintTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .fee(BigInteger.valueOf(230000))
                        .mintAssets(Arrays.asList(multiAsset))
                        .policyScript(scriptAll)
                        .policyKeys(Arrays.asList(sk2, sk3))
                        .build();

        System.out.println(JsonUtil.getPrettyJson(mintTransaction));

        Result<String> result = transactionHelperService.mintToken(mintTransaction,
                TransactionDetailsParams.builder().ttl(getTtl()).build());

        System.out.println("Request: \n" + JsonUtil.getPrettyJson(mintTransaction));
        if(result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        assertThat(result.isSuccessful(), is(false));
        waitForTransaction(result);
    }


    @Test
    void mintTokenWithScriptAtLeastBefore() throws TransactionSerializationException, CborException, AddressExcepion, ApiException {
        Tuple<ScriptPubkey, Keys> tuple1 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey1 = tuple1._1;
        SecretKey sk1 = tuple1._2.getSkey();

        Tuple<ScriptPubkey, Keys> tuple2 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey2 = tuple2._1;
        SecretKey sk2 = tuple2._2.getSkey();

        Tuple<ScriptPubkey, Keys> tuple3 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey3 = tuple3._1;
        SecretKey sk3 = tuple3._2.getSkey();

        long slot = getTtl();

        RequireTimeBefore requireTimeBefore = new RequireTimeBefore((int)slot);

        ScriptAtLeast scriptAtLeast = new ScriptAtLeast(2)
                .addScript(requireTimeBefore)
                .addScript(scriptPubkey1)
                .addScript(scriptPubkey2)
                .addScript(scriptPubkey3);

        String policyId = scriptAtLeast.getPolicyId();

        System.out.println(">> Policy Id: " + policyId);

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policyId);
        Asset asset = new Asset(HexUtil.encodeHexString("selftoken1".getBytes(StandardCharsets.UTF_8)), BigInteger.valueOf(250000));
        multiAsset.getAssets().add(asset);

        MintTransaction mintTransaction =
                MintTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .fee(BigInteger.valueOf(230000))
                        .mintAssets(Arrays.asList(multiAsset))
                        .policyScript(scriptAtLeast)
                        .policyKeys(Arrays.asList(sk2, sk3))
                        .build();

        System.out.println(JsonUtil.getPrettyJson(mintTransaction));

        Result<String> result = transactionHelperService.mintToken(mintTransaction,
                TransactionDetailsParams.builder().ttl(getTtl()).build());

        System.out.println("Request: \n" + JsonUtil.getPrettyJson(mintTransaction));
        if(result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        assertThat(result.isSuccessful(), is(true));
        waitForTransaction(result);
    }

    @Test
    void mintTokenWithScriptPubBeforeValidSlot() throws TransactionSerializationException, CborException, AddressExcepion, ApiException {
        Tuple<ScriptPubkey, Keys> tuple1 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey1 = tuple1._1;
        SecretKey sk1 = tuple1._2.getSkey();

        Tuple<ScriptPubkey, Keys> tuple2 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey2 = tuple2._1;
        SecretKey sk2 = tuple2._2.getSkey();

        Tuple<ScriptPubkey, Keys> tuple3 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey3 = tuple3._1;
        SecretKey sk3 = tuple3._2.getSkey();

        ScriptAtLeast scriptAtLeast = new ScriptAtLeast(2)
                .addScript(scriptPubkey1)
                .addScript(scriptPubkey2)
                .addScript(scriptPubkey3);

        long slot = getTtl();
        RequireTimeBefore requireTimeBefore = new RequireTimeBefore(slot);

        ScriptAll scriptAll = new ScriptAll()
                .addScript(requireTimeBefore)
                .addScript(
                        scriptAtLeast
                );

        System.out.println(JsonUtil.getPrettyJson(scriptAll));

        String policyId = scriptAll.getPolicyId();

        System.out.println(">> Policy Id: " + policyId);

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policyId);
        Asset asset = new Asset(HexUtil.encodeHexString("selftoken1".getBytes(StandardCharsets.UTF_8)), BigInteger.valueOf(250000));
        multiAsset.getAssets().add(asset);

        MintTransaction mintTransaction =
                MintTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .fee(BigInteger.valueOf(230000))
                        .mintAssets(Arrays.asList(multiAsset))
                        .policyScript(scriptAll)
                        .policyKeys(Arrays.asList(sk1, sk2))
                        .build();

        System.out.println(JsonUtil.getPrettyJson(mintTransaction));

        Result<String> result = transactionHelperService.mintToken(mintTransaction,
                TransactionDetailsParams.builder().ttl(getTtl()).build());

        System.out.println("Request: \n" + JsonUtil.getPrettyJson(mintTransaction));
        System.out.println(result);
        if(result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        assertThat(result.isSuccessful(), is(true));
        waitForTransaction(result);
    }

    @Test
    void mintTokenWithScriptPubBeforeInValidSlot() throws TransactionSerializationException, CborException, AddressExcepion, ApiException {
        Tuple<ScriptPubkey, Keys> tuple = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey = tuple._1;
        SecretKey sk = tuple._2.getSkey();

        long slot = getTtl() - 5000;
        RequireTimeBefore requireTimeBefore = new RequireTimeBefore(slot);
        ScriptAll scriptAll = new ScriptAll()
                .addScript(requireTimeBefore)
                .addScript(scriptPubkey);

        System.out.println(JsonUtil.getPrettyJson(scriptAll));
        String policyId = scriptAll.getPolicyId();

        System.out.println(">> Policy Id: " + policyId);

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policyId);
        Asset asset = new Asset(HexUtil.encodeHexString("selftoken1".getBytes(StandardCharsets.UTF_8)), BigInteger.valueOf(250000));
        multiAsset.getAssets().add(asset);

        MintTransaction mintTransaction =
                MintTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .fee(BigInteger.valueOf(230000))
                        .mintAssets(Arrays.asList(multiAsset))
                        .policyScript(scriptAll)
                        .policyKeys(Arrays.asList(sk))
                        .build();

        System.out.println(JsonUtil.getPrettyJson(mintTransaction));

        Result<String> result = transactionHelperService.mintToken(mintTransaction,
                TransactionDetailsParams.builder().ttl(getTtl()).build());

        System.out.println("Request:- \n" + JsonUtil.getPrettyJson(mintTransaction));
        System.out.println(result);
        if(result.isSuccessful())
            System.out.println("Transaction Id:- " + result.getValue());
        else
            System.out.println("Transaction failed:- " + result);

        assertThat(result.isSuccessful(), is(false));
        waitForTransaction(result);
    }

    @Test
    void mintTokenWithScriptPubAfterValidSlot() throws TransactionSerializationException, CborException, AddressExcepion, ApiException {
        Tuple<ScriptPubkey, Keys> tuple = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey = tuple._1;
        SecretKey sk = tuple._2.getSkey();

        long afterSlot = getTtl() - 4000; //As getTtl() is currentslot + 2000
        RequireTimeAfter requireTimeAfter = new RequireTimeAfter(afterSlot);
        ScriptAll policyScript = new ScriptAll()
                .addScript(requireTimeAfter)
                .addScript(scriptPubkey);

        System.out.println(JsonUtil.getPrettyJson(policyScript));
        String policyId = policyScript.getPolicyId();

        System.out.println(">> Policy Id: " + policyId);

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policyId);
        Asset asset = new Asset("selftoken100", BigInteger.valueOf(250000));
        multiAsset.getAssets().add(asset);

        MintTransaction mintTransaction =
                MintTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .fee(BigInteger.valueOf(230000))
                        .mintAssets(Arrays.asList(multiAsset))
                        .policyScript(policyScript)
                        .policyKeys(Arrays.asList(sk))
                        .build();

        System.out.println(JsonUtil.getPrettyJson(mintTransaction));

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(getTtl())
                .validityStartInterval(afterSlot + 100) //validity start should be after slot or later
                .build();

        Result<String> result = transactionHelperService.mintToken(mintTransaction, detailsParams);

        System.out.println("Request:- \n" + JsonUtil.getPrettyJson(mintTransaction));
        System.out.println(result);
        if(result.isSuccessful())
            System.out.println("Transaction Id:- " + result.getValue());
        else
            System.out.println("Transaction failed:- " + result);

        assertThat(result.isSuccessful(), is(true));
        waitForTransaction(result);
    }

    @Test
    void mintTokenWithScriptPubAfterInValidSlot() throws TransactionSerializationException, CborException, AddressExcepion, ApiException {
        Tuple<ScriptPubkey, Keys> tuple = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey = tuple._1;
        SecretKey sk = tuple._2.getSkey();

        long afterSlot = getTtl() + 4000; //As getTtl() is currentslot + 2000
        RequireTimeAfter requireTimeAfter = new RequireTimeAfter(afterSlot);
        ScriptAll policyScript = new ScriptAll()
                .addScript(requireTimeAfter)
                .addScript(scriptPubkey);

        System.out.println(JsonUtil.getPrettyJson(policyScript));
        String policyId = policyScript.getPolicyId();

        System.out.println(">> Policy Id: " + policyId);

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policyId);
        Asset asset = new Asset("selftoken100", BigInteger.valueOf(250000));
        multiAsset.getAssets().add(asset);

        MintTransaction mintTransaction =
                MintTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .fee(BigInteger.valueOf(230000))
                        .mintAssets(Arrays.asList(multiAsset))
                        .policyScript(policyScript)
                        .policyKeys(Arrays.asList(sk))
                        .build();

        System.out.println(JsonUtil.getPrettyJson(mintTransaction));

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(getTtl())
                .validityStartInterval(afterSlot + 100) //validity start should be after slot or later
                .build();

        Result<String> result = transactionHelperService.mintToken(mintTransaction, detailsParams);

        System.out.println("Request:- \n" + JsonUtil.getPrettyJson(mintTransaction));
        System.out.println(result);
        if(result.isSuccessful())
            System.out.println("Transaction Id:- " + result.getValue());
        else
            System.out.println("Transaction failed:- " + result);

        assertThat(result.isSuccessful(), is(false));
        waitForTransaction(result);
    }

    @Test
    void mintTokenWithMetadata() throws TransactionSerializationException,
            CborException, AddressExcepion, ApiException {

        Keys keys = KeyGenUtil.generateKey();
        VerificationKey vkey = keys.getVkey();
        SecretKey skey = keys.getSkey();

        ScriptPubkey scriptPubkey = ScriptPubkey.create(vkey);
        String policyId = scriptPubkey.getPolicyId();

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policyId);
        Asset asset = new Asset(HexUtil.encodeHexString("Testtoken123".getBytes(StandardCharsets.UTF_8)), BigInteger.valueOf(150000));
        multiAsset.getAssets().add(asset);

        MintTransaction paymentTransaction =
                MintTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .fee(BigInteger.valueOf(230000))
                        .mintAssets(Arrays.asList(multiAsset))
                        .policyScript(scriptPubkey)
                        .policyKeys(Arrays.asList(skey))
                        .build();

        Metadata metadata = new CBORMetadata()
                .put(new BigInteger("1"), "SAT Token")
                .put(new BigInteger("2"), "SAT")
                .put(new BigInteger("3"), "This is a test token");


        Result<String> result = transactionHelperService.mintToken(paymentTransaction,
                TransactionDetailsParams.builder().ttl(getTtl()).build(), metadata);

        System.out.println("Request: \n" + JsonUtil.getPrettyJson(paymentTransaction));
        if(result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        waitForTransaction(result);

        assertThat(result.isSuccessful(), is(true));
    }

    @Test
    void transferWithCBORMetadata() throws TransactionSerializationException, CborException, AddressExcepion, ApiException {

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        CBORMetadataMap mm = new CBORMetadataMap()
                .put(new BigInteger("1978"), "201value")
                .put(new BigInteger("197819"),new BigInteger("200001"))
                .put("203", new byte[] { 11,11,10});

        CBORMetadataList list = new CBORMetadataList()
                .add("301value")
                .add(new BigInteger("300001"))
                .add(new byte[] { 11,11,10})
                .add(new CBORMetadataMap()
                        .put(new BigInteger("401"), "401str")
                        .put("hello", "hellovalue"));
        Metadata metadata = new CBORMetadata()
                .put(new BigInteger("197819781978"), "John")
                .put(new BigInteger("197819781979"), "CA")
                .put(new BigInteger("1978197819710"), new byte[]{0,11})
                .put(new BigInteger("1978197819711"), mm)
                .put(new BigInteger("1978197819712"), list);

        System.out.println(HexUtil.encodeHexString(metadata.serialize()));

        PaymentTransaction paymentTransaction =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .amount(BigInteger.valueOf(1500000))
                        .fee(BigInteger.valueOf(230000))
                        .unit("lovelace")
                        .build();

        Result<String> result = transactionHelperService.transfer(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), metadata);

        if(result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        waitForTransaction(result);

        assertThat(result.isSuccessful(), is(true));
    }

    @Test
    void transferWithJSONMetadata() throws TransactionSerializationException, CborException, AddressExcepion, ApiException, IOException {

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        JsonNode json = loadJsonMetadata("json-1");
        Metadata metadata = JsonNoSchemaToMetadataConverter.jsonToCborMetadata(json.toString());

        PaymentTransaction paymentTransaction =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .amount(BigInteger.valueOf(1500000))
                        .fee(BigInteger.valueOf(230000))
                        .unit("lovelace")
                        .build();

        Result<String> result = transactionHelperService.transfer(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), metadata);

        if(result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        waitForTransaction(result);

        assertThat(result.isSuccessful(), is(true));
    }

    @Test
    void transferWithJSONMetadataComplex() throws TransactionSerializationException, CborException, AddressExcepion, ApiException, IOException {

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        JsonNode json = loadJsonMetadata("json-3");
        Metadata metadata = JsonNoSchemaToMetadataConverter.jsonToCborMetadata(json.toString());

        PaymentTransaction paymentTransaction =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .amount(BigInteger.valueOf(1500000))
                        .fee(BigInteger.valueOf(230000))
                        .unit("lovelace")
                        .build();

        Result<String> result = transactionHelperService.transfer(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), metadata);

        if(result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        waitForTransaction(result);

        assertThat(result.isSuccessful(), is(true));
    }

    private long getTtl() throws ApiException {
        Block block = blockService.getLastestBlock().getValue();
        long slot = block.getSlot();
        return slot + 2000;
    }

    private void waitForTransaction(Result<String> result) {
        try {
            if (result.isSuccessful()) { //Wait for transaction to be mined
                int count = 0;
                while (count < 20) {
                    Result<TransactionContent> txnResult = transactionService.getTransaction(result.getValue());
                    if (txnResult.isSuccessful()) {
                        System.out.println(JsonUtil.getPrettyJson(txnResult.getValue()));
                        break;
                    } else {
                        System.out.println("Waiting for training to be mined ....");
                    }

                    count++;
                    Thread.sleep(2000);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private JsonNode loadJsonMetadata(String key) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(this.getClass().getClassLoader().getResourceAsStream(dataFile));
        ObjectNode root = (ObjectNode)rootNode;

        return root.get(key);
    }
}
