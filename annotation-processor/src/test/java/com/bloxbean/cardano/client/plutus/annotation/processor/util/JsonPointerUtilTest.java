package com.bloxbean.cardano.client.plutus.annotation.processor.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for RFC 6901 JSON Pointer escape/unescape operations.
 *
 * @see <a href="https://tools.ietf.org/html/rfc6901">RFC 6901 - JSON Pointer</a>
 */
class JsonPointerUtilTest {

    // ========== Unescape Tests ==========

    @Test
    void unescape_shouldHandleNull() {
        assertThat(JsonPointerUtil.unescape(null)).isNull();
    }

    @Test
    void unescape_shouldHandleEmptyString() {
        assertThat(JsonPointerUtil.unescape("")).isEmpty();
    }

    @Test
    void unescape_shouldHandleStringWithoutEscapeSequences() {
        assertThat(JsonPointerUtil.unescape("simpleString")).isEqualTo("simpleString");
        assertThat(JsonPointerUtil.unescape("camelCaseString")).isEqualTo("camelCaseString");
        assertThat(JsonPointerUtil.unescape("with-dashes")).isEqualTo("with-dashes");
        assertThat(JsonPointerUtil.unescape("with_underscores")).isEqualTo("with_underscores");
    }

    @Test
    void unescape_shouldConvertTilde1ToSlash() {
        assertThat(JsonPointerUtil.unescape("~1")).isEqualTo("/");
        assertThat(JsonPointerUtil.unescape("types~1order")).isEqualTo("types/order");
        assertThat(JsonPointerUtil.unescape("a~1b~1c")).isEqualTo("a/b/c");
    }

    @Test
    void unescape_shouldConvertTilde0ToTilde() {
        assertThat(JsonPointerUtil.unescape("~0")).isEqualTo("~");
        assertThat(JsonPointerUtil.unescape("some~0key")).isEqualTo("some~key");
        assertThat(JsonPointerUtil.unescape("a~0b~0c")).isEqualTo("a~b~c");
    }

    @Test
    void unescape_shouldHandleMixedEscapeSequences() {
        // Slash and tilde in same string
        assertThat(JsonPointerUtil.unescape("types~1order~0Action")).isEqualTo("types/order~Action");
        assertThat(JsonPointerUtil.unescape("~1~0~1~0")).isEqualTo("/~/~");
    }

    @Test
    void unescape_shouldHandleCIP57Examples() {
        // Real example from CIP-57 blueprints
        assertThat(JsonPointerUtil.unescape("types~1automatic_payments~1AutomatedPayment"))
                .isEqualTo("types/automatic_payments/AutomatedPayment");

        // Module paths
        assertThat(JsonPointerUtil.unescape("aiken~1crypto~1Hash"))
                .isEqualTo("aiken/crypto/Hash");

        assertThat(JsonPointerUtil.unescape("cardano~1address~1Credential"))
                .isEqualTo("cardano/address/Credential");

        // Action types
        assertThat(JsonPointerUtil.unescape("types~1order~1Action"))
                .isEqualTo("types/order/Action");
    }

    @Test
    void unescape_shouldHandleOrderCorrectly() {
        // Test that ~1 is processed before ~0 to avoid incorrect results
        // "~01" is the escape sequence "~0" followed by literal "1", so it becomes "~1"
        assertThat(JsonPointerUtil.unescape("~01")).isEqualTo("~1");

        // If we had processed ~0 first, then "~01" would incorrectly become "/"
        // This tests that we process in the correct order

        // "~0~1" contains two escape sequences: "~0" (tilde) and "~1" (slash)
        assertThat(JsonPointerUtil.unescape("~0~1")).isEqualTo("~/");

        // Another edge case: "~10" is escape sequence "~1" (slash) followed by literal "0"
        assertThat(JsonPointerUtil.unescape("~10")).isEqualTo("/0");
    }

    @Test
    void unescape_shouldHandleMultipleOccurrences() {
        assertThat(JsonPointerUtil.unescape("~1~1~1")).isEqualTo("///");
        assertThat(JsonPointerUtil.unescape("~0~0~0")).isEqualTo("~~~");
        assertThat(JsonPointerUtil.unescape("a~1b~1c~1d")).isEqualTo("a/b/c/d");
        assertThat(JsonPointerUtil.unescape("a~0b~0c~0d")).isEqualTo("a~b~c~d");
    }

    @Test
    void unescape_shouldHandleEscapeSequencesAtBoundaries() {
        // At start
        assertThat(JsonPointerUtil.unescape("~1start")).isEqualTo("/start");
        assertThat(JsonPointerUtil.unescape("~0start")).isEqualTo("~start");

        // At end
        assertThat(JsonPointerUtil.unescape("end~1")).isEqualTo("end/");
        assertThat(JsonPointerUtil.unescape("end~0")).isEqualTo("end~");

        // Both ends
        assertThat(JsonPointerUtil.unescape("~1middle~1")).isEqualTo("/middle/");
        assertThat(JsonPointerUtil.unescape("~0middle~0")).isEqualTo("~middle~");
    }

    @Test
    void unescape_shouldNotModifyInvalidSequences() {
        // Only ~0 and ~1 are valid, other sequences should be left as-is
        assertThat(JsonPointerUtil.unescape("~2")).isEqualTo("~2");
        assertThat(JsonPointerUtil.unescape("~3")).isEqualTo("~3");
        assertThat(JsonPointerUtil.unescape("~9")).isEqualTo("~9");
        assertThat(JsonPointerUtil.unescape("~a")).isEqualTo("~a");
    }

