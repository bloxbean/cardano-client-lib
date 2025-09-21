package com.bloxbean.cardano.client.quicktx.filter.ast;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class Or implements FilterNode {
    private final List<FilterNode> terms;

    public Or(List<FilterNode> terms) {
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
        if (!(o instanceof Or)) return false;
        Or or = (Or) o;
        return Objects.equals(terms, or.terms);
    }

    @Override
    public int hashCode() {
        return Objects.hash(terms);
    }

    @Override
    public String toString() {
        return "Or" + terms;
    }
}
