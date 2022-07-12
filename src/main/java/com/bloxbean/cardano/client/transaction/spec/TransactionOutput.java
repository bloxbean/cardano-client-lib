package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Number;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.address.util.AddressUtil;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.script.Script;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder=true)
public class TransactionOutput {
    private String address;
    private Value value;
    private byte[] datumHash;

    //transaction_output = [address, amount : value, ? datum_hash : $hash32]

    //babbage
    private PlutusData inlineDatum;
    private byte[] scriptRef;

    //TODO -- Remove later if not required
    //serialize to Alonzo era (legacy) format
    //boolean alonzoEraFormat;

    public TransactionOutput(String address, Value value) {
        this.address = address;
        this.value = value;
    }

    //custom setters
    public void setScriptRef(byte[] scriptRef) {
        this.scriptRef = scriptRef;
    }

    //custom setters
    public void setScriptRef(Script script) {
        if (script == null)
            return;

        try {
            this.scriptRef = script.serialize();
        } catch (CborSerializationException e) {
            throw new CborRuntimeException(e);
        }
    }

    public DataItem serialize() throws CborSerializationException, AddressExcepion {
// TODO -- Remove later if not required.
//        if (alonzoEraFormat) {
//            if (datum == null && scriptRef == null)
//                return serializeAlonzo();
//            else
//                throw new CborSerializationException("Legacy format (Alonzo era) can't have datum or script_ref value set");
//
//        }
        //For now, serialize to legacy format if inlineDatum and scriptRef are not set
        if (inlineDatum == null && scriptRef == null)
            return serializeAlonzo();
        else {
            return serializePostAlonzo();
        }
    }

    private Map serializePostAlonzo() throws AddressExcepion, CborSerializationException {
        Map map = new Map();

        byte[] addressByte = AddressUtil.addressToBytes(address);
        map.put(new UnsignedInteger(0), new ByteString(addressByte));

        if(value == null)
            throw new CborSerializationException("Value cannot be null");

        if(value.getMultiAssets() != null && value.getMultiAssets().size() > 0) {
            Array coinAssetArray = new Array();

            if(value.getCoin() != null) {
                if(value.getCoin().compareTo(BigInteger.ZERO) == 0 || value.getCoin().compareTo(BigInteger.ZERO) == 1) {
                    coinAssetArray.add(new UnsignedInteger(value.getCoin()));
                } else {
                    coinAssetArray.add(new NegativeInteger(value.getCoin()));
                }
            } else {
                coinAssetArray.add(new UnsignedInteger(BigInteger.ZERO));
            }

            Map valueMap = value.serialize();
            coinAssetArray.add(valueMap);

            map.put(new UnsignedInteger(1), coinAssetArray);

        } else {
            if(value.getCoin() != null) {
                if (value.getCoin().compareTo(BigInteger.ZERO) == 0 || value.getCoin().compareTo(BigInteger.ZERO) == 1) {
                    map.put(new UnsignedInteger(1), new UnsignedInteger(value.getCoin()));
                } else {
                    map.put(new UnsignedInteger(1), new NegativeInteger(value.getCoin()));
                }
            } else {
                map.put(new UnsignedInteger(1), new UnsignedInteger(BigInteger.ZERO));
            }
        }

        if (datumHash != null && inlineDatum != null)
            throw new CborSerializationException("Only one can be set. datumHash or datum");

        Array datumArray = new Array();
        if(datumHash != null) {
            datumArray.add(new UnsignedInteger(0));
            datumArray.add(new ByteString(datumHash));
            map.put(new UnsignedInteger(2), datumArray);
        } else if(inlineDatum != null) {
            DataItem inlineDatumDI = inlineDatum.serialize();
            ByteString inlineDatumBS = null;
            try {
                inlineDatumBS = new ByteString(CborSerializationUtil.serialize(inlineDatumDI));
                inlineDatumBS.setTag(new Tag(24));
            } catch (CborException e) {
                throw new CborSerializationException("Cbor serialization error", e);
            }
            datumArray.add(new UnsignedInteger(1));
            datumArray.add(inlineDatumBS);
            map.put(new UnsignedInteger(2), datumArray);
        }

        if (scriptRef != null) {
            ByteString scriptRefBS = new ByteString(scriptRef);
            scriptRefBS.setTag(new Tag(24)); //tag 6.24
            map.put(new UnsignedInteger(3), scriptRefBS);
        }

        return map;
    }

