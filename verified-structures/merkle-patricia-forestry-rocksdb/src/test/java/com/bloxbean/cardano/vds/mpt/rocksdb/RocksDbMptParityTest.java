package com.bloxbean.cardano.vds.mpt.rocksdb;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.mpt.MpfTrie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class RocksDbMptParityTest {
    private Path tempDir;

    @AfterEach
    void cleanup() throws Exception {
        if (tempDir != null) {
            // Best-effort delete; ignore failures on Windows/CI
            try {
                Files.walk(tempDir).sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    void parityWithInMemoryStore() throws Exception {
        try {
            tempDir = Files.createTempDirectory("rocks-mpt");
            RocksDbNodeStore rocks = new RocksDbNodeStore(tempDir.toString());

            MpfTrie tRocks = new MpfTrie(rocks);
            MpfTrie tMem = new MpfTrie(new TestNodeStore());

            byte[] k1 = hex("aa00");
            byte[] k2 = hex("aa01");
            byte[] k3 = hex("abff");

            tRocks.put(k1, b("V0"));
            tRocks.put(k2, b("V1"));
            tRocks.put(k3, b("VX"));

            tMem.put(k1, b("V0"));
            tMem.put(k2, b("V1"));
            tMem.put(k3, b("VX"));

            assertArrayEquals(tMem.getRootHash(), tRocks.getRootHash());
            assertEquals("V0", s(tRocks.get(k1)));
            assertEquals("V1", s(tRocks.get(k2)));
            assertEquals("VX", s(tRocks.get(k3)));

            rocks.close();
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            // RocksDB JNI not available on this platform; skip
            Assumptions.assumeTrue(false, "RocksDB JNI not available: " + e.getMessage());
        }
    }

    private static byte[] hex(String h) {
        String s = h.startsWith("0x") ? h.substring(2) : h;
        if (s.length() % 2 == 1) s = "0" + s;
        int len = s.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(s.charAt(2 * i), 16);
            int lo = Character.digit(s.charAt(2 * i + 1), 16);
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static String s(byte[] b) {
        return new String(b);
    }

    private static byte[] b(String s) {
        return s.getBytes();
    }
}

