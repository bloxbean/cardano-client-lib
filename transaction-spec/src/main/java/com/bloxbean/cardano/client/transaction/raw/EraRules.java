package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.spec.Era;

/**
 * The rules of the raw views that depend on the era (ADR 0001 D8), in one table: the only place where the views branch
 * on the era. Everything else is the same in every Shelley-family era: records are read by key and unknown keys are
 * passed through, a duplicate record key is rejected, tag 258 is accepted on sets, and both redeemer forms and every aux
 * data shape are read wherever they occur, as the ledger's decoders of later eras still accept the earlier shapes.
 * <p>
 * Adding an era means adding its {@link Era} constant and its row here.
 */
enum EraRules {
    //       era          invalid_transactions  duplicate aux index  absent redeemers
    SHELLEY(Era.Shelley, false, false, null),
    ALLEGRA(Era.Allegra, false, false, null),
    MARY(Era.Mary, false, false, null),
    ALONZO(Era.Alonzo, true, false, new byte[]{(byte) 0x80}),
    BABBAGE(Era.Babbage, true, false, new byte[]{(byte) 0x80}),
    CONWAY(Era.Conway, true, true, new byte[]{(byte) 0xa0});

    final Era era;
    /**
     * From Alonzo, a block body has a fifth part, {@code invalid_transactions}, which is the fourth part of the block body
     * hash, and a transaction has an {@code isValid} flag (shelley and alonzo {@code BlockBody/Internal.hs}).
     */
    final boolean invalidTransactions;
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

    EraRules(Era era, boolean invalidTransactions, boolean rejectsDuplicateAuxIndex, byte[] absentRedeemers) {
        this.era = era;
        this.invalidTransactions = invalidTransactions;
        this.rejectsDuplicateAuxIndex = rejectsDuplicateAuxIndex;
        this.absentRedeemers = absentRedeemers;
    }

    static EraRules of(Era era) {
        for (EraRules rules : values()) {
            if (rules.era == era)
                return rules;
        }
        throw new IllegalArgumentException("No raw view rules for era " + era);
    }

    /**
     * @return the parts of a block body: tx bodies, witness sets, the aux data map and, from Alonzo, invalid txs
     */
    int blockBodyParts() {
        return invalidTransactions ? 4 : 3;
    }

    byte[] absentRedeemers() {
        if (absentRedeemers == null)
            throw new IllegalArgumentException("There is no script integrity hash before Alonzo; era: " + era);
        return absentRedeemers.clone();
    }
}
