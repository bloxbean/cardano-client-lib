package com.bloxbean.cardano.client.transaction.spec.governance.actions;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.common.cbor.custom.SortedMap;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.UnitInterval;
import com.bloxbean.cardano.client.transaction.spec.Comparators;
import com.bloxbean.cardano.client.transaction.util.CredentialSerializer;
import com.bloxbean.cardano.client.transaction.raw.GovActionKind;
import com.bloxbean.cardano.client.transaction.raw.RawGovAction;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import com.bloxbean.cardano.client.transaction.raw.RawCredential;
import lombok.*;

import java.util.Map;
import java.util.*;

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.unsignedLongItem;
import static com.bloxbean.cardano.client.transaction.util.SerializationUtil.createArray;

/**
 * {@literal
 * update_committee = (4, gov_action_id / null, set<committee_cold_credential>, { committee_cold_credential => epoch }, unit_interval)
 * }
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
public class UpdateCommittee implements GovAction {
    private final GovActionType type = GovActionType.UPDATE_COMMITTEE;

    private GovActionId prevGovActionId;
    @Builder.Default
    private Set<Credential> membersForRemoval = new LinkedHashSet<>();

    @Builder.Default
    private Map<Credential, Long> newMembersAndTerms = new LinkedHashMap<>();
    private UnitInterval quorumThreshold; //TODO?? Check the correct name.

    @Override
    @SneakyThrows
    public Array serialize(Era era) {
        Objects.requireNonNull(membersForRemoval);
        Objects.requireNonNull(newMembersAndTerms);

        Array array = new Array();
        array.add(new UnsignedInteger(4));

        if (prevGovActionId != null)
            array.add(prevGovActionId.serialize());
        else
            array.add(SimpleValue.NULL);

        Array membersForRemovalArray = createArray(era);
        List<Credential> removed = new ArrayList<>(membersForRemoval);
        removed.sort(Comparators.CREDENTIAL);
        for (Credential member: removed) {
            membersForRemovalArray.add(CredentialSerializer.serialize(member));
        }
        array.add(membersForRemovalArray);

        SortedMap newMembersTermsMap = new SortedMap();
        List<Map.Entry<Credential, Long>> newMembers = new ArrayList<>(newMembersAndTerms.entrySet());
        newMembers.sort(Map.Entry.comparingByKey(Comparators.CREDENTIAL));
        for (Map.Entry<Credential, Long> newMemberTerm: newMembers) {
            newMembersTermsMap.put(CredentialSerializer.serialize(newMemberTerm.getKey()), unsignedLongItem(newMemberTerm.getValue()));
        }
        array.add(newMembersTermsMap);

        array.add(new RationalNumber(new UnsignedInteger(quorumThreshold.getNumerator()), new UnsignedInteger(quorumThreshold.getDenominator())));
        return array;
    }

    /**
     * {@literal
     * update_committee = (4, gov_action_id / null, set<committee_cold_credential>, { committee_cold_credential => epoch }, unit_interval)
     * }
     *
     * @param govActionArray
     * @return
     */
    public static UpdateCommittee deserialize(Array govActionArray) {
        return from(RawGovAction.lenient(GovActionKind.UPDATE_COMMITTEE, RawModels.span(govActionArray),
                RawModels.READ_IN));
    }

    /**
     * @param raw a committee update as received
     * @return the model: a credential that equals an earlier one keeps that one's place, and in the new members takes
     * the later term
     */
    static UpdateCommittee from(RawGovAction raw) {
        Set<Credential> removed = new LinkedHashSet<>();
        for (RawCredential member : raw.membersToRemove())
            removed.add(RawModels.credential(member));
        Map<Credential, Long> added = new LinkedHashMap<>();
        for (Map.Entry<RawCredential, Long> member : raw.membersToAdd())
            added.put(RawModels.credential(member.getKey()), member.getValue());
        return new UpdateCommittee(GovAction.previous(raw.previousActionId()), removed, added, raw.quorum());
    }


}
