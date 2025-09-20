package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.intent.PaymentIntent;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class for the new unified YAML serialization format in QuickTx.
 */
class UnifiedYamlSerializationTest {

    @Test
    void testSingleTransactionToYaml() {
        // Given
        Tx tx = new Tx()
            .from("addr1_treasury_test")
            .payToAddress("addr1_alice_test", Amount.ada(10))
            .payToAddress("addr1_bob_test", Amount.ada(5));

        // When
        String yaml = TxPlan.from(tx).toYaml();
        System.out.println("Single Tx YAML:");
        System.out.println(yaml);

        // Then
        assertThat(yaml).isNotNull();
        assertThat(yaml).contains("version: 1.0");
        assertThat(yaml).contains("transaction:");
        assertThat(yaml).contains("- tx:");
        assertThat(yaml).contains("from: addr1_treasury_test");
        assertThat(yaml).contains("intents:");
        assertThat(yaml).contains("type: payment");
        assertThat(yaml).contains("addr1_alice_test");
        assertThat(yaml).contains("addr1_bob_test");
    }

    @Test
    void testSingleTransactionWithVariables() {
        // Given
        Tx tx = new Tx()
            .from("addr1_treasury_test")
            .payToAddress("addr1_alice_test", Amount.ada(15));

        Map<String, Object> variables = Map.of(
            "treasury", "addr1_treasury_test",
            "alice", "addr1_alice_test",
            "amount", "15000000"
        );

        // When
        String yaml = TxPlan.from(tx).setVariables(variables).toYaml();
        System.out.println("Tx with Variables YAML:");
        System.out.println(yaml);

        // Then
        assertThat(yaml).contains("version: 1.0");
        assertThat(yaml).contains("variables:");
        assertThat(yaml).contains("treasury: addr1_treasury_test");
        assertThat(yaml).contains("alice: addr1_alice_test");
        assertThat(yaml).contains("amount: 15000000");
    }

    @Test
    void testSingleTransactionFromYaml() {
        // Given
        Tx original = new Tx()
            .from("addr1_treasury_test")
            .payToAddress("addr1_alice_test", Amount.ada(10))
            .withChangeAddress("addr1_change_test");

        String yaml = TxPlan.from(original).toYaml();

        // When
        Tx restored = (Tx) TxPlan.fromYaml(yaml).get(0);

        // Then
        assertThat(restored).isNotNull();
        assertThat(restored.getSender()).isEqualTo("addr1_treasury_test");
        assertThat(restored.getPublicChangeAddress()).isEqualTo("addr1_change_test");
        assertThat(restored.getIntentions()).hasSize(1);
        assertThat(restored.getIntentions().get(0)).isInstanceOf(PaymentIntent.class);

        PaymentIntent payment = (PaymentIntent) restored.getIntentions().get(0);
        assertThat(payment.getAddress()).isEqualTo("addr1_alice_test");
        assertThat(payment.getAmounts()).hasSize(1);
        assertThat(payment.getAmounts().get(0).getQuantity()).isEqualTo(BigInteger.valueOf(10000000L));
    }

    @Test
    void testMultipleTransactionsWithCollection() {
        // Given
        Tx tx1 = new Tx()
            .from("addr1_treasury_test")
            .payToAddress("addr1_alice_test", Amount.ada(10));

        Tx tx2 = new Tx()
            .from("addr1_alice_test")
            .payToAddress("addr1_treasury_test", Amount.ada(5));

        TxPlan collection = new TxPlan()
            .addVariable("treasury", "addr1_treasury_test")
            .addVariable("alice", "addr1_alice_test")
            .addTransaction(tx1)
            .addTransaction(tx2);

        // When
        String yaml = collection.toYaml();
        System.out.println("Multi-Transaction YAML:");
        System.out.println(yaml);

        // Then
        assertThat(yaml).contains("version: 1.0");
        assertThat(yaml).contains("variables:");
        assertThat(yaml).contains("treasury: addr1_treasury_test");
        assertThat(yaml).contains("alice: addr1_alice_test");
        assertThat(yaml).contains("transaction:");

        // Should contain two transaction entries
        long txCount = yaml.lines().filter(line -> line.trim().startsWith("- tx:")).count();
        assertThat(txCount).isEqualTo(2);
    }

    @Test
    void testMultipleTransactionsFromYaml() {
        // Given
        Tx tx1 = new Tx()
            .from("addr1_treasury_test")
            .payToAddress("addr1_alice_test", Amount.ada(10));

        Tx tx2 = new Tx()
            .from("addr1_alice_test")
            .payToAddress("addr1_treasury_test", Amount.ada(5));

        TxPlan collection = new TxPlan()
            .addTransaction(tx1)
            .addTransaction(tx2);

        String yaml = collection.toYaml();

        // When
        List<AbstractTx<?>> restored = TxPlan.fromYaml(yaml);

        // Then
        assertThat(restored).hasSize(2);
        assertThat(restored.get(0)).isInstanceOf(Tx.class);
        assertThat(restored.get(1)).isInstanceOf(Tx.class);

        Tx restoredTx1 = (Tx) restored.get(0);
        Tx restoredTx2 = (Tx) restored.get(1);

        assertThat(restoredTx1.getSender()).isEqualTo("addr1_treasury_test");
        assertThat(restoredTx1.getIntentions()).hasSize(1);

        assertThat(restoredTx2.getSender()).isEqualTo("addr1_alice_test");
        assertThat(restoredTx2.getIntentions()).hasSize(1);
    }

