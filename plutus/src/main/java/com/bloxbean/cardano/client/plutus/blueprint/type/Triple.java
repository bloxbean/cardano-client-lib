package com.bloxbean.cardano.client.plutus.blueprint.type;

import java.util.Objects;

//NOTE: This class is not used currently. It is here for future implementation
class Triple<T, Z, Y> {
    public T first;
    public Z second;
    public Y third;

    public Triple(T first, Z second, Y third) {
        this.first = first;
        this.second = second;
        this.third = third;
    }

    public T getFirst() {
        return first;
    }

    public Z getSecond() {
        return second;
    }

    public Y getThird() {
        return third;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Triple<?, ?, ?> triple = (Triple<?, ?, ?>) o;
        return Objects.equals(first, triple.first) && Objects.equals(second, triple.second) && Objects.equals(third, triple.third);
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second, third);
    }

    @Override
    public String toString() {
        return "Triple{" +
                "first=" + first +
                ", second=" + second +
                ", third=" + third +
                '}';
    }
}
