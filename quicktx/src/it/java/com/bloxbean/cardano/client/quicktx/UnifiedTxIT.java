package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.InsufficientBalanceException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.cip.cip20.MessageMetadata;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.VerificationKey;

import com.bloxbean.cardano.client.function.helper.ScriptUtxoFinders;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.quicktx.filter.ast.FilterNode;
import com.bloxbean.cardano.client.quicktx.filter.dsl.Spec;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.ProtocolParamUpdate;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.governance.*;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.*;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.quicktx.filter.dsl.UtxoFilters.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the Unified Tx API.
 * All tests use {@link Tx} (not {@link ScriptTx}) for script operations,
 * exercising the new unified API where regular and script intents coexist
 * in a single builder.
 * <p>
 * Requires Yaci DevKit running on localhost:8080.
 */
public class UnifiedTxIT extends TestDataBaseIT {

    // ---- Scripts reused across tests ----

    static PlutusV3Script alwaysTrueV3() {
        return PlutusV3Script.builder()
                .type("PlutusScriptV3")
                .cborHex("46450101002499")
                .build();
    }

    static PlutusV2Script alwaysTrueV2() {
        return PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();
    }

    // ---- Helpers ----

    private BigIntPlutusData randomDatum() {
        return new BigIntPlutusData(BigInteger.valueOf(System.currentTimeMillis() + new Random().nextInt(100_000)));
    }

    private String getRandomTokenName() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    // ========================================================================
    // Category 1: Script Lock/Unlock via Unified Tx
    // ========================================================================

    @Test
    void unifiedTx_lockAndUnlock_plutusV3() throws Exception {
        PlutusV3Script script = alwaysTrueV3();
        String scriptAddr = AddressProvider.getEntAddress(script, Networks.testnet()).toBech32();
        BigIntPlutusData datum = randomDatum();

        // Lock 5 ADA (more than we spend, so there's a change output to script address)
        Tx lockTx = new Tx()
                .payToContract(scriptAddr, Amount.ada(5), datum)
                .from(sender2Addr);

        Result<String> lockResult = quickTxBuilder.compose(lockTx)
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);

        assertTrue(lockResult.isSuccessful());
        checkIfUtxoAvailable(lockResult.getValue(), scriptAddr);

