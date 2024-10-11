package com.bloxbean.cardano.client.quicktx;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.exception.InsufficientBalanceException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.PolicyUtil;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.cip.cip20.MessageMetadata;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.coinselection.impl.RandomImproveUtxoSelectionStrategy;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.VerificationKey;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static com.bloxbean.cardano.client.quicktx.verifiers.TxVerifiers.outputAmountVerifier;
import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

public class QuickTxBuilderIT extends QuickTxBaseIT {
    BackendService backendService;
    static Account sender1;
    static Account sender2;

    static String sender1Addr;
    static String sender2Addr;

    static String receiver1 = "addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c";
    static String receiver2 = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
    static String receiver3 = "addr_test1qqqvjp4ffcdqg3fmx0k8rwamnn06wp8e575zcv8d0m3tjn2mmexsnkxp7az774522ce4h3qs4tjp9rxjjm46qf339d9sk33rqn";

    QuickTxBuilder quickTxBuilder;

    @BeforeAll
    static void setupAll() {
        //Set the backend type
        backendType = DEVKIT;

        //addr_test1qp73ljurtknpm5fgey5r2y9aympd33ksgw0f8rc5khheg83y35rncur9mjvs665cg4052985ry9rzzmqend9sqw0cdksxvefah
        String senderMnemonic = "drive useless envelope shine range ability time copper alarm museum near flee wrist live type device meadow allow churn purity wisdom praise drop code";
        sender1 = new Account(Networks.testnet(), senderMnemonic);
        sender1Addr = sender1.baseAddress();

        //addr_test1qz5fcpvkg7pekqvv9ld03t5sx2w2c2fac67fzlaxw5844s83l4p6tr389lhgcpe4797kt7xkcxqvcc4a6qjshzsmta8sh3ncs4
        String sender2Mnemonic = "access else envelope between rubber celery forum brief bubble notice stomach add initial avocado current net film aunt quick text joke chase robust artefact";
        sender2 = new Account(Networks.testnet(), sender2Mnemonic);
        sender2Addr = sender2.baseAddress();

        if (backendType.equals(DEVKIT)) {
            topUpFund(sender1Addr, 50000);
            topUpFund(sender2Addr, 50000);
        }
    }

    @BeforeEach
    void setup() {
        backendService = getBackendService();
        quickTxBuilder = new QuickTxBuilder(backendService);
    }

