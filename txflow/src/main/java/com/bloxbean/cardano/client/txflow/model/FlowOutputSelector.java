package com.bloxbean.cardano.client.txflow.model;

import com.bloxbean.cardano.client.api.model.Utxo;

import java.util.List;

/**
 * Immutable selection and cardinality rule for a named step output.
 *
 * <p>Selectors operate on the ordered UTXOs produced by a transaction. The
 * portable v1alpha1 contract exposes index selection with exactly-one cardinality,
 * so an absent index is an execution error rather than an unresolved reference.</p>
 */
public final class FlowOutputSelector {
    /** Cardinality expected after applying a selector. */
    public enum Expect {
        /** Exactly one output must be selected. */
        EXACTLY_ONE,
        /** Selection may be empty but cannot contain multiple outputs. */
        ZERO_OR_ONE,
        /** Any number of outputs is accepted. */
        MANY
    }

    private final Integer outputIndex;
    private final Expect expect;

    private FlowOutputSelector(Integer outputIndex, Expect expect) {
        this.outputIndex = outputIndex;
        this.expect = expect;
    }

    /**
     * Selects one output by its zero-based transaction output index.
     *
     * @param index non-negative output index
     * @return exactly-one selector
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public static FlowOutputSelector atIndex(int index) {
        if (index < 0) throw new IllegalArgumentException("output index cannot be negative");
        return new FlowOutputSelector(index, Expect.EXACTLY_ONE);
    }

    /**
     * Returns an equivalent immutable selector requiring exactly one result.
     *
     * @return exactly-one selector
     */
    public FlowOutputSelector expectExactlyOne() {
        return new FlowOutputSelector(outputIndex, Expect.EXACTLY_ONE);
    }

    /**
     * Returns the selected zero-based output index.
     *
     * @return output index
     */
    public Integer getOutputIndex() {
        return outputIndex;
    }

    /**
     * Returns the required result cardinality.
     *
     * @return cardinality rule
     */
    public Expect getExpect() {
        return expect;
    }

    /**
     * Applies this selector to a transaction's ordered outputs and enforces its
     * cardinality rule.
     *
     * @param outputs ordered transaction outputs
     * @return immutable list of selected outputs
     * @throws IllegalStateException if the selected result violates cardinality
     */
    public List<Utxo> select(List<Utxo> outputs) {
        List<Utxo> selected;
        if (outputIndex == null) {
            selected = List.copyOf(outputs);
        } else if (outputIndex < outputs.size()) {
            selected = List.of(outputs.get(outputIndex));
        } else {
            selected = List.of();
        }
        if (expect == Expect.EXACTLY_ONE && selected.size() != 1) {
            throw new IllegalStateException("Expected exactly one output but selected " + selected.size());
        }
        if (expect == Expect.ZERO_OR_ONE && selected.size() > 1) {
            throw new IllegalStateException("Expected at most one output but selected " + selected.size());
        }
        return selected;
    }
}
