package com.bloxbean.cardano.client.crypto.api.impl;

import com.bloxbean.cardano.client.crypto.api.Ed25519Verifier;
import com.bloxbean.cardano.client.crypto.config.CryptoConfiguration;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The default verifier runs on Android, which lacks some Java APIs on older API levels. Core library desugaring does
 * not supply the ones below, so the classes on the default verify path must not refer to them.
 */
class Ed25519AndroidApiTest {

    /** A reference as {@code owner.name} (or the class alone), with the Android API level that added it. */
    private static final Map<String, Integer> NEWER_THAN_ANDROID = Map.of(
            "java/util/HexFormat", 34,
            "java/lang/StringBuilder.isEmpty", 35,
            "java/lang/CharSequence.isEmpty", 35,
            "java/math/BigInteger.TWO", 33);

    @Test
    void theDefaultVerifyPathUsesNoJavaApiThatOlderAndroidLacks() throws IOException {
        List<Class<?>> classes = new ArrayList<>(List.of(BouncyCastleEd25519Verifier.class, Ed25519InputChecks.class,
                Ed25519Verifier.class, CryptoConfiguration.class, EdDSASigningProvider.class,
                Ed25519Equation.class));
        classes.addAll(List.of(Ed25519Equation.class.getDeclaredClasses()));

        Map<String, String> found = new TreeMap<>();
        for (Class<?> c : classes) {
            for (String banned : newerThanAndroid(c)) {
                found.put(c.getName() + " -> " + banned, "Android API " + NEWER_THAN_ANDROID.get(banned));
            }
        }
        assertThat(found).isEmpty();
    }

    @Test
    void wouldSeeEachOfThem() throws IOException {
        assertThat(newerThanAndroid(UsesThem.class)).containsExactlyInAnyOrder("java/util/HexFormat",
                "java/lang/StringBuilder.isEmpty", "java/math/BigInteger.TWO");
    }

    /** What the code before the Android fix used. */
    @SuppressWarnings("unused")
    private static final class UsesThem {
        static boolean all() {
            StringBuilder b = new StringBuilder();
            return java.util.HexFormat.of().parseHex("00").length == 1 && b.isEmpty()
                    && java.math.BigInteger.TWO.signum() == 1;
        }
    }

    private static Set<String> newerThanAndroid(Class<?> c) throws IOException {
        Set<String> found = new HashSet<>();
        for (String reference : references(c)) {
            String owner = reference.contains(".") ? reference.substring(0, reference.indexOf('.')) : reference;
            for (String candidate : List.of(reference, owner)) {
                if (NEWER_THAN_ANDROID.containsKey(candidate)) {
                    found.add(candidate);
                }
            }
        }
        return found;
    }

    /** The classes, fields and methods the constant pool of {@code c} refers to, as {@code owner.name}. */
    private static Set<String> references(Class<?> c) throws IOException {
        String resource = "/" + c.getName().replace('.', '/') + ".class";
        try (InputStream raw = c.getResourceAsStream(resource); DataInputStream in = new DataInputStream(raw)) {
            in.readInt(); // magic
            in.readUnsignedShort(); // minor
            in.readUnsignedShort(); // major
            int count = in.readUnsignedShort();
            String[] utf8 = new String[count];
            int[] classNames = new int[count];
            int[][] members = new int[count][];
            int[][] nameAndTypes = new int[count][];
            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> utf8[i] = in.readUTF();
                    case 7 -> classNames[i] = in.readUnsignedShort();
                    case 9, 10, 11 -> members[i] = new int[]{in.readUnsignedShort(), in.readUnsignedShort()};
                    case 12 -> nameAndTypes[i] = new int[]{in.readUnsignedShort(), in.readUnsignedShort()};
                    case 3, 4, 17, 18 -> in.readInt();
                    case 5, 6 -> {
                        in.readLong();
                        i++;
                    }
                    case 8, 16, 19, 20 -> in.readUnsignedShort();
                    case 15 -> {
                        in.readUnsignedByte();
                        in.readUnsignedShort();
                    }
                    default -> throw new IOException("constant pool tag " + tag + " in " + resource);
                }
            }
            Set<String> references = new HashSet<>();
            for (int i = 1; i < count; i++) {
                if (classNames[i] != 0) {
                    references.add(utf8[classNames[i]]);
                }
                if (members[i] != null) {
                    String owner = utf8[classNames[members[i][0]]];
                    String name = utf8[nameAndTypes[members[i][1]][0]];
                    references.add(owner + "." + name);
                }
            }
            return references;
        }
    }
}
