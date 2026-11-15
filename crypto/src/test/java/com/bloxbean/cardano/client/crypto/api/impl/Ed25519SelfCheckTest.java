package com.bloxbean.cardano.client.crypto.api.impl;

import com.bloxbean.cardano.client.crypto.api.Ed25519Verifier;
import org.bouncycastle.math.ec.rfc8032.Ed25519;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The self-check that guards {@link Ed25519Equation} against a bcprov it does not agree with libsodium on. */
class Ed25519SelfCheckTest {

    private static final String ADVICE = "select I2pEd25519Verifier";

    @Test
    void passesOnTheBouncyCastleOnTheClasspath() {
        assertNull(Ed25519Equation.failures(Ed25519Equation.buildTables(), Ed25519Equation.SELF_CHECK_VECTORS));
        assertNull(Ed25519Equation.selfCheck());
        assertNull(Ed25519Equation.selfCheck());
        Ed25519Equation.Vector rfc = Ed25519Equation.SELF_CHECK_VECTORS.get(0);
        HexFormat hex = HexFormat.of();
        assertTrue(new BouncyCastleEd25519Verifier().verify(hex.parseHex(rfc.signature()), new byte[0],
                hex.parseHex(rfc.publicKey())));
    }

    @Test
    void wouldCatchACofactoredEquation() {
        // BouncyCastle's own verify checks the equation times the cofactor; on these vectors it disagrees with
        // libsodium, so an equation that behaved like it would fail the self-check.
        HexFormat hex = HexFormat.of();
        int disagreements = 0;
        for (Ed25519Equation.Vector v : Ed25519Equation.SELF_CHECK_VECTORS) {
            byte[] message = hex.parseHex(v.message());
            boolean cofactored = Ed25519.verify(hex.parseHex(v.signature()), 0, hex.parseHex(v.publicKey()), 0,
                    message, 0, message.length);
            if (cofactored != v.libsodium()) {
                disagreements++;
            }
        }
        assertEquals(1, disagreements);
    }

    @Test
    void tripsWhenTheEquationDisagreesWithLibsodium() {
        List<Ed25519Equation.Vector> wrong = Ed25519Equation.SELF_CHECK_VECTORS.stream()
                .map(v -> new Ed25519Equation.Vector(v.name(), v.publicKey(), v.message(), v.signature(),
                        !v.libsodium()))
                .toList();
        String failure = Ed25519Equation.failures(Ed25519Equation.buildTables(), wrong);
        assertNotNull(failure);
        assertTrue(failure.contains("RFC 8032 section 7.1 test 1: accepted, libsodium rejects"), failure);
        assertTrue(failure.contains("an R with a small-order part: rejected, libsodium accepts"), failure);
        assertTrue(failure.matches("(?s).*BouncyCastle bcprov \\d+\\.\\d+.*bcprov-jdk18on.*"), failure);
    }

    @Test
    void keepsWrongVerdictsAndThrowsThemEveryTime() {
        // A table of the wrong points: no exception, but the RFC 8032 signature no longer verifies.
        AtomicInteger builds = new AtomicInteger();
        Ed25519Equation.SelfCheck check = new Ed25519Equation.SelfCheck(() -> {
            builds.incrementAndGet();
            Ed25519Equation.Tables tables = Ed25519Equation.buildTables();
            List<Object> reversed = Arrays.asList((Object[]) tables.baseOddMultiples);
            Collections.reverse(reversed);
            return tables;
        });
        LinkageError first = assertThrows(LinkageError.class, check::passed);
        LinkageError second = assertThrows(LinkageError.class, check::passed);
        assertEquals(first.getMessage(), second.getMessage());
        assertTrue(first.getMessage().contains("failed its self-check"), first.getMessage());
        assertTrue(first.getMessage().contains("RFC 8032 section 7.1 test 1: rejected, libsodium accepts"),
                first.getMessage());
        assertNull(first.getCause());
        assertEquals(1, builds.get());
    }

