package com.bloxbean.cardano.hdwallet;

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
import com.bloxbean.cardano.hdwallet.utxosupplier.DefaultWalletUtxoSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class QuickTxBuilderIT extends QuickTxBaseIT {

    BackendService backendService;
    UtxoSupplier utxoSupplier;
    DefaultWalletUtxoSupplier walletUtxoSupplier;
    Wallet wallet1;
    Wallet wallet2;

    @BeforeEach
    void setup() {
        backendService = getBackendService();
        utxoSupplier = getUTXOSupplier();

        walletUtxoSupplier = new DefaultWalletUtxoSupplier(backendService.getUtxoService());
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


        UtxoSupplier walletUtxoSupplier = new DefaultWalletUtxoSupplier(backendService.getUtxoService(), wallet1);
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService, walletUtxoSupplier);

        Tx tx = new Tx()
                .payToAddress(wallet2.getBaseAddress(0).getAddress(), Amount.ada(5))
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