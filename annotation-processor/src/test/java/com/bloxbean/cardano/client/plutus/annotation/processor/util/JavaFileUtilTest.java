package com.bloxbean.cardano.client.plutus.annotation.processor.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@DisplayName("JavaFileUtil")
public class JavaFileUtilTest {

    @Test
    public void testClassNameFormat_convertsSnakeCaseToPascalCase() {
        String s = "gift_card";
        String result = JavaFileUtil.toClassNameFormat(s);
        assertThat(result).isEqualTo("GiftCard");
    }

    @Test
    public void testToCamelCase_convertsSnakeCaseToLowerCamelCase() {
        String s = "gift_card";
        String result = JavaFileUtil.toCamelCase(s);
        assertThat(result).isEqualTo("giftCard");
    }

    @Test
    public void testToCamelCase_whenInputIsPascalCase() {
        String s = "GiftCard";
        String result = JavaFileUtil.toCamelCase(s);
        assertThat(result).isEqualTo("giftCard");
    }

    @Nested
    @DisplayName("Generic type and module path conversion")
    class GenericAndModulePathConversion {

        @Test
        @DisplayName("simple generic: List<Int> → listOfInt")
        void simpleAngleBracketGeneric() {
            String result = JavaFileUtil.toCamelCase("List<Int>");
            assertThat(result).isEqualTo("listOfInt");
            assertThat(isValidJavaIdentifier(result)).isTrue();
        }

        @Test
        @DisplayName("nested generic: List<Tuple<Int,Option<Data>,Int>> → listOfTupleOfIntAndOptionOfDataAndInt")
        void nestedAngleBracketGeneric() {
            String result = JavaFileUtil.toCamelCase("List<Tuple<Int,Option<Data>,Int>>");
            assertThat(result).isEqualTo("listOfTupleOfIntAndOptionOfDataAndInt");
            assertThat(isValidJavaIdentifier(result)).isTrue();
        }

        @Test
        @DisplayName("generic with module path: List<aiken/crypto/VerificationKey> → listOfAikenCryptoVerificationKey")
        void genericWithModulePath() {
            String result = JavaFileUtil.toCamelCase("List<aiken/crypto/VerificationKey>");
            assertThat(result).isEqualTo("listOfAikenCryptoVerificationKey");
            assertThat(isValidJavaIdentifier(result)).isTrue();
        }

        @Test
        @DisplayName("field-name with index suffix: List<Int>0 → listOfInt0")
        void fieldNameWithIndexSuffix() {
            String camelCase = JavaFileUtil.toCamelCase("List<Int>" + "0");
            String fieldName = JavaFileUtil.firstLowerCase(camelCase);
            assertThat(fieldName).isEqualTo("listOfInt0");
            assertThat(isValidJavaIdentifier(fieldName)).isTrue();
        }

        @Test
        @DisplayName("cardano address credential list: List<cardano/address/Credential> → listOfCardanoAddressCredential")
        void cardanoAddressCredentialList() {
            String result = JavaFileUtil.toCamelCase("List<cardano/address/Credential>");
            assertThat(result).isEqualTo("listOfCardanoAddressCredential");
            assertThat(isValidJavaIdentifier(result)).isTrue();
        }

        @Test
        @DisplayName("multisig script list: List<sundae/multisig/MultisigScript> → listOfSundaeMultisigMultisigScript")
        void multisigScriptList() {
            String result = JavaFileUtil.toCamelCase("List<sundae/multisig/MultisigScript>");
            assertThat(result).isEqualTo("listOfSundaeMultisigMultisigScript");
            assertThat(isValidJavaIdentifier(result)).isTrue();
        }
    }

    @Nested
    @DisplayName("Package Name Formatting")
    class PackageNameFormatting {

        @Test
        @DisplayName("removes hyphens, underscores and slashes")
        void shouldHandlePackageNamesWithHyphens() {
            String result = JavaFileUtil.toPackageNameFormat("aiken-lang/gift_card");
            assertThat(result).isEqualTo("aikenlanggiftcard");
            assertThat(result).doesNotContain("-").doesNotContain("_").doesNotContain("/");
        }

        @Test
        @DisplayName("converts to lowercase")
        void shouldConvertPackageNamesToLowercase() {
            String result = JavaFileUtil.toPackageNameFormat("SundaeSwap-Finance");
            assertThat(result).isEqualTo("sundaeswapfinance");
        }
    }

    private boolean isValidJavaIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(identifier.charAt(0))) {
            return false;
        }
        for (int i = 1; i < identifier.length(); i++) {
            if (!Character.isJavaIdentifierPart(identifier.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
