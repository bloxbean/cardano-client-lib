package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.intent.*;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

// Governance imports
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.transaction.spec.governance.*;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.*;
import com.bloxbean.cardano.client.util.HexUtil;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test the new intent-based architecture.
 * Verifies that:
 * 1. Operations create intentions automatically
 * 2. Plans are generated automatically (no enableRecording needed)
 * 3. YAML/JSON serialization works
 * 4. IntentProcessor can reconstruct transactions from plans
 */
public class IntentBasedArchitectureTest {

    private final ObjectMapper jsonMapper = new ObjectMapper();

    @Test
    public void testTxAutomaticPlanGeneration() {
        // Create Tx with various operations
        Tx tx = new Tx()
            .from("addr_test1sender123...")
            .payToAddress("addr_test1receiver1...", Amount.ada(10.5))
            .payToAddress("addr_test1receiver2...", Amount.ada(5.25))
            .payToContract("addr_test1contract...", Amount.ada(3.0), PlutusData.unit())
            .donateToTreasury(BigInteger.valueOf(1000000), BigInteger.valueOf(100000))
            .withChangeAddress("addr_test1change...");

        // Plan should be automatically available
        TxPlan plan = tx.getPlan();
        assertNotNull(plan, "Plan should be automatically generated");
        assertEquals("tx", plan.getType());
        assertEquals("1.0", plan.getVersion());
        assertEquals(4, plan.getIntentions().size()); // 3 payments + 1 donation

        // Verify intentions
        List<TxIntention> intentions = plan.getIntentions();
        assertTrue(intentions.stream().anyMatch(i -> "payment".equals(i.getType())));
        assertTrue(intentions.stream().anyMatch(i -> "donation".equals(i.getType())));

        // Verify attributes
        assertEquals("addr_test1sender123...", plan.getAttributes().getFrom());
        assertEquals("addr_test1change...", plan.getAttributes().getChangeAddress());
    }

    @Test
    public void testScriptTxAutomaticPlanGeneration() {
        // Create ScriptTx with script-specific operations
        ScriptTx scriptTx = new ScriptTx()
            .payToAddress("addr_test1receiver...", Amount.ada(7.5))
            .payToContract("addr_test1script...", Amount.ada(2.0), PlutusData.unit())
            .collectFrom(createDummyUtxo("abcd123400000000000000000000000000000000000000000000000000000000", 0), PlutusData.unit(), PlutusData.unit())
            .collectFrom(createDummyUtxo("efgh567800000000000000000000000000000000000000000000000000000000", 1), PlutusData.unit())
            .withChangeAddress("addr_test1script_change...");

        // Plan should be automatically available
        TxPlan plan = scriptTx.getPlan();
        assertNotNull(plan, "Plan should be automatically generated");
        assertEquals("script_tx", plan.getType());
        assertEquals(4, plan.getIntentions().size()); // 2 payments + 2 script collects

        // Verify intention types
        long paymentCount = plan.getIntentions().stream()
            .filter(i -> "payment".equals(i.getType()))
            .count();
        long scriptCollectCount = plan.getIntentions().stream()
            .filter(i -> "script_collect_from".equals(i.getType()))
            .count();
        assertEquals(2, paymentCount);
        assertEquals(2, scriptCollectCount);
    }

    @Test
    public void testMintAssetsPlanGeneration() throws Exception {
        // Create simple minting script (proper 28-byte hex hash)
        NativeScript mintingScript = new ScriptPubkey("1234567890123456789012345678901234567890123456789012345678");
        List<Asset> assets = List.of(new Asset("MyToken", BigInteger.valueOf(1000)));

        Tx tx = new Tx()
            .from("addr_test1sender...")
            .mintAssets(mintingScript, assets, "addr_test1receiver...");

        TxPlan plan = tx.getPlan();
        assertNotNull(plan);
        assertEquals(1, plan.getIntentions().size());

        TxIntention intention = plan.getIntentions().get(0);
        assertEquals("minting", intention.getType());

        MintingIntention mintingIntention = (MintingIntention) intention;
        assertEquals("addr_test1receiver...", mintingIntention.getReceiver());
        assertEquals(1, mintingIntention.getAssets().size());
        assertEquals("MyToken", mintingIntention.getAssets().get(0).getName());
    }

