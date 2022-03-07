package com.bloxbean.cardano.client.backend.api.helper;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.api.*;
import com.bloxbean.cardano.client.backend.api.helper.model.TransactionResult;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Amount;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.crypto.Keys;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.crypto.VerificationKey;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.bloxbean.cardano.client.metadata.helper.JsonNoSchemaToMetadataConverter;
import com.bloxbean.cardano.client.transaction.model.MintTransaction;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Policy;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.script.*;
import com.bloxbean.cardano.client.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TransactionHelperServiceIT extends BaseITTest {

    UtxoService utxoService;
    TransactionService transactionService;
    TransactionHelperService transactionHelperService;
    BlockService blockService;
    FeeCalculationService feeCalculationService;
    EpochService epochService;

    String dataFile = "json-metadata.json";

    @BeforeEach
    public void setup() {
        BackendService backendService = getBackendService();//BackendFactory.getBlockfrostBackendService(Constants.BLOCKFROST_TESTNET_URL, bfProjectId);
        utxoService = backendService.getUtxoService();
        transactionService = backendService.getTransactionService();
        transactionHelperService = backendService.getTransactionHelperService();
        blockService = backendService.getBlockService();
        feeCalculationService = backendService.getFeeCalculationService();
        epochService = backendService.getEpochService();
    }

    @Test
    void transfer() throws CborSerializationException, AddressExcepion, ApiException {
        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        PaymentTransaction paymentTransaction =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .amount(BigInteger.valueOf(1500000))
                        .unit("lovelace")
                        .build();

        BigInteger fee = feeCalculationService.calculateFee(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), null);

        paymentTransaction.setFee(fee);

        Result<TransactionResult> result = transactionHelperService.transfer(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build());

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        waitForTransaction(result);

        assertThat(result.isSuccessful(), is(true));
    }

    @Test
    void transferToByronAddress() throws CborSerializationException, AddressExcepion, ApiException {

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "2cWKMJemoBaipzQe9BArYdo2iPUfJQdZAjm4iCzDA1AfNxJSTgm9FZQTmFCYhKkeYrede";

        PaymentTransaction paymentTransaction =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .amount(BigInteger.valueOf(1500000))
                        .unit("lovelace")
                        .build();

        BigInteger fee = feeCalculationService.calculateFee(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), null);

        paymentTransaction.setFee(fee);

        Result<TransactionResult> result = transactionHelperService.transfer(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build());

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        waitForTransaction(result);

        assertThat(result.isSuccessful(), is(true));
    }

    @Test
    void transferWithAdditionalWitness() throws CborSerializationException, AddressExcepion, ApiException {

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        String additionalAccMnemonic = "plate swamp urge edit avoid discover sibling raven awkward tell science increase fame practice bike caught taxi never critic ski north slogan fuel kitten";
        Account additioinalAccount = new Account(Networks.testnet(), additionalAccMnemonic);

        PaymentTransaction paymentTransaction =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .amount(BigInteger.valueOf(1500000))
                        .unit("lovelace")
                        .additionalWitnessAccounts(Arrays.asList(additioinalAccount)) //Unnecessary for now, but may be used in future
                        .build();

        BigInteger fee =
                feeCalculationService.calculateFee(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), null);

        paymentTransaction.setFee(fee);

        Result<TransactionResult> result =
                transactionHelperService.transfer(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build());

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        waitForTransaction(result);

        assertThat(result.isSuccessful(), is(true));
    }

    @Test
    void transferMultiAsset() throws CborSerializationException, AddressExcepion, ApiException {
        //Sender address : addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y
        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqukflcjxrph0ff8vpd3mpvdnk40srgfjkx0p2q9jcdwp7q70l3jd6rdppxmttlj272jr8l7pudacze588wt8m2kts3qkuqv9j";

        PaymentTransaction paymentTransaction =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .amount(BigInteger.valueOf(12))
                        .unit("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e")
                        .build();

        BigInteger fee = feeCalculationService.calculateFee(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), null);
        System.out.println(fee);

        paymentTransaction.setFee(fee);

        Result<TransactionResult> result = transactionHelperService.transfer(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build());

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        System.out.println(result);
        waitForTransaction(result);
        assertThat(result.isSuccessful(), is(true));
    }

    @Test
    void transferMultiAssetMultiPayments_whenMultipleSenders() throws CborSerializationException, AddressExcepion, ApiException {
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
                        .unit("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e")
                        .build();

        PaymentTransaction paymentTransaction2 =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver2)
                        .amount(BigInteger.valueOf(33))
                        .unit("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e")
                        .build();

        PaymentTransaction paymentTransaction3 =
                PaymentTransaction.builder()
                        .sender(sender2)
                        .receiver(receiver3)
                        .amount(BigInteger.valueOf(3110000))
                        .unit(LOVELACE)
                        .build();

        //Calculate total fee for all 3 payment transactions and set in one of the payment transaction
        BigInteger fee = feeCalculationService.calculateFee(Arrays.asList(paymentTransaction1, paymentTransaction2, paymentTransaction3),
                TransactionDetailsParams
                        .builder()
                        .ttl(getTtl())
                        .build(), null);
        paymentTransaction1.setFee(fee);

        Result<TransactionResult> result = transactionHelperService.transfer(Arrays.asList(paymentTransaction1, paymentTransaction2, paymentTransaction3),
                TransactionDetailsParams.builder().ttl(getTtl()).build());

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        System.out.println(result);
        waitForTransaction(result);
        assertThat(result.isSuccessful(), is(true));
    }

    @Test
    void transferMultiAssetMultiPayments_whenSingleSender() throws CborSerializationException, AddressExcepion, ApiException {
        //Sender address : addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y
        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);

        String receiver1 = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String receiver2 = "addr_test1qz7r5eu2jg0hx470mmf79vpgueaggh22pmayry8xrre5grtpyy9s8u2heru58a4r68wysmdw9v40zznttmwrg0a6v9tq36pjak";
        String receiver3 = "addr_test1qrp6x6aq2m28xhvxhqzufl0ff7x8gmzjejssrk29mx0q829dsty3hzmrl2k8jhwzghgxuzfjatgxlhg9wtl6ecv0v3cqf92rnh";

        PaymentTransaction paymentTransaction1 =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver1)
                        .unit(LOVELACE)
                        .amount(BigInteger.valueOf(1000000))
                        .fee(BigInteger.valueOf(4000)) //some low fee (invalid). Just for testing
                        .build();

        PaymentTransaction paymentTransaction2 =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver2)
                        .unit(LOVELACE)
                        .amount(BigInteger.valueOf(2000000))
                        .build();

        PaymentTransaction paymentTransaction3 =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver3)
                        .amount(BigInteger.valueOf(3000000))
                        .unit(LOVELACE)
                        .build();

        //Calculate total fee for all 3 payment transactions and set in one of the payment transaction
        BigInteger fee = feeCalculationService.calculateFee(Arrays.asList(paymentTransaction1, paymentTransaction2, paymentTransaction3),
                TransactionDetailsParams
                        .builder()
                        .ttl(getTtl())
                        .build(), null);
        paymentTransaction1.setFee(fee);

        Result<TransactionResult> result = transactionHelperService.transfer(Arrays.asList(paymentTransaction1, paymentTransaction2, paymentTransaction3),
                TransactionDetailsParams.builder().ttl(getTtl()).build());

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        System.out.println(result);
        waitForTransaction(result);
        assertThat(result.isSuccessful(), is(true));
    }

    @Test
    void transferMultiAssetMultiPayments_whenSingleSender_multipleToken() throws CborSerializationException, AddressExcepion, ApiException {
        //Sender address : addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y
        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);

        String receiver1 = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String receiver2 = "addr_test1qz7r5eu2jg0hx470mmf79vpgueaggh22pmayry8xrre5grtpyy9s8u2heru58a4r68wysmdw9v40zznttmwrg0a6v9tq36pjak";
        String receiver3 = "addr_test1qrp6x6aq2m28xhvxhqzufl0ff7x8gmzjejssrk29mx0q829dsty3hzmrl2k8jhwzghgxuzfjatgxlhg9wtl6ecv0v3cqf92rnh";
        String receiver4 = "addr_test1qzj02w9f2lv3ayekxwr3hdxvn2umf3hyw6azze4rtczxzuwljt3yugepv88k3htyr60llrcj4a52hlytaka7jj3kf85szkyp6l";

        PaymentTransaction paymentTransaction1 =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver1)
                        .unit(LOVELACE)
                        .amount(BigInteger.valueOf(1000000))
                        .fee(BigInteger.valueOf(4000)) //some low fee (invalid). Just for testing
                        .build();

        PaymentTransaction paymentTransaction2 =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver2)
                        .unit("6b248bf1bbfac692610ca7e9873f988dc5e358b9229be8d6363aedd34d59546f6b656e")
                        .amount(BigInteger.valueOf(15))
                        .build();

        PaymentTransaction paymentTransaction3 =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver3)
                        .amount(BigInteger.valueOf(4))
                        .unit("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e")
                        .build();

        PaymentTransaction paymentTransaction4 =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver4)
                        .amount(BigInteger.valueOf(2))
                        .unit("b9bd3fb4511908402fbef848eece773bb44c867c25ac8c08d9ec3313696e746a")
                        .build();

        //Calculate total fee for all 4 payment transactions and set in one of the payment transaction
        BigInteger fee = feeCalculationService.calculateFee(Arrays.asList(paymentTransaction1, paymentTransaction2, paymentTransaction3, paymentTransaction4),
                TransactionDetailsParams
                        .builder()
                        .ttl(getTtl())
                        .build(), null);
        paymentTransaction1.setFee(fee);

        Result<TransactionResult> result = transactionHelperService.transfer(Arrays.asList(paymentTransaction1, paymentTransaction2, paymentTransaction3, paymentTransaction4),
                TransactionDetailsParams.builder().ttl(getTtl()).build());

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        System.out.println(result);
        waitForTransaction(result);
        assertThat(result.isSuccessful(), is(true));
    }

    @Test
    void mintTokenBackward() throws CborSerializationException, AddressExcepion, ApiException {
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
                        .mintAssets(Arrays.asList(multiAsset))
                        .policyScript(scriptPubkey)
                        .policyKeys(Arrays.asList(skey))
                        .build();
        BigInteger fee = feeCalculationService.calculateFee(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), null);
        paymentTransaction.setFee(fee);
        System.out.println(fee);
        Result<TransactionResult> result = transactionHelperService.mintToken(paymentTransaction,
                TransactionDetailsParams.builder().ttl(getTtl()).build());
        System.out.println("Request: \n" + JsonUtil.getPrettyJson(paymentTransaction));
        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);
        waitForTransaction(result);
        assertThat(result.isSuccessful(), is(true));
    }

    @Test
    void transferAndCollateMultiAssetMultiPayments_whenSingleSender_multipleToken() throws CborSerializationException, AddressExcepion, ApiException {
        //Sender address : addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y
        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);

        String receiver1 = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String receiver2 = "addr_test1qz7r5eu2jg0hx470mmf79vpgueaggh22pmayry8xrre5grtpyy9s8u2heru58a4r68wysmdw9v40zznttmwrg0a6v9tq36pjak";
        String receiver3 = "addr_test1qrp6x6aq2m28xhvxhqzufl0ff7x8gmzjejssrk29mx0q829dsty3hzmrl2k8jhwzghgxuzfjatgxlhg9wtl6ecv0v3cqf92rnh";
        String receiver4 = "addr_test1qzj02w9f2lv3ayekxwr3hdxvn2umf3hyw6azze4rtczxzuwljt3yugepv88k3htyr60llrcj4a52hlytaka7jj3kf85szkyp6l";

        PaymentTransaction paymentTransaction1 =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver1)
                        .unit(LOVELACE)
                        .amount(BigInteger.valueOf(1000000))
                        .fee(BigInteger.valueOf(4000)) //some low fee (invalid). Just for testing
                        .build();

        PaymentTransaction paymentTransaction2a =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver2)
                        .unit(LOVELACE)
                        .amount(BigInteger.valueOf(1500000L))
                        .build();

        PaymentTransaction paymentTransaction2b =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver2)
                        .unit("6b248bf1bbfac692610ca7e9873f988dc5e358b9229be8d6363aedd34d59546f6b656e")
                        .amount(BigInteger.valueOf(15))
                        .build();

        PaymentTransaction paymentTransaction3 =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver3)
                        .amount(BigInteger.valueOf(4))
                        .unit("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e")
                        .build();

        PaymentTransaction paymentTransaction4 =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver4)
                        .amount(BigInteger.valueOf(2))
                        .unit("b9bd3fb4511908402fbef848eece773bb44c867c25ac8c08d9ec3313696e746a")
                        .build();

        //Calculate total fee for all 4 payment transactions and set in one of the payment transaction
        BigInteger fee = feeCalculationService.calculateFee(Arrays.asList(paymentTransaction1, paymentTransaction2a, paymentTransaction2b, paymentTransaction3, paymentTransaction4),
                TransactionDetailsParams
                        .builder()
                        .ttl(getTtl())
                        .build(), null);
        paymentTransaction1.setFee(fee);

        Result<TransactionResult> result = transactionHelperService.transfer(Arrays.asList(paymentTransaction1, paymentTransaction2a, paymentTransaction2b, paymentTransaction3, paymentTransaction4),
                TransactionDetailsParams.builder().ttl(getTtl()).build());

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        System.out.println(result);
        waitForTransaction(result);
        assertThat(result.isSuccessful(), is(true));
    }

    @Test
    void mintToken() throws CborSerializationException, AddressExcepion, ApiException {

        Keys keys = KeyGenUtil.generateKey();
        VerificationKey vkey = keys.getVkey();
        SecretKey skey = keys.getSkey();

        ScriptPubkey scriptPubkey = ScriptPubkey.create(vkey);
        String policyId = scriptPubkey.getPolicyId();

        Policy policy = new Policy(scriptPubkey, Arrays.asList(skey));

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
                        .mintAssets(Arrays.asList(multiAsset))
                        .policy(policy)
                        .build();

        BigInteger fee = feeCalculationService.calculateFee(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), null);
        paymentTransaction.setFee(fee);
        System.out.println(fee);

        Result<TransactionResult> result = transactionHelperService.mintToken(paymentTransaction,
                TransactionDetailsParams.builder().ttl(getTtl()).build());

        System.out.println("Request: \n" + JsonUtil.getPrettyJson(paymentTransaction));
        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        waitForTransaction(result);

        assertThat(result.isSuccessful(), is(true));
    }

    @Test
    void mintTokenWithScriptAtLeast() throws CborSerializationException, AddressExcepion, ApiException {
        Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("scriptAtLeast", 3, 2);

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policy.getPolicyId());
        Asset asset = new Asset("selftoken1", BigInteger.valueOf(250000));
        multiAsset.getAssets().add(asset);

        MintTransaction paymentTransaction =
                MintTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .mintAssets(Arrays.asList(multiAsset))
                        .policy(policy)
                        .build();

        BigInteger fee = feeCalculationService.calculateFee(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), null);
        paymentTransaction.setFee(fee);
        System.out.println(fee);

        Result<TransactionResult> result = transactionHelperService.mintToken(paymentTransaction,
                TransactionDetailsParams.builder().ttl(getTtl()).build());

        System.out.println("Request: \n" + JsonUtil.getPrettyJson(paymentTransaction));
        System.out.println(result);
        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        assertThat(result.isSuccessful(), is(true));
        waitForTransaction(result);
    }

    @Test
    void mintTokenWithScriptAtLeastButNotSufficientKeys() throws CborSerializationException, AddressExcepion, ApiException {
        Tuple<ScriptPubkey, Keys> tuple1 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey1 = tuple1._1;

        Tuple<ScriptPubkey, Keys> tuple2 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey2 = tuple2._1;
        SecretKey sk2 = tuple2._2.getSkey();

        Tuple<ScriptPubkey, Keys> tuple3 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey3 = tuple3._1;

        ScriptAtLeast scriptAtLeast = new ScriptAtLeast(2)
                .addScript(scriptPubkey1)
                .addScript(scriptPubkey2)
                .addScript(scriptPubkey3);

        Policy policy = new Policy(scriptAtLeast, Arrays.asList(sk2));

        String policyId = scriptAtLeast.getPolicyId();

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policyId);
        Asset asset = new Asset("selftoken1", BigInteger.valueOf(250000));
        multiAsset.getAssets().add(asset);

        MintTransaction mintTransaction =
                MintTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .mintAssets(Arrays.asList(multiAsset))
                        .policy(policy)
                        .build();

        BigInteger fee = feeCalculationService.calculateFee(mintTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), null);
        mintTransaction.setFee(fee);
        System.out.println(fee);

        System.out.println(JsonUtil.getPrettyJson(mintTransaction));

        Result<TransactionResult> result = transactionHelperService.mintToken(mintTransaction,
                TransactionDetailsParams.builder().ttl(getTtl()).build());

        System.out.println("Request: \n" + JsonUtil.getPrettyJson(mintTransaction));
        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        assertThat(result.isSuccessful(), is(false));
        waitForTransaction(result);
    }

    @Test
    void mintTokenWithScriptAll() throws CborSerializationException, AddressExcepion, ApiException {
        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("ScriptAll", 3);

        System.out.println(">> Policy Id: " + policy.getPolicyId());

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policy.getPolicyId());
        Asset asset = new Asset(HexUtil.encodeHexString("selftoken1".getBytes(StandardCharsets.UTF_8), true), BigInteger.valueOf(250000));
        multiAsset.getAssets().add(asset);

        MintTransaction mintTransaction =
                MintTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .mintAssets(Arrays.asList(multiAsset))
                        .policy(policy)
                        .build();

        BigInteger fee = feeCalculationService.calculateFee(mintTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), null);
        mintTransaction.setFee(fee);
        System.out.println(fee);

        System.out.println(JsonUtil.getPrettyJson(mintTransaction));

        Result<TransactionResult> result = transactionHelperService.mintToken(mintTransaction,
                TransactionDetailsParams.builder().ttl(getTtl()).build());

        System.out.println("Request: \n" + JsonUtil.getPrettyJson(mintTransaction));
        System.out.println(result);
        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        assertThat(result.isSuccessful(), is(true));
        waitForTransaction(result);
    }

    @Test
    void mintTokenWithScriptAllButNotSufficientKeys() throws CborSerializationException, AddressExcepion, ApiException {
        Tuple<ScriptPubkey, Keys> tuple1 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey1 = tuple1._1;

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

        Policy policy = new Policy(scriptAll, Arrays.asList(sk2, sk3));

        String policyId = scriptAll.getPolicyId();

        System.out.println(">> Policy Id: " + policyId);

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policyId);
        Asset asset = new Asset(HexUtil.encodeHexString("selftoken1".getBytes(StandardCharsets.UTF_8), true), BigInteger.valueOf(250000));
        multiAsset.getAssets().add(asset);

        MintTransaction mintTransaction =
                MintTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .mintAssets(Arrays.asList(multiAsset))
                        .policy(policy)
                        .build();

        BigInteger fee = feeCalculationService.calculateFee(mintTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), null);
        mintTransaction.setFee(fee);
        System.out.println(fee);

        System.out.println(JsonUtil.getPrettyJson(mintTransaction));

        Result<TransactionResult> result = transactionHelperService.mintToken(mintTransaction,
                TransactionDetailsParams.builder().ttl(getTtl()).build());

        System.out.println("Request: \n" + JsonUtil.getPrettyJson(mintTransaction));
        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        assertThat(result.isSuccessful(), is(false));
        waitForTransaction(result);
    }


    @Test
    void mintTokenWithScriptAtLeastBefore() throws CborSerializationException, AddressExcepion, ApiException {
        Tuple<ScriptPubkey, Keys> tuple1 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey1 = tuple1._1;

        Tuple<ScriptPubkey, Keys> tuple2 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey2 = tuple2._1;
        SecretKey sk2 = tuple2._2.getSkey();

        Tuple<ScriptPubkey, Keys> tuple3 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey3 = tuple3._1;
        SecretKey sk3 = tuple3._2.getSkey();

        long slot = getTtl();

        RequireTimeBefore requireTimeBefore = new RequireTimeBefore((int) slot);

        ScriptAtLeast scriptAtLeast = new ScriptAtLeast(2)
                .addScript(requireTimeBefore)
                .addScript(scriptPubkey1)
                .addScript(scriptPubkey2)
                .addScript(scriptPubkey3);

        Policy policy = new Policy(scriptAtLeast, Arrays.asList(sk2, sk3));

        String policyId = scriptAtLeast.getPolicyId();

        System.out.println(">> Policy Id: " + policyId);

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policyId);
        Asset asset = new Asset("selftoken1", BigInteger.valueOf(250000));
        multiAsset.getAssets().add(asset);

        MintTransaction mintTransaction =
                MintTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .mintAssets(Arrays.asList(multiAsset))
                        .policy(policy)
                        .build();

        BigInteger fee = feeCalculationService.calculateFee(mintTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), null);
        mintTransaction.setFee(fee);
        System.out.println(fee);

        System.out.println(JsonUtil.getPrettyJson(mintTransaction));

        Result<TransactionResult> result = transactionHelperService.mintToken(mintTransaction,
                TransactionDetailsParams.builder().ttl(getTtl()).build());

        System.out.println("Request: \n" + JsonUtil.getPrettyJson(mintTransaction));
        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        assertThat(result.isSuccessful(), is(true));
        waitForTransaction(result);
    }

    @Test
    void mintTokenWithScriptPubBeforeValidSlot() throws CborSerializationException, AddressExcepion, ApiException {
        Tuple<ScriptPubkey, Keys> tuple1 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey1 = tuple1._1;
        SecretKey sk1 = tuple1._2.getSkey();

        Tuple<ScriptPubkey, Keys> tuple2 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey2 = tuple2._1;
        SecretKey sk2 = tuple2._2.getSkey();

        Tuple<ScriptPubkey, Keys> tuple3 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey3 = tuple3._1;

        ScriptAtLeast scriptAtLeast = new ScriptAtLeast(2)
                .addScript(scriptPubkey1)
                .addScript(scriptPubkey2)
                .addScript(scriptPubkey3);

        long slot = getTtl();
        RequireTimeBefore requireTimeBefore = new RequireTimeBefore(slot);

        ScriptAll scriptAll = new ScriptAll()
                .addScript(requireTimeBefore)
                .addScript(scriptAtLeast);

        Policy policy = new Policy(scriptAll, Arrays.asList(sk1, sk2));

        String policyId = scriptAll.getPolicyId();

        System.out.println(">> Policy Id: " + policyId);

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policyId);
        Asset asset = new Asset("selftoken1", BigInteger.valueOf(250000));
        multiAsset.getAssets().add(asset);

        MintTransaction mintTransaction =
                MintTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .mintAssets(Arrays.asList(multiAsset))
                        .policy(policy)
                        .build();

        BigInteger fee = feeCalculationService.calculateFee(mintTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), null);
        mintTransaction.setFee(fee);
        System.out.println(fee);

        System.out.println(JsonUtil.getPrettyJson(mintTransaction));

        Result<TransactionResult> result = transactionHelperService.mintToken(mintTransaction,
                TransactionDetailsParams.builder().ttl(getTtl()).build());

        System.out.println("Request: \n" + JsonUtil.getPrettyJson(mintTransaction));
        System.out.println(result);
        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        assertThat(result.isSuccessful(), is(true));
        waitForTransaction(result);
    }

    @Test
    void mintTokenWithScriptPubBeforeInValidSlot() throws CborSerializationException, AddressExcepion, ApiException {
        Tuple<ScriptPubkey, Keys> tuple = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey = tuple._1;
        SecretKey sk = tuple._2.getSkey();

        long slot = getTtl() - 5000;
        RequireTimeBefore requireTimeBefore = new RequireTimeBefore(slot);
        ScriptAll scriptAll = new ScriptAll()
                .addScript(requireTimeBefore)
                .addScript(scriptPubkey);

        Policy policy = new Policy(scriptAll, Arrays.asList(sk));

        String policyId = scriptAll.getPolicyId();

        System.out.println(">> Policy Id: " + policyId);

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policyId);
        Asset asset = new Asset("selftoken1", BigInteger.valueOf(250000));
        multiAsset.getAssets().add(asset);

        MintTransaction mintTransaction =
                MintTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .mintAssets(Arrays.asList(multiAsset))
                        .policy(policy)
                        .build();

        BigInteger fee = feeCalculationService.calculateFee(mintTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), null);
        mintTransaction.setFee(fee);
        System.out.println(fee);

        System.out.println(JsonUtil.getPrettyJson(mintTransaction));

        Result<TransactionResult> result = transactionHelperService.mintToken(mintTransaction,
                TransactionDetailsParams.builder().ttl(getTtl()).build());

        System.out.println("Request:- \n" + JsonUtil.getPrettyJson(mintTransaction));
        System.out.println(result);
        if (result.isSuccessful())
            System.out.println("Transaction Id:- " + result.getValue());
        else
            System.out.println("Transaction failed:- " + result);

        assertThat(result.isSuccessful(), is(false));
        waitForTransaction(result);
    }

    @Test
    void mintTokenWithScriptPubAfterValidSlot() throws CborSerializationException, AddressExcepion, ApiException {
        Tuple<ScriptPubkey, Keys> tuple = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey = tuple._1;
        SecretKey sk = tuple._2.getSkey();

        long afterSlot = getTtl() - 4000; //As getTtl() is currentslot + 2000
        RequireTimeAfter requireTimeAfter = new RequireTimeAfter(afterSlot);
        ScriptAll policyScript = new ScriptAll()
                .addScript(requireTimeAfter)
                .addScript(scriptPubkey);

        Policy policy = new Policy(policyScript, Arrays.asList(sk));
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
                        .mintAssets(Arrays.asList(multiAsset))
                        .policy(policy)
                        .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(getTtl())
                .validityStartInterval(afterSlot + 100) //validity start should be after slot or later
                .build();

        BigInteger fee = feeCalculationService.calculateFee(mintTransaction, detailsParams, null);
        mintTransaction.setFee(fee);
        System.out.println(fee);

        System.out.println(JsonUtil.getPrettyJson(mintTransaction));

        Result<TransactionResult> result = transactionHelperService.mintToken(mintTransaction, detailsParams);

        System.out.println("Request:- \n" + JsonUtil.getPrettyJson(mintTransaction));
        System.out.println(result);
        if (result.isSuccessful())
            System.out.println("Transaction Id:- " + result.getValue());
        else
            System.out.println("Transaction failed:- " + result);

        assertThat(result.isSuccessful(), is(true));
        waitForTransaction(result);
    }

    @Test
    void mintTokenWithScriptPubAfterInValidSlot() throws CborSerializationException, AddressExcepion, ApiException {
        Tuple<ScriptPubkey, Keys> tuple = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey = tuple._1;
        SecretKey sk = tuple._2.getSkey();

        long afterSlot = getTtl() + 4000; //As getTtl() is currentslot + 2000
        RequireTimeAfter requireTimeAfter = new RequireTimeAfter(afterSlot);
        ScriptAll policyScript = new ScriptAll()
                .addScript(requireTimeAfter)
                .addScript(scriptPubkey);

        Policy policy = new Policy(policyScript, Arrays.asList(sk));
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
                        .mintAssets(Arrays.asList(multiAsset))
                        .policy(policy)
                        .build();

        BigInteger fee = feeCalculationService.calculateFee(mintTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), null);
        mintTransaction.setFee(fee);
        System.out.println(fee);

        System.out.println(JsonUtil.getPrettyJson(mintTransaction));

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(getTtl())
                .validityStartInterval(afterSlot + 100) //validity start should be after slot or later
                .build();

        Result<TransactionResult> result = transactionHelperService.mintToken(mintTransaction, detailsParams);

        System.out.println("Request:- \n" + JsonUtil.getPrettyJson(mintTransaction));
        System.out.println(result);
        if (result.isSuccessful())
            System.out.println("Transaction Id:- " + result.getValue());
        else
            System.out.println("Transaction failed:- " + result);

        assertThat(result.isSuccessful(), is(false));
        waitForTransaction(result);
    }

    @Test
    void mintTokenWithMetadata() throws CborSerializationException, AddressExcepion, ApiException {

        Keys keys = KeyGenUtil.generateKey();
        VerificationKey vkey = keys.getVkey();
        SecretKey skey = keys.getSkey();

        ScriptPubkey scriptPubkey = ScriptPubkey.create(vkey);

        Policy policy = new Policy(scriptPubkey, Arrays.asList(skey));
        String policyId = scriptPubkey.getPolicyId();

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policyId);
        Asset asset = new Asset(HexUtil.encodeHexString("Testtoken123".getBytes(StandardCharsets.UTF_8), true), BigInteger.valueOf(150000));
        multiAsset.getAssets().add(asset);

        MintTransaction mintTransaction =
                MintTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .mintAssets(Arrays.asList(multiAsset))
                        .policy(policy)
                        .build();

        Metadata metadata = new CBORMetadata()
                .put(new BigInteger("1"), "SAT Token")
                .put(new BigInteger("2"), "SAT")
                .put(new BigInteger("3"), "This is a test token");

        BigInteger fee = feeCalculationService.calculateFee(mintTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), metadata);
        mintTransaction.setFee(fee);
        System.out.println(fee);

        Result<TransactionResult> result = transactionHelperService.mintToken(mintTransaction,
                TransactionDetailsParams.builder().ttl(getTtl()).build(), metadata);

        System.out.println("Request: \n" + JsonUtil.getPrettyJson(mintTransaction));
        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        waitForTransaction(result);

        assertThat(result.isSuccessful(), is(true));
    }

    @Test
    void mintTokenWithNormalAccountKeyHash() throws CborSerializationException, ApiException, AddressExcepion {
        String senderMnemonic = "kit color frog trick speak employ suit sort bomb goddess jewel primary spoil fade person useless measure manage warfare reduce few scrub beyond era";
        Account sender = new Account(Networks.testnet(), senderMnemonic);

        String scriptAccountMnemonic = "episode same use wreck force already grief spike kiss host magic spoon cry lecture tuition style old detect session creek champion cry service exchange";
        Account scriptAccount = new Account(Networks.testnet(), scriptAccountMnemonic);

        byte[] prvKeyBytes = scriptAccount.privateKeyBytes();
        byte[] pubKeyBytes = scriptAccount.publicKeyBytes();

        SecretKey sk = SecretKey.create(prvKeyBytes);
        VerificationKey vkey = VerificationKey.create(pubKeyBytes);

        ScriptPubkey scriptPubkey = ScriptPubkey.create(vkey);

        Policy policy = new Policy(scriptPubkey, Arrays.asList(sk));
        String policyId = scriptPubkey.getPolicyId();

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policyId);

        Asset asset = new Asset("token_12", BigInteger.valueOf(6000));
        multiAsset.getAssets().add(asset);

        MintTransaction mintTransaction = MintTransaction.builder()
                .sender(sender)
                .mintAssets(Arrays.asList(multiAsset))
                .policy(policy)
                .build();

        TransactionDetailsParams detailsParams =
                TransactionDetailsParams.builder()
                        .ttl(getTtl())
                        .build();

        //Calculate fee
        BigInteger fee
                = feeCalculationService.calculateFee(mintTransaction, detailsParams, null);
        mintTransaction.setFee(fee);

        Result<TransactionResult> result = transactionHelperService.mintToken(mintTransaction,
                TransactionDetailsParams.builder().ttl(getTtl()).build());

        System.out.println(result);

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        waitForTransaction(result);

        String fingerPrint = AssetUtil.calculateFingerPrint(policyId, HexUtil.encodeHexString(asset.getNameAsBytes()));
        System.out.println("Fingerprint: " + fingerPrint);

        assertThat(result.isSuccessful(), is(true));
    }

    @Test
    void mintTokenWithNormalAccountKeyHashAndSignWithA2ndNormalAccount() throws CborSerializationException, ApiException, AddressExcepion {
        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);

        String scriptAccountMnemonic = "episode same use wreck force already grief spike kiss host magic spoon cry lecture tuition style old detect session creek champion cry service exchange";
        Account scriptAccount = new Account(Networks.testnet(), scriptAccountMnemonic);

        byte[] pubKeyBytes = scriptAccount.publicKeyBytes();

        VerificationKey vkey = VerificationKey.create(pubKeyBytes);

        ScriptPubkey scriptPubkey = ScriptPubkey.create(vkey);
        String policyId = scriptPubkey.getPolicyId();

        Policy policy = new Policy();
        policy.setPolicyScript(scriptPubkey);

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policyId);

        Asset asset = new Asset("token_12", BigInteger.valueOf(6000));
        multiAsset.getAssets().add(asset);

        MintTransaction mintTransaction = MintTransaction.builder()
                .sender(sender)
                .mintAssets(Arrays.asList(multiAsset))
                .policy(policy)
                .additionalWitnessAccounts(Arrays.asList(scriptAccount))
                .build();

        TransactionDetailsParams detailsParams =
                TransactionDetailsParams.builder()
                        .ttl(getTtl())
                        .build();

        //Calculate fee
        BigInteger fee = feeCalculationService.calculateFee(mintTransaction, detailsParams, null);
        mintTransaction.setFee(fee);

        Result<TransactionResult> result = transactionHelperService.mintToken(mintTransaction,
                TransactionDetailsParams.builder().ttl(getTtl()).build());

        System.out.println(result);

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        waitForTransaction(result);

        assertThat(result.isSuccessful(), is(true));
    }

    @Test
    void transferWithCBORMetadata() throws CborSerializationException, AddressExcepion, ApiException {

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qpjhf07kzxedmr6kl8u543cpw33up020mzk5hnkst0y24rnufy0uyckakmut387jckln56w99mjrejt9rp5zcg0aj7ashhl9z9";

        CBORMetadataMap mm = new CBORMetadataMap()
                .put(new BigInteger("1978"), "1978value")
                .put(new BigInteger("197819"), new BigInteger("200001"))
                .put("203", new byte[]{11, 11, 10});

        CBORMetadataList list = new CBORMetadataList()
                .add("301value")
                .add(new BigInteger("300001"))
                .add(new byte[]{11, 11, 10})
                .add(new CBORMetadataMap()
                        .put(new BigInteger("401"), "401str")
                        .put("hello", "hellovalue"));
        Metadata metadata = new CBORMetadata()
                .put(new BigInteger("197819781978"), "John")
                .put(new BigInteger("197819781979"), "CA")
                .put(new BigInteger("1978197819710"), new byte[]{0, 11})
                .put(new BigInteger("1978197819711"), mm)
                .put(new BigInteger("1978197819712"), list);

        System.out.println(HexUtil.encodeHexString(metadata.serialize()));

        PaymentTransaction paymentTransaction =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .amount(BigInteger.valueOf(1500000))
                        .unit("lovelace")
                        .build();

        BigInteger fee = feeCalculationService.calculateFee(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), metadata);
        System.out.println(fee);
        paymentTransaction.setFee(fee);

        Result<TransactionResult> result = transactionHelperService.transfer(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), metadata);

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        waitForTransaction(result);

        assertThat(result.isSuccessful(), is(true));
    }

    @Test
    void transferWithJSONMetadata() throws CborSerializationException, AddressExcepion, ApiException, IOException {

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
                        .unit("lovelace")
                        .build();

        BigInteger fee = feeCalculationService.calculateFee(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), metadata);
        paymentTransaction.setFee(fee);
        System.out.println(fee);

        Result<TransactionResult> result = transactionHelperService.transfer(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), metadata);

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        waitForTransaction(result);

        assertThat(result.isSuccessful(), is(true));
    }

    @Test
    void transferWithJSONMetadataComplex() throws CborSerializationException, AddressExcepion, ApiException, IOException {

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
                        .unit("lovelace")
                        .build();

        BigInteger fee = feeCalculationService.calculateFee(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), metadata);
        paymentTransaction.setFee(fee);
        System.out.println(fee);

        Result<TransactionResult> result = transactionHelperService.transfer(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), metadata);

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        waitForTransaction(result);

        assertThat(result.isSuccessful(), is(true));
    }

    @Test
    void testCreateSignedTransaction() throws CborSerializationException, AddressExcepion, ApiException, IOException, CborDeserializationException {

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        String additionalAccMnemonic = "plate swamp urge edit avoid discover sibling raven awkward tell science increase fame practice bike caught taxi never critic ski north slogan fuel kitten";
        Account additionalAccount = new Account(Networks.testnet(), additionalAccMnemonic);

        JsonNode json = loadJsonMetadata("json-3");
        Metadata metadata = JsonNoSchemaToMetadataConverter.jsonToCborMetadata(json.toString());

        PaymentTransaction paymentTransaction =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .amount(BigInteger.valueOf(1500000))
                        .unit("lovelace")
                        .additionalWitnessAccounts(Arrays.asList(additionalAccount, sender))
                        .build();

        BigInteger fee = feeCalculationService.calculateFee(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), metadata);
        paymentTransaction.setFee(fee);
        System.out.println(fee);

        String result = transactionHelperService.createSignedTransaction(Arrays.asList(paymentTransaction),
                TransactionDetailsParams.builder().ttl(getTtl()).build(), metadata);

        Transaction txn = Transaction.deserialize(HexUtil.decodeHexString(result));

        System.out.println(result);
        assertNotNull(result);
        assertNotNull(txn);
        assertThat(txn.getWitnessSet().getVkeyWitnesses(), hasSize(3));
    }

    private long getTtl() throws ApiException {
        Block block = blockService.getLastestBlock().getValue();
        long slot = block.getSlot();
        return slot + 2000;
    }

    private void waitForTransaction(Result<TransactionResult> result) {
        try {
            if (result.isSuccessful()) { //Wait for transaction to be mined
                int count = 0;
                while (count < 60) {
                    Result<TransactionContent> txnResult = transactionService.getTransaction(result.getValue().getTransactionId());
                    if (txnResult.isSuccessful()) {
                        System.out.println(JsonUtil.getPrettyJson(txnResult.getValue()));
                        break;
                    } else {
                        System.out.println("Waiting for transaction to be mined ....");
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
        ObjectNode root = (ObjectNode) rootNode;

        return root.get(key);
    }

    @Nested
    class SendAllScenarios {

        @Test
        void sendAllWhenOnlyLovelace() throws Exception {
            String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
            Account sender = new Account(Networks.testnet(), senderMnemonic);
            System.out.println("Sender: " + sender.baseAddress());

            //Create a random receiver
            Account receiver = new Account(Networks.testnet());
            String receiverAddress = receiver.baseAddress();

            Amount lovelaceAmount = new Amount(LOVELACE, adaToLovelace(1.5));
            Result<TransactionResult> result = payTo(sender, receiverAddress, List.of(lovelaceAmount));
            assertThat(result.isSuccessful(), is(true));
            waitForTransaction(result);

            //Refund all lovelace from receiver to sender
            PaymentTransaction paymentTransaction =
                    PaymentTransaction.builder()
                            .sender(receiver)
                            .receiver(sender.baseAddress())
                            .amount(adaToLovelace(1.5))
                            .unit(LOVELACE)
                            .build();

            BigInteger fee = feeCalculationService.calculateFee(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), null);

            //Update the amount and fee
            paymentTransaction.setAmount(paymentTransaction.getAmount().subtract(fee));
            paymentTransaction.setFee(fee);

            System.out.println(paymentTransaction);

            result = transactionHelperService.transfer(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build());

            System.out.println(result);

            assertThat(result.isSuccessful(), is(true));
            waitForTransaction(result);
        }

        @Test
        void sendAllWhenLovelaceAndTokens() throws Exception {
            String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
            Account sender = new Account(Networks.testnet(), senderMnemonic);
            System.out.println("Sender >>> " + sender.baseAddress());

            //Create a random receiver
            Account receiver = new Account(Networks.testnet());
            String receiverAddress = receiver.baseAddress();

            Amount lovelaceAmount = new Amount(LOVELACE, adaToLovelace(1.54));
            //If it fails.. then check the available token at sender
            Amount tokenAmount = new Amount("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e", BigInteger.valueOf(5));
            Result<TransactionResult> result = payTo(sender, receiverAddress, List.of(lovelaceAmount, tokenAmount));
            assertThat(result.isSuccessful(), is(true));
            waitForTransaction(result);

            //Refund all lovelace from receiver to sender
            PaymentTransaction lovelacePayment =
                    PaymentTransaction.builder()
                            .sender(receiver)
                            .receiver(sender.baseAddress())
                            .amount(lovelaceAmount.getQuantity())
                            .unit(lovelaceAmount.getUnit())
                            .build();

            PaymentTransaction tokenPayment =
                    PaymentTransaction.builder()
                            .sender(receiver)
                            .receiver(sender.baseAddress())
                            .amount(tokenAmount.getQuantity())
                            .unit(tokenAmount.getUnit())
                            .build();

            BigInteger fee = feeCalculationService.calculateFee(List.of(lovelacePayment, tokenPayment), TransactionDetailsParams.builder().ttl(getTtl()).build(), null);

            //Update the amount and fee
            lovelacePayment.setAmount(lovelacePayment.getAmount().subtract(fee));
            lovelacePayment.setFee(fee);

            System.out.println(List.of(lovelaceAmount, tokenPayment));

            result = transactionHelperService.transfer(List.of(lovelacePayment, tokenPayment), TransactionDetailsParams.builder().ttl(getTtl()).build());

            System.out.println(result);

            assertThat(result.isSuccessful(), is(true));
            waitForTransaction(result);
        }

        private Result<TransactionResult> payTo(Account sender, String receiverAddress, List<Amount> amounts) throws ApiException, CborSerializationException, AddressExcepion {

            List<PaymentTransaction> payments = new ArrayList<>();

            amounts.forEach( amount -> {
                        PaymentTransaction paymentTransaction =
                                PaymentTransaction.builder()
                                        .sender(sender)
                                        .receiver(receiverAddress)
                                        .amount(amount.getQuantity())
                                        .unit(amount.getUnit())
                                        .build();
                        payments.add(paymentTransaction);
                    });

            Result<TransactionResult> result = null;
            if (payments.size() == 1) {
                BigInteger fee = feeCalculationService.calculateFee(payments.get(0), TransactionDetailsParams.builder().ttl(getTtl()).build(), null);
                payments.get(0).setFee(fee);
                result = transactionHelperService.transfer(payments.get(0), TransactionDetailsParams.builder().ttl(getTtl()).build());
            } else {
                BigInteger fee = feeCalculationService.calculateFee(payments, TransactionDetailsParams.builder().ttl(getTtl()).build(), null);
                payments.get(0).setFee(fee);
                result = transactionHelperService.transfer(payments, TransactionDetailsParams.builder().ttl(getTtl()).build());
            }

            if (result.isSuccessful())
                System.out.println("Transaction Id: " + result.getValue());
            else
                System.out.println("Transaction failed: " + result);
            return result;
        }
    }
}
