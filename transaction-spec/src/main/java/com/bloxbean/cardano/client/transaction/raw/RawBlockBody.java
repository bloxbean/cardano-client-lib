package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A Shelley-family block body built from transactions as submitted, for a block producer: the inverse of
 * {@link RawBlock}. Each transaction's body, witness set and aux data go into the block as their original bytes, never
 * re-serialised, so the block carries every transaction exactly as it was signed: its id, its aux data hash and its
 * witnesses all still match. A re-serialisation can change valid encodings the ledger accepts, such as a chunked
 * metadata string (each chunk at most 64 bytes, the whole longer), an integer not in its shortest form or an
 * indefinite-length array, and the block then fails to decode or validate.
 * <p>
 * The body is {@code [transaction_bodies, transaction_witness_sets, auxiliary_data_set (, invalid_transactions)]}: aux
 * data keyed by transaction index in ascending order and, from Alonzo, the indexes of the transactions whose
 * {@code isValid} flag is false. Each array and the map is encoded as the ledger encodes a block body, definite-length
 * up to 23 entries and indefinite-length above ({@code variableListLenEncoding}, {@code variableMapLenEncoding},
 * cardano-ledger-binary {@code Encoder.hs}), so the body is the one cardano-node builds from the same transactions.
 */
public final class RawBlockBody {
    private final Era era;
    private final List<byte[]> parts;
    private final int[] invalidIndexes;

    private RawBlockBody(Era era, List<byte[]> parts, int[] invalidIndexes) {
        this.era = era;
        this.parts = parts;
        this.invalidIndexes = invalidIndexes;
    }

    /**
     * @param era          the block's era
     * @param transactions each {@code [body, witnesses, isValid, aux / null]}, or {@code [body, witnesses, aux / null]},
     *                     in block order
     * @return the block body
     * @throws CborRuntimeException     if a transaction is not well-formed or not a Shelley-family transaction
     * @throws IllegalArgumentException if the era has no raw view rules, or a transaction is invalid ({@code isValid}
     *                                  false) in an era before Alonzo, whose blocks cannot mark it
     */
    public static RawBlockBody of(Era era, List<byte[]> transactions) {
        EraRules rules = EraRules.of(Objects.requireNonNull(era, "era is null"));
        Objects.requireNonNull(transactions, "transactions is null");
        int count = transactions.size();
        List<RawTx> txs = new ArrayList<>(count);
        int auxCount = 0;
        for (byte[] transaction : transactions) {
            RawTx tx = RawTx.of(transaction);
            txs.add(tx);
            if (tx.auxData().isPresent())
                auxCount++;
        }

        ByteArrayOutputStream bodies = new ByteArrayOutputStream();
        ByteArrayOutputStream witnessSets = new ByteArrayOutputStream();
        ByteArrayOutputStream auxData = new ByteArrayOutputStream();
        List<Integer> invalid = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RawTx tx = txs.get(i);
            bodies.writeBytes(tx.body().bytes());
            witnessSets.writeBytes(tx.witnessSet().bytes());
            int index = i;
            tx.auxData().ifPresent(aux -> {
                head(auxData, 0, index);
                auxData.writeBytes(aux.bytes());
            });
            if (!tx.isValid())
                invalid.add(i);
        }

        List<byte[]> parts = new ArrayList<>(List.of(container(4, count, bodies), container(4, count, witnessSets),
                container(5, auxCount, auxData)));
        if (rules.invalidTransactions) {
            ByteArrayOutputStream invalidTransactions = new ByteArrayOutputStream();
            invalid.forEach(index -> head(invalidTransactions, 0, index));
            parts.add(container(4, invalid.size(), invalidTransactions));
        } else if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("Transaction " + invalid.get(0) + " is invalid (isValid false); a " + era
                    + " block has no invalid_transactions");
        }
        return new RawBlockBody(era, List.copyOf(parts), invalid.stream().mapToInt(Integer::intValue).toArray());
    }

    public Era era() {
        return era;
    }

    /**
     * @return the block body size the header declares: the encoded size of the body parts
     */
    public long size() {
        long size = 0;
        for (byte[] part : parts)
            size += part.length;
        return size;
    }

    /**
     * @return the block body hash the header declares, as {@link RawBlock#bodyHash()} computes it
     */
    public byte[] hash() {
        return RawBlock.bodyHash(parts);
    }

    /**
     * @return the indexes of the transactions whose {@code isValid} flag is false, ascending
     */
    public int[] invalidTxIndexes() {
        return invalidIndexes.clone();
    }

    /**
     * @param header the block header, {@code [header_body, body_signature]}, which declares this body's
     *               {@link #size()} and {@link #hash()}
     * @return the block in its hard-fork combinator envelope, {@code [era, [header, body parts...]]}, which
     * {@link RawBlock#of(byte[])} reads back
     * @throws CborRuntimeException if the header is not a single well-formed CBOR item
     */
    public byte[] block(byte[] header) {
        CborSpan headerSpan = CborSpan.of(Objects.requireNonNull(header, "header is null"));
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(Integer.MAX_VALUE,
                size() + headerSpan.length() + 16));
        head(out, 4, 2);
        head(out, 0, era.value);
        head(out, 4, parts.size() + 1L);
        out.writeBytes(headerSpan.bytes());
        parts.forEach(out::writeBytes);
        return out.toByteArray();
    }

    // An array (major type 4) or a map (5) of entries already encoded: definite-length up to 23 entries, indefinite
    // above, as the ledger's variable length encoding
    private static byte[] container(int major, int entries, ByteArrayOutputStream contents) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(contents.size() + 9);
        boolean definite = entries <= 23;
        if (definite)
            head(out, major, entries);
        else
            out.write(major << 5 | 31);
        out.writeBytes(contents.toByteArray());
        if (!definite)
            out.write(0xff);
        return out.toByteArray();
    }

    // The shortest CBOR head of a major type with a non-negative argument (RFC 8949 section 3).
    private static void head(ByteArrayOutputStream out, int major, long argument) {
        int type = major << 5;
        if (argument < 24) {
            out.write(type | (int) argument);
        } else if (argument < 0x100) {
            out.write(type | 24);
            out.write((int) argument);
        } else if (argument < 0x10000) {
            out.write(type | 25);
            out.write((int) (argument >>> 8));
            out.write((int) argument);
        } else if (argument < 0x1_0000_0000L) {
            out.write(type | 26);
            for (int shift = 24; shift >= 0; shift -= 8)
                out.write((int) (argument >>> shift));
        } else {
            out.write(type | 27);
            for (int shift = 56; shift >= 0; shift -= 8)
                out.write((int) (argument >>> shift));
        }
    }
}
