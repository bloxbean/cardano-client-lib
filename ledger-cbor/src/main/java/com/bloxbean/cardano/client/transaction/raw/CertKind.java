package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.bloxbean.cardano.client.transaction.raw.FieldType.COIN;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.CREDENTIAL;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.DREP;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.HASH28;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.HASH32;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.KEY_HASH_SET;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.MIR;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.OPTIONAL_ANCHOR;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.OPTIONAL_BLS_KEY;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.OPTIONAL_POOL_METADATA;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.RELAY_LIST;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.REWARD_ACCOUNT;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.UINT;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.UNIT_INTERVAL;

/**
 * The certificate table: every tag of the {@code certificate} rule in any era, with the eras it is valid for and the
 * items after the tag. Read from the pinned CDDL ({@code eras/*}{@code /impl/cddl/data}). From Dijkstra a pool
 * registration may also have a BLS key, or null, after its VRF key hash ({@code StakePool.hs}): the union then has a
 * second member of tag 3 with one item more, {@link #POOL_REGISTRATION_WITH_BLS_KEY}. Read in Dijkstra, a registration
 * has that slot when the item after its VRF key hash is an array (the key) or null, as the ledger peeks at it; read
 * with no era or in an earlier era, it has none. That member is experimental: Dijkstra is not final; this may change
 * in any release.
 */
public enum CertKind {
    //                      tag  from         until        items after the tag
    STAKE_REGISTRATION(0, Era.Shelley, Era.Conway, CREDENTIAL),
    STAKE_DEREGISTRATION(1, Era.Shelley, Era.Conway, CREDENTIAL),
    STAKE_DELEGATION(2, Era.Shelley, null, CREDENTIAL, HASH28),
    POOL_REGISTRATION(3, Era.Shelley, null, HASH28, HASH32, COIN, COIN, UNIT_INTERVAL, REWARD_ACCOUNT, KEY_HASH_SET,
            RELAY_LIST, OPTIONAL_POOL_METADATA),
    POOL_RETIREMENT(4, Era.Shelley, null, HASH28, UINT),
    GENESIS_KEY_DELEGATION(5, Era.Shelley, Era.Babbage, HASH28, HASH28, HASH32),
    MOVE_INSTANTANEOUS_REWARDS(6, Era.Shelley, Era.Babbage, MIR),
    REG_CERT(7, Era.Conway, null, CREDENTIAL, COIN),
    UNREG_CERT(8, Era.Conway, null, CREDENTIAL, COIN),
    VOTE_DELEG_CERT(9, Era.Conway, null, CREDENTIAL, DREP),
    STAKE_VOTE_DELEG_CERT(10, Era.Conway, null, CREDENTIAL, HASH28, DREP),
    STAKE_REG_DELEG_CERT(11, Era.Conway, null, CREDENTIAL, HASH28, COIN),
    VOTE_REG_DELEG_CERT(12, Era.Conway, null, CREDENTIAL, DREP, COIN),
    STAKE_VOTE_REG_DELEG_CERT(13, Era.Conway, null, CREDENTIAL, HASH28, DREP, COIN),
    AUTH_COMMITTEE_HOT_CERT(14, Era.Conway, null, CREDENTIAL, CREDENTIAL),
    RESIGN_COMMITTEE_COLD_CERT(15, Era.Conway, null, CREDENTIAL, OPTIONAL_ANCHOR),
    REG_DREP_CERT(16, Era.Conway, null, CREDENTIAL, COIN, OPTIONAL_ANCHOR),
    UNREG_DREP_CERT(17, Era.Conway, null, CREDENTIAL, COIN),
    UPDATE_DREP_CERT(18, Era.Conway, null, CREDENTIAL, OPTIONAL_ANCHOR);

    /**
     * The member of a pool registration with a BLS key or null, from Dijkstra. Experimental: Dijkstra is not final;
     * this may change in any release.
     */
    public static final FieldSpec POOL_REGISTRATION_WITH_BLS_KEY = FieldSpec.member(3, Era.Dijkstra, null, HASH28,
            HASH32, OPTIONAL_BLS_KEY, COIN, COIN, UNIT_INTERVAL, REWARD_ACCOUNT, KEY_HASH_SET, RELAY_LIST,
            OPTIONAL_POOL_METADATA);

    /** The {@code certificate} rule, a tagged union. */
    public static final RecordSpec RECORD;

    static {
        List<FieldSpec> members = new ArrayList<>();
        for (CertKind kind : values())
            members.add(kind.member);
        members.add(POOL_REGISTRATION_WITH_BLS_KEY);
        RECORD = RecordSpec.tagged("certificate", members);
    }

    private final FieldSpec member;

    CertKind(int tag, Era from, Era until, FieldType... items) {
        this.member = FieldSpec.member(tag, from, until, items);
    }

    /**
     * @return the certificate's tag
     */
    public int tag() {
        return (int) member.key();
    }

    /**
     * @return the row of the {@code certificate} union, with the eras the tag is valid for; for a pool registration,
     * the form without a BLS key
     */
    public FieldSpec member() {
        return member;
    }

    // The table of a certificate of this kind with these items: in an era from Dijkstra, a pool registration with an
    // array or null after its VRF key hash has a BLS key slot; with no era, the member.
    RecordSpec record(List<CborSpan> items, Era era) {
        return era == null ? member.record() : RECORD.withOptionalItem(member, items, era).record();
    }

    /**
     * @param certificate a certificate as encoded
     * @return the kind its tag names, or empty for an unknown tag
     * @throws CborRuntimeException if it is not an array starting with an unsigned
     *                              integer that fits an int
     */
    public static Optional<CertKind> of(CborSpan certificate) {
        return of(RawItems.array(certificate, RECORD.name()), certificate);
    }

    static Optional<CertKind> of(List<CborSpan> items, CborSpan certificate) {
        if (items.isEmpty())
            throw RawTx.error("A certificate has a tag", certificate);
        int tag = RawItems.smallUnsigned(items.get(0));
        for (CertKind kind : values()) {
            if (kind.tag() == tag)
                return Optional.of(kind);
        }
        return Optional.empty();
    }
}
