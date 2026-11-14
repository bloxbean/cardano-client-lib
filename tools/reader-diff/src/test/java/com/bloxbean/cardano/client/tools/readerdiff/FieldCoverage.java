package com.bloxbean.cardano.client.tools.readerdiff;

import co.nstant.in.cbor.model.DataItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Walks a model by reflection next to its canonical JSON, and collects every field or value the JSON does not show. This
 * pins the one thing that would make the tool worthless: a field the comparison never sees.
 */
final class FieldCoverage {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final CanonicalJson canonical = new CanonicalJson();
    final Set<String> fieldsSeen = new TreeSet<>();
    final List<String> problems = new ArrayList<>();

    void check(Object model) throws Exception {
        walk(model, JSON.readTree(canonical.write(model)), "");
    }

    private void walk(Object value, JsonNode json, String path) throws Exception {
        if (value == null) {
            expect(json.isNull(), path, "null");
        } else if (value instanceof byte[] bytes) {
            expect(json.asText().equals(HexFormat.of().formatHex(bytes)), path, "the hex of its bytes");
        } else if (value instanceof DataItem item) {
            expect(json.asText().equals(CanonicalJson.encoded(item)), path, "the hex of its encoding");
        } else if (value instanceof BigDecimal || value instanceof String) {
            expect(json.isTextual() && json.asText().equals(value.toString()), path, value.toString());
        } else if (value instanceof Number || value instanceof Boolean) {
            expect(json.asText().equals(value.toString()), path, value.toString());
        } else if (value instanceof Enum<?> e) {
            expect(json.asText().equals(e.name()), path, e.name());
        } else if (value instanceof Map<?, ?> map) {
            expect(json.isObject() && json.size() == map.size(), path, map.size() + " entries");
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = canonical.keyName(entry.getKey());
                expect(json.has(key), path, "key " + key);
                walk(entry.getValue(), json.path(key), path + "." + key);
            }
        } else if (value instanceof Set<?> set) {
            expect(json.isArray() && json.size() == set.size(), path, set.size() + " elements");
            for (Object element : set) {
                JsonNode tree = JSON.readTree(canonical.write(element));
                boolean found = false;
                for (JsonNode item : json)
                    found |= item.equals(tree);
                expect(found, path, "element " + tree);
                walk(element, tree, path + "[]");
            }
        } else if (value instanceof Collection<?> list) {
            expect(json.isArray() && json.size() == list.size(), path, list.size() + " elements");
            int i = 0;
            for (Object element : list) {
                walk(element, json.path(i), path + "[" + i + "]");
                i++;
            }
        } else if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            expect(json.isArray() && json.size() == length, path, length + " elements");
            for (int i = 0; i < length; i++)
                walk(Array.get(value, i), json.path(i), path + "[" + i + "]");
        } else if (value instanceof InetAddress address) { // a relay's IP address
            expect(json.asText().equals(address.getHostAddress()), path, address.getHostAddress());
        } else if (value.getClass().getName().startsWith("java.")) {
            problems.add(path + ": a JDK type with no rule here: " + value.getClass().getName());
        } else {
            bean(value, json, path);
        }
    }

    private void bean(Object bean, JsonNode json, String path) throws Exception {
        expect(json.path("@class").asText().equals(bean.getClass().getName()), path, "its class");
        int fields = 0;
        for (Class<?> c = bean.getClass(); c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic())
                    continue;
                fields++;
                String name = c.getName() + "." + field.getName();
                fieldsSeen.add(name);
                if (Modifier.isTransient(field.getModifiers()))
                    problems.add(path + ": transient field " + name);
                expect(json.has(field.getName()), path, "field " + name);
                field.setAccessible(true);
                walk(field.get(bean), json.path(field.getName()), path + "." + field.getName());
            }
        }
        expect(json.size() == fields + 1, path, fields + " fields and @class, not " + json.size());
    }

    private void expect(boolean ok, String path, String what) {
        if (!ok && problems.size() < 100)
            problems.add(path + ": the JSON does not show " + what);
    }
}