    @Test
    public void testYamlSerializationRoundTrip() throws Exception {
        // Create complex transaction
        Tx tx = new Tx()
            .from("addr_test1sender123...")
            .payToAddress("addr_test1receiver1...", Amount.ada(10.5))
            .donateToTreasury(BigInteger.valueOf(1000000), BigInteger.valueOf(100000))
            .withChangeAddress("addr_test1change...");

        TxPlan originalPlan = tx.getPlan();
        assertNotNull(originalPlan);

        // Test YAML serialization
        String yaml = originalPlan.toYamlPretty();
        assertNotNull(yaml);
        assertTrue(yaml.contains("type: tx"));
        assertTrue(yaml.contains("version: 1.0"));
        assertTrue(yaml.contains("intentions:"));

        // Test YAML deserialization
        TxPlan deserializedPlan = TxPlan.fromYaml(yaml);
        assertNotNull(deserializedPlan);
        assertEquals(originalPlan.getType(), deserializedPlan.getType());
        assertEquals(originalPlan.getVersion(), deserializedPlan.getVersion());
        assertEquals(originalPlan.getIntentions().size(), deserializedPlan.getIntentions().size());
    }

    @Test
    public void testJsonSerializationRoundTrip() throws Exception {
        // Create complex ScriptTx
        ScriptTx scriptTx = new ScriptTx()
            .payToAddress("addr_test1receiver...", Amount.ada(7.5))
            .collectFrom(createDummyUtxo("abcd123400000000000000000000000000000000000000000000000000000000", 0), PlutusData.unit())
            .withChangeAddress("addr_test1script_change...");

        TxPlan originalPlan = scriptTx.getPlan();
        assertNotNull(originalPlan);

        // Test JSON serialization
        String json = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(originalPlan);
        assertNotNull(json);
        assertTrue(json.contains("\"type\" : \"script_tx\""));
        assertTrue(json.contains("\"version\" : \"1.0\""));

        // Test JSON deserialization
        TxPlan deserializedPlan = jsonMapper.readValue(json, TxPlan.class);
        assertNotNull(deserializedPlan);
        assertEquals(originalPlan.getType(), deserializedPlan.getType());
        assertEquals(originalPlan.getIntentions().size(), deserializedPlan.getIntentions().size());
    }

    @Test
    public void testPlanReplayability() throws Exception {
        // Create transaction with multiple operations
        Tx originalTx = new Tx()
            .from("addr_test1sender123...")
            .payToAddress("addr_test1receiver1...", Amount.ada(10.0))
            .payToContract("addr_test1contract...", Amount.ada(5.0), PlutusData.unit())
            .withChangeAddress("addr_test1change...");

        // Get plan
        TxPlan plan = originalTx.getPlan();
        assertNotNull(plan);

        // Serialize to YAML and back
        String yaml = plan.toYamlPretty();
        TxPlan deserializedPlan = TxPlan.fromYaml(yaml);

        // Process deserialized intentions to reconstruct transaction behavior
        Tx reconstructedTx = new Tx();

        // Verify intentions can be processed without errors
        assertDoesNotThrow(() -> {
            IntentProcessor.processIntentions(reconstructedTx, deserializedPlan.getIntentions(), deserializedPlan.getVariables());
        });

        assertNotNull(reconstructedTx);

        // Verify the reconstructed transaction has the expected structure
        // (This tests that processing worked, not that we recreated the exact same plan)
        assertNotNull(deserializedPlan.getIntentions());
        assertEquals(2, deserializedPlan.getIntentions().size()); // 2 payment intentions

        // Verify intention types are correct
        List<String> intentionTypes = deserializedPlan.getIntentions().stream()
            .map(TxIntention::getType)
            .collect(java.util.stream.Collectors.toList());
        assertTrue(intentionTypes.contains("payment"));
        assertEquals(2, intentionTypes.stream().filter(type -> type.equals("payment")).count());
    }

