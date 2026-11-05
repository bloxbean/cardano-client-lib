package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.bloxbean.cardano.client.transaction.raw.RawTx.error;

/**
 * A Shelley-family block as received, read from its original bytes (ADR 0001 D2): the header, the transaction bodies,
 * witness sets, aux data map and, from Alonzo, invalid transactions, as spans of the caller's buffer; each transaction as
 * a {@link RawTx}; and the block body hash.
 * <p>
 * Accepts the hard-fork combinator envelope {@code [era, block]}, or the bare block with its era given, either one
 * optionally wrapped in tag 24; or a {@link BlockEnvelope}. The era decides the layouts and the rules that differ
 * between eras ({@code EraSpec}). Checked when the view is created, as the ledger's decoder checks them (D6):
 * <ul>
 *     <li>as many witness sets as transaction bodies;</li>
 *     <li>aux data map keys are transaction indexes; a repeated index keeps the last value until Babbage and is
 *     rejected in Conway;</li>
 *     <li>{@code invalid_transactions} are strictly ascending transaction indexes.</li>
 * </ul>
 * A transaction's records are checked when it is read with {@link #tx(int)}. Byron blocks (era 0 and 1) and Dijkstra
 * blocks (era 8, {@code [header, block_body]}) are not supported.
 */
public final class RawBlock {
    private final Era era;
    private final EraSpec rules;
    private final CborSpan header;
    private final CborSpan transactionBodies;
    private final CborSpan transactionWitnessSets;
    private final CborSpan auxiliaryDataSet;
    private final CborSpan invalidTransactions;
    private final List<CborSpan> bodies;
    private final List<CborSpan> witnessSets;
    private final CborSpan[] auxData;
    private final int[] invalidIndexes;

    private RawBlock(Era era, CborSpan block) {
        this.era = era;
        this.rules = EraSpec.of(era);
        if (block.majorType() != 4 || block.tag() != -1)
            throw error("A block is an untagged array", block);
        List<CborSpan> parts = block.items();
        if (parts.size() == 2)
            throw error("Dijkstra blocks ([header, block_body]) are not supported", block);
        if (parts.size() != rules.blockLayout.items)
            throw error("A " + era + " block has " + rules.blockLayout.items + " items, found " + parts.size(), block);
        header = parts.get(0);
        transactionBodies = parts.get(1);
        transactionWitnessSets = parts.get(2);
        auxiliaryDataSet = parts.get(3);
        invalidTransactions = rules.blockLayout.invalidTransactions() ? parts.get(4) : null;
        bodies = transactionBodies.items();
        witnessSets = transactionWitnessSets.items();
        if (bodies.size() != witnessSets.size())
            throw error(bodies.size() + " transaction bodies but " + witnessSets.size() + " witness sets", block);
        auxData = auxDataByIndex();
        invalidIndexes = invalidIndexes();
    }

    /**
     * @param blockCbor {@code [era, block]}, optionally in tag 24
     * @return the view
     * @throws CborRuntimeException if the bytes are not well-formed CBOR or not an enveloped Shelley-family block, or break
     *                              a rule above
     */
    public static RawBlock of(byte[] blockCbor) {
        return parse(blockCbor, null);
    }

    /**
     * @param blockCbor a bare block, or {@code [era, block]} whose era must be {@code era}, optionally in tag 24
     * @param era       the block's era
     * @return the view
     * @throws CborRuntimeException if the bytes are not well-formed CBOR or not a block of that era, or break a rule above
     */
    public static RawBlock of(byte[] blockCbor, Era era) {
        if (era == null)
            throw new IllegalArgumentException("era is null");
        return parse(blockCbor, era);
    }

    /**
     * @param envelope a block in its envelope
     * @return the view, of the era the envelope names
     * @throws UnsupportedEraException if the envelope's era index has no {@link EraSpec} row
     * @throws CborRuntimeException    if the bytes are not a block of that era, or break a rule above
     */
    public static RawBlock of(BlockEnvelope envelope) {
        return new RawBlock(EraSpec.ofIndex(envelope.eraIndex()).era, envelope.block());
    }

    /**
     * Reads a block with a named era's layouts and field tables, whatever index the envelope carries. This is always
     * the caller's choice, for an era it does not know: a key whose meaning changed in that era is misread, and
     * {@link #of} never falls back to this. {@link #era()} is the named era, and {@link BlockEnvelope#eraIndex()} stays
     * the fact.
     *
     * @param envelope a block in its envelope
     * @param era      the era to read it as, such as {@link EraSpec#newest()}
     * @return the view
     * @throws CborRuntimeException if the bytes are not a block of that era, or break a rule above
     */
    public static RawBlock readAs(BlockEnvelope envelope, Era era) {
        if (era == null)
            throw new IllegalArgumentException("era is null");
        return new RawBlock(era, envelope.block());
    }

