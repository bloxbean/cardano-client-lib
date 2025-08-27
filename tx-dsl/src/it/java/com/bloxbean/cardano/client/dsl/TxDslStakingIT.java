package com.bloxbean.cardano.client.dsl;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.it.BaseIT;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for TxDsl staking functionality using YaciDevKit.
 *
 * Prerequisites:
 * - YaciDevKit running on localhost:8080
 * - Test accounts funded with test ADA
 *
 * Tests staking operations:
 * - Stake registration
 * - Stake delegation to pool
 * - Stake deregistration
 * - Stake withdrawal
 */
public class TxDslStakingIT extends BaseIT {
    private static final Logger log = LoggerFactory.getLogger(TxDslStakingIT.class);

    // YaciDevKit default pool ID
    private static final String DEFAULT_POOL_ID = "pool1wvqhvyrgwch4jq9aa84hc8q4kzvyq2z3xr6mpafkqmx9wce39zy";

    private BackendService backendService;

    @BeforeEach
    void setup() {
        backendService = getBackendService();
        initializeAccounts();

        // Topup sender account for staking operations (which require deposits)
        topupIfNeeded(address1);

        printBalances();
    }

    @Test
    void testStakeRegistration() {
        log.info("=== Testing Stake Registration with TxDsl ===");

        // Given - prepare stake registration transaction
        TxDsl txDsl = new TxDsl()
                .registerStakeAddress(account1.stakeAddress())
                .from(address1);

        log.info("Generated YAML for stake registration:");
        log.info(txDsl.toYaml());

        // Verify DSL structure
        assertThat(txDsl.getIntentions()).hasSize(1);
        assertThat(txDsl.getIntentions().get(0).getType()).isEqualTo("stake_registration");

        // Verify YAML contains expected elements
        String yaml = txDsl.toYaml();
        assertThat(yaml).contains("type: stake_registration");
        assertThat(yaml).contains("stake_address: " + account1.stakeAddress());
        assertThat(yaml).contains("from: " + address1);

        // When - build and submit transaction
        QuickTxBuilder builder = new QuickTxBuilder(backendService);
        Result<String> result = builder.compose(txDsl.unwrap())
                .withSigner(SignerProviders.signerFrom(account1))
                .withSigner(SignerProviders.stakeKeySignerFrom(account1))
                .completeAndWait();

        // Then - verify transaction succeeded
        assertThat(result.isSuccessful())
                .withFailMessage("Stake registration failed: " + result.getResponse())
                .isTrue();

        log.info("✓ Stake registration transaction submitted: {}", result.getValue());

        // Wait for transaction confirmation
        waitForTransaction(result);
        waitForBlocks(2);

        log.info("✓ Stake registration completed successfully!");
    }

    @Test
    void testStakeDelegation() {
        log.info("=== Testing Stake Delegation with TxDsl ===");

        // Given - prepare stake delegation transaction (assuming stake address is already registered)
        TxDsl txDsl = new TxDsl()
                .delegateTo(account1.stakeAddress(), DEFAULT_POOL_ID)
                .from(address1);

        log.info("Generated YAML for stake delegation:");
        log.info(txDsl.toYaml());

        // Verify DSL structure
        assertThat(txDsl.getIntentions()).hasSize(1);
        assertThat(txDsl.getIntentions().get(0).getType()).isEqualTo("stake_delegation");

        // Verify YAML contains expected elements
        String yaml = txDsl.toYaml();
        assertThat(yaml).contains("type: stake_delegation");
        assertThat(yaml).contains("stake_address: " + account1.stakeAddress());
        assertThat(yaml).contains("pool_id: " + DEFAULT_POOL_ID);
        assertThat(yaml).contains("from: " + address1);

        // When - build and submit transaction
        QuickTxBuilder builder = new QuickTxBuilder(backendService);
        Result<String> result = builder.compose(txDsl.unwrap())
                .withSigner(SignerProviders.signerFrom(account1))
                .withSigner(SignerProviders.stakeKeySignerFrom(account1))
                .completeAndWait();

        // Then - verify transaction succeeded
        assertThat(result.isSuccessful())
                .withFailMessage("Stake delegation failed: " + result.getResponse())
                .isTrue();

        log.info("✓ Stake delegation transaction submitted: {}", result.getValue());

        // Wait for transaction confirmation
        waitForTransaction(result);
        waitForBlocks(2);

        log.info("✓ Stake delegation to pool {} completed successfully!", DEFAULT_POOL_ID);
    }

