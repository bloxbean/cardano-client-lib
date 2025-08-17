package com.bloxbean.cardano.client.dsl;

import com.bloxbean.cardano.client.api.model.Amount;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VariableSubstitutionTest {
    
    @Test
    void testVariableStorage() {
        // Given
        TxDsl txDsl = new TxDsl();
        
        // When
        txDsl.withVariable("TREASURY", "addr1_treasury...")
             .withVariable("AMOUNT", "5000000");
        
        // Then - Variables should be stored (we'll access through serialization)
        String yaml = txDsl.toYaml();
        assertThat(yaml).contains("TREASURY");
        assertThat(yaml).contains("addr1_treasury...");
        assertThat(yaml).contains("AMOUNT");
        assertThat(yaml).contains("5000000");
    }
    
    @Test
    void testVariableSubstitutionInYaml() {
        // Given - YAML with variables using new unified structure
        String yamlTemplate = "version: \"1.0\"\n" +
            "variables:\n" +
            "  TREASURY: \"addr1_treasury123...\"\n" +
            "  RECEIVER: \"addr1_alice456...\"\n" +
            "  AMOUNT: \"10000000\"\n" +
            "transaction:\n" +
            "- tx:\n" +
            "    intentions:\n" +
            "      - type: from\n" +
            "        address: \"${TREASURY}\"\n" +
            "      - type: payment\n" +
            "        address: \"${RECEIVER}\"\n" +
            "        amounts:\n" +
            "          - unit: lovelace\n" +
            "            quantity: \"${AMOUNT}\"\n";
        
        // When - Deserialize with variable substitution
        TxDsl txDsl = TxDsl.fromYaml(yamlTemplate);
        
        // Then - Variables should be resolved in the transactions
        assertThat(txDsl.getIntentions()).hasSize(2);
        // We'll verify this works when we implement variable resolution
    }
    
    @Test
    void testVariableOverride() {
        // Given - YAML template with variables using new unified structure
        String yamlTemplate = "version: \"1.0\"\n" +
            "variables:\n" +
            "  RECEIVER: \"addr1_default...\"\n" +
            "  AMOUNT: \"5000000\"\n" +
            "transaction:\n" +
            "- tx:\n" +
            "    intentions:\n" +
            "      - type: payment\n" +
            "        address: \"${RECEIVER}\"\n" +
            "        amounts:\n" +
            "          - unit: lovelace\n" +
            "            quantity: \"${AMOUNT}\"\n";
        
        // When - Override variables
        TxDsl txDsl = TxDsl.fromYaml(yamlTemplate)
            .withVariable("RECEIVER", "addr1_alice...")
            .withVariable("AMOUNT", "10000000");
        
        // Then - New variables should take precedence
        String yaml = txDsl.toYaml();
        assertThat(yaml).contains("addr1_alice...");
        assertThat(yaml).contains("10000000");
    }
    
    @Test
    void testBuildTemplateWithVariables() {
        // Given - Build a transaction template
        TxDsl template = new TxDsl()
            .from("${TREASURY}")
            .payToAddress("${EMPLOYEE}", Amount.ada(5)) // Use actual amount, not template var
            .withVariable("TREASURY", "addr1_treasury...")
            .withVariable("EMPLOYEE", "addr1_alice...")
            .withVariable("SALARY", "5");
        
        // When - Serialize to create template
        String yamlTemplate = template.toYaml();
        
        // Then - Template should contain variable placeholders
        assertThat(yamlTemplate).contains("${TREASURY}");
        assertThat(yamlTemplate).contains("${EMPLOYEE}");
        
        // And also contain the variable definitions
        assertThat(yamlTemplate).contains("TREASURY: addr1_treasury...");
        assertThat(yamlTemplate).contains("EMPLOYEE: addr1_alice...");
        assertThat(yamlTemplate).contains("SALARY: 5");
    }
}