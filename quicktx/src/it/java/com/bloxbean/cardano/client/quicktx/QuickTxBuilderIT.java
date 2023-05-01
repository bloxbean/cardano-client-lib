package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.util.PolicyUtil;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.cip.cip20.MessageMetadata;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.AuxiliaryData;
import com.bloxbean.cardano.client.transaction.spec.Policy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QuickTxBuilderIT extends QuickTxBaseIT {
    BackendService backendService;
    Account sender;
    Account sender2;

    String receiver1 = "addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c";
    String receiver2 = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
    String receiver3 = "addr_test1qqqvjp4ffcdqg3fmx0k8rwamnn06wp8e575zcv8d0m3tjn2mmexsnkxp7az774522ce4h3qs4tjp9rxjjm46qf339d9sk33rqn";

    QuickTxBuilder quickTxBuilder;

    @BeforeEach
    void setup() {
        backendService = getBackendService();
        quickTxBuilder = new QuickTxBuilder(backendService);

        //addr_test1qp73ljurtknpm5fgey5r2y9aympd33ksgw0f8rc5khheg83y35rncur9mjvs665cg4052985ry9rzzmqend9sqw0cdksxvefah
        String senderMnemonic = "drive useless envelope shine range ability time copper alarm museum near flee wrist live type device meadow allow churn purity wisdom praise drop code";
        sender = new Account(Networks.testnet(), senderMnemonic);

        //addr_test1qz5fcpvkg7pekqvv9ld03t5sx2w2c2fac67fzlaxw5844s83l4p6tr389lhgcpe4797kt7xkcxqvcc4a6qjshzsmta8sh3ncs4
        String sender2Mnemonic = "access else envelope between rubber celery forum brief bubble notice stomach add initial avocado current net film aunt quick text joke chase robust artefact";
        sender2 = new Account(Networks.testnet(), sender2Mnemonic);
    }

    @Test
    void simplePayment() {
        Metadata metadata = MetadataBuilder.createMetadata();
        metadata.put(BigInteger.valueOf(100), "This is second metadata");
        metadata.putNegative(200, -900);

        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(1.5))
                .payToAddress(receiver2, Amount.ada(2.5))
                .attachMetadata(MessageMetadata.create().add("This is a test message 2"))
                .attachMetadata(metadata)
                .from(sender);

        Result<String> result = quickTxBuilder.create(tx)
                .complete();

        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);
    }

    @Test
    void simplePayment_compose() {
        Tx tx1 = new Tx()
                .payToAddress(receiver1, Amount.ada(1.5))
                .payToAddress(receiver2, Amount.ada(2.5))
                .attachMetadata(MessageMetadata.create().add("This is a test message 2"))
                .from(sender);

        Tx tx2 = new Tx()
                .payToAddress(receiver3, Amount.ada(4.5))
                .from(sender2);

        Result<String> result = quickTxBuilder
                .create(tx1, tx2)
                .feePayer(sender.baseAddress())
                .complete();

        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);
    }

    @Nested
    class MintingTests {
        @Test
        void minting() throws CborSerializationException {
            Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("test_policy", 1, 1);
            String assetName = "MyAsset";
            BigInteger qty = BigInteger.valueOf(1000);

            Tx tx = new Tx()
                    .mintAssets(policy.getPolicyScript(), new Asset(assetName, qty), sender.baseAddress())
                    .attachMetadata(MessageMetadata.create().add("Minting tx"))
                    .from(sender)
                    .withSigner(SignerProviders.signerFrom(policy));

            Result<String> result = quickTxBuilder.create(tx)
                    .complete();

            System.out.println(result);
            assertTrue(result.isSuccessful());
            waitForTransaction(result);
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
                    .from(sender)
                    .withSigner(SignerProviders.signerFrom(policy));

            Tx tx2 = new Tx()
                    .payToAddress(receiver3, new Amount(LOVELACE, adaToLovelace(2.13)))
                    .from(sender2);

            Result<String> result = quickTxBuilder.create(tx1, tx2)
                    .feePayer(sender.baseAddress())
                    .complete();

            System.out.println(result);
            assertTrue(result.isSuccessful());
            waitForTransaction(result);
        }

        @Test
        void minting_transferMintedToTwoAccounts() throws CborSerializationException {
            Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("test_policy", 1, 1);
            String assetName = "MyAsset";
            BigInteger qty = BigInteger.valueOf(2000);

            Tx tx1 = new Tx()
                    .payToAddress(receiver1, Amount.asset(policy.getPolicyId(), assetName, 200), true)
                    .payToAddress(receiver2, List.of(Amount.ada(1.5), Amount.asset(policy.getPolicyId(), assetName, 1800)), true)
                    .mintAssets(policy.getPolicyScript(), new Asset(assetName, qty))
                    .attachMetadata(MessageMetadata.create().add("Minting tx"))
                    .from(sender)
                    .withSigner(SignerProviders.signerFrom(policy));

            Tx tx2 = new Tx()
                    .payToAddress(receiver3, Amount.ada(2.13))
                    .from(sender2);


            Result<String> result = quickTxBuilder.create(tx1, tx2)
                    .feePayer(sender2.baseAddress())
                    .complete();

            System.out.println(result);
            assertTrue(result.isSuccessful());
            waitForTransaction(result);
        }
    }

    @Nested
    class CustomChangeAddress {
        @Test
        void simplePayment_customChangeAddress() {
            //Send 4 ada to a new change account
            Account changeAccount = new Account(Networks.testnet());
            Tx tx = new Tx()
                    .payToAddress(changeAccount.baseAddress(), Amount.ada(4.0))
                    .from(sender);

            Result<String> result = quickTxBuilder.create(tx)
                    .complete();
            System.out.println(result);
            assertTrue(result.isSuccessful());
            waitForTransaction(result);

            //Now create another tx with custom change address
            Tx tx2 = new Tx()
                    .payToAddress(receiver1, Amount.ada(1.1))
                    .withChangeAddress(sender.baseAddress())
                    .from(changeAccount);

            Result<String> result2 = quickTxBuilder.create(tx2)
                    .complete();
            System.out.println(result2);
            assertTrue(result2.isSuccessful());
            waitForTransaction(result2);
        }

        @Test
        void simplePayment_customChangeAddress_multipleTxs_requiredFeePayer() {
            //Send 4 ada to a new change account
            Account changeAccount = new Account(Networks.testnet());
            Tx tx = new Tx()
                    .payToAddress(changeAccount.baseAddress(), Amount.ada(4.0))
                    .from(sender);

            Result<String> result = quickTxBuilder.create(tx)
                    .complete();
            System.out.println(result);
            assertTrue(result.isSuccessful());
            waitForTransaction(result);

            //Now create another tx with custom change address
            Tx tx2 = new Tx()
                    .payToAddress(receiver1, Amount.ada(1.1))
                    .withChangeAddress(sender.baseAddress())
                    .from(changeAccount);

            Tx tx3 = new Tx()
                    .payToAddress(receiver2, Amount.ada(2.1))
                    .from(sender2);

            Result<String> result2 = quickTxBuilder.create(tx2, tx3)
                    .feePayer(sender.baseAddress())
                    .complete();
            System.out.println(result2);
            assertTrue(result2.isSuccessful());
            waitForTransaction(result2);
        }
    }

    @Test
    void simplePayment_withPreAndPostBalanceBuilder() {
        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(1.5))
                .payToAddress(receiver2, Amount.ada(2.5))
                .attachMetadata(MessageMetadata.create().add("This is a test message 2"))
                .from(sender);

        Result<String> result = quickTxBuilder
                .create(tx)
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
    }

}
