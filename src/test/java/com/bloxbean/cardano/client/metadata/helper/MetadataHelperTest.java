package com.bloxbean.cardano.client.metadata.helper;

import com.bloxbean.cardano.client.metadata.cbor.MetadataHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetadataHelperTest {

    @Test
    void checkLengthTest() {
        String str = "a61bf710c72e671fae4ba01b0d205105e6e7bacf504ebc4ea3b43bb0cc76bb326f17a30d8f1b12c2c4e58b6778f6a26430783065463bdefda922656830783134666638643bb6597a178e6a18971b6827b4dcb50c5c0b71726365486c5578586c576d5a4a637859641b64f4d10bda83efe33bcd995b2806a1d9971b12127f810d7dcee28264554a42333be153691687de9f67";
        assertEquals(str.length(), MetadataHelper.checkLength(str));

        str = "";
        assertEquals(0, MetadataHelper.checkLength(str));

        assertEquals(0, MetadataHelper.checkLength(null));
    }
}
