package com.bloxbean.cardano.client.transaction.spec.governance;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.transaction.raw.RawDRep;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class DRep {
    private DRepType type;
    private String hash; //key hash or script hash

    public static DRep addrKeyHash(byte[] bytes) {
        return addrKeyHash(HexUtil.encodeHexString(bytes));
    }

    public static DRep addrKeyHash(String addrKeyHash) {
        DRep drep = new DRep();
        drep.type = DRepType.ADDR_KEYHASH;
        drep.hash = addrKeyHash;

        return drep;
    }

    public static DRep scriptHash(byte[] scriptHash) {
        return scriptHash(HexUtil.encodeHexString(scriptHash));
    }

    public static DRep scriptHash(String scriptHash) {
        DRep drep = new DRep();
        drep.type = DRepType.SCRIPTHASH;
        drep.hash = scriptHash;

        return drep;
    }

    public static DRep abstain() {
        DRep drep = new DRep();
        drep.type = DRepType.ABSTAIN;

        return drep;
    }

    public static DRep noConfidence() {
        DRep drep = new DRep();
        drep.type = DRepType.NO_CONFIDENCE;

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

    public static DRep deserialize(DataItem di) {
        return RawModels.drep(RawDRep.lenient(RawModels.span(di)));
    }
}
