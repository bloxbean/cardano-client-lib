package com.bloxbean.cardano.client.quicktx.filter.yaml;

import com.bloxbean.cardano.client.quicktx.filter.ImmutableUtxoFilterSpec;
import com.bloxbean.cardano.client.quicktx.filter.Selection;
import com.bloxbean.cardano.client.quicktx.filter.UtxoFilterSpec;
import com.bloxbean.cardano.client.quicktx.filter.ast.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Parser for the simplified YAML mapping under `utxo_filter` into a Filter AST.
 */
public final class UtxoFilterYaml {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private UtxoFilterYaml() {}

    public static ImmutableUtxoFilterSpec parse(String utxoFilterYaml) throws IOException {
        JsonNode root = YAML.readTree(utxoFilterYaml);
        return parseNode(root);
    }

    public static ImmutableUtxoFilterSpec parseNode(JsonNode root) {
        FilterNode filterNode = parseFilterNode(root);
        Selection selection = parseSelection(root.get("selection"));
        return ImmutableUtxoFilterSpec.builder(filterNode)
                .selection(selection)
                .build();
    }

    private static FilterNode parseFilterNode(JsonNode node) {
        if (node == null || node.isNull())
            throw new IllegalArgumentException("filter mapping cannot be null");

        // Logical wrappers first
        if (node.has("and")) {
            List<FilterNode> terms = new ArrayList<>();
            for (JsonNode term : node.get("and")) {
                terms.add(parseFilterNode(term));
            }
            return new And(terms);
        }
        if (node.has("or")) {
            List<FilterNode> terms = new ArrayList<>();
            for (JsonNode term : node.get("or")) {
                terms.add(parseFilterNode(term));
            }
            return new Or(terms);
        }
        if (node.has("not")) {
            return new Not(parseFilterNode(node.get("not")));
        }

        // Implicit AND across mapping keys
        if (node.isObject()) {
            List<FilterNode> terms = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                String key = e.getKey();
                if ("selection".equals(key)) continue; // handled separately
                terms.add(parseLeaf(key, e.getValue()));
            }
            if (terms.isEmpty()) {
                // Allow selection-only objects: empty filter means match all, then apply selection
                if (node.has("selection")) return new And(java.util.Collections.emptyList());
                throw new IllegalArgumentException("Empty filter object is not allowed");
            }
            if (terms.size() == 1) return terms.get(0);
            return new And(terms);
        }

