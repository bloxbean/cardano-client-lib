package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.List;

@Accessors(fluent = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class COSERecipient implements COSEItem {
    private Headers headers;
    private byte[] ciphertext;

    public static COSERecipient deserialize(@NonNull Array coseRecptArray) {

        List<DataItem> dataItems = coseRecptArray.getDataItems();
        if (dataItems.size() != 3)
            throw new CborRuntimeException(
                    String.format("De-serialization error. Expected array size: 3, Found: %s", dataItems.size()));

        Headers headers = Headers.deserialize(new DataItem[]{dataItems.get(0), dataItems.get(1)});
        byte[] ciphertext = ((ByteString) dataItems.get(2)).getBytes();

        return new COSERecipient(headers, ciphertext);
    }

    @Override
    public Array serialize() {
        Array cosRecptArray = new Array();

        //2 DataItems from Headers
        Arrays.stream(headers.serialize())
                .forEach(header -> cosRecptArray.add(header));

        if (ciphertext != null)
            cosRecptArray.add(new ByteString(ciphertext));
        else
            cosRecptArray.add(SimpleValue.NULL);

        return cosRecptArray;
    }
}
