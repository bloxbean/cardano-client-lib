package com.bloxbean.cardano.client.dsl;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.it.BaseIT;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for TxDsl payment functionality using YaciDevKit.
 *
 * Prerequisites:
 * - YaciDevKit running on localhost:8080
 * - Test accounts funded with test ADA
 */
public class TxDslPaymentIT extends BaseIT {
    private static final Logger log = LoggerFactory.getLogger(TxDslPaymentIT.class);

    private BackendService backendService;

    @BeforeEach
    void setup() {
        backendService = getBackendService();
        initializeAccounts();
        
        // Topup sender account if balance is below threshold
        topupIfNeeded(address1);
        
        printBalances();
    }

    @Test
    void testBasicPayment() {
        log.info("=== Testing Basic Payment with TxDsl ===");

        // Given - transfer 5 ADA from account1 to account2
        Amount transferAmount = Amount.ada(5);
        long initialBalance2 = getBalance(address2);

        // When - create transaction using TxDsl
        TxDsl txDsl = new TxDsl()
                .payToAddress(address2, transferAmount)
                .from(address1);

        log.info("Generated YAML:");
        log.info(txDsl.toYaml());
        System.out.println(txDsl.toYaml());

        // Build and submit transaction
        QuickTxBuilder builder = new QuickTxBuilder(backendService);
        Result<String> result = builder.compose(txDsl.unwrap())
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait();

        // Then - verify transaction succeeded
        assertThat(result.isSuccessful())
                .withFailMessage("Transaction failed: " + result.getResponse())
                .isTrue();

        log.info("Payment transaction submitted: {}", result.getValue());

        // Wait for transaction confirmation
        waitForTransaction(result);

        // Verify balance increased for recipient
        waitForBlocks(2);
        long finalBalance2 = getBalance(address2);
        long expectedIncrease = transferAmount.getQuantity().longValue();

        assertThat(finalBalance2)
                .withFailMessage("Balance should have increased by at least %d lovelace, but was %d -> %d",
                        expectedIncrease, initialBalance2, finalBalance2)
                .isGreaterThanOrEqualTo(initialBalance2 + expectedIncrease);

        log.info("✓ Payment successful! Balance increased from {} to {} lovelace",
                initialBalance2, finalBalance2);
    }

    @Test
    void testMultiplePayments() {
        log.info("=== Testing Multiple Payments with TxDsl ===");

        // Given - transfer to multiple recipients
        Amount amount1 = Amount.ada(2);
        Amount amount2 = Amount.ada(3);
        long initialBalance2 = getBalance(address2);
        long initialBalance3 = getBalance(address3);

        // When - create transaction with multiple payments
        TxDsl txDsl = new TxDsl()
                .payToAddress(address2, amount1)
                .payToAddress(address3, amount2)
                .from(address1);

        log.info("Generated YAML for multiple payments:");
        log.info(txDsl.toYaml());

        // Verify DSL captured multiple intentions
        assertThat(txDsl.getIntentions()).hasSize(2);
        assertThat(txDsl.getIntentions().get(0).getType()).isEqualTo("payment");
        assertThat(txDsl.getIntentions().get(1).getType()).isEqualTo("payment");

        // Build and submit transaction
        QuickTxBuilder builder = new QuickTxBuilder(backendService);
        Result<String> result = builder.compose(txDsl.unwrap())
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait();

        // Then - verify transaction succeeded
        assertThat(result.isSuccessful())
                .withFailMessage("Transaction failed: " + result.getResponse())
                .isTrue();

        log.info("Multiple payment transaction submitted: {}", result.getValue());

        // Wait for transaction confirmation
        waitForTransaction(result);
        waitForBlocks(2);

        // Verify balances increased for both recipients
        long finalBalance2 = getBalance(address2);
        long finalBalance3 = getBalance(address3);

        assertThat(finalBalance2).isGreaterThanOrEqualTo(initialBalance2 + amount1.getQuantity().longValue());
        assertThat(finalBalance3).isGreaterThanOrEqualTo(initialBalance3 + amount2.getQuantity().longValue());

        log.info("✓ Multiple payments successful!");
        log.info("  Address 2: {} -> {} lovelace", initialBalance2, finalBalance2);
        log.info("  Address 3: {} -> {} lovelace", initialBalance3, finalBalance3);
    }

    @Test
    void testYamlSerialization() {
        log.info("=== Testing YAML Serialization ===");

        // Given - create a TxDsl with various operations
        TxDsl original = new TxDsl()
                .payToAddress(address2, Amount.ada(1))
                .payToAddress(address3, Amount.ada(2))
                .from(address1);

        // When - serialize to YAML and back
        String yaml = original.toYaml();
        log.info("Serialized YAML:\n{}", yaml);

        TxDsl restored = TxDsl.fromYaml(yaml);

        // Then - verify structure is preserved
        assertThat(restored).isNotNull();
        assertThat(restored.getIntentions()).hasSize(2);

        // Verify YAML contains expected elements
        assertThat(yaml).contains("type: payment");
        assertThat(yaml).contains("from: " + address1);
        assertThat(yaml).contains("version: 1.0");
        assertThat(yaml).contains("intentions:");

        log.info("✓ YAML serialization working correctly");
    }

    @Test
    void testDonation() {
        log.info("=== Testing Treasury Donation ===");

        // Given - donation parameters
        java.math.BigInteger currentTreasury = java.math.BigInteger.valueOf(1_000_000_000L); // 1B lovelace
        java.math.BigInteger donationAmount = java.math.BigInteger.valueOf(10_000_000L); // 10 ADA

        // When - create donation transaction
        TxDsl txDsl = new TxDsl()
                .donateToTreasury(currentTreasury, donationAmount)
                .from(address1);

        log.info("Generated YAML for donation:");
        log.info(txDsl.toYaml());

        // Then - verify donation intention captured
        assertThat(txDsl.getIntentions()).hasSize(1);
        assertThat(txDsl.getIntentions().get(0).getType()).isEqualTo("donation");

        // Verify YAML structure
        String yaml = txDsl.toYaml();
        assertThat(yaml).contains("type: donation");
        assertThat(yaml).contains("current_treasury_value:");
        assertThat(yaml).contains("donation_amount:");

        log.info("✓ Treasury donation DSL working correctly");

        // Note: We don't actually submit this transaction as it requires governance capabilities
        // This test focuses on DSL structure and serialization
    }
}