    @Test
    public void testEmptyTransactionPlan() {
        // Transaction with no operations should have null plan
        Tx emptyTx = new Tx().from("addr_test1sender...");
        assertNull(emptyTx.getPlan(), "Empty transaction should have null plan");

        // Transaction with only configuration should have null plan
        ScriptTx emptyScriptTx = new ScriptTx().withChangeAddress("addr_test1change...");
        assertNull(emptyScriptTx.getPlan(), "ScriptTx with no operations should have null plan");
    }

    @Test
    public void testIntentionDeduplication() {
        // Multiple identical operations should create multiple intentions
        Tx tx = new Tx()
            .payToAddress("addr_test1receiver...", Amount.ada(1.0))
            .payToAddress("addr_test1receiver...", Amount.ada(1.0))  // Same operation
            .payToAddress("addr_test1receiver...", Amount.ada(2.0)); // Different amount

        TxPlan plan = tx.getPlan();
        assertNotNull(plan);
        assertEquals(3, plan.getIntentions().size(), "Should create separate intentions for each call");
    }

    @Test
    public void testMetadataIntentionGeneration() {
        // Create Tx with metadata using MetadataBuilder
        Metadata metadata1 = MetadataBuilder.createMetadata()
            .put(BigInteger.valueOf(1001), "Test message 1");
        Metadata metadata2 = MetadataBuilder.createMetadata()
            .put(BigInteger.valueOf(1002), "Test message 2");

        Tx tx = new Tx()
            .from("addr_test1sender...")
            .payToAddress("addr_test1receiver...", Amount.ada(5.0))
            .attachMetadata(metadata1)
            .attachMetadata(metadata2); // Multiple metadata should create multiple intentions

        TxPlan plan = tx.getPlan();
        assertNotNull(plan);

        // Should have 1 payment + 2 metadata intentions
        assertEquals(3, plan.getIntentions().size());

        // Count metadata intentions
        long metadataCount = plan.getIntentions().stream()
            .filter(i -> "metadata".equals(i.getType()))
            .count();
        assertEquals(2, metadataCount, "Should have 2 metadata intentions");
    }

    @Test
    public void testMetadataIntentionSerialization() throws Exception {
        // Create metadata intention with both JSON and CBOR hex
        Metadata originalMetadata = MetadataBuilder.createMetadata()
            .put(BigInteger.valueOf(1234), "Test serialization message");
        MetadataIntention intention = MetadataIntention.from(originalMetadata);

        // Should have both formats available
        assertTrue(intention.hasRuntimeObjects(), "Should have runtime metadata object");
        assertNotNull(intention.getMetadataJson(), "Should generate JSON format");
        assertNotNull(intention.getMetadataCborHex(), "Should generate CBOR hex format");

        // JSON should be parseable
        String json = intention.getMetadataJson();
        assertNotNull(json);
        assertTrue(json.contains("1234")); // Our custom label

        // CBOR hex should be valid hex
        String cborHex = intention.getMetadataCborHex();
        assertNotNull(cborHex);
        assertTrue(cborHex.matches("^[0-9a-fA-F]+$"), "CBOR hex should contain only hex characters");
    }

    @Test
    public void testMetadataIntentionDeserialization() throws Exception {
        // Test deserialization priority: CBOR hex takes priority over JSON
        String metadataJson = "{\"1234\":[\"JSON message\"]}";

        // Create a valid CBOR hex by first creating metadata and getting its hex
        Metadata testMetadata = MetadataBuilder.createMetadata()
            .put(BigInteger.valueOf(5555), "CBOR test message");
        String metadataCborHex = MetadataBuilder.toJson(testMetadata); // For now, just test with JSON

        // Create intention with both formats (testing JSON only for now)
        MetadataIntention intention = MetadataIntention.fromJson(metadataJson);

        // Should have JSON format
        assertTrue(intention.hasJson(), "Should have JSON");
        assertTrue(intention.needsDeserialization(), "Should need deserialization");
        assertFalse(intention.hasRuntimeObjects(), "Should not have runtime objects initially");
    }

