package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.crypto.VerificationKey;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import static com.bloxbean.cardano.client.crypto.KeyGenUtil.blake2bHash224;

@Data
public class ScriptPubkey implements NativeScript {
    private final static Logger LOG = LoggerFactory.getLogger(ScriptPubkey.class);

    private String keyHash;
    private ScriptType type;

    public ScriptPubkey() {
        this.type = ScriptType.sig;
    }

    public ScriptPubkey(String keyHash) {
        this();
        this.keyHash = keyHash;
    }

    public byte[] toBytes() {
        if (keyHash == null || keyHash.length() == 0)
            return new byte[0];

        byte[] keyHashBytes = new byte[0];
        try {
            keyHashBytes = HexUtil.decodeHexString(keyHash);
        } catch (Exception e) {
            LOG.error("Error ", e);
        }
        return keyHashBytes;
    }

    public byte[] serialize() throws CborException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CborBuilder cborBuilder = new CborBuilder();
//
//        ArrayBuilder arrayBuilder = cborBuilder.addArray();
        Array array = (Array) serializeAsDataItem();

        cborBuilder.add(array);
//
//
        new CborEncoder(baos).nonCanonical().encode(cborBuilder.build());
        byte[] encodedBytes = baos.toByteArray();
        return encodedBytes;
//
//        return encodedBytes;
    }

    public DataItem serializeAsDataItem() throws CborException {
        Array array = new Array();
        array.add(new UnsignedInteger(0));
        array.add(new ByteString(HexUtil.decodeHexString(keyHash)));
        return array;
    }

    @JsonIgnore
    public String getPolicyId() throws CborException {
        byte[] first = new byte[]{00};
        byte[] serializedBytes = this.serialize();
        byte[] finalBytes = ByteBuffer.allocate(first.length + serializedBytes.length)
                .put(first)
                .put(serializedBytes)
                .array();

        return Hex.toHexString(blake2bHash224(finalBytes));
    }

    public static ScriptPubkey create(VerificationKey vkey) {
        return new ScriptPubkey(KeyGenUtil.getKeyHash(vkey));
    }
}
