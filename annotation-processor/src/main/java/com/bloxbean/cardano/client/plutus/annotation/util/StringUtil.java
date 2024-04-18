package com.bloxbean.cardano.client.plutus.annotation.util;

public class StringUtil {

    public static String snakeToCamel(String snakeCaseString) {
        if (snakeCaseString == null || snakeCaseString.isEmpty()) {
            return snakeCaseString;
        }

        StringBuilder camelCaseString = new StringBuilder();
        boolean capitalizeNext = false;

        // Iterate through each character in the string
        for (char currentChar : snakeCaseString.toCharArray()) {
            if (currentChar == '_') {
                // If the current character is an underscore, don't add it to the result
                // but capitalize the next character
                capitalizeNext = true;
            } else if (capitalizeNext) {
                // If the previous character was an underscore, capitalize the current character
                camelCaseString.append(Character.toUpperCase(currentChar));
                capitalizeNext = false;
            } else {
                // Otherwise, just add the character as it is
                camelCaseString.append(currentChar);
            }
        }

        return camelCaseString.toString();
    }
}
