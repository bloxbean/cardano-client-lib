package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.bloxbean.cardano.client.transaction.raw.FieldType.COMMITTEE_TERMS;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.CONSTITUTION;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.CREDENTIAL_SET;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.OPTIONAL_GOV_ACTION_ID;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.OPTIONAL_HASH28;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.PROTOCOL_PARAM_UPDATE;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.PROTOCOL_VERSION;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.UNIT_INTERVAL;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.WITHDRAWALS;

/**
 * The governance action table: every tag of the {@code gov_action} rule, with the eras it is valid for and the items
 * after the tag. Read from the pinned CDDL ({@code eras/*}{@code /impl/cddl/data}).
 */
public enum GovActionKind {
    //                    tag  from        until  items after the tag
    PARAMETER_CHANGE(0, Era.Conway, null, OPTIONAL_GOV_ACTION_ID, PROTOCOL_PARAM_UPDATE, OPTIONAL_HASH28),
    HARD_FORK_INITIATION(1, Era.Conway, null, OPTIONAL_GOV_ACTION_ID, PROTOCOL_VERSION),
    TREASURY_WITHDRAWALS(2, Era.Conway, null, WITHDRAWALS, OPTIONAL_HASH28),
    NO_CONFIDENCE(3, Era.Conway, null, OPTIONAL_GOV_ACTION_ID),
    UPDATE_COMMITTEE(4, Era.Conway, null, OPTIONAL_GOV_ACTION_ID, CREDENTIAL_SET, COMMITTEE_TERMS, UNIT_INTERVAL),
    NEW_CONSTITUTION(5, Era.Conway, null, OPTIONAL_GOV_ACTION_ID, CONSTITUTION),
    INFO(6, Era.Conway, null);

    /** The {@code gov_action} rule, a tagged union. */
    public static final RecordSpec RECORD;

    static {
        List<FieldSpec> members = new ArrayList<>();
        for (GovActionKind kind : values())
            members.add(kind.member);
        RECORD = RecordSpec.tagged("gov_action", members);
    }

    private final FieldSpec member;

    GovActionKind(int tag, Era from, Era until, FieldType... items) {
        this.member = FieldSpec.member(tag, from, until, items);
    }

    /**
     * @return the action's tag
     */
    public int tag() {
        return (int) member.key();
    }

    /**
     * @return the row of the {@code gov_action} union, with the eras the tag is valid for
     */
    public FieldSpec member() {
        return member;
    }

    static Optional<GovActionKind> of(List<CborSpan> items, CborSpan action) {
        if (items.isEmpty())
            throw RawTx.error("A governance action has a tag", action);
        int tag = RawItems.smallUnsigned(items.get(0));
        for (GovActionKind kind : values()) {
            if (kind.tag() == tag)
                return Optional.of(kind);
        }
        return Optional.empty();
    }

    /**
     * @param action an action as encoded
     * @return the kind its tag names, or empty for an unknown tag
     * @throws CborRuntimeException if it is not an array starting with an unsigned integer that fits an int
     */
    public static Optional<GovActionKind> of(CborSpan action) {
        return of(RawItems.array(action, RECORD.name()), action);
    }
}
