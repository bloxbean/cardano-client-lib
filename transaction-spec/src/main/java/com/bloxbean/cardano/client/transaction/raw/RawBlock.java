package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.io.ByteArrayOutputStream;
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
 * optionally wrapped in tag 24. The era decides the rules that differ between eras ({@code EraRules}). Checked when the
 * view is created, as the ledger's decoder checks them (D6):
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
    private static final int EMBEDDED_CBOR = 24;

    private final Era era;
    private final EraRules rules;
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
        this.rules = EraRules.of(era);
        if (block.majorType() != 4 || block.tag() != -1)
            throw error("A block is an untagged array", block);
        List<CborSpan> parts = block.items();
        if (parts.size() == 2)
            throw error("Dijkstra blocks ([header, block_body]) are not supported", block);
        if (parts.size() != rules.blockBodyParts() + 1)
            throw error("A " + era + " block has " + (rules.blockBodyParts() + 1) + " items, found " + parts.size(), block);
        header = parts.get(0);
        transactionBodies = parts.get(1);
        transactionWitnessSets = parts.get(2);
        auxiliaryDataSet = parts.get(3);
        invalidTransactions = rules.invalidTransactions ? parts.get(4) : null;
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

    private static RawBlock parse(byte[] bytes, Era expected) {
        CborSpan top = CborSpan.of(bytes);
        if (top.tag() == EMBEDDED_CBOR)
            top = top.embedded();
        // the envelope [era, block] is the one block-level array of two items with an integer first
        boolean envelope = top.majorType() == 4 && top.tag() == -1 && top.size() == 2 && top.get(0).majorType() == 0;
        if (!envelope) {
            if (expected == null)
                throw error("A block without its [era, block] envelope needs its era: use RawBlock.of(bytes, era)", top);
            return new RawBlock(expected, top);
        }
        long index = top.get(0).asLong();
        if (index == 0 || index == 1)
            throw error("Byron blocks (era " + index + ") are not supported", top);
        if (index == 8)
            throw error("Dijkstra blocks (era 8) are not supported", top);
        Era era;
        try {
            era = Era.fromValue((int) Math.min(index, Integer.MAX_VALUE));
        } catch (IllegalArgumentException e) {
            throw error("Unknown era " + index, top);
        }
        if (expected != null && expected != era)
            throw error("The envelope's era is " + era + ", expected " + expected, top);
        return new RawBlock(era, top.get(1));
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
        return RawTx.inBlock(bodies.get(i), witnessSets.get(i), auxData[i], !isInvalid(i), rules.invalidTransactions);
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
        ByteArrayOutputStream hashes = new ByteArrayOutputStream(32 * rules.blockBodyParts());
        hashes.writeBytes(Blake2bUtil.blake2bHash256(transactionBodies.bytes()));
        hashes.writeBytes(Blake2bUtil.blake2bHash256(transactionWitnessSets.bytes()));
        hashes.writeBytes(Blake2bUtil.blake2bHash256(auxiliaryDataSet.bytes()));
        if (invalidTransactions != null)
            hashes.writeBytes(Blake2bUtil.blake2bHash256(invalidTransactions.bytes()));
        return Blake2bUtil.blake2bHash256(hashes.toByteArray());
    }

    private boolean isInvalid(int i) {
        return Arrays.binarySearch(invalidIndexes, i) >= 0;
    }
}
