package com.bloxbean.cardano.client.cip.cip68;

import com.bloxbean.cardano.client.backend.model.TxContentUtxoOutputs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CIP68MetadataUtilTest {

    @Test
    void getReferenzTokenNameTest() {
        String userTokenName = "76fa5eb96f727aa1548de7a0e7c46de8bdc3116029e083039ed624dd000de14054657374313233546f6b656e";
        String referenzTokenName = CIP68MetadataUtil.getReferenceTokenName(userTokenName);
        assertEquals("76fa5eb96f727aa1548de7a0e7c46de8bdc3116029e083039ed624dd000643b054657374313233546f6b656e", referenzTokenName);
    }

    @Test
    void getDatumAsUTF8JsonTest() {
        TxContentUtxoOutputs output = new TxContentUtxoOutputs();
        output.setInlineDatum("d8799fa6446e616d654c54657374313233546f6b656e45696d6167655835697066733a2f2f516d5763636a566279694d6a394637775278725053743554387953687974714331646f5132585072457838485379496d65646961747970654a696d6167652f6a706567496d65646961547970654a696d6167652f6a7065674b6465736372697074696f6e404566696c65739fa3496d65646961747970654a696d6167652f6a706567446e616d654a696d6167652f6a706567437372635835697066733a2f2f516d5763636a566279694d6a394637775278725053743554387953687974714331646f5132585072457838485379ff01ff");

        String datumAsUTF8Json = CIP68MetadataUtil.getDatumAsUTF8Json(output);
        String expectedJson = "{\n" +
                "  \"image\" : \"ipfs://QmWccjVbyiMj9F7wRxrPSt5T8yShytqC1doQ2XPrEx8HSy\",\n" +
                "  \"name\" : \"Test123Token\",\n" +
                "  \"description\" : \"\",\n" +
                "  \"files\" : [ {\n" +
                "    \"src\" : \"ipfs://QmWccjVbyiMj9F7wRxrPSt5T8yShytqC1doQ2XPrEx8HSy\",\n" +
                "    \"name\" : \"image/jpeg\",\n" +
                "    \"mediatype\" : \"image/jpeg\"\n" +
                "  } ],\n" +
                "  \"mediaType\" : \"image/jpeg\",\n" +
                "  \"mediatype\" : \"image/jpeg\"\n" +
                "}";
        assertEquals(expectedJson, datumAsUTF8Json);
    }
}
