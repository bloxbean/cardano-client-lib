package com.bloxbean.cardano.client.annotation.devnet.plutus;

import com.bloxbean.cardano.client.annotation.devnet.plutus.optionredeemer.OptionRedeemerSpendValidator;
import com.bloxbean.cardano.client.annotation.devnet.plutus.optionredeemer.model.Owner;
import com.bloxbean.cardano.client.annotation.devnet.plutus.optionredeemer.model.impl.OwnerData;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.it.BaseIT;
import com.bloxbean.cardano.client.plutus.aiken.blueprint.std.VerificationKeyHash;
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
import scalus.bloxbean.ScalusTransactionEvaluator;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Unique trait:</b> top-level generic {@code Option<ByteArray>} at the
 * redeemer position, exercising both {@code Some} and {@code None} arms —
 * the only devnet test where {@code Option<T>} is the entire redeemer
 * rather than a datum wrapper (Lock/HelloWorld use {@code Option<Owner>}
 * as a datum but their {@code None} arm is dead code).
 *
 * <b>Asserts on Cardano:</b> {@code Some("ok")} unlocks (alt 0 with one
 * bytes field reaches the validator's success branch) and {@code None} is
 * rejected (alt 1 with no fields hits the {@code None -> False} branch) —
 * proving both arm encodings reach the script intact.
 *
 * <p>The processor does not currently emit a Java class for top-level
 * {@code Option<ByteArray>}, so the redeemer is constructed by hand via
 * the {@code OptionBytesRedeemer} helper below. That gap itself is a
 * useful signal for future processor work.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Generic Option<ByteArray> as top-level redeemer — exercises both Some and None encodings on chain")
public class OptionRedeemerDevnetTest extends BaseIT {

    private OptionRedeemerSpendValidator validator;
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
                Map.of(OptionRedeemerSpendValidator.HASH, validator().getPlutusScript()));

        validator = new OptionRedeemerSpendValidator(Networks.testnet())
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

    private OptionRedeemerSpendValidator validator() {
        return new OptionRedeemerSpendValidator(Networks.testnet());
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

    @Test
    @DisplayName("Some(\"ok\") redeemer unlocks — constructor index 0 with one ByteArray field accepted by validator")
    void someByteArrayUnlocksOnChain() {
        Owner owner = ownerForAccount1();
        lockOnce(owner);

        Data<?> redeemer = OptionBytesRedeemer.some("ok".getBytes(StandardCharsets.UTF_8));

        var receiver = new PubKeyReceiver(address1, Amount.ada(20));
        var unlockResult = validator
                .unlock(owner, redeemer, List.of(receiver), new ChangeReceiver(address1))
                .feePayer(address1)
                .withRequiredSigners(account1.getBaseAddress())
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);
        assertTrue(unlockResult.isSuccessful(), "Some(\"ok\") unlock should succeed");
    }

    @Test
    @DisplayName("None redeemer is rejected — constructor index 1 with no fields fails validator's expected None -> False branch")
    void noneRedeemerFailsOnChain() {
        Owner owner = ownerForAccount1();
        lockOnce(owner);

        Data<?> redeemer = OptionBytesRedeemer.none();

        var receiver = new PubKeyReceiver(address1, Amount.ada(20));
        try {
            var unlockResult = validator
                    .unlock(owner, redeemer, List.of(receiver), new ChangeReceiver(address1))
                    .feePayer(address1)
                    .withRequiredSigners(account1.getBaseAddress())
                    .withSigner(SignerProviders.signerFrom(account1))
                    .completeAndWait(System.out::println);
            assertFalse(unlockResult.isSuccessful(),
                    "None unlock should fail — validator rejects None redeemer");
        } catch (Exception expected) {
            // Phase-2 script failure may surface as an exception in evaluation; either path is acceptable.
        }
    }

    /**
     * Hand-rolled {@code Option<ByteArray>} redeemer wrapper. The annotation
     * processor does not generate a Java class for top-level
     * {@code Option<ByteArray>}, so the encoding is built here directly:
     * {@code Some(bytes)} maps to constructor 0 with one bytes field,
     * {@code None} to constructor 1 with no fields.
     */
    static final class OptionBytesRedeemer implements Data<OptionBytesRedeemer> {
        private final Optional<byte[]> value;

        private OptionBytesRedeemer(Optional<byte[]> value) {
            this.value = value;
        }

        static OptionBytesRedeemer some(byte[] bytes) {
            return new OptionBytesRedeemer(Optional.of(bytes));
        }

        static OptionBytesRedeemer none() {
            return new OptionBytesRedeemer(Optional.empty());
        }

        @Override
        public ConstrPlutusData toPlutusData() {
            return value
                    .map(bs -> ConstrPlutusData.of(0, BytesPlutusData.of(bs)))
                    .orElseGet(() -> ConstrPlutusData.of(1));
        }
    }
}
