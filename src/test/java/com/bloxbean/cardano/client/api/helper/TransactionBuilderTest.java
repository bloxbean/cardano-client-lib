package com.bloxbean.cardano.client.api.helper;

import com.bloxbean.cardano.client.BaseTest;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.CardanoConstants;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.model.MintTransaction;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Policy;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.PolicyUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

//TODO -- Add more unit tests
@ExtendWith(MockitoExtension.class)
class TransactionBuilderTest extends BaseTest {
    public static final String LIST_2 = "list2";

    @Mock
    private UtxoSupplier utxoSupplier;

    private ProtocolParams protocolParams;

    @BeforeEach
    void setup() throws IOException {
        utxoJsonFile = "utxos.json";
        protocolParamJsonFile = "protocol-params.json";
        protocolParams = (ProtocolParams) loadObjectFromJson("protocol-parameters", ProtocolParams.class);
    }

    @Test
    void createSignedTransaction_sameSender_shouldHaveOneWitness() throws Exception {
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<Utxo> utxos = loadUtxos(LIST_2);

        Account sender = new Account(Networks.testnet());
        PaymentTransaction paymentTransaction1 = PaymentTransaction.builder()
                .sender(sender)
                .fee(BigInteger.valueOf(120000))
                .unit(CardanoConstants.LOVELACE)
                .amount(BigInteger.valueOf(1000000))
                .receiver(receiver)
                .utxosToInclude(utxos)
                .build();

        PaymentTransaction paymentTransaction2 = PaymentTransaction.builder()
                .sender(sender)
                .fee(BigInteger.valueOf(120000))
                .unit(CardanoConstants.LOVELACE)
                .amount(BigInteger.valueOf(1000000))
                .receiver(receiver)
                .utxosToInclude(utxos)
                .build();

        PaymentTransaction paymentTransaction3 = PaymentTransaction.builder()
                .sender(sender)
                .fee(BigInteger.valueOf(120000))
                .unit(CardanoConstants.LOVELACE)
                .amount(BigInteger.valueOf(1000000))
                .receiver(receiver)
                .utxosToInclude(utxos)
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        TransactionBuilder txBuilder = new TransactionBuilder(utxoSupplier, () -> protocolParams);

        String signedTxnHex = txBuilder.createSignedTransaction(Arrays.asList(paymentTransaction1, paymentTransaction2, paymentTransaction3)
                , detailsParams, null);

        Transaction signedTxn = Transaction.deserialize(HexUtil.decodeHexString(signedTxnHex));

        System.out.println(signedTxn);
        assertThat(signedTxn.getWitnessSet().getVkeyWitnesses()).hasSize(1);
    }

    @Test
    void createSignedTransaction_multipleSenders_shouldHaveMultipleWitnesses() throws Exception {
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<Utxo> utxos = loadUtxos(LIST_2);

        Account sender1 = new Account(Networks.testnet());
        Account sender2 = new Account(Networks.testnet());
        Account sender3 = new Account(Networks.testnet());
        PaymentTransaction paymentTransaction1 = PaymentTransaction.builder()
                .sender(sender1)
                .fee(BigInteger.valueOf(120000))
                .unit(CardanoConstants.LOVELACE)
                .amount(BigInteger.valueOf(1000000))
                .receiver(receiver)
                .utxosToInclude(utxos)
                .build();

        PaymentTransaction paymentTransaction2 = PaymentTransaction.builder()
                .sender(sender1)
                .fee(BigInteger.valueOf(120000))
                .unit(CardanoConstants.LOVELACE)
                .amount(BigInteger.valueOf(1500000))
                .receiver(receiver)
                .utxosToInclude(utxos)
                .build();

        PaymentTransaction paymentTransaction3 = PaymentTransaction.builder()
                .sender(sender2)
                .fee(BigInteger.valueOf(120000))
                .unit(CardanoConstants.LOVELACE)
                .amount(BigInteger.valueOf(1200000))
                .receiver(receiver)
                .utxosToInclude(utxos)
                .build();

        PaymentTransaction paymentTransaction4 = PaymentTransaction.builder()
                .sender(sender3)
                .fee(BigInteger.valueOf(120000))
                .unit(CardanoConstants.LOVELACE)
                .amount(BigInteger.valueOf(1300000))
                .receiver(receiver)
                .utxosToInclude(utxos)
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        TransactionBuilder txBuilder = new TransactionBuilder(utxoSupplier, () -> protocolParams);
        String signedTxnHex = txBuilder.createSignedTransaction(
                Arrays.asList(paymentTransaction1, paymentTransaction2, paymentTransaction3, paymentTransaction4),
                detailsParams, null);

        Transaction signedTxn = Transaction.deserialize(HexUtil.decodeHexString(signedTxnHex));

        System.out.println(signedTxn);
        assertThat(signedTxn.getWitnessSet().getVkeyWitnesses()).hasSize(3);
    }

