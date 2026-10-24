package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.util.RealCborFixturesAccess;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static com.bloxbean.cardano.client.crypto.Blake2bUtil.blake2bHash256;
import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RawBlockBody}: a block built from transactions carries each one byte for byte, whatever valid encoding it
 * uses, which {@link RawBlock} confirms by reading the block back; and real blocks rebuilt from their transactions are
 * the original blocks.
 */
class RawBlockBodyTest {
    private static final String HEADER = "82" + "80" + "40"; // stands in for [header_body, signature]
    private static final String BODY = "a3" + "0080" + "0180" + "0200"; // {0: [], 1: [], 2: 0}
    private static final String WITNESSES = "a0";

    @Test
    void realBlocksRebuiltFromTheirTransactionsAreTheOriginalBlocks() {
        int blocks = 0;
        for (RealCborFixturesAccess.Block block : RealCborFixturesAccess.blocks()) {
            RawBlock original = RawBlock.of(block.cbor());
            List<byte[]> txs = IntStream.range(0, original.txCount()).mapToObj(original::txBytes).toList();

            RawBlockBody body = RawBlockBody.of(original.era(), txs);

            assertThat(body.hash()).as(block.name()).isEqualTo(original.bodyHash());
            assertThat(body.size()).as(block.name()).isEqualTo(bodySize(original));
            assertThat(body.invalidTxIndexes()).as(block.name()).isEqualTo(original.invalidTxIndexes());
            assertThat(body.block(original.header().bytes())).as(block.name()).isEqualTo(block.cbor());
            blocks++;
        }
        assertThat(blocks).isPositive();
    }

    @Test
    void chunkedMetadataStringsStayChunked() {
        // metadata {1: (_ h'00..'(64), h'00..'(36))}: valid, each chunk at most 64 bytes; merged it would be 100 bytes,
        // which the ledger's decoder rejects ("ByteString exceeds 64 bytes")
        String chunkedBytes = "5f" + "5840" + "00".repeat(64) + "5824" + "00".repeat(36) + "ff";
        String chunkedText = "7f" + "7840" + "61".repeat(64) + "6461616161" + "ff";
        assertRoundTrip(Era.Conway, tx(BODY, WITNESSES, true, "a2" + "01" + chunkedBytes + "02" + chunkedText));
    }

    @Test
    void integersNotInTheirShortestFormKeepTheTxId() {
        // fee 5 in a 4-byte argument, an output amount 1 in an 8-byte one
        String body = "a3" + "0080" + "0181" + "82" + "581d61" + "00".repeat(28) + "1b0000000000000001" + "02"
                + "1a00000005";
        assertRoundTrip(Era.Conway, tx(body, WITNESSES, true, null));
    }

    @Test
    void indefiniteLengthContainersAndSetTagsAreKept() {
        String input = "82" + "5820" + "11".repeat(32) + "00";
        String body = "bf" + "00" + "d90102" + "9f" + input + "ff" + "01" + "9f" + "ff" + "02" + "00" + "ff";
        String witnesses = "bf" + "01" + "9f" + "8200" + "581c" + "22".repeat(28) + "ff" + "ff";
        String aux = "d90103" + "bf" + "00" + "bf" + "01" + "9f" + "01" + "02" + "ff" + "ff" + "ff";
        assertRoundTrip(Era.Conway, tx(body, witnesses, true, aux));
    }

    @Test
    void mapKeysOutOfOrderAreKept() {
        assertRoundTrip(Era.Conway, tx("a3" + "0200" + "0180" + "0080", WITNESSES, true, "a2" + "02" + "00" + "01" + "00"));
    }

    @Test
    void aTransactionArrayEncodedIndefinitelyIsRead() {
        assertRoundTrip(Era.Conway, "9f" + BODY + WITNESSES + "f5" + "f6" + "ff");
    }

