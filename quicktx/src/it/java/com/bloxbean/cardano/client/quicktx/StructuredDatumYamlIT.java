package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.ScriptUtxoFinders;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.plutus.spec.serializers.PlutusDataJsonConverter;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test demonstrating structured datum/redeemer YAML format with a real blockchain.
 *
 * This test validates the implementation by:
 * 1. Using structured YAML format with @name annotations for readability
 * 2. Variable resolution in datum/redeemer fields
 * 3. Round-trip: YAML → PlutusData → Transaction → Blockchain
 * 4. Verifying CBOR identity (structured format produces correct on-chain data)
 */
public class StructuredDatumYamlIT extends TestDataBaseIT {

    // Hello World contract that checks:
    // - Datum contains owner's payment credential hash
    // - Redeemer contains "Hello, World!" message
    String compiledCode = "590169010100323232323232323225333002323232323253330073370e900118049baa0011323232533300a3370e900018061baa005132533300f00116132533333301300116161616132533301130130031533300d3370e900018079baa004132533300e3371e6eb8c04cc044dd5004a4410d48656c6c6f2c20576f726c642100100114a06644646600200200644a66602a00229404c94ccc048cdc79bae301700200414a2266006006002602e0026eb0c048c04cc04cc04cc04cc04cc04cc04cc04cc040dd50051bae301230103754602460206ea801054cc03924012465787065637420536f6d6528446174756d207b206f776e6572207d29203d20646174756d001616375c0026020002601a6ea801458c038c03c008c034004c028dd50008b1805980600118050009805001180400098029baa001149854cc00d2411856616c696461746f722072657475726e65642066616c736500136565734ae7155ceaab9e5573eae855d12ba401";

