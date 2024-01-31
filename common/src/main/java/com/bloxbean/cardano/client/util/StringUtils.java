package com.bloxbean.cardano.client.util;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;

public class StringUtils {

    public static String[] splitStringEveryNCharacters(String text, int n) {
        return text.split("(?<=\\G.{" + n + "})");
    }

    public static boolean isEmpty(final String string) {
        return string == null || string.isEmpty();
    }

    public static boolean isUtf8String(byte[] bytes) {
        CharsetDecoder decoder =
                StandardCharsets.UTF_8.newDecoder();
        try {
            decoder.decode(
                    ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException ex) {
            return false;
        }
        return true;
    }
}
