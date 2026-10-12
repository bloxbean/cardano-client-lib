package com.bloxbean.cardano.client.transaction.util;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.bip32.util.BytesUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.bloxbean.cardano.client.crypto.Blake2bUtil.blake2bHash224;
import static com.bloxbean.cardano.client.crypto.Blake2bUtil.blake2bHash256;
import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CborSpan} is era-agnostic: these tests slice real blocks of every Shelley-family era with it alone and check
 * every slice against on-chain values (block hash, tx ids, block body hash and size, the trigger's native script hash).
 * There is no typed block view in this step; the slicing below is test code.
 */
class RealBlockSpanTest {
    private static final Map<String, Integer> ERAS = Map.of(
            "Shelley", 2, "Allegra", 3, "Mary", 4, "Alonzo", 5, "Babbage", 6, "Conway", 7);
    private static final int ALONZO = 5;
    private static final int BABBAGE = 6;

    @Test
    void blocksOfEveryEraAreSlicedAndHashedFromTheirOriginalBytes() {
        Set<String> eras = new HashSet<>();
        Map<String, byte[]> submitted = submittedTxs();
        int comparedWithSubmitted = 0;
        for (RealCborFixtures.Block block : RealCborFixtures.blocks()) {
            eras.add(block.era());
            byte[] bytes = block.cbor();

            // [era, block]: the hard-fork combinator envelope
            CborSpan envelope = CborSpan.of(bytes);
            int era = (int) envelope.get(0).asLong();
            assertThat(era).as(block.toString()).isEqualTo(ERAS.get(block.era()));
            CborSpan blockSpan = envelope.get(1);

            // [header, tx bodies, witness sets, aux data map (, invalid txs from Alonzo)]
            List<CborSpan> parts = blockSpan.items();
            assertThat(parts).hasSize(era >= ALONZO ? 5 : 4);
            assertSlicesCover(bytes, envelope, blockSpan, parts);

            CborSpan header = parts.get(0);
            assertThat(encodeHexString(blake2bHash256(header.bytes()))).as(block.toString()).isEqualTo(block.blockHash());

            // Block body hash: blake2b256 of the concatenated blake2b256 hashes of each body segment
            List<CborSpan> bodySegments = parts.subList(1, parts.size());
            ByteArrayOutputStream segmentHashes = new ByteArrayOutputStream();
            int bodySize = 0;
            for (CborSpan segment : bodySegments) {
                segmentHashes.writeBytes(blake2bHash256(segment.bytes()));
                bodySize += segment.length();
            }
            CborSpan headerBody = header.get(0);
            int bodySizeIndex = era >= BABBAGE ? 6 : 7;
            assertThat(headerBody.get(bodySizeIndex).asLong()).as(block.toString()).isEqualTo(bodySize);
            assertThat(blake2bHash256(segmentHashes.toByteArray()))
                    .as(block.toString())
                    .isEqualTo(headerBody.get(bodySizeIndex + 1).byteString());

            // Tx ids are the hashes of the original body slices
            List<CborSpan> bodies = parts.get(1).items();
            List<CborSpan> witnesses = parts.get(2).items();
            assertThat(witnesses).hasSameSizeAs(bodies);
            List<String> txIds = new ArrayList<>();
            for (CborSpan body : bodies)
                txIds.add(encodeHexString(blake2bHash256(body.bytes())));
            assertThat(txIds).as(block.toString()).containsExactlyInAnyOrderElementsOf(block.txHashes());

            // Each transaction reassembled from the block equals the transaction as submitted
            Map<Long, CborSpan> auxData = auxDataByIndex(parts.get(3), bodies.size());
            Set<Long> invalid = new HashSet<>();
            if (era >= ALONZO)
                parts.get(4).items().forEach(index -> invalid.add(index.asLong()));
            for (int i = 0; i < bodies.size(); i++) {
                byte[] tx = assemble(era, bodies.get(i), witnesses.get(i), !invalid.contains((long) i), auxData.get((long) i));
                assertThat(TransactionUtil.getTxHash(tx)).isEqualTo(txIds.get(i));
                assertThat(new TransactionBytes(tx).getTxBytes()).isEqualTo(tx);
                if (submitted.containsKey(txIds.get(i))) {
                    assertThat(tx).as(block + " tx " + txIds.get(i)).isEqualTo(submitted.get(txIds.get(i)));
                    comparedWithSubmitted++;
                }
            }
        }
        assertThat(eras).containsAll(ERAS.keySet());
        assertThat(comparedWithSubmitted).isGreaterThanOrEqualTo(30);
    }

    @Test
    void triggerNativeScriptIsHashedFromItsOriginalBytes() {
        RealCborFixtures.Tx trigger = RealCborFixtures.trigger();
        CborSpan witnessSet = CborSpan.of(new TransactionBytes(trigger.cbor()).getTxWitnessBytes());
        CborSpan script = witnessSet.field(1).orElseThrow().untagIf(258).get(0);

        // Native script hash = blake2b224(0x00 || script bytes as encoded)
        byte[] preimage = BytesUtil.merge(new byte[]{0}, script.bytes());
        assertThat(encodeHexString(blake2bHash224(preimage))).isEqualTo(RealCborFixtures.TRIGGER_NATIVE_SCRIPT_HASH);

        // [1, [[1, [ ... ]]]]: count the ScriptAll levels down to the innermost script
        int levels = 0;
        while (script.get(0).asLong() == 1) {
            script = script.get(1).get(0);
            levels++;
        }
        assertThat(levels).isEqualTo(RealCborFixtures.TRIGGER_NATIVE_SCRIPT_LEVELS);
    }

    @Test
    void triggerBlockContainsTheTriggerTransaction() {
        RealCborFixtures.Tx trigger = RealCborFixtures.trigger();
        RealCborFixtures.Block block = RealCborFixtures.blocks().stream()
                .filter(b -> b.blockHeight() == trigger.blockHeight() && b.network().equals(trigger.network()))
                .findFirst().orElseThrow();
        assertThat(block.txHashes()).contains(trigger.txHash());
        // the trigger is 16,383 bytes as submitted ([body, witnesses, isValid, aux]); the ledger's size measure omits
        // the isValid flag, giving 16,382, just under the 16,384-byte limit
        assertThat(trigger.cbor()).hasSize(16_383);
    }

    // Every byte of the block belongs to exactly one slice, in order.
    private static void assertSlicesCover(byte[] bytes, CborSpan envelope, CborSpan blockSpan, List<CborSpan> parts) {
        assertThat(envelope.get(0).offset()).isEqualTo(envelope.headerLength());
        assertThat(blockSpan.offset()).isEqualTo(envelope.get(0).offset() + envelope.get(0).length());
        int pos = blockSpan.offset() + blockSpan.headerLength();
        for (CborSpan part : parts) {
            assertThat(part.buffer()).isSameAs(bytes);
            assertThat(part.offset()).isEqualTo(pos);
            pos += part.length();
        }
        assertThat(pos).isEqualTo(bytes.length);
    }

    private static Map<Long, CborSpan> auxDataByIndex(CborSpan auxMap, int txCount) {
        Map<Long, CborSpan> auxData = new TreeMap<>();
        for (Map.Entry<CborSpan, CborSpan> entry : auxMap.entries()) {
            long index = entry.getKey().asLong();
            assertThat(index).isBetween(0L, txCount - 1L);
            auxData.put(index, entry.getValue());
        }
        return auxData;
    }

    private static byte[] assemble(int era, CborSpan body, CborSpan witnesses, boolean valid, CborSpan auxData) {
        byte[] aux = auxData != null ? auxData.bytes() : new byte[]{(byte) 0xf6};
        if (era >= ALONZO)
            return BytesUtil.merge(new byte[]{(byte) 0x84}, body.bytes(), witnesses.bytes(),
                    new byte[]{(byte) (valid ? 0xf5 : 0xf4)}, aux);
        return BytesUtil.merge(new byte[]{(byte) 0x83}, body.bytes(), witnesses.bytes(), aux);
    }

    private static Map<String, byte[]> submittedTxs() {
        Map<String, byte[]> txs = new TreeMap<>();
        RealCborFixtures.allTxs().forEach(tx -> txs.put(tx.txHash(), tx.cbor()));
        return txs;
    }
}
