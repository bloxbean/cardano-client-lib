package com.bloxbean.cardano.client.backend.api.helper.impl;

import com.bloxbean.cardano.client.BaseTest;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.api.helper.TransactionHelperService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.backend.model.ProtocolParams;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.common.CardanoConstants;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.spec.*;
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
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FeeCalculationServiceTest extends BaseTest {

    public static final String LIST_1 = "list1";
    public static final String LIST_2 = "list2-insufficient-change-amount";

    @Mock
    UtxoService utxoService;

    @Mock
    TransactionService transactionService;

    @InjectMocks
    UtxoTransactionBuilderImpl utxoTransactionBuilder;

    @InjectMocks
    TransactionHelperService transactionHelperService;

    @Mock
    EpochService epochService;

    ObjectMapper objectMapper = new ObjectMapper();
    ProtocolParams protocolParams;

    private FeeCalculationServiceImpl feeCalculationService;

    @BeforeEach
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        utxoTransactionBuilder = new UtxoTransactionBuilderImpl(utxoService);
        transactionHelperService = new TransactionHelperService(transactionService, epochService, utxoService);
        feeCalculationService = new FeeCalculationServiceImpl(transactionHelperService, epochService);
        utxoJsonFile = "fee-test-utxos.json";
        protocolParamJsonFile = "protocol-params.json";
        protocolParams = (ProtocolParams) loadObjectFromJson("protocol-parameters", ProtocolParams.class);
    }

    @Test
    void testCalculateFeeSimplePaymentTransaction() throws ApiException, IOException, AddressExcepion, CborSerializationException {

        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<Utxo> utxos = loadUtxos(LIST_1);
        given(utxoService.getUtxos(any(), anyInt(), anyInt(), any())).willReturn(Result.success(utxos.toString()).withValue(utxos).code(200));
        given(epochService.getProtocolParameters()).willReturn(Result.success(protocolParams.toString()).withValue(protocolParams).code(200));

        Account sender = new Account(Networks.testnet());
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .sender(sender)
                .unit(CardanoConstants.LOVELACE)
                .amount(BigInteger.valueOf(500000000))
                .receiver(receiver)
                .build();

        BigInteger fee = feeCalculationService.calculateFee(paymentTransaction, TransactionDetailsParams.builder().build(), null, protocolParams);
        System.out.println(fee);

        assertThat(fee.longValue(), greaterThan(166000L));
        assertThat(fee.longValue(), lessThan(176000L));
    }

    @Test
    void testCalculateFeeMultiplePaymentTransactions() throws ApiException, IOException, AddressExcepion, CborSerializationException {

        String receiver1 = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String receiver2 = "addr_test1qpchc7f6zln5c2ruarssn9p8vulhhn3rk7cvz4lm05eq2xwx8e7tf5l25n4ek3gch8tnj4k5z4236fvy6yndrusf7x0sv287t5";

        List<Utxo> utxos = loadUtxos(LIST_1);
        given(utxoService.getUtxos(any(), anyInt(), anyInt(), any())).willReturn(Result.success(utxos.toString()).withValue(utxos).code(200));
        given(epochService.getProtocolParameters()).willReturn(Result.success(protocolParams.toString()).withValue(protocolParams).code(200));

        Account sender = new Account(Networks.testnet());
        PaymentTransaction paymentTransaction1 = PaymentTransaction.builder()
                .sender(sender)
                .unit(CardanoConstants.LOVELACE)
                .amount(BigInteger.valueOf(500000000))
                .receiver(receiver1)
                .fee(BigInteger.valueOf(200))
                .build();

        PaymentTransaction paymentTransaction2 = PaymentTransaction.builder()
                .sender(sender)
                .unit(CardanoConstants.LOVELACE)
                .amount(BigInteger.valueOf(200000000))
                .receiver(receiver2)
                .fee(BigInteger.valueOf(400))
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        BigInteger fee = feeCalculationService.calculateFee(Arrays.asList(paymentTransaction1, paymentTransaction2),
                TransactionDetailsParams.builder().build(), null, protocolParams);
        System.out.println(fee);

        assertThat(fee.longValue(), greaterThan(178000L));
        assertThat(fee.longValue(), lessThan(180000L));
        //assert if original fee are there
        assertThat(paymentTransaction1.getFee(), is(BigInteger.valueOf(200)));
        assertThat(paymentTransaction2.getFee(), is(BigInteger.valueOf(400)));
    }

    @Test
    void testCalculateFeePaymentTransactionWithMetadata() throws ApiException, IOException, AddressExcepion, CborSerializationException {

        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<Utxo> utxos = loadUtxos(LIST_1);
        given(utxoService.getUtxos(any(), anyInt(), anyInt(), any())).willReturn(Result.success(utxos.toString()).withValue(utxos).code(200));
        given(epochService.getProtocolParameters()).willReturn(Result.success(protocolParams.toString()).withValue(protocolParams).code(200));

        CBORMetadataMap mm = new CBORMetadataMap()
                .put(new BigInteger("1978"), "1978value")
                .put(new BigInteger("197819"),new BigInteger("200001"))
                .put("203", new byte[] { 11,11,10});

        CBORMetadataList list = new CBORMetadataList()
                .add("301value")
                .add(new BigInteger("300001"))
                .add(new byte[] { 11,11,10})
                .add(new CBORMetadataMap()
                        .put(new BigInteger("401"), "401str")
                        .put("hello", "HelloValue"));
        Metadata metadata = new CBORMetadata()
                .put(new BigInteger("197819781978"), "John")
                .put(new BigInteger("197819781979"), "CA")
                .put(new BigInteger("1978197819710"), new byte[]{0,11})
                .put(new BigInteger("1978197819711"), mm)
                .put(new BigInteger("1978197819712"), list);


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

        BigInteger fee = feeCalculationService.calculateFee(paymentTransaction, TransactionDetailsParams.builder().build(), metadata, protocolParams);
        System.out.println(fee);

        assertThat(fee.longValue(), greaterThan(170905L));
        assertThat(fee.longValue(), lessThan(180000L));
    }

    @Test
    public void testBuildTransaction_whenNotEnoughUtxosLeft() throws ApiException, IOException, AddressExcepion, CborSerializationException {
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<Utxo> utxos = loadUtxos(LIST_2);
        given(utxoService.getUtxos(any(), anyInt(), eq(1), any())).willReturn(Result.success(utxos.toString()).withValue(utxos).code(200));
        given(utxoService.getUtxos(any(), anyInt(), eq(2), any())).willReturn(Result.success(utxos.toString()).withValue(Collections.EMPTY_LIST).code(200));
        given(epochService.getProtocolParameters()).willReturn(Result.success(protocolParams.toString()).withValue(protocolParams).code(200));

        Account sender = new Account(Networks.testnet());
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .sender(sender)
                .unit(CardanoConstants.LOVELACE)
                .amount(BigInteger.valueOf(11000000))
                .receiver(receiver)
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        Assertions.assertThrows(ApiRuntimeException.class, () -> {
            BigInteger fee = feeCalculationService.calculateFee(paymentTransaction, detailsParams
                    , null, protocolParams);
        });
    }

    @Test
    public void calculateFee_whenTransaction() throws ApiException, IOException, AddressExcepion, CborSerializationException, CborDeserializationException {
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<TransactionInput> inputs = new ArrayList<>();
        TransactionInput txnInput = TransactionInput.builder()
                .transactionId("5c0d3777218c631b945ebae44eb49f81acff6b53a87919d2606ac49ead07343a")
                .index(0)
                .build();
        inputs.add(txnInput);

        List<TransactionOutput> outputs = new ArrayList<>();
        TransactionOutput output = TransactionOutput.builder()
                .address(receiver)
                .value(Value.builder()
                        .coin(BigInteger.valueOf(2000))
                        .build())
                .build();

        TransactionBody txBody = TransactionBody.builder()
                .inputs(inputs)
                .outputs(outputs)
                .fee(BigInteger.valueOf(17000))
                .ttl(233000001)
                .build();

        Transaction transaction = Transaction.builder()
                .body(txBody)
                .build();

        Account account = new Account(Networks.testnet());
        Transaction signedTxn = account.sign(transaction);
        byte[] signedTxnBytes =  signedTxn.serialize();

        Transaction signedTransaction = Transaction.deserialize(signedTxnBytes);

        BigInteger fee = feeCalculationService.calculateFee(signedTransaction, protocolParams);

        System.out.println(fee);
        assertThat(fee, greaterThan(new BigInteger("15000")));
    }

    @Test
    public void calculateScriptFee() throws ApiException {
        given(epochService.getProtocolParameters()).willReturn(Result.success(protocolParams.toString()).withValue(protocolParams).code(200));

        ExUnits exUnits1 = ExUnits.builder()
                .mem(BigInteger.valueOf(1700))
                .steps(BigInteger.valueOf(476468)).build();

        ExUnits exUnits2 = ExUnits.builder()
                .mem(BigInteger.valueOf(2100))
                .steps(BigInteger.valueOf(576468)).build();

        BigInteger fee = feeCalculationService.calculateScriptFee(Arrays.asList(exUnits1, exUnits2));
        assertThat(fee.intValue(), is(296));
    }
}
