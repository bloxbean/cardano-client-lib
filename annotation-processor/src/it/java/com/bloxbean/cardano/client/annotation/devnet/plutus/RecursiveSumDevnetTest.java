package com.bloxbean.cardano.client.annotation.devnet.plutus;

import com.bloxbean.cardano.client.annotation.devnet.plutus.recursivesum.RecursiveSumSpendValidator;
import com.bloxbean.cardano.client.annotation.devnet.plutus.recursivesum.model.IntList;
import com.bloxbean.cardano.client.annotation.devnet.plutus.recursivesum.model.impl.RedeemerData;
import com.bloxbean.cardano.client.annotation.devnet.plutus.recursivesum.model.intlist.impl.ConsData;
import com.bloxbean.cardano.client.annotation.devnet.plutus.recursivesum.model.intlist.impl.NilData;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.it.BaseIT;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Unique trait:</b> only test with a self-referencing datum —
 * {@code Cons.tail: IntList} is a field of the generated Java class's own
 * enclosing interface type; the test builds {@code Cons(1, Cons(2, Cons(3,
 * Nil)))} manually.
 *
 * <b>Asserts on Cardano:</b> unlock with redeemer {@code expected_sum=6}
 * succeeds, proving nested {@code ConstrPlutusData} survives all three
 * levels of {@code Cons} wrapping and the recursive converter terminates
 * correctly at {@code Nil} (alt 0 with no fields). Depth-handling or
 * off-by-one defects in the converter fail silently in encoder-only tests
 * but here surface as an on-chain arithmetic mismatch.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Recursive IntList datum — pins self-referencing ADT codegen and nested-constr CBOR depth on chain")
public class RecursiveSumDevnetTest extends BaseIT {

    private RecursiveSumSpendValidator validator;
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
                Map.of(RecursiveSumSpendValidator.HASH, validator().getPlutusScript()));

        validator = new RecursiveSumSpendValidator(Networks.testnet())
                .withBackendService(backendService)
                .withTransactionEvaluator(
                        new ScalusTransactionEvaluator(protocolParams, utxoSupplier, scriptSupplier));
    }

    private RecursiveSumSpendValidator validator() {
        return new RecursiveSumSpendValidator(Networks.testnet());
    }

    @Test
    @DisplayName("Cons(1, Cons(2, Cons(3, Nil))) datum unlocks when redeemer claims sum=6")
    void recursiveDatumSumsCorrectlyOnChain() {
        var deployResult = validator
                .deploy(address1)
                .feePayer(address1)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);
        assertTrue(deployResult.isSuccessful(), "Deploy should succeed");
        validator.withReferenceTxInput(deployResult.getValue(), 0);

        // Build Cons(1, Cons(2, Cons(3, Nil))) from the inside out.
        IntList list = cons(1, cons(2, cons(3, new NilData())));

        var lockResult = validator
                .lock(address1, Amount.ada(20), (com.bloxbean.cardano.client.plutus.blueprint.model.Data<?>) list)
                .feePayer(address1)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);
        assertTrue(lockResult.isSuccessful(), "Lock should succeed");

        var redeemer = new RedeemerData();
        redeemer.setExpectedSum(BigInteger.valueOf(6));

        var receiver = new PubKeyReceiver(address1, Amount.ada(20));
        var unlockResult = validator
                .unlock(
                        (com.bloxbean.cardano.client.plutus.blueprint.model.Data<?>) list,
                        redeemer, List.of(receiver), new ChangeReceiver(address1))
                .feePayer(address1)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);
        assertTrue(unlockResult.isSuccessful(),
                "Unlock should succeed — on-chain sum of [1, 2, 3] must equal 6");
    }

    private static ConsData cons(int head, IntList tail) {
        ConsData c = new ConsData();
        c.setHead(BigInteger.valueOf(head));
        c.setTail(tail);
        return c;
    }
}