    @Test
    void createSignedTransaction_multipleSendersAndAdditionalAccounts_shouldHaveMultipleWitnesses() throws Exception {
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<Utxo> utxos = loadUtxos(LIST_2);

        Account sender1 = new Account(Networks.testnet());
        Account sender2 = new Account(Networks.testnet());
        Account sender3 = new Account(Networks.testnet());

        Account sender4 = new Account(Networks.testnet());
        Account sender5 = new Account(Networks.testnet());
        Account sender6 = new Account(Networks.testnet());
        Account sender7 = new Account(Networks.testnet());
        Account sender8 = new Account(Networks.testnet());
        Account sender9 = new Account(Networks.testnet());
        PaymentTransaction paymentTransaction1 = PaymentTransaction.builder()
                .sender(sender1)
                .fee(BigInteger.valueOf(120000))
                .unit(CardanoConstants.LOVELACE)
                .amount(BigInteger.valueOf(1000000))
                .receiver(receiver)
                .utxosToInclude(utxos)
                .build();

        PaymentTransaction paymentTransaction2 = PaymentTransaction.builder()
                .sender(sender1)
                .fee(BigInteger.valueOf(120000))
                .unit(CardanoConstants.LOVELACE)
                .amount(BigInteger.valueOf(1500000))
                .receiver(receiver)
                .utxosToInclude(utxos)
                .additionalWitnessAccounts(List.of(sender4, sender5))
                .build();

        PaymentTransaction paymentTransaction3 = PaymentTransaction.builder()
                .sender(sender2)
                .fee(BigInteger.valueOf(120000))
                .unit(CardanoConstants.LOVELACE)
                .amount(BigInteger.valueOf(1200000))
                .receiver(receiver)
                .utxosToInclude(utxos)
                .additionalWitnessAccounts(List.of(sender6, sender7, sender8))
                .build();

        PaymentTransaction paymentTransaction4 = PaymentTransaction.builder()
                .sender(sender3)
                .fee(BigInteger.valueOf(120000))
                .unit(CardanoConstants.LOVELACE)
                .amount(BigInteger.valueOf(1300000))
                .receiver(receiver)
                .utxosToInclude(utxos)
                .additionalWitnessAccounts(List.of(sender8, sender9))
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        TransactionBuilder txBuilder = new TransactionBuilder(utxoSupplier, () -> protocolParams);
        String signedTxnHex = txBuilder.createSignedTransaction(
                Arrays.asList(paymentTransaction1, paymentTransaction2, paymentTransaction3, paymentTransaction4),
                detailsParams, null);

        Transaction signedTxn = Transaction.deserialize(HexUtil.decodeHexString(signedTxnHex));

        System.out.println(signedTxn);
        assertThat(signedTxn.getWitnessSet().getVkeyWitnesses()).hasSize(9);
    }

    @Test
    void createSignedMintTransaction_withSingleSenderAndSinglePolicyScript_shouldHaveTwoWitnesses() throws IOException, CborSerializationException, AddressExcepion, ApiException, CborDeserializationException {
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<Utxo> utxos = loadUtxos(LIST_2);

        Policy policy1 = PolicyUtil.createMultiSigScriptAllPolicy("policy1", 1);

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policy1.getPolicyId());
        Asset asset = new Asset("token1", BigInteger.valueOf(250000));
        multiAsset.getAssets().add(asset);

