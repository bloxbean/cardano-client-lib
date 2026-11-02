package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.util.RealCborFixturesAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

/**
 * Malformed input (ADR 0001 section 6.8): every real transaction cut at every byte, a seeded fuzz over mutated real
 * transactions and blocks with time and heap bounds, and declared sizes far beyond the input. The views either read the
 * input or reject it with a {@link CborRuntimeException}; nothing else escapes, and nothing hangs or overflows.
 */
class RawViewMalformedTest {

    @Test
    void everyTruncationIsRejected() {
        int cuts = 0;
        for (RealCborFixturesAccess.Tx tx : standaloneTxs()) {
            byte[] cbor = tx.cbor();
            for (int length = 0; length < cbor.length; length++) {
                byte[] cut = Arrays.copyOf(cbor, length);
                assertThatThrownBy(() -> RawTx.of(cut)).as("%s cut at %d", tx.name(), length).isInstanceOf(CborRuntimeException.class);
                cuts++;
            }
        }
        assertThat(cuts).isGreaterThan(100_000);
    }

    @Test
    void fuzzedTransactionsAndBlocksAreReadOrRejected() {
        List<byte[]> txs = standaloneTxs().stream().map(RealCborFixturesAccess.Tx::cbor).collect(Collectors.toList());
        List<byte[]> blocks = RealCborFixturesAccess.blocks().stream().map(RealCborFixturesAccess.Block::cbor)
                .filter(block -> block.length < 20_000).collect(Collectors.toList());
        Random random = new Random(681_688);
        long deadline = System.nanoTime() + 120_000_000_000L;
        int read = 0;
        for (int i = 0; i < 20_000; i++) {
            boolean block = i % 5 == 0;
            byte[] input = mutate(block ? blocks.get(random.nextInt(blocks.size())) : txs.get(random.nextInt(txs.size())), random);
            long allocatedBefore = allocatedBytes();
            try {
                if (block)
                    readBlock(RawBlock.of(input));
                else
                    readTx(RawTx.of(input));
                read++;
            } catch (CborRuntimeException expected) {
                // rejected
            } catch (RuntimeException | StackOverflowError e) {
                fail("unexpected " + e + " on fuzz input " + i, e);
            }
            // reading decodes every model, but nothing is allocated from a declared size
            assertThat(allocatedBytes() - allocatedBefore).as("allocation on fuzz input %d", i).isLessThan(512L * input.length + 4_000_000);
            assertThat(System.nanoTime()).as("fuzz loop exceeded its time budget").isLessThan(deadline);
        }
        assertThat(read).isGreaterThan(100);
    }

    @ParameterizedTest
    @ValueSource(strings = {"9bffffffffffffffff", "9a7fffffff00", "84" + "bb0000000100000000", "83" + "a0" + "a0" + "5b7fffffffffffffff",
            "83" + "a0" + "a0" + "7a7fffffff"})
    void declaredSizesBeyondTheInputAreRejectedBeforeAllocating(String hex) {
        byte[] input = decodeHexString(hex);
        assertThatThrownBy(() -> RawTx.of(input)).isInstanceOf(CborRuntimeException.class); // loads the classes
        long allocatedBefore = allocatedBytes();
        assertThatThrownBy(() -> RawTx.of(input)).isInstanceOf(CborRuntimeException.class);
        assertThat(allocatedBytes() - allocatedBefore).isLessThan(64 * 1024);
    }

    // Everything a caller can read from a transaction, decoding included.
    private static void readTx(RawTx tx) {
        tx.txId();
        tx.auxDataHash();
        tx.isValid();
        tx.vkeyWitnesses();
        tx.bootstrapWitnesses();
        for (RawOutput output : tx.outputs()) {
            output.inlineDatum().ifPresent(datum -> decode(datum::toPlutusData, datum.hash()));
            output.scriptRef().ifPresent(script -> decode(script::toScript, script.hash()));
        }
        tx.collateralReturn();
        tx.witnessDatums().forEach(datum -> decode(datum::toPlutusData, datum.hash()));
        tx.redeemers().forEach(redeemer -> decode(new RawDatum(redeemer.data())::toPlutusData, null));
        tx.scripts().forEach(script -> decode(script::toScript, script.hash()));
        tx.auxScripts().forEach(script -> decode(script::toScript, script.hash()));
        tx.scriptDataHash(Era.Conway, decodeHexString("a0"));
        tx.span();
    }

    private static void readBlock(RawBlock block) {
        block.bodyHash();
        block.invalidTxIndexes();
        for (int i = 0; i < block.txCount(); i++) {
            readTx(block.tx(i));
            block.txBytes(i);
        }
    }

    private interface Decode {
        Object run() throws CborDeserializationException;
    }

    // A model decode may also reject what is not Plutus data or a script of its type.
    private static void decode(Decode decode, byte[] hash) {
        try {
            decode.run();
        } catch (CborDeserializationException expected) {
            // not data or not a script
        }
    }

    private static List<RealCborFixturesAccess.Tx> standaloneTxs() {
        return RealCborFixturesAccess.allTxsAndBlockTxs().stream().filter(tx -> !tx.name().startsWith("block "))
                .collect(Collectors.toList());
    }

    private static byte[] mutate(byte[] original, Random random) {
        byte[] bytes = original.clone();
        int mutations = 1 + random.nextInt(4);
        for (int m = 0; m < mutations && bytes.length > 0; m++) {
            int at = random.nextInt(bytes.length);
            switch (random.nextInt(4)) {
                case 0:
                    bytes[at] = (byte) random.nextInt(256);
                    break;
                case 1:
                    bytes[at] ^= (byte) (1 << random.nextInt(8));
                    break;
                case 2:
                    bytes = Arrays.copyOf(bytes, at);
                    break;
                default:
                    byte[] longer = new byte[bytes.length + 1];
                    System.arraycopy(bytes, 0, longer, 0, at);
                    longer[at] = (byte) random.nextInt(256);
                    System.arraycopy(bytes, at, longer, at + 1, bytes.length - at);
                    bytes = longer;
                    break;
            }
        }
        return bytes;
    }

    private static long allocatedBytes() {
        return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes();
    }
}
