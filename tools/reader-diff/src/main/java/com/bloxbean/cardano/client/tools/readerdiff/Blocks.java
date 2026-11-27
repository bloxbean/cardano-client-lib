package com.bloxbean.cardano.client.tools.readerdiff;

import com.bloxbean.cardano.client.common.cbor.CborSpan;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads the little the tool needs from a block's raw bytes, on the tool's own {@link CborSpan}, and the block file: for
 * each block, a 4-byte big-endian length and its bytes.
 */
final class Blocks {
    private static final List<String> ERAS = List.of("byron", "byron", "shelley", "allegra", "mary", "alonzo",
            "babbage", "conway");

    @FunctionalInterface
    interface BlockConsumer {
        void accept(byte[] block) throws Exception;
    }

    private Blocks() {
    }

    /** @param bytes {@code [era, block]} or a bare block, either one optionally in tag 24 */
    private static CborSpan top(byte[] bytes) {
        CborSpan top = CborSpan.of(bytes);
        return top.tag() == 24 ? top.embedded() : top;
    }

    static int eraIndex(byte[] block) {
        return (int) top(block).get(0).asLong();
    }

    static String eraName(byte[] block) {
        int index = eraIndex(block);
        return index < ERAS.size() ? ERAS.get(index) : "era-" + index;
    }

    /** @return the original bytes of the header of a block, with or without its envelope */
    static byte[] headerBytes(byte[] block) {
        CborSpan top = top(block);
        boolean envelope = top.size() == 2 && top.get(0).majorType() == 0;
        return (envelope ? top.get(1) : top).get(0).bytes();
    }

    /** @return the slot of a Shelley-or-later block with its envelope */
    static long slot(byte[] block) {
        return top(block).get(1).get(0).get(0).get(1).asLong();
    }

    static void read(Path file, BlockConsumer consumer) throws Exception {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file), 1 << 20))) {
            while (true) {
                int length;
                try {
                    length = in.readInt();
                } catch (EOFException end) {
                    return;
                }
                byte[] block = new byte[length];
                in.readFully(block);
                consumer.accept(block);
            }
        }
    }

    static DataOutputStream writer(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        return new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file), 1 << 20));
    }

    static void write(DataOutputStream out, byte[] block) throws IOException {
        out.writeInt(block.length);
        out.write(block);
    }
}
