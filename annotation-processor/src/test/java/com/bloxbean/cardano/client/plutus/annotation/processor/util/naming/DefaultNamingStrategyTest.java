package com.bloxbean.cardano.client.plutus.annotation.processor.util.naming;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for DefaultNamingStrategy covering all CIP-53 blueprint naming conventions.
 *
 * Tests cover:
 * - Legacy Aiken v1.0.x style: List$ByteArray, Tuple$Int_Int
 * - Modern Aiken v1.1.x+ style: List<Int>, aiken/crypto/Hash
 * - Edge cases: null, empty, special characters
 * - Real-world examples from SundaeSwap and other contracts
 */
@DisplayName("DefaultNamingStrategy")
class DefaultNamingStrategyTest {

    private DefaultNamingStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new DefaultNamingStrategy();
    }

    @Nested
    @DisplayName("Basic Naming Operations")
    class BasicNamingOperations {

        @Test
        @DisplayName("toClassName() should convert to PascalCase")
        void toClassName_convertsFirstLetterUppercase() {
            assertThat(strategy.toClassName("myClass")).isEqualTo("MyClass");
            assertThat(strategy.toClassName("MyClass")).isEqualTo("MyClass");
            assertThat(strategy.toClassName("my_class")).isEqualTo("MyClass");
            assertThat(strategy.toClassName("my-class")).isEqualTo("MyClass");
        }

        @Test
        @DisplayName("toCamelCase() should convert to camelCase")
        void toCamelCase_convertsToProperCamelCase() {
            assertThat(strategy.toCamelCase("my_field")).isEqualTo("myField");
            assertThat(strategy.toCamelCase("my-field")).isEqualTo("myField");
            assertThat(strategy.toCamelCase("MyField")).isEqualTo("myField"); // Should lowercase first char
            assertThat(strategy.toCamelCase("myField")).isEqualTo("myField"); // Already correct
        }

        @Test
        @DisplayName("firstUpperCase() should capitalize first character")
        void firstUpperCase_capitalizesFirstChar() {
            assertThat(strategy.firstUpperCase("test")).isEqualTo("Test");
            assertThat(strategy.firstUpperCase("Test")).isEqualTo("Test");
            assertThat(strategy.firstUpperCase("t")).isEqualTo("T");
        }

        @Test
        @DisplayName("firstLowerCase() should lowercase first character")
        void firstLowerCase_lowercasesFirstChar() {
            assertThat(strategy.firstLowerCase("Test")).isEqualTo("test");
            assertThat(strategy.firstLowerCase("test")).isEqualTo("test");
            assertThat(strategy.firstLowerCase("T")).isEqualTo("t");
        }

        @Test
        @DisplayName("should handle null and empty strings")
        void handlesNullAndEmpty() {
            assertThat(strategy.toClassName(null)).isNull();
            assertThat(strategy.toClassName("")).isEmpty();
            assertThat(strategy.toCamelCase(null)).isNull();
            assertThat(strategy.toCamelCase("")).isEmpty();
            assertThat(strategy.firstUpperCase(null)).isNull();
            assertThat(strategy.firstUpperCase("")).isEmpty();
            assertThat(strategy.firstLowerCase(null)).isNull();
            assertThat(strategy.firstLowerCase("")).isEmpty();
        }
    }

    @Nested
    @DisplayName("JSON Pointer Unescaping (RFC 6901)")
    class JsonPointerUnescaping {

        @Test
        @DisplayName("should unescape ~1 to forward slash")
        void unescapesTildeOneToSlash() {
            assertThat(strategy.unescapeJsonPointer("types~1order~1Action"))
                    .isEqualTo("types/order/Action");
            assertThat(strategy.unescapeJsonPointer("aiken~1crypto~1Hash"))
                    .isEqualTo("aiken/crypto/Hash");
            assertThat(strategy.unescapeJsonPointer("cardano~1address~1Credential"))
                    .isEqualTo("cardano/address/Credential");
        }

        @Test
        @DisplayName("should unescape ~0 to tilde")
        void unescapesTildeZeroToTilde() {
            assertThat(strategy.unescapeJsonPointer("some~0key"))
                    .isEqualTo("some~key");
            assertThat(strategy.unescapeJsonPointer("Type~0Name"))
                    .isEqualTo("Type~Name");
            assertThat(strategy.unescapeJsonPointer("~0tilde~0prefix"))
                    .isEqualTo("~tilde~prefix");
        }

        @Test
        @DisplayName("should handle combined escapes")
        void handlesCombinedEscapes() {
            // Both ~0 and ~1 in same string
            assertThat(strategy.unescapeJsonPointer("path~1with~0tilde"))
                    .isEqualTo("path/with~tilde");
            assertThat(strategy.unescapeJsonPointer("~0start~1middle~0end"))
                    .isEqualTo("~start/middle~end");
        }

        @Test
        @DisplayName("should handle multiple consecutive escapes")
        void handlesMultipleConsecutiveEscapes() {
            assertThat(strategy.unescapeJsonPointer("a~1~1b"))
                    .isEqualTo("a//b");
            assertThat(strategy.unescapeJsonPointer("a~0~0b"))
                    .isEqualTo("a~~b");
            assertThat(strategy.unescapeJsonPointer("a~0~1b"))
                    .isEqualTo("a~/b");
        }

        @Test
        @DisplayName("should process ~1 before ~0 to avoid double-processing")
        void processesInCorrectOrder() {
            // This tests the critical ordering requirement documented in the method
            // If we process ~0 first, "~01" becomes "~1" which then becomes "/"
            // If we process ~1 first, "~01" stays "~01" and then becomes "~1"
            assertThat(strategy.unescapeJsonPointer("~01"))
                    .isEqualTo("~1");  // Should be ~1, not /
            assertThat(strategy.unescapeJsonPointer("prefix~01suffix"))
                    .isEqualTo("prefix~1suffix");
        }

        @Test
        @DisplayName("should handle strings without escape sequences")
        void handlesStringsWithoutEscapes() {
            assertThat(strategy.unescapeJsonPointer("normalString"))
                    .isEqualTo("normalString");
            assertThat(strategy.unescapeJsonPointer("path/with/slashes"))
                    .isEqualTo("path/with/slashes");
            assertThat(strategy.unescapeJsonPointer("tilde~character"))
                    .isEqualTo("tilde~character");
        }

        @Test
        @DisplayName("should handle null and empty strings")
        void handlesNullAndEmpty() {
            assertThat(strategy.unescapeJsonPointer(null)).isNull();
            assertThat(strategy.unescapeJsonPointer("")).isEmpty();
        }

        @Test
        @DisplayName("should handle edge cases with tildes")
        void handlesEdgeCasesWithTildes() {
            // Tilde at start
            assertThat(strategy.unescapeJsonPointer("~1start"))
                    .isEqualTo("/start");
            // Tilde at end
            assertThat(strategy.unescapeJsonPointer("end~1"))
                    .isEqualTo("end/");
            // Just escapes
            assertThat(strategy.unescapeJsonPointer("~1"))
                    .isEqualTo("/");
            assertThat(strategy.unescapeJsonPointer("~0"))
                    .isEqualTo("~");
            // Multiple escapes only
            assertThat(strategy.unescapeJsonPointer("~1~0~1"))
                    .isEqualTo("/~/");
        }

        @Test
        @DisplayName("should handle invalid escape sequences gracefully")
        void handlesInvalidEscapeSequences() {
            // RFC 6901 only defines ~0 and ~1, but other sequences might appear
            // They should be left as-is (not unescaped)
            assertThat(strategy.unescapeJsonPointer("~2invalid"))
                    .isEqualTo("~2invalid");
            assertThat(strategy.unescapeJsonPointer("~9other"))
                    .isEqualTo("~9other");
            assertThat(strategy.unescapeJsonPointer("~abc"))
                    .isEqualTo("~abc");
        }

        @Test
        @DisplayName("should work correctly in sanitizeIdentifier")
        void worksInSanitizeIdentifier() {
            // Verify unescaping is applied in the full identifier sanitization flow
            // Note: sanitizeForwardSlashes capitalizes each path segment
            assertThat(strategy.sanitizeIdentifier("types~1order~1Action"))
                    .isEqualTo("TypesOrderAction");  // ~1 -> /, then each segment capitalized
            assertThat(strategy.sanitizeIdentifier("data~0field"))
                    .isEqualTo("datafield");  // ~0 -> ~, then ~ removed (invalid char)
        }

        @Test
        @DisplayName("should work correctly in toCamelCase")
        void worksInToCamelCase() {
            // Verify unescaping is applied in the camel case conversion flow
            assertThat(strategy.toCamelCase("types~1order~1action"))
                    .isEqualTo("typesOrderAction");
            assertThat(strategy.toCamelCase("my~1module~1name"))
                    .isEqualTo("myModuleName");
        }

        @Test
        @DisplayName("real-world CIP-53 blueprint examples")
        void realWorldBlueprintExamples() {
            // Example from SundaeSwap V3 and other modern Aiken contracts
            assertThat(strategy.unescapeJsonPointer("types~1automatic_payments~1AutomatedPayment"))
                    .isEqualTo("types/automatic_payments/AutomatedPayment");

            // Complex nested module paths
            assertThat(strategy.unescapeJsonPointer("sundaeswap~1v3~1types~1order~1Action"))
                    .isEqualTo("sundaeswap/v3/types/order/Action");

            // Mixed with other characters
            assertThat(strategy.unescapeJsonPointer("Option<types~1order~1Action>"))
                    .isEqualTo("Option<types/order/Action>");
        }

        @Test
        @DisplayName("RFC 6901 specification compliance")
        void rfc6901ComplianceExamples() {
            // Examples directly from RFC 6901 specification
            // https://tools.ietf.org/html/rfc6901

            // Section 3: "~0" represents "~"
            assertThat(strategy.unescapeJsonPointer("~0"))
                    .isEqualTo("~");

            // Section 3: "~1" represents "/"
            assertThat(strategy.unescapeJsonPointer("~1"))
                    .isEqualTo("/");

            // Section 3: Example with both escapes
            assertThat(strategy.unescapeJsonPointer("a~1b~0c"))
                    .isEqualTo("a/b~c");

            // Section 3: Escape sequence at different positions
            assertThat(strategy.unescapeJsonPointer("~1foo~0bar~1"))
                    .isEqualTo("/foo~bar/");
        }
    }

    @Nested
    @DisplayName("Legacy Aiken Alpha Naming (v1.0.x)")
    class LegacyAikenNaming {

        @Test
        @DisplayName("should handle dollar sign delimiters")
        void handlesDollarSignDelimiters() {
            // Dollar signs are valid in Java identifiers, keep as-is
            assertThat(strategy.sanitizeIdentifier("List$ByteArray"))
                    .isEqualTo("List$ByteArray");
            assertThat(strategy.sanitizeIdentifier("Tuple$Int$Int"))
                    .isEqualTo("Tuple$Int$Int");
            assertThat(strategy.toClassName("List$ByteArray"))
                    .isEqualTo("List$ByteArray");
        }

        @Test
        @DisplayName("should handle underscore delimiters")
        void handlesUnderscoreDelimiters() {
            // Underscores are valid in Java identifiers
            assertThat(strategy.sanitizeIdentifier("Tuple_Int_Int"))
                    .isEqualTo("Tuple_Int_Int");
            assertThat(strategy.toClassName("some_type_name"))
                    .isEqualTo("SomeTypeName");
        }

        @Test
        @DisplayName("should handle mixed dollar and underscore")
        void handlesMixedDollarAndUnderscore() {
            assertThat(strategy.sanitizeIdentifier("Tuple$Int_Data"))
                    .isEqualTo("Tuple$Int_Data");
            assertThat(strategy.toClassName("List$byte_array"))
                    .isEqualTo("List$ByteArray");
        }

        @Test
        @DisplayName("all outputs should be valid Java identifiers")
        void outputsAreValidJavaIdentifiers() {
            String[] inputs = {
                "List$ByteArray",
                "Tuple$Int_Int",
                "Option$Data",
                "my_type_name"
            };

            for (String input : inputs) {
                String result = strategy.sanitizeIdentifier(input);
                assertThat(isValidJavaIdentifier(result))
                        .as("'%s' should be a valid Java identifier", result)
                        .isTrue();
            }
        }
    }

    @Nested
    @DisplayName("Modern Aiken Naming (v1.1.x+) - Angle Brackets")
    class ModernAikenAngleBrackets {

        @Test
        @DisplayName("should convert simple generic types")
        void convertsSimpleGenericTypes() {
            assertThat(strategy.sanitizeIdentifier("List<Int>"))
                    .isEqualTo("ListOfInt");
            assertThat(strategy.sanitizeIdentifier("Option<Data>"))
                    .isEqualTo("OptionOfData");
            assertThat(strategy.sanitizeIdentifier("List<ByteArray>"))
                    .isEqualTo("ListOfByteArray");
        }

        @Test
        @DisplayName("should convert tuples with multiple type parameters")
        void convertsTuplesWithMultipleParameters() {
            assertThat(strategy.sanitizeIdentifier("Tuple<Int,Int>"))
                    .isEqualTo("TupleOfIntAndInt");
            assertThat(strategy.sanitizeIdentifier("Tuple<Int,Data>"))
                    .isEqualTo("TupleOfIntAndData");
            assertThat(strategy.sanitizeIdentifier("Tuple<ByteArray,Int,Data>"))
                    .isEqualTo("TupleOfByteArrayAndIntAndData");
        }

        @Test
        @DisplayName("should convert nested generic types")
        void convertsNestedGenericTypes() {
            assertThat(strategy.sanitizeIdentifier("List<Option<Int>>"))
                    .isEqualTo("ListOfOptionOfInt");
            assertThat(strategy.sanitizeIdentifier("Option<List<Data>>"))
                    .isEqualTo("OptionOfListOfData");
            assertThat(strategy.sanitizeIdentifier("List<Tuple<Int,Data>>"))
                    .isEqualTo("ListOfTupleOfIntAndData");
        }

        @Test
        @DisplayName("should convert complex nested structures")
        void convertsComplexNestedStructures() {
            // Real example from SundaeSwap V3
            assertThat(strategy.sanitizeIdentifier("List<Tuple<Int,Option<Data>,Int>>"))
                    .isEqualTo("ListOfTupleOfIntAndOptionOfDataAndInt");

            assertThat(strategy.sanitizeIdentifier("Option<Tuple<List<Int>,Data>>"))
                    .isEqualTo("OptionOfTupleOfListOfIntAndData");
        }

        @Test
        @DisplayName("should capitalize first letter after opening bracket")
        void capitalizesAfterOpeningBracket() {
            assertThat(strategy.sanitizeIdentifier("List<int>"))
                    .isEqualTo("ListOfInt");
            assertThat(strategy.sanitizeIdentifier("Option<data>"))
                    .isEqualTo("OptionOfData");
        }

        @Test
        @DisplayName("all outputs should be valid Java identifiers")
        void outputsAreValidJavaIdentifiers() {
            String[] inputs = {
                "List<Int>",
                "Option<Data>",
                "Tuple<Int,Int>",
                "List<Tuple<Int,Data>>",
                "List<Tuple<Int,Option<Data>,Int>>"
            };

            for (String input : inputs) {
                String result = strategy.sanitizeIdentifier(input);
                assertThat(isValidJavaIdentifier(result))
                        .as("'%s' should be a valid Java identifier", result)
                        .isTrue();
            }
        }
    }

    @Nested
    @DisplayName("Modern Aiken Naming (v1.1.x+) - Module Paths")
    class ModernAikenModulePaths {

        @Test
        @DisplayName("should convert simple module paths")
        void convertsSimpleModulePaths() {
            assertThat(strategy.sanitizeIdentifier("aiken/crypto/Hash"))
                    .isEqualTo("AikenCryptoHash");
            assertThat(strategy.sanitizeIdentifier("cardano/address/Credential"))
                    .isEqualTo("CardanoAddressCredential");
            assertThat(strategy.sanitizeIdentifier("types/order/Action"))
                    .isEqualTo("TypesOrderAction");
        }

        @Test
        @DisplayName("should handle tilde-escaped slashes")
        void handlesTildeEscapedSlashes() {
            // JSON reference escaping: ~1 represents /
            assertThat(strategy.sanitizeIdentifier("types~1order~1Action"))
                    .isEqualTo("TypesOrderAction");
            assertThat(strategy.sanitizeIdentifier("aiken~1crypto~1Hash"))
                    .isEqualTo("AikenCryptoHash");
        }

        @Test
        @DisplayName("should handle escaped tildes")
        void handlesEscapedTildes() {
            // JSON reference escaping: ~0 represents ~
            assertThat(strategy.sanitizeIdentifier("Type~0Name"))
                    .isEqualTo("TypeName");
        }

        @Test
        @DisplayName("should capitalize each path segment")
        void capitalizesEachPathSegment() {
            assertThat(strategy.sanitizeIdentifier("my/module/path"))
                    .isEqualTo("MyModulePath");
            assertThat(strategy.sanitizeIdentifier("aiken/list/functions"))
                    .isEqualTo("AikenListFunctions");
        }

        @Test
        @DisplayName("all outputs should be valid Java identifiers")
        void outputsAreValidJavaIdentifiers() {
            String[] inputs = {
                "aiken/crypto/Hash",
                "cardano/address/Credential",
                "types~1order~1Action",
                "my/module/path"
            };

            for (String input : inputs) {
                String result = strategy.sanitizeIdentifier(input);
                assertThat(isValidJavaIdentifier(result))
                        .as("'%s' should be a valid Java identifier", result)
                        .isTrue();
            }
        }
    }

    @Nested
    @DisplayName("Mixed Naming Patterns")
    class MixedNamingPatterns {

        @Test
        @DisplayName("should handle generic types with module paths")
        void handlesGenericTypesWithModulePaths() {
            assertThat(strategy.sanitizeIdentifier("List<aiken/crypto/Hash>"))
                    .isEqualTo("ListOfAikenCryptoHash");
            assertThat(strategy.sanitizeIdentifier("Option<cardano/address/Credential>"))
                    .isEqualTo("OptionOfCardanoAddressCredential");
        }

        @Test
        @DisplayName("should handle module paths with escaped slashes in generics")
        void handlesModulePathsWithEscapedSlashesInGenerics() {
            assertThat(strategy.sanitizeIdentifier("Option<types~1order~1Action>"))
                    .isEqualTo("OptionOfTypesOrderAction");
            assertThat(strategy.sanitizeIdentifier("List<aiken~1crypto~1Hash>"))
                    .isEqualTo("ListOfAikenCryptoHash");
        }

        @Test
        @DisplayName("should handle complex mixed patterns")
        void handlesComplexMixedPatterns() {
            // Tuple with module paths
            assertThat(strategy.sanitizeIdentifier("Tuple<aiken/crypto/Hash,cardano/address/Credential>"))
                    .isEqualTo("TupleOfAikenCryptoHashAndCardanoAddressCredential");

            // Nested generics with module paths
            assertThat(strategy.sanitizeIdentifier("List<Option<aiken/crypto/Hash>>"))
                    .isEqualTo("ListOfOptionOfAikenCryptoHash");
        }

        @Test
        @DisplayName("all outputs should be valid Java identifiers")
        void outputsAreValidJavaIdentifiers() {
            String[] inputs = {
                "List<aiken/crypto/Hash>",
                "Option<types~1order~1Action>",
                "Tuple<aiken/crypto/Hash,cardano/address/Credential>",
                "List<Option<aiken~1crypto~1Hash>>"
            };

            for (String input : inputs) {
                String result = strategy.sanitizeIdentifier(input);
                assertThat(isValidJavaIdentifier(result))
                        .as("'%s' should be a valid Java identifier", result)
                        .isTrue();
            }
        }
    }

    @Nested
    @DisplayName("Real-World SundaeSwap Examples")
    class RealWorldSundaeSwapExamples {

        @Test
        @DisplayName("SundaeSwap V2 (Aiken alpha) types")
        void sundaeSwapV2Types() {
            // Examples from sundaeswap_aiken_v1.0.26-alpha075668b.json
            assertThat(strategy.sanitizeIdentifier("List$ByteArray"))
                    .isEqualTo("List$ByteArray");
            assertThat(strategy.toClassName("List$ByteArray"))
                    .isEqualTo("List$ByteArray");

            assertThat(isValidJavaIdentifier("List$ByteArray")).isTrue();
        }

        @Test
        @DisplayName("SundaeSwap V3 (Aiken 1.x) types")
        void sundaeSwapV3Types() {
            // Examples from sundaeswap_aiken_v1.1.2142babe5.json
            assertThat(strategy.sanitizeIdentifier("List<Int>"))
                    .isEqualTo("ListOfInt");
            assertThat(strategy.toClassName("List<Int>"))
                    .isEqualTo("ListOfInt");

            assertThat(isValidJavaIdentifier("ListOfInt")).isTrue();
        }

        @Test
        @DisplayName("should convert SundaeSwap V3 Mint constructor types")
        void sundaeSwapV3MintConstructorTypes() {
            // From line 1019 in sundaeswap_aiken_v1.1.2142babe5.json
            String className = strategy.toClassName("List<Int>");
            assertThat(className).isEqualTo("ListOfInt");
            assertThat(isValidJavaIdentifier(className)).isTrue();
        }
    }

    @Nested
    @DisplayName("Edge Cases and Special Characters")
    class EdgeCasesAndSpecialCharacters {

        @Test
        @DisplayName("should handle empty string after sanitization")
        void handlesEmptyAfterSanitization() {
            // If all characters are removed, should return underscore
            // Note: $ is a valid Java identifier, so it's kept
            assertThat(strategy.sanitizeIdentifier("@#$%"))
                    .isEqualTo("$");
            // Only truly invalid characters result in underscore
            assertThat(strategy.sanitizeIdentifier("!!!"))
                    .isEqualTo("_");
            assertThat(strategy.sanitizeIdentifier("@#%"))
                    .isEqualTo("_");
        }

        @Test
        @DisplayName("should prefix with underscore if starts with digit")
        void prefixesWithUnderscoreIfStartsWithDigit() {
            assertThat(strategy.sanitizeIdentifier("123Type"))
                    .isEqualTo("_123Type");
            assertThat(strategy.sanitizeIdentifier("9Data"))
                    .isEqualTo("_9Data");
        }

        @Test
        @DisplayName("should remove all invalid characters")
        void removesInvalidCharacters() {
            assertThat(strategy.sanitizeIdentifier("Type@Name"))
                    .isEqualTo("TypeName");
            assertThat(strategy.sanitizeIdentifier("My#Type$Name"))
                    .isEqualTo("MyType$Name"); // $ is valid
            assertThat(strategy.sanitizeIdentifier("Type!With?Special"))
                    .isEqualTo("TypeWithSpecial");
        }

        @Test
        @DisplayName("should handle single character inputs")
        void handlesSingleCharacterInputs() {
            assertThat(strategy.sanitizeIdentifier("A")).isEqualTo("A");
            assertThat(strategy.sanitizeIdentifier("a")).isEqualTo("a");
            assertThat(strategy.sanitizeIdentifier("_")).isEqualTo("_");
            assertThat(strategy.sanitizeIdentifier("$")).isEqualTo("$");
            assertThat(strategy.sanitizeIdentifier("1")).isEqualTo("_1");
        }

        @Test
        @DisplayName("should handle whitespace")
        void handlesWhitespace() {
            assertThat(strategy.sanitizeIdentifier("Type Name"))
                    .isEqualTo("TypeName");
            assertThat(strategy.sanitizeIdentifier("Type\tName"))
                    .isEqualTo("TypeName");
            assertThat(strategy.sanitizeIdentifier("Type\nName"))
                    .isEqualTo("TypeName");
        }

        @Test
        @DisplayName("all edge case outputs should be valid Java identifiers")
        void allEdgeCaseOutputsAreValid() {
            String[] inputs = {
                "@#$%",
                "123Type",
                "Type@Name",
                "A",
                "_",
                "$",
                "Type Name"
            };

            for (String input : inputs) {
                String result = strategy.sanitizeIdentifier(input);
                assertThat(isValidJavaIdentifier(result))
                        .as("Sanitized '%s' to '%s' which should be a valid Java identifier",
                            input, result)
                        .isTrue();
            }
        }
    }

    @Nested
    @DisplayName("Package Name Formatting")
    class PackageNameFormatting {

        @Test
        @DisplayName("should convert to lowercase")
        void convertsToLowercase() {
            assertThat(strategy.toPackageNameFormat("MyPackage"))
                    .isEqualTo("mypackage");
            assertThat(strategy.toPackageNameFormat("MYPACKAGE"))
                    .isEqualTo("mypackage");
        }

        @Test
        @DisplayName("should remove all special characters")
        void removesSpecialCharacters() {
            assertThat(strategy.toPackageNameFormat("my-package"))
                    .isEqualTo("mypackage");
            assertThat(strategy.toPackageNameFormat("my_package"))
                    .isEqualTo("mypackage");
            assertThat(strategy.toPackageNameFormat("my/package"))
                    .isEqualTo("mypackage");
            assertThat(strategy.toPackageNameFormat("my$package"))
                    .isEqualTo("mypackage");
        }

        @Test
        @DisplayName("should handle angle brackets and tildes")
        void handlesAngleBracketsAndTildes() {
            assertThat(strategy.toPackageNameFormat("package<name>"))
                    .isEqualTo("packagename");
            assertThat(strategy.toPackageNameFormat("package~name"))
                    .isEqualTo("packagename");
        }

        @Test
        @DisplayName("should handle null")
        void handlesNull() {
            assertThat(strategy.toPackageNameFormat(null)).isNull();
        }

        @Test
        @DisplayName("should produce valid package names")
        void producesValidPackageNames() {
            String[] inputs = {
                "MyPackage",
                "my-package",
                "my_package",
                "my/package/name",
                "aiken~1crypto"
            };

            for (String input : inputs) {
                String result = strategy.toPackageNameFormat(input);
                assertThat(result)
                        .as("Package name should be lowercase and contain only letters/digits")
                        .matches("[a-z0-9]+");
            }
        }
    }

    /**
     * Helper method to check if a string is a valid Java identifier.
     * Uses the same validation logic as JavaPoet.
     */
    private boolean isValidJavaIdentifier(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }

        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }

        return true;
    }
}
