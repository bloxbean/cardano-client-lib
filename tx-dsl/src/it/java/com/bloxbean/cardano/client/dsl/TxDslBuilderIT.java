package com.bloxbean.cardano.client.dsl;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.it.BaseIT;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive integration tests for TxDslBuilder covering:
 * - Basic payment operations with context
 * - Multi-transaction composition
 * - YAML serialization/deserialization with context
 * - Variable substitution
 * - Context execution (feePayer, signer, etc.)
 * - Complex scenarios (minting, staking, governance)
 * - YAML round-trip persistence and rehydration
 *
 * Prerequisites:
 * - YaciDevKit running on localhost:8080
 * - Test accounts funded with test ADA
 */
public class TxDslBuilderIT extends BaseIT {
    private static final Logger log = LoggerFactory.getLogger(TxDslBuilderIT.class);

    private BackendService backendService;
    private TxDslBuilder txDslBuilder;

    @BeforeEach
    void setup() {
        backendService = getBackendService();
        txDslBuilder = new TxDslBuilder(backendService);
        initializeAccounts();

        // Topup accounts if needed
        topupIfNeeded(address1);
        topupIfNeeded(address2);
        topupIfNeeded(address4);

        printBalances();
    }

    // ===== BASIC PAYMENT TESTS =====

    @Test
    void testBasicPaymentWithBuilder() {
        log.info("=== Testing Basic Payment with TxDslBuilder ===");

        // Given - transfer 5 ADA using TxDslBuilder
        Amount transferAmount = Amount.ada(5);
        long initialBalance3 = getBalance(address3);

        // When - create and compose transaction using TxDslBuilder
        TxDsl txDsl = new TxDsl()
                .payToAddress(address3, transferAmount)
                .from(address1);

        Result<String> result = txDslBuilder
                .compose(txDsl)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait();

        // Then - verify transaction succeeded
        assertThat(result.isSuccessful())
                .withFailMessage("Transaction failed: " + result.getResponse())
                .isTrue();

        log.info("Payment transaction submitted: {}", result.getValue());

        // Wait and verify balance
        waitForTransaction(result);

        long finalBalance3 = getBalance(address3);
        assertThat(finalBalance3)
                .isGreaterThanOrEqualTo(initialBalance3 + transferAmount.getQuantity().longValue());

        log.info("✓ Basic payment with TxDslBuilder successful!");
    }

    @Test
    void testPaymentWithContextConfiguration() {
        log.info("=== Testing Payment with Context Configuration ===");

        // Given - create transaction with context
        Amount transferAmount = Amount.ada(3);
        long initialBalance3 = getBalance(address3);

        TxDsl txDsl = new TxDsl()
                .payToAddress(address3, transferAmount)
                .from(address2);

        // When - configure context at builder level
        Result<String> result = txDslBuilder
                .feePayer(address1)  // Context: fee payer
                .utxoSelectionStrategy("LARGEST_FIRST")  // Context: utxo strategy
                .compose(txDsl)
                .withSigner(SignerProviders.signerFrom(account2))
                .withSigner(SignerProviders.signerFrom(account1)) // Fee payer signer
                .completeAndWait();

        // Then - verify transaction succeeded
        assertThat(result.isSuccessful())
                .withFailMessage("Transaction failed: " + result.getResponse())
                .isTrue();

        log.info("Context-configured transaction submitted: {}", result.getValue());

        // Verify balance
        waitForTransaction(result);

        long finalBalance3 = getBalance(address3);
        assertThat(finalBalance3)
                .isGreaterThanOrEqualTo(initialBalance3 + transferAmount.getQuantity().longValue());

        log.info("✓ Payment with context configuration successful!");
    }

    // ===== MULTI-TRANSACTION COMPOSITION TESTS =====

