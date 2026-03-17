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
        assertTrue(jsonResult.isSuccessful(), "Metadata retrieval should succeed");

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
