package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.NegativeInteger;
import co.nstant.in.cbor.model.Number;
import co.nstant.in.cbor.model.Special;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.util.HexUtil;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Converts native scripts between {@link DataItem} trees and models, and compares, hashes and prints the models, without
 * recursion: {@code all}, {@code any} and {@code atLeast} are frames on an explicit stack, so scripts of any nesting
 * depth work on any thread.
 * <p>
 * Decoding follows the ledger's Timelock decoder (cardano-ledger, allegra {@code Scripts.hs}): a script is an untagged
 * array {@code [type, fields]} with exactly the fields of its type, definite or indefinite; an unknown type is rejected;
 * a key hash is 28 bytes; {@code m} of {@code atLeast} is a signed 64-bit integer ({@code TimelockMOf !Int}); a slot is
 * an unsigned 64-bit integer. Types 4 and 5 (time locks) exist from Allegra on; Shelley's multisig scripts have types 0-3
 * only.
 */
final class NativeScriptCodec {
    private static final int KEY_HASH_SIZE = 28;
    private static final BigInteger INT64_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger INT64_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private NativeScriptCodec() {
    }

    // ---- DataItem -> NativeScript

    static NativeScript decode(DataItem item) throws CborDeserializationException {
        DecodeFrame top = null;
        NativeScript root = null;
        DataItem current = item;
        while (true) {
            List<DataItem> fields = items(current, "native script");
            NativeScript script;
            DataItem children = null;
            int type = type(fields);
            switch (type) {
                case 0:
                    arity(fields, 2, type);
                    script = new ScriptPubkey(keyHash(fields.get(1)));
                    break;
                case 1:
                    arity(fields, 2, type);
                    script = new ScriptAll();
                    children = fields.get(1);
                    break;
                case 2:
                    arity(fields, 2, type);
                    script = new ScriptAny();
                    children = fields.get(1);
                    break;
                case 3:
                    arity(fields, 3, type);
                    script = new ScriptAtLeast(required(fields.get(1)));
                    children = fields.get(2);
                    break;
                case 4:
                    arity(fields, 2, type);
                    script = new RequireTimeAfter(slot(fields.get(1)));
                    break;
                default:
                    arity(fields, 2, type);
                    script = new RequireTimeBefore(slot(fields.get(1)));
                    break;
            }
            if (top == null)
                root = script;
            else
                top.scripts.add(script);
            if (children != null)
                top = new DecodeFrame(top, scripts(script), items(children, "native script list"));
            while (top != null && !top.hasNext())
                top = top.parent;
            if (top == null)
                return root;
            current = top.next();
        }
    }

    /**
     * Decodes a script that must be of the given class.
     */
    static <T extends NativeScript> T decode(DataItem item, Class<T> type) throws CborDeserializationException {
        NativeScript script = decode(item);
        if (!type.isInstance(script))
            throw new CborDeserializationException("NativeScript deserialization failed. Expected " + type.getSimpleName()
                    + ", found " + script.getClass().getSimpleName());
        return type.cast(script);
    }

    // The items of an untagged array, without the BREAK that ends an indefinite one.
    private static List<DataItem> items(DataItem item, String what) throws CborDeserializationException {
        if (!(item instanceof Array) || item.hasTag())
            throw new CborDeserializationException("NativeScript deserialization failed. Expected an untagged array for "
                    + what + ", found " + item);
        List<DataItem> items = ((Array) item).getDataItems();
        int size = items.size();
        if (size > 0 && items.get(size - 1) == Special.BREAK)
            return items.subList(0, size - 1);
        return items;
    }

    private static int type(List<DataItem> fields) throws CborDeserializationException {
        if (fields.isEmpty())
            throw new CborDeserializationException("NativeScript deserialization failed. Invalid no of DataItem");
        DataItem type = fields.get(0);
        if (type instanceof UnsignedInteger && !type.hasTag()) {
            BigInteger value = ((UnsignedInteger) type).getValue();
            if (value.compareTo(BigInteger.valueOf(5)) <= 0)
                return value.intValue();
        }
        throw new CborDeserializationException("NativeScript deserialization failed. Unknown native script type " + type);
    }