    @Test
    void keepsAMissingX25519FieldWithItsAdviceOnce() {
        // What a bcprov before 1.67 gives when the tables are built.
        AtomicInteger builds = new AtomicInteger();
        NoClassDefFoundError missing = new NoClassDefFoundError("org/bouncycastle/math/ec/rfc7748/X25519Field");
        Ed25519Equation.SelfCheck check = new Ed25519Equation.SelfCheck(() -> {
            builds.incrementAndGet();
            throw missing;
        });
        for (int i = 0; i < 3; i++) {
            LinkageError error = assertThrows(LinkageError.class, check::passed);
            assertTrue(error.getMessage().startsWith("Ed25519 verification cannot run on BouncyCastle bcprov "),
                    error.getMessage());
            assertEquals(1, occurrences(error.getMessage(), ADVICE), error.getMessage());
            assertSame(missing, error.getCause());
        }
        assertEquals(1, builds.get());
    }

    @Test
    void keepsARuntimeExceptionFromTheArithmeticAsAnIncompatibility() {
        // The tables build, then the self-check's run of the equation throws an ordinary exception, as a bcprov whose
        // X25519Field changed its limb layout would. It is a LinkageError every time, never a signature verdict.
        AtomicInteger builds = new AtomicInteger();
        Ed25519Equation.SelfCheck check = new Ed25519Equation.SelfCheck(() -> {
            builds.incrementAndGet();
            Ed25519Equation.Tables tables = Ed25519Equation.buildTables();
            Arrays.fill(tables.baseOddMultiples, null);
            return tables;
        });
        LinkageError first = assertThrows(LinkageError.class, check::passed);
        LinkageError second = assertThrows(LinkageError.class, check::passed);
        assertEquals(first.getMessage(), second.getMessage());
        assertTrue(first.getCause() instanceof NullPointerException, String.valueOf(first.getCause()));
        assertEquals(1, occurrences(first.getMessage(), ADVICE), first.getMessage());
        assertEquals(1, builds.get());
    }

    @Test
    void triesAgainAfterAnOutOfMemoryError() {
        AtomicInteger builds = new AtomicInteger();
        Supplier<Ed25519Equation.Tables> once = () -> {
            if (builds.incrementAndGet() == 1) {
                throw new OutOfMemoryError("injected");
            }
            return Ed25519Equation.buildTables();
        };
        Ed25519Equation.SelfCheck check = new Ed25519Equation.SelfCheck(once);
        assertThrows(OutOfMemoryError.class, check::passed);
        Ed25519Equation.Tables tables = check.passed();
        assertNotNull(tables);
        assertSame(tables, check.passed());
        assertNull(check.outcome().failure());
        assertEquals(2, builds.get());
    }

    /**
     * A bcprov without {@code X25519Field} (as before 1.67), in a class loader of its own so that the real classes
     * are used from a fresh start: every verification throws the advice, not a "Could not initialize class" error.
     */
    @Test
    void theDefaultVerifierGivesItsAdviceOnEveryCallWithoutX25519Field() throws Exception {
        ClassLoader loader = new WithoutX25519Field(Ed25519SelfCheckTest.class.getClassLoader());
        Ed25519Verifier verifier = (Ed25519Verifier) loader
                .loadClass(BouncyCastleEd25519Verifier.class.getName()).getDeclaredConstructor().newInstance();
        assertFalse(verifier.getClass() == BouncyCastleEd25519Verifier.class);

        Ed25519Equation.Vector rfc = Ed25519Equation.SELF_CHECK_VECTORS.get(0);
        HexFormat hex = HexFormat.of();
        byte[] signature = hex.parseHex(rfc.signature());
        byte[] key = hex.parseHex(rfc.publicKey());
        for (int i = 0; i < 3; i++) {
            LinkageError error = assertThrows(LinkageError.class, () -> verifier.verify(signature, new byte[0], key));
            assertTrue(error.getMessage().startsWith("Ed25519 verification cannot run on BouncyCastle bcprov "),
                    error.getMessage());
            assertTrue(error.getMessage().contains("X25519Field"), error.getMessage());
            assertEquals(1, occurrences(error.getMessage(), ADVICE), error.getMessage());
        }
        // Input refused by libsodium's checks still gives false without touching the arithmetic.
        assertFalse(verifier.verify(new byte[63], new byte[0], key));
        // The i2p verifier still works in the same class loader: the documented way out.
        Ed25519Verifier i2p = (Ed25519Verifier) loader
                .loadClass(I2pEd25519Verifier.class.getName()).getDeclaredConstructor().newInstance();
        assertTrue(i2p.verify(signature, new byte[0], key));
    }

