package com.bloxbean.cardano.client.plutus.aiken.blueprint.std;

import com.bloxbean.cardano.client.plutus.blueprint.model.Data;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Shared representation of the CIP-57 IntervalBoundType schema.
 * <p>
 * Represents the type of a bound in a validity interval:
 * <ul>
 *   <li>{@link NegativeInfinity} (index 0) — no lower bound</li>
 *   <li>{@link Finite} (index 1) — a specific POSIX time value</li>
 *   <li>{@link PositiveInfinity} (index 2) — no upper bound</li>
 * </ul>
 * Structure is identical across all Aiken stdlib versions.
 */
public interface IntervalBoundType extends Data<IntervalBoundType> {

    static IntervalBoundType negativeInfinity() {
        return NegativeInfinity.INSTANCE;
    }

    static IntervalBoundType finite(BigInteger value) {
        return new Finite(value);
    }

    static IntervalBoundType positiveInfinity() {
        return PositiveInfinity.INSTANCE;
    }

    /**
     * Deserializes a {@link ConstrPlutusData} back into an {@link IntervalBoundType}.
     * Alternative 0 → {@link NegativeInfinity}, 1 → {@link Finite}, 2 → {@link PositiveInfinity}.
     */
    static IntervalBoundType fromPlutusData(ConstrPlutusData constr) {
        return switch ((int) constr.getAlternative()) {
            case 0 -> NegativeInfinity.INSTANCE;
            case 1 -> new Finite(((BigIntPlutusData) constr.getData().getPlutusDataList().get(0)).getValue());
            case 2 -> PositiveInfinity.INSTANCE;
            default -> throw new IllegalArgumentException("Invalid IntervalBoundType alternative: " + constr.getAlternative());
        };
    }

    /**
     * Negative infinity — no lower bound.
     */
    final class NegativeInfinity implements IntervalBoundType {
        static final NegativeInfinity INSTANCE = new NegativeInfinity();

        private NegativeInfinity() {}

        @Override
        public ConstrPlutusData toPlutusData() {
            return ConstrPlutusData.of(0);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof NegativeInfinity;
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public String toString() {
            return "NegativeInfinity";
        }
    }

    /**
     * Finite bound with a specific integer value (typically POSIX time in milliseconds).
     */
    final class Finite implements IntervalBoundType {
        private final BigInteger value;

        public Finite(BigInteger value) {
            this.value = Objects.requireNonNull(value, "value cannot be null");
        }

        public BigInteger getValue() {
            return value;
        }

        @Override
        public ConstrPlutusData toPlutusData() {
            PlutusData val = BigIntPlutusData.of(value);
            return ConstrPlutusData.of(1, val);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Finite)) return false;
            Finite finite = (Finite) o;
            return value.equals(finite.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return "Finite(" + value + ")";
        }
    }

    /**
     * Positive infinity — no upper bound.
     */
    final class PositiveInfinity implements IntervalBoundType {
        static final PositiveInfinity INSTANCE = new PositiveInfinity();

        private PositiveInfinity() {}

        @Override
        public ConstrPlutusData toPlutusData() {
            return ConstrPlutusData.of(2);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof PositiveInfinity;
        }

        @Override
        public int hashCode() {
            return 2;
        }

        @Override
        public String toString() {
            return "PositiveInfinity";
        }
    }
}