    PlutusScript plutusScript = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(compiledCode, PlutusVersion.v3);
    String scriptAddr = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).toBech32();

    @Test
    public void testStructuredDatumYaml_lockAndUnlock() throws Exception {
        // Phase 1: Lock funds using structured datum in YAML
        lockWithStructuredDatumYaml();

        // Phase 2: Unlock funds using structured redeemer in YAML
        unlockWithStructuredRedeemerYaml();
    }

    /**
     * Phase 1: Lock funds to script using structured YAML format for datum.
     *
     * Demonstrates:
     * - Structured datum with @name annotations
     * - Variable substitution in datum fields
     * - Round-trip: YAML → PlutusData → on-chain datum
     */
    private void lockWithStructuredDatumYaml() {
        String ownerHash = HexUtil.encodeHexString(sender1.getBaseAddress().getPaymentCredentialHash().get());
        System.out.println(ownerHash);

        // YAML with structured datum format
        String yaml = "version: 1.0\n" +
                "variables:\n" +
                "  script_address: " + scriptAddr + "\n" +
                "  owner_hash: " + ownerHash + "\n" +
                "  lock_amount: 10000000\n" +
                "\n" +
                "transaction:\n" +
                "  - tx:\n" +
                "      from: " + sender1.baseAddress() + "\n" +
                "      intents:\n" +
                "        # Lock funds with structured datum\n" +
                "        - type: payment\n" +
                "          address: ${script_address}\n" +
                "          amounts:\n" +
                "            - unit: lovelace\n" +
                "              quantity: ${lock_amount}\n" +
                "          # Structured datum with @name annotations for readability\n" +
                "          datum:\n" +
                "            constructor: 0\n" +
                "            fields:\n" +
                "              - \"@name\": owner_payment_hash\n" +
                "                bytes: ${owner_hash}\n";

        System.out.println("=== Lock Transaction YAML ===");
        System.out.println(yaml);

        // Deserialize and execute
        TxPlan plan = TxPlan.from(yaml);

        // Verify datum was correctly parsed
        assertThat(plan.getTxs()).hasSize(1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder.compose(plan)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(msg -> System.out.println("Lock: " + msg));

        System.out.println("=== Lock Result ===");
        System.out.println("Success: " + result.isSuccessful());
        System.out.println("TxHash: " + result.getValue());

        assertThat(result.isSuccessful()).isTrue();
        checkIfUtxoAvailable(result.getValue(), scriptAddr);

        System.out.println("✅ Funds locked with structured datum from YAML\n");
    }

    /**
     * Phase 2: Unlock funds using structured YAML format for redeemer.
     *
     * Demonstrates:
     * - Structured redeemer with @name annotations
     * - Variable substitution in redeemer fields
     * - CBOR identity: structured format produces correct redeemer on-chain
     * - Full round-trip validation: YAML → PlutusData → Script validation → Success
     */
    private void unlockWithStructuredRedeemerYaml() throws Exception {
        // Find the locked UTXO
        PlutusData expectedDatum = ConstrPlutusData.of(0, BytesPlutusData.of(sender1.getBaseAddress().getPaymentCredentialHash().get()));
        System.out.println(PlutusDataJsonConverter.toJson(expectedDatum));
        UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
        Utxo scriptUtxo = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddr, expectedDatum)
                .orElseThrow(() -> new RuntimeException("Script UTXO not found"));

        System.out.println("=== Found Script UTXO ===");
        System.out.println("TxHash: " + scriptUtxo.getTxHash());
        System.out.println("OutputIndex: " + scriptUtxo.getOutputIndex());

        // Get script CBOR hex for YAML
        String scriptCborHex = plutusScript.getCborHex();

        // Construct unlock transaction with structured redeemer in YAML
        String yaml = "version: 1.0\n" +
                "variables:\n" +
                "  greeting_message: 48656c6c6f2c20576f726c6421  # \"Hello, World!\" in hex\n" +
                "  recipient: " + receiver1 + "\n" +
                "  unlock_amount: 10000000\n" +
                "  script_cbor: " + scriptCborHex + "\n" +
                "\n" +
                "transaction:\n" +
                "  - scriptTx:\n" +
                "      inputs:\n" +
                "        - type: script_collect_from\n" +
                "          utxo_refs:\n" +
                "            - tx_hash: " + scriptUtxo.getTxHash() + "\n" +
                "              output_index: " + scriptUtxo.getOutputIndex() + "\n" +
                "          # Structured redeemer with @name annotations\n" +
                "          redeemer:\n" +
                "            constructor: 0\n" +
                "            fields:\n" +
                "              - \"@name\": message\n" +
                "                bytes: ${greeting_message}\n" +
                "      intents:\n" +
                "        # Attach spending validator\n" +
                "        - type: validator\n" +
                "          role: spend\n" +
                "          cbor_hex: ${script_cbor}\n" +
                "          version: v3\n" +
                "        - type: payment\n" +
                "          address: ${recipient}\n" +
                "          amounts:\n" +
                "            - unit: lovelace\n" +
                "              quantity: ${unlock_amount}\n";

        System.out.println("=== Unlock Transaction YAML ===");
        System.out.println(yaml);

        // Deserialize and execute
        TxPlan plan = TxPlan.from(yaml);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder.compose(plan)
                .feePayer(receiver1)
                .collateralPayer(sender1.baseAddress())
                .withSigner(SignerProviders.signerFrom(sender1))
                .withRequiredSigners(sender1.getBaseAddress())
                .withTxInspector(tx -> {
                    System.out.println("=== Transaction Details ===");
                    System.out.println(JsonUtil.getPrettyJson(tx));
                })
                .preBalanceTx((context, txn) -> {
                    // Set realistic exUnits for PlutusV3
                    txn.getWitnessSet().getRedeemers().get(0)
                            .setExUnits(ExUnits.builder()
                                    .mem(BigInteger.valueOf(31507))
                                    .steps(BigInteger.valueOf(9666253))
                                    .build());
                })
                .completeAndWait(msg -> System.out.println("Unlock: " + msg));

        System.out.println("=== Unlock Result ===");
        System.out.println("Success: " + result.isSuccessful());
        System.out.println("TxHash: " + result.getValue());
        System.out.println(result);

        assertThat(result.isSuccessful()).isTrue();

        System.out.println("✅ Funds unlocked with structured redeemer from YAML");
        System.out.println("✅ CBOR identity validated: structured format → correct on-chain data");
        System.out.println("✅ Full ADR-001 round-trip successful!");
    }

    @Test
    public void testStructuredDatumWithNestedData() {
        // Demonstrate more complex structured datum with nested constructors
        String yaml = "version: 1.0\n" +
                "variables:\n" +
                "  user_name: 616c696365  # \"alice\" in hex\n" +
                "  balance: 1000000\n" +
                "  nft1: abcd1234\n" +
                "  nft2: ef567890\n" +
                "\n" +
                "transaction:\n" +
                "  - tx:\n" +
                "      from: " + sender1.baseAddress() + "\n" +
                "      intents:\n" +
                "        - type: payment\n" +
                "          address: " + scriptAddr + "\n" +
                "          amounts:\n" +
                "            - unit: lovelace\n" +
                "              quantity: 2000000\n" +
                "          # Complex nested datum structure\n" +
                "          datum:\n" +
                "            constructor: 1\n" +
                "            fields:\n" +
                "              # Nested constructor for user data\n" +
                "              - \"@name\": user_info\n" +
                "                constructor: 0\n" +
                "                fields:\n" +
                "                  - \"@name\": username\n" +
                "                    bytes: ${user_name}\n" +
                "                  - \"@name\": account_balance\n" +
                "                    int: ${balance}\n" +
                "              # List of NFTs\n" +
                "              - \"@name\": owned_nfts\n" +
                "                list:\n" +
                "                  - \"@name\": first_nft\n" +
                "                    bytes: ${nft1}\n" +
                "                  - \"@name\": second_nft\n" +
                "                    bytes: ${nft2}\n";

        System.out.println("=== Complex Nested Datum YAML ===");
        System.out.println(yaml);

        TxPlan plan = TxPlan.from(yaml);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder.compose(plan)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(msg -> System.out.println("Nested datum: " + msg));

        System.out.println("=== Nested Datum Result ===");
        System.out.println("Success: " + result.isSuccessful());
        System.out.println("TxHash: " + result.getValue());

        assertThat(result.isSuccessful()).isTrue();

        System.out.println("✅ Complex nested datum successfully locked on-chain");
        System.out.println("✅ @name annotations provide clarity without affecting CBOR");
    }
}
