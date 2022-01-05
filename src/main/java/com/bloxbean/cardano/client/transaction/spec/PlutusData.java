package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Number;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;
import com.bloxbean.cardano.client.util.HexUtil;

public interface PlutusData {

//    plutus_data = ; New
//    constr<plutus_data>
//  / { * plutus_data => plutus_data }
//  / [ * plutus_data ]
//            / big_int
//  / bounded_bytes

//    big_int = int / big_uint / big_nint ; New
//    big_uint = #6.2(bounded_bytes) ; New
//    big_nint = #6.3(bounded_bytes) ; New

    DataItem serialize() throws CborSerializationException;

    static PlutusData deserialize(DataItem dataItem) throws CborDeserializationException {
        if (dataItem == null)
            return null;

        if (dataItem instanceof Number) {
            return BigIntPlutusData.deserialize((Number) dataItem);
        } else if (dataItem instanceof ByteString) {
            return BytesPlutusData.deserialize((ByteString) dataItem);
        } else if (dataItem instanceof Array) {
            if (dataItem.getTag() == null) {
                return ListPlutusData.deserialize((Array) dataItem);
            } else { //Tag found .. try Constr
                return ConstrPlutusData.deserialize(dataItem);
            }
        } else if (dataItem instanceof Map) {
            return MapPlutusData.deserialize((Map) dataItem);
        } else
            throw new CborDeserializationException("Cbor deserialization failed. Invalid type. " + dataItem);
    }

    default String getDatumHash() throws CborSerializationException, CborException {
        return HexUtil.encodeHexString(getDatumHashAsBytes());
    }

    default byte[] getDatumHashAsBytes() throws CborSerializationException, CborException {
        return KeyGenUtil.blake2bHash256(CborSerializationUtil.serialize(serialize()));
    }
}