        Account sender = new Account(Networks.testnet());
        MintTransaction mintTransaction = MintTransaction.builder()
                .sender(sender)
                .fee(BigInteger.valueOf(1750000))
                .mintAssets(Arrays.asList(multiAsset))
                .policy(policy1)
                .receiver(receiver)
                .utxosToInclude(Arrays.asList(utxos.get(1), utxos.get(4)))
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        TransactionBuilder txBuilder = new TransactionBuilder(utxoSupplier, () -> protocolParams);
        String signedTxnHex = txBuilder.createSignedMintTransaction(mintTransaction, detailsParams, null);

        Transaction signedTxn = Transaction.deserialize(HexUtil.decodeHexString(signedTxnHex));

        System.out.println(signedTxn);
        assertThat(signedTxn.getWitnessSet().getVkeyWitnesses()).hasSize(2);
    }

    @Test
    void createSignedMintTransaction_withSingleSenderAndSinglePolicyScriptWithMultipleSecretKeys_shouldHaveMultipleWitnesses() throws IOException, CborSerializationException, AddressExcepion, ApiException, CborDeserializationException {
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<Utxo> utxos = loadUtxos(LIST_2);

        Policy policy1 = PolicyUtil.createMultiSigScriptAllPolicy("policy1", 3);

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policy1.getPolicyId());
        Asset asset = new Asset("token1", BigInteger.valueOf(250000));
        multiAsset.getAssets().add(asset);

        Account sender = new Account(Networks.testnet());
        MintTransaction mintTransaction = MintTransaction.builder()
                .sender(sender)
                .fee(BigInteger.valueOf(1750000))
                .mintAssets(Arrays.asList(multiAsset))
                .policy(policy1)
                .receiver(receiver)
                .utxosToInclude(Arrays.asList(utxos.get(1), utxos.get(4)))
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        TransactionBuilder txBuilder = new TransactionBuilder(utxoSupplier, () -> protocolParams);
        String signedTxnHex = txBuilder.createSignedMintTransaction(mintTransaction, detailsParams, null);

        Transaction signedTxn = Transaction.deserialize(HexUtil.decodeHexString(signedTxnHex));

        System.out.println(signedTxn);
        assertThat(signedTxn.getWitnessSet().getVkeyWitnesses()).hasSize(4);
    }

    @Test
    void createSignedMintTransaction_withMultiplePolicyScriptsWithMultipleSecretKeys_shouldHaveMultipleWitnesses() throws IOException, CborSerializationException, AddressExcepion, ApiException, CborDeserializationException {
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<Utxo> utxos = loadUtxos(LIST_2);

        Policy policy1 = PolicyUtil.createMultiSigScriptAllPolicy("policy1", 3);
        MultiAsset multiAsset1 = new MultiAsset();
        multiAsset1.setPolicyId(policy1.getPolicyId());
        for (int i=0; i<4;i++) {
            Asset asset = new Asset("token" + i, BigInteger.valueOf(250000));
            multiAsset1.getAssets().add(asset);
        }

        MultiAsset multiAsset2 = new MultiAsset();
        multiAsset2.setPolicyId(policy1.getPolicyId());
        for (int i=0; i < 5;i++) {
            Asset asset = new Asset("secondtoken" + i, BigInteger.valueOf(350000));
            multiAsset2.getAssets().add(asset);
        }

        Account sender = new Account(Networks.testnet());
        MintTransaction mintTransaction = MintTransaction.builder()
                .sender(sender)
                .fee(BigInteger.valueOf(1750000))
                .mintAssets(List.of(multiAsset1, multiAsset2))
                .policy(policy1)
                .receiver(receiver)
                .utxosToInclude(Arrays.asList(utxos.get(1), utxos.get(4)))
                .build();

        TransactionDetailsParams detailsParams = TransactionDetailsParams.builder()
                .ttl(199999)
                .build();

        TransactionBuilder txBuilder = new TransactionBuilder(utxoSupplier, () -> protocolParams);
        String signedTxnHex = txBuilder.createSignedMintTransaction(mintTransaction, detailsParams, null);

        Transaction signedTxn = Transaction.deserialize(HexUtil.decodeHexString(signedTxnHex));

        System.out.println(signedTxn);
        assertThat(signedTxn.getWitnessSet().getVkeyWitnesses()).hasSize(4);
        assertThat(signedTxn.getBody().getMint()).hasSize(1);
        assertThat(signedTxn.getBody().getMint().get(0).getAssets()).hasSize(9);
    }
}