    @Test
    void testStakeWithdrawal() {
        log.info("=== Testing Stake Withdrawal with TxDsl ===");

        // Given - prepare stake withdrawal transaction
        // Note: In a real scenario, there would need to be rewards available to withdraw
        BigInteger withdrawalAmount = BigInteger.valueOf(1_000_000L); // 1 ADA in lovelace

        TxDsl txDsl = new TxDsl()
                .withdraw(account1.stakeAddress(), withdrawalAmount)
                .from(address1);

        log.info("Generated YAML for stake withdrawal:");
        log.info(txDsl.toYaml());

        // Verify DSL structure
        assertThat(txDsl.getIntentions()).hasSize(1);
        assertThat(txDsl.getIntentions().get(0).getType()).isEqualTo("stake_withdrawal");

        // Verify YAML contains expected elements
        String yaml = txDsl.toYaml();
        assertThat(yaml).contains("type: stake_withdrawal");
        assertThat(yaml).contains("reward_address: " + account1.stakeAddress());
        assertThat(yaml).contains("amount:");
        assertThat(yaml).contains("from: " + address1);

        log.info("✓ Stake withdrawal DSL structure validated successfully!");

        // Note: We don't submit this transaction as there are likely no rewards available
        // This test focuses on DSL structure and YAML serialization
    }

    @Test
    void testStakeDeregistration() {
        log.info("=== Testing Stake Deregistration with TxDsl ===");

        // Given - prepare stake deregistration transaction (assumes stake address was previously registered)
        TxDsl txDsl = new TxDsl()
                .deregisterStakeAddress(account1.stakeAddress())
                .from(address1);

        log.info("Generated YAML for stake deregistration:");
        log.info(txDsl.toYaml());

        // Verify DSL structure
        assertThat(txDsl.getIntentions()).hasSize(1);
        assertThat(txDsl.getIntentions().get(0).getType()).isEqualTo("stake_deregistration");

        // Verify YAML contains expected elements
        String yaml = txDsl.toYaml();
        assertThat(yaml).contains("type: stake_deregistration");
        assertThat(yaml).contains("stake_address: " + account1.stakeAddress());
        assertThat(yaml).contains("from: " + address1);

        // When - build and submit transaction
        QuickTxBuilder builder = new QuickTxBuilder(backendService);
        Result<String> result = builder.compose(txDsl.unwrap())
                .withSigner(SignerProviders.signerFrom(account1))
                .withSigner(SignerProviders.stakeKeySignerFrom(account1))
                .completeAndWait();

        // Then - verify transaction succeeded
        assertThat(result.isSuccessful())
                .withFailMessage("Stake deregistration failed: " + result.getResponse())
                .isTrue();

        log.info("✓ Stake deregistration transaction submitted: {}", result.getValue());

        // Wait for transaction confirmation
        waitForTransaction(result);
        waitForBlocks(2);

        log.info("✓ Stake deregistration completed successfully!");
    }

    @Test
    void testCompleteStakingWorkflow() {
        log.info("=== Testing Complete Staking Workflow ===");

        // This test demonstrates a complete staking workflow:
        // 1. Register stake address
        // 2. Delegate to pool
        // 3. Eventually deregister (for cleanup)

        Account account = new Account(Networks.testnet());
        String address = account.baseAddress();

        String stakeAddr = account.stakeAddress(); // Use account2 for this workflow

        // Ensure account2 has sufficient funds
        topupIfNeeded(address);

        // Step 1: Register stake address
        log.info("Step 1: Registering stake address...");
        TxDsl registerTxDsl = new TxDsl()
                .registerStakeAddress(stakeAddr)
                .from(address);

        QuickTxBuilder builder = new QuickTxBuilder(backendService);
        Result<String> registerResult = builder.compose(registerTxDsl.unwrap())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait();

        log.info(registerResult.getResponse());
        assertThat(registerResult.isSuccessful()).isTrue();
        log.info("✓ Stake registration completed: {}", registerResult.getValue());
        waitForTransaction(registerResult);

        // Step 2: Delegate to pool
        log.info("Step 2: Delegating stake to pool...");
        TxDsl delegateTxDsl = new TxDsl()
                .delegateTo(stakeAddr, DEFAULT_POOL_ID)
                .from(address);

        Result<String> delegateResult = builder.compose(delegateTxDsl.unwrap())
                .withSigner(SignerProviders.signerFrom(account))
                .withSigner(SignerProviders.stakeKeySignerFrom(account))
                .completeAndWait();

        assertThat(delegateResult.isSuccessful()).isTrue();
        log.info("✓ Stake delegation completed: {}", delegateResult.getValue());
        waitForTransaction(delegateResult);

        // Step 3: Deregister for cleanup
        log.info("Step 3: Deregistering stake address for cleanup...");
        TxDsl deregisterTxDsl = new TxDsl()
                .deregisterStakeAddress(stakeAddr)
                .from(address);

        Result<String> deregisterResult = builder.compose(deregisterTxDsl.unwrap())
                .withSigner(SignerProviders.signerFrom(account))
                .withSigner(SignerProviders.stakeKeySignerFrom(account))
                .completeAndWait();

        assertThat(deregisterResult.isSuccessful()).isTrue();
        log.info("✓ Stake deregistration completed: {}", deregisterResult.getValue());
        waitForTransaction(deregisterResult);

        log.info("✓ Complete staking workflow executed successfully!");
        log.info("  Registration tx: {}", registerResult.getValue());
        log.info("  Delegation tx: {}", delegateResult.getValue());
        log.info("  Deregistration tx: {}", deregisterResult.getValue());
    }