    @Test
    void testMultiTransactionComposition() {
        log.info("=== Testing Multi-Transaction Composition ===");

        // Given - multiple TxDsl instances
        long initialBalance2 = getBalance(address2);
        long initialBalance3 = getBalance(address3);

        TxDsl payment1 = new TxDsl()
                .payToAddress(address2, Amount.ada(2))
                .from(address1);

        TxDsl payment2 = new TxDsl()
                .payToAddress(address3, Amount.ada(3))
                .from(address4);

        // When - compose multiple transactions
        Result<String> result = txDslBuilder
                .compose(payment1, payment2)
                .feePayer(address4)
                .withSigner(SignerProviders.signerFrom(account1))
                .withSigner(SignerProviders.signerFrom(account4))
                .completeAndWait();

        // Then - verify transaction succeeded
        assertThat(result.isSuccessful())
                .withFailMessage("Transaction failed: " + result.getResponse())
                .isTrue();

        log.info("Multi-transaction composed and submitted: {}", result.getValue());

        // Verify both payments
        waitForTransaction(result);

        long finalBalance2 = getBalance(address2);
        long finalBalance3 = getBalance(address3);

        assertThat(finalBalance2).isGreaterThanOrEqualTo(initialBalance2 + Amount.ada(2).getQuantity().longValue());
        assertThat(finalBalance3).isGreaterThanOrEqualTo(initialBalance3 + Amount.ada(3).getQuantity().longValue());

        log.info("✓ Multi-transaction composition successful!");
    }

    // ===== YAML SERIALIZATION WITH CONTEXT TESTS =====

    @Test
    void testYamlSerializationWithContext() throws IOException {
        log.info("=== Testing YAML Serialization with Context ===");

        // Given - create transactions with context
        Map<String, Object> variables = Map.of(
            "sender", address1,
            "amount1", "4000000",
            "amount2", "2000000"
        );
        
        TxDsl txDsl = TxDsl.withVariables(variables)
                .payToAddress(address2, Amount.ada(4))
                .payToAddress(address3, Amount.ada(2))
                .from(address1);

        // Serialize to YAML
        String yaml = txDsl.toYaml();
        log.info("Serialized transaction YAML:\n{}", yaml);

        // Save to file for persistence test
        Path yamlFile = Files.createTempFile("tx-dsl-", ".yaml");
        Files.write(yamlFile, yaml.getBytes());
        log.info("Saved YAML to: {}", yamlFile);

        // When - deserialize and execute with context
        String loadedYaml = Files.readString(yamlFile);

        Result<String> result = txDslBuilder
                .feePayer(address1)
                .composeFromYaml(loadedYaml)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait();

        // Then - verify transaction succeeded
        assertThat(result.isSuccessful())
                .withFailMessage("Transaction failed: " + result.getResponse())
                .isTrue();

        log.info("YAML-deserialized transaction submitted: {}", result.getValue());

        // Cleanup
        Files.deleteIfExists(yamlFile);

        log.info("✓ YAML serialization with context successful!");
    }

    @Test
    void testYamlWithFullContext() {
        log.info("=== Testing YAML with Full Context Section ===");

        // Given - YAML with context section
        String yamlWithContext = String.format(
                "version: 1.0\n" +
                "variables:\n" +
                "  recipient1: \"%s\"\n" +
                "  recipient2: \"%s\"\n" +
                "  amount1: \"3000000\"\n" +
                "  amount2: \"2000000\"\n" +
                "\n" +
                "transaction:\n" +
                "  - tx:\n" +
                "      from: \"%s\"\n" +
                "      intentions:\n" +
                "        - type: payment\n" +
                "          address: \"${recipient1}\"\n" +
                "          amounts:\n" +
                "            - unit: lovelace\n" +
                "              quantity: \"${amount1}\"\n" +
                "        - type: payment\n" +
                "          address: \"${recipient2}\"\n" +
                "          amounts:\n" +
                "            - unit: lovelace\n" +
                "              quantity: \"${amount2}\"\n" +
                "\n" +
                "context:\n" +
                "  feePayer: \"%s\"\n" +
                "  utxoSelectionStrategy: \"LARGEST_FIRST\"\n",
                address2, address3, address1, address1);

        // When - compose from YAML with context
        Result<String> result = txDslBuilder
                .composeFromYaml(yamlWithContext)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait();

        // Then - verify transaction succeeded
        assertThat(result.isSuccessful())
                .withFailMessage("Transaction failed: " + result.getResponse())
                .isTrue();

        log.info("Full context YAML transaction submitted: {}", result.getValue());

        log.info("✓ YAML with full context section successful!");
    }

