package com.bloxbean.cardano.client.txflow.stream;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prevents emitted core error literals and the public catalog from drifting. */
class TxStreamCodesCatalogTest {
    private static final Pattern CODE_LITERAL =
            Pattern.compile("\\\"(TXSTREAM_[A-Z0-9_]+)\\\"");

    @Test
    void everyCoreLiteralHasExactlyOneCatalogConstantAndNoConstantIsOrphaned()
            throws Exception {
        Set<String> emitted = emittedCoreCodes();
        Set<String> catalog = new TreeSet<>();

        for (Field field : TxStreamCodes.class.getDeclaredFields()) {
            if (!Modifier.isPublic(field.getModifiers())
                    || !Modifier.isStatic(field.getModifiers())
                    || field.getType() != String.class) {
                continue;
            }
            String value = (String) field.get(null);
            assertEquals("TXSTREAM_" + field.getName(), value,
                    "constant names must be the code suffix for predictable discovery");
            assertTrue(catalog.add(value), "duplicate catalog value: " + value);
        }

        assertFalse(emitted.isEmpty(), "the source scan must find core TxStream codes");
        assertEquals(emitted, catalog,
                "missing values are uncatalogued core literals; extra values are orphan constants");
    }

    private Set<String> emittedCoreCodes() throws Exception {
        Path sourceRoot = Path.of("src/main/java/com/bloxbean/cardano/client/txflow");
        Path catalogSource = sourceRoot.resolve("stream/TxStreamCodes.java");
        Set<String> codes = new TreeSet<>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path source : files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.equals(catalogSource))
                    .toList()) {
                Matcher matcher = CODE_LITERAL.matcher(Files.readString(source));
                while (matcher.find()) {
                    codes.add(matcher.group(1));
                }
            }
        }
        return codes;
    }
}
