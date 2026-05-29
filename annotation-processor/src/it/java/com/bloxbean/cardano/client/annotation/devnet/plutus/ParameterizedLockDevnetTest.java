package com.bloxbean.cardano.client.annotation.devnet.plutus;

import com.bloxbean.cardano.client.annotation.devnet.plutus.parameterizedlock.ParameterizedLockSpendValidator;
import com.bloxbean.cardano.client.annotation.devnet.plutus.parameterizedlock.model.impl.RedeemerData;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.it.BaseIT;
import com.bloxbean.cardano.client.plutus.blueprint.model.Data;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.ChangeReceiver;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.PubKeyReceiver;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import scalus.bloxbean.MapScriptSupplier;
import scalus.bloxbean.ScalusScriptUtils;
import scalus.bloxbean.ScalusTransactionEvaluator;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Unique trait:</b> only test exercising compile-time validator
 * parameters — applies a {@code VerificationKeyHash} authority param via
 * Scalus's {@code applyParamsToScript} to produce distinct script hashes
 * per param value.
 *
 * <b>Asserts on Cardano:</b> (1) applied scripts for {@code account1} vs
 * {@code account2} authorities have distinct script hashes, proving the
 * UPLC applicator and the script-hash-per-param invariant the processor's
 * parameter codegen relies on actually hold against a real network;
 * (2) the instance bound to {@code account1} unlocks under {@code account1}'s
 * signature, proving the {@code applyParamCompiledCode} constructor of the
 * generated validator wires up end-to-end.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Parameterised validator — distinct script hashes per applied param value, then lock/unlock with the bound authority")
public class ParameterizedLockDevnetTest extends BaseIT {

    private BackendService backendService;
    private ParameterizedLockSpendValidator validatorForAccount1;

    @SneakyThrows
    @BeforeAll
    void setup() {
        initializeAccounts();
        backendService = getBackendService();
        topupAllTestAccounts();

        var protocolParams = backendService.getEpochService().getProtocolParameters().getValue();
        var utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());

        // Apply 'authority = account1.paymentKeyHash' to the base compiled code.
        String appliedCode1 = ScalusScriptUtils.applyParamsToScript(
                ParameterizedLockSpendValidator.COMPILED_CODE,
                BytesPlutusData.of(account1.getBaseAddress().getPaymentCredentialHash().get()));

        // Apply 'authority = account2.paymentKeyHash' to the same base — should give a different hash.
        String appliedCode2 = ScalusScriptUtils.applyParamsToScript(
                ParameterizedLockSpendValidator.COMPILED_CODE,
                BytesPlutusData.of(account2.getBaseAddress().getPaymentCredentialHash().get()));

        validatorForAccount1 = new ParameterizedLockSpendValidator(Networks.testnet(), appliedCode1)
                .withBackendService(backendService)
                .withTransactionEvaluator(new ScalusTransactionEvaluator(protocolParams, utxoSupplier,
                        new MapScriptSupplier(Map.of(
                                new ParameterizedLockSpendValidator(Networks.testnet(), appliedCode1).getApplyParamHash(),
                                new ParameterizedLockSpendValidator(Networks.testnet(), appliedCode1).getPlutusScript()))));

        var validatorForAccount2 = new ParameterizedLockSpendValidator(Networks.testnet(), appliedCode2);

        // Pin the script-hash-per-param invariant before going further on chain.
        assertNotEquals(
                validatorForAccount1.getApplyParamHash(),
                validatorForAccount2.getApplyParamHash(),
                "Different applied params must produce different script hashes");
    }

    @SneakyThrows
    @Test
    @DisplayName("Validator with account1's authority param locks/unlocks under account1's signature")
    void boundAuthorityUnlocksOnChain() {
        var deployResult = validatorForAccount1
                .deploy(address1)
                .feePayer(address1)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);
        assertTrue(deployResult.isSuccessful(), "Deploy should succeed");
        validatorForAccount1.withReferenceTxInput(deployResult.getValue(), 0);

        // Datum: any opaque Data value — validator ignores it. Pass an empty constr.
        Data<?> datum = () -> ConstrPlutusData.of(0);

        var lockResult = validatorForAccount1
                .lock(address1, Amount.ada(20), datum)
                .feePayer(address1)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);
        assertTrue(lockResult.isSuccessful(), "Lock should succeed");

        var redeemer = new RedeemerData();
        redeemer.setMsg("open".getBytes(StandardCharsets.UTF_8));

        var receiver = new PubKeyReceiver(address1, Amount.ada(20));
        var unlockResult = validatorForAccount1
                .unlock(datum, redeemer, List.of(receiver), new ChangeReceiver(address1))
                .feePayer(address1)
                .withRequiredSigners(account1.getBaseAddress())
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);
        assertTrue(unlockResult.isSuccessful(), "Unlock should succeed");
    }
}
