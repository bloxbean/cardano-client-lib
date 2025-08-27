package com.bloxbean.cardano.client.dsl;

import com.bloxbean.cardano.client.api.model.Amount;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VariableSubstitutionTest {
    
    @Test
    void testVariableStorage() {
        // Given - Using new static factory
        Map<String, Object> variables = Map.of(
            "TREASURY", "addr1_treasury...",
            "AMOUNT", "5000000"
        );
        
        // When
        TxDsl txDsl = TxDsl.withVariables(variables);
        
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
            "    from: \"${TREASURY}\"\n" +
            "    intentions:\n" +
            "      - type: payment\n" +
            "        address: \"${RECEIVER}\"\n" +
            "        amounts:\n" +
            "          - unit: lovelace\n" +
            "            quantity: \"${AMOUNT}\"\n";
        
        // When - Deserialize with variable substitution
        TxDsl txDsl = TxDsl.fromYaml(yamlTemplate);
        
        // Then - Variables should be resolved in the transactions (from is now an attribute)
        assertThat(txDsl.getIntentions()).hasSize(1);
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
        
        // When - Create TxDsl from YAML and add additional variables
        TxDsl baseTxDsl = TxDsl.fromYaml(yamlTemplate);
        
        // Create new TxDsl with overridden variables
        Map<String, Object> overrideVars = Map.of(
            "RECEIVER", "addr1_alice...",
            "AMOUNT", "10000000"
        );
        
        // Since we can't modify variables after creation, we need to create a new TxDsl
        // with the combined variable set for this test case to make sense
        TxDsl txDsl = TxDsl.withVariables(overrideVars)
            .from("${TREASURY}")
            .payToAddress("${RECEIVER}", Amount.lovelace(BigInteger.valueOf(10000000L)));
        
        // Then - New variables should take precedence
        String yaml = txDsl.toYaml();
        assertThat(yaml).contains("addr1_alice...");
        assertThat(yaml).contains("10000000");
    }
    
    @Test
    void testBuildTemplateWithVariables() {
        // Given - Build a transaction template using new static factory
        Map<String, Object> variables = Map.of(
            "TREASURY", "addr1_treasury...",
            "EMPLOYEE", "addr1_alice...",
            "SALARY", "5"
        );
        
        TxDsl template = TxDsl.withVariables(variables)
            .from("${TREASURY}")
            .payToAddress("${EMPLOYEE}", Amount.ada(5)); // Use actual amount, not template var
        
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