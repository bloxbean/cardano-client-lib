package com.bloxbean.cardano.client.backend.api.helper.impl;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Amount;
import com.bloxbean.cardano.client.backend.model.ProtocolParams;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.coinselection.impl.RandomImproveUtxoSelectionStrategy;
import com.bloxbean.cardano.client.common.ADAConversionUtil;
import com.bloxbean.cardano.client.common.CardanoConstants;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class RandomImproveUtxoTransactionBuilderTest {

    public static final String LIST_9 = "list9-e2e-sender1";
    public static final String LIST_10 = "list10-e2e-sender2";

    @Mock
    UtxoService utxoService;

    @InjectMocks
    UtxoTransactionBuilderImpl utxoTransactionBuilder;

    ProtocolParams protocolParams;

    ObjectMapper objectMapper = new ObjectMapper();

    String dataFile = "utxos.json";

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        utxoTransactionBuilder = new UtxoTransactionBuilderImpl(new RandomImproveUtxoSelectionStrategy(utxoService));
        protocolParams = ProtocolParams.builder()
                .coinsPerUtxoWord("34482")
                .build();
    }

    private List<Utxo> loadUtxos(String key) throws IOException {
        TypeReference<HashMap<String, List<Utxo>>> typeRef
                = new TypeReference<HashMap<String, List<Utxo>>>() {};
        Map<String, List<Utxo>> map = objectMapper.readValue(this.getClass().getClassLoader().getResourceAsStream(dataFile), typeRef);
        return map.getOrDefault(key, Collections.emptyList());
    }

    @Test
    public void testE2ESingleAsset() throws ApiException, IOException{
        String senderMnemonic2 = "mixture peasant wood unhappy usage hero great elder emotion picnic talent fantasy program clean patch wheel drip disorder bullet cushion bulk infant balance address";
        Account sender2 = new Account(Networks.testnet(), senderMnemonic2);

        List<Utxo> utxosSender2 = loadUtxos(LIST_10);
        given(utxoService.getUtxos(eq(sender2.baseAddress()), anyInt(), eq(1), any())).willReturn(Result.success(utxosSender2.toString()).withValue(utxosSender2).code(200));

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

        // validate input matches output
        Map<String, BigInteger> inputs = UtxoTransactionBuilderTest.getInputAmounts(utxosSender2, transaction);
        Map<String, BigInteger> outputs = UtxoTransactionBuilderTest.getOutputAmounts(transaction);
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
        given(utxoService.getUtxos(eq(sender.baseAddress()), anyInt(), eq(1), any())).willReturn(Result.success(utxosSender1.toString()).withValue(utxosSender1).code(200));
        given(utxoService.getUtxos(eq(sender.baseAddress()), anyInt(), eq(2), any())).willReturn(Result.success("").withValue(Collections.emptyList()).code(400));

        List<Utxo> utxosSender2 = loadUtxos(LIST_10);
        given(utxoService.getUtxos(eq(sender2.baseAddress()), anyInt(), eq(1), any())).willReturn(Result.success(utxosSender2.toString()).withValue(utxosSender2).code(200));
        given(utxoService.getUtxos(eq(sender2.baseAddress()), anyInt(), eq(2), any())).willReturn(Result.success("").withValue(Collections.emptyList()).code(400));

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
        Map<String, BigInteger> inputs = UtxoTransactionBuilderTest.getInputAmounts(Stream.concat(utxosSender1.stream(), utxosSender2.stream()).collect(Collectors.toList()), transaction);
        Map<String, BigInteger> outputs = UtxoTransactionBuilderTest.getOutputAmounts(transaction);

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

        List<Utxo> utxos = Collections.singletonList(new Utxo("496760b59ba36169bf6a62b09880824896b8e0044a4893f9649b6604741a89ed", 3, Collections.singletonList(new Amount(LOVELACE, ADAConversionUtil.adaToLovelace(new BigDecimal("1.5")))), null));
        given(utxoService.getUtxos(any(), anyInt(), eq(1), any())).willReturn(Result.success(utxos.toString()).withValue(utxos).code(200));

        Account sender = new Account(Networks.testnet());
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
}