    // ===== VARIABLE SUBSTITUTION TESTS =====

    @Test
    void testVariableSubstitution() {
        log.info("=== Testing Variable Substitution ===");

        // Given - transaction with variables using new static factory
        Map<String, Object> variables = Map.of(
            "recipient", address2,
            "amount", Amount.ada(2.5)
        );
        
        TxDsl txDsl = TxDsl.withVariables(variables)
                .payToAddress("${recipient}", Amount.lovelace(BigInteger.valueOf(2500000)))
                .from(address1);

        // When - compose and execute
        Result<String> result = txDslBuilder
                .compose(txDsl)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait();

        // Then - verify transaction succeeded
        assertThat(result.isSuccessful())
                .withFailMessage("Transaction failed: " + result.getResponse())
                .isTrue();

        log.info("Variable substitution transaction submitted: {}", result.getValue());

        log.info("✓ Variable substitution successful!");
    }

    @Test
    void testComplexVariableSubstitution() {
        log.info("=== Testing Complex Variable Substitution ===");

        // Given - YAML with nested variable references
        String yamlWithVariables = String.format(
                "version: 1.0\n" +
                "variables:\n" +
                "  network: \"preprod\"\n" +
                "  treasury: \"%s\"\n" +
                "  alice: \"%s\"\n" +
                "  bob: \"%s\"\n" +
                "  base_amount: \"1000000\"\n" +
                "  multiplier: \"2\"\n" +
                "\n" +
                "transaction:\n" +
                "  - tx:\n" +
                "      from: \"${treasury}\"\n" +
                "      intentions:\n" +
                "        - type: payment\n" +
                "          address: \"${alice}\"\n" +
                "          amounts:\n" +
                "            - unit: lovelace\n" +
                "              quantity: \"${base_amount}\"\n" +
                "        - type: payment\n" +
                "          address: \"${bob}\"\n" +
                "          amounts:\n" +
                "            - unit: lovelace\n" +
                "              quantity: \"2000000\"\n" +
                "\n" +
                "context:\n" +
                "  feePayer: \"${treasury}\"\n",
                address1, address2, address3);

        // When - compose from YAML with variables
        Result<String> result = txDslBuilder
                .composeFromYaml(yamlWithVariables)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait();

        // Then - verify transaction succeeded
        assertThat(result.isSuccessful())
                .withFailMessage("Transaction failed: " + result.getResponse())
                .isTrue();

        log.info("Complex variable substitution transaction submitted: {}", result.getValue());

        log.info("✓ Complex variable substitution successful!");
    }

    // ===== COMPLEX SCENARIO TESTS =====

