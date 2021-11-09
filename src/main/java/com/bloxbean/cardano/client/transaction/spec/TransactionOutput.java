package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.*;
import co.nstant.in.cbor.model.Number;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.exception.AddressExcepion;
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
public class TransactionOutput {
    private String address;
    private Value value;

    //transaction_output = [address, amount : value]
    public Array serialize() throws CborSerializationException, AddressExcepion {
        Array array = new Array();
        byte[] addressByte = Account.toBytes(address);
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

        return array;
    }

    public static TransactionOutput deserialize(Array ouptutItem) throws CborDeserializationException {
        List<DataItem> items = ouptutItem.getDataItems();
        TransactionOutput output = new TransactionOutput();

        if(items == null || items.size() != 2) {
            throw new CborDeserializationException("TransactionOutput deserialization failed. Invalid no of DataItems");
        }

        ByteString addrByteStr = (ByteString)items.get(0);
        if(addrByteStr != null) {
            try {
                output.setAddress(Account.bytesToBech32(addrByteStr.getBytes()));
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

        output.setValue(value);
        return output;
    }

    @Override
    public String toString() {
        return "TransactionOutput{" +
                "address='" + address + '\'' +
                ", value=" + value +
                '}';
    }
}
