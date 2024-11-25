package com.bloxbean.cardano.hdwallet;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.PolicyUtil;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.cip.cip20.MessageMetadata;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.Policy;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.bloxbean.cardano.hdwallet.model.WalletUtxo;
import com.bloxbean.cardano.hdwallet.supplier.DefaultWalletUtxoSupplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class QuickTxBuilderIT extends QuickTxBaseIT {

    BackendService backendService;
    UtxoSupplier utxoSupplier;
    DefaultWalletUtxoSupplier walletUtxoSupplier;
    Wallet wallet1;
    Wallet wallet2;

    static Account topupAccount;

    @BeforeAll
    static void beforeAll() {
        String topupAccountMnemonic = "weapon news intact viable rigid hope ginger defy remove enemy dog volume belt clay shuffle angle crunch eye end asthma arctic sphere arm limit";
        topupAccount = new Account(Networks.testnet(), topupAccountMnemonic);

        topUpFund(topupAccount.baseAddress(), 100000);
        topUpFund("addr_test1qz5t8wq55e09usmh07ymxry8atzwxwt2nwwzfngg6esffxvw2pfap6uqmkj3n6zmlrsgz397md2gt7yqs5p255uygaesx608y5", 5);
        System.out.println("Topup address : " + topupAccount.baseAddress());
    }

    @BeforeEach
    void setup() {
        backendService = getBackendService();
        utxoSupplier = getUTXOSupplier();

        String wallet1Mnemonic = "clog book honey force cricket stamp until seed minimum margin denial kind volume undo simple federal then jealous solid legal crucial crazy acoustic thank";
        wallet1 = new Wallet(Networks.testnet(), wallet1Mnemonic);
        String wallet2Mnemonic = "theme orphan remind output arrive lobster decorate ten gap piece casual distance attend total blast dilemma damp punch pride file limit soldier plug canoe";
        wallet2 = new Wallet(Networks.testnet(), wallet2Mnemonic);

        walletUtxoSupplier = new DefaultWalletUtxoSupplier(backendService.getUtxoService(), wallet1);
    }

    @Test
    void simplePayment() {
        Metadata metadata = MetadataBuilder.createMetadata();
        metadata.put(BigInteger.valueOf(100), "This is first metadata");
        metadata.putNegative(200, -900);

        //topup wallet
        splitPaymentBetweenAddress(topupAccount, wallet1, 20, Double.valueOf(50000));


        UtxoSupplier walletUtxoSupplier = new DefaultWalletUtxoSupplier(backendService.getUtxoService(), wallet1);
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService, walletUtxoSupplier);

        Tx tx = new Tx()
                .payToAddress(wallet2.getBaseAddress(0).getAddress(), Amount.ada(50000))
                .from(wallet1);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(wallet1))
                .withTxInspector(txn -> {
                    System.out.println(JsonUtil.getPrettyJson(txn));
                })
                .complete();

        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);

        checkIfUtxoAvailable(result.getValue(), wallet2.getBaseAddress(0).getAddress());
    }

    @Test
    void simplePayment_withIndexesToScan() {
        String mnemonic = "buzz sentence empty coffee manage grid claw street misery deputy direct seek tortoise wedding stay twist crew august omit taste expect obscure abandon iron";
        Wallet wallet = new Wallet(Networks.testnet(), mnemonic);
        wallet.setIndexesToScan(new int[]{5, 30, 45});

        //topup index 5, 45
        topUpFund(wallet.getBaseAddressString(5), 5);
        topUpFund(wallet.getBaseAddressString(45), 15);

        UtxoSupplier walletUtxoSupplier = new DefaultWalletUtxoSupplier(backendService.getUtxoService(), wallet);
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService, walletUtxoSupplier);

        Tx tx = new Tx()
                .payToAddress(wallet2.getBaseAddress(0).getAddress(), Amount.ada(18))
                .from(wallet);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(wallet))
                .withTxInspector(txn -> {
                    System.out.println(JsonUtil.getPrettyJson(txn));
                })
                .complete();

        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);

        checkIfUtxoAvailable(result.getValue(), wallet2.getBaseAddress(0).getAddress());
    }

    @Test
    void minting() throws CborSerializationException {
        Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("test_policy", 1, 1);
        String assetName = "MyAsset";
        BigInteger qty = BigInteger.valueOf(1000);

        Tx tx = new Tx()
                .mintAssets(policy.getPolicyScript(), new Asset(assetName, qty), wallet1.getBaseAddress(0).getAddress())
                .attachMetadata(MessageMetadata.create().add("Minting tx"))
                .from(wallet1);

        UtxoSupplier walletUtxoSupplier = new DefaultWalletUtxoSupplier(backendService.getUtxoService(), wallet1);
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService, walletUtxoSupplier);
        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(wallet1))
                .withSigner(SignerProviders.signerFrom(policy))
                .complete();

        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);

        checkIfUtxoAvailable(result.getValue(), wallet1.getBaseAddress(0).getAddress());
    }

    @Test
    void utxoTest() {
        List<WalletUtxo> utxos = walletUtxoSupplier.getAll();
        Map<String, Integer> amountMap = new HashMap<>();
        for (Utxo utxo : utxos) {
            int totalAmount = 0;
            if (amountMap.containsKey(utxo.getAddress())) {
                int amount = amountMap.get(utxo.getAddress());
                System.out.println(utxo.getAmount().get(0));
                totalAmount = amount + utxo.getAmount().get(0).getQuantity().intValue();
            }
            amountMap.put(utxo.getAddress(), totalAmount);
        }

        assertTrue(!utxos.isEmpty());
    }

    void splitPaymentBetweenAddress(Account topupAccount, Wallet receiverWallet, int totalAddresses, Double adaAmount) {
        // Create an amount array with no of totalAddresses with random distribution of split amounts
        Double[] amounts = new Double[totalAddresses];
        Double remainingAmount = adaAmount;
        Random rand = new Random();

        for (int i = 0; i < totalAddresses - 1; i++) {
            Double randomAmount = Double.valueOf(rand.nextInt(remainingAmount.intValue()));
            amounts[i] = randomAmount;
            remainingAmount = remainingAmount - randomAmount;
        }
        amounts[totalAddresses - 1] = remainingAmount;

        String[] addresses = new String[totalAddresses];
        Random random = new Random();
        int currentIndex = 0;

        for (int i = 0; i < totalAddresses; i++) {
            addresses[i] = receiverWallet.getBaseAddressString(currentIndex);
            currentIndex += random.nextInt(20) + 1;
        }

        Tx tx = new Tx();
        for (int i = 0; i < addresses.length; i++) {
            tx.payToAddress(addresses[i], Amount.ada(amounts[i]));
        }

        tx.from(topupAccount.baseAddress());

        var result = new QuickTxBuilder(backendService)
                .compose(tx)
                .withSigner(SignerProviders.signerFrom(topupAccount))
                .completeAndWait();

        System.out.println(result);
    }
}
