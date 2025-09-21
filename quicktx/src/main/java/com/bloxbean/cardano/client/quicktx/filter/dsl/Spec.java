package com.bloxbean.cardano.client.quicktx.filter.dsl;

import com.bloxbean.cardano.client.quicktx.filter.ImmutableUtxoFilterSpec;
import com.bloxbean.cardano.client.quicktx.filter.Order;
import com.bloxbean.cardano.client.quicktx.filter.Selection;
import com.bloxbean.cardano.client.quicktx.filter.UtxoFilterSpec;
import com.bloxbean.cardano.client.quicktx.filter.ast.FilterNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builder for UtxoFilterSpec starting from a root node.
 */
public final class Spec {
    private Spec() {}

    public static Builder of(FilterNode root) {
        return new Builder(root);
    }

    public static final class Builder {
        private final FilterNode root;
        private final List<Order> orders = new ArrayList<>();
        private Integer limit; // null => ALL
        private String backend; // optional

        public Builder(FilterNode root) {
            if (root == null) throw new IllegalArgumentException("root cannot be null");
            this.root = root;
        }

        public Builder orderBy(Order... os) {
            if (os != null && os.length > 0) orders.addAll(Arrays.asList(os));
            return this;
        }

        public Builder limit(int n) {
            if (n < 0) throw new IllegalArgumentException("limit must be >= 0");
            this.limit = n; return this;
        }

        public Builder limitAll() { this.limit = null; return this; }

        public Builder backend(String backend) { this.backend = backend; return this; }

        public UtxoFilterSpec build() {
            return ImmutableUtxoFilterSpec.builder(root)
                    .selection(Selection.of(orders, limit))
                    .backend(backend)
                    .build();
        }
    }
}