    @Test
    void namesTheBouncyCastleVersion() {
        assertTrue(Ed25519Equation.bouncyCastleVersion().matches("\\d+\\.\\d+(\\.\\d+)? \\(.*bcprov.*\\.jar\\)"),
                Ed25519Equation.bouncyCastleVersion());
    }

    /**
     * A bcprov jar inside an application jar, as in a Spring Boot executable jar: the version is the inner jar's,
     * read through the jar connection the URL's handler gives, not the outer jar's from the text of the URL.
     */
    @Test
    @SuppressWarnings("deprecation")
    void namesTheInnerJarOfANestedJar(@TempDir Path dir) throws IOException {
        Path outer = jar(dir.resolve("app.jar"), "APPLICATION-9.9");
        Path inner = jar(dir.resolve("bcprov-jdk18on-1.86.jar"), "1.86.0");
        String nested = "jar:" + outer.toUri() + "!/BOOT-INF/lib/bcprov-jdk18on-1.86.jar!/";
        URLStreamHandler handler = new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) throws IOException {
                return new JarURLConnection(url) {
                    @Override
                    public void connect() {
                        connected = true;
                    }

                    @Override
                    public JarFile getJarFile() throws IOException {
                        return new JarFile(inner.toFile());
                    }

                    @Override
                    public URL getJarFileURL() {
                        try {
                            return new URL(null, nested.substring(0, nested.length() - 2), handler());
                        } catch (MalformedURLException e) {
                            throw new IllegalStateException(e);
                        }
                    }

                    @Override
                    public Manifest getManifest() throws IOException {
                        try (JarFile jar = getJarFile()) {
                            return jar.getManifest();
                        }
                    }
                };
            }

            private URLStreamHandler handler() {
                return this;
            }
        };
        URL classFile = new URL(null, nested + "org/bouncycastle/math/ec/rfc7748/X25519Field.class", handler);

        String version = Ed25519Equation.bouncyCastleVersion(classFile);
        assertTrue(version.startsWith("1.86.0 ("), version);
        assertTrue(version.contains("app.jar!/BOOT-INF/lib/bcprov-jdk18on-1.86.jar"), version);
        assertFalse(version.contains("APPLICATION"), version);
    }

    @Test
    void anUnknownJarIsOfUnknownVersion() throws IOException {
        assertEquals("of unknown version", Ed25519Equation.bouncyCastleVersion(null));
        assertEquals("of unknown version", Ed25519Equation.bouncyCastleVersion(Path.of(".").toUri().toURL()));
    }

    private static Path jar(Path path, String bundleVersion) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Bundle-Version", bundleVersion);
        try (OutputStream out = Files.newOutputStream(path); JarOutputStream jar = new JarOutputStream(out, manifest)) {
            jar.flush();
        }
        return path;
    }

    private static int occurrences(String text, String part) {
        int count = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) {
            count++;
        }
        return count;
    }

    /**
     * Defines this package's classes and BouncyCastle's afresh, except {@code X25519Field}, which it does not find;
     * everything else comes from the parent.
     */
    private static final class WithoutX25519Field extends ClassLoader {
        private static final String HIDDEN = "org.bouncycastle.math.ec.rfc7748.X25519Field";

        WithoutX25519Field(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(HIDDEN)) {
                throw new ClassNotFoundException(name);
            }
            boolean fresh = name.startsWith(Ed25519Equation.class.getPackageName() + ".")
                    || name.startsWith("org.bouncycastle.");
            if (!fresh) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (in == null) {
                            throw new ClassNotFoundException(name);
                        }
                        byte[] bytes = in.readAllBytes();
                        c = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                }
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }
        }
    }
}
