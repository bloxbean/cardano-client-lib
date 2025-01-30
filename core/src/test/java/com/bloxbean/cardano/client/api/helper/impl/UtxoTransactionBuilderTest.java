package com.bloxbean.cardano.client.api.helper.impl;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.exception.InsufficientBalanceException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelectionStrategyImpl;
import com.bloxbean.cardano.client.common.ADAConversionUtil;
import com.bloxbean.cardano.client.common.CardanoConstants;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.model.MintTransaction;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.AssetUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static com.bloxbean.cardano.client.common.CardanoConstants.ONE_ADA;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UtxoTransactionBuilderTest {

    public static final String LIST_2 = "list2";
    public static final String LIST_3 = "list3-multiassets";
    public static final String LIST_4 = "list4-multiassets";
    public static final String LIST_5 = "list5-multiassets-insufficientADA";
    public static final String LIST_6 = "list6-multiassets-insufficientADA_Error";
    public static final String LIST_7 = "list7-insufficient-change-amount";
    public static final String LIST_8 = "list8-insufficient-change-amount-with-native-token";

    public static final String LIST_9 = "list9-e2e-sender1";
    public static final String LIST_10 = "list10-e2e-sender2";

    @Mock
    UtxoSupplier utxoSupplier;

    @InjectMocks
    UtxoTransactionBuilderImpl utxoTransactionBuilder;

    ProtocolParams protocolParams;

    ObjectMapper objectMapper = new ObjectMapper();

    String dataFile = "utxos.json";

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        utxoTransactionBuilder = new UtxoTransactionBuilderImpl(new DefaultUtxoSelectionStrategyImpl(utxoSupplier));
        protocolParams = ProtocolParams.builder()
                .coinsPerUtxoWord("34482")
                .coinsPerUtxoSize("4310")
                .build();
    }

    private List<Utxo> loadUtxos(String key) throws IOException {
        TypeReference<HashMap<String, List<Utxo>>> typeRef
                = new TypeReference<HashMap<String, List<Utxo>>>() {};
        Map<String, List<Utxo>> map = objectMapper.readValue(this.getClass().getClassLoader().getResourceAsStream(dataFile), typeRef);
        return map.getOrDefault(key, Collections.emptyList());
    }

    @Test
    public void testGetUtxosWithLovelaceWillReturnCorrectUtxos() throws ApiException, IOException, AddressExcepion {
        String address = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<Utxo> utxos = loadUtxos(LIST_2);
        given(utxoSupplier.getPage(anyString(), anyInt(), anyInt(), any())).willReturn(utxos);

        List<Utxo> utxoList = utxoTransactionBuilder.getUtxos(address, "lovelace", BigInteger.valueOf(500000000));

        verify(utxoSupplier, times(1)).getPage(anyString(), anyInt(), anyInt(), any());

        assertThat(utxoList, hasSize(2));
        assertThat(utxoList.get(0).getAmount().get(0).getQuantity(), is(BigInteger.valueOf(1407406)));
        assertThat(utxoList.get(1).getAmount().get(0).getQuantity(), is(BigInteger.valueOf(995770000)));
    }

    @Test
    public void testGetUtxosWithAssetWillReturnCorrectUtxos() throws ApiException, IOException, AddressExcepion {
        String address = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String unit = "329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e";

        List<Utxo> utxos = loadUtxos(LIST_2);
        given(utxoSupplier.getPage(anyString(), anyInt(), anyInt(), any())).willReturn(utxos);

        List<Utxo> utxoList = utxoTransactionBuilder.getUtxos(address, unit, BigInteger.valueOf(400000000));

        assertThat(utxoList, hasSize(2));
        assertThat(utxoList.get(1).getAmount().get(0).getQuantity(), is(BigInteger.valueOf(999817955)));
        assertThat(utxoList.get(1).getAmount().get(1).getQuantity(), is(BigInteger.valueOf(5000000000L)));

    }

    @Test
    public void testBuildTransactionWithLovelaceWillReturnTrasaction() throws ApiException, IOException, AddressExcepion {
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<Utxo> utxos = loadUtxos(LIST_2);
        given(utxoSupplier.getPage(anyString(), anyInt(), anyInt(), any())).willReturn(utxos);

        Account sender = new Account(Networks.testnet());
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .sender(sender)
                .fee(BigInteger.valueOf(120000))
                .unit(CardanoConstants.LOVELACE)
                .amount(BigInteger.valueOf(500000000))
                .receiver(receiver)
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        Transaction transaction = utxoTransactionBuilder.buildTransaction(Arrays.asList(paymentTransaction), detailsParams, null, protocolParams);

        assertThat(transaction.getBody().getInputs(), hasSize(2));
        assertThat(transaction.getBody().getInputs().get(0).getTransactionId(), is("735262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9"));
        assertThat(transaction.getBody().getInputs().get(1).getTransactionId(), is("1d98fc1aad22af10eec3cfc924d9edb4dcea6181e1d33895588c4d3c60d2af8b"));

        assertThat(transaction.getBody().getOutputs().get(0).getAddress(), is(receiver));
        assertThat(transaction.getBody().getOutputs().get(0).getValue().getCoin(), is(BigInteger.valueOf(500000000L)));

        assertThat(transaction.getBody().getOutputs().get(1).getAddress(), is(sender.baseAddress()));
        assertThat(transaction.getBody().getOutputs().get(1).getValue().getCoin(), is(BigInteger.valueOf(497057406)));
    }

    @Test
    public void testBuildTransactionWithMultiAssetWillReturnTrasaction() throws ApiException, IOException, AddressExcepion, CborSerializationException {
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String unit = "329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e";

        List<Utxo> utxos = loadUtxos(LIST_3);
        given(utxoSupplier.getPage(anyString(), anyInt(), anyInt(), any())).willReturn(utxos);

        Account sender = new Account(Networks.testnet());
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .sender(sender)
                .fee(BigInteger.valueOf(120000))
                .unit(unit)
                .amount(BigInteger.valueOf(20000))
                .receiver(receiver)
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();
        Transaction transaction = utxoTransactionBuilder.buildTransaction(Arrays.asList(paymentTransaction), detailsParams, null, protocolParams);

        System.out.println(JsonUtil.getPrettyJson(transaction));

        System.out.println(transaction.serializeToHex());

        assertThat(transaction.getBody().getInputs(), hasSize(1));
        assertThat(transaction.getBody().getInputs().get(0).getTransactionId(), is("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c"));

        assertThat(transaction.getBody().getOutputs().get(0).getAddress(), is(receiver));
        assertThat(transaction.getBody().getOutputs().get(0).getValue().getCoin(), is(greaterThan(ONE_ADA)));
        assertThat(transaction.getBody().getOutputs().get(0).getValue().getMultiAssets().get(0).getAssets().get(0).getValue(), is(BigInteger.valueOf(20000)));

        assertThat(transaction.getBody().getOutputs().get(1).getAddress(), is(sender.baseAddress()));
        assertThat(transaction.getBody().getOutputs().get(1).getValue().getMultiAssets(), hasSize(2));
        assertThat(transaction.getBody().getOutputs().get(1).getValue().getMultiAssets().get(0).getAssets(), hasSize(2));
        assertThat(transaction.getBody().getOutputs().get(1).getValue().getMultiAssets().get(1).getAssets(), hasSize(1));
    }

    @Test
    public void testBuildTransactionWithMultiAssetAndMultipleUtxosWillReturnTrasaction() throws ApiException, IOException, AddressExcepion, CborSerializationException {
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String unit = "6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7";

        List<Utxo> utxos = loadUtxos(LIST_3);
        given(utxoSupplier.getPage(anyString(), anyInt(), anyInt(), any())).willReturn(utxos);

        Account sender = new Account(Networks.testnet());
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .sender(sender)
                .fee(BigInteger.valueOf(12000))
                .unit(unit)
                .amount(BigInteger.valueOf(80000))
                .receiver(receiver)
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        Transaction transaction = utxoTransactionBuilder.buildTransaction(Arrays.asList(paymentTransaction), detailsParams, null, protocolParams);

        System.out.println(JsonUtil.getPrettyJson(transaction));

        System.out.println(transaction.serializeToHex());

        assertThat(transaction.getBody().getInputs(), hasSize(2));
        assertThat(transaction.getBody().getInputs().get(0).getTransactionId(), is("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c"));
        assertThat(transaction.getBody().getInputs().get(1).getTransactionId(), is("3aaaaa41761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c"));

        assertThat(transaction.getBody().getOutputs().get(0).getAddress(), is(receiver));
        assertThat(transaction.getBody().getOutputs().get(0).getValue().getCoin(), is(greaterThan(ONE_ADA)));
        assertThat(transaction.getBody().getOutputs().get(0).getValue().getMultiAssets().get(0).getAssets().get(0).getValue(), is(BigInteger.valueOf(80000)));

        assertThat(transaction.getBody().getOutputs().get(1).getAddress(), is(sender.baseAddress()));
        assertThat(transaction.getBody().getOutputs().get(1).getValue().getMultiAssets(), hasSize(2));

        assertThat(transaction.getBody().getOutputs().get(1).getValue().getMultiAssets().get(1).getAssets(), hasSize(1));
        assertThat(transaction.getBody().getOutputs().get(1).getValue().getMultiAssets().get(1).getAssets().get(0).getValue(), is(BigInteger.valueOf(37000)));

        assertThat(transaction.getBody().getOutputs().get(1).getValue().getMultiAssets().get(0).getAssets(), hasSize(2));

        var expectedBaseAmount = BigInteger.ZERO;
        // add utxo-1 base amount
        expectedBaseAmount = expectedBaseAmount.add(utxos.get(1).getAmount().get(0).getQuantity());
        // subtract min amount used in output to received
        expectedBaseAmount = expectedBaseAmount.subtract(transaction.getBody().getOutputs().get(0).getValue().getCoin());
        // add utxo-2 base amount
        expectedBaseAmount = expectedBaseAmount.add(utxos.get(2).getAmount().get(0).getQuantity());
        // subtract fee
        expectedBaseAmount = expectedBaseAmount.subtract(transaction.getBody().getFee());
        Assertions.assertEquals(expectedBaseAmount, transaction.getBody().getOutputs().get(1).getValue().getCoin());
        Assertions.assertEquals(new BigInteger("985022195"), transaction.getBody().getOutputs().get(1).getValue().getCoin());
    }

    @Test
    public void testBuildTransactionWithMultiAssetAndNotSufficientLovelaceWillReturnTrasaction()
            throws ApiException, IOException, AddressExcepion, CborSerializationException {
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String unit = "6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7";

        List<Utxo> utxos = loadUtxos(LIST_4);
        given(utxoSupplier.getPage(anyString(), anyInt(), anyInt(), any())).willReturn(utxos);

        Account sender = new Account(Networks.testnet());
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .sender(sender)
                .fee(BigInteger.valueOf(23000))
                .unit(unit)
                .amount(BigInteger.valueOf(10000))
                .receiver(receiver)
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        Transaction transaction = utxoTransactionBuilder.buildTransaction(Arrays.asList(paymentTransaction), detailsParams, null, protocolParams);

        System.out.println(JsonUtil.getPrettyJson(transaction));

        System.out.println(transaction.serializeToHex());

        assertThat(transaction.getBody().getInputs(), hasSize(2));
        assertThat(transaction.getBody().getInputs().get(0).getTransactionId(), is("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c"));
        assertThat(transaction.getBody().getInputs().get(1).getTransactionId(), is("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85"));

        assertThat(transaction.getBody().getOutputs().get(0).getAddress(), is(receiver));
        assertThat(transaction.getBody().getOutputs().get(0).getValue().getCoin(), is(greaterThan(ONE_ADA)));
        assertThat(transaction.getBody().getOutputs().get(0).getValue().getMultiAssets().get(0).getAssets().get(0).getValue(), is(BigInteger.valueOf(10000)));

        assertThat(transaction.getBody().getOutputs().get(1).getAddress(), is(sender.baseAddress()));
        assertThat(transaction.getBody().getOutputs().get(1).getValue().getMultiAssets(), hasSize(3));

        assertThat(transaction.getBody().getOutputs().get(1).getValue().getMultiAssets().get(0).getAssets(), hasSize(2));
        assertThat(transaction.getBody().getOutputs().get(1).getValue().getCoin(), is(BigInteger.valueOf(975612202)));

        assertThat(transaction.getBody().getOutputs().get(1).getValue().getMultiAssets().get(1).getAssets(), hasSize(1));
        assertThat(transaction.getBody().getOutputs().get(1).getValue().getMultiAssets().get(2).getAssets(), hasSize(1));
    }

    @Test
    public void testBuildTransactionWithMultiplePaymentsAndMultiAssetAndMultipleUtxosWillReturnTrasaction() throws ApiException, IOException, AddressExcepion, CborSerializationException {
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String receiver2 = "addr_test1vqx5tjj902x5ck0rd2nev0y9j9a27axudjlxag68aj8j9asl0cy3s";
        String unit = "6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7";
        String unit2 = "329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e";

        List<Utxo> utxos = loadUtxos(LIST_3);
        given(utxoSupplier.getPage(anyString(), anyInt(), anyInt(), any())).willReturn(utxos);

        Account sender = new Account(Networks.testnet());
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .sender(sender)
                .fee(BigInteger.valueOf(12000))
                .unit(unit)
                .amount(BigInteger.valueOf(80000))
                .receiver(receiver)
                .build();

        Account sender2 = new Account(Networks.testnet());
        PaymentTransaction paymentTransaction2 = PaymentTransaction.builder()
                .sender(sender2)
                .fee(BigInteger.valueOf(13000))
                .unit(unit2)
                .amount(BigInteger.valueOf(305))
                .receiver(receiver2)
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        Transaction transaction = utxoTransactionBuilder.buildTransaction(Arrays.asList(paymentTransaction, paymentTransaction2), detailsParams, null, protocolParams);

        System.out.println(JsonUtil.getPrettyJson(transaction));

        System.out.println(transaction.serializeToHex());

        assertThat(transaction.getBody().getInputs(), hasSize(3));
        assertThat(transaction.getBody().getOutputs(), hasSize(4));
    }

    @Test
    public void testBuildTransactionWithMultiAssetAndNotSufficientLovelaceWillReturnTrasactionWithAdditionalUtxos()
            throws ApiException, IOException, AddressExcepion, CborSerializationException {
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String unit = "777777d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7";

        List<Utxo> utxos = loadUtxos(LIST_5);
        given(utxoSupplier.getPage(anyString(), anyInt(), anyInt(), any())).willReturn(utxos);

        Account sender = new Account(Networks.testnet());
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .sender(sender)
                .fee(BigInteger.valueOf(12000))
                .unit(unit)
                .amount(BigInteger.valueOf(10000))
                .receiver(receiver)
                .build();

        PaymentTransaction paymentTransaction2 = PaymentTransaction.builder()
                .sender(sender)
                .fee(BigInteger.valueOf(12000))
                .unit(LOVELACE)
                .amount(BigInteger.valueOf(3000000))
                .receiver(receiver)
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        Transaction transaction = utxoTransactionBuilder.buildTransaction(Arrays.asList(paymentTransaction, paymentTransaction2), detailsParams, null, protocolParams);

        System.out.println(JsonUtil.getPrettyJson(transaction));

        System.out.println(transaction.serializeToHex());

        assertThat(transaction.getBody().getInputs(), hasSize(3));
        assertThat(transaction.getBody().getInputs().get(0).getTransactionId(), is("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85"));
        assertThat(transaction.getBody().getInputs().get(1).getTransactionId(), is("bbbbbb341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85"));
        assertThat(transaction.getBody().getInputs().get(2).getTransactionId(), is("aaaaaa341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85"));

        assertThat(transaction.getBody().getOutputs(), hasSize(2));
        assertThat(transaction.getBody().getOutputs().get(0).getAddress(), is(receiver));
        assertThat(transaction.getBody().getOutputs().get(0).getValue().getCoin(), is(greaterThan(ONE_ADA)));
        assertThat(transaction.getBody().getOutputs().get(0).getValue().getMultiAssets().get(0).getAssets().get(0).getValue(), is(BigInteger.valueOf(10000)));

        assertThat(transaction.getBody().getOutputs().get(1).getAddress(), is(sender.baseAddress()));
        assertThat(transaction.getBody().getOutputs().get(1).getValue().getCoin(), is(greaterThan(ONE_ADA)));
    }

    @Test
    public void testBuildTransactionWithMultiAssetAndNotSufficientBalanceWillThrowInsufficientBalance()
            throws ApiException, IOException, AddressExcepion, CborSerializationException {
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String unit = "777777d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7";

        List<Utxo> utxos = loadUtxos(LIST_6);
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(utxos);
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(1), any())).willReturn(utxos);
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(2), any())).willReturn(Collections.emptyList());

        Account sender = new Account(Networks.testnet());
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .sender(sender)
                .fee(BigInteger.valueOf(12000))
                .unit(unit)
                .amount(BigInteger.valueOf(10000))
                .receiver(receiver)
                .build();

        PaymentTransaction paymentTransaction2 = PaymentTransaction.builder()
                .sender(sender)
                .fee(BigInteger.valueOf(12000))
                .unit(LOVELACE)
                .amount(BigInteger.valueOf(3700000))
                .receiver(receiver)
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        Assertions.assertThrows(InsufficientBalanceException.class, () -> {
            Transaction transaction = utxoTransactionBuilder.buildTransaction(Arrays.asList(paymentTransaction, paymentTransaction2), detailsParams, null, protocolParams);
            System.out.println(JsonUtil.getPrettyJson(transaction));
        });

    }

    @Test
    public void testBuildTokenMintTransactionWithMultiAssetAndNotSufficientLovelaceWillReturnTrasactionWithAdditionalUtxos()
            throws ApiException, IOException, AddressExcepion, CborSerializationException {
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String unit = "777777d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7";

        List<Utxo> utxos = loadUtxos(LIST_5);
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(Arrays.asList(utxos.get(0)));
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(1), any())).willReturn(Arrays.asList(utxos.get(1)));
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(2), any())).willReturn(Arrays.asList(utxos.get(2)));

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId("b9bd3fb4511908402fbef848eece773bb44c867c25ac8c08d9ec3313");
        Asset asset = new Asset("selftoken1", BigInteger.valueOf(250000));
        multiAsset.getAssets().add(asset);

        Account sender = new Account(Networks.testnet());
        MintTransaction mintTransaction = MintTransaction.builder()
                .sender(sender)
                .fee(BigInteger.valueOf(1750000))
                .mintAssets(Arrays.asList(multiAsset))
                .receiver(receiver)
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        Transaction transaction = utxoTransactionBuilder.buildMintTokenTransaction(mintTransaction, detailsParams, null, protocolParams);

        System.out.println(JsonUtil.getPrettyJson(transaction));

        System.out.println(transaction.serializeToHex());

        assertThat(transaction.getBody().getInputs(), hasSize(3));
        assertThat(transaction.getBody().getInputs().get(0).getTransactionId(), is("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85"));
        assertThat(transaction.getBody().getInputs().get(1).getTransactionId(), is("bbbbbb341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85"));
        assertThat(transaction.getBody().getInputs().get(2).getTransactionId(), is("aaaaaa341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85"));

        assertThat(transaction.getBody().getOutputs(), hasSize(2));


        Assertions.assertEquals(sender.baseAddress(), transaction.getBody().getOutputs().get(0).getAddress());
        Assertions.assertEquals(6, transaction.getBody().getOutputs().get(0).getValue().getMultiAssets().size());

        Assertions.assertEquals(receiver, transaction.getBody().getOutputs().get(1).getAddress());
        Assertions.assertTrue(transaction.getBody().getOutputs().get(1).getValue().getCoin().compareTo(CardanoConstants.ONE_ADA) > 0);
        Assertions.assertEquals(BigInteger.valueOf(250000), transaction.getBody().getOutputs().get(1).getValue().getMultiAssets().get(0).getAssets().get(0).getValue());
        Assertions.assertEquals("selftoken1", transaction.getBody().getOutputs().get(1).getValue().getMultiAssets().get(0).getAssets().get(0).getName());

        Assertions.assertEquals(multiAsset, transaction.getBody().getMint().get(0));

        var expectedBaseAmount = BigInteger.ZERO;
        // add utxo-0 base amount
        expectedBaseAmount = expectedBaseAmount.add(utxos.get(0).getAmount().get(0).getQuantity());
        // subtract fee
        expectedBaseAmount = expectedBaseAmount.subtract(mintTransaction.getFee());
        // subtract output 2 base amount
        expectedBaseAmount = expectedBaseAmount.subtract(transaction.getBody().getOutputs().get(1).getValue().getCoin());
        // add utxo-1 base amount (since base amount is currently less than allowed)
        expectedBaseAmount = expectedBaseAmount.add(utxos.get(1).getAmount().get(0).getQuantity());
        // add utxo-2 base amount (since base amount is currently still less than allowed)
        expectedBaseAmount = expectedBaseAmount.add(utxos.get(2).getAmount().get(0).getQuantity());

        Assertions.assertEquals(expectedBaseAmount, transaction.getBody().getOutputs().get(0).getValue().getCoin());
        Assertions.assertEquals(BigInteger.valueOf(1980466), transaction.getBody().getOutputs().get(0).getValue().getCoin());
    }

    @Test
    public void testBuildTokenMintTransactionWithMultiAssetAndNotSufficientLovelaceWillThrowInsufficientBalance()
            throws ApiException, IOException, AddressExcepion, CborSerializationException {
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String unit = "777777d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7";

        List<Utxo> utxos = loadUtxos(LIST_5);
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(Arrays.asList(utxos.get(0)));
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(1), any())).willReturn(Arrays.asList(utxos.get(1)));
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(2), any())).willReturn(Arrays.asList(utxos.get(2)));
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(3), any())).willReturn(Arrays.asList(utxos.get(3)));
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(4), any())).willReturn(Collections.emptyList());

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId("b9bd3fb4511908402fbef848eece773bb44c867c25ac8c08d9ec3313");
        Asset asset = new Asset("selftoken1", BigInteger.valueOf(250000));
        multiAsset.getAssets().add(asset);

        Account sender = new Account(Networks.testnet());
        MintTransaction mintTransaction = MintTransaction.builder()
                .sender(sender)
                .fee(BigInteger.valueOf(100750000)) //Add higher fee
                .mintAssets(Arrays.asList(multiAsset))
                .receiver(receiver)
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        Assertions.assertThrows(InsufficientBalanceException.class, () -> {
            Transaction transaction = utxoTransactionBuilder.buildMintTokenTransaction(mintTransaction, detailsParams, null, protocolParams);
            System.out.println(transaction);
        });
    }

    @Test
    public void testBuildTransactionWithUtxosProvided() throws ApiException, IOException, AddressExcepion {
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<Utxo> utxos = loadUtxos(LIST_2);

        Account sender = new Account(Networks.testnet());
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .sender(sender)
                .fee(BigInteger.valueOf(120000))
                .unit(CardanoConstants.LOVELACE)
                .amount(BigInteger.valueOf(500000000))
                .receiver(receiver)
                .utxosToInclude(Arrays.asList(utxos.get(1), utxos.get(4)))
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        Transaction transaction = utxoTransactionBuilder.buildTransaction(Arrays.asList(paymentTransaction), detailsParams, null, protocolParams);

        assertThat(transaction.getBody().getInputs(), hasSize(2));
        assertThat(transaction.getBody().getInputs().get(0).getTransactionId(), is("1d98fc1aad22af10eec3cfc924d9edb4dcea6181e1d33895588c4d3c60d2af8b"));
        assertThat(transaction.getBody().getInputs().get(1).getTransactionId(), is("e755448d0d17651ff308c2a1d218fbbee5f290924482d85b2bb691576aee5105"));

        assertThat(transaction.getBody().getOutputs().get(0).getAddress(), is(receiver));
        assertThat(transaction.getBody().getOutputs().get(0).getValue().getCoin(), is(BigInteger.valueOf(500000000L)));

        assertThat(transaction.getBody().getOutputs().get(1).getAddress(), is(sender.baseAddress()));
        assertThat(transaction.getBody().getOutputs().get(1).getValue().getCoin(), is(BigInteger.valueOf(1495467955)));

        verify(utxoSupplier, never()).getPage(anyString(), anyInt(), anyInt(), any());
    }

    @Test
    public void testBuildTransactionWithUtxosProvidedButRequiredAdditionalUtxos() throws ApiException, IOException, AddressExcepion {
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<Utxo> utxos = loadUtxos(LIST_2);
        given(utxoSupplier.getPage(anyString(), anyInt(), anyInt(), any())).willReturn(utxos);

        Account sender = new Account(Networks.testnet());
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .sender(sender)
                .fee(BigInteger.valueOf(120000))
                .unit(CardanoConstants.LOVELACE)
                .amount(BigInteger.valueOf(3000000000L))
                .receiver(receiver)
                .utxosToInclude(Arrays.asList(utxos.get(1), utxos.get(4)))
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        Transaction transaction = utxoTransactionBuilder.buildTransaction(Arrays.asList(paymentTransaction), detailsParams, null, protocolParams);

        assertThat(transaction.getBody().getInputs(), hasSize(5));
        assertThat(transaction.getBody().getInputs().get(0).getTransactionId(), is("1d98fc1aad22af10eec3cfc924d9edb4dcea6181e1d33895588c4d3c60d2af8b"));
        assertThat(transaction.getBody().getInputs().get(1).getTransactionId(), is("e755448d0d17651ff308c2a1d218fbbee5f290924482d85b2bb691576aee5105"));

        assertThat(transaction.getBody().getOutputs().get(0).getAddress(), is(receiver));
        assertThat(transaction.getBody().getOutputs().get(0).getValue().getCoin(), is(BigInteger.valueOf(3000000000L)));

        assertThat(transaction.getBody().getOutputs().get(1).getAddress(), is(sender.baseAddress()));
        verify(utxoSupplier, atLeastOnce()).getPage(anyString(), anyInt(), anyInt(), any());
    }

    @Test
    public void testBuildTokenMintTransactionWithUtxosProvided()
            throws ApiException, IOException, AddressExcepion, CborSerializationException {
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<Utxo> utxos = loadUtxos(LIST_2);

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId("b9bd3fb4511908402fbef848eece773bb44c867c25ac8c08d9ec3313");
        Asset asset = new Asset("selftoken1", BigInteger.valueOf(250000));
        multiAsset.getAssets().add(asset);

        Account sender = new Account(Networks.testnet());
        MintTransaction mintTransaction = MintTransaction.builder()
                .sender(sender)
                .fee(BigInteger.valueOf(1750000))
                .mintAssets(Arrays.asList(multiAsset))
                .receiver(receiver)
                .utxosToInclude(Arrays.asList(utxos.get(1), utxos.get(4)))
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        Transaction transaction = utxoTransactionBuilder.buildMintTokenTransaction(mintTransaction, detailsParams, null, protocolParams);

        System.out.println(JsonUtil.getPrettyJson(transaction));

        System.out.println(transaction.serializeToHex());

        assertThat(transaction.getBody().getInputs(), hasSize(2));
        assertThat(transaction.getBody().getInputs().get(0).getTransactionId(), is("1d98fc1aad22af10eec3cfc924d9edb4dcea6181e1d33895588c4d3c60d2af8b"));
        assertThat(transaction.getBody().getInputs().get(1).getTransactionId(), is("e755448d0d17651ff308c2a1d218fbbee5f290924482d85b2bb691576aee5105"));

        assertThat(transaction.getBody().getOutputs(), hasSize(2));

        verify(utxoSupplier, never()).getPage(anyString(), anyInt(), anyInt(), any());
    }

    @Test
    public void testBuildTokenMintTransactionWithUtxosProvidedButAdditionalUtxosRequired()
            throws ApiException, IOException, CborSerializationException {
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<Utxo> utxos = loadUtxos(LIST_2);
        given(utxoSupplier.getPage(anyString(), anyInt(), anyInt(), any())).willReturn(utxos);

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId("b9bd3fb4511908402fbef848eece773bb44c867c25ac8c08d9ec3313");
        Asset asset = new Asset("selftoken1", BigInteger.valueOf(250000));
        multiAsset.getAssets().add(asset);

        Account sender = new Account(Networks.testnet());
        MintTransaction mintTransaction = MintTransaction.builder()
                .sender(sender)
                .fee(BigInteger.valueOf(1000000000)) //High fee to force include of other Utxos
                .mintAssets(Arrays.asList(multiAsset))
                .receiver(receiver)
                .utxosToInclude(Arrays.asList(utxos.get(1)))
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        Transaction transaction = utxoTransactionBuilder.buildMintTokenTransaction(mintTransaction, detailsParams, null, protocolParams);

        System.out.println(JsonUtil.getPrettyJson(transaction));

        System.out.println(transaction.serializeToHex());

        assertThat(transaction.getBody().getInputs(), hasSize(3));
        assertThat(transaction.getBody().getInputs().get(0).getTransactionId(), is("1d98fc1aad22af10eec3cfc924d9edb4dcea6181e1d33895588c4d3c60d2af8b"));
        assertThat(transaction.getBody().getInputs().get(1).getTransactionId(), is("735262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9"));
        assertThat(transaction.getBody().getInputs().get(2).getTransactionId(), is("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85"));

        assertThat(transaction.getBody().getOutputs(), hasSize(2));

        verify(utxoSupplier, atLeast(1)).getPage(anyString(), anyInt(), anyInt(), any());
    }

    @Test
    public void testBuildTransaction_whenNotEnoughUtxosLeft() throws ApiException, IOException, AddressExcepion {
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<Utxo> utxos = loadUtxos(LIST_7);
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(utxos);

        Account sender = new Account(Networks.testnet());
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .sender(sender)
                .unit(CardanoConstants.LOVELACE)
                .amount(BigInteger.valueOf(11000000))
                .fee(BigInteger.valueOf(17000))
                .receiver(receiver)
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        BigInteger balanceAmt = BigInteger.valueOf(10000000 + 1452846); //Utxo amount
        BigInteger newAmount = balanceAmt.subtract(paymentTransaction.getFee());

        paymentTransaction.setAmount(newAmount);

        //TODO -- Now not enough exception is thrown instead of continuing silently
//        Assertions.assertThrows(InsufficientBalanceException.class, () -> {
        Transaction transaction = utxoTransactionBuilder.buildTransaction(Arrays.asList(paymentTransaction), detailsParams, null, protocolParams);
//        });

        assertThat(transaction.getBody().getInputs(), hasSize(2));
        assertThat(transaction.getBody().getOutputs(), hasSize(1));
        assertThat(transaction.getBody().getInputs().get(0).getTransactionId(), is("d60669420bc15d3f359b74f5177cd4035325c22f7a67cf96d466472acf145ecb"));
        assertThat(transaction.getBody().getInputs().get(1).getTransactionId(), is("f3c464be15a5e29a1a6d322c5cd040c87075d1cfc89d4b397568d14c0ba53cd9"));

        assertThat(transaction.getBody().getOutputs().get(0).getValue().getCoin(), is(newAmount));
        assertThat(transaction.getBody().getOutputs().get(0).getValue().getMultiAssets(), is(empty()));
    }

    @Test
    public void testBuildTransaction_whenNotEnoughUtxosLeftWithNativeToken() throws ApiException, IOException, AddressExcepion {
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<Utxo> utxos = loadUtxos(LIST_8);
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(utxos);
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(1), any())).willReturn(Collections.EMPTY_LIST);

        Account sender = new Account(Networks.testnet());
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .sender(sender)
                .unit(CardanoConstants.LOVELACE)
                .amount(BigInteger.valueOf(11000000))
                .fee(BigInteger.valueOf(17000))
                .receiver(receiver)
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        BigInteger balanceAmt = BigInteger.valueOf(10000000 + 1452846); //Utxo amount
        BigInteger newAmount = balanceAmt.subtract(paymentTransaction.getFee());

        paymentTransaction.setAmount(newAmount);

        //TODO -- Now exception is thrown in case not enough utxo
        Assertions.assertThrows(InsufficientBalanceException.class, () -> {
            Transaction transaction = utxoTransactionBuilder.buildTransaction(Arrays.asList(paymentTransaction), detailsParams, null, protocolParams);
        });

        /*assertThat(transaction.getBody().getInputs(), hasSize(2));
        assertThat(transaction.getBody().getOutputs(), hasSize(2));
        assertThat(transaction.getBody().getInputs().get(0).getTransactionId(), is("d60669420bc15d3f359b74f5177cd4035325c22f7a67cf96d466472acf145ecb"));
        assertThat(transaction.getBody().getInputs().get(1).getTransactionId(), is("f3c464be15a5e29a1a6d322c5cd040c87075d1cfc89d4b397568d14c0ba53cd9"));

        assertThat(transaction.getBody().getOutputs().get(0).getValue().getCoin(), is(newAmount));
        assertThat(transaction.getBody().getOutputs().get(1).getValue().getCoin(), is(BigInteger.ZERO));
        assertThat(transaction.getBody().getOutputs().get(1).getValue().getMultiAssets().size(), is(1));*/
    }

    @Test
    public void testE2ESingleAsset() throws ApiException, IOException{
        String senderMnemonic2 = "mixture peasant wood unhappy usage hero great elder emotion picnic talent fantasy program clean patch wheel drip disorder bullet cushion bulk infant balance address";
        Account sender2 = new Account(Networks.testnet(), senderMnemonic2);

        List<Utxo> utxosSender2 = loadUtxos(LIST_10);
        given(utxoSupplier.getPage(eq(sender2.baseAddress()), anyInt(), eq(0), any())).willReturn(utxosSender2);

        String receiver3 = "addr_test1qrp6x6aq2m28xhvxhqzufl0ff7x8gmzjejssrk29mx0q829dsty3hzmrl2k8jhwzghgxuzfjatgxlhg9wtl6ecv0v3cqf92rnh";

        Map<String, BigInteger> requestedAmounts = Map.of(LOVELACE, BigInteger.valueOf(3110000));

        PaymentTransaction paymentTransaction3 =
                PaymentTransaction.builder()
                        .sender(sender2)
                        .receiver(receiver3)
                        .amount(BigInteger.valueOf(3110000))
                        .unit(LOVELACE)
                        .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        Transaction transaction = utxoTransactionBuilder.buildTransaction(Arrays.asList(paymentTransaction3), detailsParams, null, protocolParams);

        System.out.println(JsonUtil.getPrettyJson(transaction));

        // validate input matches output
        Map<String, BigInteger> inputs = getInputAmounts(utxosSender2, transaction);
        Map<String, BigInteger> outputs = getOutputAmounts(transaction);
        Assertions.assertEquals(inputs, outputs);

        // verify requestedAmounts
        for(Map.Entry<String, BigInteger> entry : requestedAmounts.entrySet()){
            var output = outputs.get(entry.getKey());
            var minAmountRequired = entry.getValue();
            Assertions.assertTrue(output.compareTo(minAmountRequired) >= 0);
        }
    }

    @Test
    public void testE2EMultiAsset() throws ApiException, IOException{
        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);

        //addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82
        String senderMnemonic2 = "mixture peasant wood unhappy usage hero great elder emotion picnic talent fantasy program clean patch wheel drip disorder bullet cushion bulk infant balance address";
        Account sender2 = new Account(Networks.testnet(), senderMnemonic2);

        List<Utxo> utxosSender1 = loadUtxos(LIST_9);
        given(utxoSupplier.getPage(eq(sender.baseAddress()), anyInt(), eq(0), any())).willReturn(utxosSender1);

        List<Utxo> utxosSender2 = loadUtxos(LIST_10);
        given(utxoSupplier.getPage(eq(sender2.baseAddress()), anyInt(), eq(0), any())).willReturn(utxosSender2);

        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        String receiver2 = "addr_test1qz7r5eu2jg0hx470mmf79vpgueaggh22pmayry8xrre5grtpyy9s8u2heru58a4r68wysmdw9v40zznttmwrg0a6v9tq36pjak";

        String receiver3 = "addr_test1qrp6x6aq2m28xhvxhqzufl0ff7x8gmzjejssrk29mx0q829dsty3hzmrl2k8jhwzghgxuzfjatgxlhg9wtl6ecv0v3cqf92rnh";

        BigInteger fee = BigInteger.valueOf(248089);

        Map<String, BigInteger> requestedAmounts = Map.of("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e", BigInteger.valueOf(13 + 14),
                LOVELACE, BigInteger.valueOf(3110000).add(fee));

        PaymentTransaction paymentTransaction1 =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .amount(BigInteger.valueOf(14))
                        .fee(fee)
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

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        Transaction transaction = utxoTransactionBuilder.buildTransaction(Arrays.asList(paymentTransaction1, paymentTransaction2, paymentTransaction3), detailsParams, null, protocolParams);

        // validate input matches output
        Map<String, BigInteger> inputs = getInputAmounts(Stream.concat(utxosSender1.stream(), utxosSender2.stream()).collect(Collectors.toList()), transaction);
        Map<String, BigInteger> outputs = getOutputAmounts(transaction);

        Assertions.assertEquals(inputs, outputs);

        // verify requestedAmounts
        for(Map.Entry<String, BigInteger> entry : requestedAmounts.entrySet()){
            var output = outputs.get(entry.getKey());
            var minAmountRequired = entry.getValue();
            Assertions.assertTrue(output.compareTo(minAmountRequired) >= 0);
        }
    }

    @Test
    void testSendAll() throws Exception{
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        Account sender = new Account(Networks.testnet());

        List<Utxo> utxos = Collections.singletonList(new Utxo("496760b59ba36169bf6a62b09880824896b8e0044a4893f9649b6604741a89ed", 3, sender.getBaseAddress().getAddress(), Collections.singletonList(new Amount(LOVELACE, ADAConversionUtil.adaToLovelace(new BigDecimal("1.5")))), null, null, null));
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(utxos);


        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .sender(sender)
                .unit(CardanoConstants.LOVELACE)
                .amount(ADAConversionUtil.adaToLovelace(new BigDecimal("1.5").subtract(new BigDecimal("0.168317"))))
                .fee(ADAConversionUtil.adaToLovelace(new BigDecimal("0.168317")))
                .receiver(receiver)
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        Transaction transaction = utxoTransactionBuilder.buildTransaction(Arrays.asList(paymentTransaction), detailsParams, null, protocolParams);

        assertThat(transaction.getBody().getInputs(), hasSize(1));
        assertThat(transaction.getBody().getOutputs(), hasSize(1));
        assertThat(transaction.getBody().getInputs().get(0).getTransactionId(), is("496760b59ba36169bf6a62b09880824896b8e0044a4893f9649b6604741a89ed"));
        assertThat(transaction.getBody().getOutputs().get(0).getValue().getCoin(), is(ADAConversionUtil.adaToLovelace(new BigDecimal("1.5").subtract(new BigDecimal("0.168317")))));
    }

    @Test
    void testSendAllMultiAsset() throws Exception{
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        Account sender = new Account(Networks.testnet());

        String unit = "777777d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7";

        List<Utxo> utxos = Collections.singletonList(new Utxo("496760b59ba36169bf6a62b09880824896b8e0044a4893f9649b6604741a89ed", 3, sender.getBaseAddress().getAddress(), Arrays.asList(new Amount(unit, BigInteger.valueOf(1000)), new Amount(LOVELACE, ADAConversionUtil.adaToLovelace(new BigDecimal("3")))), null, null, null));
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(utxos);

        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .sender(sender)
                .unit(unit)
                .amount(BigInteger.valueOf(1000))
                .fee(ADAConversionUtil.adaToLovelace(new BigDecimal("0.168317")))
                .receiver(receiver)
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        Transaction transaction = utxoTransactionBuilder.buildTransaction(Arrays.asList(paymentTransaction), detailsParams, null, protocolParams);

        assertThat(transaction.getBody().getInputs(), hasSize(1));
        assertThat(transaction.getBody().getOutputs(), hasSize(2));
        assertThat(transaction.getBody().getInputs().get(0).getTransactionId(), is("496760b59ba36169bf6a62b09880824896b8e0044a4893f9649b6604741a89ed"));
        assertThat(transaction.getBody().getOutputs().get(0).getValue().getCoin(), is(greaterThan(ONE_ADA)));
        assertThat(transaction.getBody().getOutputs().get(0).getValue().getMultiAssets().get(0).getAssets().get(0).getValue(), is(BigInteger.valueOf(1000)));
    }

    @Test
    void testSendAllMultiAssetExactAmountSameReceiver() throws Exception{
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        Account sender = new Account(Networks.testnet());

        String unit = "777777d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7";

        List<Utxo> utxos = Collections.singletonList(new Utxo("496760b59ba36169bf6a62b09880824896b8e0044a4893f9649b6604741a89ed", 3, sender.getBaseAddress().getAddress(), Arrays.asList(new Amount(unit, BigInteger.valueOf(1000)), new Amount(LOVELACE, ADAConversionUtil.adaToLovelace(new BigDecimal("1.5")))), null, null, null));
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(utxos);


        PaymentTransaction paymentTransaction1 = PaymentTransaction.builder()
                .sender(sender)
                .unit(unit)
                .amount(BigInteger.valueOf(1000))
                .fee(ADAConversionUtil.adaToLovelace(new BigDecimal("0.168317")))
                .receiver(receiver)
                .build();
        PaymentTransaction paymentTransaction2 = PaymentTransaction.builder()
                .sender(sender)
                .unit(LOVELACE)
                .amount(ADAConversionUtil.adaToLovelace(new BigDecimal("1.5").subtract(new BigDecimal("0.168317"))))
                .fee(null)
                .receiver(receiver)
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        Transaction transaction = utxoTransactionBuilder.buildTransaction(Arrays.asList(paymentTransaction1, paymentTransaction2), detailsParams, null, protocolParams);

        assertThat(transaction.getBody().getInputs(), hasSize(1));
        assertThat(transaction.getBody().getOutputs(), hasSize(1));
        assertThat(transaction.getBody().getInputs().get(0).getTransactionId(), is("496760b59ba36169bf6a62b09880824896b8e0044a4893f9649b6604741a89ed"));
        assertThat(transaction.getBody().getOutputs().get(0).getValue().getCoin(), is(ADAConversionUtil.adaToLovelace(new BigDecimal("1.5").subtract(new BigDecimal("0.168317")))));
        assertThat(transaction.getBody().getOutputs().get(0).getValue().getMultiAssets().get(0).getAssets().get(0).getValue(), is(BigInteger.valueOf(1000)));
    }

    @Test
    void testSendAllMultiAssetExactAmountDifferentReceiverFails() throws Exception{
        String receiver1 = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String receiver2 = "addr_test1qz7r5eu2jg0hx470mmf79vpgueaggh22pmayry8xrre5grtpyy9s8u2heru58a4r68wysmdw9v40zznttmwrg0a6v9tq36pjak";
        Account sender = new Account(Networks.testnet());

        String unit = "777777d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7";

        List<Utxo> utxos = Collections.singletonList(new Utxo("496760b59ba36169bf6a62b09880824896b8e0044a4893f9649b6604741a89ed", 3, sender.getBaseAddress().getAddress(), Arrays.asList(new Amount(unit, BigInteger.valueOf(1000)), new Amount(LOVELACE, ADAConversionUtil.adaToLovelace(new BigDecimal("1.5")))), null, null, null));
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(utxos);

        PaymentTransaction paymentTransaction1 = PaymentTransaction.builder()
                .sender(sender)
                .unit(unit)
                .amount(BigInteger.valueOf(1000))
                .fee(ADAConversionUtil.adaToLovelace(new BigDecimal("0.168317")))
                .receiver(receiver1)
                .build();
        PaymentTransaction paymentTransaction2 = PaymentTransaction.builder()
                .sender(sender)
                .unit(LOVELACE)
                .amount(ADAConversionUtil.adaToLovelace(new BigDecimal("1.5").subtract(new BigDecimal("0.168317"))))
                .fee(null)
                .receiver(receiver2)
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        // should fail since not enough funds to pay min ada for receiver 1
        Assertions.assertThrows(InsufficientBalanceException.class, () -> {
            utxoTransactionBuilder.buildTransaction(Arrays.asList(paymentTransaction1, paymentTransaction2), detailsParams, null, protocolParams);
        });
    }

    @Test
    void testSendAllMultiAssetExactAmountDifferentReceiverWithMinAda() throws Exception{
        String receiver1 = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String receiver2 = "addr_test1qz7r5eu2jg0hx470mmf79vpgueaggh22pmayry8xrre5grtpyy9s8u2heru58a4r68wysmdw9v40zznttmwrg0a6v9tq36pjak";
        Account sender = new Account(Networks.testnet());

        String unit = "777777d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7";

        List<Utxo> utxos = Collections.singletonList(new Utxo("496760b59ba36169bf6a62b09880824896b8e0044a4893f9649b6604741a89ed", 3, sender.getBaseAddress().getAddress(), Arrays.asList(new Amount(unit, BigInteger.valueOf(1000)), new Amount(LOVELACE, BigInteger.valueOf(2478633))), null, null, null));
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(utxos);

        PaymentTransaction paymentTransaction1 = PaymentTransaction.builder()
                .sender(sender)
                .unit(unit)
                .amount(BigInteger.valueOf(1000))
                .fee(ADAConversionUtil.adaToLovelace(new BigDecimal("0.168317")))
                .receiver(receiver1)
                .build();
        PaymentTransaction paymentTransaction2 = PaymentTransaction.builder()
                .sender(sender)
                .unit(LOVELACE)
                .amount(ADAConversionUtil.adaToLovelace(new BigDecimal("2.478633")
                                                            .subtract(new BigDecimal("0.168317")))
                                                            .subtract(BigInteger.valueOf(1129220))) // min ada to receive 1
                .fee(null)
                .receiver(receiver2)
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        // should fail since not enough funds to pay min ada for receiver 1
        var transaction = utxoTransactionBuilder.buildTransaction(Arrays.asList(paymentTransaction1, paymentTransaction2), detailsParams, null, protocolParams);

        assertThat(transaction.getBody().getInputs(), hasSize(1));
        assertThat(transaction.getBody().getOutputs(), hasSize(2));
        assertThat(transaction.getBody().getInputs().get(0).getTransactionId(), is("496760b59ba36169bf6a62b09880824896b8e0044a4893f9649b6604741a89ed"));
        assertThat(transaction.getBody().getOutputs().get(0).getValue().getCoin(), is(BigInteger.valueOf(1181096)));
        assertThat(transaction.getBody().getOutputs().get(1).getValue().getCoin(), is(BigInteger.valueOf(1129220)));
        assertThat(transaction.getBody().getOutputs().get(1).getValue().getMultiAssets().get(0).getAssets().get(0).getValue(), is(BigInteger.valueOf(1000)));
    }

    public static Map<String, BigInteger> getInputAmounts(List<Utxo> utxos, Transaction transaction){
        return utxos.stream()
                .filter(it -> transaction.getBody().getInputs().stream().filter(tx -> tx.getTransactionId().equals(it.getTxHash()) && tx.getIndex() == it.getOutputIndex()).findFirst().isPresent())
                .flatMap(it -> it.getAmount().stream())
                .collect(Collectors.groupingBy(Amount::getUnit,
                        Collectors.reducing(BigInteger.ZERO,
                                Amount::getQuantity,
                                BigInteger::add)));
    }
    public static Map<String, BigInteger> getOutputAmounts(Transaction transaction){
        Map<String, BigInteger> outputs = new HashMap<>();
        if(transaction.getBody().getFee() != null){
            outputs.put(LOVELACE, transaction.getBody().getFee());
        }
        for(var output : transaction.getBody().getOutputs()){
            var lovelace = output.getValue().getCoin();
            if(lovelace != null){
                var existing = outputs.getOrDefault(LOVELACE, BigInteger.ZERO);
                outputs.put(LOVELACE, existing.add(lovelace));
            }
            var other = output.getValue().getMultiAssets().stream()
                    .flatMap(it -> it.getAssets().stream()
                    .map(asset -> new Amount(AssetUtil.getUnit(it.getPolicyId(), asset), asset.getValue())))
                    .collect(Collectors.groupingBy(Amount::getUnit,
                            Collectors.reducing(BigInteger.ZERO,
                                    Amount::getQuantity,
                                    BigInteger::add)));
            for(var entry : other.entrySet()){
                var existing = outputs.getOrDefault(entry.getKey(), BigInteger.ZERO);
                outputs.put(entry.getKey(), existing.add(entry.getValue()));
            }
        }
        return outputs;
    }
}
