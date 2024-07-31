package com.bloxbean.cardano.client.plutus.annotation.processor.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class JavaFileUtilTest {

    @Test
    public void testClassNameFormat() {
        String s = "gift_card";
        String result = JavaFileUtil.toClassNameFormat(s);
        assertThat(result).isEqualTo("GiftCard");
    }

    @Test
    public void testToCamelCase() {
        String s = "gift_card";
        String result = JavaFileUtil.toCamelCase(s);
        assertThat(result).isEqualTo("GiftCard");
    }

    @Test
    public void testToCamelCase_whenAlreadyCamelCase() {
        String s = "GiftCard";
        String result = JavaFileUtil.toCamelCase(s);
        assertThat(result).isEqualTo("GiftCard");
    }
}
