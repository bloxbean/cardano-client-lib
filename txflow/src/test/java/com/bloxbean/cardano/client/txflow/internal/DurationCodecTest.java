package com.bloxbean.cardano.client.txflow.internal;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurationCodecTest {
    @Test
    void legacyAndPortableGrammarsAreExplicit() {
        assertEquals(Duration.ofSeconds(30), DurationCodec.parseLegacy("30", "timeout"));
        assertThrows(IllegalArgumentException.class,
                () -> DurationCodec.parsePortable("30", "timeout"));

        assertEquals(Duration.ofHours(2), DurationCodec.parsePortable("2h", "timeout"));
        assertThrows(IllegalArgumentException.class,
                () -> DurationCodec.parseLegacy("2h", "timeout"));
        assertEquals(Duration.ofHours(2), DurationCodec.parsePortable("PT2H", "timeout"));
    }

    @Test
    void negativeValuesRemainVisibleToOwningPolicyValidation() {
        assertEquals(Duration.ofSeconds(-5),
                DurationCodec.parsePortable("-5s", "timeout"));
        assertEquals("1500ms", DurationCodec.format(Duration.ofMillis(1_500)));
    }
}
