package com.bloxbean.cardano.client.transaction.model;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionOutput {
    private String address;
    //private BigInteger value;
    private Value value;

//    public byte[] getAddress() {
//        return address;
//    }
//
//    public void setAddress(byte[] address) {
//        this.address = address;
//    }
//
//    public BigInteger getValue() {
//        return value;
//    }
//
//    public void setValue(BigInteger value) {
//        this.value = value;
//    }


    public void serialize(ArrayBuilder builder) throws CborException, AddressExcepion {
        byte[] addressByte = Account.toBytes(address);
        builder.add(addressByte);
               // .add(value.longValue()) //TODO BigInteger to long value

//                .add(value.getCoin().longValue()); //TODO BigInteger to long value

        if(value.getMultiAssets() != null && value.getMultiAssets().size() > 0) {
            ArrayBuilder coinAssetArrayBuilder = builder.addArray();
            if(value.getCoin() != null)
                coinAssetArrayBuilder.add(value.getCoin().longValue());//TODO

            MapBuilder valueMapBuilder = coinAssetArrayBuilder.addMap();
            value.serializeMultiAsset(valueMapBuilder);
        } else {
            builder.add(value.getCoin().longValue()); //TODO
        }

        builder.end();
    }

    @Override
    public String toString() {
        return "TransactionOutput{" +
                "address='" + address + '\'' +
                ", value=" + value +
                '}';
    }
}
