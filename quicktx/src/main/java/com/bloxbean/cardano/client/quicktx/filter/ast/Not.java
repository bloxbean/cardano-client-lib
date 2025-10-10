package com.bloxbean.cardano.client.quicktx.filter.ast;

import java.util.Objects;

public final class Not implements FilterNode {
    private final FilterNode term;

    public Not(FilterNode term) {
        if (term == null) throw new IllegalArgumentException("NOT requires a term");
        this.term = term;
    }

    public FilterNode getTerm() {
        return term;
    }

    @Override
    public <T> T accept(FilterVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Not)) return false;
        Not not = (Not) o;
        return Objects.equals(term, not.term);
    }

    @Override
    public int hashCode() {
        return Objects.hash(term);
    }

    @Override
    public String toString() {
        return "Not{" + term + '}';
    }
}