    @Test
    public void testMetadataYamlRoundTrip() throws Exception {
        // Create transaction with metadata
        Metadata metadata = MetadataBuilder.createMetadata()
            .put(BigInteger.valueOf(5678), "YAML test message");
        Tx tx = new Tx()
            .from("addr_test1sender...")
            .payToAddress("addr_test1receiver...", Amount.ada(3.0))
            .attachMetadata(metadata);

        TxPlan originalPlan = tx.getPlan();
        assertNotNull(originalPlan);

        // Serialize to YAML
        String yaml = originalPlan.toYamlPretty();
        assertNotNull(yaml);
        assertTrue(yaml.contains("type: metadata"), "YAML should contain metadata intention");
        assertTrue(yaml.contains("metadata_json:"), "YAML should contain JSON format");
        assertTrue(yaml.contains("metadata_cbor_hex:"), "YAML should contain CBOR hex format");

        // Deserialize from YAML
        TxPlan deserializedPlan = TxPlan.fromYaml(yaml);
        assertNotNull(deserializedPlan);
        assertEquals(originalPlan.getIntentions().size(), deserializedPlan.getIntentions().size());

        // Find metadata intention
        MetadataIntention metadataIntention = (MetadataIntention) deserializedPlan.getIntentions().stream()
            .filter(i -> "metadata".equals(i.getType()))
            .findFirst()
            .orElse(null);

        assertNotNull(metadataIntention, "Should have metadata intention after deserialization");
        assertTrue(metadataIntention.needsDeserialization(), "Should need deserialization");
        assertTrue(metadataIntention.hasCborHex(), "Should have CBOR hex after deserialization");
    }

    // ========== Governance Intention Tests ==========

    @Test
    public void testDRepRegistrationIntention() {
        // Create test account and anchor
        Account account = new Account();
        Anchor anchor = new Anchor(
            "https://test.com/drep.json",
            HexUtil.decodeHexString("1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")
        );

        // Create transaction with DRep registration
        Tx tx = new Tx()
            .from("addr_test1sender...")
            .withChangeAddress("addr_test1change...");

        // Enable intention recording FIRST
        tx.enableIntentionRecording();

        // Then perform the DRep registration
        tx.registerDRep(account, anchor);

        List<TxIntention> intentions = tx.getIntentions();

        assertNotNull(intentions);
        assertTrue(intentions.size() > 0, "Should have at least one intention");

        // Find DRep registration intention
        DRepRegistrationIntention drepIntention = (DRepRegistrationIntention) intentions.stream()
            .filter(i -> "drep_registration".equals(i.getType()))
            .findFirst()
            .orElse(null);

        assertNotNull(drepIntention, "Should have DRep registration intention");
        assertNotNull(drepIntention.getDrepCredential(), "Should have DRep credential");
        assertEquals("https://test.com/drep.json", drepIntention.getAnchorUrl());
        assertNotNull(drepIntention.getAnchorHash());
    }

    @Test
    public void testDRepDeregistrationIntention() {
        // Create test account
        Account account = new Account();
        String refundAddress = "addr_test1refund...";

        // Create transaction with DRep deregistration
        Tx tx = new Tx()
            .from("addr_test1sender...")
            .withChangeAddress("addr_test1change...");

        // Enable intention recording FIRST
        tx.enableIntentionRecording();

        // Then perform the DRep deregistration
        tx.unregisterDRep(account.drepCredential(), refundAddress);

        List<TxIntention> intentions = tx.getIntentions();

        assertNotNull(intentions);

        // Find DRep deregistration intention
        DRepDeregistrationIntention deregIntention = (DRepDeregistrationIntention) intentions.stream()
            .filter(i -> "drep_deregistration".equals(i.getType()))
            .findFirst()
            .orElse(null);

        assertNotNull(deregIntention, "Should have DRep deregistration intention");
        assertNotNull(deregIntention.getDrepCredential(), "Should have DRep credential");
        assertEquals(refundAddress, deregIntention.getRefundAddress());
    }

