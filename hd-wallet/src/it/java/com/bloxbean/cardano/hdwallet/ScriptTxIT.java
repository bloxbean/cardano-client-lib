package com.bloxbean.cardano.hdwallet;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.ScriptUtxoFinders;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusV3Script;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.TxStatus;
import com.bloxbean.cardano.client.quicktx.verifiers.TxVerifiers;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScriptTxIT extends QuickTxBaseIT {

    BackendService backendService;
    UtxoSupplier utxoSupplier;

    String receiver1 = "addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c";

    static Account topupAccount;

    @BeforeAll
    static void beforeAll() {
        String topupAccountMnemonic = "weapon news intact viable rigid hope ginger defy remove enemy dog volume belt clay shuffle angle crunch eye end asthma arctic sphere arm limit";
        topupAccount = new Account(Networks.testnet(), topupAccountMnemonic);

        topUpFund(topupAccount.baseAddress(), 100000);
    }

    @BeforeEach
    void setup() {
        backendService = getBackendService();
        utxoSupplier = getUTXOSupplier();
    }

    @Test
    void alwaysTrueScript() throws ApiException {
        Wallet walletA = Wallet.create(Networks.testnet());
        Wallet walletB = Wallet.create(Networks.testnet());

        splitPaymentBetweenAddress(topupAccount, walletA, 20, Double.valueOf(3000), false);
        payToAddressAt(topupAccount, walletB, 13, Double.valueOf(5));

        PlutusV3Script plutusScript = PlutusV3Script.builder()
                .type("PlutusScriptV3")
                .cborHex("46450101002499")
                .build();

        String scriptAddress = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).toBech32();
        BigInteger scriptAmt = new BigInteger("2479280");

        long randInt = System.currentTimeMillis();
        BigIntPlutusData plutusData = new BigIntPlutusData(BigInteger.valueOf(randInt));

        Tx tx = new Tx();
        tx.payToContract(scriptAddress, Amount.lovelace(scriptAmt), plutusData)
                .from(walletA);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        var result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(walletA))
                .complete();

        assertThat(result.getTxStatus()).isEqualTo(TxStatus.SUBMITTED);

        System.out.println(result.getResponse());
        checkIfUtxoAvailable(result.getValue(), scriptAddress);

        Optional<Utxo> optionalUtxo = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddress, plutusData);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(optionalUtxo.get(), plutusData)
                .payToAddress(receiver1, Amount.lovelace(scriptAmt))
                .attachSpendingValidator(plutusScript)
                .withChangeAddress(scriptAddress, plutusData);

        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(walletB)
                .withSigner(SignerProviders.signerFrom(walletB))
                .withVerifier(TxVerifiers.outputAmountVerifier(walletB.getBaseAddressString(0),
                        LOVELACE,
                        amt -> (Amount.ada(5).getQuantity().compareTo(amt.getQuantity()) > 0)))
                .withTxInspector(txn -> {
                    assertThat(txn.getBody().getOutputs()).hasSize(2);
                    assertThat(txn.getBody().getInputs()).hasSize(2);
                })
                .completeAndWait(System.out::println);

        System.out.println(result1);
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), receiver1);
    }

    @Test
    void alwaysTrueScript_separateCollateralPayer() throws ApiException {
        Wallet walletA = Wallet.create(Networks.testnet());
        Wallet walletB = Wallet.create(Networks.testnet());
        Wallet collaterWallet = Wallet.create(Networks.testnet());

        splitPaymentBetweenAddress(topupAccount, walletA, 20, Double.valueOf(3000), false);
        payToAddressAt(topupAccount, walletB, 13, Double.valueOf(5));
        payToAddressAt(topupAccount, collaterWallet, 2, Double.valueOf(7));

        PlutusV3Script plutusScript = PlutusV3Script.builder()
                .type("PlutusScriptV3")
                .cborHex("46450101002499")
                .build();

        String scriptAddress = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).toBech32();
        BigInteger scriptAmt = new BigInteger("2479280");

        long randInt = System.currentTimeMillis();
        BigIntPlutusData plutusData = new BigIntPlutusData(BigInteger.valueOf(randInt));

        Tx tx = new Tx();
        tx.payToContract(scriptAddress, Amount.lovelace(scriptAmt), plutusData)
                .from(walletA);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        var result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(walletA))
                .complete();

        assertThat(result.getTxStatus()).isEqualTo(TxStatus.SUBMITTED);

        System.out.println(result.getResponse());
        checkIfUtxoAvailable(result.getValue(), scriptAddress);

        Optional<Utxo> optionalUtxo = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddress, plutusData);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(optionalUtxo.get(), plutusData)
                .payToAddress(receiver1, Amount.lovelace(scriptAmt))
                .attachSpendingValidator(plutusScript)
                .withChangeAddress(scriptAddress, plutusData);

        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(walletB)
                .collateralPayer(collaterWallet)
                .withSigner(SignerProviders.signerFrom(walletB))
                .withSigner(SignerProviders.signerFrom(collaterWallet))
                .withVerifier(TxVerifiers.outputAmountVerifier(walletB.getBaseAddressString(0),
                        LOVELACE,
                        amt -> (Amount.ada(5).getQuantity().compareTo(amt.getQuantity()) > 0)))
                .withTxInspector(txn -> {
                    assertThat(txn.getBody().getOutputs()).hasSize(2);
                    assertThat(txn.getBody().getInputs()).hasSize(2);

                    //check if the correct collateral is selected
                    List<Utxo> collWalletUtxos = utxoSupplier.getAll(collaterWallet.getBaseAddressString(2));
                    var collWalletUtxoTIs = collWalletUtxos.stream().map(utxo -> new TransactionInput(utxo.getTxHash(), utxo.getOutputIndex()))
                                    .collect(Collectors.toList());
                    assertThat(collWalletUtxoTIs).contains(txn.getBody().getCollateral().get(0));
                })
                .completeAndWait(System.out::println);

        System.out.println(result1);
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), receiver1);
    }

    void splitPaymentBetweenAddress(Account topupAccount, Wallet receiverWallet, int totalAddresses, Double adaAmount, boolean enableEntAddrPayment) {
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
            if (enableEntAddrPayment) {
                if (i % 2 == 0)
                    addresses[i] = receiverWallet.getBaseAddressString(currentIndex);
                else
                    addresses[i] = receiverWallet.getEntAddress(currentIndex).toBech32();
            } else {
                addresses[i] = receiverWallet.getBaseAddressString(currentIndex);
            }

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

    void payToAddressAt(Account topupAccount, Wallet receiverWallet, int index, Double adaAmount) {
        Tx tx = new Tx()
                .payToAddress(receiverWallet.getBaseAddressString(index), Amount.ada(adaAmount))
                .from(topupAccount.baseAddress());

        var result = new QuickTxBuilder(backendService)
                .compose(tx)
                .withSigner(SignerProviders.signerFrom(topupAccount))
                .completeAndWait();

        System.out.println(result);
    }

}
