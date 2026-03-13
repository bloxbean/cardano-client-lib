package com.bloxbean.cardano.client.annotation.devnet.metadata;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataCBORContent;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.it.BaseIT;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.util.HexUtil;
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

        var cborResult = backendService.getMetadataService().getCBORMetadataByTxnHash(txHash);
        assertTrue(cborResult.isSuccessful(), "Metadata retrieval should succeed");

        MetadataMap chainMap = extractMetadataMap(cborResult.getValue(), "1000");
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

    @SneakyThrows
    private MetadataMap extractMetadataMap(List<MetadataCBORContent> entries, String label) {
        for (MetadataCBORContent entry : entries) {
            if (label.equals(entry.getLabel())) {
                byte[] cborBytes = HexUtil.decodeHexString(entry.getCborMetadata());
                List<DataItem> items = CborDecoder.decode(cborBytes);
                return new CBORMetadataMap((co.nstant.in.cbor.model.Map) items.get(0));
            }
        }
        fail("No metadata found for label " + label);
        return null;
    }
}
