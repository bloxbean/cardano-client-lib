package com.bloxbean.cardano.client.annotation.devnet.plutus;

import com.bloxbean.cardano.client.annotation.devnet.plutus.helloworld.HelloWorldSpendValidator;
import com.bloxbean.cardano.client.annotation.devnet.plutus.helloworld.model.Owner;
import com.bloxbean.cardano.client.annotation.devnet.plutus.helloworld.model.impl.OwnerData;
import com.bloxbean.cardano.client.annotation.devnet.plutus.helloworld.model.impl.RedeemerData;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.it.BaseIT;
import com.bloxbean.cardano.client.plutus.aiken.blueprint.std.VerificationKeyHash;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.ChangeReceiver;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.PubKeyReceiver;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import scalus.bloxbean.MapScriptSupplier;
import scalus.bloxbean.ScalusTransactionEvaluator;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * On-chain validation of the stdlib v3 shared-type pipeline. Exercises a
 * contract whose datum embeds an {@code aiken/crypto/VerificationKeyHash}
 * (resolved by {@code AikenBlueprintTypeRegistry} to the prebuilt
 * {@link VerificationKeyHash}); a successful lock + unlock proves that
 * shared-type's serialization round-trips through the ledger.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HelloWorldDevnetTest extends BaseIT {

    private HelloWorldSpendValidator validator;
    private BackendService backendService;

    @SneakyThrows
    @BeforeAll
    void setup() {
        initializeAccounts();
        backendService = getBackendService();
        topupAllTestAccounts();

        var protocolParams = backendService.getEpochService().getProtocolParameters().getValue();
        var utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());

        var scriptSupplier = new MapScriptSupplier(
                Map.of(HelloWorldSpendValidator.HASH, validator().getPlutusScript()));

        validator = new HelloWorldSpendValidator(Networks.testnet())
                .withBackendService(backendService)
                .withTransactionEvaluator(
                        new ScalusTransactionEvaluator(protocolParams, utxoSupplier, scriptSupplier));
    }

    private HelloWorldSpendValidator validator() {
        return new HelloWorldSpendValidator(Networks.testnet());
    }

    @Test
    void deployAndLockAndUnlock() {
        var deployResult = validator
                .deploy(address1)
                .feePayer(address1)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);

        System.out.println("Deploy result: " + deployResult);
        assertTrue(deployResult.isSuccessful(), "Deploy should succeed");

        validator.withReferenceTxInput(deployResult.getValue(), 0);

        // The owner field is a shared aiken/crypto/VerificationKeyHash — registry-resolved.
        Owner owner = new OwnerData();
        owner.setOwner(VerificationKeyHash.of(account1.getBaseAddress().getPaymentCredentialHash().get()));

        var lockResult = validator
                .lock(address1, Amount.ada(20), owner)
                .feePayer(address1)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);

        System.out.println("Lock result: " + lockResult);
        assertTrue(lockResult.isSuccessful(), "Lock should succeed");

        var receiver = new PubKeyReceiver(address1, Amount.ada(20));
        var redeemer = new RedeemerData();
        redeemer.setMsg("Hello, World!".getBytes(StandardCharsets.UTF_8));

        var unlockResult = validator
                .unlock(owner, redeemer, List.of(receiver), new ChangeReceiver(address1))
                .feePayer(address1)
                .withRequiredSigners(account1.getBaseAddress())
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);

        System.out.println("Unlock result: " + unlockResult.getValue());
        assertTrue(unlockResult.isSuccessful(), "Unlock should succeed");
    }
}
