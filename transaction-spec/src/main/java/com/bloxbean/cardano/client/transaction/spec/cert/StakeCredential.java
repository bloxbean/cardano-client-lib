package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.crypto.VerificationKey;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Script;
import com.bloxbean.cardano.client.transaction.raw.RawCredential;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;

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
        try {
            return RawModels.stakeCredential(RawCredential.of(CborSerializationUtil.span(stakeCredArray)));
        } catch (CborRuntimeException e) {
            throw new CborDeserializationException("StakeCredential deserialization failed", e);
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