    private static void arity(List<DataItem> fields, int expected, int type) throws CborDeserializationException {
        if (fields.size() != expected)
            throw new CborDeserializationException("NativeScript deserialization failed. Native script type " + type
                    + " has " + expected + " items, found " + fields.size());
    }

    private static String keyHash(DataItem item) throws CborDeserializationException {
        if (!(item instanceof ByteString) || item.hasTag() || ((ByteString) item).getBytes().length != KEY_HASH_SIZE)
            throw new CborDeserializationException("NativeScript deserialization failed. Expected a " + KEY_HASH_SIZE
                    + "-byte key hash, found " + item);
        return HexUtil.encodeHexString(((ByteString) item).getBytes());
    }

    private static BigInteger required(DataItem item) throws CborDeserializationException {
        if (item instanceof Number && !item.hasTag()) {
            BigInteger value = ((Number) item).getValue();
            if (value.compareTo(INT64_MIN) >= 0 && value.compareTo(INT64_MAX) <= 0)
                return value;
        }
        throw new CborDeserializationException("NativeScript deserialization failed. Expected a 64-bit signed integer for m, found " + item);
    }

    private static BigInteger slot(DataItem item) throws CborDeserializationException {
        if (!(item instanceof UnsignedInteger) || item.hasTag())
            throw new CborDeserializationException("NativeScript deserialization failed. Expected a slot, found " + item);
        return ((UnsignedInteger) item).getValue();
    }

    private static final class DecodeFrame {
        final DecodeFrame parent;
        final List<NativeScript> scripts;
        private final List<DataItem> items;
        private int index;

        DecodeFrame(DecodeFrame parent, List<NativeScript> scripts, List<DataItem> items) {
            this.parent = parent;
            this.scripts = scripts;
            this.items = items;
        }

        boolean hasNext() {
            return index < items.size();
        }

        DataItem next() {
            return items.get(index++);
        }
    }

    // ---- NativeScript -> DataItem

    static DataItem encode(NativeScript script) throws CborSerializationException {
        EncodeFrame top = null;
        DataItem root = null;
        NativeScript current = script;
        while (true) {
            List<NativeScript> children = scripts(current);
            DataItem item;
            Array list = null;
            if (children == null) {
                if (current == null)
                    throw new CborSerializationException("Cbor serialization failed. NULL native script");
                item = current.serializeAsDataItem();
            } else {
                Array array = new Array();
                if (current instanceof ScriptAtLeast) {
                    ScriptAtLeast atLeast = (ScriptAtLeast) current;
                    if (atLeast.getRequired() == null)
                        atLeast.setRequired(BigInteger.ZERO);
                    BigInteger required = atLeast.getRequired();
                    array.add(new UnsignedInteger(3));
                    array.add(required.signum() >= 0 ? new UnsignedInteger(required) : new NegativeInteger(required));
                } else {
                    array.add(new UnsignedInteger(current instanceof ScriptAll ? 1 : 2));
                }
                list = new Array();
                array.add(list);
                item = array;
            }
            if (top == null)
                root = item;
            else
                top.list.add(item);
            if (list != null)
                top = new EncodeFrame(top, children, list);
            while (top != null && !top.hasNext())
                top = top.parent;
            if (top == null)
                return root;
            current = top.next();
        }
    }

    private static final class EncodeFrame {
        final EncodeFrame parent;
        final Array list;
        private final List<NativeScript> scripts;
        private int index;

        EncodeFrame(EncodeFrame parent, List<NativeScript> scripts, Array list) {
            this.parent = parent;
            this.scripts = scripts;
            this.list = list;
        }

        boolean hasNext() {
            return index < scripts.size();
        }

        NativeScript next() {
            return scripts.get(index++);
        }
    }

    // ---- equals, hashCode and toString

