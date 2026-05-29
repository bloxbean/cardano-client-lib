package com.bloxbean.cardano.client.annotation.devnet.plutus;

import com.bloxbean.cardano.client.annotation.devnet.plutus.mapdatum.MapDatumSpendValidator;
import com.bloxbean.cardano.client.annotation.devnet.plutus.mapdatum.model.Vault;
import com.bloxbean.cardano.client.annotation.devnet.plutus.mapdatum.model.impl.RedeemerData;
import com.bloxbean.cardano.client.annotation.devnet.plutus.mapdatum.model.impl.VaultData;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import scalus.bloxbean.MapScriptSupplier;
import scalus.bloxbean.ScalusTransactionEvaluator;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Unique trait:</b> datum carries a {@code Pairs<ByteArray, Int>} field —
 * the only devnet test exercising CIP-57 {@code dataType: "map"} encoding.
 * Keys deliberately inserted out-of-lexicographic-order to stress whatever
 * sort the encoder applies.
 *
 * <b>Asserts on Cardano:</b> on-chain {@code pairs.get_first(balances,
 * "alpha")} resolves to the locked value (=42) and unlocks, proving the
 * Java encoder's map CBOR (sort order, definite vs indefinite length,
 * {@code mapData} wrapper) matches what the script expects — invariants
 * compile-time tests can not pin down.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Map<ByteArray, Int> datum field — exercises CIP-57 map dataType end-to-end")
public class MapDatumDevnetTest extends BaseIT {

    private MapDatumSpendValidator validator;
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
                Map.of(MapDatumSpendValidator.HASH, validator().getPlutusScript()));

        validator = new MapDatumSpendValidator(Networks.testnet())
                .withBackendService(backendService)
                .withTransactionEvaluator(
                        new ScalusTransactionEvaluator(protocolParams, utxoSupplier, scriptSupplier));
    }

    private MapDatumSpendValidator validator() {
        return new MapDatumSpendValidator(Networks.testnet());
    }

    @Test
    @DisplayName("Map balances datum unlocks when redeemer key resolves to a positive value")
    void mapBalancesDatumLookupRoundTripsOnChain() {
        var deployResult = validator
                .deploy(address1)
                .feePayer(address1)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);
        assertTrue(deployResult.isSuccessful(), "Deploy should succeed");
        validator.withReferenceTxInput(deployResult.getValue(), 0);

        // Deliberately insert keys in non-lexicographic order to exercise whatever sort
        // the encoder applies. A LinkedHashMap preserves insertion order; if the
        // validator only accepts CIP-21 canonical order, the encoder is responsible
        // for sorting before serialisation.
        Map<byte[], BigInteger> balances = new LinkedHashMap<>();
        balances.put("zeta".getBytes(StandardCharsets.UTF_8), BigInteger.valueOf(1));
        balances.put("alpha".getBytes(StandardCharsets.UTF_8), BigInteger.valueOf(42));
        balances.put("mu".getBytes(StandardCharsets.UTF_8), BigInteger.valueOf(7));

        Vault vault = new VaultData();
        vault.setOwner(VerificationKeyHash.of(account1.getBaseAddress().getPaymentCredentialHash().get()));
        vault.setBalances(balances);

        var lockResult = validator
                .lock(address1, Amount.ada(20), vault)
                .feePayer(address1)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);
        assertTrue(lockResult.isSuccessful(), "Lock should succeed");

        var redeemer = new RedeemerData();
        redeemer.setKey("alpha".getBytes(StandardCharsets.UTF_8));

        var receiver = new PubKeyReceiver(address1, Amount.ada(20));
        var unlockResult = validator
                .unlock(vault, redeemer, List.of(receiver), new ChangeReceiver(address1))
                .feePayer(address1)
                .withRequiredSigners(account1.getBaseAddress())
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);
        assertTrue(unlockResult.isSuccessful(), "Unlock should succeed");
    }
}
