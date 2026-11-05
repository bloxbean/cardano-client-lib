package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.spec.Era;

import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.transaction.raw.FieldType.LIST;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.NONEMPTY_LIST;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.NONEMPTY_SET;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.REDEEMERS;

/**
 * The fields of the {@code transaction_witness_set} rule, read from the pinned CDDL
 * ({@code eras/*}{@code /impl/cddl/data}). Every field is a list that may be empty to Babbage, and a list or set that
 * may not in Conway. A row names the table of an item, or of the value; scripts and Plutus data are recursive, so such
 * an item is one value of a walk (ADR 0003 D6).
 */
public enum WitnessSetField {
    //                   key  type        from         until        table of an item or the value
    VKEY_WITNESSES_LIST(0, LIST, Era.Shelley, Era.Babbage, RawVkeyWitness.RECORD),
    VKEY_WITNESSES(0, NONEMPTY_LIST, Era.Conway, null, RawVkeyWitness.RECORD),
    NATIVE_SCRIPTS_LIST(1, LIST, Era.Shelley, Era.Babbage, RawScript.NATIVE),
    NATIVE_SCRIPTS(1, NONEMPTY_LIST, Era.Conway, null, RawScript.NATIVE),
    BOOTSTRAP_WITNESSES_LIST(2, LIST, Era.Shelley, Era.Babbage, RawBootstrapWitness.RECORD),
    BOOTSTRAP_WITNESSES(2, NONEMPTY_LIST, Era.Conway, null, RawBootstrapWitness.RECORD),
    PLUTUS_V1_SCRIPTS_LIST(3, LIST, Era.Alonzo, Era.Babbage, RawScript.PLUTUS),
    PLUTUS_V1_SCRIPTS(3, NONEMPTY_SET, Era.Conway, null, RawScript.PLUTUS),
    PLUTUS_DATA_LIST(4, LIST, Era.Alonzo, Era.Babbage, RawDatum.DATA),
    PLUTUS_DATA(4, NONEMPTY_LIST, Era.Conway, null, RawDatum.DATA),
    REDEEMERS_FIELD(5, REDEEMERS, Era.Alonzo, null, RawRedeemer.RECORD),
    PLUTUS_V2_SCRIPTS_LIST(6, LIST, Era.Babbage, Era.Babbage, RawScript.PLUTUS),
    PLUTUS_V2_SCRIPTS(6, NONEMPTY_SET, Era.Conway, null, RawScript.PLUTUS),
    PLUTUS_V3_SCRIPTS(7, NONEMPTY_SET, Era.Conway, null, RawScript.PLUTUS);

    /** The {@code transaction_witness_set} rule, a keyed record. */
    public static final RecordSpec RECORD;

    static {
        List<FieldSpec> rows = new ArrayList<>();
        for (WitnessSetField field : values())
            rows.add(field.spec);
        RECORD = RecordSpec.keyed("transaction_witness_set", rows);
    }

    private final FieldSpec spec;

    WitnessSetField(int key, FieldType type, Era from, Era until, RecordSpec record) {
        this.spec = FieldSpec.of(key, type, false, from, until, record);
    }

    /** @return the field's key: read a field the views do not type with {@link RawTx#witnessField(int)} */
    public long key() {
        return spec.key();
    }

    /** @return the field's row */
    public FieldSpec spec() {
        return spec;
    }
}
