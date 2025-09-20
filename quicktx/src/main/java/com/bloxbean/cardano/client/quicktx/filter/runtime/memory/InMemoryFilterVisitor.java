package com.bloxbean.cardano.client.quicktx.filter.runtime.memory;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.quicktx.filter.ast.*;

import java.math.BigInteger;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Compiles Filter AST into a Predicate&lt;Utxo&gt; for in-memory evaluation.
 * This visitor traverses the filter AST and produces Java predicates that
 * can be applied directly to Utxo objects in memory.
 *
 * <p>Type validation:
 * <ul>
 *   <li>String fields (address, dataHash, inlineDatum) only accept EQ/NE operations</li>
 *   <li>Numeric fields (amount quantities) accept all comparison operations</li>
 *   <li>NULL values are only valid with EQ/NE operations</li>
 * </ul>
 *
 * <p>Usage example:
 * <pre>{@code
 * FilterNode filter = new Comparison(AddressField.INSTANCE, CmpOp.EQ, Value.ofString("addr1..."));
 * Predicate<Utxo> predicate = filter.accept(new InMemoryFilterVisitor());
 * List<Utxo> filtered = utxos.stream().filter(predicate).collect(Collectors.toList());
 * }</pre>
 *
 * @see FilterVisitor
 * @see InMemoryUtxoFilterEngine
 */
public final class InMemoryFilterVisitor implements FilterVisitor<Predicate<Utxo>> {

    /**
     * Compiles a comparison node into a predicate that evaluates field comparisons.
     *
     * @param node the comparison node with field, operator, and value
     * @return a predicate that evaluates the comparison for each UTxO
     * @throws IllegalArgumentException if the field/operator combination is invalid
     */
    @Override
    public Predicate<Utxo> visit(Comparison node) {
        FieldRef field = node.getField();
        CmpOp op = node.getOp();
        Value val = node.getValue();

        if (field instanceof AddressField) {
            ensureStringOp(op, "address");
            return strCmp(Utxo::getAddress, op, val);
        } else if (field instanceof DataHashField) {
            ensureStringOp(op, "dataHash");
            return strCmp(Utxo::getDataHash, op, val);
        } else if (field instanceof InlineDatumField) {
            ensureStringOp(op, "inlineDatum");
            return strCmp(Utxo::getInlineDatum, op, val);
        } else if (field instanceof AmountQuantityField) {
            String unit = ((AmountQuantityField) field).getUnit();
            ensureNumericOp(op, "amount.quantity(" + unit + ")");
            return numCmp(u -> quantityOf(u, unit), op, val);
        } else {
            throw new IllegalArgumentException("Unknown field: " + field);
        }
    }

    /**
     * Compiles an AND node into a predicate that requires all terms to be true.
     *
     * @param node the AND node containing multiple filter terms
     * @return a predicate that is true only if all sub-predicates are true
     */
    @Override
    public Predicate<Utxo> visit(And node) {
        var terms = node.getTerms();
        Predicate<Utxo> p = u -> true;
        for (FilterNode t : terms) {
            p = p.and(t.accept(this));
        }
        return p;
    }

    /**
     * Compiles an OR node into a predicate that requires at least one term to be true.
     *
     * @param node the OR node containing multiple filter terms
     * @return a predicate that is true if any sub-predicate is true
     */
    @Override
    public Predicate<Utxo> visit(Or node) {
        var terms = node.getTerms();
        Predicate<Utxo> p = u -> false;
        for (FilterNode t : terms) {
            p = p.or(t.accept(this));
        }
        return p;
    }

    /**
     * Compiles a NOT node into a predicate that negates its inner term.
     *
     * @param node the NOT node containing a single filter term
     * @return a predicate that inverts the result of the inner predicate
     */
    @Override
    public Predicate<Utxo> visit(Not node) {
        return node.getTerm().accept(this).negate();
    }

    private static void ensureStringOp(CmpOp op, String field) {
        if (!(op == CmpOp.EQ || op == CmpOp.NE)) {
            throw new IllegalArgumentException("Invalid op for string field '" + field + "': " + op);
        }
    }

    private static void ensureNumericOp(CmpOp op, String field) {
        // all ops allowed on numeric
        Objects.requireNonNull(op);
    }

    private static Predicate<Utxo> strCmp(java.util.function.Function<Utxo, String> getter, CmpOp op, Value val) {
        if (!(op == CmpOp.EQ || op == CmpOp.NE))
            throw new IllegalArgumentException("Invalid op for string comparison: " + op);

        if (val.getKind() == Value.Kind.NULL) {
            return op == CmpOp.EQ
                    ? (u -> getter.apply(u) == null)
                    : (u -> getter.apply(u) != null);
        }

        if (val.getKind() != Value.Kind.STRING)
            throw new IllegalArgumentException("Expected string or null value for string comparison");

        final String right = val.asString();
        return op == CmpOp.EQ
                ? (u -> Objects.equals(getter.apply(u), right))
                : (u -> !Objects.equals(getter.apply(u), right));
    }

    private static Predicate<Utxo> numCmp(java.util.function.Function<Utxo, BigInteger> getter, CmpOp op, Value val) {
        if (val.getKind() != Value.Kind.INTEGER)
            throw new IllegalArgumentException("Expected integer value for numeric comparison");
        BigInteger right = val.asInteger();
        switch (op) {
            case EQ: return u -> getter.apply(u).compareTo(right) == 0;
            case NE: return u -> getter.apply(u).compareTo(right) != 0;
            case GT: return u -> getter.apply(u).compareTo(right) > 0;
            case GTE: return u -> getter.apply(u).compareTo(right) >= 0;
            case LT: return u -> getter.apply(u).compareTo(right) < 0;
            case LTE: return u -> getter.apply(u).compareTo(right) <= 0;
            default: throw new IllegalStateException("Unexpected op: " + op);
        }
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
