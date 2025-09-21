package com.bloxbean.cardano.client.quicktx.filter;

import com.bloxbean.cardano.client.quicktx.filter.ast.FilterNode;

import java.util.Objects;

public final class ImmutableUtxoFilterSpec implements UtxoFilterSpec {
    private final FilterNode root;
    private final Selection selection;
    private final String backend;

    private ImmutableUtxoFilterSpec(FilterNode root, Selection selection, String backend) {
        this.root = Objects.requireNonNull(root, "root");
        this.selection = selection;
        this.backend = backend;
    }

    public static Builder builder(FilterNode root) {
        return new Builder(root);
    }

    @Override
    public FilterNode root() {
        return root;
    }

    @Override
    public Selection selection() {
        return selection;
    }

    @Override
    public String backend() {
        return backend;
    }

    public static final class Builder {
        private final FilterNode root;
        private Selection selection;
        private String backend;

        public Builder(FilterNode root) {
            if (root == null) throw new IllegalArgumentException("root cannot be null");
            this.root = root;
        }

        public Builder selection(Selection selection) {
            this.selection = selection;
            return this;
        }

        public Builder backend(String backend) {
            this.backend = backend;
            return this;
        }

        public ImmutableUtxoFilterSpec build() {
            return new ImmutableUtxoFilterSpec(root, selection, backend);
        }
    }
}