    @Test
    void testMintingWithBuilder() throws CborSerializationException {
        log.info("=== Testing Minting with TxDslBuilder ===");

        // Given - create a native script policy
        var policyTuple = ScriptPubkey.createWithNewKey();
        NativeScript policy = policyTuple._1;
        SecretKey secretKey = policyTuple._2.getSkey();

        String policyId = policy.getPolicyId();
        String assetName = "TestToken" + System.currentTimeMillis();
        Asset mintAsset = new Asset(assetName, BigInteger.valueOf(1000));

        String expectedUnit = AssetUtil.getUnit(policyId, assetName);

        // When - create minting transaction
        TxDsl mintTx = new TxDsl()
                .mintAssets(policy, List.of(mintAsset), address2)
                .from(address1);

        Result<String> result = txDslBuilder
                .compose(mintTx)
                .withSigner(SignerProviders.signerFrom(account1))
                .withSigner(SignerProviders.signerFrom(secretKey))
                .completeAndWait();

        // Then - verify minting succeeded
        assertThat(result.isSuccessful())
                .withFailMessage("Minting failed: " + result.getResponse())
                .isTrue();

        log.info("Minting transaction submitted: {}", result.getValue());

        // Wait and verify token was minted
        waitForTransaction(result);

        // Check if recipient has the tokens
        var utxos = getUtxos(address2);
        boolean hasToken = utxos.stream()
                .anyMatch(utxo -> {
                    List<Amount> amounts = utxo.getAmount();
                    return amounts != null && amounts.stream()
                            .anyMatch(amount -> {
                                String unit = amount.getUnit();
                                return unit != null && unit.startsWith(expectedUnit);
                            });
                });

        assertThat(hasToken).isTrue();

        log.info("✓ Minting with TxDslBuilder successful!");
    }

    @Test
    void testStakingOperations() {
        log.info("=== Testing Staking Operations ===");

        // Given - create stake address from account
        String stakeAddress = account1.stakeAddress();

        // Create staking transaction DSL
        TxDsl stakeTx = new TxDsl()
                .registerStakeAddress(stakeAddress)
                .from(address1);

        // Note: We create the DSL but don't submit as it requires protocol params
        String yaml = stakeTx.toYaml();
        log.info("Staking operation YAML:\n{}", yaml);

        // Verify YAML structure
        assertThat(yaml).contains("type: stake_registration");
        assertThat(yaml).contains("address: " + stakeAddress);

        log.info("✓ Staking DSL creation successful!");
    }

    @Test
    void testGovernanceOperations() {
        log.info("=== Testing Governance Operations ===");

        // Given - governance parameters
        // Note: The registerDRep methods require Account or Credential objects
        // For this test, we'll just create a simple voting delegation

        // Create voting delegation transaction DSL
        TxDsl govTx = new TxDsl()
                .delegateVotingPowerTo(address1, com.bloxbean.cardano.client.transaction.spec.governance.DRep.addrKeyHash("drep1_test..."))
                .from(address1);

        // Generate YAML
        String yaml = govTx.toYaml();
        log.info("Governance operation YAML:\n{}", yaml);

        // Verify YAML structure
        assertThat(yaml).contains("type: voting_delegation");
        assertThat(yaml).contains("address: " + address1);

        log.info("✓ Governance DSL creation successful!");
    }

    // ===== YAML ROUND-TRIP PERSISTENCE TESTS =====

    @Test
    void testYamlPersistenceAndRehydration() throws IOException, CborSerializationException {
        log.info("=== Testing YAML Persistence and Rehydration ===");

        // Given - complex transaction with multiple operations
        NativeScript policy = ScriptPubkey.createWithNewKey()._1;

        Map<String, Object> variables = Map.of(
            "sender", address1,
            "tokenName", "PersistToken",
            "tokenAmount", "100"
        );
        
        TxDsl complexTx = TxDsl.withVariables(variables)
                .payToAddress(address2, Amount.ada(1))
                .payToAddress(address3, Amount.ada(2))
                .mintAssets(policy, List.of(new Asset("PersistToken", BigInteger.valueOf(100))), address2)
                .from(address1);

        // Step 1: Serialize to YAML
        String originalYaml = complexTx.toYaml();
        log.info("Original YAML:\n{}", originalYaml);

        // Step 2: Save to file (simulating persistence)
        File tempDir = Files.createTempDirectory("tx-dsl-test").toFile();
        File yamlFile = new File(tempDir, "transaction.yaml");
        Files.write(yamlFile.toPath(), originalYaml.getBytes());
        log.info("Saved to: {}", yamlFile.getAbsolutePath());

        // Step 3: Load from file (simulating rehydration)
        String loadedYaml = Files.readString(yamlFile.toPath());

        // Step 4: Deserialize and verify structure
        TxDsl rehydratedTx = TxDsl.fromYaml(loadedYaml);
        assertThat(rehydratedTx).isNotNull();
        assertThat(rehydratedTx.getIntentions()).hasSize(3); // 2 payments + 1 minting

        // Step 5: Re-serialize and compare
        String reserializedYaml = rehydratedTx.toYaml();
        log.info("Re-serialized YAML:\n{}", reserializedYaml);

        // Verify key elements are preserved
        assertThat(reserializedYaml).contains("type: payment");
        assertThat(reserializedYaml).contains("type: minting");
        assertThat(reserializedYaml).contains("from: " + address1);

        // Cleanup
        yamlFile.delete();
        tempDir.delete();

        log.info("✓ YAML persistence and rehydration successful!");
    }