    @Test
    void auxDataIsKeyedByTransactionIndexBeyondOneByteHeads() {
        List<String> txs = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            String body = "a3" + "0080" + "0180" + "02" + "19" + String.format("%04x", i); // distinct fees
            txs.add(tx(body, WITNESSES, true, i % 7 == 0 ? "a1" + "00" + "00" : null));
        }
        RawBlock raw = assertRoundTrip(Era.Conway, txs.toArray(String[]::new));
        assertThat(raw.auxiliaryDataSet().size()).isEqualTo(43);
        assertThat(raw.tx(294).auxData()).isPresent(); // key 294: a 2-byte argument
    }

    @Test
    void containersAreDefiniteUpTo23EntriesAndIndefiniteAbove() {
        for (int count : new int[]{23, 24}) {
            String[] txs = new String[count];
            for (int i = 0; i < count; i++)
                txs[i] = tx(BODY, WITNESSES, i % 2 == 0, "a0");
            RawBlock raw = assertRoundTrip(Era.Conway, txs);
            int auxEntries = count, invalidEntries = count / 2;
            assertThat(raw.transactionBodies().isIndefinite()).as("bodies of %d", count).isEqualTo(count > 23);
            assertThat(raw.transactionWitnessSets().isIndefinite()).isEqualTo(count > 23);
            assertThat(raw.auxiliaryDataSet().isIndefinite()).isEqualTo(auxEntries > 23);
            assertThat(raw.invalidTransactions().orElseThrow().isIndefinite()).isEqualTo(invalidEntries > 23);
        }
    }

    @Test
    void invalidTransactionsAreListedFromAlonzo() {
        RawBlock raw = assertRoundTrip(Era.Conway, tx(BODY, WITNESSES, true, null), tx(BODY, WITNESSES, false, null),
                tx(BODY, WITNESSES, false, "a0"));
        assertThat(raw.invalidTransactions().orElseThrow().bytes()).isEqualTo(decodeHexString("82" + "01" + "02"));
        assertThat(RawBlockBody.of(Era.Babbage, List.of(decodeHexString(tx(BODY, WITNESSES, false, null))))
                .invalidTxIndexes()).containsExactly(0);
    }

    @Test
    void erasBeforeAlonzoHaveThreeBodyPartsAndCannotMarkInvalidTransactions() {
        String shelleyTx = "83" + BODY + WITNESSES + "f6";
        RawBlock raw = assertRoundTrip(Era.Mary, shelleyTx, tx(BODY, WITNESSES, true, "a0"));
        assertThat(raw.invalidTransactions()).isEmpty();

        assertThatThrownBy(() -> RawBlockBody.of(Era.Mary, List.of(decodeHexString(tx(BODY, WITNESSES, false, null)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("invalid");
    }

    @Test
    void anEmptyBody() {
        RawBlockBody body = RawBlockBody.of(Era.Conway, List.of());
        byte[] block = body.block(decodeHexString(HEADER));
        assertThat(encodeHexString(block)).isEqualTo("82" + "07" + "85" + HEADER + "80" + "80" + "a0" + "80");
        assertThat(body.size()).isEqualTo(4);
        assertThat(encodeHexString(body.hash())).isEqualTo(bodyHash("80", "80", "a0", "80"));
    }

    @Test
    void malformedInputIsRejected() {
        assertThatThrownBy(() -> RawBlockBody.of(Era.Conway, List.of(decodeHexString("84" + BODY))))
                .isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawBlockBody.of(Era.Conway, List.of(decodeHexString("a0"))))
                .isInstanceOf(CborRuntimeException.class);
        RawBlockBody body = RawBlockBody.of(Era.Conway, List.of());
        assertThatThrownBy(() -> body.block(decodeHexString(HEADER + "00"))).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawBlockBody.of(null, Collections.emptyList())).isInstanceOf(NullPointerException.class);
    }

    /**
     * Builds a block of {@code txs}, reads it back and checks every transaction byte for byte, the body hash against an
     * independent computation, and the body size.
     */
    private static RawBlock assertRoundTrip(Era era, String... txs) {
        List<byte[]> transactions = new ArrayList<>();
        for (String tx : txs)
            transactions.add(decodeHexString(tx));
        RawBlockBody body = RawBlockBody.of(era, transactions);

        RawBlock raw = RawBlock.of(body.block(decodeHexString(HEADER)));

        assertThat(raw.era()).isEqualTo(era);
        assertThat(raw.header().bytes()).isEqualTo(decodeHexString(HEADER));
        assertThat(raw.txCount()).isEqualTo(txs.length);
        for (int i = 0; i < txs.length; i++) {
            RawTx submitted = RawTx.of(transactions.get(i));
            RawTx inBlock = raw.tx(i);
            assertThat(inBlock.body().bytes()).as("body %d", i).isEqualTo(submitted.body().bytes());
            assertThat(inBlock.witnessSet().bytes()).as("witnesses %d", i).isEqualTo(submitted.witnessSet().bytes());
            assertThat(inBlock.auxData().map(aux -> encodeHexString(aux.bytes()))).as("aux data %d", i)
                    .isEqualTo(submitted.auxData().map(aux -> encodeHexString(aux.bytes())));
            assertThat(inBlock.isValid()).as("isValid %d", i).isEqualTo(submitted.isValid());
            assertThat(inBlock.txId()).as("tx id %d", i).isEqualTo(submitted.txId());
        }
        assertThat(raw.bodyHash()).isEqualTo(body.hash());
        assertThat(body.size()).isEqualTo(bodySize(raw));
        List<String> parts = new ArrayList<>(List.of(encodeHexString(raw.transactionBodies().bytes()),
                encodeHexString(raw.transactionWitnessSets().bytes()), encodeHexString(raw.auxiliaryDataSet().bytes())));
        raw.invalidTransactions().ifPresent(invalid -> parts.add(encodeHexString(invalid.bytes())));
        assertThat(encodeHexString(body.hash())).isEqualTo(bodyHash(parts.toArray(String[]::new)));
        assertThat(body.invalidTxIndexes()).isEqualTo(raw.invalidTxIndexes());
        return raw;
    }

    private static String tx(String body, String witnesses, boolean valid, String aux) {
        return "84" + body + witnesses + (valid ? "f5" : "f4") + (aux == null ? "f6" : aux);
    }

    private static long bodySize(RawBlock raw) {
        long size = raw.transactionBodies().length() + raw.transactionWitnessSets().length()
                + raw.auxiliaryDataSet().length();
        return size + raw.invalidTransactions().map(CborSpan::length).orElse(0);
    }

    // blake2b256 of the concatenated blake2b256 of each part
    private static String bodyHash(String... parts) {
        ByteArrayOutputStream hashes = new ByteArrayOutputStream();
        for (String part : parts)
            hashes.writeBytes(blake2bHash256(decodeHexString(part)));
        return encodeHexString(blake2bHash256(hashes.toByteArray()));
    }
}
