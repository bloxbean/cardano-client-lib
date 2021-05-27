package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

            if(value.getCoin() != null)
                coinAssetArray.add(new UnsignedInteger(value.getCoin()));

            Map valueMap = value.serialize();
            coinAssetArray.add(valueMap);

            array.add(coinAssetArray);

        } else {
            array.add(new UnsignedInteger(value.getCoin()));
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
        if(MajorType.UNSIGNED_INTEGER.equals(valueItem.getMajorType())) {
            value = new Value();
            value.setCoin(((UnsignedInteger)valueItem).getValue());
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
