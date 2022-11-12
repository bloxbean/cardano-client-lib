package com.bloxbean.cardano.client.cip.cip20;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageMetadataTest {

    @Test
    void createAndGetMessages() {
        String msg1 = "This is line1";
        String msg2 = "This is line2";
        String msg3 = "This is line3";

        MessageMetadata messageMetadata = MessageMetadata.create()
                .add(msg1)
                .add(msg2)
                .add(msg3);

        System.out.println(messageMetadata.serialize());

        List<String> messages = messageMetadata.getMessages();
        assertThat(messages).hasSize(3);
        assertThat(messages).contains(msg1, msg2, msg3);
    }

    @Test
    void create_throwExceptionWhenMessageSizeGreaterThan64Character() {
        String msg1 = "This is line1";
        String msg2 = "This is line2";
        String msg3 = "This is line is more than 64 character. This is line is more than 64 character " +
                "This is line is more than 64 character This is line is more than 64 character This is line is more than 64 character" +
                "This is line is more than 64 character This is line is more than 64 character This is line is more than 64 character" +
                "This is line is more than 64 character This is line is more than 64 character This is line is more than 64 character";

        assertThrows(IllegalArgumentException.class, () -> {
            MessageMetadata messageMetadata = MessageMetadata.create()
                    .add(msg1)
                    .add(msg2)
                    .add(msg3);
        });
    }

}
