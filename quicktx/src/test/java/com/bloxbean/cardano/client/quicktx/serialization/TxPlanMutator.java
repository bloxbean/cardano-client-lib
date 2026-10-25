package com.bloxbean.cardano.client.quicktx.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Structural mutations of TxPlan documents for the reader and schema property tests. Choices come from a {@link Choices}
 * source, so the same mutations run from a seeded {@link Random} in every build and from Jazzer when fuzzing.
 */
final class TxPlanMutator {

    /** A source of bounded choices. */
    interface Choices {
        /** @return a value in {@code [0, bound)} */
        int pick(int bound);

        /** @return a short arbitrary string */
        String text();

        static Choices of(Random random) {
            return new Choices() {
                @Override
                public int pick(int bound) {
                    return random.nextInt(bound);
                }

                @Override
                public String text() {
                    int length = random.nextInt(8);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < length; i++) sb.append((char) (' ' + random.nextInt(95)));
                    return sb.toString();
                }
            };
        }
    }

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final List<String> ARRAYS = List.of("inputs", "intents", "scripts");

    private final List<String> intentTypes;
    private final List<String> fieldNames;

    TxPlanMutator(List<String> intentTypes, List<String> fieldNames) {
        this.intentTypes = intentTypes;
        this.fieldNames = fieldNames;
    }

    /** Applies one to four mutations to a copy of {@code document}. */
    JsonNode mutate(JsonNode document, Choices choices) {
        JsonNode copy = document.deepCopy();
        int count = 1 + choices.pick(4);
        for (int i = 0; i < count; i++) copy = mutateOnce(copy, choices);
        return copy;
    }

    private JsonNode mutateOnce(JsonNode root, Choices choices) {
        List<ObjectNode> objects = new ArrayList<>();
        collectObjects(root, objects);
        if (objects.isEmpty()) return root;
        ObjectNode target = objects.get(choices.pick(objects.size()));
        List<String> names = new ArrayList<>();
        target.fieldNames().forEachRemaining(names::add);
        switch (choices.pick(9)) {
            case 0 -> { // drop a field
                if (!names.isEmpty()) target.remove(names.get(choices.pick(names.size())));
            }
            case 1 -> { // rename a field to a known or arbitrary name
                if (!names.isEmpty()) {
                    String from = names.get(choices.pick(names.size()));
                    String to = choices.pick(3) == 0 ? choices.text() : fieldNames.get(choices.pick(fieldNames.size()));
                    JsonNode value = target.remove(from);
                    target.set(to, value);
                }
            }
            case 2 -> { // retype a value
                if (!names.isEmpty()) target.set(names.get(choices.pick(names.size())), scalar(choices));
            }
            case 3 -> { // add a known or arbitrary field
                String name = choices.pick(3) == 0 ? choices.text() : fieldNames.get(choices.pick(fieldNames.size()));
                target.set(name, scalar(choices));
            }
            case 4 -> { // change an intent's type
                if (target.has("type")) target.put("type", intentTypes.get(choices.pick(intentTypes.size())));
            }
            case 5 -> moveIntent(root, choices);
            case 6 -> { // wrap a value in an array or object
                if (!names.isEmpty()) {
                    String name = names.get(choices.pick(names.size()));
                    JsonNode value = target.get(name);
                    if (choices.pick(2) == 0) target.set(name, NODES.arrayNode().add(value));
                    else target.set(name, NODES.objectNode().set(fieldNames.get(choices.pick(fieldNames.size())), value));
                }
            }
            case 7 -> { // duplicate an array element, or add a null one
                List<ArrayNode> arrays = new ArrayList<>();
                collectArrays(root, arrays);
                if (!arrays.isEmpty()) {
                    ArrayNode array = arrays.get(choices.pick(arrays.size()));
                    if (choices.pick(3) == 0 || array.isEmpty()) array.addNull();
                    else array.add(array.get(choices.pick(array.size())).deepCopy());
                }
            }
            default -> { // replace a value with a placeholder
                if (!names.isEmpty()) target.put(names.get(choices.pick(names.size())), "${v" + choices.pick(3) + "}");
            }
        }
        return root;
    }

    private void moveIntent(JsonNode root, Choices choices) {
        List<ObjectNode> txs = new ArrayList<>();
        for (JsonNode entry : root.path("transaction")) {
            if (entry.path("tx").isObject()) txs.add((ObjectNode) entry.get("tx"));
        }
        if (txs.isEmpty()) return;
        ObjectNode tx = txs.get(choices.pick(txs.size()));
        String from = ARRAYS.get(choices.pick(ARRAYS.size()));
        String to = ARRAYS.get(choices.pick(ARRAYS.size()));
        if (!(tx.get(from) instanceof ArrayNode source) || source.isEmpty()) return;
        JsonNode intent = source.remove(choices.pick(source.size()));
        JsonNode destination = tx.get(to);
        if (!(destination instanceof ArrayNode)) destination = tx.putArray(to);
        ((ArrayNode) destination).add(intent);
    }

    private JsonNode scalar(Choices choices) {
        return switch (choices.pick(12)) {
            case 0 -> NODES.numberNode(choices.pick(1000));
            case 1 -> NODES.numberNode(-1 - choices.pick(1000));
            case 2 -> NODES.numberNode(choices.pick(1000) + 0.5);
            case 3 -> NODES.textNode(Integer.toString(choices.pick(1000)));
            case 4 -> NODES.textNode(choices.text());
            case 5 -> NODES.booleanNode(choices.pick(2) == 0);
            case 6 -> NODES.nullNode();
            case 7 -> NODES.textNode("${x}");
            case 8 -> NODES.numberNode(new BigInteger("123456789012345678901234567890"));
            case 9 -> NODES.textNode(intentTypes.get(choices.pick(intentTypes.size())));
            case 10 -> NODES.arrayNode();
            default -> NODES.objectNode();
        };
    }

    private static void collectObjects(JsonNode node, List<ObjectNode> out) {
        if (node instanceof ObjectNode object) {
            out.add(object);
            for (Iterator<Map.Entry<String, JsonNode>> it = object.fields(); it.hasNext(); ) collectObjects(it.next().getValue(), out);
        } else if (node instanceof ArrayNode array) {
            for (JsonNode child : array) collectObjects(child, out);
        }
    }

    private static void collectArrays(JsonNode node, List<ArrayNode> out) {
        if (node instanceof ObjectNode object) {
            for (Iterator<Map.Entry<String, JsonNode>> it = object.fields(); it.hasNext(); ) collectArrays(it.next().getValue(), out);
        } else if (node instanceof ArrayNode array) {
            out.add(array);
            for (JsonNode child : array) collectArrays(child, out);
        }
    }
}
