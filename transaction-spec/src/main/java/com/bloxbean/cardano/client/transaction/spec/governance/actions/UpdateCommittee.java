package com.bloxbean.cardano.client.transaction.spec.governance.actions;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.common.cbor.custom.SortedMap;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.UnitInterval;
import com.bloxbean.cardano.client.transaction.spec.Comparators;
import com.bloxbean.cardano.client.transaction.util.CredentialSerializer;
import lombok.*;

import java.util.Map;
import java.util.*;

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.toUnsignedLong;
import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.unsignedLongItem;
import static com.bloxbean.cardano.client.transaction.util.RationalNumberUtil.toUnitInterval;
import static com.bloxbean.cardano.client.transaction.util.SerializationUtil.createArray;
import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.withoutBreak;

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
        List<DataItem> govActionDIList = govActionArray.getDataItems();

        DataItem actionIdDI = govActionDIList.get(1);
        GovActionId govActionId = GovAction.getGovActionId(actionIdDI);

        //committee_cold_credentials
        List<DataItem> committeeColdCredArray = withoutBreak(((Array) govActionDIList.get(2)).getDataItems());
        Set<Credential> committeeColdCredSet = new LinkedHashSet<>();
        committeeColdCredArray.stream()
                .map(coldCredDI -> CredentialSerializer.deserialize((Array) coldCredDI))
                .forEach(coldCred -> committeeColdCredSet.add(coldCred));

        //committee_cold_credential => epoch
        java.util.Map<Credential, Long> committeeColdCredEpochMap = new java.util.LinkedHashMap<>();
        co.nstant.in.cbor.model.Map committeeColdCredEpochMapDI = (co.nstant.in.cbor.model.Map) govActionDIList.get(3);
        for (DataItem key : committeeColdCredEpochMapDI.getKeys()) {
            Credential cred = CredentialSerializer.deserialize((Array) key);
            long epoch = toUnsignedLong(committeeColdCredEpochMapDI.get(key));
            committeeColdCredEpochMap.put(cred, epoch);
        }

        //unit_interval
        UnitInterval unitInterval = toUnitInterval((RationalNumber) govActionDIList.get(4));
        return new UpdateCommittee(govActionId, committeeColdCredSet, committeeColdCredEpochMap, unitInterval);
    }


}
