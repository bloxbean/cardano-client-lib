package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;

import java.util.Map;

/**
 * Quick test to check YAML output.
 */
public class TestYamlOutput {

    public static void main(String[] args) {
        // Test 1: Simple transaction without variables
        Tx tx1 = new Tx()
            .from("addr1_test")
            .payToAddress("addr1_receiver", Amount.ada(5));

        System.out.println("=== YAML without variables ===");
        System.out.println(TxPlan.from(tx1).toYaml());

        // Test 2: Transaction with variables
        Tx tx2 = new Tx()
            .from("addr1_treasury")
            .payToAddress("addr1_alice", Amount.ada(10));

        System.out.println("\n=== YAML with variables ===");
        System.out.println(TxPlan.from(tx2).setVariables(Map.of("treasury", "addr1_treasury", "alice", "addr1_alice")).toYaml());

        // Test 3: Multiple transactions
        TxPlan collection = new TxPlan()
            .addVariable("treasury", "addr1_treasury_test")
            .addVariable("alice", "addr1_alice_test")
            .addTransaction(tx1)
            .addTransaction(tx2);

        System.out.println("\n=== Multiple transactions with variables ===");
        System.out.println(collection.toYaml());
    }
}
