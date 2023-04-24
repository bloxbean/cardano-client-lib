package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.crypto.Keys;
import com.bloxbean.cardano.client.crypto.VerificationKey;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.Tuple;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@NoArgsConstructor
public class ScriptPubkey implements NativeScript {

    private final ScriptType type = ScriptType.sig;
    private String keyHash;

    public ScriptPubkey(String keyHash) {
        this.keyHash = keyHash;
    }

    public byte[] toBytes() {
        if (keyHash == null || keyHash.length() == 0)
            return new byte[0];

        byte[] keyHashBytes = new byte[0];
        try {
            keyHashBytes = HexUtil.decodeHexString(keyHash);
        } catch (Exception e) {
            log.error("Error ", e);
        }
        return keyHashBytes;
    }

    public DataItem serializeAsDataItem() {
        Array array = new Array();
        array.add(new UnsignedInteger(0));
        array.add(new ByteString(HexUtil.decodeHexString(keyHash)));
        return array;
    }

    public static ScriptPubkey deserialize(Array array) throws CborDeserializationException {
        ScriptPubkey scriptPubkey = new ScriptPubkey();
        ByteString keyHashBS = (ByteString) (array.getDataItems().get(1));
        scriptPubkey.setKeyHash(HexUtil.encodeHexString(keyHashBS.getBytes()));
        return scriptPubkey;
    }

    public static ScriptPubkey deserialize(JsonNode jsonNode) throws CborDeserializationException {
        ScriptPubkey scriptPubkey = new ScriptPubkey();
        String keyHash = jsonNode.get("keyHash").asText();
        scriptPubkey.setKeyHash(keyHash);
        return scriptPubkey;
    }

    public static ScriptPubkey create(VerificationKey vkey) {
        return new ScriptPubkey(KeyGenUtil.getKeyHash(vkey));
    }

    public static Tuple<ScriptPubkey, Keys> createWithNewKey() throws CborSerializationException {
        Keys keys = KeyGenUtil.generateKey();

        ScriptPubkey scriptPubkey = ScriptPubkey.create(keys.getVkey());
        return new Tuple<>(scriptPubkey, keys);
    }
}
