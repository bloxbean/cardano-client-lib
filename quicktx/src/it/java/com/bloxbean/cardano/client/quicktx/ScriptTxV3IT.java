package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.aiken.AikenTransactionEvaluator;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.model.TxContentRedeemers;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.ScriptUtxoFinders;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScriptTxV3IT extends TestDataBaseIT {

    private boolean aikenEvaluation = false;

    @Test
    void alwaysTrueScript() throws ApiException {
        PlutusV3Script plutusScript = PlutusV3Script.builder()
                .type("PlutusScriptV3")
                .cborHex("46450101002499")
                .build();

        String scriptAddress = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).toBech32();
        BigInteger scriptAmt = new BigInteger("2479280");

        Random rand = new Random();
        long randInt = System.currentTimeMillis();
        BigIntPlutusData plutusData = new BigIntPlutusData(BigInteger.valueOf(randInt)); //any random number

        Tx tx = new Tx();
        tx.payToContract(scriptAddress, Amount.lovelace(scriptAmt), plutusData)
                .from(sender2Addr);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);

        System.out.println(result.getResponse());
        checkIfUtxoAvailable(result.getValue(), scriptAddress);

        Optional<Utxo> optionalUtxo = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddress, plutusData);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(optionalUtxo.get(), plutusData)
                .payToAddress(receiver1, Amount.lovelace(scriptAmt))
                .attachSpendingValidator(plutusScript)
                .withChangeAddress(scriptAddress, plutusData);

        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(sender2Addr)
                .withSigner(SignerProviders.signerFrom(sender2))
                .withRequiredSigners(sender2.getBaseAddress())
                .withVerifier(txn -> {
                    System.out.println(JsonUtil.getPrettyJson(txn));
                    assertThat(txn.getBody().getRequiredSigners()).hasSize(1);
                    assertThat(txn.getBody().getRequiredSigners().get(0)) //Verify sender's payment cred hash in required signer
                            .isEqualTo(sender2.getBaseAddress().getPaymentCredentialHash().get());
                })
                .completeAndWait(System.out::println);

        System.out.println(result1);
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), sender2Addr);

        // Example of getting the redeemer datum hash and then getting the datum values.
        List<TxContentRedeemers> redeemers = getBackendService().getTransactionService()
                .getTransactionRedeemers(result1.getValue()).getValue();
    }

    @Test
    void referenceInputUtxo_guessSumScript() throws ApiException, InterruptedException {
        //Sum Script
        PlutusV3Script sumScript =
                PlutusV3Script.builder()
                        .cborHex("46450101002499")
                        .build();
        String sumScriptAddr = AddressProvider.getEntAddress(sumScript, Networks.testnet()).toBech32();
        Amount sumScriptAmt = Amount.ada(4.0);
        PlutusData sumScriptDatum = new BigIntPlutusData(BigInteger.valueOf(8)); //redeemer should be 36
        PlutusData sumScriptRedeemer = new BigIntPlutusData(BigInteger.valueOf(36));

        //Create a reference input and send lock amount at script address
        Tx createRefInputTx = new Tx();
        createRefInputTx.payToAddress(receiver1, List.of(Amount.ada(1.0)), sumScript)
                .payToContract(sumScriptAddr, sumScriptAmt, sumScriptDatum)
                .from(sender1Addr);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder.compose(createRefInputTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);
        System.out.println("Tx Response: " + result.getResponse());
        assertTrue(result.isSuccessful());

        //Required as backend service returns outdated utxo
        if (result.isSuccessful()) {
            checkIfUtxoAvailable(result.getValue(), sender1Addr);
        }

        Utxo refUtxo = Utxo.builder()
                .txHash(result.getValue())
                .outputIndex(0)
                .build();

        //Pay to script
        Tx scriptPayTx = new Tx();
        scriptPayTx.payToContract(sumScriptAddr, sumScriptAmt, sumScriptDatum)
                .from(sender1Addr);
        result = quickTxBuilder.compose(scriptPayTx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);
        //Required as backend service returns outdated utxo
        if (result.isSuccessful()) {
            checkIfUtxoAvailable(result.getValue(), sender1Addr);
        }

        //Find the utxo for the script address
        Optional<Utxo> sumUtxo = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, sumScriptAddr, sumScriptDatum);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(sumUtxo.get(), sumScriptRedeemer)
                .readFrom(refUtxo)
                .payToAddress(receiver1, List.of(sumScriptAmt))
                .withChangeAddress(sumScriptAddr, sumScriptDatum);

        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxEvaluator(!backendType.equals(BLOCKFROST) && aikenEvaluation?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier, scriptHash -> sumScript): null)
                .completeAndWait(System.out::println);

        System.out.println(result1.getResponse());
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), sender1Addr);
    }


    @Test
    void referenceInputUtxo_guessSumScript_withRefScriptsCall() throws ApiException, InterruptedException {
        //Sum Script
        PlutusV3Script sumScript =
                PlutusV3Script.builder()
                        .cborHex("46450101002499")
                        .build();
        String sumScriptAddr = AddressProvider.getEntAddress(sumScript, Networks.testnet()).toBech32();
        Amount sumScriptAmt = Amount.ada(4.0);
        PlutusData sumScriptDatum = new BigIntPlutusData(BigInteger.valueOf(8)); //redeemer should be 36
        PlutusData sumScriptRedeemer = new BigIntPlutusData(BigInteger.valueOf(36));

        //Create a reference input and send lock amount at script address
        Tx createRefInputTx = new Tx();
        createRefInputTx.payToAddress(receiver1, List.of(Amount.ada(1.0)), sumScript)
                .payToContract(sumScriptAddr, sumScriptAmt, sumScriptDatum)
                .from(sender1Addr);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder.compose(createRefInputTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);
        System.out.println("Tx Response: " + result.getResponse());
        assertTrue(result.isSuccessful());

        //Required as backend service returns outdated utxo
        if (result.isSuccessful()) {
            checkIfUtxoAvailable(result.getValue(), sender1Addr);
        }

        Utxo refUtxo = Utxo.builder()
                .txHash(result.getValue())
                .outputIndex(0)
                .build();

        //Pay to script
        Tx scriptPayTx = new Tx();
        scriptPayTx.payToContract(sumScriptAddr, sumScriptAmt, sumScriptDatum)
                .from(sender1Addr);
        result = quickTxBuilder.compose(scriptPayTx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);
        //Required as backend service returns outdated utxo
        if (result.isSuccessful()) {
            checkIfUtxoAvailable(result.getValue(), sender1Addr);
        }

        //Find the utxo for the script address
        Optional<Utxo> sumUtxo = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, sumScriptAddr, sumScriptDatum);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(sumUtxo.get(), sumScriptRedeemer)
                .readFrom(refUtxo)
                .payToAddress(receiver1, List.of(sumScriptAmt))
                .withChangeAddress(sumScriptAddr, sumScriptDatum);

        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withReferenceScripts(sumScript)
                .withTxEvaluator(!backendType.equals(BLOCKFROST) && aikenEvaluation?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier, scriptHash -> sumScript): null)
                .completeAndWait(System.out::println);

        System.out.println(result1.getResponse());
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), sender1Addr);
    }

    @Test
    void plutusV2AndPlutusV3_withConwayEraFormat() throws ApiException {
        //Sum Script
        PlutusV3Script sumScript =
                PlutusV3Script.builder()
                        .cborHex("46450101002499")
                        .build();
        String sumScriptAddr = AddressProvider.getEntAddress(sumScript, Networks.testnet()).toBech32();
        Amount sumScriptAmt = Amount.ada(4.0);
        PlutusData sumScriptDatum = new BigIntPlutusData(BigInteger.valueOf(8)); //redeemer should be 36
        PlutusData sumScriptRedeemer = new BigIntPlutusData(BigInteger.valueOf(36));

        //Create a reference input and send lock amount at script address
        Tx createRefInputTx = new Tx();
        createRefInputTx.payToAddress(receiver1, List.of(Amount.ada(1.0)), sumScript)
                .payToContract(sumScriptAddr, sumScriptAmt, sumScriptDatum)
                .from(sender1Addr);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder.compose(createRefInputTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);
        System.out.println("Tx Response: " + result.getResponse());
        assertTrue(result.isSuccessful());

        //Required as backend service returns outdated utxo
        if (result.isSuccessful()) {
            checkIfUtxoAvailable(result.getValue(), sender1Addr);
        }

        Utxo refUtxo = Utxo.builder()
                .txHash(result.getValue())
                .outputIndex(0)
                .build();

        //Pay to script
        Tx scriptPayTx = new Tx();
        scriptPayTx.payToContract(sumScriptAddr, sumScriptAmt, sumScriptDatum)
                .from(sender1Addr);
        result = quickTxBuilder.compose(scriptPayTx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);
        //Required as backend service returns outdated utxo
        if (result.isSuccessful()) {
            checkIfUtxoAvailable(result.getValue(), sender1Addr);
        }

        PlutusV2Script alwaysSuccessScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        String alwaysSuccessV2ScriptAddress = AddressProvider.getEntAddress(alwaysSuccessScript, Networks.testnet()).toBech32();
        BigInteger scriptAmt = new BigInteger("2479280");

        Random rand = new Random();
        int randInt = rand.nextInt();
        BigIntPlutusData plutusData = new BigIntPlutusData(BigInteger.valueOf(randInt)); //any random number

        Tx tx = new Tx();
        tx.payToContract(alwaysSuccessV2ScriptAddress, Amount.lovelace(scriptAmt), plutusData)
                .from(sender2Addr);

        Result<String> result1 = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);

        System.out.println(result1.getResponse());
        checkIfUtxoAvailable(result1.getValue(), alwaysSuccessV2ScriptAddress);

        //Find the utxo for the script address
        Optional<Utxo> sumUtxo = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, sumScriptAddr, sumScriptDatum);
        Optional<Utxo> alwaysSuccessUtxo = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, alwaysSuccessV2ScriptAddress, plutusData);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(sumUtxo.get(), sumScriptRedeemer)
                .collectFrom(alwaysSuccessUtxo.get(), PlutusData.unit())
                .readFrom(refUtxo)
                .payToAddress(receiver1, List.of(sumScriptAmt))
                .attachSpendingValidator(alwaysSuccessScript)
                .withChangeAddress(sumScriptAddr, sumScriptDatum);

        Result<String> result2 = quickTxBuilder.compose(scriptTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withReferenceScripts(sumScript)
                .completeAndWait(System.out::println);

        System.out.println(result2.getResponse());
        assertTrue(result2.isSuccessful());

        checkIfUtxoAvailable(result2.getValue(), sender1Addr);
    }

    @Test
    //self-destructing script
    //Reference script is attached in the input utxo instead of reference input
    void alwaysTrueScript_whenSpendInputWithRefScript() throws ApiException {
        PlutusV3Script plutusScript = PlutusV3Script.builder()
                .type("PlutusScriptV3")
                .cborHex("46450101002499")
                .build();

        String scriptAddress = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).toBech32();
        BigInteger scriptAmt = new BigInteger("2479280");

        long randInt = System.currentTimeMillis();
        BigIntPlutusData plutusData = new BigIntPlutusData(BigInteger.valueOf(randInt)); //any random number

        Tx tx = new Tx()
                .payToContract(scriptAddress, Amount.lovelace(scriptAmt), plutusData, plutusScript)
                .from(sender2Addr);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);

        System.out.println(result.getResponse());
        checkIfUtxoAvailable(result.getValue(), scriptAddress);

        Optional<Utxo> optionalUtxo = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddress, plutusData);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(optionalUtxo.get(), plutusData)
                .payToAddress(receiver1, Amount.lovelace(scriptAmt))
                .withChangeAddress(scriptAddress, plutusData);

        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(sender2Addr)
                .withSigner(SignerProviders.signerFrom(sender2))
                .withRequiredSigners(sender2.getBaseAddress())
                .withTxInspector(txn -> {
                    System.out.println(JsonUtil.getPrettyJson(txn));
                })
                .completeAndWait(System.out::println);

        System.out.println(result1);
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), sender2Addr);
    }

    @Test
    void alwaysTrueScript_whenInputWithRefScriptAndSameScriptAsAttachedValidator_removeDuplicateWitness() throws ApiException {
        PlutusV3Script plutusScript = PlutusV3Script.builder()
                .type("PlutusScriptV3")
                .cborHex("46450101002499")
                .build();

        String scriptAddress = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).toBech32();
        BigInteger scriptAmt = new BigInteger("2479280");

        long randInt = System.currentTimeMillis();
        BigIntPlutusData plutusData = new BigIntPlutusData(BigInteger.valueOf(randInt)); //any random number

        Tx tx = new Tx()
                .payToContract(scriptAddress, Amount.lovelace(scriptAmt), plutusData)
                .payToAddress(receiver1, Amount.ada(1), plutusScript)
                .payToAddress(sender2Addr, Amount.ada(5), plutusScript)
                .from(sender2Addr);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);

        System.out.println(result.getResponse());
        checkIfUtxoAvailable(result.getValue(), scriptAddress);

        Optional<Utxo> optionalUtxo = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddress, plutusData);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(optionalUtxo.get(), plutusData)
                .payToAddress(receiver1, Amount.lovelace(scriptAmt))
                .readFrom(new TransactionInput(result.getValue(), 1))
                .attachSpendingValidator(plutusScript)
                .withChangeAddress(scriptAddress, plutusData);

        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(sender2Addr)
                .withSigner(SignerProviders.signerFrom(sender2))
                .withRequiredSigners(sender2.getBaseAddress())
                .withTxInspector(txn -> {
                    System.out.println(JsonUtil.getPrettyJson(txn));
                })
                .removeDuplicateScriptWitnesses(true)
                .completeAndWait(System.out::println);

        System.out.println(result1);
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), sender2Addr);
    }

}
