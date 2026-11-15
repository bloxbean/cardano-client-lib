package com.bloxbean.cardano.client.transaction.spec;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR 0004 W2: every public constructor, method and field that the classes ADR 0004 touches had before it still exists
 * with the same signature. The listing was read by reflection from the classes before ADR 0004, rendered as
 * {@link #signatures(Class)} renders them now.
 */
class PublicApiCompatibilityTest {
    private static final String LISTING = "/compat/adr0004-public-api.txt";

    @Test
    void everyPublicSignatureBeforeAdr0004StillExists() throws Exception {
        List<String> before = listing();
        Set<String> now = new TreeSet<>();
        for (String type : before.stream().map(line -> line.substring(0, line.indexOf('#'))).distinct()
                .collect(Collectors.toList()))
            now.addAll(signatures(Class.forName(type)));

        assertThat(before).hasSizeGreaterThan(500);
        assertThat(before.stream().filter(signature -> !now.contains(signature)).collect(Collectors.toList()))
                .as("public signatures that changed or disappeared").isEmpty();
    }

    private static Set<String> signatures(Class<?> type) {
        Set<String> signatures = new TreeSet<>();
        for (Constructor<?> constructor : type.getConstructors())
            signatures.add(type.getName() + "#<init>(" + parameters(constructor.getParameterTypes()) + ")");
        for (Method method : type.getMethods()) {
            if (method.getDeclaringClass() == Object.class || method.isSynthetic() || method.isBridge())
                continue;
            signatures.add(type.getName() + "#" + (Modifier.isStatic(method.getModifiers()) ? "static " : "")
                    + method.getName() + "(" + parameters(method.getParameterTypes()) + ")"
                    + method.getReturnType().getTypeName());
        }
        for (Field field : type.getFields())
            signatures.add(type.getName() + "#field " + (Modifier.isStatic(field.getModifiers()) ? "static " : "")
                    + field.getName() + ":" + field.getType().getTypeName());
        return signatures;
    }

    private static String parameters(Class<?>[] types) {
        return Arrays.stream(types).map(Class::getTypeName).collect(Collectors.joining(","));
    }

    private static List<String> listing() throws IOException {
        try (InputStream in = PublicApiCompatibilityTest.class.getResourceAsStream(LISTING)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#")).collect(Collectors.toList());
        }
    }
}
