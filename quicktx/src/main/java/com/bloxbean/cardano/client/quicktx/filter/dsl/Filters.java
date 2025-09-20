package com.bloxbean.cardano.client.quicktx.filter.dsl;

import com.bloxbean.cardano.client.quicktx.filter.ast.And;
import com.bloxbean.cardano.client.quicktx.filter.ast.FilterNode;
import com.bloxbean.cardano.client.quicktx.filter.ast.Not;
import com.bloxbean.cardano.client.quicktx.filter.ast.Or;

import java.util.Arrays;

/**
 * Logical combinators to compose filter nodes.
 */
public final class Filters {
    private Filters() {}

    public static FilterNode and(FilterNode... nodes) { return new And(Arrays.asList(nodes)); }

    public static FilterNode or(FilterNode... nodes) { return new Or(Arrays.asList(nodes)); }

    public static FilterNode not(FilterNode node) { return new Not(node); }
}