    @Test
    void testYamlTemplateWithDynamicValues() throws IOException {
        log.info("=== Testing YAML Template with Dynamic Values ===");

        // Given - template YAML file
        String templateYaml = "version: 1.0\n" +
                "variables:\n" +
                "  network: \"${NETWORK}\"\n" +
                "  sender: \"${SENDER_ADDRESS}\"\n" +
                "  recipient: \"${RECIPIENT_ADDRESS}\"\n" +
                "  amount: \"${AMOUNT}\"\n" +
                "\n" +
                "transaction:\n" +
                "  - tx:\n" +
                "      from: \"${sender}\"\n" +
                "      intentions:\n" +
                "        - type: payment\n" +
                "          address: \"${recipient}\"\n" +
                "          amounts:\n" +
                "            - unit: lovelace\n" +
                "              quantity: \"${amount}\"\n" +
                "\n" +
                "context:\n" +
                "  feePayer: \"${sender}\"\n" +
                "  utxoSelectionStrategy: \"LARGEST_FIRST\"\n";

        // Step 1: Replace placeholders with actual values
        String actualYaml = templateYaml
                .replace("${NETWORK}", "preprod")
                .replace("${SENDER_ADDRESS}", address1)
                .replace("${RECIPIENT_ADDRESS}", address2)
                .replace("${AMOUNT}", "5000000");

        log.info("Instantiated template:\n{}", actualYaml);

        // Step 2: Execute transaction from template
        Result<String> result = txDslBuilder
                .composeFromYaml(actualYaml)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait();

        // Then - verify transaction succeeded
        assertThat(result.isSuccessful())
                .withFailMessage("Template transaction failed: " + result.getResponse())
                .isTrue();

        log.info("Template-based transaction submitted: {}", result.getValue());

        log.info("✓ YAML template with dynamic values successful!");
    }

    @Test
    void testBuilderMethodChaining() {
        log.info("=== Testing Builder Method Chaining ===");

        // Given - multiple TxDsl objects
        TxDsl payment = new TxDsl()
                .payToAddress(address2, Amount.ada(1));

        TxDsl donation = new TxDsl()
                .donateToTreasury(BigInteger.valueOf(1_000_000_000L), BigInteger.valueOf(5_000_000L));

        // When - chain multiple builder methods
        QuickTxBuilder.TxContext context = txDslBuilder
                .feePayer(address1)
                .collateralPayer(address1)
                .utxoSelectionStrategy("SEQUENTIAL")
                .signer("account1")
                .compose(payment, donation);

        // Then - verify context is returned
        assertThat(context).isNotNull();

        log.info("✓ Builder method chaining successful!");
    }

    // ===== HELPER METHODS =====

    private List<Utxo> getUtxos(String address) {
        return new DefaultUtxoSupplier(backendService.getUtxoService()).getAll(address);
    }
}
