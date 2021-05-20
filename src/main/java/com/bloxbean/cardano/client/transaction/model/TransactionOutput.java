package com.bloxbean.cardano.client.transaction.model;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionOutput {
    private String address;
    private Value value;

    //transaction_output = [address, amount : value]
    public Array serialize() throws CborException, AddressExcepion {
        Array array = new Array();
        byte[] addressByte = Account.toBytes(address);
        array.add(new ByteString(addressByte));

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

    @Override
    public String toString() {
        return "TransactionOutput{" +
                "address='" + address + '\'' +
                ", value=" + value +
                '}';
    }
}
