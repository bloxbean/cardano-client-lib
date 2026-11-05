package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.raw.RawRedeemer;
import com.bloxbean.cardano.client.util.Tuple;
import lombok.*;

import java.math.BigInteger;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Redeemer {
    private RedeemerTag tag;
    private BigInteger index; //TODO BigInteger ??
    private PlutusData data;
    private ExUnits exUnits;

    /**
     * This method is deprecated. Use setIndex(int index) instead.
     */
    @Deprecated(forRemoval = true)
    public void setIndex(BigInteger index) {
        this.index = index;
    }

    public void setIndex(int index) {
        this.index = BigInteger.valueOf(index);
    }

    public Array serializePreConway() throws CborSerializationException {
        Array redeemerArray = new Array();

        if (tag == null)
            throw new CborSerializationException("Redeemer tag cannot be null");
        redeemerArray.add(new UnsignedInteger(tag.value));

        redeemerArray.add(new UnsignedInteger(index));

        if (data != null)
            redeemerArray.add(data.serialize());
        else
            throw new CborSerializationException("Redeemer data cannot be null");

        if (exUnits != null)
            redeemerArray.add(exUnits.serialize());
        else
            throw new CborSerializationException("Redeemer exUnits cannot be null");

        return redeemerArray;
    }

    public Tuple<Array, Array> serialize() throws CborSerializationException {
        Array keyArray = new Array();

        if (tag == null)
            throw new CborSerializationException("Redeemer tag cannot be null");
        keyArray.add(new UnsignedInteger(tag.value));

        keyArray.add(new UnsignedInteger(index));

        Array valueArray = new Array();
        if (data != null)
            valueArray.add(data.serialize());
        else
            throw new CborSerializationException("Redeemer data cannot be null");

        if (exUnits != null)
            valueArray.add(exUnits.serialize());
        else
            throw new CborSerializationException("Redeemer exUnits cannot be null");

        return new Tuple<>(keyArray, valueArray);
    }

    @Deprecated(forRemoval = true)
    /**
     * This method is deprecated and is there for backward compatibility, but not used in CCL.
     */
    public static Redeemer deserialize(Array redeemerDI) throws CborDeserializationException {
        return deserializePreConway(redeemerDI);
    }

    public static Redeemer deserializePreConway(Array redeemerDI) throws CborDeserializationException {
        try {
            return from(RawRedeemer.lenient(CborSerializationUtil.span(redeemerDI)));
        } catch (CborRuntimeException e) {
            throw new CborDeserializationException("Redeemer deserialization failed", e);
        }
    }

    public static Redeemer deserialize(Array redeemerKey, Array redeemerValue) throws CborDeserializationException {
        try {
            return from(RawRedeemer.lenient(CborSerializationUtil.span(redeemerKey),
                    CborSerializationUtil.span(redeemerValue)));
        } catch (CborRuntimeException e) {
            throw new CborDeserializationException("Redeemer deserialization failed", e);
        }
    }

    /**
     * @param raw a redeemer as received, read as {@link RawRedeemer#lenient} reads one
     * @return the model, whose tag is null if the number is no tag's
     * @throws CborDeserializationException if the data is not Plutus data, or the execution units are not two unsigned
     *                                      integers in a definite array
     */
    public static Redeemer from(RawRedeemer raw) throws CborDeserializationException {
        Redeemer redeemer = new Redeemer();
        redeemer.tag = RedeemerTag.fromValue(raw.tag());
        redeemer.index = raw.unsignedIndex();
        redeemer.data = PlutusData.deserialize(raw.data().bytes());
        redeemer.exUnits = ExUnits.from(raw.units());
        return redeemer;
    }

    public static class RedeemerBuilder {
        @Deprecated
        public RedeemerBuilder index(BigInteger index) {
            this.index = index;
            return this;
        }

        public RedeemerBuilder index(int index) {
            this.index = BigInteger.valueOf(index);
            return this;
        }
    }
}

