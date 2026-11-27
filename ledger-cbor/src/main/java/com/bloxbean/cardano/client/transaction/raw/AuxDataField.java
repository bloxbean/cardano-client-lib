package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.spec.Era;

import java.util.ArrayList;
import java.util.List;

/**
 * The fields of the {@code auxiliary_data_map} rule, read from the pinned CDDL ({@code eras/*}{@code /impl/cddl/data}):
 * a keyed record in tag 259, the form of auxiliary data from Alonzo on. The row of key 5, Plutus V4 scripts, is
 * experimental: Dijkstra is not final; this may change in any release.
 */
public enum AuxDataField {
    //       key  type                from         table of an item
    METADATA(0, FieldType.METADATA, Era.Alonzo, null),
    NATIVE_SCRIPTS(1, FieldType.LIST, Era.Alonzo, RawScript.NATIVE),
    PLUTUS_V1_SCRIPTS(2, FieldType.LIST, Era.Alonzo, RawScript.PLUTUS),
    PLUTUS_V2_SCRIPTS(3, FieldType.LIST, Era.Babbage, RawScript.PLUTUS),
    PLUTUS_V3_SCRIPTS(4, FieldType.LIST, Era.Conway, RawScript.PLUTUS),
    PLUTUS_V4_SCRIPTS(5, FieldType.LIST, Era.Dijkstra, RawScript.PLUTUS);

    private static final long TAG = 259;

    /** The {@code auxiliary_data_map} rule. */
    public static final RecordSpec RECORD;

    static {
        List<FieldSpec> rows = new ArrayList<>();
        for (AuxDataField field : values())
            rows.add(field.spec);
        RECORD = RecordSpec.keyed("auxiliary_data_map", TAG, rows);
    }

    private final FieldSpec spec;

    AuxDataField(int key, FieldType type, Era from, RecordSpec record) {
        this.spec = FieldSpec.of(key, type, false, from, null, record);
    }

    /** @return the field's key */
    public long key() {
        return spec.key();
    }

    /** @return the field's row */
    public FieldSpec spec() {
        return spec;
    }
}
