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
import static com.bloxbean.cardano.client.transaction.raw.TxBodyField.Level.ANY;
import static com.bloxbean.cardano.client.transaction.raw.TxBodyField.Level.TOP;

/**
 * The fields of the {@code transaction_body} rule, read from the pinned CDDL ({@code eras/*}{@code /impl/cddl/data}). A
 * key has a row for each era range with its own type or requirement, and may change meaning: key 14 is
 * {@code required_signers} to Conway, and {@code guards} after it.
 * <p>
 * The level column says whether a Dijkstra sub-transaction body may have the field ({@code ANY}) or only the top-level
 * body ({@code TOP}): the ledger reads keys 2, 13, 16, 17, 23 and 27 of a sub-transaction body as unknown keys
 * (Dijkstra {@code TxBody.hs}). {@link #SUB_RECORD} is the table of the other rows. The rows from Dijkstra are
 * experimental: Dijkstra is not final; this may change in any release.
 */
public enum TxBodyField {
    //                   key  type             required level from         until        table of an item or the value
    INPUTS(0, SET, true, ANY, Era.Shelley, null, RawInput.RECORD),
    OUTPUTS(1, LIST, true, ANY, Era.Shelley, null, RawOutput.RECORD),
    FEE(2, COIN, true, TOP, Era.Shelley, null),
    TTL_REQUIRED(3, UINT, true, ANY, Era.Shelley, Era.Shelley),
    TTL(3, UINT, false, ANY, Era.Allegra, null),
    CERTIFICATES_LIST(4, CERTIFICATE_LIST, false, ANY, Era.Shelley, Era.Babbage),
    CERTIFICATES(4, NONEMPTY_CERTIFICATE_SET, false, ANY, Era.Conway, null),
    WITHDRAWALS_MAYBE_EMPTY(5, MAP, false, ANY, Era.Shelley, Era.Babbage, RawWithdrawal.RECORD),
    WITHDRAWALS(5, NONEMPTY_MAP, false, ANY, Era.Conway, null, RawWithdrawal.RECORD),
    UPDATE_PROPOSAL(6, UPDATE, false, ANY, Era.Shelley, Era.Babbage),
    AUX_DATA_HASH(7, HASH32, false, ANY, Era.Shelley, null),
    VALIDITY_START(8, UINT, false, ANY, Era.Allegra, null),
    MINT_MAYBE_EMPTY(9, MAP, false, ANY, Era.Mary, Era.Babbage, RawMultiAsset.MINT),
    MINT(9, NONEMPTY_MAP, false, ANY, Era.Conway, null, RawMultiAsset.MINT),
    SCRIPT_DATA_HASH(11, HASH32, false, ANY, Era.Alonzo, null),
    COLLATERAL_MAYBE_EMPTY(13, SET, false, TOP, Era.Alonzo, Era.Babbage, RawInput.RECORD),
    COLLATERAL(13, NONEMPTY_SET, false, TOP, Era.Conway, null, RawInput.RECORD),
    REQUIRED_SIGNERS_MAYBE_EMPTY(14, KEY_HASH_SET, false, ANY, Era.Alonzo, Era.Babbage),
    REQUIRED_SIGNERS(14, NONEMPTY_SET, false, ANY, Era.Conway, Era.Conway, RecordSpec.KEY_HASH),
    // code over CDDL: guards may be empty, and have no tag 258 (Dijkstra TxBody.hs, decodeGuards has no failOnNull)
    GUARDS(14, SET, false, ANY, Era.Dijkstra, null, RawCredential.GUARD),
    NETWORK(15, NETWORK_ID, false, ANY, Era.Alonzo, null),
    COLLATERAL_RETURN(16, TRANSACTION_OUTPUT, false, TOP, Era.Babbage, null),
    TOTAL_COLLATERAL(17, COIN, false, TOP, Era.Babbage, null),
    REFERENCE_INPUTS_MAYBE_EMPTY(18, SET, false, ANY, Era.Babbage, Era.Babbage, RawInput.RECORD),
    REFERENCE_INPUTS(18, NONEMPTY_SET, false, ANY, Era.Conway, null, RawInput.RECORD),
    VOTING_PROCEDURES(19, NONEMPTY_MAP, false, ANY, Era.Conway, null, RawVotingProcedures.RECORD),
    PROPOSAL_PROCEDURES(20, NONEMPTY_SET, false, ANY, Era.Conway, null, RawProposal.RECORD),
    CURRENT_TREASURY(21, COIN, false, ANY, Era.Conway, null),
    DONATION(22, POSITIVE_COIN, false, ANY, Era.Conway, null),
    SUB_TRANSACTIONS(23, NONEMPTY_SET, false, TOP, Era.Dijkstra, null, subTransaction()),
    REQUIRED_TOP_LEVEL_GUARDS(24, NONEMPTY_MAP, false, ANY, Era.Dijkstra, null, RawTopLevelGuard.MAP),
    DIRECT_DEPOSITS(25, NONEMPTY_MAP, false, ANY, Era.Dijkstra, null, RawWithdrawal.RECORD),
    ACCOUNT_BALANCE_INTERVALS(26, NONEMPTY_MAP, false, ANY, Era.Dijkstra, null, RawAccountBalanceInterval.MAP),
    STARTING_ACCOUNT_BALANCE_INTERVALS(27, NONEMPTY_MAP, false, TOP, Era.Dijkstra, null,
            RawAccountBalanceInterval.MAP);