        // Unlock via unified Tx — spend only 2 ADA, change goes back to script address
        Optional<Utxo> utxo = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddr, datum);
        assertTrue(utxo.isPresent());

        Tx unlockTx = new Tx()
                .collectFrom(utxo.get(), datum)
                .payToAddress(receiver1, Amount.ada(2))
                .attachSpendingValidator(script)
                .withChangeAddress(scriptAddr, datum);

        Result<String> unlockResult = quickTxBuilder.compose(unlockTx)
                .feePayer(sender2Addr)
                .withSigner(SignerProviders.signerFrom(sender2))
                .withRequiredSigners(sender2.getBaseAddress())
                .completeAndWait(System.out::println);

        System.out.println(unlockResult);
        assertTrue(unlockResult.isSuccessful());
        checkIfUtxoAvailable(unlockResult.getValue(), sender2Addr);

        // Validate inline datum preserved in change output at script address
        Optional<Utxo> scriptChangeOpt = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddr, datum);
        assertTrue(scriptChangeOpt.isPresent(), "Change UTXO with inline datum should exist at script address");
        assertThat(scriptChangeOpt.get().getAddress()).isEqualTo(scriptAddr);
    }

    @Test
    void unifiedTx_lockAndUnlock_plutusV2() throws Exception {
        PlutusV2Script script = alwaysTrueV2();
        String scriptAddr = AddressProvider.getEntAddress(script, Networks.testnet()).toBech32();
        BigInteger scriptAmt = new BigInteger("2479280");
        BigIntPlutusData datum = randomDatum();

        // Lock
        Tx lockTx = new Tx()
                .payToContract(scriptAddr, Amount.lovelace(scriptAmt), datum)
                .from(sender2Addr);

        Result<String> lockResult = quickTxBuilder.compose(lockTx)
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);

        assertTrue(lockResult.isSuccessful());
        checkIfUtxoAvailable(lockResult.getValue(), scriptAddr);

        // Unlock via unified Tx
        Optional<Utxo> utxo = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddr, datum);
        assertTrue(utxo.isPresent());

        Tx unlockTx = new Tx()
                .collectFrom(utxo.get(), datum)
                .payToAddress(receiver1, Amount.lovelace(scriptAmt))
                .attachSpendingValidator(script)
                .withChangeAddress(scriptAddr, datum);

        Result<String> unlockResult = quickTxBuilder.compose(unlockTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withRequiredSigners(sender1.getBaseAddress())
                .withVerifier(txn -> {
                    assertThat(txn.getBody().getRequiredSigners()).hasSize(1);
                    assertThat(txn.getBody().getRequiredSigners().get(0))
                            .isEqualTo(sender1.getBaseAddress().getPaymentCredentialHash().get());
                })
                .completeAndWait(System.out::println);

        System.out.println(unlockResult);
        assertTrue(unlockResult.isSuccessful());
        checkIfUtxoAvailable(unlockResult.getValue(), sender1Addr);
    }

    @Test
    void unifiedTx_lockAndUnlock_withDatumHashChange() throws Exception {
        PlutusV3Script script = alwaysTrueV3();
        String scriptAddr = AddressProvider.getEntAddress(script, Networks.testnet()).toBech32();
        BigInteger scriptAmt = new BigInteger("2479280");
        BigIntPlutusData datum = randomDatum();

        // Lock with inline datum
        Tx lockTx = new Tx()
                .payToContract(scriptAddr, Amount.lovelace(scriptAmt), datum)
                .from(sender2Addr);

        Result<String> lockResult = quickTxBuilder.compose(lockTx)
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);

        assertTrue(lockResult.isSuccessful());
        checkIfUtxoAvailable(lockResult.getValue(), scriptAddr);

        // Unlock with datumHash in change output
        Optional<Utxo> utxo = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddr, datum);
        assertTrue(utxo.isPresent());

        Tx unlockTx = new Tx()
                .collectFrom(utxo.get(), datum)
                .payToAddress(receiver1, Amount.ada(1))
                .attachSpendingValidator(script)
                .withChangeAddress(scriptAddr, datum.getDatumHash());

        Result<String> unlockResult = quickTxBuilder.compose(unlockTx)
                .feePayer(sender2Addr)
                .withSigner(SignerProviders.signerFrom(sender2))
                .withRequiredSigners(sender2.getBaseAddress())
                .completeAndWait(System.out::println);

        assertTrue(unlockResult.isSuccessful());
        checkIfUtxoAvailable(unlockResult.getValue(), sender2Addr);

        // Validate change output has datumHash (not inline datum)
        var changeUtxoOpt = utxoSupplier.getTxOutput(unlockResult.getValue(), 1);
        assertTrue(changeUtxoOpt.isPresent());
        assertThat(changeUtxoOpt.get().getAddress()).isEqualTo(scriptAddr);
        assertThat(changeUtxoOpt.get().getDataHash()).isNotEmpty();
        assertThat(changeUtxoOpt.get().getDataHash()).isEqualTo(datum.getDatumHash());
    }

    // ========================================================================
    // Category 2: Script Minting via Unified Tx
    // ========================================================================

    @Test
    void unifiedTx_mintAsset_withPlutusScript() throws Exception {
        PlutusV2Script mintScript = alwaysTrueV2();
        String tokenName = getRandomTokenName();
        Asset asset = new Asset(tokenName, BigInteger.valueOf(1000));

        Tx mintTx = new Tx()
                .mintAsset(mintScript, asset, PlutusData.unit(), sender1Addr);

        Result<String> result = quickTxBuilder.compose(mintTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    void unifiedTx_mintAsset_withPolicyId() throws Exception {
        PlutusV2Script mintScript = alwaysTrueV2();
        String policyId = mintScript.getPolicyId();
        String tokenName = getRandomTokenName();
        Asset asset = new Asset(tokenName, BigInteger.valueOf(2000));

        Tx mintTx = new Tx()
                .mintAsset(policyId, asset, PlutusData.unit(), sender1Addr)
                .attachMintValidator(mintScript);

        Result<String> result = quickTxBuilder.compose(mintTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    void unifiedTx_mintAsset_withOutputDatum() throws Exception {
        PlutusV2Script mintScript = alwaysTrueV2();
        String tokenName = getRandomTokenName();
        Asset asset = new Asset(tokenName, BigInteger.valueOf(500));
        PlutusData outputDatum = BigIntPlutusData.of(42);

        Tx mintTx = new Tx()
                .mintAsset(mintScript, List.of(asset), PlutusData.unit(), receiver1, outputDatum);

        Result<String> result = quickTxBuilder.compose(mintTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    // ========================================================================
    // Category 3: Mixed Script + Regular Operations in Single Tx
    // ========================================================================

    @Test
    void unifiedTx_paymentAndCollectFrom_singleTx() throws Exception {
        PlutusV2Script script = alwaysTrueV2();
        String scriptAddr = AddressProvider.getEntAddress(script, Networks.testnet()).toBech32();
        BigIntPlutusData datum = randomDatum();

        // Lock 10 ADA — enough to fund both script and regular outputs
        Tx lockTx = new Tx()
                .payToContract(scriptAddr, Amount.ada(10), datum)
                .from(sender2Addr);

        Result<String> lockResult = quickTxBuilder.compose(lockTx)
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);

        assertTrue(lockResult.isSuccessful());
        checkIfUtxoAvailable(lockResult.getValue(), scriptAddr);

        // Single unified Tx: regular payment + script spend
        Optional<Utxo> utxo = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddr, datum);
        assertTrue(utxo.isPresent());

        Tx mixedTx = new Tx()
                .payToAddress(receiver3, Amount.ada(1))  // regular payment funded by script input
                .collectFrom(utxo.get(), datum)           // script spend
                .payToAddress(receiver1, Amount.ada(4))
                .attachSpendingValidator(script)
                .withChangeAddress(scriptAddr, datum);

        Result<String> result = quickTxBuilder.compose(mixedTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    void unifiedTx_paymentAndMint_singleTx() throws Exception {
        PlutusV2Script mintScript = alwaysTrueV2();
        String tokenName = getRandomTokenName();
        Asset asset = new Asset(tokenName, BigInteger.valueOf(3000));

        // Single Tx: regular payment + script minting
        Tx mixedTx = new Tx()
                .payToAddress(receiver3, Amount.ada(2))
                .mintAsset(mintScript, asset, PlutusData.unit(), receiver1);

        Result<String> result = quickTxBuilder.compose(mixedTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .mergeOutputs(false)
                .completeAndWait(System.out::println);

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    void unifiedTx_collectFromAndMint_singleTx() throws Exception {
        PlutusV3Script spendScript = alwaysTrueV3();
        PlutusV2Script mintScript = alwaysTrueV2();
        String scriptAddr = AddressProvider.getEntAddress(spendScript, Networks.testnet()).toBech32();
        BigInteger scriptAmt = new BigInteger("2479280");
        BigIntPlutusData datum = randomDatum();

        // Lock funds
        Tx lockTx = new Tx()
                .payToContract(scriptAddr, Amount.lovelace(scriptAmt), datum)
                .from(sender2Addr);

        Result<String> lockResult = quickTxBuilder.compose(lockTx)
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);

        assertTrue(lockResult.isSuccessful());
        checkIfUtxoAvailable(lockResult.getValue(), scriptAddr);

        // Single Tx: script spend + script mint
        Optional<Utxo> utxo = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddr, datum);
        assertTrue(utxo.isPresent());

        String tokenName = getRandomTokenName();
        Asset asset = new Asset(tokenName, BigInteger.valueOf(5000));

        Tx mixedTx = new Tx()
                .collectFrom(utxo.get(), datum)
                .mintAsset(mintScript, asset, PlutusData.unit(), sender1Addr)
                .payToAddress(receiver1, Amount.lovelace(scriptAmt))
                .attachSpendingValidator(spendScript)
                .withChangeAddress(sender1Addr);

        Result<String> result = quickTxBuilder.compose(mixedTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    // ========================================================================
    // Category 4: Reference Scripts & Inputs via Unified Tx
    // ========================================================================

    @Test
    void unifiedTx_referenceInput_withReadFrom() throws Exception {
        PlutusV3Script script = alwaysTrueV3();
        String scriptAddr = AddressProvider.getEntAddress(script, Networks.testnet()).toBech32();
        Amount scriptAmt = Amount.ada(4.0);
        BigIntPlutusData datum = new BigIntPlutusData(BigInteger.valueOf(8));
        PlutusData redeemer = new BigIntPlutusData(BigInteger.valueOf(36));

        // Create reference script UTXO + lock at script address
        Tx setupTx = new Tx()
                .payToAddress(receiver1, List.of(Amount.ada(1.0)), script)
                .payToContract(scriptAddr, scriptAmt, datum)
                .from(sender1Addr);

        Result<String> setupResult = quickTxBuilder.compose(setupTx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);

        assertTrue(setupResult.isSuccessful());
        checkIfUtxoAvailable(setupResult.getValue(), sender1Addr);

        Utxo refUtxo = Utxo.builder()
                .txHash(setupResult.getValue())
                .outputIndex(0)
                .build();

        // Lock again to create a separate spendable UTXO
        Tx lockTx = new Tx()
                .payToContract(scriptAddr, scriptAmt, datum)
                .from(sender1Addr);

        Result<String> lockResult = quickTxBuilder.compose(lockTx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);

        assertTrue(lockResult.isSuccessful());
        checkIfUtxoAvailable(lockResult.getValue(), sender1Addr);

        // Unlock via readFrom — no attachSpendingValidator needed
        Optional<Utxo> utxo = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddr, datum);
        assertTrue(utxo.isPresent());

        Tx unlockTx = new Tx()
                .collectFrom(utxo.get(), redeemer)
                .readFrom(refUtxo)
                .payToAddress(receiver1, List.of(scriptAmt))
                .withChangeAddress(scriptAddr, datum);

        Result<String> result = quickTxBuilder.compose(unlockTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    void unifiedTx_referenceInput_withRefScriptsCall() throws Exception {
        PlutusV3Script script = alwaysTrueV3();
        String scriptAddr = AddressProvider.getEntAddress(script, Networks.testnet()).toBech32();
        Amount scriptAmt = Amount.ada(4.0);
        BigIntPlutusData datum = new BigIntPlutusData(BigInteger.valueOf(8));
        PlutusData redeemer = new BigIntPlutusData(BigInteger.valueOf(36));

        // Create reference script UTXO + lock
        Tx setupTx = new Tx()
                .payToAddress(receiver1, List.of(Amount.ada(1.0)), script)
                .payToContract(scriptAddr, scriptAmt, datum)
                .from(sender1Addr);

        Result<String> setupResult = quickTxBuilder.compose(setupTx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);

        assertTrue(setupResult.isSuccessful());
        checkIfUtxoAvailable(setupResult.getValue(), sender1Addr);

        Utxo refUtxo = Utxo.builder()
                .txHash(setupResult.getValue())
                .outputIndex(0)
                .build();

        // Lock another UTXO
        Tx lockTx = new Tx()
                .payToContract(scriptAddr, scriptAmt, datum)
                .from(sender1Addr);

        Result<String> lockResult = quickTxBuilder.compose(lockTx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);

        assertTrue(lockResult.isSuccessful());
        checkIfUtxoAvailable(lockResult.getValue(), sender1Addr);

        // Unlock with withReferenceScripts()
        Optional<Utxo> utxo = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddr, datum);
        assertTrue(utxo.isPresent());

        Tx unlockTx = new Tx()
                .collectFrom(utxo.get(), redeemer)
                .readFrom(refUtxo)
                .payToAddress(receiver1, List.of(scriptAmt))
                .withChangeAddress(scriptAddr, datum);

        Result<String> result = quickTxBuilder.compose(unlockTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withReferenceScripts(script)
                .completeAndWait(System.out::println);

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    void unifiedTx_selfDestructingScript_refScriptInInput() throws Exception {
        PlutusV3Script script = alwaysTrueV3();
        String scriptAddr = AddressProvider.getEntAddress(script, Networks.testnet()).toBech32();
        BigInteger scriptAmt = new BigInteger("2479280");
        BigIntPlutusData datum = randomDatum();

        // Lock with reference script embedded in the UTXO itself
        Tx lockTx = new Tx()
                .payToContract(scriptAddr, Amount.lovelace(scriptAmt), datum, script)
                .from(sender2Addr);

        Result<String> lockResult = quickTxBuilder.compose(lockTx)
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);

        assertTrue(lockResult.isSuccessful());
        checkIfUtxoAvailable(lockResult.getValue(), scriptAddr);

        // Unlock — reference script is in the spending input itself (self-destructing)
        Optional<Utxo> utxo = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddr, datum);
        assertTrue(utxo.isPresent());

        Tx unlockTx = new Tx()
                .collectFrom(utxo.get(), datum)
                .payToAddress(receiver1, Amount.lovelace(scriptAmt))
                .withChangeAddress(scriptAddr, datum);

        Result<String> result = quickTxBuilder.compose(unlockTx)
                .feePayer(sender2Addr)
                .withSigner(SignerProviders.signerFrom(sender2))
                .withRequiredSigners(sender2.getBaseAddress())
                .completeAndWait(System.out::println);

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender2Addr);
    }

    // ========================================================================
    // Category 5: Collection Variants via Unified Tx
    // ========================================================================

    @Test
    void unifiedTx_collectFrom_withPredicate() throws Exception {
        PlutusV3Script script = alwaysTrueV3();
        String scriptAddr = AddressProvider.getEntAddress(script, Networks.testnet()).toBech32();
        BigInteger scriptAmt = new BigInteger("2479280");
        BigIntPlutusData datum = randomDatum();

        // Lock 2 UTXOs
        Tx lockTx = new Tx()
                .payToContract(scriptAddr, Amount.lovelace(scriptAmt), datum)
                .payToContract(scriptAddr, Amount.lovelace(scriptAmt), datum)
                .from(sender2Addr);

        Result<String> lockResult = quickTxBuilder.compose(lockTx)
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);

        assertTrue(lockResult.isSuccessful());
        checkIfUtxoAvailable(lockResult.getValue(), scriptAddr);

        // Collect via predicate
        String datumCborHex = datum.serializeToHex();

        Tx collectTx = new Tx()
                .collectFrom(scriptAddr, utx -> datumCborHex.equals(utx.getInlineDatum()), datum)
                .payToAddress(receiver1, Amount.lovelace(scriptAmt))
                .attachSpendingValidator(script)
                .withChangeAddress(scriptAddr, datum);

        Result<String> result = quickTxBuilder.compose(collectTx)
                .feePayer(sender2Addr)
                .withSigner(SignerProviders.signerFrom(sender2))
                .withRequiredSigners(sender2.getBaseAddress())
                .completeAndWait(System.out::println);

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender2Addr);
    }

    @Test
    void unifiedTx_collectFrom_withFilterSpec() throws Exception {
        PlutusV3Script script = alwaysTrueV3();
        String scriptAddr = AddressProvider.getEntAddress(script, Networks.testnet()).toBech32();
        BigInteger scriptAmt = new BigInteger("2479280");
        BigIntPlutusData datum = randomDatum();

        // Lock 2 UTXOs
        Tx lockTx = new Tx()
                .payToContract(scriptAddr, Amount.lovelace(scriptAmt), datum)
                .payToContract(scriptAddr, Amount.lovelace(scriptAmt), datum)
                .from(sender2Addr);

        Result<String> lockResult = quickTxBuilder.compose(lockTx)
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);

        assertTrue(lockResult.isSuccessful());
        checkIfUtxoAvailable(lockResult.getValue(), scriptAddr);

        // Collect via FilterSpec
        String datumCborHex = datum.serializeToHex();
        FilterNode filter = and(
                inlineDatum().eq(datumCborHex),
                lovelace().gte(1_000_000)
        );
        var filterSpec = Spec.of(filter).limitAll().build();

        Tx collectTx = new Tx()
                .collectFrom(scriptAddr, filterSpec, datum)
                .payToAddress(receiver1, Amount.lovelace(scriptAmt))
                .attachSpendingValidator(script)
                .withChangeAddress(scriptAddr, datum);

        Result<String> result = quickTxBuilder.compose(collectTx)
                .feePayer(sender2Addr)
                .withSigner(SignerProviders.signerFrom(sender2))
                .withRequiredSigners(sender2.getBaseAddress())
                .completeAndWait(System.out::println);

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender2Addr);
    }

    // ========================================================================
    // Category 6: Multi-Tx Compose (Tx + Tx-with-script-intents)
    // ========================================================================

    @Test
    void unifiedTx_compose_regularTxAndScriptTx() throws Exception {
        PlutusV2Script script = alwaysTrueV2();
        String scriptAddr = AddressProvider.getEntAddress(script, Networks.testnet()).toBech32();
        BigInteger scriptAmt = new BigInteger("2479280");
        BigIntPlutusData datum = randomDatum();

        // Lock multiple UTXOs
        Tx lockTx = new Tx()
                .payToContract(scriptAddr, Amount.lovelace(scriptAmt), datum)
                .payToContract(scriptAddr, Amount.ada(2), datum)
                .payToContract(scriptAddr, Amount.ada(3), datum)
                .from(sender2Addr);

        Result<String> lockResult = quickTxBuilder.compose(lockTx)
                .withSigner(SignerProviders.signerFrom(sender2))
                .mergeOutputs(false)
                .completeAndWait(System.out::println);

        assertTrue(lockResult.isSuccessful());
        checkIfUtxoAvailable(lockResult.getValue(), scriptAddr);

        // Compose two Tx objects: regular + script
        Tx regularTx = new Tx()
                .payToAddress(receiver3, Amount.ada(1))
                .from(sender1Addr);

        List<Utxo> utxos = ScriptUtxoFinders.findAllByInlineDatum(utxoSupplier, scriptAddr, datum);
        assertThat(utxos).isNotEmpty();

        Tx scriptUnifiedTx = new Tx()
                .collectFrom(utxos, datum)
                .payToAddress(receiver1, Amount.lovelace(scriptAmt))
                .payToAddress(receiver1, Amount.ada(2))
                .payToAddress(receiver1, Amount.ada(3))
                .attachSpendingValidator(script)
                .withChangeAddress(scriptAddr, datum);

        Result<String> result = quickTxBuilder.compose(regularTx, scriptUnifiedTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .mergeOutputs(false)
                .completeAndWait(System.out::println);

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    // ========================================================================
    // Category 7: Governance Operations via Unified Tx
    // ========================================================================

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class GovernanceTests {
        static BackendService govBackendService;
        static Account govSender;
        static String govSenderAddr;
        static QuickTxBuilder govQuickTxBuilder;
        static GovActionId lastGovActionId;

        @BeforeAll
        static void setupGovernance() {
            govBackendService = new BFBackendService("http://localhost:8080/api/v1/", "Dummy");
            govQuickTxBuilder = new QuickTxBuilder(govBackendService);

            String senderMnemonic = "test test test test test test test test test test test test test test test test test test test test test test test sauce";
            govSender = new Account(Networks.testnet(), senderMnemonic);
            govSenderAddr = govSender.baseAddress();

            resetDevNet();
            topUpFund(govSenderAddr, 500000L);
        }

        private void registerStakeKeys() {
            Tx tx = new Tx()
                    .registerStakeAddress(govSenderAddr)
                    .attachMetadata(MessageMetadata.create().add("Stake registration for governance tests"))
                    .from(govSenderAddr);

            Result<String> result = govQuickTxBuilder.compose(tx)
                    .withSigner(SignerProviders.signerFrom(govSender))
                    .completeAndWait(msg -> System.out.println(msg));

            if (result.isSuccessful())
                checkIfUtxoAvailable(result.getValue(), govSenderAddr);
        }

        @Test
        @Order(1)
        void unifiedTx_registerDRep_withScript() throws Exception {
            registerStakeKeys();

            PlutusV3Script script = alwaysTrueV3();
            var scriptHash = script.getScriptHash();
            var scriptCredential = Credential.fromScript(scriptHash);

            var anchor = new Anchor("https://pages.bloxbean.com/cardano-stake/bloxbean-pool.json",
                    HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

            Tx drepRegTx = new Tx()
                    .registerDRep(scriptCredential, anchor, BigIntPlutusData.of(1))
                    .attachCertificateValidator(script);

            Result<String> result = govQuickTxBuilder.compose(drepRegTx)
                    .feePayer(govSenderAddr)
                    .withSigner(SignerProviders.signerFrom(govSender))
                    .complete();

            System.out.println(result);
            assertTrue(result.isSuccessful());
            waitForTransaction(result);
            checkIfUtxoAvailable(result.getValue(), govSenderAddr);
        }

        @Test
        @Order(2)
        void unifiedTx_createProposal_withScript() throws Exception {
            PlutusV3Script script = alwaysTrueV3();

            var parameterChange = new ParameterChangeAction();
            parameterChange.setProtocolParamUpdate(ProtocolParamUpdate.builder()
                    .minPoolCost(adaToLovelace(300))
                    .build()
            );
            parameterChange.setPolicyHash(script.getScriptHash());

            var anchor = new Anchor("https://xyz.com",
                    HexUtil.decodeHexString("cafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

            Tx proposalTx = new Tx()
                    .createProposal(parameterChange, govSender.stakeAddress(), anchor, BigIntPlutusData.of(1))
                    .attachProposingValidator(script);

            Result<String> result = govQuickTxBuilder.compose(proposalTx)
                    .feePayer(govSenderAddr)
                    .withSigner(SignerProviders.signerFrom(govSender))
                    .completeAndWait(s -> System.out.println(s));

            lastGovActionId = new GovActionId(result.getValue(), 0);

            System.out.println(result);
            assertTrue(result.isSuccessful());
            checkIfUtxoAvailable(result.getValue(), govSenderAddr);
        }

        @Test
        @Order(3)
        void unifiedTx_createVote_withScript() throws Exception {
            PlutusV3Script script = alwaysTrueV3();
            var scriptHash = script.getScriptHash();
            var drepCredential = Credential.fromScript(scriptHash);

            var anchor = new Anchor("https://script.com",
                    HexUtil.decodeHexString("daeef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

            var voter = new Voter(VoterType.DREP_SCRIPT_HASH, drepCredential);

            Tx voteTx = new Tx()
                    .createVote(voter, lastGovActionId, Vote.YES, anchor, BigIntPlutusData.of(1))
                    .attachVotingValidator(script);

            Result<String> result = govQuickTxBuilder.compose(voteTx)
                    .feePayer(govSenderAddr)
                    .withSigner(SignerProviders.signerFrom(govSender))
                    .completeAndWait(s -> System.out.println(s));

            System.out.println(result);
            assertTrue(result.isSuccessful());
            checkIfUtxoAvailable(result.getValue(), govSenderAddr);
        }

        @Test
        @Order(4)
        void unifiedTx_delegateVotingPower_withScript() throws Exception {
            PlutusV3Script drepScript = alwaysTrueV3();
            var drepScriptHash = drepScript.getScriptHash();
            var drep = DRep.scriptHash(HexUtil.encodeHexString(drepScriptHash));

            // Use same script for delegator
            var delegatorScript = drepScript;
            Address delegatorStakeAddress = AddressProvider.getRewardAddress(delegatorScript, Networks.testnet());

            // Register delegator stake key
            Tx stakeKeyRegTx = new Tx()
                    .registerStakeAddress(delegatorStakeAddress)
                    .attachMetadata(MessageMetadata.create().add("Script stake registration tx"))
                    .from(govSenderAddr);

            Result<String> regResult = govQuickTxBuilder.compose(stakeKeyRegTx)
                    .withSigner(SignerProviders.signerFrom(govSender))
                    .withTxInspector(txn -> System.out.println(JsonUtil.getPrettyJson(txn)))
                    .completeAndWait(msg -> System.out.println(msg));

            if (regResult.isSuccessful())
                checkIfUtxoAvailable(regResult.getValue(), govSenderAddr);

            // Delegate voting power via unified Tx
            Tx delegationTx = new Tx()
                    .delegateVotingPowerTo(delegatorStakeAddress, drep, BigIntPlutusData.of(1))
                    .attachCertificateValidator(delegatorScript);

            Result<String> result = govQuickTxBuilder.compose(delegationTx)
                    .feePayer(govSenderAddr)
                    .withSigner(SignerProviders.signerFrom(govSender))
                    .completeAndWait(s -> System.out.println(s));

            System.out.println(result);
            assertTrue(result.isSuccessful());
            checkIfUtxoAvailable(result.getValue(), govSenderAddr);
        }
    }

    // ========================================================================
    // Category 8: Mixed Deposit + Script Operations
    // ========================================================================

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class MixedDepositAndScriptTests {
        static BackendService mixBackendService;
        static Account mixSender1;
        static Account mixSender2;
        static String mixSender1Addr;
        static String mixSender2Addr;
        static QuickTxBuilder mixQuickTxBuilder;
        static UtxoSupplier mixUtxoSupplier;
        static ProtocolParamsSupplier mixProtocolParamsSupplier;

        @BeforeAll
        static void setupMixed() {
            mixBackendService = new BFBackendService("http://localhost:8080/api/v1/", "Dummy");
            mixQuickTxBuilder = new QuickTxBuilder(mixBackendService);
            mixUtxoSupplier = new DefaultUtxoSupplier(mixBackendService.getUtxoService());
            mixProtocolParamsSupplier = new DefaultProtocolParamsSupplier(mixBackendService.getEpochService());

            String sender1Mnemonic = "drive useless envelope shine range ability time copper alarm museum near flee wrist live type device meadow allow churn purity wisdom praise drop code";
            mixSender1 = new Account(Networks.testnet(), sender1Mnemonic);
            mixSender1Addr = mixSender1.baseAddress();

            String sender2Mnemonic = "access else envelope between rubber celery forum brief bubble notice stomach add initial avocado current net film aunt quick text joke chase robust artefact";
            mixSender2 = new Account(Networks.testnet(), sender2Mnemonic);
            mixSender2Addr = mixSender2.baseAddress();

            resetDevNet();
            topUpFund(mixSender1Addr, 500000);
            topUpFund(mixSender2Addr, 500000);
        }

        private BigInteger getLovelaceBalance(String address) {
            return mixUtxoSupplier.getAll(address).stream()
                    .flatMap(u -> u.getAmount().stream())
                    .filter(a -> a.getUnit().equals("lovelace"))
                    .map(Amount::getQuantity)
                    .reduce(BigInteger.ZERO, BigInteger::add);
        }

        private void tryDeregisterStakeKey(String address, Account account) {
            try {
                Tx tx = new Tx()
                        .deregisterStakeAddress(address)
                        .from(address);

                Result<String> result = mixQuickTxBuilder.compose(tx)
                        .withSigner(SignerProviders.signerFrom(account))
                        .withSigner(SignerProviders.stakeKeySignerFrom(account))
                        .completeAndWait(msg -> System.out.println(msg));

                if (result.isSuccessful())
                    checkIfUtxoAvailable(result.getValue(), address);
                else
                    System.out.println("Stake key deregistration skipped (not registered): " + result);
            } catch (Exception e) {
                System.out.println("Stake key deregistration skipped: " + e.getMessage());
            }
        }

        private void tryUnregisterDRep(Account account, String fromAddr) {
            try {
                Tx tx = new Tx()
                        .unregisterDRep(account.drepCredential())
                        .from(fromAddr);

                Result<String> result = mixQuickTxBuilder.compose(tx)
                        .withSigner(SignerProviders.drepKeySignerFrom(account))
                        .withSigner(SignerProviders.signerFrom(account))
                        .completeAndWait(msg -> System.out.println(msg));

                if (result.isSuccessful())
                    checkIfUtxoAvailable(result.getValue(), fromAddr);
                else
                    System.out.println("DRep unregistration skipped: " + result);
            } catch (Exception e) {
                System.out.println("DRep unregistration skipped: " + e.getMessage());
            }
        }

        /**
         * Lock ADA at a script address with inline datum. Returns the lock tx hash.
         */
        private String lockFundsAtScript(PlutusScript script, String scriptAddr, BigIntPlutusData datum,
                                         Amount amount, Account fromAccount, String fromAddr) {
            Tx lockTx = new Tx()
                    .payToContract(scriptAddr, amount, datum)
                    .from(fromAddr);

            Result<String> lockResult = mixQuickTxBuilder.compose(lockTx)
                    .withSigner(SignerProviders.signerFrom(fromAccount))
                    .completeAndWait(System.out::println);

            assertTrue(lockResult.isSuccessful(), "Lock tx failed: " + lockResult);
            checkIfUtxoAvailable(lockResult.getValue(), scriptAddr);
            return lockResult.getValue();
        }

        // ---- Category A: Stake Registration + Script Operations ----

        @Test
        @Order(1)
        void compose_stakeRegistration_and_scriptUnlock() throws Exception {
            PlutusV3Script script = alwaysTrueV3();
            String scriptAddr = AddressProvider.getEntAddress(script, Networks.testnet()).toBech32();
            BigIntPlutusData datum = randomDatum();

            // Lock 5 ADA at script address
            lockFundsAtScript(script, scriptAddr, datum, Amount.ada(5), mixSender2, mixSender2Addr);

            Optional<Utxo> utxo = ScriptUtxoFinders.findFirstByInlineDatum(mixUtxoSupplier, scriptAddr, datum);
            assertTrue(utxo.isPresent(), "Script UTXO not found");

            BigInteger sender1BalanceBefore = getLovelaceBalance(mixSender1Addr);
            BigInteger keyDeposit = new BigInteger(mixProtocolParamsSupplier.getProtocolParams().getKeyDeposit());

            // Regular Tx: stake registration
            Tx regularTx = new Tx()
                    .registerStakeAddress(mixSender1Addr)
                    .from(mixSender1Addr);

            // Script Tx: unlock from script
            Tx scriptTx = new Tx()
                    .collectFrom(utxo.get(), datum)
                    .payToAddress(receiver1, Amount.ada(2))
                    .attachSpendingValidator(script)
                    .withChangeAddress(scriptAddr, datum);

            Result<String> result = mixQuickTxBuilder.compose(regularTx, scriptTx)
                    .feePayer(mixSender1Addr)
                    .withSigner(SignerProviders.signerFrom(mixSender1))
                    .completeAndWait(System.out::println);

            System.out.println("compose_stakeRegistration_and_scriptUnlock: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), mixSender1Addr);

            BigInteger sender1BalanceAfter = getLovelaceBalance(mixSender1Addr);
            BigInteger sender1Spent = sender1BalanceBefore.subtract(sender1BalanceAfter);
            assertThat(sender1Spent).isGreaterThanOrEqualTo(keyDeposit);

            // Verify script change UTXO still exists
            Optional<Utxo> scriptChange = ScriptUtxoFinders.findFirstByInlineDatum(mixUtxoSupplier, scriptAddr, datum);
            assertTrue(scriptChange.isPresent(), "Script change UTXO should exist");
        }

        @Test
        @Order(2)
        void compose_stakeRegistration_and_scriptUnlock_depositFromFrom_notFeePayer() throws Exception {
            PlutusV3Script script = alwaysTrueV3();
            String scriptAddr = AddressProvider.getEntAddress(script, Networks.testnet()).toBech32();
            BigIntPlutusData datum = randomDatum();

            // Lock 5 ADA at script address
            lockFundsAtScript(script, scriptAddr, datum, Amount.ada(5), mixSender2, mixSender2Addr);

            Optional<Utxo> utxo = ScriptUtxoFinders.findFirstByInlineDatum(mixUtxoSupplier, scriptAddr, datum);
            assertTrue(utxo.isPresent(), "Script UTXO not found");

            BigInteger sender1BalanceBefore = getLovelaceBalance(mixSender1Addr);
            BigInteger sender2BalanceBefore = getLovelaceBalance(mixSender2Addr);
            BigInteger keyDeposit = new BigInteger(mixProtocolParamsSupplier.getProtocolParams().getKeyDeposit());

            // Regular Tx: stake registration for sender2 with from(sender2)
            Tx regularTx = new Tx()
                    .registerStakeAddress(mixSender2Addr)
                    .from(mixSender2Addr);

            // Script Tx: unlock from script
            Tx scriptTx = new Tx()
                    .collectFrom(utxo.get(), datum)
                    .payToAddress(receiver1, Amount.ada(2))
                    .attachSpendingValidator(script)
                    .withChangeAddress(scriptAddr, datum);

            Result<String> result = mixQuickTxBuilder.compose(regularTx, scriptTx)
                    .feePayer(mixSender1Addr)
                    .withSigner(SignerProviders.signerFrom(mixSender1))
                    .withSigner(SignerProviders.signerFrom(mixSender2))
                    .completeAndWait(System.out::println);

            System.out.println("compose_stakeRegistration_depositFromFrom_notFeePayer: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), mixSender1Addr);

            BigInteger sender1BalanceAfter = getLovelaceBalance(mixSender1Addr);
            BigInteger sender2BalanceAfter = getLovelaceBalance(mixSender2Addr);

            // Deposit should come from sender2 (.from()), not sender1 (feePayer)
            BigInteger sender2Spent = sender2BalanceBefore.subtract(sender2BalanceAfter);
            BigInteger sender1Spent = sender1BalanceBefore.subtract(sender1BalanceAfter);

            assertThat(sender2Spent).isGreaterThanOrEqualTo(keyDeposit);
            // sender1 only pays fee, which is much less than keyDeposit
            assertThat(sender1Spent).isLessThan(keyDeposit);
        }

        @Test
        @Order(3)
        void singleTx_stakeRegistration_and_scriptUnlock_withFrom() throws Exception {
            // Deregister sender1 stake key first (registered in Test 1)
            tryDeregisterStakeKey(mixSender1Addr, mixSender1);

            PlutusV3Script script = alwaysTrueV3();
            String scriptAddr = AddressProvider.getEntAddress(script, Networks.testnet()).toBech32();
            BigIntPlutusData datum = randomDatum();

            // Lock 15 ADA (enough for script output + regular output in single Tx)
            lockFundsAtScript(script, scriptAddr, datum, Amount.ada(15), mixSender2, mixSender2Addr);

            Optional<Utxo> utxo = ScriptUtxoFinders.findFirstByInlineDatum(mixUtxoSupplier, scriptAddr, datum);
            assertTrue(utxo.isPresent(), "Script UTXO not found");

            BigInteger sender1BalanceBefore = getLovelaceBalance(mixSender1Addr);
            BigInteger keyDeposit = new BigInteger(mixProtocolParamsSupplier.getProtocolParams().getKeyDeposit());

            // Single Tx: stake registration + script unlock
            Tx mixedTx = new Tx()
                    .registerStakeAddress(mixSender1Addr)
                    .collectFrom(utxo.get(), datum)
                    .payToAddress(receiver1, Amount.ada(5))
                    .attachSpendingValidator(script)
                    .withChangeAddress(scriptAddr, datum)
                    .from(mixSender1Addr);

            Result<String> result = mixQuickTxBuilder.compose(mixedTx)
                    .feePayer(mixSender1Addr)
                    .withSigner(SignerProviders.signerFrom(mixSender1))
                    .completeAndWait(System.out::println);

            System.out.println("singleTx_stakeRegistration_and_scriptUnlock: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), mixSender1Addr);

            BigInteger sender1BalanceAfter = getLovelaceBalance(mixSender1Addr);
            BigInteger sender1Spent = sender1BalanceBefore.subtract(sender1BalanceAfter);
            // In a single Tx, the deposit may come from the combined input pool (script UTXO + sender1).
            // sender1 pays at least the fee; deposit may be absorbed by the script UTXO's value.
            assertThat(sender1Spent).isGreaterThan(BigInteger.ZERO);
        }

        @Test
        @Order(4)
        void singleTx_stakeRegistration_and_scriptMint_noFrom_depositFromFeePayer() throws Exception {
            // Deregister sender1 stake key first (registered in Test 3)
            tryDeregisterStakeKey(mixSender1Addr, mixSender1);

            PlutusV2Script mintScript = alwaysTrueV2();
            String tokenName = getRandomTokenName();
            Asset asset = new Asset(tokenName, BigInteger.valueOf(1000));

            BigInteger sender1BalanceBefore = getLovelaceBalance(mixSender1Addr);
            BigInteger keyDeposit = new BigInteger(mixProtocolParamsSupplier.getProtocolParams().getKeyDeposit());

            // Single Tx: stake registration + plutus script minting — NO .from()
            // feePayer becomes default from via setDefaultFrom()
            Tx mixedTx = new Tx()
                    .registerStakeAddress(mixSender1Addr)
                    .mintAsset(mintScript, asset, PlutusData.unit(), receiver1);

            Result<String> result = mixQuickTxBuilder.compose(mixedTx)
                    .feePayer(mixSender1Addr)
                    .withSigner(SignerProviders.signerFrom(mixSender1))
                    .completeAndWait(System.out::println);

            System.out.println("singleTx_stakeRegistration_and_scriptMint_noFrom: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), mixSender1Addr);

            BigInteger sender1BalanceAfter = getLovelaceBalance(mixSender1Addr);
            BigInteger sender1Spent = sender1BalanceBefore.subtract(sender1BalanceAfter);

            // Deposit comes from feePayer (which becomes from via setDefaultFrom)
            assertThat(sender1Spent).isGreaterThanOrEqualTo(keyDeposit);

            // Verify minted token at receiver1
            String policyId = mintScript.getPolicyId();
            boolean tokenFound = mixUtxoSupplier.getAll(receiver1).stream()
                    .flatMap(u -> u.getAmount().stream())
                    .anyMatch(a -> a.getUnit().contains(policyId));
            assertTrue(tokenFound, "Minted token should be at receiver1");
        }

        @Test
        @Order(5)
        void singleTx_stakeRegistration_and_scriptMint_differentFromAndFeePayer() throws Exception {
            // Deregister sender2 stake key first (registered in Test 2)
            tryDeregisterStakeKey(mixSender2Addr, mixSender2);

            PlutusV2Script mintScript = alwaysTrueV2();
            String tokenName = getRandomTokenName();
            Asset asset = new Asset(tokenName, BigInteger.valueOf(500));

            BigInteger sender1BalanceBefore = getLovelaceBalance(mixSender1Addr);
            BigInteger sender2BalanceBefore = getLovelaceBalance(mixSender2Addr);
            BigInteger keyDeposit = new BigInteger(mixProtocolParamsSupplier.getProtocolParams().getKeyDeposit());

            // Single Tx: stake registration with from(sender2) + plutus minting
            Tx mixedTx = new Tx()
                    .registerStakeAddress(mixSender2Addr)
                    .mintAsset(mintScript, asset, PlutusData.unit(), receiver1)
                    .from(mixSender2Addr);

            Result<String> result = mixQuickTxBuilder.compose(mixedTx)
                    .feePayer(mixSender1Addr)
                    .withSigner(SignerProviders.signerFrom(mixSender1))
                    .withSigner(SignerProviders.signerFrom(mixSender2))
                    .completeAndWait(System.out::println);

            System.out.println("singleTx_stakeRegistration_and_scriptMint_differentFromAndFeePayer: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), mixSender1Addr);

            BigInteger sender1BalanceAfter = getLovelaceBalance(mixSender1Addr);
            BigInteger sender2BalanceAfter = getLovelaceBalance(mixSender2Addr);

            // Deposit from sender2 (from), fee from sender1 (feePayer)
            BigInteger sender2Spent = sender2BalanceBefore.subtract(sender2BalanceAfter);
            BigInteger sender1Spent = sender1BalanceBefore.subtract(sender1BalanceAfter);

            assertThat(sender2Spent).isGreaterThanOrEqualTo(keyDeposit);
            assertThat(sender1Spent).isLessThan(keyDeposit);

            // Verify minted token at receiver1
            String policyId = mintScript.getPolicyId();
            boolean tokenFound = mixUtxoSupplier.getAll(receiver1).stream()
                    .flatMap(u -> u.getAmount().stream())
                    .anyMatch(a -> a.getUnit().contains(policyId));
            assertTrue(tokenFound, "Minted token should be at receiver1");
        }

        // ---- Category B: Stake Deregistration (Refund) + Script ----

        @Test
        @Order(6)
        void compose_stakeDeregistration_refund_and_scriptUnlock() throws Exception {
            // sender1 was registered in Test 4, sender2 in Test 5
            PlutusV3Script script = alwaysTrueV3();
            String scriptAddr = AddressProvider.getEntAddress(script, Networks.testnet()).toBech32();
            BigIntPlutusData datum = randomDatum();

            // Lock 5 ADA at script address
            lockFundsAtScript(script, scriptAddr, datum, Amount.ada(5), mixSender2, mixSender2Addr);

            Optional<Utxo> utxo = ScriptUtxoFinders.findFirstByInlineDatum(mixUtxoSupplier, scriptAddr, datum);
            assertTrue(utxo.isPresent(), "Script UTXO not found");

            BigInteger sender1BalanceBefore = getLovelaceBalance(mixSender1Addr);

            // Regular Tx: deregister sender1 stake key (gets refund)
            Tx regularTx = new Tx()
                    .deregisterStakeAddress(mixSender1Addr)
                    .from(mixSender1Addr);

            // Script Tx: unlock from script
            Tx scriptTx = new Tx()
                    .collectFrom(utxo.get(), datum)
                    .payToAddress(receiver1, Amount.ada(2))
                    .attachSpendingValidator(script)
                    .withChangeAddress(scriptAddr, datum);

            Result<String> result = mixQuickTxBuilder.compose(regularTx, scriptTx)
                    .feePayer(mixSender1Addr)
                    .withSigner(SignerProviders.signerFrom(mixSender1))
                    .withSigner(SignerProviders.stakeKeySignerFrom(mixSender1))
                    .completeAndWait(System.out::println);

            System.out.println("compose_stakeDeregistration_refund_and_scriptUnlock: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), mixSender1Addr);

            BigInteger sender1BalanceAfter = getLovelaceBalance(mixSender1Addr);

            // Refund offsets deposit, so net cost should be roughly fee only (< 1 ADA)
            BigInteger sender1Spent = sender1BalanceBefore.subtract(sender1BalanceAfter);
            assertThat(sender1Spent).isLessThan(adaToLovelace(1));
        }

        // ---- Category C: DRep Registration + Script ----

        @Test
        @Order(7)
        void compose_drepRegistration_and_scriptUnlock() throws Exception {
            PlutusV3Script script = alwaysTrueV3();
            String scriptAddr = AddressProvider.getEntAddress(script, Networks.testnet()).toBech32();
            BigIntPlutusData datum = randomDatum();

            // Lock 5 ADA at script address
            lockFundsAtScript(script, scriptAddr, datum, Amount.ada(5), mixSender2, mixSender2Addr);

            Optional<Utxo> utxo = ScriptUtxoFinders.findFirstByInlineDatum(mixUtxoSupplier, scriptAddr, datum);
            assertTrue(utxo.isPresent(), "Script UTXO not found");

            BigInteger sender1BalanceBefore = getLovelaceBalance(mixSender1Addr);
            BigInteger drepDeposit = mixProtocolParamsSupplier.getProtocolParams().getDrepDeposit();

            var anchor = new Anchor("https://pages.bloxbean.com/cardano-stake/bloxbean-pool.json",
                    HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

            // Regular Tx: DRep registration
            Tx regularTx = new Tx()
                    .registerDRep(mixSender1, anchor)
                    .from(mixSender1Addr);

            // Script Tx: unlock from script
            Tx scriptTx = new Tx()
                    .collectFrom(utxo.get(), datum)
                    .payToAddress(receiver1, Amount.ada(2))
                    .attachSpendingValidator(script)
                    .withChangeAddress(scriptAddr, datum);

            Result<String> result = mixQuickTxBuilder.compose(regularTx, scriptTx)
                    .feePayer(mixSender1Addr)
                    .withSigner(SignerProviders.signerFrom(mixSender1))
                    .withSigner(SignerProviders.signerFrom(mixSender1.drepHdKeyPair()))
                    .completeAndWait(System.out::println);

            System.out.println("compose_drepRegistration_and_scriptUnlock: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), mixSender1Addr);

            BigInteger sender1BalanceAfter = getLovelaceBalance(mixSender1Addr);
            BigInteger sender1Spent = sender1BalanceBefore.subtract(sender1BalanceAfter);
            assertThat(sender1Spent).isGreaterThanOrEqualTo(drepDeposit);
        }

        @Test
        @Order(8)
        void singleTx_drepRegistration_and_scriptMint() throws Exception {
            // Must use PlutusV3 — PlutusV2 doesn't support Conway-era DRep certificates in script evaluation
            PlutusV3Script mintScript = alwaysTrueV3();
            String tokenName = getRandomTokenName();
            Asset asset = new Asset(tokenName, BigInteger.valueOf(800));

            BigInteger sender2BalanceBefore = getLovelaceBalance(mixSender2Addr);
            BigInteger drepDeposit = mixProtocolParamsSupplier.getProtocolParams().getDrepDeposit();

            var anchor = new Anchor("https://pages.bloxbean.com/cardano-stake/bloxbean-pool.json",
                    HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

            // Single Tx: DRep registration + plutus minting
            Tx mixedTx = new Tx()
                    .registerDRep(mixSender2, anchor)
                    .mintAsset(mintScript, asset, PlutusData.unit(), receiver1)
                    .from(mixSender2Addr);

            Result<String> result = mixQuickTxBuilder.compose(mixedTx)
                    .feePayer(mixSender2Addr)
                    .withSigner(SignerProviders.signerFrom(mixSender2))
                    .withSigner(SignerProviders.signerFrom(mixSender2.drepHdKeyPair()))
                    .completeAndWait(System.out::println);

            System.out.println("singleTx_drepRegistration_and_scriptMint: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), mixSender2Addr);

            BigInteger sender2BalanceAfter = getLovelaceBalance(mixSender2Addr);
            BigInteger sender2Spent = sender2BalanceBefore.subtract(sender2BalanceAfter);
            assertThat(sender2Spent).isGreaterThanOrEqualTo(drepDeposit);

            // Verify minted token at receiver1
            String policyId = mintScript.getPolicyId();
            boolean tokenFound = mixUtxoSupplier.getAll(receiver1).stream()
                    .flatMap(u -> u.getAmount().stream())
                    .anyMatch(a -> a.getUnit().contains(policyId));
            assertTrue(tokenFound, "Minted token should be at receiver1");
        }

        // ---- Category D: Mixed Native Script + Plutus Script Minting ----

        @Test
        @Order(9)
        void singleTx_nativeMint_and_plutusMint() throws Exception {
            PlutusV2Script plutusMintScript = alwaysTrueV2();
            ScriptPubkey nativeScript = ScriptPubkey.create(VerificationKey.create(mixSender1.publicKeyBytes()));

            String nativeTokenName = getRandomTokenName();
            String plutusTokenName = getRandomTokenName();
            Asset nativeAsset = new Asset(nativeTokenName, BigInteger.valueOf(5000));
            Asset plutusAsset = new Asset(plutusTokenName, BigInteger.valueOf(3000));

            // Single Tx: native mint + plutus mint
            Tx mixedTx = new Tx()
                    .mintAssets(nativeScript, nativeAsset, mixSender1Addr)
                    .mintAsset(plutusMintScript, plutusAsset, PlutusData.unit(), receiver1)
                    .from(mixSender1Addr);

            Result<String> result = mixQuickTxBuilder.compose(mixedTx)
                    .feePayer(mixSender1Addr)
                    .withSigner(SignerProviders.signerFrom(mixSender1))
                    .completeAndWait(System.out::println);

            System.out.println("singleTx_nativeMint_and_plutusMint: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), mixSender1Addr);

            // Verify native-minted token at sender1
            String nativePolicyId = nativeScript.getPolicyId();
            boolean nativeTokenFound = mixUtxoSupplier.getAll(mixSender1Addr).stream()
                    .flatMap(u -> u.getAmount().stream())
                    .anyMatch(a -> a.getUnit().contains(nativePolicyId));
            assertTrue(nativeTokenFound, "Native-minted token should be at sender1");

            // Verify plutus-minted token at receiver1
            String plutusPolicyId = plutusMintScript.getPolicyId();
            boolean plutusTokenFound = mixUtxoSupplier.getAll(receiver1).stream()
                    .flatMap(u -> u.getAmount().stream())
                    .anyMatch(a -> a.getUnit().contains(plutusPolicyId));
            assertTrue(plutusTokenFound, "Plutus-minted token should be at receiver1");
        }

        @Test
        @Order(10)
        void compose_nativeMint_and_plutusMint_and_scriptUnlock() throws Exception {
            PlutusV3Script spendScript = alwaysTrueV3();
            PlutusV2Script plutusMintScript = alwaysTrueV2();
            ScriptPubkey nativeScript = ScriptPubkey.create(VerificationKey.create(mixSender1.publicKeyBytes()));

            String scriptAddr = AddressProvider.getEntAddress(spendScript, Networks.testnet()).toBech32();
            BigIntPlutusData datum = randomDatum();

            // Lock 5 ADA at script address
            lockFundsAtScript(spendScript, scriptAddr, datum, Amount.ada(5), mixSender2, mixSender2Addr);

            Optional<Utxo> utxo = ScriptUtxoFinders.findFirstByInlineDatum(mixUtxoSupplier, scriptAddr, datum);
            assertTrue(utxo.isPresent(), "Script UTXO not found");

            String nativeTokenName = getRandomTokenName();
            String plutusTokenName = getRandomTokenName();
            Asset nativeAsset = new Asset(nativeTokenName, BigInteger.valueOf(2000));
            Asset plutusAsset = new Asset(plutusTokenName, BigInteger.valueOf(1000));

            // Tx1: native minting + regular payment
            Tx nativeMintTx = new Tx()
                    .mintAssets(nativeScript, nativeAsset, mixSender1Addr)
                    .payToAddress(receiver2, Amount.ada(1))
                    .from(mixSender1Addr);

            // Tx2: script unlock + plutus minting
            Tx scriptTx = new Tx()
                    .collectFrom(utxo.get(), datum)
                    .mintAsset(plutusMintScript, plutusAsset, PlutusData.unit(), receiver1)
                    .registerStakeAddress(new Account(Networks.testnet()).stakeAddress())
                    .payToAddress(receiver1, Amount.ada(2))
                    .attachSpendingValidator(spendScript)
                    .withChangeAddress(scriptAddr, datum);

            Result<String> result = mixQuickTxBuilder.compose(nativeMintTx, scriptTx)
                    .feePayer(mixSender1Addr)
                    .withSigner(SignerProviders.signerFrom(mixSender1))
                    .mergeOutputs(false)
                    .withTxInspector(transaction -> System.out.println(JsonUtil.getPrettyJson(transaction))
                    )
                    .completeAndWait(System.out::println);

            System.out.println("compose_nativeMint_and_plutusMint_and_scriptUnlock: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), mixSender1Addr);

            // Verify native-minted token at sender1
            String nativePolicyId = nativeScript.getPolicyId();
            boolean nativeTokenFound = mixUtxoSupplier.getAll(mixSender1Addr).stream()
                    .flatMap(u -> u.getAmount().stream())
                    .anyMatch(a -> a.getUnit().contains(nativePolicyId));
            assertTrue(nativeTokenFound, "Native-minted token should be at sender1");

            // Verify plutus-minted token at receiver1
            String plutusPolicyId = plutusMintScript.getPolicyId();
            boolean plutusTokenFound = mixUtxoSupplier.getAll(receiver1).stream()
                    .flatMap(u -> u.getAmount().stream())
                    .anyMatch(a -> a.getUnit().contains(plutusPolicyId));
            assertTrue(plutusTokenFound, "Plutus-minted token should be at receiver1");

            // Verify script change UTXO still exists
            Optional<Utxo> scriptChange = ScriptUtxoFinders.findFirstByInlineDatum(mixUtxoSupplier, scriptAddr, datum);
            assertTrue(scriptChange.isPresent(), "Script change UTXO should exist");
        }

        // ---- Category E: collectFrom + Deposit + Explicit DepositMode ----

        @Test
        @Order(11)
        void singleTx_collectFrom_stakeReg_depositMode_changeOutput_mergeOff() throws Exception {
            // Deregister sender1 stake key (may be registered from prior tests)
            tryDeregisterStakeKey(mixSender1Addr, mixSender1);

            PlutusV3Script script = alwaysTrueV3();
            String scriptAddr = AddressProvider.getEntAddress(script, Networks.testnet()).toBech32();
            BigIntPlutusData datum = randomDatum();

            // Lock 5 ADA at script address
            lockFundsAtScript(script, scriptAddr, datum, Amount.ada(5), mixSender2, mixSender2Addr);

            Optional<Utxo> utxo = ScriptUtxoFinders.findFirstByInlineDatum(mixUtxoSupplier, scriptAddr, datum);
            assertTrue(utxo.isPresent(), "Script UTXO not found");

            BigInteger sender1BalanceBefore = getLovelaceBalance(mixSender1Addr);
            BigInteger keyDeposit = new BigInteger(mixProtocolParamsSupplier.getProtocolParams().getKeyDeposit());

            // Single Tx: stake registration + collectFrom + payment
            // No explicit withChangeAddress → change defaults to feePayer (= mixSender1Addr)
            Tx mixedTx = new Tx()
                    .registerStakeAddress(mixSender1Addr)
                    .collectFrom(utxo.get(), datum)
                    .payToAddress(receiver1, Amount.ada(2))
                    .attachSpendingValidator(script);

            AtomicLong actualFee = new AtomicLong();
            Result<String> result = mixQuickTxBuilder.compose(mixedTx)
                    .feePayer(mixSender1Addr)
                    .withSigner(SignerProviders.signerFrom(mixSender1))
                    .depositMode(DepositMode.CHANGE_OUTPUT)
                    .mergeOutputs(false)
                    .withVerifier(txn -> {
                        // Verify receiver1 output with 2 ADA
                        TransactionOutput receiverOut = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(receiver1)).findFirst().orElseThrow();
                        assertThat(receiverOut.getValue().getCoin()).isEqualTo(adaToLovelace(2));
                        // Verify sender1 change output exists with positive balance
                        TransactionOutput senderOut = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(mixSender1Addr)).findFirst().orElseThrow();
                        assertThat(senderOut.getValue().getCoin()).isGreaterThan(BigInteger.ZERO);
                        actualFee.set(txn.getBody().getFee().longValue());
                    })
                    .completeAndWait(System.out::println);

            System.out.println("singleTx_collectFrom_stakeReg_depositMode_changeOutput_mergeOff: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), mixSender1Addr);

            BigInteger sender1BalanceAfter = getLovelaceBalance(mixSender1Addr);
            BigInteger sender1Spent = sender1BalanceBefore.subtract(sender1BalanceAfter);
            // Script UTXO (5 ADA) was consumed; 2 ADA went to receiver1. The remaining ~3 ADA
            // flows to sender1 as change (no withChangeAddress set), offsetting the deposit cost.
            BigInteger scriptExcess = adaToLovelace(5).subtract(adaToLovelace(2)); // 3 ADA
            assertThat(sender1Spent.add(scriptExcess)).isGreaterThanOrEqualTo(keyDeposit);
            assertThat(sender1BalanceAfter.add(BigInteger.valueOf(actualFee.longValue())).add(keyDeposit)).isEqualTo(sender1BalanceBefore.add(scriptExcess));
        }

        @Test
        @Order(12)
        void singleTx_collectFrom_stakeReg_depositMode_anyOutput_mergeOn() throws Exception {
            // Deregister sender1 stake key
            tryDeregisterStakeKey(mixSender1Addr, mixSender1);

            PlutusV3Script script = alwaysTrueV3();
            String scriptAddr = AddressProvider.getEntAddress(script, Networks.testnet()).toBech32();
            BigIntPlutusData datum = randomDatum();

            // Lock 5 ADA at script address
            lockFundsAtScript(script, scriptAddr, datum, Amount.ada(5), mixSender2, mixSender2Addr);

            Optional<Utxo> utxo = ScriptUtxoFinders.findFirstByInlineDatum(mixUtxoSupplier, scriptAddr, datum);
            assertTrue(utxo.isPresent(), "Script UTXO not found");

            BigInteger sender1BalanceBefore = getLovelaceBalance(mixSender1Addr);
            BigInteger keyDeposit = new BigInteger(mixProtocolParamsSupplier.getProtocolParams().getKeyDeposit());

            // Single Tx: stake registration + collectFrom + payment with ANY_OUTPUT + mergeOn
            Tx mixedTx = new Tx()
                    .registerStakeAddress(mixSender1Addr)
                    .collectFrom(utxo.get(), datum)
                    .payToAddress(receiver1, Amount.ada(2))
                    .attachSpendingValidator(script)
                    .from(mixSender1Addr);

            AtomicLong actualFee = new AtomicLong();
            Result<String> result = mixQuickTxBuilder.compose(mixedTx)
                    .feePayer(mixSender1Addr)
                    .withSigner(SignerProviders.signerFrom(mixSender1))
                    .depositMode(DepositMode.ANY_OUTPUT)
                    .mergeOutputs(true)
                    .withVerifier(txn -> {
                        // mergeOutputs=true: outputs should be merged
                        // Receiver1 output with 2 ADA
                        TransactionOutput receiverOut = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(receiver1)).findFirst().orElseThrow();
                        assertThat(receiverOut.getValue().getCoin()).isEqualTo(adaToLovelace(2));
                        // Sender1 change output with positive balance (deposit deducted)
                        TransactionOutput senderOut = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(mixSender1Addr)).findFirst().orElseThrow();
                        assertThat(senderOut.getValue().getCoin()).isGreaterThan(BigInteger.ZERO);
                        actualFee.set(txn.getBody().getFee().longValue());
                    })
                    .completeAndWait(System.out::println);

            System.out.println("singleTx_collectFrom_stakeReg_depositMode_anyOutput_mergeOn: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), mixSender1Addr);

            BigInteger sender1BalanceAfter = getLovelaceBalance(mixSender1Addr);
            BigInteger sender1Spent = sender1BalanceBefore.subtract(sender1BalanceAfter);
            // Script UTXO (5 ADA) was consumed; 2 ADA went to receiver1. The remaining ~3 ADA
            // flows to sender1 as change, offsetting the deposit cost.
            BigInteger scriptExcess = adaToLovelace(5).subtract(adaToLovelace(2)); // 3 ADA
            assertThat(sender1Spent.add(scriptExcess)).isGreaterThanOrEqualTo(keyDeposit);
            assertThat(sender1BalanceAfter.add(BigInteger.valueOf(actualFee.longValue())).add(keyDeposit)).isEqualTo(sender1BalanceBefore.add(scriptExcess));
        }

        @Test
        @Order(13)
        void singleTx_collectFrom_stakeReg_withScriptChangeAddr_depositMode_auto() throws Exception {
            // Deregister sender1 stake key
            tryDeregisterStakeKey(mixSender1Addr, mixSender1);

            PlutusV3Script script = alwaysTrueV3();
            String scriptAddr = AddressProvider.getEntAddress(script, Networks.testnet()).toBech32();
            BigIntPlutusData datum = randomDatum();

            // Lock 15 ADA at script (enough for outputs + change at script addr)
            lockFundsAtScript(script, scriptAddr, datum, Amount.ada(15), mixSender2, mixSender2Addr);

            Optional<Utxo> utxo = ScriptUtxoFinders.findFirstByInlineDatum(mixUtxoSupplier, scriptAddr, datum);
            assertTrue(utxo.isPresent(), "Script UTXO not found");

            BigInteger sender1BalanceBefore = getLovelaceBalance(mixSender1Addr);
            BigInteger keyDeposit = new BigInteger(mixProtocolParamsSupplier.getProtocolParams().getKeyDeposit());

            // Single Tx: collectFrom + stake reg + withChangeAddress(scriptAddr, datum)
            // Change goes to script address, NOT sender. AUTO should fall back to NEW_UTXO_SELECTION.
            Tx mixedTx = new Tx()
                    .registerStakeAddress(mixSender1Addr)
                    .collectFrom(utxo.get(), datum)
                    .payToAddress(receiver1, Amount.ada(2))
                    .attachSpendingValidator(script)
                    .withChangeAddress(scriptAddr, datum)
                    .from(mixSender1Addr);

            Result<String> result = mixQuickTxBuilder.compose(mixedTx)
                    .feePayer(mixSender1Addr)
                    .withSigner(SignerProviders.signerFrom(mixSender1))
                    .depositMode(DepositMode.AUTO)
                    .completeAndWait(System.out::println);

            System.out.println("singleTx_collectFrom_stakeReg_withScriptChangeAddr_depositMode_auto: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), mixSender1Addr);

            BigInteger sender1BalanceAfter = getLovelaceBalance(mixSender1Addr);
            BigInteger sender1Spent = sender1BalanceBefore.subtract(sender1BalanceAfter);
            // sender1 pays at least keyDeposit (deposit resolved via fallback)
            assertThat(sender1Spent).isGreaterThanOrEqualTo(keyDeposit);

            // Verify script change UTXO exists with datum
            Optional<Utxo> scriptChange = ScriptUtxoFinders.findFirstByInlineDatum(mixUtxoSupplier, scriptAddr, datum);
            assertTrue(scriptChange.isPresent(), "Script change UTXO should exist with datum");
        }

        @Test
        @Order(14)
        void singleTx_collectFrom_stakeReg_from_different_from_feePayer() throws Exception {
            // Deregister sender2 stake key
            tryDeregisterStakeKey(mixSender2Addr, mixSender2);

            PlutusV3Script script = alwaysTrueV3();
            String scriptAddr = AddressProvider.getEntAddress(script, Networks.testnet()).toBech32();
            BigIntPlutusData datum = randomDatum();

            // Lock 5 ADA at script address
            lockFundsAtScript(script, scriptAddr, datum, Amount.ada(5), mixSender1, mixSender1Addr);

            Optional<Utxo> utxo = ScriptUtxoFinders.findFirstByInlineDatum(mixUtxoSupplier, scriptAddr, datum);
            assertTrue(utxo.isPresent(), "Script UTXO not found");

            BigInteger sender1BalanceBefore = getLovelaceBalance(mixSender1Addr);
            BigInteger sender2BalanceBefore = getLovelaceBalance(mixSender2Addr);
            BigInteger keyDeposit = new BigInteger(mixProtocolParamsSupplier.getProtocolParams().getKeyDeposit());

            // Single Tx: collectFrom + stake reg for sender2 with from(sender2) + feePayer(sender1)
            // Deposit should come from sender2 (from), fee from sender1 (feePayer)
            Tx mixedTx = new Tx()
                    .registerStakeAddress(mixSender2Addr)
                    .collectFrom(utxo.get(), datum)
                    .payToAddress(receiver1, Amount.ada(2))
                    .attachSpendingValidator(script)
                    .from(mixSender2Addr);

            Result<String> result = mixQuickTxBuilder.compose(mixedTx)
                    .feePayer(mixSender1Addr)
                    .withSigner(SignerProviders.signerFrom(mixSender1))
                    .withSigner(SignerProviders.signerFrom(mixSender2))
                    .completeAndWait(System.out::println);

            System.out.println("singleTx_collectFrom_stakeReg_from_different_from_feePayer: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), mixSender1Addr);

            BigInteger sender1BalanceAfter = getLovelaceBalance(mixSender1Addr);
            BigInteger sender2BalanceAfter = getLovelaceBalance(mixSender2Addr);

            // Deposit comes from sender2 (.from()), fee from sender1 (feePayer).
            // Script UTXO (5 ADA) excess (5 - 2 = 3 ADA) flows to sender2 as change,
            // offsetting the deposit cost for sender2.
            BigInteger sender2Spent = sender2BalanceBefore.subtract(sender2BalanceAfter);
            BigInteger sender1Spent = sender1BalanceBefore.subtract(sender1BalanceAfter);
            BigInteger scriptExcess = adaToLovelace(5).subtract(adaToLovelace(2)); // 3 ADA

            assertThat(sender2Spent.add(scriptExcess)).isGreaterThanOrEqualTo(keyDeposit);
            // sender1 only pays fee, which is much less than keyDeposit
            assertThat(sender1Spent).isLessThan(keyDeposit);
        }

        @Test
        @Order(15)
        void compose_scriptUnlock_and_stakeReg_depositMode_changeOutput() throws Exception {
            // Deregister sender1 stake key
            tryDeregisterStakeKey(mixSender1Addr, mixSender1);

            PlutusV3Script script = alwaysTrueV3();
            String scriptAddr = AddressProvider.getEntAddress(script, Networks.testnet()).toBech32();
            BigIntPlutusData datum = randomDatum();

            // Lock 5 ADA at script address
            lockFundsAtScript(script, scriptAddr, datum, Amount.ada(5), mixSender2, mixSender2Addr);

            Optional<Utxo> utxo = ScriptUtxoFinders.findFirstByInlineDatum(mixUtxoSupplier, scriptAddr, datum);
            assertTrue(utxo.isPresent(), "Script UTXO not found");

            BigInteger sender1BalanceBefore = getLovelaceBalance(mixSender1Addr);
            BigInteger keyDeposit = new BigInteger(mixProtocolParamsSupplier.getProtocolParams().getKeyDeposit());

            // Compose: regularTx (stake reg + payment to trigger UTXO selection) + scriptTx (unlock)
            // The regularTx needs a payment output so UTXO selection runs for sender1,
            // creating a ChangeOutput that CHANGE_OUTPUT mode can deduct the deposit from.
            Tx regularTx = new Tx()
                    .registerStakeAddress(mixSender1Addr)
                    .payToAddress(receiver2, Amount.ada(1))
                    .from(mixSender1Addr);

            Tx scriptTx = new Tx()
                    .collectFrom(utxo.get(), datum)
                    .payToAddress(receiver1, Amount.ada(2))
                    .attachSpendingValidator(script)
                    .withChangeAddress(scriptAddr, datum);

            AtomicLong actualFee = new AtomicLong();
            Result<String> result = mixQuickTxBuilder.compose(regularTx, scriptTx)
                    .feePayer(mixSender1Addr)
                    .withSigner(SignerProviders.signerFrom(mixSender1))
                    .depositMode(DepositMode.CHANGE_OUTPUT)
                    .mergeOutputs(false)
                    .withVerifier(txn -> {
                        // Verify receiver1 output with 2 ADA
                        TransactionOutput receiverOut = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(receiver1)).findFirst().orElseThrow();
                        assertThat(receiverOut.getValue().getCoin()).isEqualTo(adaToLovelace(2));
                        // Verify receiver2 output with 1 ADA
                        TransactionOutput receiver2Out = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(receiver2)).findFirst().orElseThrow();
                        assertThat(receiver2Out.getValue().getCoin()).isEqualTo(adaToLovelace(1));
                        // Verify sender1 change output (deposit deducted from change)
                        TransactionOutput sender1Out = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(mixSender1Addr)).findFirst().orElseThrow();
                        assertThat(sender1Out.getValue().getCoin()).isGreaterThan(BigInteger.ZERO);
                        actualFee.set(txn.getBody().getFee().longValue());
                    })
                    .completeAndWait(System.out::println);

            System.out.println("compose_scriptUnlock_and_stakeReg_depositMode_changeOutput: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), mixSender1Addr);

            BigInteger sender1BalanceAfter = getLovelaceBalance(mixSender1Addr);
            BigInteger sender1Spent = sender1BalanceBefore.subtract(sender1BalanceAfter);
            // sender1 pays keyDeposit + 1 ADA payment + fee
            assertThat(sender1Spent).isEqualTo(keyDeposit.add(adaToLovelace(1)).add(BigInteger.valueOf(actualFee.longValue())));

            // Verify script change UTXO still exists with datum
            Optional<Utxo> scriptChange = ScriptUtxoFinders.findFirstByInlineDatum(mixUtxoSupplier, scriptAddr, datum);
            assertTrue(scriptChange.isPresent(), "Script change UTXO should exist");
        }
    }

    // ========================================================================
    // Category 9: Deposit Resolution API Tests
    // ========================================================================

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class DepositResolutionTests {
        static BackendService drBackendService;
        static Account drSender1;
        static Account drSender2;
        static Account drTreasury;
        static String drSender1Addr;
        static String drSender2Addr;
        static String drTreasuryAddr;
        static QuickTxBuilder drQuickTxBuilder;
        static UtxoSupplier drUtxoSupplier;
        static ProtocolParamsSupplier drProtocolParamsSupplier;

        @BeforeAll
        static void setupDepositResolution() {
            drBackendService = new BFBackendService("http://localhost:8080/api/v1/", "Dummy");
            drQuickTxBuilder = new QuickTxBuilder(drBackendService);
            drUtxoSupplier = new DefaultUtxoSupplier(drBackendService.getUtxoService());
            drProtocolParamsSupplier = new DefaultProtocolParamsSupplier(drBackendService.getEpochService());

            String sender1Mnemonic = "drive useless envelope shine range ability time copper alarm museum near flee wrist live type device meadow allow churn purity wisdom praise drop code";
            drSender1 = new Account(Networks.testnet(), sender1Mnemonic);
            drSender1Addr = drSender1.baseAddress();

            String sender2Mnemonic = "access else envelope between rubber celery forum brief bubble notice stomach add initial avocado current net film aunt quick text joke chase robust artefact";
            drSender2 = new Account(Networks.testnet(), sender2Mnemonic);
            drSender2Addr = drSender2.baseAddress();

            String treasuryMnemonic = "test test test test test test test test test test test test test test test test test test test test test test test sauce";
            drTreasury = new Account(Networks.testnet(), treasuryMnemonic);
            drTreasuryAddr = drTreasury.baseAddress();

            resetDevNet();
            topUpFund(drSender1Addr, 500000);
            topUpFund(drSender2Addr, 500000);
            topUpFund(drTreasuryAddr, 500000);
        }

        private BigInteger getLovelaceBalance(String address) {
            return drUtxoSupplier.getAll(address).stream()
                    .flatMap(u -> u.getAmount().stream())
                    .filter(a -> a.getUnit().equals("lovelace"))
                    .map(Amount::getQuantity)
                    .reduce(BigInteger.ZERO, BigInteger::add);
        }

        private void tryDeregisterStakeKey(String address, Account account) {
            try {
                Tx tx = new Tx()
                        .deregisterStakeAddress(address)
                        .from(address);

                Result<String> result = drQuickTxBuilder.compose(tx)
                        .withSigner(SignerProviders.signerFrom(account))
                        .withSigner(SignerProviders.stakeKeySignerFrom(account))
                        .completeAndWait(msg -> System.out.println(msg));

                if (result.isSuccessful())
                    checkIfUtxoAvailable(result.getValue(), address);
            } catch (Exception e) {
                // Ignore — stake key may not be registered
            }
        }

        private void consolidateUtxos(String address, Account account) {
            Tx tx = new Tx()
                    .payToAddress(address, Amount.lovelace(getLovelaceBalance(address).subtract(Amount.ada(1).getQuantity())))
                    .from(address);
            Result<String> result = drQuickTxBuilder.compose(tx)
                    .withSigner(SignerProviders.signerFrom(account))
                    .completeAndWait(System.out::println);
            if (result.isSuccessful())
                checkIfUtxoAvailable(result.getValue(), address);
        }

        private void splitUtxos(String address, Account account) {
            Tx tx = new Tx()
                    .payToAddress(address, Amount.ada(100))
                    .payToAddress(address, Amount.ada(100))
                    .from(address);
            Result<String> result = drQuickTxBuilder.compose(tx)
                    .withSigner(SignerProviders.signerFrom(account))
                    .mergeOutputs(false)
                    .completeAndWait(System.out::println);
            assertTrue(result.isSuccessful(), "Split UTXOs tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), address);
        }

        // ---- Test: Regular Tx + deposit + mergeOutputs=false ----

        @Test
        @Order(1)
        void stakeRegistration_autoMode_mergeOff() throws Exception {
            BigInteger sender1BalanceBefore = getLovelaceBalance(drSender1Addr);
            BigInteger keyDeposit = new BigInteger(drProtocolParamsSupplier.getProtocolParams().getKeyDeposit());

            Tx tx = new Tx()
                    .registerStakeAddress(drSender1Addr)
                    .payToAddress(receiver1, Amount.ada(5))
                    .payToAddress(receiver1, Amount.ada(4))
                    .from(drSender1Addr);

            Result<String> result = drQuickTxBuilder.compose(tx)
                    .withSigner(SignerProviders.signerFrom(drSender1))
                    .mergeOutputs(false)
                    .withVerifier(txn -> {
                        // mergeOutputs=false: 3 outputs — two separate receiver1 outputs + sender change
                        assertThat(txn.getBody().getOutputs()).hasSize(3);
                        // Two distinct receiver1 outputs with 5 ADA and 4 ADA
                        List<TransactionOutput> receiverOuts = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(receiver1)).toList();
                        assertThat(receiverOuts).hasSize(2);
                        assertThat(receiverOuts.stream().map(o -> o.getValue().getCoin()).toList())
                                .containsExactlyInAnyOrder(adaToLovelace(5), adaToLovelace(4));
                        // Sender change output exists (deposit already deducted during building)
                        TransactionOutput senderOut = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(drSender1Addr)).findFirst().orElseThrow();
                        assertThat(senderOut.getValue().getCoin()).isGreaterThan(BigInteger.ZERO);
                    })
                    .completeAndWait(System.out::println);

            System.out.println("stakeRegistration_autoMode_mergeOff: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), drSender1Addr);

            BigInteger sender1BalanceAfter = getLovelaceBalance(drSender1Addr);
            BigInteger spent = sender1BalanceBefore.subtract(sender1BalanceAfter);

            // spent = 9 ADA (5+4 payment) + keyDeposit + fee
            BigInteger feeOnly = spent.subtract(adaToLovelace(9)).subtract(keyDeposit);
            assertThat(feeOnly).isGreaterThan(BigInteger.ZERO);   // fee > 0
            assertThat(feeOnly).isLessThan(keyDeposit);             // fee < 2 ADA
        }

        // ---- Test: Regular Tx + deposit + mergeOutputs=true (default) ----

        @Test
        @Order(2)
        void stakeRegistration_autoMode_mergeOn() throws Exception {
            // Deregister sender1 first (registered in Test 1)
            tryDeregisterStakeKey(drSender1Addr, drSender1);

            BigInteger sender1BalanceBefore = getLovelaceBalance(drSender1Addr);
            BigInteger keyDeposit = new BigInteger(drProtocolParamsSupplier.getProtocolParams().getKeyDeposit());

            Tx tx = new Tx()
                    .registerStakeAddress(drSender1Addr)
                    .payToAddress(receiver1, Amount.ada(5))
                    .payToAddress(receiver1, Amount.ada(4))
                    .from(drSender1Addr);

            Result<String> result = drQuickTxBuilder.compose(tx)
                    .withSigner(SignerProviders.signerFrom(drSender1))
                    .mergeOutputs(true) // Default
                    .withVerifier(txn -> {
                        // mergeOutputs=true: one merged receiver1 output + sender change = 2 outputs
                        assertThat(txn.getBody().getOutputs()).hasSize(2);
                        // Receiver gets merged 9 ADA (5+4)
                        TransactionOutput receiverOut = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(receiver1)).findFirst().orElseThrow();
                        assertThat(receiverOut.getValue().getCoin()).isEqualTo(adaToLovelace(9));
                        // Sender change output exists with positive balance (deposit deducted)
                        TransactionOutput senderOut = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(drSender1Addr)).findFirst().orElseThrow();
                        assertThat(senderOut.getValue().getCoin()).isGreaterThan(BigInteger.ZERO);
                    })
                    .completeAndWait(System.out::println);

            System.out.println("stakeRegistration_autoMode_mergeOn: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), drSender1Addr);

            BigInteger sender1BalanceAfter = getLovelaceBalance(drSender1Addr);
            BigInteger spent = sender1BalanceBefore.subtract(sender1BalanceAfter);

            // spent = 9 ADA (5+4 payment) + keyDeposit + fee
            BigInteger feeOnly = spent.subtract(adaToLovelace(9)).subtract(keyDeposit);
            assertThat(feeOnly).isGreaterThan(BigInteger.ZERO);   // fee > 0
            assertThat(feeOnly).isLessThan(keyDeposit);             // fee < 2 ADA
        }

        // ---- Test: Explicit depositPayer different from sender ----

        @Test
        @Order(3)
        void stakeRegistration_explicit_depositPayer() throws Exception {
            // Deregister sender1 first
            tryDeregisterStakeKey(drSender1Addr, drSender1);

            BigInteger sender1BalanceBefore = getLovelaceBalance(drSender1Addr);
            BigInteger treasuryBalanceBefore = getLovelaceBalance(drTreasuryAddr);
            BigInteger keyDeposit = new BigInteger(drProtocolParamsSupplier.getProtocolParams().getKeyDeposit());

            Tx tx = new Tx()
                    .registerStakeAddress(drSender1Addr)
                    .payToAddress(receiver1, Amount.ada(3))
                    .payToAddress(receiver1, Amount.ada(4))
                    .from(drSender1Addr);

            AtomicLong actualFee = new AtomicLong();
            Result<String> result = drQuickTxBuilder.compose(tx)
                    .withSigner(SignerProviders.signerFrom(drSender1))
                    .withSigner(SignerProviders.signerFrom(drTreasury))
                    .depositPayer(drTreasuryAddr)
                    .mergeOutputs(false)
                    .withVerifier(txn -> {
                        // mergeOutputs=false + depositPayer=treasury: 4 outputs
                        // 2 receiver1 outputs (3 ADA, 4 ADA) + sender1 change + treasury change
                        assertThat(txn.getBody().getOutputs()).hasSize(4);
                        // Two distinct receiver1 outputs
                        List<TransactionOutput> receiverOuts = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(receiver1)).toList();
                        assertThat(receiverOuts).hasSize(2);
                        assertThat(receiverOuts.stream().map(o -> o.getValue().getCoin()).toList())
                                .containsExactlyInAnyOrder(adaToLovelace(3), adaToLovelace(4));
                        // Sender1 change output with positive balance
                        TransactionOutput sender1Out = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(drSender1Addr)).findFirst().orElseThrow();
                        assertThat(sender1Out.getValue().getCoin()).isGreaterThan(BigInteger.ZERO);
                        // Treasury change output with positive balance
                        TransactionOutput treasuryOut = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(drTreasuryAddr)).findFirst().orElseThrow();
                        assertThat(treasuryOut.getValue().getCoin()).isGreaterThan(BigInteger.ZERO);

                        actualFee.set(txn.getBody().getFee().longValue());
                    })
                    .completeAndWait(System.out::println);

            System.out.println("stakeRegistration_explicit_depositPayer: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), drSender1Addr);

            BigInteger sender1BalanceAfter = getLovelaceBalance(drSender1Addr);
            BigInteger treasuryBalanceAfter = getLovelaceBalance(drTreasuryAddr);

            BigInteger sender1Spent = sender1BalanceBefore.subtract(sender1BalanceAfter);
            BigInteger treasurySpent = treasuryBalanceBefore.subtract(treasuryBalanceAfter);

            // Treasury pays exactly the key deposit
            assertThat(treasurySpent).isEqualTo(keyDeposit);
            // sender1 pays 7 ADA (3+4 payment) + fee only — fee portion must be less than keyDeposit,
            // proving the deposit was not charged to sender1
            BigInteger sender1Fee = sender1Spent.subtract(adaToLovelace(7));
            assertThat(sender1Fee).isGreaterThan(BigInteger.ZERO);
            assertThat(sender1Fee.longValue()).isEqualTo(actualFee.longValue());
        }

        // ---- Test: DepositMode.NEW_UTXO_SELECTION (single UTXO → failure) ----

        @Test
        @Order(4)
        void stakeRegistration_depositMode_newUtxoSelection_singleUtxo_fails() throws Exception {
            // Deregister sender1 first — leaves a single UTXO
            tryDeregisterStakeKey(drSender1Addr, drSender1);

            Tx tx = new Tx()
                    .registerStakeAddress(drSender1Addr)
                    .payToAddress(receiver1, Amount.ada(3))
                    .payToAddress(receiver1, Amount.ada(4))
                    .from(drSender1Addr);

            // With a single UTXO, NEW_UTXO_SELECTION cannot find a separate UTXO for the deposit
            assertThrows(InsufficientBalanceException.class, () ->
                    drQuickTxBuilder.compose(tx)
                            .withSigner(SignerProviders.signerFrom(drSender1))
                            .depositMode(DepositMode.NEW_UTXO_SELECTION)
                            .mergeOutputs(false)
                            .complete()
            );
        }

        // ---- Test: DepositMode.NEW_UTXO_SELECTION (multiple UTXOs → success) ----

        @Test
        @Order(5)
        void stakeRegistration_depositMode_newUtxoSelection_multipleUtxos() throws Exception {
            // Deregister sender1 first
            tryDeregisterStakeKey(drSender1Addr, drSender1);

            // Split into multiple UTXOs so NEW_UTXO_SELECTION can find one for deposit
            splitUtxos(drSender1Addr, drSender1);

            BigInteger sender1BalanceBefore = getLovelaceBalance(drSender1Addr);
            BigInteger keyDeposit = new BigInteger(drProtocolParamsSupplier.getProtocolParams().getKeyDeposit());

            Tx tx = new Tx()
                    .registerStakeAddress(drSender1Addr)
                    .payToAddress(receiver1, Amount.ada(3))
                    .payToAddress(receiver1, Amount.ada(4))
                    .from(drSender1Addr);

            AtomicLong actualFee = new AtomicLong();
            Result<String> result = drQuickTxBuilder.compose(tx)
                    .withSigner(SignerProviders.signerFrom(drSender1))
                    .depositMode(DepositMode.NEW_UTXO_SELECTION)
                    .mergeOutputs(false)
                    .withVerifier(txn -> {
                        // mergeOutputs=false + NEW_UTXO_SELECTION: ≥3 outputs
                        // 2 receiver1 outputs (3 ADA, 4 ADA) + at least 1 sender change
                        assertThat(txn.getBody().getOutputs().size()).isGreaterThanOrEqualTo(3);
                        // Two distinct receiver1 outputs
                        List<TransactionOutput> receiverOuts = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(receiver1)).toList();
                        assertThat(receiverOuts).hasSize(2);
                        assertThat(receiverOuts.stream().map(o -> o.getValue().getCoin()).toList())
                                .containsExactlyInAnyOrder(adaToLovelace(3), adaToLovelace(4));
                        // At least one sender1 change output with positive balance
                        List<TransactionOutput> senderOuts = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(drSender1Addr)).toList();
                        assertThat(senderOuts).isNotEmpty();
                        assertThat(senderOuts.get(0).getValue().getCoin()).isGreaterThan(BigInteger.ZERO);
                        actualFee.set(txn.getBody().getFee().longValue());
                    })
                    .completeAndWait(System.out::println);

            System.out.println("stakeRegistration_depositMode_newUtxoSelection_multipleUtxos: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), drSender1Addr);

            BigInteger sender1BalanceAfter = getLovelaceBalance(drSender1Addr);
            BigInteger spent = sender1BalanceBefore.subtract(sender1BalanceAfter);

            // spent = 7 ADA (3+4 payment) + keyDeposit + actualFee
            BigInteger feeOnly = spent.subtract(adaToLovelace(7)).subtract(keyDeposit);
            assertThat(feeOnly.longValue()).isEqualTo(actualFee.longValue());
        }

        // ---- Test: DRep registration deposit ----

        @Test
        @Order(6)
        void drepRegistration_autoMode() throws Exception {
            BigInteger sender2BalanceBefore = getLovelaceBalance(drSender2Addr);
            BigInteger drepDeposit = drProtocolParamsSupplier.getProtocolParams().getDrepDeposit();

            var anchor = new Anchor("https://pages.bloxbean.com/cardano-stake/bloxbean-pool.json",
                    HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

            Tx tx = new Tx()
                    .registerDRep(drSender2, anchor)
                    .payToAddress(receiver1, Amount.ada(3))
                    .payToAddress(receiver1, Amount.ada(4))
                    .from(drSender2Addr);

            AtomicLong actualFee = new AtomicLong();
            Result<String> result = drQuickTxBuilder.compose(tx)
                    .withSigner(SignerProviders.signerFrom(drSender2))
                    .withSigner(SignerProviders.signerFrom(drSender2.drepHdKeyPair()))
                    .mergeOutputs(false)
                    .withVerifier(txn -> {
                        // mergeOutputs=false: 3 outputs — 2 receiver1 (3 ADA, 4 ADA) + sender2 change
                        assertThat(txn.getBody().getOutputs()).hasSize(3);
                        // Two distinct receiver1 outputs
                        List<TransactionOutput> receiverOuts = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(receiver1)).toList();
                        assertThat(receiverOuts).hasSize(2);
                        assertThat(receiverOuts.stream().map(o -> o.getValue().getCoin()).toList())
                                .containsExactlyInAnyOrder(adaToLovelace(3), adaToLovelace(4));
                        // Sender2 change output with positive balance
                        TransactionOutput sender2Out = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(drSender2Addr)).findFirst().orElseThrow();
                        assertThat(sender2Out.getValue().getCoin()).isGreaterThan(BigInteger.ZERO);
                        actualFee.set(txn.getBody().getFee().longValue());
                    })
                    .completeAndWait(System.out::println);

            System.out.println("drepRegistration_autoMode: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), drSender2Addr);

            BigInteger sender2BalanceAfter = getLovelaceBalance(drSender2Addr);
            BigInteger spent = sender2BalanceBefore.subtract(sender2BalanceAfter);

            // spent = 7 ADA (3+4 payment) + drepDeposit + actualFee
            BigInteger feeOnly = spent.subtract(adaToLovelace(7)).subtract(drepDeposit);
            assertThat(feeOnly.longValue()).isEqualTo(actualFee.longValue());
        }

        // ---- Test: Composed Txs with depositPayer ----

        @Test
        @Order(7)
        void compose_stakeReg_and_payment_depositPayer_treasury() throws Exception {
            // Deregister sender1 first
            tryDeregisterStakeKey(drSender1Addr, drSender1);

            BigInteger sender1BalanceBefore = getLovelaceBalance(drSender1Addr);
            BigInteger sender2BalanceBefore = getLovelaceBalance(drSender2Addr);
            BigInteger treasuryBalanceBefore = getLovelaceBalance(drTreasuryAddr);
            BigInteger keyDeposit = new BigInteger(drProtocolParamsSupplier.getProtocolParams().getKeyDeposit());

            // Tx1: stake registration
            Tx stakeRegTx = new Tx()
                    .registerStakeAddress(drSender1Addr)
                    .from(drSender1Addr);

            // Tx2: regular payment
            Tx paymentTx = new Tx()
                    .payToAddress(receiver1, Amount.ada(3))
                    .from(drSender2Addr);

            AtomicLong actualFee = new AtomicLong();
            Result<String> result = drQuickTxBuilder.compose(stakeRegTx, paymentTx)
                    .feePayer(drSender1Addr)
                    .withSigner(SignerProviders.signerFrom(drSender1))
                    .withSigner(SignerProviders.signerFrom(drSender2))
                    .depositPayer(drTreasuryAddr)
                    .withSigner(SignerProviders.signerFrom(drTreasury))
                    .withVerifier(txn -> {
                        // Composed Txs: receiver1 output + sender1 change + sender2 change + treasury change
                        // Receiver1 output with 3 ADA
                        TransactionOutput receiverOut = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(receiver1)).findFirst().orElseThrow();
                        assertThat(receiverOut.getValue().getCoin()).isEqualTo(adaToLovelace(3));
                        // Sender1 change output with positive balance (feePayer)
                        TransactionOutput sender1Out = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(drSender1Addr)).findFirst().orElseThrow();
                        assertThat(sender1Out.getValue().getCoin()).isGreaterThan(BigInteger.ZERO);
                        // Sender2 change output exists (paid exactly 3 ADA)
                        List<TransactionOutput> sender2Outs = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(drSender2Addr)).toList();
                        assertThat(sender2Outs).isNotEmpty();
                        // Treasury change output with positive balance
                        TransactionOutput treasuryOut = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(drTreasuryAddr)).findFirst().orElseThrow();
                        assertThat(treasuryOut.getValue().getCoin()).isGreaterThan(BigInteger.ZERO);
                        actualFee.set(txn.getBody().getFee().longValue());
                    })
                    .completeAndWait(System.out::println);

            System.out.println("compose_stakeReg_and_payment_depositPayer_treasury: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), drSender1Addr);

            BigInteger sender1BalanceAfter = getLovelaceBalance(drSender1Addr);
            BigInteger sender2BalanceAfter = getLovelaceBalance(drSender2Addr);
            BigInteger treasuryBalanceAfter = getLovelaceBalance(drTreasuryAddr);

            BigInteger treasurySpent = treasuryBalanceBefore.subtract(treasuryBalanceAfter);
            BigInteger sender2Spent = sender2BalanceBefore.subtract(sender2BalanceAfter);

            //No change in sender1 balance as sender1 is fee payer but deposit is paid by treasury
            assertThat(sender1BalanceAfter).isEqualTo(sender1BalanceBefore.subtract(BigInteger.valueOf(actualFee.longValue())));

            // Treasury pays exactly the key deposit
            assertThat(treasurySpent).isEqualTo(keyDeposit);
            // Sender2 pays only 3 ADA payment
            assertThat(sender2Spent).isEqualTo(adaToLovelace(3));
        }

        // ---- Test: Multiple deposits (stake + DRep) in single Tx ----

        @Test
        @Order(8)
        void multipleDeposits_stakeAndDrep_singleTx() throws Exception {
            // Deregister stake key for sender1 (registered in Test 6)
            tryDeregisterStakeKey(drSender1Addr, drSender1);
            // Deregister DRep for sender1 if registered
            try {
                Tx unregTx = new Tx()
                        .unregisterDRep(drSender1.drepCredential())
                        .from(drSender1Addr);
                drQuickTxBuilder.compose(unregTx)
                        .withSigner(SignerProviders.drepKeySignerFrom(drSender1))
                        .withSigner(SignerProviders.signerFrom(drSender1))
                        .completeAndWait(msg -> {});
            } catch (Exception e) {
                // Ignore
            }

            BigInteger sender1BalanceBefore = getLovelaceBalance(drSender1Addr);
            BigInteger keyDeposit = new BigInteger(drProtocolParamsSupplier.getProtocolParams().getKeyDeposit());
            BigInteger drepDeposit = drProtocolParamsSupplier.getProtocolParams().getDrepDeposit();
            BigInteger totalDeposits = keyDeposit.add(drepDeposit);

            var anchor = new Anchor("https://pages.bloxbean.com/cardano-stake/bloxbean-pool.json",
                    HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

            Tx tx = new Tx()
                    .registerStakeAddress(drSender1Addr)
                    .registerDRep(drSender1, anchor)
                    .payToAddress(receiver1, Amount.ada(3))
                    .payToAddress(receiver1, Amount.ada(4))
                    .from(drSender1Addr);

            AtomicLong actualFee = new AtomicLong();
            Result<String> result = drQuickTxBuilder.compose(tx)
                    .withSigner(SignerProviders.signerFrom(drSender1))
                    .withSigner(SignerProviders.signerFrom(drSender1.drepHdKeyPair()))
                    .mergeOutputs(false)
                    .withVerifier(txn -> {
                        // mergeOutputs=false: 3 outputs — 2 receiver1 (3 ADA, 4 ADA) + sender1 change
                        assertThat(txn.getBody().getOutputs()).hasSize(3);
                        // Two distinct receiver1 outputs
                        List<TransactionOutput> receiverOuts = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(receiver1)).toList();
                        assertThat(receiverOuts).hasSize(2);
                        assertThat(receiverOuts.stream().map(o -> o.getValue().getCoin()).toList())
                                .containsExactlyInAnyOrder(adaToLovelace(3), adaToLovelace(4));
                        // Sender1 change output with positive balance (after both deposits deducted)
                        TransactionOutput sender1Out = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(drSender1Addr)).findFirst().orElseThrow();
                        assertThat(sender1Out.getValue().getCoin()).isGreaterThan(BigInteger.ZERO);
                        actualFee.set(txn.getBody().getFee().longValue());
                    })
                    .completeAndWait(System.out::println);

            System.out.println("multipleDeposits_stakeAndDrep_singleTx: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), drSender1Addr);

            BigInteger sender1BalanceAfter = getLovelaceBalance(drSender1Addr);
            BigInteger spent = sender1BalanceBefore.subtract(sender1BalanceAfter);

            // spent = 7 ADA (3+4 payment) + totalDeposits (keyDeposit + drepDeposit) + fee
            BigInteger feeOnly = spent.subtract(adaToLovelace(7)).subtract(totalDeposits);
            assertThat(feeOnly.longValue()).isEqualTo(actualFee.longValue());
        }

        // ---- Test: DepositMode.CHANGE_OUTPUT + mergeOutputs=false ----

        @Test
        @Order(9)
        void stakeRegistration_depositMode_changeOutput_mergeOff() throws Exception {
            // Deregister sender1 first
            tryDeregisterStakeKey(drSender1Addr, drSender1);

            BigInteger sender1BalanceBefore = getLovelaceBalance(drSender1Addr);
            BigInteger keyDeposit = new BigInteger(drProtocolParamsSupplier.getProtocolParams().getKeyDeposit());

            Tx tx = new Tx()
                    .registerStakeAddress(drSender1Addr)
                    .payToAddress(receiver1, Amount.ada(5))
                    .payToAddress(receiver1, Amount.ada(4))
                    .from(drSender1Addr);

            AtomicLong actualFee = new AtomicLong();
            Result<String> result = drQuickTxBuilder.compose(tx)
                    .withSigner(SignerProviders.signerFrom(drSender1))
                    .depositMode(DepositMode.CHANGE_OUTPUT)
                    .mergeOutputs(false)
                    .withVerifier(txn -> {
                        // CHANGE_OUTPUT + mergeOutputs=false: 3 outputs
                        // 2 receiver1 outputs (5 ADA, 4 ADA) + sender1 change (deposit deducted from change)
                        assertThat(txn.getBody().getOutputs()).hasSize(3);
                        // Two distinct receiver1 outputs
                        List<TransactionOutput> receiverOuts = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(receiver1)).toList();
                        assertThat(receiverOuts).hasSize(2);
                        assertThat(receiverOuts.stream().map(o -> o.getValue().getCoin()).toList())
                                .containsExactlyInAnyOrder(adaToLovelace(5), adaToLovelace(4));
                        // Sender1 change output with positive balance
                        TransactionOutput sender1Out = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(drSender1Addr)).findFirst().orElseThrow();
                        assertThat(sender1Out.getValue().getCoin()).isGreaterThan(BigInteger.ZERO);

                        assertThat(txn.getBody().getInputs()).hasSize(1);
                        actualFee.set(txn.getBody().getFee().longValue());
                    })
                    .completeAndWait(System.out::println);

            System.out.println("stakeRegistration_depositMode_changeOutput_mergeOff: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), drSender1Addr);

            BigInteger sender1BalanceAfter = getLovelaceBalance(drSender1Addr);
            BigInteger spent = sender1BalanceBefore.subtract(sender1BalanceAfter);

            // spent = 9 ADA (5+4) + keyDeposit + fee
            BigInteger feeOnly = spent.subtract(adaToLovelace(9)).subtract(keyDeposit);
            assertThat(feeOnly.longValue()).isEqualTo(actualFee.longValue());
        }

        // ---- Test: DepositMode.ANY_OUTPUT + mergeOutputs=false ----

        @Test
        @Order(10)
        void stakeRegistration_depositMode_anyOutput_mergeOff() throws Exception {
            // Deregister sender1 first
            tryDeregisterStakeKey(drSender1Addr, drSender1);

            BigInteger sender1BalanceBefore = getLovelaceBalance(drSender1Addr);
            BigInteger keyDeposit = new BigInteger(drProtocolParamsSupplier.getProtocolParams().getKeyDeposit());

            Tx tx = new Tx()
                    .registerStakeAddress(drSender1Addr)
                    .payToAddress(receiver1, Amount.ada(5))
                    .payToAddress(receiver1, Amount.ada(4))
                    .payToAddress(drSender1Addr, Amount.ada(8)) // Extra output to ensure deposit can be taken from any output
                    .from(drSender1Addr);

            AtomicLong actualFee = new AtomicLong();
            Result<String> result = drQuickTxBuilder.compose(tx)
                    .withSigner(SignerProviders.signerFrom(drSender1))
                    .depositMode(DepositMode.ANY_OUTPUT)
                    .mergeOutputs(false)
                    .withVerifier(txn -> {
                        // ANY_OUTPUT + mergeOutputs=false: 4 outputs
                        // 2 receiver1 outputs (5 ADA, 4 ADA) + sender1 output + sender1 change
                        assertThat(txn.getBody().getOutputs()).hasSize(4);
                        // Two distinct receiver1 outputs
                        List<TransactionOutput> receiverOuts = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(receiver1)).toList();
                        assertThat(receiverOuts).hasSize(2);
                        assertThat(receiverOuts.stream().map(o -> o.getValue().getCoin()).toList())
                                .containsExactlyInAnyOrder(adaToLovelace(5), adaToLovelace(4));
                        // Sender1 outputs (pay + change)
                        var sender1Outs = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(drSender1Addr)).toList();
                        assertThat(sender1Outs).hasSize(2);
                        assertThat(sender1Outs.get(0).getValue().getCoin()).isEqualTo(Amount.ada(8).getQuantity().subtract(keyDeposit));
                        actualFee.set(txn.getBody().getFee().longValue());
                    })
                    .completeAndWait(System.out::println);

            System.out.println("stakeRegistration_depositMode_anyOutput_mergeOff: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), drSender1Addr);

            BigInteger sender1BalanceAfter = getLovelaceBalance(drSender1Addr);
            BigInteger spent = sender1BalanceBefore.subtract(sender1BalanceAfter);

            // spent = 9 ADA (5+4) + keyDeposit + fee
            BigInteger feeOnly = spent.subtract(adaToLovelace(9)).subtract(keyDeposit);
            assertThat(feeOnly.longValue()).isEqualTo(actualFee.longValue());
        }

        // ---- Test: DepositMode.CHANGE_OUTPUT + mergeOutputs=true ----

        @Test
        @Order(11)
        void stakeRegistration_depositMode_changeOutput_mergeOn() throws Exception {
            // Deregister sender1 first
            tryDeregisterStakeKey(drSender1Addr, drSender1);

            BigInteger sender1BalanceBefore = getLovelaceBalance(drSender1Addr);
            BigInteger keyDeposit = new BigInteger(drProtocolParamsSupplier.getProtocolParams().getKeyDeposit());

            Tx tx = new Tx()
                    .registerStakeAddress(drSender1Addr)
                    .payToAddress(receiver1, Amount.ada(5))
                    .from(drSender1Addr);

            // CHANGE_OUTPUT + mergeOutputs=true succeeds here because no payment goes to
            // drSender1Addr (the change address). The merge finds no existing output at the
            // change address, so the orElse fallback creates a ChangeOutput instance, which
            // phase 4's findChangeOutput() detects and deducts the deposit from.
            AtomicLong actualFee = new AtomicLong();
            Result<String> result = drQuickTxBuilder.compose(tx)
                    .withSigner(SignerProviders.signerFrom(drSender1))
                    .depositMode(DepositMode.CHANGE_OUTPUT)
                    .mergeOutputs(true)
                    .withVerifier(txn -> {
                        // CHANGE_OUTPUT + mergeOutputs=true: 2 outputs
                        // 1 receiver1 output (5 ADA) + sender1 change (deposit deducted from change)
                        assertThat(txn.getBody().getOutputs()).hasSize(2);
                        // Receiver1 output with 5 ADA
                        TransactionOutput receiverOut = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(receiver1)).findFirst().orElseThrow();
                        assertThat(receiverOut.getValue().getCoin()).isEqualTo(adaToLovelace(5));
                        // Sender1 change output with positive balance
                        TransactionOutput sender1Out = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(drSender1Addr)).findFirst().orElseThrow();
                        assertThat(sender1Out.getValue().getCoin()).isGreaterThan(BigInteger.ZERO);
                        actualFee.set(txn.getBody().getFee().longValue());
                    })
                    .completeAndWait(System.out::println);

            System.out.println("stakeRegistration_depositMode_changeOutput_mergeOn: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), drSender1Addr);

            BigInteger sender1BalanceAfter = getLovelaceBalance(drSender1Addr);
            BigInteger spent = sender1BalanceBefore.subtract(sender1BalanceAfter);

            // spent = 5 ADA + keyDeposit + fee
            BigInteger feeOnly = spent.subtract(adaToLovelace(5)).subtract(keyDeposit);
            assertThat(feeOnly.longValue()).isEqualTo(actualFee.longValue());
        }

        // ---- Test: DepositMode.ANY_OUTPUT + mergeOutputs=true ----

        @Test
        @Order(12)
        void stakeRegistration_depositMode_anyOutput_mergeOn() throws Exception {
            // Deregister sender1 first (may have been re-registered if Test 10 somehow succeeded)
            tryDeregisterStakeKey(drSender1Addr, drSender1);

            BigInteger sender1BalanceBefore = getLovelaceBalance(drSender1Addr);
            BigInteger keyDeposit = new BigInteger(drProtocolParamsSupplier.getProtocolParams().getKeyDeposit());

            Tx tx = new Tx()
                    .registerStakeAddress(drSender1Addr)
                    .payToAddress(receiver1, Amount.ada(5))
                    .payToAddress(receiver1, Amount.ada(4))
                    .from(drSender1Addr);

            Result<String> result = drQuickTxBuilder.compose(tx)
                    .withSigner(SignerProviders.signerFrom(drSender1))
                    .depositMode(DepositMode.ANY_OUTPUT)
                    .mergeOutputs(true)
                    .withVerifier(txn -> {
                        // ANY_OUTPUT + mergeOutputs=true: 2 outputs
                        // 1 merged receiver1 output (9 ADA) + sender1 change
                        assertThat(txn.getBody().getOutputs()).hasSize(2);
                        // Merged receiver1 output with 9 ADA
                        TransactionOutput receiverOut = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(receiver1)).findFirst().orElseThrow();
                        assertThat(receiverOut.getValue().getCoin()).isEqualTo(adaToLovelace(9));
                        // Sender1 change output with positive balance
                        TransactionOutput sender1Out = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(drSender1Addr)).findFirst().orElseThrow();
                        assertThat(sender1Out.getValue().getCoin()).isGreaterThan(BigInteger.ZERO);
                    })
                    .completeAndWait(System.out::println);

            System.out.println("stakeRegistration_depositMode_anyOutput_mergeOn: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), drSender1Addr);

            BigInteger sender1BalanceAfter = getLovelaceBalance(drSender1Addr);
            BigInteger spent = sender1BalanceBefore.subtract(sender1BalanceAfter);

            // spent = 9 ADA (5+4) + keyDeposit + fee
            BigInteger feeOnly = spent.subtract(adaToLovelace(9)).subtract(keyDeposit);
            assertThat(feeOnly).isGreaterThan(BigInteger.ZERO);
            assertThat(feeOnly).isLessThan(keyDeposit);
        }

        // ---- Test: DepositMode.NEW_UTXO_SELECTION + mergeOutputs=true (single UTXO → failure) ----

        @Test
        @Order(13)
        void stakeRegistration_depositMode_newUtxoSelection_mergeOn_singleUtxo_fails() throws Exception {
            // Deregister sender1 first — leaves a single UTXO
            tryDeregisterStakeKey(drSender1Addr, drSender1);
            // Consolidate all UTXOs into one so NEW_UTXO_SELECTION cannot find a separate UTXO
            consolidateUtxos(drSender1Addr, drSender1);

            Tx tx = new Tx()
                    .registerStakeAddress(drSender1Addr)
                    .payToAddress(receiver1, Amount.ada(5))
                    .payToAddress(receiver1, Amount.ada(4))
                    .from(drSender1Addr);

            // With a single UTXO, NEW_UTXO_SELECTION cannot find a separate UTXO for the deposit
            assertThrows(InsufficientBalanceException.class, () ->
                    drQuickTxBuilder.compose(tx)
                            .withSigner(SignerProviders.signerFrom(drSender1))
                            .depositMode(DepositMode.NEW_UTXO_SELECTION)
                            .mergeOutputs(true)
                            .completeAndWait()
            );
        }

        // ---- Test: DepositMode.NEW_UTXO_SELECTION + mergeOutputs=true (multiple UTXOs → success) ----

        @Test
        @Order(14)
        void stakeRegistration_depositMode_newUtxoSelection_mergeOn_multipleUtxos() throws Exception {
            // Deregister sender1 first
            tryDeregisterStakeKey(drSender1Addr, drSender1);

            // Split into multiple UTXOs so NEW_UTXO_SELECTION can find one for deposit
            splitUtxos(drSender1Addr, drSender1);

            BigInteger sender1BalanceBefore = getLovelaceBalance(drSender1Addr);
            BigInteger keyDeposit = new BigInteger(drProtocolParamsSupplier.getProtocolParams().getKeyDeposit());

            Tx tx = new Tx()
                    .registerStakeAddress(drSender1Addr)
                    .payToAddress(receiver1, Amount.ada(5))
                    .payToAddress(receiver1, Amount.ada(4))
                    .from(drSender1Addr);

            AtomicLong actualFee = new AtomicLong();
            Result<String> result = drQuickTxBuilder.compose(tx)
                    .withSigner(SignerProviders.signerFrom(drSender1))
                    .depositMode(DepositMode.NEW_UTXO_SELECTION)
                    .mergeOutputs(true)
                    .withVerifier(txn -> {
                        // NEW_UTXO_SELECTION + mergeOutputs=true: 2 outputs
                        // 1 merged receiver1 output (9 ADA) + sender1 change
                        assertThat(txn.getBody().getOutputs()).hasSize(2);
                        // Merged receiver1 output with 9 ADA
                        TransactionOutput receiverOut = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(receiver1)).findFirst().orElseThrow();
                        assertThat(receiverOut.getValue().getCoin()).isEqualTo(adaToLovelace(9));
                        // Sender1 change output with positive balance
                        TransactionOutput sender1Out = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(drSender1Addr)).findFirst().orElseThrow();
                        assertThat(sender1Out.getValue().getCoin()).isGreaterThan(BigInteger.ZERO);

                        actualFee.set(txn.getBody().getFee().longValue());
                    })
                    .completeAndWait(System.out::println);

            System.out.println("stakeRegistration_depositMode_newUtxoSelection_mergeOn_multipleUtxos: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), drSender1Addr);

            BigInteger sender1BalanceAfter = getLovelaceBalance(drSender1Addr);
            BigInteger spent = sender1BalanceBefore.subtract(sender1BalanceAfter);

            // spent = 9 ADA (5+4) + keyDeposit + fee
            BigInteger feeOnly = spent.subtract(adaToLovelace(9)).subtract(keyDeposit);
            assertThat(feeOnly).isGreaterThan(BigInteger.ZERO);
            assertThat(feeOnly.longValue()).isEqualTo(actualFee.longValue());
        }

        // ---- Test: Two Txs, two stake registrations, two different from addresses, depositMode AUTO ----

        @Test
        @Order(15)
        void compose_twoTxs_twoStakeRegistrations_differentFromAddresses_autoDeposit() throws Exception {
            // Deregister both senders first
            tryDeregisterStakeKey(drSender1Addr, drSender1);
            tryDeregisterStakeKey(drSender2Addr, drSender2);

            BigInteger sender1BalanceBefore = getLovelaceBalance(drSender1Addr);
            BigInteger sender2BalanceBefore = getLovelaceBalance(drSender2Addr);
            BigInteger treasuryBalanceBefore = getLovelaceBalance(drTreasuryAddr);
            BigInteger keyDeposit = new BigInteger(drProtocolParamsSupplier.getProtocolParams().getKeyDeposit());

            // Tx1: register sender1 stake address + payment from sender1
            Tx tx1 = new Tx()
                    .registerStakeAddress(drSender1Addr)
                    .payToAddress(receiver1, Amount.ada(3))
                    .from(drSender1Addr);

            // Tx2: register sender2 stake address + payment from sender2
            Tx tx2 = new Tx()
                    .registerStakeAddress(drSender2Addr)
                    .payToAddress(receiver1, Amount.ada(2))
                    .from(drSender2Addr);

            AtomicLong actualFee = new AtomicLong();
            Result<String> result = drQuickTxBuilder.compose(tx1, tx2)
                    .feePayer(drTreasuryAddr)
                    .withSigner(SignerProviders.signerFrom(drSender1))
                    .withSigner(SignerProviders.signerFrom(drSender2))
                    .withSigner(SignerProviders.signerFrom(drTreasury))
                    .withVerifier(txn -> {
                        // Verify sender1 change output exists (deposit deducted from it)
                        TransactionOutput sender1Out = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(drSender1Addr)).findFirst().orElseThrow();
                        assertThat(sender1Out.getValue().getCoin()).isGreaterThan(BigInteger.ZERO);
                        // Verify sender2 change output exists (deposit deducted from it)
                        TransactionOutput sender2Out = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(drSender2Addr)).findFirst().orElseThrow();
                        assertThat(sender2Out.getValue().getCoin()).isGreaterThan(BigInteger.ZERO);
                        // Verify receiver1 output
                        var receiverLovelaceOut = txn.getBody().getOutputs().stream()
                                .filter(o -> o.getAddress().equals(receiver1)).map(transactionOutput -> transactionOutput.getValue().getCoin())
                                        .reduce(BigInteger.ZERO, BigInteger::add);
                        assertThat(receiverLovelaceOut).isEqualTo(adaToLovelace(5)); // 3 + 2 merged
                        actualFee.set(txn.getBody().getFee().longValue());
                    })
                    .completeAndWait(System.out::println);

            System.out.println("compose_twoTxs_twoStakeRegistrations_differentFromAddresses_autoDeposit: " + result);
            assertTrue(result.isSuccessful(), "Tx failed: " + result);
            checkIfUtxoAvailable(result.getValue(), drSender1Addr);

            BigInteger sender1BalanceAfter = getLovelaceBalance(drSender1Addr);
            BigInteger sender2BalanceAfter = getLovelaceBalance(drSender2Addr);
            BigInteger treasuryBalanceAfter = getLovelaceBalance(drTreasuryAddr);

            // sender1 spent = 3 ADA payment + keyDeposit
            BigInteger sender1Spent = sender1BalanceBefore.subtract(sender1BalanceAfter);
            assertThat(sender1Spent).isEqualTo(adaToLovelace(3).add(keyDeposit));

            // sender2 spent = 2 ADA payment + keyDeposit
            BigInteger sender2Spent = sender2BalanceBefore.subtract(sender2BalanceAfter);
            assertThat(sender2Spent).isEqualTo(adaToLovelace(2).add(keyDeposit));

            // treasury spent = fee only (no deposit)
            BigInteger treasurySpent = treasuryBalanceBefore.subtract(treasuryBalanceAfter);
            assertThat(treasurySpent).isEqualTo(BigInteger.valueOf(actualFee.longValue()));
        }
    }
}
