package com.bloxbean.cardano.client.plutus.annotation.processor.converter;

import com.bloxbean.cardano.client.plutus.annotation.processor.model.Type;
import com.bloxbean.cardano.client.plutus.blueprint.type.Pair;
import com.bloxbean.cardano.client.plutus.blueprint.type.Quartet;
import com.bloxbean.cardano.client.plutus.blueprint.type.Quintet;
import com.bloxbean.cardano.client.plutus.blueprint.type.Triple;

/**
 * Metadata enum that captures the common properties of the four tuple types
 * (Pair, Triple, Quartet, Quintet), enabling loop-based code generation
 * instead of per-arity copy-paste.
 */
public enum TupleInfo {

    PAIR(2, Type.PAIR, Pair.class, "Pair"),
    TRIPLE(3, Type.TRIPLE, Triple.class, "Triple"),
    QUARTET(4, Type.QUARTET, Quartet.class, "Quartet"),
    QUINTET(5, Type.QUINTET, Quintet.class, "Quintet");

    private static final String[] ACCESSOR_NAMES = {"getFirst", "getSecond", "getThird", "getFourth", "getFifth"};
    private static final String[] ORDINAL_NAMES = {"first", "second", "third", "fourth", "fifth"};

    final int arity;
    final Type type;
    final Class<?> tupleClass;
    final String label;

    TupleInfo(int arity, Type type, Class<?> tupleClass, String label) {
        this.arity = arity;
        this.type = type;
        this.tupleClass = tupleClass;
        this.label = label;
    }

    public int arity() { return arity; }
    public Type type() { return type; }
    public Class<?> tupleClass() { return tupleClass; }
    public String label() { return label; }

    public String accessor(int i) { return ACCESSOR_NAMES[i]; }
    public String ordinal(int i) { return ORDINAL_NAMES[i]; }

    public static TupleInfo fromType(Type type) {
        for (TupleInfo info : values()) {
            if (info.type == type) return info;
        }
        throw new IllegalArgumentException("Not a tuple type: " + type);
    }

    public static boolean isTupleType(Type type) {
        return type == Type.PAIR || type == Type.TRIPLE || type == Type.QUARTET || type == Type.QUINTET;
    }

}