        throw new IllegalArgumentException("Unexpected filter node: " + node);
    }

    private static FilterNode parseLeaf(String key, JsonNode valueNode) {
        switch (key) {
            case "address":
                return parseStringComparison(AddressField.INSTANCE, valueNode);
            case "dataHash":
                return parseStringComparison(DataHashField.INSTANCE, valueNode);
            case "inlineDatum":
                return parseStringComparison(InlineDatumField.INSTANCE, valueNode);
            case "referenceScriptHash":
                return parseStringComparison(ReferenceScriptHashField.INSTANCE, valueNode);
            case "txHash":
                return parseStringComparison(TxHashField.INSTANCE, valueNode);
            case "outputIndex":
                return parseNumericComparison(OutputIndexField.INSTANCE, valueNode);
            case "lovelace":
                return parseNumericComparison(new AmountQuantityField("lovelace"), valueNode);
            case "amount":
                return parseAmount(valueNode);
            case "and":
            case "or":
            case "not":
                return parseFilterNode(new ObjectMapper().valueToTree(Map.of(key, valueNode)));
            default:
                throw new IllegalArgumentException("Unknown filter field: " + key);
        }
    }

    private static Comparison parseStringComparison(FieldRef field, JsonNode node) {
        if (node.isTextual() || node.isNull()) {
            return new Comparison(field, CmpOp.EQ, node.isNull() ? Value.nullValue() : Value.ofString(node.asText()));
        }
        if (node.isObject()) {
            if (node.has("eq")) return new Comparison(field, CmpOp.EQ, valStringOrNull(node.get("eq")));
            if (node.has("ne")) return new Comparison(field, CmpOp.NE, valStringOrNull(node.get("ne")));
        }
        throw new IllegalArgumentException("Invalid string comparison for field: " + field + ", node: " + node);
    }

    private static Value valStringOrNull(JsonNode node) {
        return node.isNull() ? Value.nullValue() : Value.ofString(node.asText());
    }

    private static Comparison parseNumericComparison(FieldRef field, JsonNode node) {
        if (node.isNumber()) {
            return new Comparison(field, CmpOp.EQ, Value.ofInteger(node.bigIntegerValue()));
        }
        if (node.isObject()) {
            if (node.has("eq")) return new Comparison(field, CmpOp.EQ, asBigInt(node.get("eq")));
            if (node.has("ne")) return new Comparison(field, CmpOp.NE, asBigInt(node.get("ne")));
            if (node.has("gt")) return new Comparison(field, CmpOp.GT, asBigInt(node.get("gt")));
            if (node.has("gte")) return new Comparison(field, CmpOp.GTE, asBigInt(node.get("gte")));
            if (node.has("lt")) return new Comparison(field, CmpOp.LT, asBigInt(node.get("lt")));
            if (node.has("lte")) return new Comparison(field, CmpOp.LTE, asBigInt(node.get("lte")));
        }
        throw new IllegalArgumentException("Invalid numeric comparison for field: " + field + ", node: " + node);
    }

    private static Comparison parseAmount(JsonNode node) {
        if (!node.isObject())
            throw new IllegalArgumentException("amount must be an object");
        String unit = null;
        if (node.has("unit")) {
            unit = node.get("unit").asText();
        } else if (node.has("policyId") && node.has("assetName")) {
            unit = node.get("policyId").asText() + node.get("assetName").asText();
        }
        if (unit == null || unit.isEmpty())
            throw new IllegalArgumentException("amount requires unit or (policyId + assetName)");

        // Find exactly one numeric op (short form: { lt: 50 })
        Comparison cmp = null;
        for (String op : new String[]{"eq","ne","gt","gte","lt","lte"}) {
            if (node.has(op)) {
                if (cmp != null) throw new IllegalArgumentException("amount must specify exactly one numeric op");
                CmpOp cmpOp = CmpOp.valueOf(op.toUpperCase());
                cmp = new Comparison(new AmountQuantityField(unit), cmpOp, asBigInt(node.get(op)));
            }
        }
        if (cmp == null) {
            // try nested quantity: { quantity: { op: val } }
            if (node.has("quantity")) {
                JsonNode q = node.get("quantity");
                return parseNumericComparison(new AmountQuantityField(unit), q);
            }
            throw new IllegalArgumentException("amount must include a numeric op or quantity block");
        }
        return cmp;
    }

    private static String opToAlias(String op) {
        // Not used directly now; placeholder for potential remapping
        return op;
    }

    private static Value asBigInt(JsonNode node) {
        if (!node.canConvertToLong() && !node.isBigInteger() && !node.isNumber())
            throw new IllegalArgumentException("Expected numeric value, got: " + node);
        BigInteger bi = node.isBigInteger() ? node.bigIntegerValue() : new BigInteger(node.asText());
        return Value.ofInteger(bi);
    }

    private static Selection parseSelection(JsonNode selectionNode) {
        if (selectionNode == null || selectionNode.isNull()) return null;
        if (!selectionNode.isObject())
            throw new IllegalArgumentException("selection must be an object");
        JsonNode orderNode = selectionNode.get("order");
        JsonNode limitNode = selectionNode.get("limit");

        List<com.bloxbean.cardano.client.quicktx.filter.Order> orders = new ArrayList<>();
        if (orderNode != null && !orderNode.isNull()) {
            if (orderNode.isArray()) {
                for (JsonNode el : orderNode) {
                    if (!el.isTextual())
                        throw new IllegalArgumentException("selection.order entries must be strings");
                    orders.add(parseOrderString(el.asText()));
                }
            } else {
                throw new IllegalArgumentException("selection.order must be list of strings");
            }
        }

        Integer limit = null;
        if (limitNode != null && !limitNode.isNull()) {
            if (limitNode.isInt() || limitNode.isLong() || limitNode.isIntegralNumber()) {
                int n = limitNode.intValue();
                if (n < 0) throw new IllegalArgumentException("selection.limit must be >= 0 or 'all'");
                limit = n;
            } else if (limitNode.isTextual()) {
                String s = limitNode.asText();
                if (!"all".equalsIgnoreCase(s))
                    throw new IllegalArgumentException("selection.limit must be integer or 'all'");
                // leave as null to mean ALL
            } else {
                throw new IllegalArgumentException("selection.limit must be integer or 'all'");
            }
        }

        return Selection.of(orders, limit);
    }

    private static com.bloxbean.cardano.client.quicktx.filter.Order parseOrderString(String s) {
        String str = s.trim();
        String dirToken = null;
        // split last token if it's asc/desc
        int lastSpace = str.lastIndexOf(' ');
        String head = str;
        if (lastSpace > 0) {
            String tail = str.substring(lastSpace + 1).trim();
            if ("asc".equalsIgnoreCase(tail) || "desc".equalsIgnoreCase(tail)) {
                dirToken = tail.toLowerCase();
                head = str.substring(0, lastSpace).trim();
            }
        }
        com.bloxbean.cardano.client.quicktx.filter.Order.Direction dir =
                (dirToken == null || "asc".equals(dirToken))
                        ? com.bloxbean.cardano.client.quicktx.filter.Order.Direction.ASC
                        : com.bloxbean.cardano.client.quicktx.filter.Order.Direction.DESC;

        String h = head.toLowerCase();
        if (h.equals("lovelace")) {
            return com.bloxbean.cardano.client.quicktx.filter.Order.lovelace(dir);
        }
        if (h.startsWith("amount.unit(")) {
            if (!h.endsWith(")"))
                throw new IllegalArgumentException("Invalid amount.unit(...) order: " + s);
            String unit = head.substring("amount.unit(".length(), head.length() - 1);
            if (unit.isEmpty()) throw new IllegalArgumentException("Empty unit in order: " + s);
            return com.bloxbean.cardano.client.quicktx.filter.Order.amountUnit(unit, dir);
        }
        if (h.equals("address")) return com.bloxbean.cardano.client.quicktx.filter.Order.address(dir);
        if (h.equals("datahash") || h.equals("data_hash")) return com.bloxbean.cardano.client.quicktx.filter.Order.dataHash(dir);
        if (h.equals("inlinedatum") || h.equals("inline_datum")) return com.bloxbean.cardano.client.quicktx.filter.Order.inlineDatum(dir);
        if (h.equals("referencescripthash") || h.equals("reference_script_hash")) return com.bloxbean.cardano.client.quicktx.filter.Order.referenceScriptHash(dir);
        if (h.equals("txhash") || h.equals("tx_hash")) return com.bloxbean.cardano.client.quicktx.filter.Order.txHash(dir);
        if (h.equals("outputindex") || h.equals("output_index")) return com.bloxbean.cardano.client.quicktx.filter.Order.outputIndex(dir);

        throw new IllegalArgumentException("Unknown order field: " + s);
    }

    // Serialization to simplified YAML mapping (utxo_filter)
    public static ObjectNode toNode(UtxoFilterSpec spec) {
        ObjectNode root = YAML.createObjectNode();
        root.setAll(serializeFilter(spec.root()));
        if (spec.selection() != null) {
            ObjectNode sel = YAML.createObjectNode();
            ArrayNode orderArr = YAML.createArrayNode();
            if (spec.selection().getOrder() != null && !spec.selection().getOrder().isEmpty()) {
                for (com.bloxbean.cardano.client.quicktx.filter.Order o : spec.selection().getOrder()) {
                    String s = orderToString(o);
                    orderArr.add(s);
                }
                sel.set("order", orderArr);
            }
            if (spec.selection().getLimit() != null) {
                sel.put("limit", spec.selection().getLimit());
            }
            if (sel.size() > 0) root.set("selection", sel);
        }
        return root;
    }

    private static ObjectNode serializeFilter(FilterNode node) {
        ObjectNode obj = YAML.createObjectNode();
        if (node instanceof And) {
            // Try to flatten simple AND of field comparisons into top-level keys
            List<FilterNode> terms = ((And) node).getTerms();
            boolean canFlatten = true;
            ObjectNode merged = YAML.createObjectNode();
            for (FilterNode t : terms) {
                ObjectNode child = serializeFilter(t);
                if (isSimpleFieldObject(child)) {
                    // merge single entry
                    java.util.Iterator<String> names = child.fieldNames();
                    if (!names.hasNext()) continue;
                    String name = names.next();
                    if (merged.has(name)) {
                        canFlatten = false; // duplicate field; fallback to array form
                        break;
                    }
                    merged.set(name, child.get(name));
                } else {
                    canFlatten = false;
                    break;
                }
            }
            if (canFlatten) return merged;

            ArrayNode arr = YAML.createArrayNode();
            for (FilterNode t : terms) arr.add(serializeFilter(t));
            obj.set("and", arr);
            return obj;
        }
        if (node instanceof Or) {
            ArrayNode arr = YAML.createArrayNode();
            for (FilterNode t : ((Or) node).getTerms()) arr.add(serializeFilter(t));
            obj.set("or", arr);
            return obj;
        }
        if (node instanceof Not) {
            obj.set("not", serializeFilter(((Not) node).getTerm()));
            return obj;
        }
        if (node instanceof Comparison) {
            Comparison c = (Comparison) node;
            FieldRef f = c.getField();
            CmpOp op = c.getOp();
            Value v = c.getValue();
            String opk = op.name().toLowerCase();
            if (f instanceof AddressField) {
                putStringField(obj, "address", op, v);
            } else if (f instanceof DataHashField) {
                putStringField(obj, "dataHash", op, v);
            } else if (f instanceof InlineDatumField) {
                putStringField(obj, "inlineDatum", op, v);
            } else if (f instanceof ReferenceScriptHashField) {
                putStringField(obj, "referenceScriptHash", op, v);
            } else if (f instanceof TxHashField) {
                putStringField(obj, "txHash", op, v);
            } else if (f instanceof OutputIndexField) {
                putNumericField(obj, "outputIndex", op, v);
            } else if (f instanceof AmountQuantityField) {
                String unit = ((AmountQuantityField) f).getUnit();
                if ("lovelace".equals(unit)) {
                    putNumericField(obj, "lovelace", op, v);
                } else {
                    ObjectNode amt = YAML.createObjectNode();
                    amt.put("unit", unit);
                    putNumericOp(amt, opk, v);
                    obj.set("amount", amt);
                }
            }
            return obj;
        }
        throw new IllegalArgumentException("Unknown node: " + node);
    }

    private static boolean isSimpleFieldObject(ObjectNode node) {
        int size = node.size();
        if (size != 1) return false;
        java.util.Iterator<String> it = node.fieldNames();
        if (!it.hasNext()) return false;
        String name = it.next();
        return !("and".equals(name) || "or".equals(name) || "not".equals(name));
    }

    private static void putStringField(ObjectNode obj, String name, CmpOp op, Value v) {
        if (op == CmpOp.EQ) {
            if (v.getKind() == Value.Kind.NULL) obj.putNull(name);
            else obj.put(name, v.asString());
        } else if (op == CmpOp.NE) {
            ObjectNode n = YAML.createObjectNode();
            if (v.getKind() == Value.Kind.NULL) n.putNull("ne");
            else n.put("ne", v.asString());
            obj.set(name, n);
        } else {
            // For string fields, only eq/ne allowed. Fallback to wrapped form for non-standard, though we don't produce them.
            ObjectNode n = YAML.createObjectNode();
            n.put(op.name().toLowerCase(), v.getKind() == Value.Kind.NULL ? null : v.asString());
            obj.set(name, n);
        }
    }

    private static void putNumericField(ObjectNode obj, String name, CmpOp op, Value v) {
        if (op == CmpOp.EQ) {
            obj.put(name, v.asInteger());
        } else {
            ObjectNode n = YAML.createObjectNode();
            putNumericOp(n, op.name().toLowerCase(), v);
            obj.set(name, n);
        }
    }

    private static void putNumericOp(ObjectNode obj, String op, Value v) {
        switch (v.getKind()) {
            case INTEGER:
                obj.put(op, v.asInteger());
                break;
            default:
                throw new IllegalArgumentException("Numeric op requires integer value");
        }
    }

    private static String orderToString(com.bloxbean.cardano.client.quicktx.filter.Order o) {
        String head;
        switch (o.getField()) {
            case LOVELACE: head = "lovelace"; break;
            case AMOUNT_UNIT: head = "amount.unit(" + o.getUnit() + ")"; break;
            case ADDRESS: head = "address"; break;
            case DATA_HASH: head = "dataHash"; break;
            case INLINE_DATUM: head = "inlineDatum"; break;
            case REFERENCE_SCRIPT_HASH: head = "referenceScriptHash"; break;
            case TX_HASH: head = "txHash"; break;
            case OUTPUT_INDEX: head = "outputIndex"; break;
            default: head = o.getField().name();
        }
        return head + (o.getDirection() == com.bloxbean.cardano.client.quicktx.filter.Order.Direction.DESC ? " desc" : " asc");
    }
}
