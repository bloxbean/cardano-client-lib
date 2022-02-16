package com.bloxbean.cardano.client.backend.gql.it.helper;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.api.helper.UtxoTransactionBuilder;
import com.bloxbean.cardano.client.backend.api.helper.impl.UtxoTransactionBuilderImpl;
import com.bloxbean.cardano.client.backend.common.OrderEnum;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.gql.it.GqlBaseTest;
import com.bloxbean.cardano.client.backend.model.ProtocolParams;
import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.crypto.Keys;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.crypto.VerificationKey;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.model.MintTransaction;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Policy;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

class UtxoTransactionBuilderIT extends GqlBaseTest {

    public static final String senderMnemonic1 = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";

    UtxoService utxoService;
    TransactionService transactionService;
    UtxoTransactionBuilder utxoTransactionBuilder;
    EpochService epochService;
    ProtocolParams protocolParams;

    @BeforeEach
    public void setup() throws ApiException {
        utxoService = backendService.getUtxoService();
        transactionService = backendService.getTransactionService();
        utxoTransactionBuilder = new UtxoTransactionBuilderImpl(utxoService);
        epochService = backendService.getEpochService();
        protocolParams = epochService.getProtocolParameters().getValue();
    }

    @Test
    public void testBuildTransaction() throws AddressExcepion, ApiException {
        Account sender = new Account(Networks.testnet(), senderMnemonic1);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<PaymentTransaction> paymentTransactionList = Arrays.asList(
                PaymentTransaction.builder()
                .sender(sender)
                .receiver(receiver)
                .amount(BigInteger.valueOf(3000000))
                .fee(BigInteger.valueOf(230000))
                .unit("lovelace")
                .build()
        );

        Transaction transaction
                = utxoTransactionBuilder.buildTransaction(paymentTransactionList, TransactionDetailsParams.builder().ttl(1000).build(), null, protocolParams);

        System.out.println(transaction);

        assertThat(transaction.getBody().getInputs().size(), greaterThan(0));
    }

    @Test
    public void testBuildTransactionWithUtxos() throws AddressExcepion, ApiException {
        Account sender = new Account(Networks.testnet(), senderMnemonic1);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .sender(sender)
                .receiver(receiver)
                .amount(BigInteger.valueOf(30000000L))
                .fee(BigInteger.valueOf(230000))
                .unit("lovelace")
                .build();

        List<Utxo> utxos = utxoService.getUtxos(sender.baseAddress(), 20, 1, OrderEnum.desc).getValue();
        Utxo utxo = utxos.get(0);
        paymentTransaction.setUtxosToInclude(Arrays.asList(utxo));

        Transaction transaction
                = utxoTransactionBuilder.buildTransaction(Arrays.asList(paymentTransaction), TransactionDetailsParams.builder().ttl(1000).build(), null, protocolParams);

        System.out.println(JsonUtil.getPrettyJson(transaction));

        assertThat(transaction.getBody().getInputs().size(), greaterThan(0));
        assertThat(transaction.getBody().getInputs().get(0).getTransactionId(), is(utxo.getTxHash()));

    }

    @Test
    public void testBuildMintTokenTransactionWithUtxos() throws AddressExcepion, ApiException, CborSerializationException {
        Keys keys = KeyGenUtil.generateKey();
        VerificationKey vkey = keys.getVkey();
        SecretKey skey = keys.getSkey();
        ScriptPubkey scriptPubkey = ScriptPubkey.create(vkey);
        Policy policy = new Policy(scriptPubkey,Arrays.asList(skey));

        Account sender = new Account(Networks.testnet(), senderMnemonic1);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policy.getPolicyId());
        Asset asset = new Asset("mycoin", BigInteger.valueOf(250000));
        multiAsset.getAssets().add(asset);

        MintTransaction mintTokenTxn =
                MintTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .mintAssets(Arrays.asList(multiAsset))
                        .fee(BigInteger.valueOf(200000))
                        .policy(policy)
                        .build();

        List<Utxo> utxos = utxoService.getUtxos(sender.baseAddress(), 20, 1, OrderEnum.desc).getValue();
        Utxo utxo = utxos.get(0);
        mintTokenTxn.setUtxosToInclude(Arrays.asList(utxo));

        Transaction transaction
                = utxoTransactionBuilder.buildMintTokenTransaction(mintTokenTxn, TransactionDetailsParams.builder().ttl(1000).build(), null, protocolParams);

        System.out.println(JsonUtil.getPrettyJson(transaction));

        assertThat(transaction.getBody().getInputs().size(), greaterThan(0));
        assertThat(transaction.getBody().getInputs().get(0).getTransactionId(), is(utxo.getTxHash()));
    }
}
