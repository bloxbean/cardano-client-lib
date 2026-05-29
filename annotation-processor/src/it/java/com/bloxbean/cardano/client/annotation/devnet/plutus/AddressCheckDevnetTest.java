package com.bloxbean.cardano.client.annotation.devnet.plutus;

import com.bloxbean.cardano.client.annotation.devnet.plutus.addresscheck.AddressCheckSpendValidator;
import com.bloxbean.cardano.client.annotation.devnet.plutus.addresscheck.model.Vault;
import com.bloxbean.cardano.client.annotation.devnet.plutus.addresscheck.model.impl.RedeemerData;
import com.bloxbean.cardano.client.annotation.devnet.plutus.addresscheck.model.impl.VaultData;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.it.BaseIT;
import com.bloxbean.cardano.client.plutus.aiken.blueprint.std.Address;
import com.bloxbean.cardano.client.plutus.aiken.blueprint.std.PaymentCredential;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.ChangeReceiver;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.PubKeyReceiver;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import scalus.bloxbean.MapScriptSupplier;
import scalus.bloxbean.ScalusTransactionEvaluator;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Unique trait:</b> the most composite shared stdlib v3 type —
 * {@code cardano/address/Address}, containing {@code PaymentCredential}
 * (2-variant sum) and {@code Optional<StakeCredential>} — as a datum field.
 *
 * <b>Asserts on Cardano:</b> successful unlock under {@code account1}'s
 * signature proves the validator decoded the full Address CBOR shape
 * correctly — the {@code PaymentCredential.VerificationKey} constructor tag
 * (0), the inner VKH bytes, and the wrapping {@code None} stake credential
 * (alt 1 with no fields) all match the Java-side encoding.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Lock/unlock with full Address datum (registry-resolved Address + PaymentCredential + Optional StakeCredential)")
public class AddressCheckDevnetTest extends BaseIT {

    private AddressCheckSpendValidator validator;
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
                Map.of(AddressCheckSpendValidator.HASH, validator().getPlutusScript()));

        validator = new AddressCheckSpendValidator(Networks.testnet())
                .withBackendService(backendService)
                .withTransactionEvaluator(
                        new ScalusTransactionEvaluator(protocolParams, utxoSupplier, scriptSupplier));
    }

    private AddressCheckSpendValidator validator() {
        return new AddressCheckSpendValidator(Networks.testnet());
    }

    @Test
    @DisplayName("Address datum (VerificationKey credential + None stake) round-trips through the ledger end-to-end")
    void addressDatumRoundTripsOnChain() {
        var deployResult = validator
                .deploy(address1)
                .feePayer(address1)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);

        System.out.println("Deploy result: " + deployResult);
        assertTrue(deployResult.isSuccessful(), "Deploy should succeed");

        validator.withReferenceTxInput(deployResult.getValue(), 0);

        // Construct the admin Address from account1's payment credential hash.
        // Stake credential left empty (None) — the validator only checks the payment side.
        byte[] pkh = account1.getBaseAddress().getPaymentCredentialHash().get();
        Address admin = new Address(PaymentCredential.verificationKey(pkh), Optional.empty());

        Vault vault = new VaultData();
        vault.setAdmin(admin);

        var lockResult = validator
                .lock(address1, Amount.ada(20), vault)
                .feePayer(address1)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);

        System.out.println("Lock result: " + lockResult);
        assertTrue(lockResult.isSuccessful(), "Lock should succeed");

        var receiver = new PubKeyReceiver(address1, Amount.ada(20));
        var redeemer = new RedeemerData();
        redeemer.setMarker("anything".getBytes());

        var unlockResult = validator
                .unlock(vault, redeemer, List.of(receiver), new ChangeReceiver(address1))
                .feePayer(address1)
                .withRequiredSigners(account1.getBaseAddress())
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);

        System.out.println("Unlock result: " + unlockResult.getValue());
        assertTrue(unlockResult.isSuccessful(), "Unlock should succeed");
    }
}