    private Array serializeAlonzo() throws CborSerializationException, AddressExcepion {
        Array array = new Array();
        byte[] addressByte = AddressUtil.addressToBytes(address);
        array.add(new ByteString(addressByte));

        if(value == null)
            throw new CborSerializationException("Value cannot be null");

        if(value.getMultiAssets() != null && value.getMultiAssets().size() > 0) {
            Array coinAssetArray = new Array();

            if(value.getCoin() != null) {
                if(value.getCoin().compareTo(BigInteger.ZERO) == 0 || value.getCoin().compareTo(BigInteger.ZERO) == 1) {
                    coinAssetArray.add(new UnsignedInteger(value.getCoin()));
                } else {
                    coinAssetArray.add(new NegativeInteger(value.getCoin()));
                }
            } else {
                coinAssetArray.add(new UnsignedInteger(BigInteger.ZERO));
            }

            Map valueMap = value.serialize();
            coinAssetArray.add(valueMap);

            array.add(coinAssetArray);

        } else {
            if(value.getCoin() != null) {
                if (value.getCoin().compareTo(BigInteger.ZERO) == 0 || value.getCoin().compareTo(BigInteger.ZERO) == 1) {
                    array.add(new UnsignedInteger(value.getCoin()));
                } else {
                    array.add(new NegativeInteger(value.getCoin()));
                }
            } else {
                array.add(new UnsignedInteger(BigInteger.ZERO));
            }
        }

        if(datumHash != null)
            array.add(new ByteString(datumHash));

        return array;
    }

    public static TransactionOutput deserialize(DataItem dataItem) throws CborDeserializationException {
        if (MajorType.ARRAY == dataItem.getMajorType()) { //Alonzo (Legacy)
            return deserializeAlonzo((Array) dataItem);
        } else if (MajorType.MAP == dataItem.getMajorType()) { //Post Alonzo
            return deserializePostAlonzo((Map) dataItem);
        } else
            throw new CborDeserializationException("Invalid type for TransactionOutput : " + dataItem.getMajorType());
    }

    private static TransactionOutput deserializePostAlonzo(Map ouptutItem) throws CborDeserializationException {
        TransactionOutput output = new TransactionOutput();

        //address
        ByteString addrByteStr = (ByteString) ouptutItem.get(new UnsignedInteger(0));
        if(addrByteStr != null) {
            try {
                output.setAddress(AddressUtil.bytesToAddress(addrByteStr.getBytes()));
            } catch (Exception e) {
                throw new CborDeserializationException("Bytes cannot be converted to bech32 address", e);
            }
        }

        //value
        Value value = null;
        DataItem valueItem = ouptutItem.get(new UnsignedInteger(1));
        if(MajorType.UNSIGNED_INTEGER.equals(valueItem.getMajorType()) || MajorType.NEGATIVE_INTEGER.equals(valueItem.getMajorType())) {
            value = new Value();
            value.setCoin(((Number) valueItem).getValue());
        } else if(MajorType.BYTE_STRING.equals(valueItem.getMajorType())) { //For BigNum. >  2 pow 64 Tag 2
            if(valueItem.getTag().getValue() == 2) {
                value = new Value();
                value.setCoin(new BigInteger(((ByteString) valueItem).getBytes()));
            } else if(valueItem.getTag().getValue() == 3) {
                value = new Value();
                value.setCoin(new BigInteger(((ByteString)valueItem).getBytes()).multiply(BigInteger.valueOf(-1)));
            }
        } else if(MajorType.ARRAY.equals(valueItem.getMajorType())) {
            Array coinAssetArray = (Array)valueItem;
            value = Value.deserialize(coinAssetArray);
        }
        output.setValue(value);

        //datum_options
        byte[] datumHash = null;
        PlutusData inlineDatum = null;
        Array datumOptionsItem = (Array) ouptutItem.get(new UnsignedInteger(2));
        if (datumOptionsItem != null) {
            List<DataItem> datumOptionsList = datumOptionsItem.getDataItems();
            if (datumOptionsList.size() != 2)
                throw new CborDeserializationException("Invalid size for datum_options : " + datumOptionsList.size());

            if (new UnsignedInteger(0).equals(datumOptionsList.get(0))) { //datum hash
                datumHash = ((ByteString) datumOptionsList.get(1)).getBytes();
            } else if (new UnsignedInteger(1).equals(datumOptionsList.get(0))) { //datum
                ByteString inlineDatumBS = (ByteString) datumOptionsList.get(1);
                inlineDatum = PlutusData.deserialize(inlineDatumBS.getBytes());
            }
        }
        output.setDatumHash(datumHash);
        output.setInlineDatum(inlineDatum);

        //script_ref
        ByteString scriptRefBs = (ByteString) ouptutItem.get(new UnsignedInteger(3));
        if (scriptRefBs != null) {
            output.setScriptRef(scriptRefBs.getBytes());
        }

        return output;
    }

