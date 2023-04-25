package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    public Array serialize() throws CborSerializationException {
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

    public static Redeemer deserialize(Array redeemerDI) throws CborDeserializationException {
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
        }

        //Index
        redeemer.setIndex(((UnsignedInteger) indexDI).getValue());
        redeemer.setData(PlutusData.deserialize(dataDI));
        redeemer.setExUnits(ExUnits.deserialize((Array) exUnitDI));

        return redeemer;
    }

}

