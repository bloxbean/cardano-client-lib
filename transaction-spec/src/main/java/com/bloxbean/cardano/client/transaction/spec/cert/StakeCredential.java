package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.crypto.VerificationKey;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Script;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.math.BigInteger;
import java.util.List;

@Getter
@EqualsAndHashCode
public class StakeCredential {
    private StakeCredType type;
    private byte[] hash;

    private StakeCredential(StakeCredType type, byte[] hash) {
        this.type = type;
        this.hash = hash;
    }

    public static StakeCredential fromKey(VerificationKey vkey) {
        StakeCredential stakeCredential = fromKey(vkey.getBytes());
        return stakeCredential;
    }

    public static StakeCredential fromKey(byte[] key) {
        byte[] keyHash = Blake2bUtil.blake2bHash224(key);
        StakeCredential stakeCredential = new StakeCredential(StakeCredType.ADDR_KEYHASH, keyHash);
        return stakeCredential;
    }

    public static StakeCredential fromKeyHash(byte[] keyHash) {
        StakeCredential stakeCredential = new StakeCredential(StakeCredType.ADDR_KEYHASH, keyHash);
        return stakeCredential;
    }

    public static StakeCredential fromScriptHash(byte[] scriptHash) {
        StakeCredential stakeCredential = new StakeCredential(StakeCredType.SCRIPTHASH, scriptHash);
        return stakeCredential;
    }

    /**
     * @param script
     * @return StakeCredential
     * @throws CborRuntimeException
     */
    public static StakeCredential fromScript(Script script) {
        try {
            return new StakeCredential(StakeCredType.SCRIPTHASH, script.getScriptHash());
        } catch (CborSerializationException e) {
            throw new CborRuntimeException("Cbor serialization failed for the script", e);
        }
    }

    public static StakeCredential deserialize(Array stakeCredArray) throws CborDeserializationException {
        List<DataItem> dataItemList = stakeCredArray.getDataItems();
        if (dataItemList == null || dataItemList.size() != 2)
            throw new CborDeserializationException("StakeCredential deserialization failed. Invalid number of DataItem(s) : "
                    + (dataItemList != null ? String.valueOf(dataItemList.size()) : null));

        UnsignedInteger typeDI = (UnsignedInteger) dataItemList.get(0);
        ByteString hashDI = (ByteString) dataItemList.get(1);

        BigInteger typeBI = typeDI.getValue();
        if (typeBI.intValue() == 0) {
            return StakeCredential.fromKeyHash(hashDI.getBytes());
        } else if (typeBI.intValue() == 1) {
            return StakeCredential.fromScriptHash(hashDI.getBytes());
        } else {
            throw new CborDeserializationException("StakeCredential deserialization failed. Invalid StakeCredType : "
                    + typeBI.intValue());
        }
    }

    public Array serialize() throws CborSerializationException {
        Array array = new Array();
        if (type == StakeCredType.ADDR_KEYHASH) {
            array.add(new UnsignedInteger(0));
        } else if (type == StakeCredType.SCRIPTHASH) {
            array.add(new UnsignedInteger(1));
        } else {
            throw new CborSerializationException("Invalid stake credential type : " + type);
        }

        array.add(new ByteString(hash));
        return array;
    }

    public String getCborHex() throws CborSerializationException {
        try {
            return HexUtil.encodeHexString(CborSerializationUtil.serialize(serialize()));
        } catch (CborException e) {
            throw new CborSerializationException("Cbor serialization error", e);
        }
    }

}
