package com.bloxbean.cardano.client.quicktx.filter.ast;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class And implements FilterNode {
    private final List<FilterNode> terms;

    public And(List<FilterNode> terms) {
        if (terms == null)
            throw new IllegalArgumentException("terms cannot be null");
        this.terms = Collections.unmodifiableList(terms);
    }

    public List<FilterNode> getTerms() {
        return terms;
    }

    @Override
    public <T> T accept(FilterVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof And)) return false;
        And and = (And) o;
        return Objects.equals(terms, and.terms);
    }

    @Override
    public int hashCode() {
        return Objects.hash(terms);
    }

    @Override
    public String toString() {
        return "And" + terms;
    }
}
