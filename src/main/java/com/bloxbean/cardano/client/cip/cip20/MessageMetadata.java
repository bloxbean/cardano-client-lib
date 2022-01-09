package com.bloxbean.cardano.client.cip.cip20;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnicodeString;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

//Implementation for https://cips.cardano.org/cips/cip20/
//This CIP describes a basic JSON schema to add messages/comments/memos as transaction metadata by using the metadatum label 674.
//Adding informational text, invoice-numbers or similar to a transaction on the cardano blockchain.
@Slf4j
public class MessageMetadata extends CBORMetadata {

    private final static int label = 674;

    @JsonIgnore
    private final CBORMetadataMap map;
    @JsonIgnore
    private final CBORMetadataList messageList;

    private MessageMetadata() {
        super();
        map = new CBORMetadataMap();
        messageList = new CBORMetadataList();

        map.put("msg", messageList);
        put(BigInteger.valueOf(label), map);
    }

    public static MessageMetadata create() {
        return new MessageMetadata();
    }

    public MessageMetadata add(String message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }

        int len = message.getBytes(StandardCharsets.UTF_8).length;
        if (len > 64) {
            if (log.isDebugEnabled())
                log.debug("Message : " + message);

            throw new IllegalArgumentException("Each message should have a maximum length of 64 characters. But actual length is : " + len);
        }

        messageList.add(message);
        return this;
    }

    public List<String> getMessages() {
        Array array = messageList.getArray();
        if (array == null)
            return Collections.EMPTY_LIST;

        List<String> messages = new ArrayList<>();
        for (DataItem di: array.getDataItems()) {
            if (di instanceof UnicodeString) {
                messages.add(((UnicodeString) di).getString());
            } else {
                throw new IllegalArgumentException("Invalid data found in the array : " + di);
            }
        }

        return messages;
    }

    //TODO from()
}
