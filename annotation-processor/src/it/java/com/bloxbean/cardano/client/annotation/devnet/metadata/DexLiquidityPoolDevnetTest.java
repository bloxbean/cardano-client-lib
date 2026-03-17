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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DexLiquidityPoolDevnetTest extends BaseIT {

    private BackendService backendService;
    private DexLiquidityPool original;
    private DexLiquidityPool restored;

    @SneakyThrows
    @BeforeAll
    void setup() {
        initializeAccounts();
        backendService = getBackendService();
        topupAllTestAccounts();

        original = buildOriginal();

        var converter = new DexLiquidityPoolMetadataConverter();
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
        JsonNode jsonMeta = findJsonMetadataForLabel(jsonResult.getValue(), "1000");
        assertNotNull(jsonMeta, "JSON metadata for label 1000 should exist");
        System.out.println("[DIAG] JSON metadata for label 1000: " + jsonMeta);
        assertEquals(original.poolId(), jsonMeta.get("pool_id").asText(), "JSON 'pool_id' value mismatch");
        assertEquals(original.reserveA().toString(), jsonMeta.get("reserve_a").asText(), "JSON 'reserve_a' value mismatch");
        assertEquals(original.reserveB().toString(), jsonMeta.get("reserve_b").asText(), "JSON 'reserve_b' value mismatch");
        assertEquals(original.totalLpTokens().toString(), jsonMeta.get("total_lp").asText(), "JSON 'total_lp' value mismatch");
        assertEquals(original.lastAction().name(), jsonMeta.get("last_action").asText(), "JSON 'last_action' value mismatch");
        assertTrue(jsonMeta.has("token_a"), "JSON should contain 'token_a'");
        assertTrue(jsonMeta.has("token_b"), "JSON should contain 'token_b'");
        assertTrue(jsonMeta.has("fees"), "JSON should contain 'fees'");
        assertTrue(jsonMeta.has("provider_shares"), "JSON should contain 'provider_shares'");

        MetadataMap chainMap = extractMetadataMap(jsonResult.getValue(), "1000");
        restored = converter.fromMetadataMap(chainMap);
    }

    @Test
    void fullRoundTrip_poolId() {
        assertEquals(original.poolId(), restored.poolId());
    }

    @Test
    void nestedRecord_tokenA() {
        assertEquals(original.tokenA().policyId(), restored.tokenA().policyId());
        assertEquals(original.tokenA().assetName(), restored.tokenA().assetName());
        assertEquals(original.tokenA().decimals(), restored.tokenA().decimals());
    }

    @Test
    void nestedRecord_tokenB() {
        assertEquals(original.tokenB().policyId(), restored.tokenB().policyId());
        assertEquals(original.tokenB().assetName(), restored.tokenB().assetName());
        assertEquals(original.tokenB().decimals(), restored.tokenB().decimals());
    }

    @Test
    void largeBigInteger() {
        assertEquals(original.reserveA(), restored.reserveA());
        assertEquals(original.reserveB(), restored.reserveB());
        assertEquals(original.totalLpTokens(), restored.totalLpTokens());
    }

    @Test
    void nestedPojo_fees() {
        assertNotNull(restored.fees());
        assertEquals(original.fees().getSwapFee(), restored.fees().getSwapFee());
        assertEquals(original.fees().getProtocolFee(), restored.fees().getProtocolFee());
    }

    @Test
    void enumField_lastAction() {
        assertEquals(original.lastAction(), restored.lastAction());
    }

    @Test
    void enumList_recentActions() {
        assertEquals(original.recentActions(), restored.recentActions());
    }

    @Test
    void mapStringBigInteger_providerShares() {
        assertEquals(original.providerShares(), restored.providerShares());
    }

    @Test
    void adapterField_updatedAt() {
        assertEquals(original.updatedAt(), restored.updatedAt());
    }

    @Test
    void base64ByteArray_poolDatum() {
        assertArrayEquals(original.poolDatum(), restored.poolDatum());
    }

    private DexLiquidityPool buildOriginal() {
        return new DexLiquidityPool(
                "pool1abc123def456",
                new TokenInfo("aabb00112233445566778899aabb00112233445566778899aabb0011", "TokenA", 6),
                new TokenInfo("ccdd00112233445566778899ccdd00112233445566778899ccdd0011", "TokenB", 8),
                new BigInteger("1000000000000"),
                new BigInteger("2500000000000"),
                new BigInteger("50000000000"),
                new PoolFeeConfig(BigInteger.valueOf(30), BigInteger.valueOf(5)),
                LpAction.SWAP,
                List.of(LpAction.ADD_LIQUIDITY, LpAction.SWAP, LpAction.REMOVE_LIQUIDITY),
                Map.of("alice", BigInteger.valueOf(25000), "bob", BigInteger.valueOf(75000)),
                Instant.ofEpochSecond(1700000000L),
                Base64.getDecoder().decode("SGVsbG9Xb3JsZERhdHVt")
        );
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
