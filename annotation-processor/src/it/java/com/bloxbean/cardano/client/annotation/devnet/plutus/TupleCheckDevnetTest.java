package com.bloxbean.cardano.client.annotation.devnet.plutus;

import com.bloxbean.cardano.client.annotation.devnet.plutus.tuplecheck.TupleCheckSpendValidator;
import com.bloxbean.cardano.client.annotation.devnet.plutus.tuplecheck.model.Holder;
import com.bloxbean.cardano.client.annotation.devnet.plutus.tuplecheck.model.impl.HolderData;
import com.bloxbean.cardano.client.annotation.devnet.plutus.tuplecheck.model.impl.RedeemerData;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.it.BaseIT;
import com.bloxbean.cardano.client.plutus.aiken.blueprint.std.VerificationKeyHash;
import com.bloxbean.cardano.client.plutus.blueprint.type.Pair;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.ChangeReceiver;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.PubKeyReceiver;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import scalus.bloxbean.MapScriptSupplier;
import scalus.bloxbean.ScalusTransactionEvaluator;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Unique trait:</b> only test with an n-tuple datum field —
 * {@code (Int, ByteArray)} emitted as CIP-57 {@code Tuple<<Int,ByteArray>>}
 * (doubled-bracket key, {@code dataType: "list"} with positional
 * {@code items}); codegen maps it to
 * {@link com.bloxbean.cardano.client.plutus.blueprint.type.Pair Pair&lt;BigInteger, byte[]&gt;}.
 * Exercises the syntactic edge case the CIP-57 generics amendment singles
 * out.
 *
 * <b>Asserts on Cardano:</b> the validator's destructure
 * {@code let (n, label) = h.entry} matches both halves against the redeemer
 * and unlocks, proving tuples are encoded as {@code listData} (not
 * {@code constrData}) and that positional ordering is preserved. A
 * transposed tuple or wrong-data-type encoding would fail the destructure
 * on chain.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Tuple<<Int, ByteArray>> datum field — pins the doubled-bracket tuple encoding end-to-end")
public class TupleCheckDevnetTest extends BaseIT {

    private TupleCheckSpendValidator validator;
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
                Map.of(TupleCheckSpendValidator.HASH, validator().getPlutusScript()));

        validator = new TupleCheckSpendValidator(Networks.testnet())
                .withBackendService(backendService)
                .withTransactionEvaluator(
                        new ScalusTransactionEvaluator(protocolParams, utxoSupplier, scriptSupplier));
    }

    private TupleCheckSpendValidator validator() {
        return new TupleCheckSpendValidator(Networks.testnet());
    }

    @Test
    @DisplayName("Datum tuple (42, 'gold') unlocks when redeemer matches both halves on chain")
    void tupleDatumDestructuresOnChain() {
        var deployResult = validator
                .deploy(address1)
                .feePayer(address1)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);
        assertTrue(deployResult.isSuccessful(), "Deploy should succeed");
        validator.withReferenceTxInput(deployResult.getValue(), 0);

        byte[] label = "gold".getBytes(StandardCharsets.UTF_8);
        Pair<BigInteger, byte[]> entry = new Pair<>(BigInteger.valueOf(42), label);

        Holder holder = new HolderData();
        holder.setOwner(VerificationKeyHash.of(account1.getBaseAddress().getPaymentCredentialHash().get()));
        holder.setEntry(entry);

        var lockResult = validator
                .lock(address1, Amount.ada(20), holder)
                .feePayer(address1)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);
        assertTrue(lockResult.isSuccessful(), "Lock should succeed");

        var redeemer = new RedeemerData();
        redeemer.setExpectedN(BigInteger.valueOf(42));
        redeemer.setExpectedLabel(label);

        var receiver = new PubKeyReceiver(address1, Amount.ada(20));
        var unlockResult = validator
                .unlock(holder, redeemer, List.of(receiver), new ChangeReceiver(address1))
                .feePayer(address1)
                .withRequiredSigners(account1.getBaseAddress())
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);
        assertTrue(unlockResult.isSuccessful(),
                "Unlock should succeed — validator must see (n=42, label='gold') after CBOR round-trip");
    }
}