    private static TransactionOutput deserializeAlonzo(Array ouptutItem) throws CborDeserializationException {
        List<DataItem> items = ouptutItem.getDataItems();
        TransactionOutput output = new TransactionOutput();

        if(items == null || (items.size() != 2 && items.size() != 3)) {
            throw new CborDeserializationException("TransactionOutput deserialization failed. Invalid no of DataItems");
        }

        ByteString addrByteStr = (ByteString)items.get(0);
        if(addrByteStr != null) {
            try {
                output.setAddress(AddressUtil.bytesToAddress(addrByteStr.getBytes()));
            } catch (Exception e) {
                throw new CborDeserializationException("Bytes cannot be converted to bech32 address", e);
            }
        }

        Value value = null;
        DataItem valueItem = items.get(1);
        if(MajorType.UNSIGNED_INTEGER.equals(valueItem.getMajorType()) || MajorType.NEGATIVE_INTEGER.equals(valueItem.getMajorType())) {
            value = new Value();
            value.setCoin(((Number) valueItem).getValue());
        } else if(MajorType.BYTE_STRING.equals(valueItem.getMajorType())) { //For BigNum. >  2 pow 64 Tag 2
            if(valueItem.getTag().getValue() == 2) {
                value = new Value();
                value.setCoin(new BigInteger(((ByteString) valueItem).getBytes()));
            } else if(valueItem.getTag().getValue() == 3) {
                value = new Value();
                value.setCoin(new BigInteger(((ByteString)valueItem).getBytes()).multiply(BigInteger.valueOf(-1)));
            }
        } else if(MajorType.ARRAY.equals(valueItem.getMajorType())) {
            Array coinAssetArray = (Array)valueItem;
            value = Value.deserialize(coinAssetArray);

        }

        if (items.size() == 3) {
            ByteString datumBytes = (ByteString) items.get(2);
            if(datumBytes != null) {
                output.setDatumHash(datumBytes.getBytes());
            }
        }

        output.setValue(value);
        return output;
    }


    @Override
    public String toString() {
        return "TransactionOutput{" +
                "address='" + address + '\'' +
                ", value=" + value +
                ", datumHash=" + (datumHash == null? null : HexUtil.encodeHexString(datumHash)) +
                ", inlineDatum=" + (inlineDatum == null? null: inlineDatum.toString()) +
                ", scriptRef=" + (scriptRef == null? null: scriptRef.toString()) +
                '}';
    }

    public static class TransactionOutputBuilder {
        public TransactionOutputBuilder scriptRef(byte[] scriptRef) {
            this.scriptRef = scriptRef;
            return this;
        }

        public TransactionOutputBuilder scriptRef(Script script) {
            if (script == null)
                return this;

            try {
                this.scriptRef = script.serialize();
            } catch (CborSerializationException e) {
                throw new CborRuntimeException(e);
            }
            return this;
        }
    }
}
