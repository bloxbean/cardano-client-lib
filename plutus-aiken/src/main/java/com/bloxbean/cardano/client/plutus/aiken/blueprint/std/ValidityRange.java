package com.bloxbean.cardano.client.plutus.aiken.blueprint.std;

import com.bloxbean.cardano.client.plutus.blueprint.model.Data;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

import java.util.Objects;

/**
 * Shared representation of the CIP-57 ValidityRange (Interval) schema.
 * <p>
 * Represents a time interval with lower and upper {@link IntervalBound}s.
 * Named {@code ValidityRange} after the stdlib v3 canonical title
 * ({@code cardano/transaction/ValidityRange}); the v1 equivalent is titled
 * "Interval" but maps to the same on-chain structure.
 * <p>
 * On-chain layout: {@code Constr(0, [lower_bound, upper_bound])}.
 */
public final class ValidityRange implements Data<ValidityRange> {

    private final IntervalBound lowerBound;
    private final IntervalBound upperBound;

    public ValidityRange(IntervalBound lowerBound, IntervalBound upperBound) {
        this.lowerBound = Objects.requireNonNull(lowerBound, "lowerBound cannot be null");
        this.upperBound = Objects.requireNonNull(upperBound, "upperBound cannot be null");
    }

    /**
     * Deserializes a {@link ConstrPlutusData} back into a {@link ValidityRange}.
     * Field 0 = lower_bound, field 1 = upper_bound.
     */
    public static ValidityRange fromPlutusData(ConstrPlutusData constr) {
        IntervalBound lower = IntervalBound.fromPlutusData(
                (ConstrPlutusData) constr.getData().getPlutusDataList().get(0));
        IntervalBound upper = IntervalBound.fromPlutusData(
                (ConstrPlutusData) constr.getData().getPlutusDataList().get(1));
        return new ValidityRange(lower, upper);
    }

    public IntervalBound getLowerBound() {
        return lowerBound;
    }

    public IntervalBound getUpperBound() {
        return upperBound;
    }

    @Override
    public ConstrPlutusData toPlutusData() {
        PlutusData lower = lowerBound.toPlutusData();
        PlutusData upper = upperBound.toPlutusData();
        return ConstrPlutusData.of(0, lower, upper);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ValidityRange)) return false;
        ValidityRange that = (ValidityRange) o;
        return lowerBound.equals(that.lowerBound) && upperBound.equals(that.upperBound);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lowerBound, upperBound);
    }

    @Override
    public String toString() {
        return "ValidityRange";
    }
}