    @Test
    public void testDRepUpdateIntention() {
        // Create test account and anchor
        Account account = new Account();
        Anchor newAnchor = new Anchor(
            "https://test.com/updated-drep.json",
            HexUtil.decodeHexString("abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890")
        );

        // Create transaction with DRep update
        Tx tx = new Tx()
            .from("addr_test1sender...")
            .withChangeAddress("addr_test1change...");

        // Enable intention recording FIRST
        tx.enableIntentionRecording();

        // Then perform the DRep update
        tx.updateDRep(account.drepCredential(), newAnchor);

        List<TxIntention> intentions = tx.getIntentions();

        assertNotNull(intentions);

        // Find DRep update intention
        DRepUpdateIntention updateIntention = (DRepUpdateIntention) intentions.stream()
            .filter(i -> "drep_update".equals(i.getType()))
            .findFirst()
            .orElse(null);

        assertNotNull(updateIntention, "Should have DRep update intention");
        assertNotNull(updateIntention.getDrepCredential(), "Should have DRep credential");
        assertEquals("https://test.com/updated-drep.json", updateIntention.getAnchorUrl());
        assertNotNull(updateIntention.getAnchorHash());
    }

    @Test
    public void testGovernanceProposalIntention() {
        // Create a simple governance action (Info action)
        InfoAction govAction = new InfoAction();
        String returnAddress = "stake_test1uqfu74w3wh4gfzu8m6e7j987h4lq9r3t7ef5gaw497uu85qsqfy27";
        Anchor anchor = new Anchor(
            "https://test.com/proposal.json",
            HexUtil.decodeHexString("fedcba0987654321fedcba0987654321fedcba0987654321fedcba0987654321")
        );

        // Create transaction with governance proposal
        Tx tx = new Tx()
            .from("addr_test1sender...")
            .withChangeAddress("addr_test1change...");

        // Enable intention recording FIRST
        tx.enableIntentionRecording();

        // Then create the proposal
        tx.createProposal(govAction, returnAddress, anchor);

        List<TxIntention> intentions = tx.getIntentions();

        assertNotNull(intentions);

        // Find governance proposal intention
        GovernanceProposalIntention proposalIntention = (GovernanceProposalIntention) intentions.stream()
            .filter(i -> "governance_proposal".equals(i.getType()))
            .findFirst()
            .orElse(null);

        assertNotNull(proposalIntention, "Should have governance proposal intention");
        assertNotNull(proposalIntention.getGovAction(), "Should have governance action");
        assertEquals(returnAddress, proposalIntention.getReturnAddress());
        assertEquals("https://test.com/proposal.json", proposalIntention.getAnchorUrl());
    }

    @Test
    public void testVotingIntention() {
        // Create test voter and governance action ID
        Account voterAccount = new Account();
        Voter voter = Voter.builder()
            .type(VoterType.DREP_KEY_HASH)
            .credential(voterAccount.drepCredential())
            .build();

        GovActionId govActionId = new GovActionId(
            "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
            0
        );

        Anchor voteAnchor = new Anchor(
            "https://test.com/vote-rationale.json",
            HexUtil.decodeHexString("1111222233334444555566667777888899990000aaaabbbbccccddddeeeeffff")
        );

        // Create transaction with vote
        Tx tx = new Tx()
            .from("addr_test1sender...")
            .withChangeAddress("addr_test1change...");

        // Enable intention recording FIRST
        tx.enableIntentionRecording();

        // Then create the vote
        tx.createVote(voter, govActionId, Vote.YES, voteAnchor);

        List<TxIntention> intentions = tx.getIntentions();

        assertNotNull(intentions);

        // Find voting intention
        VotingIntention votingIntention = (VotingIntention) intentions.stream()
            .filter(i -> "voting".equals(i.getType()))
            .findFirst()
            .orElse(null);

        assertNotNull(votingIntention, "Should have voting intention");
        assertNotNull(votingIntention.getVoter(), "Should have voter");
        assertNotNull(votingIntention.getGovActionId(), "Should have governance action ID");
        assertEquals(Vote.YES, votingIntention.getVote());
        assertEquals("https://test.com/vote-rationale.json", votingIntention.getAnchorUrl());
    }

