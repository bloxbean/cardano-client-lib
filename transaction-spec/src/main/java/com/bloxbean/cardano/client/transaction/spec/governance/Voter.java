package com.bloxbean.cardano.client.transaction.spec.governance;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.address.Credential;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;

import static com.bloxbean.cardano.client.transaction.spec.governance.VoterType.CONSTITUTIONAL_COMMITTEE_HOT_KEY_HASH;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Voter {
    private VoterType type;
    private Credential credential; //key hash or script hash

    public DataItem serialize() {
        Objects.requireNonNull(type);
        Objects.requireNonNull(credential);

        Array array = new Array();
        int key;
        switch (type) {
            case CONSTITUTIONAL_COMMITTEE_HOT_KEY_HASH:
                key = 0; break;
            case CONSTITUTIONAL_COMMITTEE_HOT_SCRIPT_HASH:
                key = 1; break;
            case DREP_KEY_HASH:
                key = 2; break;
            case DREP_SCRIPT_HASH:
                key = 3; break;
            case STAKING_POOL_KEY_HASH:
                key = 4; break;
            default:
                throw new IllegalArgumentException("Invalid voter type : " + type);
        }

        array.add(new UnsignedInteger(key));
        array.add(new ByteString(credential.getBytes()));

        return array;
    }

    public static Voter deserialize(Array voterArray) {
        if (voterArray != null && voterArray.getDataItems().size() != 2)
            throw new IllegalArgumentException("Invalid voter array. Expected 2 items. Found : "
                    + voterArray.getDataItems().size());

        List<DataItem> diList = voterArray.getDataItems();
        int key = ((UnsignedInteger) diList.get(0)).getValue().intValue();
        byte[] hash = ((ByteString) diList.get(1)).getBytes();

        switch (key) {
            case 0:
                return new Voter(CONSTITUTIONAL_COMMITTEE_HOT_KEY_HASH, Credential.fromKey(hash));
            case 1:
                return new Voter(VoterType.CONSTITUTIONAL_COMMITTEE_HOT_SCRIPT_HASH, Credential.fromScript(hash));
            case 2:
                return new Voter(VoterType.DREP_KEY_HASH, Credential.fromKey(hash));
            case 3:
                return new Voter(VoterType.DREP_SCRIPT_HASH, Credential.fromScript(hash));
            case 4:
                return new Voter(VoterType.STAKING_POOL_KEY_HASH, Credential.fromKey(hash));
            default:
                throw new IllegalArgumentException("Invalid voter key. Expected 0,1,2,3,4. Found : " + key);
        }
    }
}
