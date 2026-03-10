package com.bloxbean.cardano.client.annotation.devnet;

import com.bloxbean.cardano.client.annotation.devnet.lock.LockSpendValidator;
import com.bloxbean.cardano.client.annotation.devnet.lock.model.Owner;
import com.bloxbean.cardano.client.annotation.devnet.lock.model.impl.OwnerData;
import com.bloxbean.cardano.client.annotation.devnet.lock.model.impl.RedeemerData;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.it.BaseIT;
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

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LockUnlockDevnetTest extends BaseIT {

    private LockSpendValidator validator;
    private BackendService backendService;

    @SneakyThrows
    @BeforeAll
    void setup() {
        initializeAccounts();
        backendService = getBackendService();
        topupAllTestAccounts();

        var protocolParams = backendService.getEpochService().getProtocolParameters().getValue();
        var utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());

        // Provide the script to Scalus so it can resolve it during reference script evaluation
        var scriptSupplier = new MapScriptSupplier(
                Map.of(LockSpendValidator.HASH, validator().getPlutusScript()));

        validator = new LockSpendValidator(Networks.testnet())
                .withBackendService(backendService)
                .withTransactionEvaluator(
                        new ScalusTransactionEvaluator(protocolParams, utxoSupplier, scriptSupplier));
    }

    private LockSpendValidator validator() {
        return new LockSpendValidator(Networks.testnet());
    }

    @Test
    void deployAndLockAndUnlock() {
        // 1. Deploy the contract to create a reference input
        var deployResult = validator
                .deploy(address1)
                .feePayer(address1)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);

        System.out.println("Deploy result: " + deployResult);
        assertTrue(deployResult.isSuccessful(), "Deploy should succeed");

        validator.withReferenceTxInput(deployResult.getValue(), 0);

        // 2. Lock funds with a datum
        Owner owner = new OwnerData();
        owner.setOwner(account1.getBaseAddress().getPaymentCredentialHash().get());

        var lockResult = validator
                .lock(address1, Amount.ada(20), owner)
                .feePayer(address1)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);

        System.out.println("Lock result: " + lockResult);
        assertTrue(lockResult.isSuccessful(), "Lock should succeed");

        // 3. Unlock funds with a redeemer
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
