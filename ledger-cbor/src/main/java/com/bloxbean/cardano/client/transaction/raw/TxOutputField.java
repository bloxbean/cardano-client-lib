package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.spec.Era;

import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.transaction.raw.FieldType.ADDRESS;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.DATUM_OPTION;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.SCRIPT_REF;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.VALUE;

/**
 * The fields of the {@code babbage_transaction_output} rule, read from the pinned CDDL
 * ({@code eras/*}{@code /impl/cddl/data}), the map form of an output.
 */
public enum TxOutputField {
    //             key  type          required  from
    OUTPUT_ADDRESS(0, ADDRESS, true, Era.Babbage),
    AMOUNT(1, VALUE, true, Era.Babbage),
    OUTPUT_DATUM(2, DATUM_OPTION, false, Era.Babbage),
    OUTPUT_SCRIPT_REF(3, SCRIPT_REF, false, Era.Babbage);

    /** The {@code babbage_transaction_output} rule, a keyed record. */
    public static final RecordSpec RECORD;

    static {
        List<FieldSpec> rows = new ArrayList<>();
        for (TxOutputField field : values())
            rows.add(field.spec);
        RECORD = RecordSpec.keyed("babbage_transaction_output", rows);
    }

    private final FieldSpec spec;

    TxOutputField(int key, FieldType type, boolean required, Era from) {
        this.spec = FieldSpec.of(key, type, required, from, null);
    }

    /** @return the field's key in the map form */
    public long key() {
        return spec.key();
    }

    /** @return the field's row */
    public FieldSpec spec() {
        return spec;
    }
}
