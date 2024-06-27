package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.util.Tuple;
import lombok.*;

import java.math.BigInteger;
import java.util.List;

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
        List<DataItem> redeemerDIList = redeemerDI.getDataItems();
        if (redeemerDIList == null || redeemerDIList.size() != 4)
            throw new CborDeserializationException("Redeemer deserialization error. Invalid no of DataItems");

        DataItem tagDI = redeemerDIList.get(0);
        DataItem indexDI = redeemerDIList.get(1);
        DataItem dataDI = redeemerDIList.get(2);
        DataItem exUnitDI = redeemerDIList.get(3);

        Redeemer redeemer = new Redeemer();

        //tag
        int tagValue = ((UnsignedInteger) tagDI).getValue().intValue();
        if (tagValue == 0) {
            redeemer.setTag(RedeemerTag.Spend);
        } else if (tagValue == 1) {
            redeemer.setTag(RedeemerTag.Mint);
        } else if (tagValue == 2) {
            redeemer.setTag(RedeemerTag.Cert);
        } else if (tagValue == 3) {
            redeemer.setTag(RedeemerTag.Reward);
        } else if (tagValue == 4) {
            redeemer.setTag(RedeemerTag.Voting);
        } else if (tagValue == 5) {
            redeemer.setTag(RedeemerTag.Proposing);
        }

        //Index
        redeemer.setIndex(((UnsignedInteger) indexDI).getValue());
        redeemer.setData(PlutusData.deserialize(dataDI));
        redeemer.setExUnits(ExUnits.deserialize((Array) exUnitDI));

        return redeemer;
    }

    public static Redeemer deserialize(Array redeemerKey, Array redeemerValue) throws CborDeserializationException {
        List<DataItem> redeemerKeyDIList = redeemerKey.getDataItems();
        if (redeemerKeyDIList == null || redeemerKeyDIList.size() != 2)
            throw new CborDeserializationException("Redeemer deserialization error. Invalid no of DataItems in key");

        List<DataItem> redeemerValueDIList = redeemerValue.getDataItems();
        if (redeemerValueDIList == null || redeemerValueDIList.size() != 2)
            throw new CborDeserializationException("Redeemer deserialization error. Invalid no of DataItems in value");

        DataItem tagDI = redeemerKeyDIList.get(0);
        DataItem indexDI = redeemerKeyDIList.get(1);

        DataItem dataDI = redeemerValueDIList.get(0);
        DataItem exUnitDI = redeemerValueDIList.get(1);

        Redeemer redeemer = new Redeemer();

        //tag
        int tagValue = ((UnsignedInteger) tagDI).getValue().intValue();
        if (tagValue == 0) {
            redeemer.setTag(RedeemerTag.Spend);
        } else if (tagValue == 1) {
            redeemer.setTag(RedeemerTag.Mint);
        } else if (tagValue == 2) {
            redeemer.setTag(RedeemerTag.Cert);
        } else if (tagValue == 3) {
            redeemer.setTag(RedeemerTag.Reward);
        } else if (tagValue == 4) {
            redeemer.setTag(RedeemerTag.Voting);
        } else if (tagValue == 5) {
            redeemer.setTag(RedeemerTag.Proposing);
        }

        //Index
        redeemer.setIndex(((UnsignedInteger) indexDI).getValue().intValue());
        redeemer.setData(PlutusData.deserialize(dataDI));
        redeemer.setExUnits(ExUnits.deserialize((Array) exUnitDI));

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