    /** The {@code transaction_body} rule, a keyed record. */
    public static final RecordSpec RECORD;
    /**
     * The {@code sub_transaction_body} rule: the rows a Dijkstra sub-transaction body may have, so a walk reports any
     * other key as unknown. Experimental: Dijkstra is not final; this may change in any release.
     */
    public static final RecordSpec SUB_RECORD;

    static {
        List<FieldSpec> rows = new ArrayList<>();
        List<FieldSpec> subRows = new ArrayList<>();
        for (TxBodyField field : values()) {
            rows.add(field.spec);
            if (field.level == ANY)
                subRows.add(field.spec);
        }
        RECORD = RecordSpec.keyed("transaction_body", rows);
        SUB_RECORD = RecordSpec.keyed("sub_transaction_body", subRows);
    }

    private final FieldSpec spec;
    private final Level level;

    TxBodyField(int key, FieldType type, boolean required, Level level, Era from, Era until) {
        this.spec = FieldSpec.of(key, type, required, from, until);
        this.level = level;
    }

    TxBodyField(int key, FieldType type, boolean required, Level level, Era from, Era until, RecordSpec record) {
        this.spec = FieldSpec.of(key, type, required, from, until, record);
        this.level = level;
    }

    // [sub_transaction_body, transaction_witness_set, auxiliary_data / nil], with no validity flag. Its body's table,
    // SUB_RECORD, is made from this one, so the body's row has it through its type.
    private static RecordSpec subTransaction() {
        return RecordSpec.positional("sub_transaction", List.of(
                FieldSpec.of(0, FieldType.SUB_TRANSACTION_BODY, true, Era.Dijkstra, null),
                FieldSpec.of(1, FieldType.TRANSACTION_WITNESS_SET, true, Era.Dijkstra, null, WitnessSetField.RECORD),
                FieldSpec.of(2, FieldType.OPTIONAL_AUXILIARY_DATA, true, Era.Dijkstra, null)));
    }

    /** @return the field's key: read a field the views do not type with {@link RawTx#bodyField(int)} */
    public long key() {
        return spec.key();
    }

    /** @return the field's row */
    public FieldSpec spec() {
        return spec;
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return whether only a top-level body may have the field, and not a Dijkstra sub-transaction body
     */
    public boolean topLevelOnly() {
        return level == TOP;
    }

    // Which bodies may have a field: any, or only the top-level one.
    enum Level {
        ANY, TOP
    }
}
