package com.bloxbean.cardano.hdwallet;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelectionStrategyImpl;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class QuickTxBuilderIT extends QuickTXBaseIT {

    BackendService backendService;
    UtxoSupplier utxoSupplier;
    WalletUtxoSupplier walletUtxoSupplier;
    Wallet wallet1;
    Wallet wallet2;

    @BeforeEach
    void setup() {
        backendService = getBackendService();
        utxoSupplier = getUTXOSupplier();
        walletUtxoSupplier = new WalletUtxoSupplier(backendService.getUtxoService());
        String wallet1Mnemonic = "clog book honey force cricket stamp until seed minimum margin denial kind volume undo simple federal then jealous solid legal crucial crazy acoustic thank";
        wallet1 = new Wallet(Networks.testnet(), wallet1Mnemonic, walletUtxoSupplier);
        String wallet2Mnemonic = "theme orphan remind output arrive lobster decorate ten gap piece casual distance attend total blast dilemma damp punch pride file limit soldier plug canoe";
        wallet2 = new Wallet(Networks.testnet(), wallet2Mnemonic, walletUtxoSupplier);
    }

    @Test
    void simplePayment() {
        Metadata metadata = MetadataBuilder.createMetadata();
        metadata.put(BigInteger.valueOf(100), "This is first metadata");
        metadata.putNegative(200, -900);


        UtxoSupplier walletUtxoSupplier = new WalletUtxoSupplier(backendService.getUtxoService(), wallet1);
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService, walletUtxoSupplier);

        Tx tx = new Tx()
                .payToAddress(wallet2.getBaseAddress(0).getAddress(), Amount.ada(4))
                .from(wallet1);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(wallet1))
                .complete();

        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);

        checkIfUtxoAvailable(result.getValue(), wallet2.getBaseAddress(0).getAddress());
    }

    @Test
    void simplePayment2() {
        Metadata metadata = MetadataBuilder.createMetadata();
        metadata.put(BigInteger.valueOf(100), "This is first metadata");
        metadata.putNegative(200, -900);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx = new Tx()
                .payToAddress(wallet2.getBaseAddress(0).getAddress(), Amount.ada(3))

                .from(wallet1.getBaseAddress(0).getAddress()); // TODO - Set a HDWallet here

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(wallet1.getSigner(0)))
                .complete();

        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);

        checkIfUtxoAvailable(result.getValue(), wallet2.getBaseAddress(0).getAddress());
    }

    @Test
    void simplePayment3() {
        Metadata metadata = MetadataBuilder.createMetadata();
        metadata.put(BigInteger.valueOf(100), "This is first metadata");
        metadata.putNegative(200, -900);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx = new Tx()
                .payToAddress(wallet1.getBaseAddress(0).getAddress(), Amount.ada(7))
                .from(wallet2); // TODO - Set a HDWallet here
        QuickTxBuilder.TxContext compose = quickTxBuilder.compose(tx);

        compose = compose.withSigner(SignerProviders.signerFrom(wallet2.getSigner(0)));
        compose = compose.withSigner(SignerProviders.signerFrom(wallet2.getSigner(1)));

        Result<String> result = compose.complete();

        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);

        checkIfUtxoAvailable(result.getValue(), wallet2.getBaseAddress(0).getAddress());
    }

    @Test
    void utxoTest() {
        List<Utxo> utxos = walletUtxoSupplier.getAll(wallet1);
        Map<String, Integer> amountMap = new HashMap<>();
        for (Utxo utxo : utxos) {
            int totalAmount = 0;
            if(amountMap.containsKey(utxo.getAddress())) {
                int amount = amountMap.get(utxo.getAddress());
                System.out.println(utxo.getAmount().get(0));
                totalAmount= amount + utxo.getAmount().get(0).getQuantity().intValue();
            }
            amountMap.put(utxo.getAddress(), totalAmount);
        }

        assertTrue(!utxos.isEmpty());
    }
}