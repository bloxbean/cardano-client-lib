package com.bloxbean.cardano.client.quicktx.intent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Declarative reference to a named output produced by an earlier TxFlow step.
 *
 * <p>This is part of QuickTx's serializable input-reference model so an embedded
 * transaction can round-trip without depending on TxFlow classes. TxFlow validates
 * that the producer and output name exist, then replaces this reference with the
 * concrete {@link UtxoRef} for each execution attempt. QuickTx does not resolve the
 * reference by itself.</p>
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class FlowOutputRef extends UtxoRef {
    @JsonProperty("flow_output")
    private Pointer flowOutput;

    /**
     * Creates a reference to a named output of a producer step.
     *
     * @param step producer step identifier
     * @param output output binding name declared by that step
     */
    public FlowOutputRef(String step, String output) {
        this.flowOutput = new Pointer(step, output);
    }

    /** Coordinates identifying a named output within a flow definition. */
    @Data
    @NoArgsConstructor
    public static class Pointer {
        private String step;
        private String output;

        /**
         * Creates a pointer to one producer's named output.
         *
         * @param step producer step identifier
         * @param output output binding name
         */
        public Pointer(String step, String output) {
            this.step = step;
            this.output = output;
        }
    }
}
