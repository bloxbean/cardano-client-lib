package com.bloxbean.cardano.client.txflow.compile;

import com.bloxbean.cardano.client.quicktx.extension.ExtensionMetadata;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.serialization.YamlSerializer;
import com.bloxbean.cardano.client.txflow.compile.QuickTxTemplateMaterializer.MaterializationException;
import com.bloxbean.cardano.client.txflow.internal.FlowExtensions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Materializes a bound portable transaction that uses QuickTx extension intents (ADR 0008 D6, D7).
 *
 * <p>The core part is still checked by the strict core materializer, with the extension intents
 * left out. The whole transaction is then decoded literally through the registered extensions,
 * with the flow-level bindings it uses, so a bound value is never expanded again. TxFlow cannot
 * know an extension's field schema, so losslessness is proven generically: every extension intent
 * written back through the codec must equal what was authored.</p>
 */
final class ExtensionTemplateMaterializer {
    private static final String INTENT_INVALID = "TXFLOW_EXTENSION_INTENT_INVALID";
    private static final String INTENT_LOSSY = "TXFLOW_EXTENSION_INTENT_LOSSY";
    private static final ObjectMapper JSON = new ObjectMapper();
    /** Equal values, with numbers compared by value so 5, 5L and 5.0 match. */
    private static final Comparator<JsonNode> SAME_VALUE = (left, right) ->
            left.equals(right) || (left.isNumber() && right.isNumber()
                    && left.decimalValue().compareTo(right.decimalValue()) == 0) ? 0 : 1;

    private final QuickTxTemplateMaterializer core;

    ExtensionTemplateMaterializer(QuickTxTemplateMaterializer core) {
        this.core = core;
    }

    /** @return whether the bound transaction holds any {@code <namespace>:<operation>} intent */
    static boolean usesExtensions(JsonNode transaction) {
        return !namespaces(transaction).isEmpty();
    }

    TxPlan materialize(JsonNode transaction, Map<String, ExtensionMetadata> bindings,
                       FlowExtensions extensions, String path) throws MaterializationException {
        ObjectNode coreOnly = transaction.deepCopy();
        JsonNode coreIntents = coreOnly.path("tx").path("intents");
        if (coreIntents instanceof ArrayNode) {
            for (int i = coreIntents.size() - 1; i >= 0; i--) {
                if (isNamespaced(coreIntents.get(i))) ((ArrayNode) coreIntents).remove(i);
            }
        }
        core.materialize(coreOnly, path);

        ObjectNode document = JSON.createObjectNode();
        document.put("version", "1.0");
        ObjectNode declared = document.putObject("extensions");
        for (String namespace : namespaces(transaction)) {
            ExtensionMetadata metadata = bindings.get(namespace);
            if (metadata != null) declared.set(namespace, JSON.valueToTree(metadata));
        }
        if (transaction.hasNonNull("context")) document.set("context", transaction.get("context"));
        document.putArray("transaction").addObject().set("tx", transaction.get("tx"));

        TxPlan plan;
        JsonNode written;
        try {
            plan = extensions.readLiteral(JSON.writeValueAsString(document));
            written = YamlSerializer.getYamlMapper().readTree(extensions.write(plan))
                    .path("transaction").path(0).path("tx").path("intents");
        } catch (Exception failure) {
            throw new MaterializationException(INTENT_INVALID, QuickTxTemplateMaterializer.rootMessage(
                    failure, "Extension intent could not be decoded"), path + ".tx.intents");
        }

        // Core input and script intents move to tx.inputs and tx.scripts on write, so the n-th
        // authored extension intent is compared with the n-th written one, not by array index.
        List<JsonNode> writtenExtensionIntents = new ArrayList<>();
        written.forEach(intent -> {
            if (isNamespaced(intent)) writtenExtensionIntents.add(intent);
        });
        JsonNode authored = transaction.path("tx").path("intents");
        int n = 0;
        for (int i = 0; i < authored.size(); i++) {
            if (!isNamespaced(authored.get(i))) continue;
            JsonNode writtenIntent = n < writtenExtensionIntents.size()
                    ? writtenExtensionIntents.get(n) : MissingNode.getInstance();
            n++;
            if (!authored.get(i).equals(SAME_VALUE, writtenIntent)) {
                throw new MaterializationException(INTENT_LOSSY, "Extension intent "
                        + authored.get(i).path("type").asText() + " does not round-trip: authored "
                        + authored.get(i) + " but the extension writes " + writtenIntent,
                        path + ".tx.intents[" + i + "]");
            }
        }
        if (n != writtenExtensionIntents.size()) {
            throw new MaterializationException(INTENT_LOSSY, "The extensions write "
                    + writtenExtensionIntents.size() + " extension intents for the " + n + " authored",
                    path + ".tx.intents");
        }
        return plan;
    }

    private static Set<String> namespaces(JsonNode transaction) {
        Set<String> namespaces = new LinkedHashSet<>();
        for (JsonNode intent : transaction.path("tx").path("intents")) {
            if (isNamespaced(intent)) {
                String type = intent.path("type").asText();
                namespaces.add(type.substring(0, type.indexOf(':')));
            }
        }
        return namespaces;
    }

    private static boolean isNamespaced(JsonNode intent) {
        return intent.path("type").asText("").indexOf(':') > 0;
    }
}
