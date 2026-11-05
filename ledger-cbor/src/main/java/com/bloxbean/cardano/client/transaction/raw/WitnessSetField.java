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
 * may not in Conway. All are leaves of a walk: the items are recursive types or are read by their own accessors.
 */
public enum WitnessSetField {
    //                   key  type        from         until
    VKEY_WITNESSES_LIST(0, LIST, Era.Shelley, Era.Babbage),
    VKEY_WITNESSES(0, NONEMPTY_LIST, Era.Conway, null),
    NATIVE_SCRIPTS_LIST(1, LIST, Era.Shelley, Era.Babbage),
    NATIVE_SCRIPTS(1, NONEMPTY_LIST, Era.Conway, null),
    BOOTSTRAP_WITNESSES_LIST(2, LIST, Era.Shelley, Era.Babbage),
    BOOTSTRAP_WITNESSES(2, NONEMPTY_LIST, Era.Conway, null),
    PLUTUS_V1_SCRIPTS_LIST(3, LIST, Era.Alonzo, Era.Babbage),
    PLUTUS_V1_SCRIPTS(3, NONEMPTY_SET, Era.Conway, null),
    PLUTUS_DATA_LIST(4, LIST, Era.Alonzo, Era.Babbage),
    PLUTUS_DATA(4, NONEMPTY_LIST, Era.Conway, null),
    REDEEMERS_FIELD(5, REDEEMERS, Era.Alonzo, null),
    PLUTUS_V2_SCRIPTS_LIST(6, LIST, Era.Babbage, Era.Babbage),
    PLUTUS_V2_SCRIPTS(6, NONEMPTY_SET, Era.Conway, null),
    PLUTUS_V3_SCRIPTS(7, NONEMPTY_SET, Era.Conway, null);

    /** The {@code transaction_witness_set} rule, a keyed record. */
    public static final RecordSpec RECORD;

    static {
        List<FieldSpec> rows = new ArrayList<>();
        for (WitnessSetField field : values())
            rows.add(field.spec);
        RECORD = RecordSpec.keyed("transaction_witness_set", rows);
    }

    private final FieldSpec spec;

    WitnessSetField(int key, FieldType type, Era from, Era until) {
        this.spec = FieldSpec.of(key, type, false, from, until);
    }

    public FieldSpec spec() {
        return spec;
    }
}
