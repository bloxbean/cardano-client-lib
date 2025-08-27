package com.bloxbean.cardano.client.dsl;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.dsl.intention.TxIntention;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TxDslMintingTest {

    @Test
    void testMintSingleAsset() throws Exception {
        // Given
        TxDsl txDsl = new TxDsl();
        NativeScript policyScript = ScriptPubkey.createWithNewKey()._1;
        Asset asset = new Asset("MyToken", BigInteger.valueOf(1000));
        
        // When
        TxDsl result = txDsl.mintAssets(policyScript, asset);
        
        // Then
        assertThat(result).isSameAs(txDsl); // Fluent API
        Tx tx = txDsl.unwrap();
        assertThat(tx).isNotNull();
        
        // Verify intention captured
        List<TxIntention> intentions = txDsl.getIntentions();
        assertThat(intentions).hasSize(1);
        TxIntention intention = intentions.get(0);
        assertThat(intention.getType()).isEqualTo("minting");
    }

    @Test
    void testMintSingleAssetWithReceiver() throws Exception {
        // Given
        TxDsl txDsl = new TxDsl();
        NativeScript policyScript = ScriptPubkey.createWithNewKey()._1;
        Asset asset = new Asset("MyToken", BigInteger.valueOf(500));
        String receiver = "addr1_receiver...";
        
        // When
        TxDsl result = txDsl.mintAssets(policyScript, asset, receiver);
        
        // Then
        assertThat(result).isSameAs(txDsl); // Fluent API
        Tx tx = txDsl.unwrap();
        assertThat(tx).isNotNull();
        
        // Verify intention captured
        List<TxIntention> intentions = txDsl.getIntentions();
        assertThat(intentions).hasSize(1);
        TxIntention intention = intentions.get(0);
        assertThat(intention.getType()).isEqualTo("minting");
    }

    @Test
    void testMintMultipleAssets() throws Exception {
        // Given
        TxDsl txDsl = new TxDsl();
        NativeScript policyScript = ScriptPubkey.createWithNewKey()._1;
        List<Asset> assets = List.of(
            new Asset("TokenA", BigInteger.valueOf(1000)),
            new Asset("TokenB", BigInteger.valueOf(2000))
        );
        
        // When
        TxDsl result = txDsl.mintAssets(policyScript, assets);
        
        // Then
        assertThat(result).isSameAs(txDsl); // Fluent API
        Tx tx = txDsl.unwrap();
        assertThat(tx).isNotNull();
        
        // Verify intention captured
        List<TxIntention> intentions = txDsl.getIntentions();
        assertThat(intentions).hasSize(1);
        TxIntention intention = intentions.get(0);
        assertThat(intention.getType()).isEqualTo("minting");
    }

    @Test
    void testMintMultipleAssetsWithReceiver() throws Exception {
        // Given
        TxDsl txDsl = new TxDsl();
        NativeScript policyScript = ScriptPubkey.createWithNewKey()._1;
        List<Asset> assets = List.of(
            new Asset("TokenX", BigInteger.valueOf(500)),
            new Asset("TokenY", BigInteger.valueOf(750))
        );
        String receiver = "addr1_mint_receiver...";
        
        // When
        TxDsl result = txDsl.mintAssets(policyScript, assets, receiver);
        
        // Then
        assertThat(result).isSameAs(txDsl); // Fluent API
        Tx tx = txDsl.unwrap();
        assertThat(tx).isNotNull();
        
        // Verify intention captured
        List<TxIntention> intentions = txDsl.getIntentions();
        assertThat(intentions).hasSize(1);
        TxIntention intention = intentions.get(0);
        assertThat(intention.getType()).isEqualTo("minting");
    }

    @Test
    void testMethodChainingWithMintingAndPayments() throws Exception {
        // Given
        String sender = "addr1_sender...";
        String receiver = "addr1_receiver...";
        NativeScript policyScript = ScriptPubkey.createWithNewKey()._1;
        Asset asset = new Asset("ChainToken", BigInteger.valueOf(100));
        
        // When
        TxDsl txDsl = new TxDsl()
            .from(sender)
            .mintAssets(policyScript, asset, receiver)
            .payToAddress(receiver, Amount.ada(5));
        
        // Then
        assertThat(txDsl).isNotNull();
        Tx tx = txDsl.unwrap();
        assertThat(tx).isNotNull();
        
        // Verify all intentions captured (from is now an attribute)
        List<TxIntention> intentions = txDsl.getIntentions();
        assertThat(intentions).hasSize(2); // minting + payment intentions
        assertThat(intentions.get(0).getType()).isEqualTo("minting");
        assertThat(intentions.get(1).getType()).isEqualTo("payment");
    }

    @Test
    void testMintingYamlSerialization() throws Exception {
        // Given
        NativeScript policyScript = ScriptPubkey.createWithNewKey()._1;
        Asset asset = new Asset("SerializeToken", BigInteger.valueOf(1500));
        String receiver = "addr1_mint_to...";
        
        TxDsl txDsl = new TxDsl()
            .from("addr1_minter...")
            .mintAssets(policyScript, asset, receiver);

        // When
        String yaml = txDsl.toYaml();

        System.out.println(yaml);

        // Then
        assertThat(yaml).isNotNull();
        assertThat(yaml).contains("from: addr1_minter...");
        assertThat(yaml).contains("intentions:");
        assertThat(yaml).contains("type: minting");
        assertThat(yaml).contains("receiver: addr1_mint_to...");
        
        // Verify only minting intention (from is now an attribute)
        assertThat(txDsl.getIntentions()).hasSize(1);
        assertThat(txDsl.getIntentions().get(0).getType()).isEqualTo("minting");
    }

    @Test
    void testMintingYamlStructure() throws Exception {
        // Given
        NativeScript policyScript = ScriptPubkey.createWithNewKey()._1;
        Asset asset = new Asset("StructureToken", BigInteger.valueOf(800));
        
        TxDsl txDsl = new TxDsl()
            .from("addr1_minter...")
            .mintAssets(policyScript, asset);

        // When
        String yamlString = txDsl.toYaml();

        // Then parse YAML to verify structure
        Yaml yaml = new Yaml();
        Map<String, Object> doc = yaml.load(yamlString);

        assertThat(doc).containsKey("version");
        assertThat(doc.get("version")).isEqualTo(1.0);

        // Check for unified transaction structure
        assertThat(doc).containsKey("transaction");
        java.util.List<Map<String, Object>> transaction = (java.util.List<Map<String, Object>>) doc.get("transaction");
        assertThat(transaction).hasSize(1);
        
        Map<String, Object> firstTx = transaction.get(0);
        assertThat(firstTx).containsKey("tx");
        Map<String, Object> tx = (Map<String, Object>) firstTx.get("tx");
        
        // Verify attributes
        assertThat(tx).containsKey("from");
        assertThat(tx.get("from")).isEqualTo("addr1_minter...");
        
        // Verify intentions
        assertThat(tx).containsKey("intentions");
        java.util.List<Map<String, Object>> intentions = (java.util.List<Map<String, Object>>) tx.get("intentions");
        assertThat(intentions).hasSize(1);
        assertThat(intentions.get(0).get("type")).isEqualTo("minting");
    }

    @Test
    void testMintingFromYamlReconstruction() throws Exception {
        // Given
        NativeScript policyScript = ScriptPubkey.createWithNewKey()._1;
        Asset asset = new Asset("ReconstructToken", BigInteger.valueOf(1200));
        String receiver = "addr1_reconstruct_receiver...";
        
        TxDsl original = new TxDsl()
            .from("addr1_original_minter...")
            .mintAssets(policyScript, asset, receiver);

        // When
        String yaml = original.toYaml();
        TxDsl restored = TxDsl.fromYaml(yaml);

        // Then
        assertThat(restored).isNotNull();
        assertThat(restored.getIntentions()).hasSize(1);
        assertThat(restored.getIntentions().get(0).getType()).isEqualTo("minting");
    }

    @Test
    void testBurnAssets() throws Exception {
        // Given - Burning is minting with negative quantities
        NativeScript policyScript = ScriptPubkey.createWithNewKey()._1;
        Asset burnAsset = new Asset("BurnToken", BigInteger.valueOf(-500)); // Negative quantity = burn
        
        TxDsl txDsl = new TxDsl()
            .from("addr1_burner...")
            .mintAssets(policyScript, burnAsset); // This will burn 500 BurnTokens

        // When
        String yaml = txDsl.toYaml();

        // Then
        assertThat(yaml).isNotNull();
        assertThat(yaml).contains("type: minting");
        assertThat(yaml).contains("BurnToken");
        
        // Verify intention captured
        List<TxIntention> intentions = txDsl.getIntentions();
        assertThat(intentions).hasSize(1);
        assertThat(intentions.get(0).getType()).isEqualTo("minting");
    }
}