package com.bloxbean.cardano.client.quicktx.intent;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

/**
 * Selects the appropriate {@link UtxoRef} representation from its serialized shape.
 * A {@code flow_output} object becomes a {@link FlowOutputRef}; otherwise the input
 * is decoded as a concrete transaction-hash/output-index reference. Semantic validation
 * remains the responsibility of the owning intent or TxFlow.
 * <p>
 * A strict reader (one that enables {@link DeserializationFeature#FAIL_ON_IGNORED_PROPERTIES},
 * as the TxPlan readers and TxFlow's portable compiler do) also checks the shape: a reference is
 * exactly {@code {tx_hash, output_index}} or {@code {flow_output: {step, output}}}, with no other
 * keys. A lenient reader keeps the historical behaviour and ignores keys it does not use.
 */
final class UtxoRefDeserializer extends StdDeserializer<UtxoRef> {
    UtxoRefDeserializer() {
        super(UtxoRef.class);
    }

    @Override
    public UtxoRef deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        if (context.isEnabled(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)) checkShape(node, context);
        JsonNode flowOutput = node.get("flow_output");
        if (flowOutput != null && flowOutput.isObject()) {
            return new FlowOutputRef(flowOutput.path("step").asText(null),
                    flowOutput.path("output").asText(null));
        }

        UtxoRef ref = new UtxoRef();
        JsonNode txHash = node.get("tx_hash");
        if (txHash != null && !txHash.isNull()) ref.setTxHash(txHash.asText());
        JsonNode outputIndex = node.get("output_index");
        if (outputIndex != null && !outputIndex.isNull()) {
            if (outputIndex.isNumber()) ref.setOutputIndex(outputIndex.intValue());
            else ref.setOutputIndexFromYaml(outputIndex.asText());
        }
        return ref;
    }

    private static void checkShape(JsonNode node, DeserializationContext context) throws IOException {
        if (!node.isObject())
            context.reportInputMismatch(UtxoRef.class, "A UTxO reference must be an object, but was: %s", node.getNodeType());
        if (node.has("flow_output")) {
            JsonNode flowOutput = node.get("flow_output");
            requireOnly(node, Set.of("flow_output"), context);
            if (!flowOutput.isObject())
                context.reportInputMismatch(UtxoRef.class, "flow_output must be an object with step and output");
            requireOnly(flowOutput, Set.of("step", "output"), context);
            requireText(flowOutput, "step", context);
            requireText(flowOutput, "output", context);
            return;
        }
        requireOnly(node, Set.of("tx_hash", "output_index"), context);
        requireText(node, "tx_hash", context);
        JsonNode outputIndex = node.get("output_index");
        if (outputIndex == null || !(outputIndex.isIntegralNumber() || outputIndex.isTextual()))
            context.reportInputMismatch(UtxoRef.class, "A UTxO reference requires an integer output_index");
    }

    private static void requireOnly(JsonNode node, Set<String> allowed, DeserializationContext context) throws IOException {
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            String name = it.next();
            if (!allowed.contains(name))
                context.reportInputMismatch(UtxoRef.class, "Unknown UTxO reference field '%s'; expected %s", name, allowed);
        }
    }

    private static void requireText(JsonNode node, String name, DeserializationContext context) throws IOException {
        JsonNode value = node.get(name);
        if (value == null || !value.isTextual() || value.asText().isEmpty())
            context.reportInputMismatch(UtxoRef.class, "A UTxO reference requires a non-empty %s", name);
    }
}
