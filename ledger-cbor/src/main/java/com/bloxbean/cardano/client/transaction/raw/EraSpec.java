package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.spec.Era;

import static com.bloxbean.cardano.client.transaction.raw.BlockLayout.PARALLEL_4;
import static com.bloxbean.cardano.client.transaction.raw.BlockLayout.PARALLEL_5;
import static com.bloxbean.cardano.client.transaction.raw.HeaderLayout.FLAT_15;
import static com.bloxbean.cardano.client.transaction.raw.HeaderLayout.NESTED_10;

/**
 * The rules of the ledger format that depend on the era (ADR 0001 D8, ADR 0003 D3), in one table: the only place where
 * the reader branches on the era, apart from the field tables. Each era names its block and header layouts, each
 * implemented once. Everything else is the same in every Shelley-family era: records are read by key and unknown keys
 * are passed through, a duplicate record key is rejected, tag 258 is accepted on sets, and both redeemer forms and
 * every aux data shape are read wherever they occur, as the ledger's decoders of later eras still accept the earlier
 * shapes.
 * <p>
 * Adding an era means adding its {@link Era} constant and its row here.
 */
public enum EraSpec {
    //       era          block       header     duplicate aux index  absent redeemers
    SHELLEY(Era.Shelley, PARALLEL_4, FLAT_15, false, null),
    ALLEGRA(Era.Allegra, PARALLEL_4, FLAT_15, false, null),
    MARY(Era.Mary, PARALLEL_4, FLAT_15, false, null),
    ALONZO(Era.Alonzo, PARALLEL_5, FLAT_15, false, new byte[]{(byte) 0x80}),
    BABBAGE(Era.Babbage, PARALLEL_5, NESTED_10, false, new byte[]{(byte) 0x80}),
    CONWAY(Era.Conway, PARALLEL_5, NESTED_10, true, new byte[]{(byte) 0xa0});

    final Era era;
    final BlockLayout blockLayout;
    final HeaderLayout headerLayout;
    /**
     * A block's aux data map repeats a transaction index: the last value wins until Babbage ({@code IntMap.fromList}),
     * and the block is rejected from Conway ({@code decodeIntMap}).
     */
    final boolean rejectsDuplicateAuxIndex;
    /**
     * The redeemers of the script integrity preimage when witness field 5 is absent: {@code 80} until Babbage and
     * {@code a0} in Conway (alonzo {@code TxWits.hs}); null before Alonzo, which has no script integrity.
     */
    private final byte[] absentRedeemers;

    EraSpec(Era era, BlockLayout blockLayout, HeaderLayout headerLayout, boolean rejectsDuplicateAuxIndex,
            byte[] absentRedeemers) {
        this.era = era;
        this.blockLayout = blockLayout;
        this.headerLayout = headerLayout;
        this.rejectsDuplicateAuxIndex = rejectsDuplicateAuxIndex;
        this.absentRedeemers = absentRedeemers;
    }

    static EraSpec of(Era era) {
        for (EraSpec rules : values()) {
            if (rules.era == era)
                return rules;
        }
        throw new IllegalArgumentException("No rules for era " + era);
    }

    /**
     * @param index an era index of the hard-fork combinator
     * @return the row of that index
     * @throws UnsupportedEraException if it has none: Byron, Dijkstra, and any index without a constant
     */
    static EraSpec ofIndex(long index) {
        for (EraSpec rules : values()) {
            if (rules.era.value == index)
                return rules;
        }
        if (index == 0 || index == 1)
            throw new UnsupportedEraException(index, "Byron blocks (era " + index + ") are not supported");
        if (index == 8)
            throw new UnsupportedEraException(index, "Dijkstra blocks (era 8) are not supported");
        throw new UnsupportedEraException(index, "Unknown era " + Long.toUnsignedString(index));
    }

    /**
     * @return the era of the table's last row, for a caller that reads a block of an era it does not know with
     * {@link RawBlock#readAs(BlockEnvelope, Era)}
     */
    public static Era newest() {
        EraSpec[] rows = values();
        return rows[rows.length - 1].era;
    }

    byte[] absentRedeemers() {
        if (absentRedeemers == null)
            throw new IllegalArgumentException("There is no script integrity hash before Alonzo; era: " + era);
        return absentRedeemers.clone();
    }
}
