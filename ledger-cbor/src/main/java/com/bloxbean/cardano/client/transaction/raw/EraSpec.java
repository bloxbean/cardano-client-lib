package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.transaction.raw.BlockLayout.PARALLEL_4;
import static com.bloxbean.cardano.client.transaction.raw.BlockLayout.PARALLEL_5;
import static com.bloxbean.cardano.client.transaction.raw.BlockLayout.UNIFIED;
import static com.bloxbean.cardano.client.transaction.raw.HeaderLayout.FLAT_15;
import static com.bloxbean.cardano.client.transaction.raw.HeaderLayout.NESTED_10;
import static com.bloxbean.cardano.client.transaction.raw.HeaderLayout.NESTED_12;

/**
 * The rules of the ledger format that depend on the era (ADR 0001 D8, ADR 0003 D3), in one table: the only place where
 * the reader branches on the era, apart from the field tables. Each era names its block layout and its header layouts,
 * each implemented once; an era with two header layouts tells them apart by their number of items. Everything else is
 * the same in every Shelley-family era: records are read by key and unknown keys are passed through, a duplicate
 * record key is rejected, tag 258 is accepted on sets, and both redeemer forms and every aux data shape are read
 * wherever they occur, as the ledger's decoders of later eras still accept the earlier shapes.
 * <p>
 * Adding an era means adding its {@link Era} constant and its row here. The Dijkstra row is experimental: Dijkstra is
 * not final; this may change in any release.
 */
public enum EraSpec {
    // era, block layout, duplicate aux index rejected, absent redeemers, header layouts
    SHELLEY(Era.Shelley, PARALLEL_4, false, null, FLAT_15),
    ALLEGRA(Era.Allegra, PARALLEL_4, false, null, FLAT_15),
    MARY(Era.Mary, PARALLEL_4, false, null, FLAT_15),
    ALONZO(Era.Alonzo, PARALLEL_5, false, new byte[]{(byte) 0x80}, FLAT_15),
    BABBAGE(Era.Babbage, PARALLEL_5, false, new byte[]{(byte) 0x80}, NESTED_10),
    CONWAY(Era.Conway, PARALLEL_5, true, new byte[]{(byte) 0xa0}, NESTED_10),
    DIJKSTRA(Era.Dijkstra, UNIFIED, true, new byte[]{(byte) 0xa0}, NESTED_10, NESTED_12);

    final Era era;
    final BlockLayout blockLayout;
    final List<HeaderLayout> headerLayouts;
    /**
     * A block's aux data map repeats a transaction index: the last value wins until Babbage ({@code IntMap.fromList}),
     * and the block is rejected in Conway ({@code decodeIntMap}). Dijkstra's block has no such map.
     */
    final boolean rejectsDuplicateAuxIndex;
    /**
     * The redeemers of the script integrity preimage when witness field 5 is absent: {@code 80} until Babbage and
     * {@code a0} from Conway (alonzo {@code TxWits.hs}); null before Alonzo, which has no script integrity.
     */
    private final byte[] absentRedeemers;

    EraSpec(Era era, BlockLayout blockLayout, boolean rejectsDuplicateAuxIndex, byte[] absentRedeemers,
            HeaderLayout... headerLayouts) {
        this.era = era;
        this.blockLayout = blockLayout;
        this.headerLayouts = List.of(headerLayouts);
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
     * @throws UnsupportedEraException if it has none: Byron, and any index without a constant
     */
    static EraSpec ofIndex(long index) {
        for (EraSpec rules : values()) {
            if (rules.era.value == index)
                return rules;
        }
        if (index == 0 || index == 1)
            throw new UnsupportedEraException(index, "Byron blocks (era " + index + ") are not supported");
        throw new UnsupportedEraException(index, "Unknown era " + Long.toUnsignedString(index));
    }

    /**
     * @param body a header body as encoded
     * @return the layout of this era with as many items as the body has
     * @throws CborRuntimeException if it is not an array, or has the items of none of this era's layouts
     */
    HeaderLayout headerLayout(CborSpan body) {
        return HeaderLayout.ofShape(body, headerLayouts);
    }

    /**
     * @param era an era
     * @return the header layouts of the eras to this one, each once, in table order: what a header of one of them may
     * have
     */
    static List<HeaderLayout> headerLayoutsTo(Era era) {
        List<HeaderLayout> layouts = new ArrayList<>();
        for (EraSpec rules : values()) {
            if (rules.era.compareTo(era) > 0)
                break;
            for (HeaderLayout layout : rules.headerLayouts) {
                if (!layouts.contains(layout))
                    layouts.add(layout);
            }
        }
        return layouts;
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
