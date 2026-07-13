package com.bloxbean.cardano.client.quicktx.intent;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

/**
 * Selects the appropriate {@link UtxoRef} representation from its serialized shape.
 * A {@code flow_output} object becomes a {@link FlowOutputRef}; otherwise the input
 * is decoded as a concrete transaction-hash/output-index reference. Structural and
 * semantic validation remains the responsibility of the owning intent or TxFlow.
 */
final class UtxoRefDeserializer extends StdDeserializer<UtxoRef> {
    UtxoRefDeserializer() {
        super(UtxoRef.class);
    }

    @Override
    public UtxoRef deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
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
}
