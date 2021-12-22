package com.bloxbean.cardano.client.backend.gql.util;

public class ConversionUtil {

    public static int intValue(String str) {
        try {
            return Integer.parseInt(str);
        } catch (Exception e) {
            return 0;
        }
    }
}
