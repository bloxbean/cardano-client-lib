package com.bloxbean.cardano.client.dsl;

import com.bloxbean.cardano.client.api.model.Amount;
import org.junit.jupiter.api.Test;

class DebugYamlTest {
    
    @Test
    void printYaml() {
        TxDsl txDsl = new TxDsl()
            .from("addr1_sender...")
            .payToAddress("addr1_receiver...", Amount.ada(5));
        
        String yaml = txDsl.toYaml();
        System.out.println("Generated YAML:");
        System.out.println(yaml);
    }
}