package com.bloxbean.cardano.client.plutus.aiken.blueprint.std;

import com.bloxbean.cardano.client.plutus.blueprint.model.Data;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

import java.util.Objects;

/**
 * Shared representation of the CIP-57 IntervalBound schema.
 * <p>
 * An interval bound consists of a {@link IntervalBoundType} (the bound value or infinity)
 * and a boolean indicating whether the bound is inclusive.
 * <p>
 * On-chain layout: {@code Constr(0, [bound_type, is_inclusive])} where
 * {@code is_inclusive} is encoded as {@code Constr(0)} for {@code False}
 * and {@code Constr(1)} for {@code True}.
 * <p>
 * Structure is identical across Aiken stdlib versions; only the {@code $ref} key
 * syntax differs ({@code $Int} in v1 vs {@code <Int>} in v3).
 */
public final class IntervalBound implements Data<IntervalBound> {

    private final IntervalBoundType boundType;
    private final boolean isInclusive;

    public IntervalBound(IntervalBoundType boundType, boolean isInclusive) {
        this.boundType = Objects.requireNonNull(boundType, "boundType cannot be null");
        this.isInclusive = isInclusive;
    }

    /**
     * Deserializes a {@link ConstrPlutusData} back into an {@link IntervalBound}.
     * Field 0 = bound_type (IntervalBoundType), field 1 = is_inclusive (Bool).
     */
    public static IntervalBound fromPlutusData(ConstrPlutusData constr) {
        IntervalBoundType boundType = IntervalBoundType.fromPlutusData(
                (ConstrPlutusData) constr.getData().getPlutusDataList().get(0));

        ConstrPlutusData boolConstr = (ConstrPlutusData) constr.getData().getPlutusDataList().get(1);
        boolean isInclusive = boolConstr.getAlternative() == 1;

        return new IntervalBound(boundType, isInclusive);
    }

    public IntervalBoundType getBoundType() {
        return boundType;
    }

    public boolean isInclusive() {
        return isInclusive;
    }

    @Override
    public ConstrPlutusData toPlutusData() {
        PlutusData boundTypeData = boundType.toPlutusData();
        // Bool: Constr(0) = False, Constr(1) = True
        PlutusData inclusiveData = ConstrPlutusData.of(isInclusive ? 1 : 0);
        return ConstrPlutusData.of(0, boundTypeData, inclusiveData);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IntervalBound that)) return false;
        return isInclusive == that.isInclusive && boundType.equals(that.boundType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(boundType, isInclusive);
    }

    @Override
    public String toString() {
        return "IntervalBound";
    }
}
