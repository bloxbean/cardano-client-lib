package com.bloxbean.cardano.client.transaction.spec;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.address.CredentialType;
import com.bloxbean.cardano.client.transaction.spec.governance.Voter;
import com.bloxbean.cardano.client.transaction.spec.governance.VoterType;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovActionId;
import com.bloxbean.cardano.client.util.HexUtil;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Comparators used during cbor serialization. A map keyed by a ledger type is written in Haskell's {@code Ord} order
 * for that type, which is not always the order of its encoded bytes.
 */
public class Comparators {

    private Comparators() {
    }

    /**
     * Length first, then unsigned bytes, as RFC 7049 section 3.9 and CIP-21 require. Haskell's {@code AssetName} order
     * is different, and this one is kept for hardware wallets (ADR 0002 Q5).
     */
    public static final Comparator<Asset> ASSET = (asset1, asset2) -> {
        byte[] name1 = asset1.getNameAsBytes();
        byte[] name2 = asset2.getNameAsBytes();

        if (name1.length != name2.length) {
            return Integer.compare(name1.length, name2.length);
        }
        return Arrays.compareUnsigned(name1, name2);
    };

    /** Haskell's {@code Credential}: {@code ScriptHashObj} before {@code KeyHashObj}, then by hash. */
    public static final Comparator<Credential> CREDENTIAL = Comparator
            .comparingInt((Credential credential) -> credential.getType() == CredentialType.Script ? 0 : 1)
            .thenComparing(Credential::getBytes, Arrays::compareUnsigned);

    /** Haskell's {@code AccountAddress}: network, then {@link #CREDENTIAL}. */
    public static final Comparator<Withdrawal> WITHDRAWAL = Comparator.comparing(
            (Withdrawal withdrawal) -> new Address(withdrawal.getRewardAddress()),
            Comparator.comparingInt((Address address) -> address.getNetwork().getNetworkId())
                    .thenComparing(Comparators::delegationCredential, CREDENTIAL));

    /**
     * Haskell's {@code Voter}: committee, then DRep, each with a script before a key, then stake pool, and by hash
     * within each. The voter type decides, as it is what the voter encodes to.
     */
    public static final Comparator<Voter> VOTER = Comparator
            .comparingInt((Voter voter) -> rank(voter.getType()))
            .thenComparing(voter -> voter.getCredential().getBytes(), Arrays::compareUnsigned);

    /** Haskell's {@code GovActionId}: transaction id, then index. */
    public static final Comparator<GovActionId> GOV_ACTION_ID = Comparator
            .comparing((GovActionId id) -> HexUtil.decodeHexString(id.getTransactionId()), Arrays::compareUnsigned)
            .thenComparingInt(GovActionId::getGovActionIndex);

    private static Credential delegationCredential(Address address) {
        return AddressProvider.getDelegationCredential(address).orElseThrow(() -> new IllegalArgumentException(
                "No delegation credential found for address: " + address.getAddress()));
    }

    private static int rank(VoterType type) {
        return switch (type) {
            case CONSTITUTIONAL_COMMITTEE_HOT_SCRIPT_HASH -> 0;
            case CONSTITUTIONAL_COMMITTEE_HOT_KEY_HASH -> 1;
            case DREP_SCRIPT_HASH -> 2;
            case DREP_KEY_HASH -> 3;
            case STAKING_POOL_KEY_HASH -> 4;
        };
    }
}
