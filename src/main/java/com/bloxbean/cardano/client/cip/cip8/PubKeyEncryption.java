package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Tag;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
@Getter
@EqualsAndHashCode
@ToString
public class PubKeyEncryption implements COSEItem {
    private final COSEEncrypt coseEncrypt;

    public PubKeyEncryption(COSEEncrypt coseEncrypt) {
        this.coseEncrypt = coseEncrypt;
    }

    public static PubKeyEncryption deserialize(@NonNull DataItem dataItem) {
        Tag tag = dataItem.getTag();
        if (tag == null || tag.getValue() != 96)
            throw new CborRuntimeException("Cbor de-serialization error. Invalid or null tag. Expected value: 96");

        return new PubKeyEncryption(COSEEncrypt.deserialize((Array) dataItem));
    }

    @Override
    public DataItem serialize() {
        if (coseEncrypt == null)
            throw new CborRuntimeException("Cbor serialization error. COSEEncrypt can't be null");

        Array coseEncryptArr = coseEncrypt.serialize();
        coseEncryptArr.setTag(96);

        return coseEncryptArr;
    }
}