    @Test
    void simplePayment() {
        Metadata metadata = MetadataBuilder.createMetadata();
        metadata.put(BigInteger.valueOf(100), "This is second metadata");
        metadata.putNegative(200, -900);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(1.5))
                .payToAddress(receiver2, Amount.ada(2.5))
                .attachMetadata(MessageMetadata.create().add("This is a test message"))
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .complete();

        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);

        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    void simplePayment_compose() {
        Tx tx1 = new Tx()
                .payToAddress(receiver1, Amount.ada(1.5))
                .payToAddress(receiver2, Amount.ada(2.5))
                .attachMetadata(MessageMetadata.create().add("This is a test message 2"))
                .from(sender1Addr);

        Tx tx2 = new Tx()
                .payToAddress(receiver3, Amount.ada(4.5))
                .from(sender2Addr);

        Result<String> result = quickTxBuilder
                .compose(tx1, tx2)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);

        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);

        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Nested
    class MintingTests {
        @Test
        void minting() throws CborSerializationException {
            Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("test_policy", 1, 1);
            String assetName = "MyAsset";
            BigInteger qty = BigInteger.valueOf(1000);

            Tx tx = new Tx()
                    .mintAssets(policy.getPolicyScript(), new Asset(assetName, qty), sender1.baseAddress())
                    .attachMetadata(MessageMetadata.create().add("Minting tx"))
                    .from(sender1.baseAddress());

            Result<String> result = quickTxBuilder.compose(tx)
                    .withSigner(SignerProviders.signerFrom(sender1))
                    .withSigner(SignerProviders.signerFrom(policy))
                    .complete();

            System.out.println(result);
            assertTrue(result.isSuccessful());
            waitForTransaction(result);

            checkIfUtxoAvailable(result.getValue(), sender1Addr);
        }

        @Test
        void minting_withScriptRef() throws CborSerializationException {
            Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("test_policy", 1, 1);

            String assetName = "MyAsset";
            BigInteger qty = BigInteger.valueOf(1000);

            Tx tx1 = new Tx()
                    .payToAddress(sender1Addr, Amount.ada(5), policy.getPolicyScript())
                    .from(sender1Addr);

            Result<String> result1 = quickTxBuilder.compose(tx1)
                    .withSigner(SignerProviders.signerFrom(sender1))
                    .withTxInspector(tx -> {
                        System.out.println(JsonUtil.getPrettyJson(tx));
                    })
                    .completeAndWait();

            System.out.println(result1);
            assertThat(result1.isSuccessful()).isTrue();

            checkIfUtxoAvailable(result1.getValue(), sender1Addr);

            Tx tx = new Tx()
                    .mintAssets(policy.getPolicyScript(), new Asset(assetName, qty), sender1.baseAddress())
                    .attachMetadata(MessageMetadata.create().add("Minting tx"))
                    .from(sender1.baseAddress());

            Result<String> result = quickTxBuilder.compose(tx)
                    .withSigner(SignerProviders.signerFrom(sender1))
                    .withSigner(SignerProviders.signerFrom(policy))
                    .removeDuplicateScriptWitnesses(true)
                    .completeAndWait();

            System.out.println(result);
            assertTrue(result.isSuccessful());
            waitForTransaction(result);

            checkIfUtxoAvailable(result.getValue(), sender1Addr);
        }

        @Test
        void minting_withTransfer() throws CborSerializationException {
            Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("test_policy", 1, 1);
            String assetName = "MyAsset";
            BigInteger qty = BigInteger.valueOf(2000);

            Tx tx1 = new Tx()
                    .payToAddress(receiver2, Amount.ada(1.5))
                    .mintAssets(policy.getPolicyScript(), new Asset(assetName, qty), receiver2)
                    .attachMetadata(MessageMetadata.create().add("Minting tx"))
                    .from(sender1.baseAddress());

            Tx tx2 = new Tx()
                    .payToAddress(receiver3, new Amount(LOVELACE, adaToLovelace(2.13)))
                    .from(sender2.baseAddress());

            Result<String> result = quickTxBuilder.compose(tx1, tx2)
                    .feePayer(sender1.baseAddress())
                    .withSigner(SignerProviders.signerFrom(sender1)
                            .andThen(SignerProviders.signerFrom(sender2)))
                    .withSigner(SignerProviders.signerFrom(policy))
                    .additionalSignersCount(1) //As we have composed TxSigners from 2 signers, we need to add 1 additional signer,
                    // as it's hard to determine how many signers are in the composed TxSigner
                    .complete();

            System.out.println(result);
            assertTrue(result.isSuccessful());
            waitForTransaction(result);

            checkIfUtxoAvailable(result.getValue(), sender1Addr);
        }

        @Test
        void minting_transferMintedToTwoAccounts() throws CborSerializationException {
            Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("test_policy", 1, 1);
            String assetName = "MyAsset";
            BigInteger qty = BigInteger.valueOf(2000);

            Tx tx1 = new Tx()
                    .payToAddress(receiver1, Amount.asset(policy.getPolicyId(), assetName, 200))
                    .payToAddress(receiver2, List.of(Amount.ada(1.5), Amount.asset(policy.getPolicyId(), assetName, 1800)))
                    .mintAssets(policy.getPolicyScript(), new Asset(assetName, qty))
                    .attachMetadata(MessageMetadata.create().add("Minting tx"))
                    .from(sender1.baseAddress());

            Tx tx2 = new Tx()
                    .payToAddress(receiver3, Amount.ada(2.13))
                    .from(sender2.baseAddress());


            Result<String> result = quickTxBuilder.compose(tx1, tx2)
                    .feePayer(sender2.baseAddress())
                    .withSigner(SignerProviders.signerFrom(sender1))
                    .withSigner(SignerProviders.signerFrom(sender2))
                    .withSigner(SignerProviders.signerFrom(policy))
                    .complete();

            System.out.println(result);
            assertTrue(result.isSuccessful());
            waitForTransaction(result);

            checkIfUtxoAvailable(result.getValue(), sender2Addr);
        }

        @Test
        void minting_transferMintedToTwoAccounts_withPayMintAssetToAddress() throws CborSerializationException, CborException {
            ScriptPubkey policyScript =
                    ScriptPubkey.create(VerificationKey.create(sender1.publicKeyBytes()));
            String policyId = policyScript.getPolicyId();

            String assetName = "MyAsset";
            BigInteger qty = BigInteger.valueOf(2000);

            PlutusV2Script referenceScript = PlutusV2Script.builder()
                    .type("PlutusScriptV2")
                    .cborHex("49480100002221200101")
                    .build();
            String scriptAddr = AddressProvider.getEntAddress(referenceScript, Networks.testnet()).toBech32();

            PlutusV2Script referenceScript2 = PlutusV2Script.builder()
                    .type("PlutusScriptV2")
                    .cborHex("49480100002221200101")
                    .build();
            String scriptAddr2 = AddressProvider.getEntAddress(referenceScript2, Networks.testnet()).toBech32();

            Tx tx1 = new Tx()
                    .mintAssets(policyScript, new Asset(assetName, qty))
                    .payToAddress(receiver1, Amount.asset(policyId, assetName, 190))
                    .payToAddress(receiver2, List.of(Amount.ada(1.5), Amount.asset(policyId, assetName, 1800)))
                    .payToContract("addr_test1wr297svp7eth4y2qd356a042gwn3th93j93843sa3hgm5lcgc3gkc",
                            Amount.asset(policyScript.getPolicyId(), assetName, 4),
                            BigIntPlutusData.of(1).getDatumHash())
                    .payToAddress(receiver3, List.of(Amount.asset(policyId, assetName, 3)), referenceScript)
                    .payToAddress(receiver2, List.of(Amount.asset(policyId, assetName, 1)), referenceScript2.scriptRefBytes())
                    .payToContract(scriptAddr2, List.of(Amount.asset(policyId, assetName, 1)), BigIntPlutusData.of(1), referenceScript2)
                    .payToContract(scriptAddr, List.of(Amount.asset(policyId, assetName, 1)), BigIntPlutusData.of(1), referenceScript.scriptRefBytes())
                    .attachMetadata(MessageMetadata.create().add("Minting tx"))
                    .from(sender1.baseAddress());

            Tx tx2 = new Tx()
                    .payToAddress(receiver3, Amount.ada(2.13))
                    .from(sender2.baseAddress());


            Result<String> result = quickTxBuilder.compose(tx1, tx2)
                    .feePayer(sender2.baseAddress())
                    .withSigner(SignerProviders.signerFrom(sender1))
                    .withSigner(SignerProviders.signerFrom(sender2))
                    .complete();

            System.out.println(result);
            assertTrue(result.isSuccessful());
            waitForTransaction(result);

            checkIfUtxoAvailable(result.getValue(), sender2Addr);
        }
    }

    @Nested
    class CustomChangeAddress {
        @Test
        void simplePayment_customChangeAddress() throws InterruptedException {
            //Send 4 ada to a new change account
            Account changeAccount = new Account(Networks.testnet());
            Tx tx = new Tx()
                    .payToAddress(changeAccount.baseAddress(), Amount.ada(4.0))
                    .from(sender1Addr);

            Result<String> result = quickTxBuilder.compose(tx)
                    .withSigner(SignerProviders.signerFrom(sender1))
                    .complete();
            System.out.println(result);
            assertTrue(result.isSuccessful());
            waitForTransaction(result);

            System.out.println("Mnemonic: " + changeAccount.mnemonic());

            //Now create another tx with custom change address
            Tx tx2 = new Tx()
                    .payToAddress(receiver1, Amount.ada(1.1))
                    .withChangeAddress(sender1Addr)
                    .from(changeAccount.baseAddress());

            Result<String> result2 = quickTxBuilder.compose(tx2)
                    .feePayer(sender1Addr)
                    .withSigner(SignerProviders.signerFrom(changeAccount))
                    .complete();
            System.out.println(result2);
            assertTrue(result2.isSuccessful());
            waitForTransaction(result2);

            checkIfUtxoAvailable(result.getValue(), sender1Addr);
        }

        @Test
        void simplePayment_customChangeAddress_multipleTxs_requiredFeePayer() {
            //Send 4 ada to a new change account
            Account changeAccount = new Account(Networks.testnet());
            Tx tx = new Tx()
                    .payToAddress(changeAccount.baseAddress(), Amount.ada(4.0))
                    .from(sender1Addr);

            Result<String> result = quickTxBuilder.compose(tx)
                    .withSigner(SignerProviders.signerFrom(sender1))
                    .complete();
            System.out.println(result);
            assertTrue(result.isSuccessful());
            waitForTransaction(result);

            //Now create another tx with custom change address
            Tx tx2 = new Tx()
                    .payToAddress(receiver1, Amount.ada(1.1))
                    .withChangeAddress(sender1Addr)
                    .from(changeAccount.baseAddress());

            Tx tx3 = new Tx()
                    .payToAddress(receiver2, Amount.ada(2.1))
                    .from(sender2Addr);

            Result<String> result2 = quickTxBuilder.compose(tx2, tx3)
                    .feePayer(sender1.baseAddress())
                    .withSigner(SignerProviders.signerFrom(changeAccount))
                    .withSigner(SignerProviders.signerFrom(sender2))
                    .complete();
            System.out.println(result2);
            assertTrue(result2.isSuccessful());
            waitForTransaction(result2);

            checkIfUtxoAvailable(result.getValue(), sender1Addr);
        }
    }

    @Test
    void simplePayment_withPreAndPostBalanceBuilder() {
        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(1.5))
                .payToAddress(receiver2, Amount.ada(2.5))
                .attachMetadata(MessageMetadata.create().add("This is a test message 2"))
                .from(sender1Addr);

        Result<String> result = quickTxBuilder
                .compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .preBalanceTx((context, txn) -> {
                    //do anything here...
                    System.out.println("Pre balance");
                    AuxiliaryData auxiliaryData = new AuxiliaryData();
                    auxiliaryData.setMetadata(MessageMetadata.create().add("This is a test message in pre balance"));
                    txn.setAuxiliaryData(auxiliaryData);
                }).postBalanceTx((context, txn) -> {
                    System.out.println("Post balance");
                })
                .complete();

        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);

        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    void simplePayment_collectFromUtxo() {
        String senderAddr = sender1.baseAddress();
        UtxoSelectionStrategy utxoSelectionStrategy =
                new RandomImproveUtxoSelectionStrategy(new DefaultUtxoSupplier(backendService.getUtxoService()));
        Set<Utxo> utxos = utxoSelectionStrategy.select(senderAddr, Amount.ada(4.0), null);

        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(1.5))
                .payToAddress(receiver2, Amount.ada(1.5))
                .collectFrom(utxos)
                .from(senderAddr);

        Result<String> result = quickTxBuilder.compose(tx)
                .feePayer(sender2Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(sender2))
                .complete();

        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);

        checkIfUtxoAvailable(result.getValue(), sender2Addr);
    }

    @Test
    void simplePayment_collectFromUtxo_withSelectedUtxos() {

    }

    @Test
    void tx_validTo() throws ApiException, CborSerializationException {
        long validSlot = backendService.getBlockService().getLatestBlock().getValue().getSlot() + 100;

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(1.5))
                .attachMetadata(MessageMetadata.create().add("This is a test message"))
                .from(sender1Addr);

        Transaction transaction = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .validTo(validSlot)
                .buildAndSign();

        Result<String> result = backendService.getTransactionService().submitTransaction(transaction.serialize());

        assertEquals(validSlot, transaction.getBody().getTtl());
        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);

        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    void tx_invalid_validTo() throws ApiException, CborSerializationException {
        long validSlot = backendService.getBlockService().getLatestBlock().getValue().getSlot() - 100;

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(1.5))
                .attachMetadata(MessageMetadata.create().add("This is a test message"))
                .from(sender1Addr);

        Transaction transaction = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .validTo(validSlot)
                .buildAndSign();

        Result<String> result = backendService.getTransactionService().submitTransaction(transaction.serialize());

        assertEquals(validSlot, transaction.getBody().getTtl());
        System.out.println(result);
        assertFalse(result.isSuccessful());
    }

    @Test
    void tx_validFrom() throws ApiException, CborSerializationException {
        long validSlot = backendService.getBlockService().getLatestBlock().getValue().getSlot() - 10;

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(1.5))
                .attachMetadata(MessageMetadata.create().add("This is a test message"))
                .from(sender1Addr);

        Transaction transaction = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .validFrom(validSlot)
                .buildAndSign();
        System.out.println(JsonUtil.getPrettyJson(transaction));

        Result<String> result = backendService.getTransactionService().submitTransaction(transaction.serialize());

        assertEquals(validSlot, transaction.getBody().getValidityStartInterval());
        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);

        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    void tx_invalid_validFrom() throws ApiException, CborSerializationException {
        long validSlot = backendService.getBlockService().getLatestBlock().getValue().getSlot() + 100;

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(1.5))
                .attachMetadata(MessageMetadata.create().add("This is a test message"))
                .from(sender1Addr);

        Transaction transaction = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .validFrom(validSlot)
                .buildAndSign();
        System.out.println(JsonUtil.getPrettyJson(transaction));

        Result<String> result = backendService.getTransactionService().submitTransaction(transaction.serialize());

        assertEquals(validSlot, transaction.getBody().getValidityStartInterval());
        System.out.println(result);
        assertFalse(result.isSuccessful());
        waitForTransaction(result);
    }

    @Test
    void simplePayment_withRandomImproveSelectionStrategy() {
        Metadata metadata = MetadataBuilder.createMetadata();
        metadata.put(BigInteger.valueOf(100), "This is second metadata");
        metadata.putNegative(200, -900);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(1.5))
                .payToAddress(receiver2, Amount.ada(2.5))
                .attachMetadata(MessageMetadata.create().add("This is a test message"))
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withUtxoSelectionStrategy(
                        new RandomImproveUtxoSelectionStrategy(new DefaultUtxoSupplier(backendService.getUtxoService())))
                .complete();

        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);

        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    void simplePayment_withDummySelectionStrategy_shouldFail() {
        Metadata metadata = MetadataBuilder.createMetadata();
        metadata.put(BigInteger.valueOf(100), "This is second metadata");
        metadata.putNegative(200, -900);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(1.5))
                .payToAddress(receiver2, Amount.ada(2.5))
                .attachMetadata(MessageMetadata.create().add("This is a test message"))
                .from(sender1Addr);

        assertThrows(InsufficientBalanceException.class, () -> {
            quickTxBuilder.compose(tx)
                    .withSigner(SignerProviders.signerFrom(sender1))
                    .withUtxoSelectionStrategy(new UtxoSelectionStrategy() {
                        @Override
                        public Set<Utxo> select(String address, List<Amount> outputAmounts, String datumHash, PlutusData inlineDatum, Set<Utxo> utxosToExclude, int maxUtxoSelectionLimit) {
                            throw new InsufficientBalanceException("Insufficient balance");
                        }

                        @Override
                        public UtxoSelectionStrategy fallback() {
                            return null;
                        }

                        @Override
                        public void setIgnoreUtxosWithDatumHash(boolean ignoreUtxosWithDatumHash) {

                        }
                    })
                    .complete();
        });
    }

    @Test
    void simplePayment_withVerifier() {
        Metadata metadata = MetadataBuilder.createMetadata();
        metadata.put(BigInteger.valueOf(100), "This is second metadata");
        metadata.putNegative(200, -900);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(1.5))
                .payToAddress(receiver2, Amount.ada(2.5))
                .attachMetadata(MessageMetadata.create().add("This is a test message"))
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withVerifier(
                        outputAmountVerifier(receiver1, Amount.ada(1.5), "Output amount is not correct")
                                .andThen(outputAmountVerifier(receiver2, Amount.ada(2.5), "Output amount is not correct"))
                ).complete();

        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);

        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    void minting_withTransfer_andVerifier() throws CborSerializationException {
        Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("test_policy", 1, 1);
        String assetName = "MyAsset";
        BigInteger qty = BigInteger.valueOf(2000);

        Tx tx1 = new Tx()
                .payToAddress(receiver2, Amount.ada(1.5))
                .mintAssets(policy.getPolicyScript(), new Asset(assetName, qty), receiver2)
                .attachMetadata(MessageMetadata.create().add("Minting tx"))
                .from(sender1.baseAddress());

        Tx tx2 = new Tx()
                .payToAddress(receiver3, new Amount(LOVELACE, adaToLovelace(2.13)))
                .payToAddress(receiver2, Amount.ada(1.5))
                .from(sender2.baseAddress());

        Result<String> result = quickTxBuilder.compose(tx1, tx2)
                .feePayer(sender1.baseAddress())
                .withSigner(SignerProviders.signerFrom(sender1)
                        .andThen(SignerProviders.signerFrom(sender2)))
                .withSigner(SignerProviders.signerFrom(policy))
                .additionalSignersCount(1) //As we have composed TxSigners from 2 signers, we need to add 1 additional signer,
                // as it's hard to determine how many signers are in the composed TxSigner
                .withVerifier(
                        outputAmountVerifier(receiver2, Amount.ada(3.0))
                                .andThen(outputAmountVerifier(receiver3, Amount.ada(2.13)))
                                .andThen(outputAmountVerifier(receiver2, Amount.asset(policy.getPolicyId(), assetName, qty)))
                ).complete();

        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);

        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    void minting_withTransfer_andFailedVerifier() throws CborSerializationException {
        Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("test_policy", 1, 1);
        String assetName = "MyAsset";
        BigInteger qty = BigInteger.valueOf(2000);

        Tx tx1 = new Tx()
                .payToAddress(receiver2, Amount.ada(1.5))
                .mintAssets(policy.getPolicyScript(), new Asset(assetName, qty), receiver2)
                .attachMetadata(MessageMetadata.create().add("Minting tx"))
                .from(sender1.baseAddress());

        Tx tx2 = new Tx()
                .payToAddress(receiver3, new Amount(LOVELACE, adaToLovelace(2.13)))
                .payToAddress(receiver2, Amount.ada(1.5))
                .from(sender2.baseAddress());

        assertThrows(VerifierException.class, () -> {
            Result<String> result = quickTxBuilder.compose(tx1, tx2)
                    .feePayer(sender1.baseAddress())
                    .withSigner(SignerProviders.signerFrom(sender1)
                            .andThen(SignerProviders.signerFrom(sender2)))
                    .withSigner(SignerProviders.signerFrom(policy))
                    .additionalSignersCount(1)
                    .postBalanceTx((context, txn) -> { //Update tx output to fail the verifier
                        TransactionOutput txOut = txn.getBody().getOutputs()
                                .stream().filter(output -> output.getAddress().equals(receiver2))
                                .findFirst().get();
                        txOut.getValue().getMultiAssets().get(0).getAssets().get(0).setValue(BigInteger.valueOf(10000));
                    })
                    .withVerifier(
                            outputAmountVerifier(receiver2, Amount.ada(3.0))
                                    .andThen(outputAmountVerifier(receiver3, Amount.ada(2.13)))
                                    .andThen(outputAmountVerifier(receiver2, Amount.asset(policy.getPolicyId(), assetName, qty)))
                    ).complete();

        });
    }

    @Test
    void mint_samepolicy_withdifferent_mintAssets() throws Exception {
        Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("test_policy", 1, 1);
        String assetName1 = "_Asset1";
        BigInteger qty1 = BigInteger.valueOf(2000);
        String assetName2 = "_Asset2";
        BigInteger qty2 = BigInteger.valueOf(5000);
        String assetName3 = "_Asset2";
        BigInteger qty3 = BigInteger.valueOf(500);

        Tx tx1 = new Tx()
                .mintAssets(policy.getPolicyScript(), new Asset(assetName1, qty1), receiver1)
                .mintAssets(policy.getPolicyScript(), new Asset(assetName2, qty2), receiver2)
                .mintAssets(policy.getPolicyScript(), new Asset(assetName3, qty3), receiver2)
                .attachMetadata(MessageMetadata.create().add("Minting tx"))
                .from(sender1.baseAddress());

        Result<String> result = quickTxBuilder.compose(tx1)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(policy))
                .completeAndWait();

        System.out.println(result);
        assertTrue(result.isSuccessful());

        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    void mint_samepolicy_withdifferent_mintAssets_withdifferent_policy() throws Exception {
        Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("test_policy", 1, 1);
        String assetName1 = "_Asset1";
        BigInteger qty1 = BigInteger.valueOf(2000);
        String assetName2 = "_Asset2";
        BigInteger qty2 = BigInteger.valueOf(5000);
        String assetName3 = "_Asset2";
        BigInteger qty3 = BigInteger.valueOf(500);

        Policy policy2 = PolicyUtil.createMultiSigScriptAtLeastPolicy("second_policy", 1, 1);
        String assetName4 = "_Asset4";
        BigInteger qty4 = BigInteger.valueOf(400);

        String assetName5 = "_Asset4";
        BigInteger qty5 = BigInteger.valueOf(200);

        Tx tx1 = new Tx()
                .mintAssets(policy.getPolicyScript(), new Asset(assetName1, qty1), receiver1)
                .mintAssets(policy.getPolicyScript(), new Asset(assetName2, qty2), receiver2)
                .mintAssets(policy.getPolicyScript(), new Asset(assetName3, qty3), receiver2)
                .mintAssets(policy2.getPolicyScript(), new Asset(assetName4, qty4), receiver2)
                .mintAssets(policy2.getPolicyScript(), new Asset(assetName5, qty5), receiver2)
                .attachMetadata(MessageMetadata.create().add("Minting tx"))
                .from(sender1.baseAddress());

        Result<String> result = quickTxBuilder.compose(tx1)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(policy))
                .withSigner(SignerProviders.signerFrom(policy2))
                .completeAndWait(System.out::println);

        System.out.println(result);
        assertTrue(result.isSuccessful());

        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    void mint_and_burn_test() throws CborSerializationException {
        Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("test_policy", 1, 1);
        String assetName = "MyAsset";
        BigInteger qty = BigInteger.valueOf(2000);

        String assetName2 = "MyAsset2";
        BigInteger qty2 = BigInteger.valueOf(300);

        Tx tx1 = new Tx()
                .mintAssets(policy.getPolicyScript(), new Asset(assetName, qty), sender1Addr)
                .mintAssets(policy.getPolicyScript(), new Asset(assetName2, qty2), sender1Addr)
                .attachMetadata(MessageMetadata.create().add("Minting tx"))
                .from(sender1.baseAddress());

        Result<String> result = quickTxBuilder.compose(tx1)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(policy))
                .completeAndWait(System.out::println);

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);

        Asset burnAsset = new Asset(assetName, BigInteger.valueOf(200).negate()); //negative value for asset
        Asset burnAsset2 = new Asset(assetName, BigInteger.valueOf(500).negate());
        Tx burnTx = new Tx()
                .payToAddress(receiver1, Amount.ada(1.0))
                .mintAssets(policy.getPolicyScript(), List.of(burnAsset))
                .mintAssets(policy.getPolicyScript(), List.of(burnAsset2))
                .attachMetadata(MessageMetadata.create().add("Burning tx"))
                .from(sender1.baseAddress());

        Result<String> burnResult = quickTxBuilder.compose(burnTx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(policy))
                .completeAndWait(System.out::println);

        System.out.println(burnResult);
        assertTrue(burnResult.isSuccessful());
    }

    @Test
    void simplePayment_mergeOutputsIsTrue() throws CborSerializationException {
        Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("test_policy", 1, 1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx = new Tx()
                .payToAddress(sender1Addr, Amount.ada(1))
                .mintAssets(policy.getPolicyScript(), new Asset("MyAsset", BigInteger.valueOf(1)), receiver1)
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(policy))
                .withTxInspector(transaction -> {
                    assertThat(transaction.getBody().getOutputs()).hasSize(2);
                    assertThat(transaction.getBody().getOutputs().get(0).getAddress()).isEqualTo(receiver1);
                    assertThat(transaction.getBody().getOutputs().get(0).getValue().getCoin()).isGreaterThanOrEqualTo(adaToLovelace(1));
                    assertThat(transaction.getBody().getOutputs().get(0).getValue().getMultiAssets()).hasSize(1);

                    assertThat(transaction.getBody().getOutputs().get(1).getAddress()).isEqualTo(sender1Addr);
                    assertThat(transaction.getBody().getOutputs().get(1).getValue().getCoin()).isGreaterThanOrEqualTo(adaToLovelace(1));
                })
                .completeAndWait(System.out::println);

        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    void simplePayment_mergeOutputsIsFalse() throws CborSerializationException {
        Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("test_policy", 1, 1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx = new Tx()
                .payToAddress(sender1Addr, Amount.ada(1))
                .mintAssets(policy.getPolicyScript(), new Asset("MyAsset", BigInteger.valueOf(1)), receiver1)
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(policy))
                .mergeOutputs(false)
                .withTxInspector(transaction -> {
                    assertThat(transaction.getBody().getOutputs()).hasSize(3);
                    assertThat(transaction.getBody().getOutputs().get(0).getAddress()).isEqualTo(sender1Addr);
                    assertThat(transaction.getBody().getOutputs().get(0).getValue().getCoin()).isEqualTo(adaToLovelace(1));
                    assertThat(transaction.getBody().getOutputs().get(0).getValue().getMultiAssets()).hasSize(0);

                    assertThat(transaction.getBody().getOutputs().get(1).getAddress()).isEqualTo(receiver1);
                    assertThat(transaction.getBody().getOutputs().get(1).getValue().getCoin()).isGreaterThanOrEqualTo(adaToLovelace(1));
                    assertThat(transaction.getBody().getOutputs().get(1).getValue().getMultiAssets()).hasSize(1);

                    assertThat(transaction.getBody().getOutputs().get(2).getAddress()).isEqualTo(sender1Addr);
                    assertThat(transaction.getBody().getOutputs().get(2).getValue().getCoin()).isGreaterThan(adaToLovelace(1));
                })
                .completeAndWait(System.out::println);

        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    void twoTxs_withSameSender() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx1 = new Tx()
                .payToAddress(receiver1, Amount.ada(1))
                .from(sender1Addr);

        Tx tx2 = new Tx()
                .payToAddress(receiver2, Amount.ada(1))
                .from(sender1Addr);

        assertThrows(TxBuildException.class, () -> {
            Result<String> result = quickTxBuilder.compose(tx1, tx2)
                    .feePayer(sender1Addr)
                    .withSigner(SignerProviders.signerFrom(sender1))
                    .completeAndWait(System.out::println);
        });
    }

    @Test
    void twoTxs_withDifferentSenders() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx1 = new Tx()
                .payToAddress(receiver1, Amount.ada(1))
                .from(sender1Addr);

        Tx tx2 = new Tx()
                .payToAddress(receiver2, Amount.ada(1))
                .from(sender2Addr);

        Result<String> result = quickTxBuilder.compose(tx1, tx2)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    void donateToTreasury() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx = new Tx()
                .donateToTreasury(BigInteger.ZERO, adaToLovelace(2))
                .attachMetadata(MessageMetadata.create().add("This is a treasury donation message"))
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);

        assertThat(result.isSuccessful()).isTrue();
    }

    @Test
    void donate_withOtherPayment() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx = new Tx()
                .donateToTreasury(BigInteger.ZERO, adaToLovelace(3.5))
                .payToAddress(receiver1, Amount.ada(1.5))
                .payToAddress(receiver2, Amount.ada(2.5))
                .attachMetadata(MessageMetadata.create().add("This is a treasury donation message"))
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);

        assertThat(result.isSuccessful()).isTrue();
    }

}
