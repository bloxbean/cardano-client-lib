package com.bloxbean.cardano.client.tools.readerdiff;

import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.yaci.core.config.YaciConfig;
import com.bloxbean.cardano.yaci.core.model.serializers.BlockSerializer;

import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The decoders under comparison. This class runs inside a side's class loader, and only Java types cross to the tool.
 * <p>
 * An outcome is {@code A:<canonical JSON>} (accepted), {@code R:<exception class>} (rejected),
 * {@code C:<error class>} (stack overflow), or {@code U:<message>} (accepted, but its model could not be written, so it
 * cannot be compared). A class that cannot link and an out of memory error are failures of the run, not rejections.
 * <p>
 * Yaci returns its CBOR fields here, so they are compared too.
 */
public final class Probes implements Supplier<Map<String, Function<byte[], String>>> {

    @FunctionalInterface
    interface Decoder {
        Object decode(byte[] cbor) throws Exception;
    }

    private final CanonicalJson json = new CanonicalJson();

    public Probes() {
        YaciConfig.INSTANCE.setReturnBlockCbor(true);
        YaciConfig.INSTANCE.setReturnTxBodyCbor(true);
        YaciConfig.INSTANCE.setReturnFullTxCbor(true);
    }

    @Override
    public Map<String, Function<byte[], String>> get() {
        Map<String, Function<byte[], String>> probes = new LinkedHashMap<>();
        probes.put("yaci-block", bytes -> outcome(cbor -> BlockSerializer.INSTANCE.deserialize(cbor), bytes));
        probes.put("ccl-tx", bytes -> outcome(Probes::transactionAndItsEncoding, bytes));
        DatasetDecoders.all().forEach((name, decoder) -> probes.put(name, bytes -> outcome(decoder, bytes)));
        return probes;
    }

    /** @return {@code model} and {@code serialize}, the hex of its encoding or the failure to encode it */
    private static Object transactionAndItsEncoding(byte[] cbor) throws Exception {
        Transaction transaction = Transaction.deserialize(cbor);
        String encoding;
        try {
            encoding = HexFormat.of().formatHex(transaction.serialize());
        } catch (Exception e) {
            encoding = "failed: " + e.getClass().getName();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("model", transaction);
        result.put("serialize", encoding);
        return result;
    }

    private String outcome(Decoder decoder, byte[] input) {
        Object model;
        try {
            model = decoder.decode(input);
        } catch (StackOverflowError e) {
            return "C:" + e.getClass().getName();
        } catch (VirtualMachineError | LinkageError e) {
            throw e;
        } catch (Throwable t) {
            return "R:" + t.getClass().getName();
        }
        try {
            return "A:" + json.write(model);
        } catch (Exception e) {
            return "U:" + e;
        }
    }

    /** The block probes as bare parsers for the benchmark: no JSON, and Yaci's configuration left at its defaults. */
    public static final class Parsers implements Supplier<Map<String, Function<byte[], Object>>> {
        @Override
        public Map<String, Function<byte[], Object>> get() {
            Map<String, Function<byte[], Object>> parsers = new LinkedHashMap<>();
            parsers.put("yaci-block", bytes -> BlockSerializer.INSTANCE.deserialize(bytes));
            parsers.put("ccl-tx", bytes -> {
                try {
                    return Transaction.deserialize(bytes);
                } catch (Exception e) {
                    throw new IllegalArgumentException(e);
                }
            });
            return parsers;
        }
    }
}
