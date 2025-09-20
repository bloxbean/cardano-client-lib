package com.bloxbean.cardano.client.quicktx.filter.runtime.memory;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.quicktx.filter.Order;
import com.bloxbean.cardano.client.quicktx.filter.Selection;
import com.bloxbean.cardano.client.quicktx.filter.ast.FilterNode;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * In-memory implementation of the UTxO filter engine.
 * Evaluates filter AST against a list of UTxOs and applies selection criteria.
 *
 * <p>Processing pipeline:
 * <ol>
 *   <li>Filter: Apply the filter AST to select matching UTxOs</li>
 *   <li>Order: Sort results according to selection criteria</li>
 *   <li>Limit: Return only the specified number of results</li>
 * </ol>
 *
 * <p>Ordering includes automatic tie-breakers (txHash ASC, outputIndex ASC)
 * to ensure deterministic results across executions.
 *
 * <p>Usage example:
 * <pre>{@code
 * FilterNode filter = new Comparison(AddressField.INSTANCE, CmpOp.EQ, Value.ofString("addr1..."));
 * Selection selection = Selection.of(List.of(Order.lovelace(Direction.DESC)), 10);
 * List<Utxo> results = InMemoryUtxoFilterEngine.filter(utxos, filter, selection);
 * }</pre>
 *
 * @see InMemoryFilterVisitor
 * @see Selection
 */
public final class InMemoryUtxoFilterEngine {
    private InMemoryUtxoFilterEngine() {}

    /**
     * Filters, orders, and limits UTxOs according to the provided criteria.
     *
     * @param utxos the input UTxOs to filter (must not be null)
     * @param root the filter AST root node (must not be null)
     * @param selection the selection criteria for ordering and limiting (may be null for defaults)
     * @return the filtered, ordered, and limited list of UTxOs
     * @throws NullPointerException if utxos or root is null
     */
    public static List<Utxo> filter(List<Utxo> utxos, FilterNode root, Selection selection) {
        Objects.requireNonNull(utxos, "utxos");
        Objects.requireNonNull(root, "root");

        Predicate<Utxo> predicate = root.accept(new InMemoryFilterVisitor());
        List<Utxo> filtered = new ArrayList<>();
        for (Utxo u : utxos) {
            if (predicate.test(u)) filtered.add(u);
        }

        // Order
        Comparator<Utxo> comp = buildComparator(selection);
        filtered.sort(comp);

        // Limit
        if (selection != null && selection.getLimit() != null && selection.getLimit() >= 0) {
            int n = Math.min(selection.getLimit(), filtered.size());
            return new ArrayList<>(filtered.subList(0, n));
        }

        return filtered;
    }

    private static Comparator<Utxo> buildComparator(Selection selection) {
        Comparator<Utxo> comp = null;
        if (selection != null && selection.getOrder() != null && !selection.getOrder().isEmpty()) {
            for (Order o : selection.getOrder()) {
                Comparator<Utxo> c = comparatorFor(o);
                comp = (comp == null) ? c : comp.thenComparing(c);
            }
        }
        // Always append canonical tie-breakers
        Comparator<Utxo> tie = Comparator
                .comparing(Utxo::getTxHash, Comparator.nullsLast(String::compareTo))
                .thenComparingInt(Utxo::getOutputIndex);
        comp = (comp == null) ? tie : comp.thenComparing(tie);
        return comp;
    }

    private static Comparator<Utxo> comparatorFor(Order o) {
        Comparator<Utxo> c;
        switch (o.getField()) {
            case LOVELACE:
                c = Comparator.comparing(u -> quantityOf(u, "lovelace"));
                break;
            case AMOUNT_UNIT:
                c = Comparator.comparing(u -> quantityOf(u, o.getUnit()));
                break;
            case ADDRESS:
                c = Comparator.comparing(Utxo::getAddress, Comparator.nullsLast(String::compareTo));
                break;
            case DATA_HASH:
                c = Comparator.comparing(Utxo::getDataHash, Comparator.nullsLast(String::compareTo));
                break;
            case INLINE_DATUM:
                c = Comparator.comparing(Utxo::getInlineDatum, Comparator.nullsLast(String::compareTo));
                break;
            case TX_HASH:
                c = Comparator.comparing(Utxo::getTxHash, Comparator.nullsLast(String::compareTo));
                break;
            case OUTPUT_INDEX:
                c = Comparator.comparingInt(u -> u.getOutputIndex());
                break;
            default:
                throw new IllegalArgumentException("Unknown order field: " + o.getField());
        }
        if (o.getDirection() == Order.Direction.DESC) c = c.reversed();
        return c;
    }

    private static BigInteger quantityOf(Utxo u, String unit) {
        if (u.getAmount() == null) return BigInteger.ZERO;
        BigInteger sum = BigInteger.ZERO;
        for (Amount amt : u.getAmount()) {
            if (unit.equals(amt.getUnit()) && amt.getQuantity() != null) {
                sum = sum.add(amt.getQuantity());
            }
        }
        return sum;
    }
}