    // The sub-scripts of all, any and atLeast; null for any other script.
    static List<NativeScript> scripts(NativeScript script) {
        if (script instanceof ScriptAll)
            return ((ScriptAll) script).getScripts();
        if (script instanceof ScriptAny)
            return ((ScriptAny) script).getScripts();
        if (script instanceof ScriptAtLeast)
            return ((ScriptAtLeast) script).getScripts();
        return null;
    }

    /**
     * The equality the generated equals had: the same class, the same {@code required} for atLeast and equal sub-scripts
     * in order.
     */
    static boolean equal(NativeScript script, Object other) {
        Deque<Object> pairs = new ArrayDeque<>();
        Object left = script;
        Object right = other;
        while (true) {
            if (left != right) {
                if (left == null || right == null || left.getClass() != right.getClass())
                    return false;
                List<NativeScript> leftScripts = scripts((NativeScript) left);
                if (leftScripts == null) {
                    if (!left.equals(right))
                        return false;
                } else {
                    List<NativeScript> rightScripts = scripts((NativeScript) right);
                    if (left instanceof ScriptAtLeast
                            && !Objects.equals(((ScriptAtLeast) left).getRequired(), ((ScriptAtLeast) right).getRequired()))
                        return false;
                    if (leftScripts.size() != rightScripts.size())
                        return false;
                    for (int i = leftScripts.size() - 1; i >= 0; i--) {
                        pairs.push(new Object[]{leftScripts.get(i), rightScripts.get(i)});
                    }
                }
            }
            if (pairs.isEmpty())
                return true;
            Object[] pair = (Object[]) pairs.pop();
            left = pair[0];
            right = pair[1];
        }
    }

    static int hash(NativeScript root) {
        HashFrame top = null;
        NativeScript current = root;
        while (true) {
            List<NativeScript> children = scripts(current);
            int hash;
            if (children == null) {
                hash = Objects.hashCode(current);
            } else {
                int seed = 31 * current.getClass().getName().hashCode() + (current instanceof ScriptAtLeast
                        ? Objects.hashCode(((ScriptAtLeast) current).getRequired()) : 0);
                if (!children.isEmpty()) {
                    top = new HashFrame(top, children, seed);
                    current = top.next();
                    continue;
                }
                hash = seed;
            }
            while (true) {
                if (top == null)
                    return hash;
                top.hash = 31 * top.hash + hash;
                if (top.hasNext()) {
                    current = top.next();
                    break;
                }
                hash = top.hash;
                top = top.parent;
            }
        }
    }

    private static final class HashFrame {
        final HashFrame parent;
        private final List<NativeScript> scripts;
        private int index;
        int hash;

        HashFrame(HashFrame parent, List<NativeScript> scripts, int seed) {
            this.parent = parent;
            this.scripts = scripts;
            this.hash = seed;
        }

        boolean hasNext() {
            return index < scripts.size();
        }

        NativeScript next() {
            return scripts.get(index++);
        }
    }

    /**
     * The text the generated toString had, such as {@code ScriptAll(type=all, scripts=[ScriptPubkey(type=sig,
     * keyHash=...)])}.
     */
    static String toString(NativeScript root) {
        StringBuilder out = new StringBuilder();
        Deque<Object> work = new ArrayDeque<>();
        work.push(root);
        while (!work.isEmpty()) {
            Object next = work.pop();
            if (next instanceof String) {
                out.append((String) next);
                continue;
            }
            NativeScript script = (NativeScript) next;
            List<NativeScript> children = scripts(script);
            if (children == null) {
                out.append(script);
                continue;
            }
            if (script instanceof ScriptAtLeast)
                out.append("ScriptAtLeast(type=atLeast, required=").append(((ScriptAtLeast) script).getRequired()).append(", scripts=[");
            else if (script instanceof ScriptAll)
                out.append("ScriptAll(type=all, scripts=[");
            else
                out.append("ScriptAny(type=any, scripts=[");
            work.push("])");
            for (int i = children.size() - 1; i >= 0; i--) {
                NativeScript child = children.get(i);
                work.push(child != null ? child : "null");
                if (i > 0)
                    work.push(", ");
            }
        }
        return out.toString();
    }
}
