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
public class PasswordEncryption implements COSEItem {
    private final COSEEncrypt0 coseEncrypt0;

    public PasswordEncryption(COSEEncrypt0 coseEncrypt0) {
        this.coseEncrypt0 = coseEncrypt0;
    }

    public static PasswordEncryption deserialize(@NonNull DataItem dataItem) {
        Tag tag = dataItem.getTag();
        if (tag == null || tag.getValue() != 16)
            throw new CborRuntimeException("Cbor de-serialization error. Invalid or null tag. Expected value: 16");

        return new PasswordEncryption(COSEEncrypt0.deserialize((Array) dataItem));
    }

    @Override
    public DataItem serialize() {
        if (coseEncrypt0 == null)
            throw new CborRuntimeException("Cbor serialization error. COSEEncrypt can't be null");

        Array coseEncryptArr = coseEncrypt0.serialize();
        coseEncryptArr.setTag(16);

        return coseEncryptArr;
    }
}