    private static RawBlock parse(byte[] bytes, Era expected) {
        CborSpan top = BlockEnvelope.content(CborSpan.of(bytes));
        if (!BlockEnvelope.isEnvelope(top)) {
            if (expected == null)
                throw error("A block without its [era, block] envelope needs its era: use RawBlock.of(bytes, era)", top);
            return new RawBlock(expected, top);
        }
        BlockEnvelope envelope = BlockEnvelope.of(top);
        Era era = EraSpec.ofIndex(envelope.eraIndex()).era;
        if (expected != null && expected != era)
            throw error("The envelope's era is " + era + ", expected " + expected, top);
        return new RawBlock(era, envelope.block());
    }

    private CborSpan[] auxDataByIndex() {
        CborSpan[] byIndex = new CborSpan[bodies.size()];
        for (Map.Entry<CborSpan, CborSpan> entry : auxiliaryDataSet.entries()) {
            CborSpan key = entry.getKey();
            if (key.majorType() != 0 || key.tag() != -1)
                throw error("An aux data map key is a transaction index", key);
            long index = key.asLong();
            if (index >= bodies.size())
                throw error("Aux data for transaction " + index + " of " + bodies.size(), key);
            if (byIndex[(int) index] != null && rules.rejectsDuplicateAuxIndex)
                throw error("Aux data for transaction " + index + " is repeated", key);
            byIndex[(int) index] = entry.getValue();
        }
        return byIndex;
    }

    private int[] invalidIndexes() {
        if (invalidTransactions == null)
            return new int[0];
        List<CborSpan> items = invalidTransactions.items();
        int[] indexes = new int[items.size()];
        for (int i = 0; i < indexes.length; i++) {
            CborSpan item = items.get(i);
            long index = item.asLong();
            if (index < 0 || index >= bodies.size())
                throw error("Invalid transaction index " + index + " of " + bodies.size(), item);
            if (i > 0 && index <= indexes[i - 1])
                throw error("Invalid transaction indexes must be strictly ascending, found " + index + " after " + indexes[i - 1], item);
            indexes[i] = (int) index;
        }
        return indexes;
    }

    public Era era() {
        return era;
    }

    public CborSpan header() {
        return header;
    }

    /**
     * @return the header, read with this era's layout when this is called: the block's other checks do not look into it
     * @throws CborRuntimeException if the header does not have the items of the layout
     */
    public RawHeader rawHeader() {
        return RawHeader.of(header, era);
    }

    public CborSpan transactionBodies() {
        return transactionBodies;
    }

    public CborSpan transactionWitnessSets() {
        return transactionWitnessSets;
    }

    /**
     * @return the aux data map, {@code {* transaction_index => auxiliary_data}}
     */
    public CborSpan auxiliaryDataSet() {
        return auxiliaryDataSet;
    }

    /**
     * @return the indexes of the transactions that failed phase-2 validation (Alonzo on), as encoded
     */
    public Optional<CborSpan> invalidTransactions() {
        return Optional.ofNullable(invalidTransactions);
    }

    public int txCount() {
        return bodies.size();
    }

    /**
     * @param i the transaction's index in the block
     * @return the transaction, valid unless {@code i} is in {@link #invalidTxIndexes()}
     * @throws CborRuntimeException if one of its records repeats a key
     */
    public RawTx tx(int i) {
        return RawTx.inBlock(bodies.get(i), witnessSets.get(i), auxData[i], !isInvalid(i),
                rules.blockLayout.invalidTransactions());
    }

    /**
     * Assembles a transaction as it was submitted: {@code [body, witnesses, isValid, aux / null]} from Alonzo, and
     * {@code [body, witnesses, aux / null]} before.
     *
     * @param i the transaction's index in the block
     * @return the transaction bytes, a new buffer
     */
    public byte[] txBytes(int i) {
        return tx(i).assemble();
    }

    /**
     * @return the indexes of the transactions that failed phase-2 validation, ascending; none before Alonzo
     */
    public int[] invalidTxIndexes() {
        return invalidIndexes.clone();
    }

    /**
     * @return the block body hash: {@code blake2b256} of the concatenated {@code blake2b256} hashes of the transaction
     * bodies, witness sets, aux data map and, from Alonzo, invalid transactions, each as encoded (shelley and alonzo
     * {@code BlockBody/Internal.hs}); the block header carries it
     */
    public byte[] bodyHash() {
        List<byte[]> parts = new ArrayList<>(List.of(transactionBodies.bytes(), transactionWitnessSets.bytes(),
                auxiliaryDataSet.bytes()));
        if (invalidTransactions != null)
            parts.add(invalidTransactions.bytes());
        return bodyHash(parts);
    }

    // The block body hash of the encoded body parts, in block order (shared with RawBlockBody).
    static byte[] bodyHash(List<byte[]> parts) {
        ByteArrayOutputStream hashes = new ByteArrayOutputStream(32 * parts.size());
        for (byte[] part : parts)
            hashes.writeBytes(Blake2bUtil.blake2bHash256(part));
        return Blake2bUtil.blake2bHash256(hashes.toByteArray());
    }

    private boolean isInvalid(int i) {
        return Arrays.binarySearch(invalidIndexes, i) >= 0;
    }
}
