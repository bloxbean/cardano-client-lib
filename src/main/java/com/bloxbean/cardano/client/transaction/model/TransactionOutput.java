package com.bloxbean.cardano.client.transaction.model;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
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

    public void serialize(ArrayBuilder builder) throws CborException, AddressExcepion {
        byte[] addressByte = Account.toBytes(address);
        builder.add(addressByte);

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
