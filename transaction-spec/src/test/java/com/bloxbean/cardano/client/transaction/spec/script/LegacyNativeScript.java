package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.NegativeInteger;
import co.nstant.in.cbor.model.Number;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.util.HexUtil;

import java.math.BigInteger;
import java.util.List;
import java.util.StringJoiner;

/**
 * The recursive native script conversion as it was before the iterative codec: the oracle for the differential tests
 * and the baseline for the benchmark. It also renders scripts as the Lombok-generated toString did.
 */
final class LegacyNativeScript {
    private LegacyNativeScript() {
    }

    static NativeScript deserialize(Array array) {
        List<DataItem> items = array.getDataItems();
        int type = ((UnsignedInteger) items.get(0)).getValue().intValue();
        switch (type) {
            case 0: {
                ScriptPubkey script = new ScriptPubkey();
                script.setKeyHash(HexUtil.encodeHexString(((ByteString) items.get(1)).getBytes()));
                return script;
            }
            case 1: {
                ScriptAll script = new ScriptAll();
                children((Array) items.get(1)).forEach(script::addScript);
                return script;
            }
            case 2: {
                ScriptAny script = new ScriptAny();
                children((Array) items.get(1)).forEach(script::addScript);
                return script;
            }
            case 3: {
                ScriptAtLeast script = new ScriptAtLeast(((Number) items.get(1)).getValue());
                children((Array) items.get(2)).forEach(script::addScript);
                return script;
            }
            case 4:
                return new RequireTimeAfter(((UnsignedInteger) items.get(1)).getValue().longValue());
            case 5:
                return new RequireTimeBefore(((UnsignedInteger) items.get(1)).getValue().longValue());
            default:
                return null;
        }
    }

    private static List<NativeScript> children(Array array) {
        List<NativeScript> scripts = new java.util.ArrayList<>();
        for (DataItem item : array.getDataItems()) {
            if (item == SimpleValue.BREAK)
                continue;
            NativeScript script = deserialize((Array) item);
            if (script != null)
                scripts.add(script);
        }
        return scripts;
    }

    static DataItem serialize(NativeScript script) throws CborSerializationException {
        List<NativeScript> children = NativeScriptCodec.scripts(script);
        if (children == null)
            return script.serializeAsDataItem();
        Array array = new Array();
        if (script instanceof ScriptAtLeast) {
            BigInteger required = ((ScriptAtLeast) script).getRequired();
            if (required == null)
                required = BigInteger.ZERO;
            array.add(new UnsignedInteger(3));
            array.add(required.signum() >= 0 ? new UnsignedInteger(required) : new NegativeInteger(required));
        } else {
            array.add(new UnsignedInteger(script instanceof ScriptAll ? 1 : 2));
        }
        Array list = new Array();
        for (NativeScript child : children)
            list.add(serialize(child));
        array.add(list);
        return array;
    }

    // What Lombok's @Data toString printed.
    static String render(NativeScript script) {
        if (script instanceof ScriptAll)
            return "ScriptAll(type=all, scripts=" + render(((ScriptAll) script).getScripts()) + ")";
        if (script instanceof ScriptAny)
            return "ScriptAny(type=any, scripts=" + render(((ScriptAny) script).getScripts()) + ")";
        if (script instanceof ScriptAtLeast)
            return "ScriptAtLeast(type=atLeast, required=" + ((ScriptAtLeast) script).getRequired() + ", scripts="
                    + render(((ScriptAtLeast) script).getScripts()) + ")";
        return String.valueOf(script);
    }

    private static String render(List<NativeScript> scripts) {
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        scripts.forEach(script -> joiner.add(render(script)));
        return joiner.toString();
    }

    // The structure the generated equals compared, recursively.
    static boolean sameModel(NativeScript a, NativeScript b) {
        if (a == null || b == null || a.getClass() != b.getClass())
            return a == b;
        List<NativeScript> left = NativeScriptCodec.scripts(a);
        if (left == null)
            return a.equals(b);
        List<NativeScript> right = NativeScriptCodec.scripts(b);
        if (a instanceof ScriptAtLeast && !java.util.Objects.equals(((ScriptAtLeast) a).getRequired(), ((ScriptAtLeast) b).getRequired()))
            return false;
        if (left.size() != right.size())
            return false;
        for (int i = 0; i < left.size(); i++)
            if (!sameModel(left.get(i), right.get(i)))
                return false;
        return true;
    }
}
