package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.util.RealCborFixturesAccess;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.bloxbean.cardano.client.crypto.Blake2bUtil.blake2bHash256;
import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RawBlock} on every real block (Shelley to Conway; mainnet, preprod, preview; the trigger block), checked against
 * on-chain values: the header's block body hash, the block hash and the TxIds; and in its three accepted forms.
 */
class RawBlockRealCorpusTest {

    @Test
    void blocksMatchTheOnChainValues() {
        Set<Era> eras = new HashSet<>();
        int invalidTxs = 0;
        for (RealCborFixturesAccess.Block block : RealCborFixturesAccess.blocks()) {
            RawBlock raw = RawBlock.of(block.cbor());
            Era era = raw.era();
            eras.add(era);
            assertThat(era.name()).as(block.name()).isEqualTo(block.era());
            assertThat(encodeHexString(blake2bHash256(raw.header().bytes()))).as(block.name()).isEqualTo(block.blockHash());

            // the header body carries the block body hash: index 8 (TPraos, Shelley to Alonzo), 7 (Praos, from Babbage)
            CborSpan headerBody = raw.header().get(0);
            int bodyHashIndex = era.value >= Era.Babbage.value ? 7 : 8;
            assertThat(raw.bodyHash()).as(block.name()).isEqualTo(headerBody.get(bodyHashIndex).byteString());

            // the header and the body parts are contiguous and cover the block
            List<CborSpan> parts = new ArrayList<>(List.of(raw.header(), raw.transactionBodies(), raw.transactionWitnessSets(),
                    raw.auxiliaryDataSet()));
            raw.invalidTransactions().ifPresent(parts::add);
            assertThat(raw.invalidTransactions().isPresent()).isEqualTo(era.value >= Era.Alonzo.value);
            for (int i = 1; i < parts.size(); i++)
                assertThat(parts.get(i).offset()).isEqualTo(parts.get(i - 1).offset() + parts.get(i - 1).length());
            CborSpan last = parts.get(parts.size() - 1);
            assertThat(last.offset() + last.length()).isEqualTo(block.cbor().length);

            List<String> txIds = new ArrayList<>();
            int[] invalid = raw.invalidTxIndexes();
            invalidTxs += invalid.length;
            for (int i = 0; i < raw.txCount(); i++) {
                RawTx tx = raw.tx(i);
                txIds.add(encodeHexString(tx.txId()));
                int txIndex = i;
                assertThat(tx.isValid()).isEqualTo(Arrays.stream(invalid).noneMatch(index -> index == txIndex));
                byte[] assembled = raw.txBytes(i);
                RawTx submitted = RawTx.of(assembled);
                assertThat(submitted.txId()).isEqualTo(tx.txId());
                assertThat(submitted.isValid()).isEqualTo(tx.isValid());
                assertThat(tx.span().bytes()).isEqualTo(assembled);
                assertThat(assembled[0]).isEqualTo(era.value >= Era.Alonzo.value ? (byte) 0x84 : (byte) 0x83);
                assertThat(tx.auxDataHash().map(hash -> encodeHexString(hash)))
                        .isEqualTo(tx.bodyField(7).map(field -> encodeHexString(field.byteString())));
            }
            assertThat(txIds).as(block.name()).containsExactlyInAnyOrderElementsOf(block.txHashes());

            assertSameBlock(raw, RawBlock.of(raw.header().buffer(), era));
            byte[] bare = CborSpan.of(block.cbor()).get(1).bytes();
            assertSameBlock(raw, RawBlock.of(bare, era));
            assertSameBlock(raw, RawBlock.of(embedded(block.cbor())));
            assertSameBlock(raw, RawBlock.of(embedded(bare), era));
        }
        assertThat(eras).containsExactlyInAnyOrder(Era.values());
        assertThat(invalidTxs).isPositive();
    }

    private static void assertSameBlock(RawBlock expected, RawBlock actual) {
        assertThat(actual.era()).isEqualTo(expected.era());
        assertThat(actual.bodyHash()).isEqualTo(expected.bodyHash());
        assertThat(actual.txCount()).isEqualTo(expected.txCount());
        assertThat(actual.invalidTxIndexes()).isEqualTo(expected.invalidTxIndexes());
        for (int i = 0; i < actual.txCount(); i++)
            assertThat(actual.txBytes(i)).isEqualTo(expected.txBytes(i));
    }

    // #6.24(bytes .cbor block)
    static byte[] embedded(byte[] cbor) {
        String head = cbor.length < 0x10000 ? String.format("59%04x", cbor.length) : String.format("5a%08x", cbor.length);
        return decodeHexString("d818" + head + encodeHexString(cbor));
    }
}
