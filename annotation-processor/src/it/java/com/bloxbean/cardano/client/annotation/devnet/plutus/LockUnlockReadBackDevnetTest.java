package com.bloxbean.cardano.client.annotation.devnet.plutus;

import com.bloxbean.cardano.client.annotation.devnet.plutus.lock.LockSpendValidator;
import com.bloxbean.cardano.client.annotation.devnet.plutus.lock.model.Owner;
import com.bloxbean.cardano.client.annotation.devnet.plutus.lock.model.impl.OwnerData;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.ScriptUtxoFinders;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.it.BaseIT;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import scalus.bloxbean.MapScriptSupplier;
import scalus.bloxbean.ScalusTransactionEvaluator;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Unique trait:</b> only test exercising the decoder direction — fetches
 * the locked UTxO from the chain, deserialises its inline-datum bytes via
 * the generated converter, and asserts equality with the original Java
 * value.
 *
 * <b>Asserts on Cardano:</b> {@code OwnerData.deserialize(chainCbor)}
 * reconstructs an {@code Owner} byte-for-byte equal to the one originally
 * locked — encoder/decoder drift (a field reorder applied to only one
 * direction) is invisible to every other encoder-only test because they
 * round-trip through an encoder that may be lying to itself.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Datum encoder/decoder round-trip — locks a datum, fetches it from chain, decodes back to Java")
public class LockUnlockReadBackDevnetTest extends BaseIT {

    private LockSpendValidator validator;
    private BackendService backendService;
    private DefaultUtxoSupplier utxoSupplier;

    @SneakyThrows
    @BeforeAll
    void setup() {
        initializeAccounts();
        backendService = getBackendService();
        topupAllTestAccounts();

        var protocolParams = backendService.getEpochService().getProtocolParameters().getValue();
        utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());

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

    @SneakyThrows
    @Test
    @DisplayName("Datum locked on chain decodes back to the original Java value byte-for-byte")
    void datumDecoderRoundTripsAgainstChainBytes() {
        var deployResult = validator
                .deploy(address1)
                .feePayer(address1)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);
        assertTrue(deployResult.isSuccessful(), "Deploy should succeed");
        validator.withReferenceTxInput(deployResult.getValue(), 0);

        Owner original = new OwnerData();
        original.setOwner(account1.getBaseAddress().getPaymentCredentialHash().get());

        var lockResult = validator
                .lock(address1, Amount.ada(20), original)
                .feePayer(address1)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);
        assertTrue(lockResult.isSuccessful(), "Lock should succeed");

        // Fetch the script UTxO holding our datum back from the chain
        var scriptUtxo = ScriptUtxoFinders
                .findFirstByInlineDatum(utxoSupplier, validator.getScriptAddress(), original.toPlutusData())
                .orElseThrow(() -> new IllegalStateException("Script UTxO not found post-lock"));

        String datumCborHex = scriptUtxo.getInlineDatum();
        assertNotNull(datumCborHex, "Locked UTxO must carry an inline datum");

        // Decode the chain bytes back through the generated decoder
        Owner decoded = OwnerData.deserialize(datumCborHex);

        assertArrayEquals(original.getOwner(), decoded.getOwner(),
                "Decoded Owner.owner must equal the originally-locked value byte-for-byte");
    }
}
