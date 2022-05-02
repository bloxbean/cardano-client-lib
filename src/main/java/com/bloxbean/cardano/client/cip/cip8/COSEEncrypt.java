package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Accessors(fluent = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class COSEEncrypt implements COSEItem {
    private Headers headers;
    private byte[] ciphertext;
    private List<COSERecipient> recipients = new ArrayList<>();

    public static COSEEncrypt deserialize(Array consencArray) {
        List<DataItem> dataItems = consencArray.getDataItems();
        if (dataItems.size() != 4)
            throw new CborRuntimeException(
                    String.format("De-serialization error. Expected array size: 4, Found: %s", dataItems.size()));

        Headers headers = Headers.deserialize(new DataItem[]{dataItems.get(0), dataItems.get(1)});
        byte[] ciphertext = ((ByteString) dataItems.get(2)).getBytes();

        Array recipientArray = ((Array) dataItems.get(3));
        List<COSERecipient> recipients = recipientArray.getDataItems().stream()
                .map(dataItem -> COSERecipient.deserialize((Array) dataItem))
                .collect(Collectors.toList());

        return new COSEEncrypt(headers, ciphertext, recipients);
    }

    public COSEEncrypt recipient(COSERecipient recipient) {
        recipients.add(recipient);
        return this;
    }

    @Override
    public Array serialize() {
        Array cosencArray = new Array();

        //2 DataItems from Headers
        Arrays.stream(headers.serialize())
                .forEach(header -> cosencArray.add(header));

        if (ciphertext != null)
            cosencArray.add(new ByteString(ciphertext));
        else
            cosencArray.add(SimpleValue.NULL);

        if (recipients != null && recipients.size() > 0) {
            Array rcptArray = new Array();
            recipients.stream()
                    .forEach(coseRecipient -> rcptArray.add(coseRecipient.serialize()));

            cosencArray.add(rcptArray);
        } else
            throw new CborRuntimeException("Serialization error. At least 1 recipient is required.");

        return cosencArray;
    }
}