    @Test
    void testVariableResolutionInDeserialization() {
        // Given - YAML with variables
        String yamlWithVariables = "version: 1.0\n" +
            "variables:\n" +
            "  treasury: addr1_treasury_resolved\n" +
            "  alice: addr1_alice_resolved\n" +
            "transaction:\n" +
            "- tx:\n" +
            "    from: ${treasury}\n" +
            "    change_address: ${treasury}\n" +
            "    intents:\n" +
            "    - type: payment\n" +
            "      address: ${alice}\n" +
            "      amounts:\n" +
            "      - unit: lovelace\n" +
            "        quantity: '10000000'\n";

        // When
        List<AbstractTx<?>> restored = TxPlan.fromYaml(yamlWithVariables);

        // Then
        assertThat(restored).hasSize(1);
        Tx tx = (Tx) restored.get(0);

        assertThat(tx.getSender()).isEqualTo("addr1_treasury_resolved");
        assertThat(tx.getPublicChangeAddress()).isEqualTo("addr1_treasury_resolved");
        assertThat(tx.getIntentions()).hasSize(1);

        PaymentIntent payment = (PaymentIntent) tx.getIntentions().get(0);
        assertThat(payment.getAddress()).isEqualTo("addr1_alice_resolved");
    }

    @Test
    void testRoundTripSerialization() {
        // Given
        Tx original = new Tx()
            .from("addr1_treasury_test")
            .payToAddress("addr1_alice_test", Amount.ada(10))
            .payToAddress("addr1_bob_test", Amount.ada(5))
            .withChangeAddress("addr1_change_test");

        // When - serialize and deserialize
        String yaml = TxPlan.from(original).toYaml();
        Tx restored = (Tx) TxPlan.fromYaml(yaml).get(0);
        String yaml2 = TxPlan.from(restored).toYaml();

        // Then - verify structural equivalence
        Yaml yamlParser = new Yaml();
        Map<String, Object> doc1 = yamlParser.load(yaml);
        Map<String, Object> doc2 = yamlParser.load(yaml2);

        assertThat(doc2).isEqualTo(doc1);

        // Verify content equivalence
        assertThat(restored.getSender()).isEqualTo(original.getSender());
        assertThat(restored.getPublicChangeAddress()).isEqualTo(original.getPublicChangeAddress());
        assertThat(restored.getIntentions()).hasSize(original.getIntentions().size());
    }

    @Test
    void testTransactionWithComplexIntentions() {
        // Given
        Tx tx = new Tx()
            .from("addr1_treasury_test")
            .payToAddress("addr1_alice_test", Amount.ada(10))
            .donateToTreasury(BigInteger.valueOf(1000000000L), BigInteger.valueOf(5000000L));

        // When
        String yaml = TxPlan.from(tx).toYaml();
        System.out.println("Complex Intentions YAML:");
        System.out.println(yaml);

        // Then
        assertThat(yaml).contains("type: payment");
        assertThat(yaml).contains("type: donation");
        assertThat(yaml).contains("current_treasury_value: 1000000000");
        assertThat(yaml).contains("donation_amount: 5000000");
    }

    @Test
    void testYamlStructureConformsToSpecification() {
        // Given
        Tx tx = new Tx()
            .from("${treasury}")
            .payToAddress("${alice}", Amount.ada(10))
            .withChangeAddress("${treasury}");

        Map<String, Object> variables = Map.of(
            "treasury", "addr1_treasury_test",
            "alice", "addr1_alice_test"
        );

        // When
        String yaml = TxPlan.from(tx).setVariables(variables).toYaml();
        System.out.println("Specification-compliant YAML:");
        System.out.println(yaml);

        // Then - verify it matches the desired format structure
        Yaml yamlParser = new Yaml();
        Map<String, Object> doc = yamlParser.load(yaml);

        // Root level structure
        assertThat(doc).containsKey("version");
        assertThat(doc).containsKey("variables");
        assertThat(doc).containsKey("transaction");

        // Variables section
        Map<String, Object> varsSection = (Map<String, Object>) doc.get("variables");
        assertThat(varsSection).containsKey("treasury");
        assertThat(varsSection).containsKey("alice");

        // Transaction array structure
        List<Map<String, Object>> transactions = (List<Map<String, Object>>) doc.get("transaction");
        assertThat(transactions).hasSize(1);

        Map<String, Object> firstTx = transactions.get(0);
        assertThat(firstTx).containsKey("tx");

        // Tx content structure
        Map<String, Object> txContent = (Map<String, Object>) firstTx.get("tx");
        assertThat(txContent).containsKey("from");
        assertThat(txContent).containsKey("change_address");
        assertThat(txContent).containsKey("intents");

        // Intents structure
        List<Map<String, Object>> intents = (List<Map<String, Object>>) txContent.get("intents");
        assertThat(intents).hasSize(1);

        Map<String, Object> paymentIntent = intents.get(0);
        assertThat(paymentIntent).containsKey("type");
        assertThat(paymentIntent).containsKey("address");
        assertThat(paymentIntent).containsKey("amounts");
        assertThat(paymentIntent.get("type")).isEqualTo("payment");
    }
}
