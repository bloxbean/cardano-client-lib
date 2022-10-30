package com.bloxbean.cardano.client.util;

public class StringUtils {

    public static String[] splitStringEveryNCharacters(String text, int n) {
        return text.split("(?<=\\G.{" + n + "})");
    }
}
