package com.bloxbean.cardano.client.backend.helper;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.backend.model.request.PaymentTransaction;
import com.bloxbean.cardano.client.common.CardanoConstants;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.transaction.model.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.*;

import static com.bloxbean.cardano.client.common.CardanoConstants.ONE_ADA;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UtxoTransactionBuilderTest {

    public static final String LIST_2 = "list2";
    public static final String LIST_3 = "list3-multiassets";

    @Mock
    UtxoService utxoService;

    @Mock
    TransactionService transactionService;

    @InjectMocks
    UtxoTransactionBuilder utxoTransactionBuilder;

    ObjectMapper objectMapper = new ObjectMapper();

    String dataFile = "utxos.json";

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        utxoTransactionBuilder = new UtxoTransactionBuilder(utxoService, transactionService);
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
        given(utxoService.getUtxos(any(), anyInt(), anyInt())).willReturn(Result.success(utxos.toString()).withValue(utxos).code(200));

        List<Utxo> utxoList = utxoTransactionBuilder.getUtxos(address, "lovelace", BigInteger.valueOf(500000000));

        verify(utxoService, times(1)).getUtxos(any(), anyInt(), anyInt());

        assertThat(utxoList, hasSize(2));
        assertThat(utxoList.get(0).getAmount().get(0).getQuantity(), is(BigInteger.valueOf(1407406)));
        assertThat(utxoList.get(1).getAmount().get(0).getQuantity(), is(BigInteger.valueOf(995770000)));
    }

    @Test
    public void testGetUtxosWithAssetWillReturnCorrectUtxos() throws ApiException, IOException, AddressExcepion {
        String address = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String unit = "329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e";

        List<Utxo> utxos = loadUtxos(LIST_2);
        given(utxoService.getUtxos(any(), anyInt(), anyInt())).willReturn(Result.success(utxos.toString()).withValue(utxos).code(200));

        List<Utxo> utxoList = utxoTransactionBuilder.getUtxos(address, unit, BigInteger.valueOf(400000000));

        assertThat(utxoList, hasSize(2));
        assertThat(utxoList.get(1).getAmount().get(0).getQuantity(), is(BigInteger.valueOf(999817955)));
        assertThat(utxoList.get(1).getAmount().get(1).getQuantity(), is(BigInteger.valueOf(5000000000L)));

    }

    @Test
    public void testBuildTransactionWithLovelaceWillReturnTrasaction() throws ApiException, IOException, AddressExcepion {
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<Utxo> utxos = loadUtxos(LIST_2);
        given(utxoService.getUtxos(any(), anyInt(), anyInt())).willReturn(Result.success(utxos.toString()).withValue(utxos).code(200));

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

        Transaction transaction = utxoTransactionBuilder.buildTransaction(Arrays.asList(paymentTransaction), detailsParams);

        assertThat(transaction.getBody().getInputs(), hasSize(2));
        assertThat(transaction.getBody().getInputs().get(0).getTransactionId(), is("735262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9"));
        assertThat(transaction.getBody().getInputs().get(1).getTransactionId(), is("1d98fc1aad22af10eec3cfc924d9edb4dcea6181e1d33895588c4d3c60d2af8b"));

        assertThat(transaction.getBody().getOutputs().get(0).getAddress(), is(receiver));
        assertThat(transaction.getBody().getOutputs().get(0).getValue().getCoin(), is(BigInteger.valueOf(500000000L)));

        assertThat(transaction.getBody().getOutputs().get(1).getAddress(), is(sender.baseAddress()));
        assertThat(transaction.getBody().getOutputs().get(1).getValue().getCoin(), is(BigInteger.valueOf(497057406)));
    }

    @Test
    public void testBuildTransactionWithMultiAssetWillReturnTrasaction() throws ApiException, IOException, AddressExcepion, CborException {
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String unit = "329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e";

        List<Utxo> utxos = loadUtxos(LIST_3);
        given(utxoService.getUtxos(any(), anyInt(), anyInt())).willReturn(Result.success(utxos.toString()).withValue(utxos).code(200));

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

        Transaction transaction = utxoTransactionBuilder.buildTransaction(Arrays.asList(paymentTransaction), detailsParams);

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

}
