package com.bloxbean.cardano.client.backend.api.helper;

import com.bloxbean.cardano.client.BaseTest;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.api.helper.impl.FeeCalculationServiceImpl;
import com.bloxbean.cardano.client.backend.api.helper.impl.UtxoTransactionBuilderImpl;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.ProtocolParams;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.common.CardanoConstants;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
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

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        BigInteger fee = feeCalculationService.calculateFee(paymentTransaction, TransactionDetailsParams.builder().build(), null, protocolParams);
        System.out.println(fee);

        assertThat(fee.longValue(), greaterThan(166000L));
        assertThat(fee.longValue(), lessThan(176000L));
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
        given(utxoService.getUtxos(any(), anyInt(), eq(0), any())).willReturn(Result.success(utxos.toString()).withValue(utxos).code(200));
        given(utxoService.getUtxos(any(), anyInt(), eq(1), any())).willReturn(Result.success(utxos.toString()).withValue(Collections.EMPTY_LIST).code(200));
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

        BigInteger fee = feeCalculationService.calculateFee(paymentTransaction, detailsParams
                , null, protocolParams);

        System.out.println(fee);
        assertThat(fee, greaterThan(new BigInteger("1000")));
    }
}
