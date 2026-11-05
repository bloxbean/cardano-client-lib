package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.spec.Era;

import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.transaction.raw.FieldType.CERTIFICATE_LIST;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.COIN;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.HASH32;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.KEY_HASH_SET;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.LIST;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.MAP;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.NETWORK_ID;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.NONEMPTY_CERTIFICATE_SET;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.NONEMPTY_MAP;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.NONEMPTY_SET;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.POSITIVE_COIN;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.SET;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.TRANSACTION_OUTPUT;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.UINT;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.UPDATE;

/**
 * The fields of the {@code transaction_body} rule, read from the pinned CDDL ({@code eras/*}{@code /impl/cddl/data}). A
 * key has a row for each era range with its own type or requirement, and may change meaning: key 14 is
 * {@code required_signers} to Conway, and {@code guards} after it.
 */
public enum TxBodyField {
    //                   key  type             required  from         until        table of an item or the value
    INPUTS(0, SET, true, Era.Shelley, null, RawInput.RECORD),
    OUTPUTS(1, LIST, true, Era.Shelley, null, RawOutput.RECORD),
    FEE(2, COIN, true, Era.Shelley, null),
    TTL_REQUIRED(3, UINT, true, Era.Shelley, Era.Shelley),
    TTL(3, UINT, false, Era.Allegra, null),
    CERTIFICATES_LIST(4, CERTIFICATE_LIST, false, Era.Shelley, Era.Babbage),
    CERTIFICATES(4, NONEMPTY_CERTIFICATE_SET, false, Era.Conway, null),
    WITHDRAWALS_MAYBE_EMPTY(5, MAP, false, Era.Shelley, Era.Babbage, RawWithdrawal.RECORD),
    WITHDRAWALS(5, NONEMPTY_MAP, false, Era.Conway, null, RawWithdrawal.RECORD),
    UPDATE_PROPOSAL(6, UPDATE, false, Era.Shelley, Era.Babbage),
    AUX_DATA_HASH(7, HASH32, false, Era.Shelley, null),
    VALIDITY_START(8, UINT, false, Era.Allegra, null),
    MINT_MAYBE_EMPTY(9, MAP, false, Era.Mary, Era.Babbage, RawMultiAsset.MINT),
    MINT(9, NONEMPTY_MAP, false, Era.Conway, null, RawMultiAsset.MINT),
    SCRIPT_DATA_HASH(11, HASH32, false, Era.Alonzo, null),
    COLLATERAL_MAYBE_EMPTY(13, SET, false, Era.Alonzo, Era.Babbage, RawInput.RECORD),
    COLLATERAL(13, NONEMPTY_SET, false, Era.Conway, null, RawInput.RECORD),
    REQUIRED_SIGNERS_MAYBE_EMPTY(14, KEY_HASH_SET, false, Era.Alonzo, Era.Babbage),
    REQUIRED_SIGNERS(14, NONEMPTY_SET, false, Era.Conway, Era.Conway, RecordSpec.KEY_HASH),
    NETWORK(15, NETWORK_ID, false, Era.Alonzo, null),
    COLLATERAL_RETURN(16, TRANSACTION_OUTPUT, false, Era.Babbage, null),
    TOTAL_COLLATERAL(17, COIN, false, Era.Babbage, null),
    REFERENCE_INPUTS_MAYBE_EMPTY(18, SET, false, Era.Babbage, Era.Babbage, RawInput.RECORD),
    REFERENCE_INPUTS(18, NONEMPTY_SET, false, Era.Conway, null, RawInput.RECORD),
    VOTING_PROCEDURES(19, NONEMPTY_MAP, false, Era.Conway, null, RawVotingProcedures.RECORD),
    PROPOSAL_PROCEDURES(20, NONEMPTY_SET, false, Era.Conway, null, RawProposal.RECORD),
    CURRENT_TREASURY(21, COIN, false, Era.Conway, null),
    DONATION(22, POSITIVE_COIN, false, Era.Conway, null);

    /** The {@code transaction_body} rule, a keyed record. */
    public static final RecordSpec RECORD;

    static {
        List<FieldSpec> rows = new ArrayList<>();
        for (TxBodyField field : values())
            rows.add(field.spec);
        RECORD = RecordSpec.keyed("transaction_body", rows);
    }

    private final FieldSpec spec;

    TxBodyField(int key, FieldType type, boolean required, Era from, Era until) {
        this.spec = FieldSpec.of(key, type, required, from, until);
    }

    TxBodyField(int key, FieldType type, boolean required, Era from, Era until, RecordSpec record) {
        this.spec = FieldSpec.of(key, type, required, from, until, record);
    }

    /** @return the field's key: read a field the views do not type with {@link RawTx#bodyField(int)} */
    public long key() {
        return spec.key();
    }

    /** @return the field's row */
    public FieldSpec spec() {
        return spec;
    }
}
