package com.bloxbean.cardano.client.quicktx.intent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UtxoRefDeserializer#isPlaceholder} replaced a backtracking regular expression; it must accept exactly what the
 * expression accepted, in linear time.
 */
class UtxoRefPlaceholderTest {

    /** The expression it replaced, kept as the oracle. */
    private static final Pattern REGEX = Pattern.compile("\\$\\{.*[^{}\\s].*}");

    @Test
    void agreesWithTheRegexOnEdgeCases() {
        List<String> cases = List.of("", "$", "${", "${}", "${}}", "${a}", "${ a }", "${{ inputs.index }}", "${{}}",
                "${ }", "${\t}", "${\u000B}", "${\f}", "${\u00A0}", "${{ }}", "${a\nb}", "${a\rb}", "${a\u0085b}",
                "${a\u2028b}", "${a\u2029b}", "${\u2028}", "${a\u2028\u2028b}", "${a\u0085b\u2029c}", "${{\u0085}}",
                "${a\r\nb}", "${\n}", "$ {a}", "${a}x", "x${a}", "${a}}", "${{a}", "${}a}", "${\uD83D\uDE00}",
                "${\uD800}", "0", "12", "${0}");
        for (String text : cases)
            assertThat(UtxoRefDeserializer.isPlaceholder(text)).as("'%s'", text).isEqualTo(REGEX.matcher(text).matches());
    }

    @Test
    void agreesWithTheRegexOnRandomText() {
        char[] alphabet = {'$', '{', '}', ' ', '\t', '\n', '\r', '\u000B', '\f',
                '\u0085', '\u2028', '\u2029', 'a', '0', '.', '\u00A0'};
        Random random = new Random(725);
        for (int run = 0; run < 200_000; run++) {
            StringBuilder text = new StringBuilder(random.nextBoolean() ? "${" : "");
            for (int i = random.nextInt(8); i > 0; i--) text.append(alphabet[random.nextInt(alphabet.length)]);
            if (random.nextBoolean()) text.append('}');
            String value = text.toString();
            assertThat(UtxoRefDeserializer.isPlaceholder(value)).as("'%s'", value).isEqualTo(REGEX.matcher(value).matches());
        }
    }

    @Test
    @Timeout(10)
    void longHostileTextIsCheckedInLinearTime() {
        // The regular expression backtracked quadratically on these shapes (about 3.7 s at 40,000 characters, so
        // about 90 s at 200,000); the scan reads each once.
        String name = "a".repeat(200_000);
        assertThat(UtxoRefDeserializer.isPlaceholder("${" + name)).isFalse();
        assertThat(UtxoRefDeserializer.isPlaceholder("${" + name + "\n}")).isFalse();
        assertThat(UtxoRefDeserializer.isPlaceholder("${" + name + "}")).isTrue();
    }
}
