package com.bloxbean.cardano.client.annotation.devnet.plutus;

import com.bloxbean.cardano.client.annotation.devnet.plutus.multiaction.MultiActionSpendValidator;
import com.bloxbean.cardano.client.annotation.devnet.plutus.multiaction.model.Action;
import com.bloxbean.cardano.client.annotation.devnet.plutus.multiaction.model.Owner;
import com.bloxbean.cardano.client.annotation.devnet.plutus.multiaction.model.action.impl.CancelData;
import com.bloxbean.cardano.client.annotation.devnet.plutus.multiaction.model.action.impl.GreetData;
import com.bloxbean.cardano.client.annotation.devnet.plutus.multiaction.model.action.impl.WithdrawData;
import com.bloxbean.cardano.client.annotation.devnet.plutus.multiaction.model.impl.OwnerData;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.it.BaseIT;
import com.bloxbean.cardano.client.plutus.aiken.blueprint.std.VerificationKeyHash;
import com.bloxbean.cardano.client.plutus.blueprint.model.Data;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.ChangeReceiver;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.PubKeyReceiver;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import scalus.bloxbean.MapScriptSupplier;
import scalus.bloxbean.ScalusTransactionEvaluator;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Unique trait:</b> three-constructor ADT redeemer
 * {@code Action { Greet { msg } | Withdraw | Cancel { reason } }} with
 * distinct field shapes per variant — the only devnet test exercising
 * constructor tags &gt; 0.
 *
 * <b>Asserts on Cardano:</b> each variant routes to the correct branch of
 * the on-chain {@code when} expression in three separate test methods
 * (Greet → alt 0, Withdraw → alt 1, Cancel → alt 2). A swapped tag or
 * reordered field would compile, produce valid-looking CBOR, and silently
 * route to the wrong branch — only on-chain evaluation rejects it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Multi-constructor ADT redeemer — pins constructor tag 0/1/2 across distinct field shapes")
public class MultiActionDevnetTest extends BaseIT {

    private MultiActionSpendValidator validator;
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
                Map.of(MultiActionSpendValidator.HASH, validator().getPlutusScript()));

        validator = new MultiActionSpendValidator(Networks.testnet())
                .withBackendService(backendService)
                .withTransactionEvaluator(
                        new ScalusTransactionEvaluator(protocolParams, utxoSupplier, scriptSupplier));

        var deployResult = validator
                .deploy(address1)
                .feePayer(address1)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);
        assertTrue(deployResult.isSuccessful(), "Deploy should succeed");
        validator.withReferenceTxInput(deployResult.getValue(), 0);
    }

    private MultiActionSpendValidator validator() {
        return new MultiActionSpendValidator(Networks.testnet());
    }

    private Owner ownerForAccount1() {
        Owner o = new OwnerData();
        o.setOwner(VerificationKeyHash.of(account1.getBaseAddress().getPaymentCredentialHash().get()));
        return o;
    }

    private void lockOnce(Owner owner) {
        var lockResult = validator
                .lock(address1, Amount.ada(20), owner)
                .feePayer(address1)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);
        assertTrue(lockResult.isSuccessful(), "Lock should succeed");
    }

    private void unlockWith(Owner owner, Data<? extends Action> redeemer) {
        var receiver = new PubKeyReceiver(address1, Amount.ada(20));
        var unlockResult = validator
                .unlock(owner, redeemer, List.of(receiver), new ChangeReceiver(address1))
                .feePayer(address1)
                .withRequiredSigners(account1.getBaseAddress())
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);
        assertTrue(unlockResult.isSuccessful(), "Unlock should succeed");
    }

    @Test
    @DisplayName("Greet variant (constructor index 0, one ByteArray field) unlocks when msg matches")
    void greetVariantUnlocksOnChain() {
        Owner owner = ownerForAccount1();
        lockOnce(owner);

        GreetData greet = new GreetData();
        greet.setMsg("hi".getBytes(StandardCharsets.UTF_8));
        unlockWith(owner, greet);
    }

    @Test
    @DisplayName("Withdraw variant (constructor index 1, no fields) unlocks for the signer")
    void withdrawVariantUnlocksOnChain() {
        Owner owner = ownerForAccount1();
        lockOnce(owner);

        WithdrawData withdraw = new WithdrawData();
        unlockWith(owner, withdraw);
    }

    @Test
    @DisplayName("Cancel variant (constructor index 2, one ByteArray field) unlocks unconditionally")
    void cancelVariantUnlocksOnChain() {
        Owner owner = ownerForAccount1();
        lockOnce(owner);

        CancelData cancel = new CancelData();
        cancel.setReason("changed my mind".getBytes(StandardCharsets.UTF_8));
        unlockWith(owner, cancel);
    }
}
