package com.bloxbean.cardano.client.util;

import java.util.Objects;

public class Tuple<T, Z> {

    public T _1;
    public Z _2;

    public Tuple(T _1, Z _2) {
        this._1 = _1;
        this._2 = _2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tuple<?, ?> tuple = (Tuple<?, ?>) o;
        return Objects.equals(_1, tuple._1) && Objects.equals(_2, tuple._2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_1, _2);
    }

}
