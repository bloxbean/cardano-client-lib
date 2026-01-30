package com.bloxbean.cardano.client.plutus.annotation.processor.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@DisplayName("JavaFileUtil")
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
        // toCamelCase returns camelCase (first letter lowercase)
        assertThat(result).isEqualTo("giftCard");
    }

    @Test
    public void testToCamelCase_whenAlreadyCamelCase() {
        String s = "GiftCard";
        String result = JavaFileUtil.toCamelCase(s);
        // toCamelCase converts to camelCase (first letter lowercase)
        assertThat(result).isEqualTo("giftCard");
    }

    @Nested
    @DisplayName("Aiken Alpha Naming Convention (v1.0.x)")
    class AikenAlphaNamingConvention {

        @Test
        @DisplayName("should handle simple list with dollar delimiter")
        void shouldHandleSimpleListWithDollar() {
            // Aiken v1.0.26-alpha+075668b format: List$ByteArray
            String input = "List$ByteArray";
            String result = JavaFileUtil.toCamelCase(input);

            // Should preserve dollar signs and convert to valid camelCase
            assertThat(result).isEqualTo("list$ByteArray");
            assertThat(isValidJavaIdentifier(result)).isTrue();
        }

        @Test
        @DisplayName("should handle nested tuple with dollar and underscore delimiters")
        void shouldHandleNestedTupleWithDollarAndUnderscore() {
            // Aiken v1.0.26-alpha format: List$Tuple$Tuple$ByteArray_ByteArray_Int
            String input = "List$Tuple$Tuple$ByteArray_ByteArray_Int";
            String result = JavaFileUtil.toCamelCase(input);

            // Should be valid Java identifier
            assertThat(isValidJavaIdentifier(result)).isTrue();
        }

        @Test
        @DisplayName("should handle definition with dollar delimiters")
        void shouldHandleDefinitionWithDollarDelimiters() {
            // Typical alpha format for nested types
            String input = "Option$List$Int";
            String result = JavaFileUtil.toCamelCase(input);

            assertThat(isValidJavaIdentifier(result)).isTrue();
        }

        @Test
        @DisplayName("should convert to lowercase first when used as field name")
        void shouldConvertToLowercaseFirstForFieldName() {
            String input = "List$ByteArray";
            String camelCase = JavaFileUtil.toCamelCase(input);
            String fieldName = JavaFileUtil.firstLowerCase(camelCase);

            // Field names should start with lowercase
            assertThat(fieldName).matches("^[a-z].*");
            assertThat(isValidJavaIdentifier(fieldName)).isTrue();
        }
    }

    @Nested
    @DisplayName("Aiken 1.x Naming Convention (v1.1.x+)")
    class Aiken1xNamingConvention {

        @Test
        @DisplayName("should handle simple list with angle brackets")
        void shouldHandleSimpleListWithAngleBrackets() {
            // Aiken v1.1.21+42babe5 format: List<Int>
            String input = "List<Int>";
            String result = JavaFileUtil.toCamelCase(input);

            // Should convert to valid Java identifier
            assertThat(result).isEqualTo("listOfInt");
            assertThat(isValidJavaIdentifier(result)).isTrue();
        }

        @Test
        @DisplayName("should handle nested generic types with multiple angle brackets")
        void shouldHandleNestedGenericTypes() {
            // Complex nested generics from SundaeSwap v3
            // Note: Extra angle brackets in original test were a typo, fixing it
            String input = "List<Tuple<Int,Option<Data>,Int>>";
            String result = JavaFileUtil.toCamelCase(input);

            // Should convert to valid Java identifier
            assertThat(result).isEqualTo("listOfTupleOfIntAndOptionOfDataAndInt");
            assertThat(isValidJavaIdentifier(result)).isTrue();
        }

        @Test
        @DisplayName("should handle list with module path")
        void shouldHandleListWithModulePath() {
            // Format with forward slashes: List<aiken/crypto/VerificationKey>
            String input = "List<aiken/crypto/VerificationKey>";
            String result = JavaFileUtil.toCamelCase(input);

            // Should convert module paths to valid Java identifier
            assertThat(result).isEqualTo("listOfAikenCryptoVerificationKey");
            assertThat(isValidJavaIdentifier(result)).isTrue();
        }

        @Test
        @DisplayName("should handle when used as field name with index suffix")
        void shouldHandleWhenUsedAsFieldNameWithIndexSuffix() {
            // Simulates what happens in ListDataTypeProcessor
            String input = "List<Int>";
            String withIndex = input + "0"; // Adding index suffix
            String camelCase = JavaFileUtil.toCamelCase(withIndex);
            String fieldName = JavaFileUtil.firstLowerCase(camelCase);

            // Should produce valid identifier
            assertThat(fieldName).isEqualTo("listOfInt0");
            assertThat(isValidJavaIdentifier(fieldName)).isTrue();
        }

        @Test
        @DisplayName("should handle cardano address credential list")
        void shouldHandleCardanoAddressCredentialList() {
            // Real example from SundaeSwap: List<cardano/address/Credential>
            String input = "List<cardano/address/Credential>";
            String result = JavaFileUtil.toCamelCase(input);

            assertThat(result).isEqualTo("listOfCardanoAddressCredential");
            assertThat(isValidJavaIdentifier(result)).isTrue();
        }

        @Test
        @DisplayName("should handle multisig script list")
        void shouldHandleMultisigScriptList() {
            // Real example from SundaeSwap: List<sundae/multisig/MultisigScript>
            String input = "List<sundae/multisig/MultisigScript>";
            String result = JavaFileUtil.toCamelCase(input);

            assertThat(result).isEqualTo("listOfSundaeMultisigMultisigScript");
            assertThat(isValidJavaIdentifier(result)).isTrue();
        }
    }

    @Nested
    @DisplayName("Package Name Formatting")
    class PackageNameFormatting {

        @Test
        @DisplayName("should handle package names with hyphens")
        void shouldHandlePackageNamesWithHyphens() {
            String pkg = "aiken-lang/gift_card";
            String result = JavaFileUtil.toPackageNameFormat(pkg);

            // Package formatting removes all special characters including slashes
            assertThat(result).isEqualTo("aikenlanggiftcard");
            assertThat(result).doesNotContain("-");
            assertThat(result).doesNotContain("_");
            assertThat(result).doesNotContain("/");
        }

        @Test
        @DisplayName("should convert package names to lowercase")
        void shouldConvertPackageNamesToLowercase() {
            String pkg = "SundaeSwap-Finance";
            String result = JavaFileUtil.toPackageNameFormat(pkg);

            assertThat(result).isEqualTo("sundaeswapfinance");
            assertThat(result).isLowerCase();
        }
    }

    /**
     * Helper method to validate if a string is a valid Java identifier.
     * A valid identifier:
     * - Must start with a letter, underscore, or dollar sign
     * - Can contain letters, digits, underscores, and dollar signs
     * - Cannot contain angle brackets, slashes, or other special characters
     */
    private boolean isValidJavaIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return false;
        }

        // Check first character
        if (!Character.isJavaIdentifierStart(identifier.charAt(0))) {
            return false;
        }

        // Check remaining characters
        for (int i = 1; i < identifier.length(); i++) {
            if (!Character.isJavaIdentifierPart(identifier.charAt(i))) {
                return false;
            }
        }

        return true;
    }
}
