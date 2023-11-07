package com.bloxbean.cardano.client.transaction.spec.governance;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.toHex;
import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.toInt;

@Getter
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class Drep {
    private DrepType type;
    private String hash; //key hash or script hash

    public static Drep addrKeyHash(String addrKeyHash) {
        Drep drep = new Drep();
        drep.type = DrepType.ADDR_KEYHASH;
        drep.hash = addrKeyHash;

        return drep;
    }

    public static Drep scriptHash(String scriptHash) {
        Drep drep = new Drep();
        drep.type = DrepType.SCRIPTHASH;
        drep.hash = scriptHash;

        return drep;
    }

    public static Drep abstain() {
        Drep drep = new Drep();
        drep.type = DrepType.ABSTAIN;

        return drep;
    }

    public static Drep noConfidence() {
        Drep drep = new Drep();
        drep.type = DrepType.NO_CONFIDENCE;

        return drep;
    }

    public DataItem serialize() {
        Array drepArray = new Array();
        switch (type) {
            case ADDR_KEYHASH:
                drepArray.add(new UnsignedInteger(0));
                drepArray.add(new ByteString(HexUtil.decodeHexString(hash)));
                break;
            case SCRIPTHASH:
                drepArray.add(new UnsignedInteger(1));
                drepArray.add(new ByteString(HexUtil.decodeHexString(hash)));
                break;
            case ABSTAIN:
                drepArray.add(new UnsignedInteger(2));
                break;
            case NO_CONFIDENCE:
                drepArray.add(new UnsignedInteger(3));
                break;
            default:
                throw new IllegalArgumentException("Invalid drep type: " + type);
        }

        return drepArray;
    }

    public static Drep deserialize(DataItem di) {
        List<DataItem> dataItemList = ((Array) di).getDataItems();

        int key = toInt(dataItemList.get(0));

        switch (key) {
            case 0:
                String addKeyHash = toHex(dataItemList.get(1));
                return Drep.addrKeyHash(addKeyHash);
            case 1:
                String scriptHash = toHex(dataItemList.get(1));
                return Drep.scriptHash(scriptHash);
            case 2:
                return Drep.abstain();
            case 3:
                return Drep.noConfidence();
            default:
                throw new IllegalArgumentException("Invalid Drep key: " + key);
        }
    }
}
