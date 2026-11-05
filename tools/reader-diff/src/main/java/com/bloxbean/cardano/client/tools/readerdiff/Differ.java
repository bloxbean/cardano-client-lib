package com.bloxbean.cardano.client.tools.readerdiff;

import com.bloxbean.cardano.client.transaction.raw.RawBlock;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

/** Runs probes on both sides and records the comparison. */
final class Differ {
    private final Map<String, Function<byte[], String>> oldProbes;
    private final Map<String, Function<byte[], String>> newProbes;
    private final Allow allow;
    private final Report report;

    Differ(Side oldSide, Side newSide, Allow allow, Report report) throws ReflectiveOperationException {
        oldProbes = oldSide.probes();
        newProbes = newSide.probes();
        this.allow = allow;
        this.report = report;
    }

    /** {@code yaci-block} on the block, and {@code ccl-tx} on each of its transactions, split by the tool's raw view. */
    void block(byte[] block) throws IOException {
        String era;
        String context;
        try {
            era = Blocks.eraName(block);
            context = "slot=" + Blocks.slot(block);
        } catch (RuntimeException e) { // a hand-made input: the decoders decide what it is
            era = "unreadable";
            context = e.toString();
        }
        probe("yaci-block", era, context, block);
        RawBlock raw;
        try {
            raw = RawBlock.of(block);
        } catch (RuntimeException e) {
            report.recordFailure("ccl-tx", era, context, block, "split-failed", e.toString());
            report.inputDone();
            return;
        }
        for (int i = 0; i < raw.txCount(); i++) {
            String txContext = context + " tx=" + i;
            byte[] tx;
            try {
                tx = raw.txBytes(i);
            } catch (RuntimeException e) {
                report.recordFailure("ccl-tx", era, txContext, block, "split-failed", e.toString());
                continue;
            }
            probe("ccl-tx", era, txContext, tx);
        }
        report.inputDone();
    }

    void probe(String probe, String group, String context, byte[] input) throws IOException {
        report.record(probe, group, context, input, OutcomeDiff.compare(probe, input,
                oldProbes.get(probe).apply(input), newProbes.get(probe).apply(input), allow));
    }
}