    @Test
    void testMultipleStakingOperations() {
        log.info("=== Testing Multiple Staking Operations in One Transaction ===");

        // Given - create a transaction with multiple staking operations
        TxDsl txDsl = new TxDsl()
                .registerStakeAddress(account3.stakeAddress())
                .delegateTo(account3.stakeAddress(), DEFAULT_POOL_ID)
                .from(address3);

        log.info("Generated YAML for multiple staking operations:");
        log.info(txDsl.toYaml());

        // Verify DSL captured multiple intentions
        assertThat(txDsl.getIntentions()).hasSize(2);
        assertThat(txDsl.getIntentions().get(0).getType()).isEqualTo("stake_registration");
        assertThat(txDsl.getIntentions().get(1).getType()).isEqualTo("stake_delegation");

        // Verify YAML structure
        String yaml = txDsl.toYaml();
        assertThat(yaml).contains("type: stake_registration");
        assertThat(yaml).contains("type: stake_delegation");
        assertThat(yaml).contains("pool_id: " + DEFAULT_POOL_ID);

        // Ensure account3 has sufficient funds
        topupIfNeeded(address3);

        // When - build and submit transaction
        QuickTxBuilder builder = new QuickTxBuilder(backendService);
        Result<String> result = builder.compose(txDsl.unwrap())
                .withSigner(SignerProviders.signerFrom(account3))
                .withSigner(SignerProviders.stakeKeySignerFrom(account3))
                .completeAndWait();

        // Then - verify transaction succeeded
        assertThat(result.isSuccessful())
                .withFailMessage("Multiple staking operations failed: " + result.getResponse())
                .isTrue();

        log.info("✓ Multiple staking operations transaction submitted: {}", result.getValue());

        // Wait for transaction confirmation
        waitForTransaction(result);
        waitForBlocks(2);

        log.info("✓ Multiple staking operations completed successfully!");
    }

    @Test
    void testStakingYamlSerialization() {
        log.info("=== Testing Staking YAML Serialization ===");

        // Given - create a TxDsl with various staking operations
        TxDsl original = new TxDsl()
                .registerStakeAddress(account1.stakeAddress())
                .delegateTo(account1.stakeAddress(), DEFAULT_POOL_ID)
                .withdraw(account1.stakeAddress(), BigInteger.valueOf(5_000_000L))
                .from(address1);

        // When - serialize to YAML and back
        String yaml = original.toYaml();
        log.info("Serialized YAML for staking operations:\n{}", yaml);

        TxDsl restored = TxDsl.fromYaml(yaml);

        // Then - verify structure is preserved
        assertThat(restored).isNotNull();
        assertThat(restored.getIntentions()).hasSize(3);

        // Verify YAML contains all expected elements
        assertThat(yaml).contains("type: stake_registration");
        assertThat(yaml).contains("type: stake_delegation");
        assertThat(yaml).contains("type: stake_withdrawal");
        assertThat(yaml).contains("pool_id: " + DEFAULT_POOL_ID);
        assertThat(yaml).contains("stake_address: " + account1.stakeAddress());
        assertThat(yaml).contains("from: " + address1);
        assertThat(yaml).contains("version: 1.0");
        assertThat(yaml).contains("intentions:");

        // Verify intention types are preserved
        assertThat(restored.getIntentions().get(0).getType()).isEqualTo("stake_registration");
        assertThat(restored.getIntentions().get(1).getType()).isEqualTo("stake_delegation");
        assertThat(restored.getIntentions().get(2).getType()).isEqualTo("stake_withdrawal");

        log.info("✓ Staking YAML serialization working correctly");
    }

    @Test
    void testStakingWithPayments() {
        log.info("=== Testing Combined Staking and Payment Operations ===");

        // Given - create a transaction that combines staking and payment operations
        TxDsl txDsl = new TxDsl()
                .payToAddress(address2, Amount.ada(1))
                .registerStakeAddress(account1.stakeAddress())
                .payToAddress(address3, Amount.ada(2))
                .delegateTo(account1.stakeAddress(), DEFAULT_POOL_ID)
                .from(address1);

        log.info("Generated YAML for combined operations:");
        log.info(txDsl.toYaml());

        // Verify DSL captured all intentions in order
        assertThat(txDsl.getIntentions()).hasSize(4);
        assertThat(txDsl.getIntentions().get(0).getType()).isEqualTo("payment");
        assertThat(txDsl.getIntentions().get(1).getType()).isEqualTo("stake_registration");
        assertThat(txDsl.getIntentions().get(2).getType()).isEqualTo("payment");
        assertThat(txDsl.getIntentions().get(3).getType()).isEqualTo("stake_delegation");

        // Verify YAML structure contains both payment and staking operations
        String yaml = txDsl.toYaml();
        assertThat(yaml).contains("type: payment");
        assertThat(yaml).contains("type: stake_registration");
        assertThat(yaml).contains("type: stake_delegation");

        log.info("✓ Combined staking and payment DSL structure validated successfully!");
    }
}
