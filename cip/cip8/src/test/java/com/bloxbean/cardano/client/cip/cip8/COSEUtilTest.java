package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.model.NegativeInteger;
import co.nstant.in.cbor.model.UnicodeString;
import co.nstant.in.cbor.model.UnsignedInteger;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

class COSEUtilTest {

    @Test
    void getIntOrTextTypeFromObject_forNumbers() {
        UnsignedInteger di = (UnsignedInteger) COSEUtil.getDataItemFromObject(4);
        assertThat(di.getValue().intValue()).isEqualTo(4);

        NegativeInteger ni = (NegativeInteger) COSEUtil.getDataItemFromObject(-4);
        assertThat(ni.getValue().intValue()).isEqualTo(-4);

        di = (UnsignedInteger) COSEUtil.getDataItemFromObject(Integer.valueOf(500));
        assertThat(di.getValue().intValue()).isEqualTo(500);

        ni = (NegativeInteger) COSEUtil.getDataItemFromObject(Integer.valueOf(-500));
        assertThat(ni.getValue().intValue()).isEqualTo(-500);

        di = (UnsignedInteger) COSEUtil.getDataItemFromObject(60000L);
        assertThat(di.getValue().longValue()).isEqualTo(60000L);

        ni = (NegativeInteger) COSEUtil.getDataItemFromObject(-60000L);
        assertThat(ni.getValue().longValue()).isEqualTo(-60000L);

        di = (UnsignedInteger) COSEUtil.getDataItemFromObject(BigInteger.valueOf(3000));
        assertThat(di.getValue()).isEqualTo(BigInteger.valueOf(3000));

        ni = (NegativeInteger) COSEUtil.getDataItemFromObject(BigInteger.valueOf(-3000));
        assertThat(ni.getValue()).isEqualTo(BigInteger.valueOf(-3000));
    }

    @Test
    void getIntOrTextTypeFromObject_forString() {
        UnicodeString textDI = (UnicodeString) COSEUtil.getDataItemFromObject("hello");
        assertThat(textDI.getString()).isEqualTo("hello");
    }

    @Test
    void decodeIntOrTextTypeFromDataItem_whenNumber() {
        UnsignedInteger ui = new UnsignedInteger(5000);
        long val = (long) COSEUtil.decodeNumberOrTextOrBytesTypeFromDataItem(ui);

        assertThat(val).isEqualTo(5000L);
    }

    @Test
    void decodeIntOrTextTypeFromDataItem_whenString() {
        UnicodeString us = new UnicodeString("Hello");
        String val = (String) COSEUtil.decodeNumberOrTextOrBytesTypeFromDataItem(us);

        assertThat(val).isEqualTo("Hello");
    }
}