    // ========== Escape Tests ==========

    @Test
    void escape_shouldHandleNull() {
        assertThat(JsonPointerUtil.escape(null)).isNull();
    }

    @Test
    void escape_shouldHandleEmptyString() {
        assertThat(JsonPointerUtil.escape("")).isEmpty();
    }

    @Test
    void escape_shouldHandleStringWithoutSpecialCharacters() {
        assertThat(JsonPointerUtil.escape("simpleString")).isEqualTo("simpleString");
        assertThat(JsonPointerUtil.escape("camelCaseString")).isEqualTo("camelCaseString");
        assertThat(JsonPointerUtil.escape("with-dashes")).isEqualTo("with-dashes");
        assertThat(JsonPointerUtil.escape("with_underscores")).isEqualTo("with_underscores");
    }

    @Test
    void escape_shouldConvertSlashToTilde1() {
        assertThat(JsonPointerUtil.escape("/")).isEqualTo("~1");
        assertThat(JsonPointerUtil.escape("types/order")).isEqualTo("types~1order");
        assertThat(JsonPointerUtil.escape("a/b/c")).isEqualTo("a~1b~1c");
    }

    @Test
    void escape_shouldConvertTildeToTilde0() {
        assertThat(JsonPointerUtil.escape("~")).isEqualTo("~0");
        assertThat(JsonPointerUtil.escape("some~key")).isEqualTo("some~0key");
        assertThat(JsonPointerUtil.escape("a~b~c")).isEqualTo("a~0b~0c");
    }

    @Test
    void escape_shouldHandleMixedSpecialCharacters() {
        // Slash and tilde in same string
        assertThat(JsonPointerUtil.escape("types/order~Action")).isEqualTo("types~1order~0Action");
        assertThat(JsonPointerUtil.escape("/~/~")).isEqualTo("~1~0~1~0");
    }

    @Test
    void escape_shouldHandleCIP57Examples() {
        assertThat(JsonPointerUtil.escape("types/automatic_payments/AutomatedPayment"))
                .isEqualTo("types~1automatic_payments~1AutomatedPayment");

        assertThat(JsonPointerUtil.escape("aiken/crypto/Hash"))
                .isEqualTo("aiken~1crypto~1Hash");

        assertThat(JsonPointerUtil.escape("cardano/address/Credential"))
                .isEqualTo("cardano~1address~1Credential");

        assertThat(JsonPointerUtil.escape("types/order/Action"))
                .isEqualTo("types~1order~1Action");
    }

    @Test
    void escape_shouldHandleOrderCorrectly() {
        // Test that ~ is processed before / to avoid double-processing
        // "~/" should become "~0~1" not "~1~0"
        assertThat(JsonPointerUtil.escape("~/")).isEqualTo("~0~1");
        assertThat(JsonPointerUtil.escape("/~")).isEqualTo("~1~0");
    }

    @Test
    void escape_shouldHandleMultipleOccurrences() {
        assertThat(JsonPointerUtil.escape("///")).isEqualTo("~1~1~1");
        assertThat(JsonPointerUtil.escape("~~~")).isEqualTo("~0~0~0");
        assertThat(JsonPointerUtil.escape("a/b/c/d")).isEqualTo("a~1b~1c~1d");
        assertThat(JsonPointerUtil.escape("a~b~c~d")).isEqualTo("a~0b~0c~0d");
    }

    @Test
    void escape_shouldHandleSpecialCharactersAtBoundaries() {
        // At start
        assertThat(JsonPointerUtil.escape("/start")).isEqualTo("~1start");
        assertThat(JsonPointerUtil.escape("~start")).isEqualTo("~0start");

        // At end
        assertThat(JsonPointerUtil.escape("end/")).isEqualTo("end~1");
        assertThat(JsonPointerUtil.escape("end~")).isEqualTo("end~0");

        // Both ends
        assertThat(JsonPointerUtil.escape("/middle/")).isEqualTo("~1middle~1");
        assertThat(JsonPointerUtil.escape("~middle~")).isEqualTo("~0middle~0");
    }

    // ========== Round-trip Tests ==========

    @Test
    void escapeAndUnescape_shouldBeReversible() {
        String[] testCases = {
                "simpleString",
                "types/order/Action",
                "some~key",
                "mixed/and~combined",
                "///",
                "~~~",
                "/~/~",
                "aiken/crypto/Hash",
                "types/automatic_payments/AutomatedPayment"
        };

        for (String testCase : testCases) {
            String escaped = JsonPointerUtil.escape(testCase);
            String unescaped = JsonPointerUtil.unescape(escaped);
            assertThat(unescaped)
                    .as("Round-trip for: %s", testCase)
                    .isEqualTo(testCase);
        }
    }

    @Test
    void unescapeAndEscape_shouldBeReversible() {
        String[] testCases = {
                "simpleString",
                "types~1order~1Action",
                "some~0key",
                "mixed~1and~0combined",
                "~1~1~1",
                "~0~0~0",
                "~1~0~1~0",
                "aiken~1crypto~1Hash",
                "types~1automatic_payments~1AutomatedPayment"
        };

        for (String testCase : testCases) {
            String unescaped = JsonPointerUtil.unescape(testCase);
            String escaped = JsonPointerUtil.escape(unescaped);
            assertThat(escaped)
                    .as("Round-trip for: %s", testCase)
                    .isEqualTo(testCase);
        }
    }
}
