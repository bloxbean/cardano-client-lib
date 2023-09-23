package com.bloxbean.cardano.client.util;

public class StringUtils {

    public static String[] splitStringEveryNCharacters(String text, int n) {
        return text.split("(?<=\\G.{" + n + "})");
    }

    public static boolean isEmpty(final String string) {
        return string == null || string.isEmpty();
    }
}