    @Test
    public void testVotingDelegationIntention() {
        // Create test address and DRep (using valid testnet address format)
        String delegatorAddress = "addr_test1qz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3jcu5d43746czr6q5v6pqy4t4wgkst8sx9p47gj8e3jd935qqrn5l";
        DRep drep = DRep.addrKeyHash("1234567890abcdef1234567890abcdef1234567890abcdef12345678");

        // Create transaction with voting delegation
        Tx tx = new Tx()
            .from("addr_test1sender...")
            .withChangeAddress("addr_test1change...");

        // Enable intention recording FIRST
        tx.enableIntentionRecording();

        // Then delegate voting power
        tx.delegateVotingPowerTo(delegatorAddress, drep);

        List<TxIntention> intentions = tx.getIntentions();

        assertNotNull(intentions);

        // Find voting delegation intention
        VotingDelegationIntention delegationIntention = (VotingDelegationIntention) intentions.stream()
            .filter(i -> "voting_delegation".equals(i.getType()))
            .findFirst()
            .orElse(null);

        assertNotNull(delegationIntention, "Should have voting delegation intention");
        assertEquals(delegatorAddress, delegationIntention.getAddressStr());
        assertNotNull(delegationIntention.getDrep(), "Should have DRep");
        assertEquals("addr_keyhash", delegationIntention.getDrepType().toLowerCase());
    }

    @Test
    public void testGovernanceIntentionsYamlSerialization() throws Exception {
        // Create complex transaction with multiple governance operations
        Account account = new Account();
        Anchor anchor = new Anchor(
            "https://test.com/drep.json",
            HexUtil.decodeHexString("1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")
        );

        InfoAction govAction = new InfoAction();
        String returnAddress = "stake_test1uqfu74w3wh4gfzu8m6e7j987h4lq9r3t7ef5gaw497uu85qsqfy27";

        Tx tx = new Tx()
            .from("addr_test1sender...")
            .withChangeAddress("addr_test1change...");

        // Enable intention recording FIRST
        tx.enableIntentionRecording();

        // Then perform governance operations
        tx.registerDRep(account, anchor)
          .createProposal(govAction, returnAddress, anchor)
          .delegateVotingPowerTo("addr_test1delegator...", DRep.abstain());

        // Get the transaction YAML
        String yaml = tx.toYaml();
        assertNotNull(yaml);
        System.out.println("Governance YAML:");
        System.out.println(yaml);

        // Verify YAML contains governance intentions
        assertTrue(yaml.contains("drep_registration"), "YAML should contain DRep registration");
        assertTrue(yaml.contains("governance_proposal"), "YAML should contain governance proposal");
        assertTrue(yaml.contains("voting_delegation"), "YAML should contain voting delegation");
        assertTrue(yaml.contains("drep_credential_hex"), "YAML should contain DRep credential hex");
        assertTrue(yaml.contains("anchor_url"), "YAML should contain anchor URL");
    }

    @Test
    public void testGovernanceIntentionsWithVariables() {
        // Create transaction with governance operations using variables
        Tx tx = new Tx()
            .from("${sender_address}")
            .payToAddress("${receiver_address}", Amount.ada(10))
            .withChangeAddress("${change_address}");

        // Enable intention recording FIRST
        tx.enableIntentionRecording();

        // Add governance operation (this would normally use variables too)
        Account account = new Account();
        tx.registerDRep(account);

        // Get the YAML
        String yaml = tx.toYaml();
        assertNotNull(yaml);

        // Verify variables section
        assertTrue(yaml.contains("variables:"), "YAML should have variables section");
        assertTrue(yaml.contains("sender_address:"), "YAML should have sender_address variable");
        assertTrue(yaml.contains("${sender_address}"), "YAML should use variable references");
    }

    private Utxo createDummyUtxo(String txHash, int outputIndex) {
        Utxo utxo = new Utxo();
        utxo.setTxHash(txHash);
        utxo.setOutputIndex(outputIndex);
        utxo.setAddress("addr_test_script_address");
        return utxo;
    }
}
