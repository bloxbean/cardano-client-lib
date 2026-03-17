package com.bloxbean.cardano.client.annotation.devnet.metadata;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataCBORContent;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.it.BaseIT;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import com.bloxbean.cardano.client.metadata.helper.JsonNoSchemaToMetadataConverter;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigInteger;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class StakeDelegationBatchDevnetTest extends BaseIT {

    private BackendService backendService;
    private StakeDelegationBatch original;
    private StakeDelegationBatch restored;
    private JsonNode jsonMeta;

    @SneakyThrows
    @BeforeAll
    void setup() {
        initializeAccounts();
        backendService = getBackendService();
        topupAllTestAccounts();

        original = buildOriginal();

        var converter = new StakeDelegationBatchMetadataConverter();
        Metadata metadata = converter.toMetadata(original);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx = new Tx()
                .payToAddress(address2, Amount.ada(1.5))
                .attachMetadata(metadata)
                .from(address1);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);

        assertTrue(result.isSuccessful(), "Transaction should succeed: " + result);
        String txHash = result.getValue();

        waitForTransaction(result);

        // Diagnostic: show that the CBOR metadata endpoint returns null cbor_metadata on Yaci Store
        var cborResult = backendService.getMetadataService().getCBORMetadataByTxnHash(txHash);
        System.out.println("[DIAG] CBOR metadata endpoint successful=" + cborResult.isSuccessful()
                + ", entries=" + (cborResult.getValue() != null ? cborResult.getValue().size() : "null"));
        if (cborResult.isSuccessful() && cborResult.getValue() != null) {
            for (MetadataCBORContent entry : cborResult.getValue()) {
                System.out.println("[DIAG]   label=" + entry.getLabel()
                        + ", cbor_metadata=" + (entry.getCborMetadata() == null ? "NULL" : entry.getCborMetadata().substring(0, Math.min(40, entry.getCborMetadata().length())) + "..."));
            }
        }

        var jsonResult = backendService.getMetadataService().getJSONMetadataByTxnHash(txHash);
        assertTrue(jsonResult.isSuccessful(), "JSON metadata retrieval should succeed");
        assertFalse(jsonResult.getValue().isEmpty(), "JSON metadata should have entries");

        // Verify raw JSON values match what was submitted
        jsonMeta = findJsonMetadataForLabel(jsonResult.getValue(), "1902");
        assertNotNull(jsonMeta, "JSON metadata for label 1902 should exist");
        System.out.println("[DIAG] JSON metadata for label 1902: " + jsonMeta);
        assertEquals(original.getBatchId(), jsonMeta.get("batch_id").asText(), "JSON 'batch_id' value mismatch");
        assertEquals(original.getStakeAddress(), jsonMeta.get("stake_addr").asText(), "JSON 'stake_addr' value mismatch");
        assertEquals(original.getVersion(), jsonMeta.get("version").asText(), "JSON 'version' value mismatch");
        assertEquals(original.getAuthor(), jsonMeta.get("author").asText(), "JSON 'author' value mismatch");
        assertEquals(original.getStatus(), jsonMeta.get("status").asText(), "JSON 'status' value mismatch");
        assertEquals(original.getTotalDelegated().toString(), jsonMeta.get("total_delegated").asText(), "JSON 'total_delegated' value mismatch");
        assertTrue(jsonMeta.has("delegations"), "JSON should contain 'delegations'");
        assertTrue(jsonMeta.get("delegations").isArray(), "'delegations' should be an array");
        assertEquals(original.getDelegations().size(), jsonMeta.get("delegations").size(), "JSON 'delegations' size mismatch");
        assertTrue(jsonMeta.has("reward_history"), "JSON should contain 'reward_history'");
        assertTrue(jsonMeta.has("pool_preferences"), "JSON should contain 'pool_preferences'");
        assertTrue(jsonMeta.has("active_pools"), "JSON should contain 'active_pools'");

        MetadataMap chainMap = extractMetadataMap(jsonResult.getValue(), "1902");
        restored = converter.fromMetadataMap(chainMap);
    }

    @Test
    void fullRoundTrip_batchIdAndStakeAddr() {
        assertEquals(original.getBatchId(), restored.getBatchId());
        assertEquals(original.getStakeAddress(), restored.getStakeAddress());
    }

    @Test
    void inheritedFields() {
        assertEquals(original.getVersion(), restored.getVersion());
        assertEquals(original.getAuthor(), restored.getAuthor());
    }

    @Test
    void nestedRecordListWithAdapter_delegations() {
        assertNotNull(restored.getDelegations());
        assertEquals(original.getDelegations().size(), restored.getDelegations().size());
        for (int i = 0; i < original.getDelegations().size(); i++) {
            DelegationInstruction orig = original.getDelegations().get(i);
            DelegationInstruction rest = restored.getDelegations().get(i);
            assertEquals(orig.poolId(), rest.poolId());
            assertEquals(orig.weightBps(), rest.weightBps());
            assertEquals(orig.delegatedAt(), rest.delegatedAt());
        }
    }

    @Test
    void mapStringNestedPojo_rewardsByEpoch() {
        assertNotNull(restored.getRewardsByEpoch());
        assertEquals(original.getRewardsByEpoch().size(), restored.getRewardsByEpoch().size());
        for (Map.Entry<String, RewardEntry> entry : original.getRewardsByEpoch().entrySet()) {
            RewardEntry origEntry = entry.getValue();
            RewardEntry restEntry = restored.getRewardsByEpoch().get(entry.getKey());
            assertNotNull(restEntry, "Missing entry for key: " + entry.getKey());
            assertEquals(origEntry.getPoolId(), restEntry.getPoolId());
            assertEquals(origEntry.getAmount(), restEntry.getAmount());
            assertEquals(origEntry.getEpochNo(), restEntry.getEpochNo());
        }
    }

    @Test
    void largeBigInteger_totalDelegated() {
        assertEquals(original.getTotalDelegated(), restored.getTotalDelegated());
    }

    @Test
    void sortedSet_poolPreferences() {
        assertNotNull(restored.getPoolPreferences());
        assertEquals(
                new ArrayList<>(original.getPoolPreferences()),
                new ArrayList<>(restored.getPoolPreferences())
        );
    }

    @Test
    void hexByteArrayList_activePoolHashes() {
        assertNotNull(restored.getActivePoolHashes());
        assertEquals(original.getActivePoolHashes().size(), restored.getActivePoolHashes().size());
        for (int i = 0; i < original.getActivePoolHashes().size(); i++) {
            assertArrayEquals(original.getActivePoolHashes().get(i), restored.getActivePoolHashes().get(i));
        }
    }

    @Test
    void adapterField_submittedAt() {
        assertEquals(original.getSubmittedAt(), restored.getSubmittedAt());
    }

    @Test
    void defaultValue_status() {
        assertEquals(original.getStatus(), restored.getStatus());
    }

    @Test
    void ignoredField() {
        assertNull(restored.getMemo());
    }

    // ── Raw JSON Assertions ────────────────────────────────────────────

    @Test
    void jsonRaw_adapterField_submittedAtIsEpochSeconds() {
        assertTrue(jsonMeta.has("submittedAt"), "JSON should contain 'submittedAt'");
        assertEquals(1700000000L, jsonMeta.get("submittedAt").asLong(),
                "submittedAt should be serialized as epoch seconds");
    }

    @Test
    void jsonRaw_nestedAdapterField_delegatedAtIsEpochSeconds() {
        JsonNode delegations = jsonMeta.get("delegations");
        assertNotNull(delegations, "JSON should contain 'delegations'");
        assertTrue(delegations.isArray());
        JsonNode first = delegations.get(0);
        assertEquals(1700000000L, first.get("delegatedAt").asLong(),
                "delegatedAt in first delegation should be epoch seconds");
    }

    @Test
    void jsonRaw_nestedRecordStructure_delegations() {
        JsonNode delegations = jsonMeta.get("delegations");
        assertNotNull(delegations);
        assertEquals(3, delegations.size());

        JsonNode first = delegations.get(0);
        assertEquals("pool1abc", first.get("pool_id").asText());
        assertEquals(5000, first.get("weight_bps").asInt());
    }

    @Test
    void jsonRaw_hexByteArrayList_activePools() {
        assertTrue(jsonMeta.has("active_pools"), "JSON should contain 'active_pools'");
        JsonNode pools = jsonMeta.get("active_pools");
        assertTrue(pools.isArray());
        assertEquals(2, pools.size());
        assertEquals("aabb00112233", pools.get(0).asText());
        assertEquals("ccdd44556677", pools.get(1).asText());
    }

    @Test
    void jsonRaw_mapWithNestedPojo_rewardHistory() {
        assertTrue(jsonMeta.has("reward_history"), "JSON should contain 'reward_history'");
        JsonNode rh = jsonMeta.get("reward_history");

        JsonNode entry400 = rh.get("400");
        assertNotNull(entry400, "reward_history should contain key '400'");
        assertEquals("pool1abc", entry400.get("pool_id").asText());
        assertEquals(5000000, entry400.get("amount").asInt());
        assertEquals(400, entry400.get("epoch_no").asInt());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private StakeDelegationBatch buildOriginal() {
        StakeDelegationBatch batch = new StakeDelegationBatch();
        batch.setVersion("2.0");
        batch.setAuthor("stake-service");
        batch.setBatchId("batch-001");
        batch.setStakeAddress("stake_test1uq...");
        batch.setDelegations(List.of(
                new DelegationInstruction("pool1abc", BigInteger.valueOf(5000), Instant.ofEpochSecond(1700000000L)),
                new DelegationInstruction("pool1def", BigInteger.valueOf(3000), Instant.ofEpochSecond(1700001000L)),
                new DelegationInstruction("pool1ghi", BigInteger.valueOf(2000), Instant.ofEpochSecond(1700002000L))
        ));

        RewardEntry reward1 = new RewardEntry();
        reward1.setPoolId("pool1abc");
        reward1.setAmount(BigInteger.valueOf(5000000));
        reward1.setEpochNo(BigInteger.valueOf(400));

        RewardEntry reward2 = new RewardEntry();
        reward2.setPoolId("pool1def");
        reward2.setAmount(BigInteger.valueOf(3000000));
        reward2.setEpochNo(BigInteger.valueOf(401));

        batch.setRewardsByEpoch(Map.of("400", reward1, "401", reward2));
        batch.setTotalDelegated(new BigInteger("50000000000"));
        batch.setPoolPreferences(new TreeSet<>(List.of("pool1abc", "pool1def", "pool1ghi")));
        batch.setActivePoolHashes(List.of(
                HexUtil.decodeHexString("aabb00112233"),
                HexUtil.decodeHexString("ccdd44556677")
        ));
        batch.setSubmittedAt(Instant.ofEpochSecond(1700000000L));
        batch.setStatus("ACTIVE");
        batch.setMemo("should-be-ignored");
        return batch;
    }

    private JsonNode findJsonMetadataForLabel(List<MetadataJSONContent> entries, String label) {
        for (MetadataJSONContent entry : entries) {
            if (label.equals(entry.getLabel())) {
                return entry.getJsonMetadata();
            }
        }
        return null;
    }

    private MetadataMap extractMetadataMap(List<MetadataJSONContent> entries, String label) {
        for (MetadataJSONContent entry : entries) {
            if (label.equals(entry.getLabel())) {
                return JsonNoSchemaToMetadataConverter.parseObjectNode(
                        (ObjectNode) entry.getJsonMetadata());
            }
        }
        fail("No metadata found for label " + label);
        return null;
    }
}
