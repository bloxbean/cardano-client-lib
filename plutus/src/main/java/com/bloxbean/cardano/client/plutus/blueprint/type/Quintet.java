package com.bloxbean.cardano.client.plutus.blueprint.type;

import java.util.Objects;

//NOTE: This class is not used currently. It is here for future implementation
class Quintet<T1, T2, T3, T4, T5> {
    private T1 first;
    private T2 second;
    private T3 third;
    private T4 fourth;
    private T5 fifth;

    public Quintet(T1 first, T2 second, T3 third, T4 fourth, T5 fifth) {
        this.first = first;
        this.second = second;
        this.third = third;
        this.fourth = fourth;
        this.fifth = fifth;
    }

    public T1 getFirst() {
        return first;
    }

    public T2 getSecond() {
        return second;
    }

    public T3 getThird() {
        return third;
    }

    public T4 getFourth() {
        return fourth;
    }

    public T5 getFifth() {
        return fifth;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Quintet<?, ?, ?, ?, ?> quintet = (Quintet<?, ?, ?, ?, ?>) o;
        return Objects.equals(first, quintet.first) && Objects.equals(second, quintet.second) && Objects.equals(third, quintet.third) && Objects.equals(fourth, quintet.fourth) && Objects.equals(fifth, quintet.fifth);
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second, third, fourth, fifth);
    }

    @Override
    public String toString() {
        return "Quintet{" +
                "first=" + first +
                ", second=" + second +
                ", third=" + third +
                ", fourth=" + fourth +
                ", fifth=" + fifth +
                '}';
    }
}
