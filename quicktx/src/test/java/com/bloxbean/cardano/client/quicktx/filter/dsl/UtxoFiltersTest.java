package com.bloxbean.cardano.client.quicktx.filter.dsl;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.quicktx.filter.Order;
import com.bloxbean.cardano.client.quicktx.filter.UtxoFilterSpec;
import com.bloxbean.cardano.client.quicktx.filter.ast.*;
import com.bloxbean.cardano.client.quicktx.filter.runtime.memory.InMemoryUtxoFilterEngine;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static com.bloxbean.cardano.client.quicktx.filter.dsl.UtxoFilters.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for UtxoFilters unified DSL API.
 * Ensures complete coverage of all field builders, operators, ordering, and helper methods.
 */
class UtxoFiltersTest {

    // ========== Helper Methods ==========

    private static Utxo utxo(String txHash, int outputIndex, String address, String dataHash,
                             String inlineDatum, String refScriptHash, long lovelace) {
        return Utxo.builder()
                .txHash(txHash)
                .outputIndex(outputIndex)
                .address(address)
                .dataHash(dataHash)
                .inlineDatum(inlineDatum)
                .referenceScriptHash(refScriptHash)
                .amount(Arrays.asList(Amount.lovelace(BigInteger.valueOf(lovelace))))
                .build();
    }

    private static Utxo utxoSimple(String txHash, int outputIndex, long lovelace) {
        return utxo(txHash, outputIndex, "addr1", null, null, null, lovelace);
    }

    // ========== Common Pattern Helper Tests ==========

    @Test
    void minLovelace_creates_correct_spec() {
        UtxoFilterSpec spec = minLovelace(2_000_000);

        assertNotNull(spec);
        assertNotNull(spec.root());

        // Verify it's a Comparison node
        assertTrue(spec.root() instanceof Comparison);
        Comparison cmp = (Comparison) spec.root();
        assertEquals(CmpOp.GTE, cmp.getOp());
        assertEquals(Value.Kind.INTEGER, cmp.getValue().getKind());
        assertEquals(BigInteger.valueOf(2_000_000), cmp.getValue().asInteger());

        // Verify it filters correctly
        List<Utxo> utxos = Arrays.asList(
                utxoSimple("tx1", 0, 1_000_000),
                utxoSimple("tx2", 0, 2_000_000),
                utxoSimple("tx3", 0, 3_000_000)
        );
        List<Utxo> result = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        assertEquals(2, result.size());
        assertEquals("tx2", result.get(0).getTxHash());
        assertEquals("tx3", result.get(1).getTxHash());
    }

    @Test
    void specificUtxo_creates_correct_spec() {
        UtxoFilterSpec spec = specificUtxo("abc123", 5);

        assertNotNull(spec);
        assertNotNull(spec.root());

        // Verify it's an AND node
        assertTrue(spec.root() instanceof And);
        And and = (And) spec.root();
        assertEquals(2, and.getTerms().size());

        // Verify both conditions
        Comparison txHashCmp = (Comparison) and.getTerms().get(0);
        assertTrue(txHashCmp.getField() instanceof TxHashField);
        assertEquals(CmpOp.EQ, txHashCmp.getOp());
        assertEquals("abc123", txHashCmp.getValue().asString());

        Comparison outputIndexCmp = (Comparison) and.getTerms().get(1);
        assertTrue(outputIndexCmp.getField() instanceof OutputIndexField);
        assertEquals(CmpOp.EQ, outputIndexCmp.getOp());
        assertEquals(BigInteger.valueOf(5), outputIndexCmp.getValue().asInteger());

        // Verify it filters correctly
        List<Utxo> utxos = Arrays.asList(
                utxo("abc123", 4, "addr1", null, null, null, 1_000_000),
                utxo("abc123", 5, "addr1", null, null, null, 2_000_000),
                utxo("def456", 5, "addr1", null, null, null, 3_000_000)
        );
        List<Utxo> result = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        assertEquals(1, result.size());
        assertEquals("abc123", result.get(0).getTxHash());
        assertEquals(5, result.get(0).getOutputIndex());
    }

    @Test
    void withReferenceScript_creates_correct_spec() {
        UtxoFilterSpec spec = withReferenceScript();

        assertNotNull(spec);
        assertNotNull(spec.root());

        // Verify it's a Comparison node checking for not null
        assertTrue(spec.root() instanceof Comparison);
        Comparison cmp = (Comparison) spec.root();
        assertTrue(cmp.getField() instanceof ReferenceScriptHashField);
        assertEquals(CmpOp.NE, cmp.getOp());
        assertEquals(Value.Kind.NULL, cmp.getValue().getKind());

        // Verify it filters correctly
        List<Utxo> utxos = Arrays.asList(
                utxo("tx1", 0, "addr1", null, null, null, 1_000_000),
                utxo("tx2", 0, "addr1", null, null, "script123", 2_000_000),
                utxo("tx3", 0, "addr1", null, null, "script456", 3_000_000)
        );
        List<Utxo> result = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        assertEquals(2, result.size());
        assertEquals("tx2", result.get(0).getTxHash());
        assertEquals("tx3", result.get(1).getTxHash());
    }

    @Test
    void addressWithMinLovelace_creates_correct_spec() {
        UtxoFilterSpec spec = addressWithMinLovelace("addr_test1xyz", 5_000_000);

        assertNotNull(spec);
        assertNotNull(spec.root());

        // Verify it's an AND node
        assertTrue(spec.root() instanceof And);
        And and = (And) spec.root();
        assertEquals(2, and.getTerms().size());

        // Verify both conditions
        Comparison addressCmp = (Comparison) and.getTerms().get(0);
        assertTrue(addressCmp.getField() instanceof AddressField);
        assertEquals(CmpOp.EQ, addressCmp.getOp());
        assertEquals("addr_test1xyz", addressCmp.getValue().asString());

        Comparison lovelaceCmp = (Comparison) and.getTerms().get(1);
        assertTrue(lovelaceCmp.getField() instanceof AmountQuantityField);
        assertEquals(CmpOp.GTE, lovelaceCmp.getOp());
        assertEquals(BigInteger.valueOf(5_000_000), lovelaceCmp.getValue().asInteger());

        // Verify it filters correctly
        List<Utxo> utxos = Arrays.asList(
                utxo("tx1", 0, "addr_test1xyz", null, null, null, 4_000_000),
                utxo("tx2", 0, "addr_test1xyz", null, null, null, 5_000_000),
                utxo("tx3", 0, "addr_other", null, null, null, 10_000_000)
        );
        List<Utxo> result = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        assertEquals(1, result.size());
        assertEquals("tx2", result.get(0).getTxHash());
    }

    // ========== Filter Builder Tests ==========

    @Test
    void filter_with_simple_condition() {
        UtxoFilterSpec spec = filter(lovelace().gte(1_000_000)).build();

        assertNotNull(spec);
        assertNotNull(spec.root());
        assertTrue(spec.root() instanceof Comparison);
    }

    @Test
    void filter_with_ordering_and_limit() {
        UtxoFilterSpec spec = filter(address().eq("addr1"))
                .orderBy(lovelaceDesc(), outputIndexAsc())
                .limit(10)
                .build();

        assertNotNull(spec);
        assertNotNull(spec.root());
        assertNotNull(spec.selection());
        assertEquals(10, spec.selection().getLimit().intValue());
        assertEquals(2, spec.selection().getOrder().size());
    }

    @Test
    void filter_with_complex_nested_conditions() {
        UtxoFilterSpec spec = filter(
                or(
                        and(address().eq("addr1"), lovelace().gte(1_000_000)),
                        and(referenceScriptHash().notNull(), lovelace().gte(2_000_000))
                )
        ).build();

        assertNotNull(spec);
        assertTrue(spec.root() instanceof Or);
        Or or = (Or) spec.root();
        assertEquals(2, or.getTerms().size());
        assertTrue(or.getTerms().get(0) instanceof And);
        assertTrue(or.getTerms().get(1) instanceof And);
    }

    // ========== String Field Builder Tests ==========

    @Test
    void address_field_all_operations() {
        // eq
        FilterNode eq = address().eq("addr1");
        assertTrue(eq instanceof Comparison);
        Comparison eqCmp = (Comparison) eq;
        assertTrue(eqCmp.getField() instanceof AddressField);
        assertEquals(CmpOp.EQ, eqCmp.getOp());
        assertEquals("addr1", eqCmp.getValue().asString());

        // ne
        FilterNode ne = address().ne("addr2");
        assertTrue(ne instanceof Comparison);
        assertEquals(CmpOp.NE, ((Comparison) ne).getOp());

        // isNull
        FilterNode isNull = address().isNull();
        assertTrue(isNull instanceof Comparison);
        assertEquals(CmpOp.EQ, ((Comparison) isNull).getOp());
        assertEquals(Value.Kind.NULL, ((Comparison) isNull).getValue().getKind());

        // notNull
        FilterNode notNull = address().notNull();
        assertTrue(notNull instanceof Comparison);
        assertEquals(CmpOp.NE, ((Comparison) notNull).getOp());
        assertEquals(Value.Kind.NULL, ((Comparison) notNull).getValue().getKind());
    }

    @Test
    void dataHash_field_all_operations() {
        FilterNode eq = dataHash().eq("0xabc");
        assertTrue(eq instanceof Comparison);
        assertTrue(((Comparison) eq).getField() instanceof DataHashField);
        assertEquals("0xabc", ((Comparison) eq).getValue().asString());

        FilterNode notNull = dataHash().notNull();
        assertEquals(CmpOp.NE, ((Comparison) notNull).getOp());
    }

    @Test
    void inlineDatum_field_all_operations() {
        FilterNode eq = inlineDatum().eq("0xdatum123");
        assertTrue(eq instanceof Comparison);
        assertTrue(((Comparison) eq).getField() instanceof InlineDatumField);
        assertEquals("0xdatum123", ((Comparison) eq).getValue().asString());

        FilterNode isNull = inlineDatum().isNull();
        assertEquals(CmpOp.EQ, ((Comparison) isNull).getOp());
    }

    @Test
    void referenceScriptHash_field_all_operations() {
        FilterNode eq = referenceScriptHash().eq("script_hash_123");
        assertTrue(eq instanceof Comparison);
        assertTrue(((Comparison) eq).getField() instanceof ReferenceScriptHashField);
        assertEquals("script_hash_123", ((Comparison) eq).getValue().asString());

        FilterNode notNull = referenceScriptHash().notNull();
        assertEquals(CmpOp.NE, ((Comparison) notNull).getOp());
    }

    @Test
    void txHash_field_all_operations() {
        FilterNode eq = txHash().eq("tx_abc123");
        assertTrue(eq instanceof Comparison);
        assertTrue(((Comparison) eq).getField() instanceof TxHashField);
        assertEquals("tx_abc123", ((Comparison) eq).getValue().asString());

        FilterNode ne = txHash().ne("tx_excluded");
        assertEquals(CmpOp.NE, ((Comparison) ne).getOp());
    }

    // ========== Numeric Field Builder Tests ==========

    @Test
    void lovelace_field_all_operations() {
        // eq
        FilterNode eq = lovelace().eq(1_000_000);
        assertTrue(eq instanceof Comparison);
        Comparison eqCmp = (Comparison) eq;
        assertTrue(eqCmp.getField() instanceof AmountQuantityField);
        assertEquals(CmpOp.EQ, eqCmp.getOp());
        assertEquals(BigInteger.valueOf(1_000_000), eqCmp.getValue().asInteger());

        // ne
        FilterNode ne = lovelace().ne(500_000);
        assertEquals(CmpOp.NE, ((Comparison) ne).getOp());

        // gt
        FilterNode gt = lovelace().gt(2_000_000);
        assertEquals(CmpOp.GT, ((Comparison) gt).getOp());

        // gte
        FilterNode gte = lovelace().gte(3_000_000);
        assertEquals(CmpOp.GTE, ((Comparison) gte).getOp());

        // lt
        FilterNode lt = lovelace().lt(4_000_000);
        assertEquals(CmpOp.LT, ((Comparison) lt).getOp());

        // lte
        FilterNode lte = lovelace().lte(5_000_000);
        assertEquals(CmpOp.LTE, ((Comparison) lte).getOp());
    }

    @Test
    void amountUnit_field_all_operations() {
        String unit = "policy123asset456";

        FilterNode gte = amountUnit(unit).gte(100);
        assertTrue(gte instanceof Comparison);
        Comparison cmp = (Comparison) gte;
        assertTrue(cmp.getField() instanceof AmountQuantityField);
        assertEquals(CmpOp.GTE, cmp.getOp());
        assertEquals(BigInteger.valueOf(100), cmp.getValue().asInteger());

        FilterNode eq = amountUnit(unit).eq(50);
        assertEquals(CmpOp.EQ, ((Comparison) eq).getOp());
    }

    @Test
    void outputIndex_field_all_operations() {
        // eq
        FilterNode eq = outputIndex().eq(5);
        assertTrue(eq instanceof Comparison);
        assertTrue(((Comparison) eq).getField() instanceof OutputIndexField);
        assertEquals(BigInteger.valueOf(5), ((Comparison) eq).getValue().asInteger());

        // lt
        FilterNode lt = outputIndex().lt(10);
        assertEquals(CmpOp.LT, ((Comparison) lt).getOp());

        // gte
        FilterNode gte = outputIndex().gte(0);
        assertEquals(CmpOp.GTE, ((Comparison) gte).getOp());
    }

    // ========== Logical Operator Tests ==========

    @Test
    void and_combines_multiple_conditions() {
        FilterNode node = and(
                lovelace().gte(1_000_000),
                address().eq("addr1"),
                dataHash().notNull()
        );

        assertTrue(node instanceof And);
        And and = (And) node;
        assertEquals(3, and.getTerms().size());
    }

    @Test
    void or_combines_multiple_conditions() {
        FilterNode node = or(
                lovelace().gte(5_000_000),
                referenceScriptHash().notNull()
        );

        assertTrue(node instanceof Or);
        Or or = (Or) node;
        assertEquals(2, or.getTerms().size());
    }

    @Test
    void not_inverts_condition() {
        FilterNode node = not(lovelace().lt(1_000_000));

        assertTrue(node instanceof Not);
        Not not = (Not) node;
        assertTrue(not.getTerm() instanceof Comparison);
    }

    @Test
    void nested_logical_operators() {
        FilterNode node = and(
                or(address().eq("addr1"), address().eq("addr2")),
                not(lovelace().lt(1_000_000))
        );

        assertTrue(node instanceof And);
        And and = (And) node;
        assertEquals(2, and.getTerms().size());
        assertTrue(and.getTerms().get(0) instanceof Or);
        assertTrue(and.getTerms().get(1) instanceof Not);
    }

    // ========== Ordering Tests ==========

    @Test
    void lovelace_ordering() {
        Order asc = lovelaceAsc();
        assertNotNull(asc);
        assertEquals(Order.Direction.ASC, asc.getDirection());
        assertEquals(Order.Field.LOVELACE, asc.getField());

        Order desc = lovelaceDesc();
        assertNotNull(desc);
        assertEquals(Order.Direction.DESC, desc.getDirection());
        assertEquals(Order.Field.LOVELACE, desc.getField());
    }

    @Test
    void amountUnit_ordering() {
        String unit = "policy123asset456";

        Order asc = amountUnitAsc(unit);
        assertNotNull(asc);
        assertEquals(Order.Direction.ASC, asc.getDirection());
        assertEquals(Order.Field.AMOUNT_UNIT, asc.getField());
        assertEquals(unit, asc.getUnit());

        Order desc = amountUnitDesc(unit);
        assertEquals(Order.Direction.DESC, desc.getDirection());
    }

    @Test
    void address_ordering() {
        Order asc = addressAsc();
        assertEquals(Order.Field.ADDRESS, asc.getField());
        assertEquals(Order.Direction.ASC, asc.getDirection());

        Order desc = addressDesc();
        assertEquals(Order.Direction.DESC, desc.getDirection());
    }

    @Test
    void dataHash_ordering() {
        Order asc = dataHashAsc();
        assertEquals(Order.Field.DATA_HASH, asc.getField());
        assertEquals(Order.Direction.ASC, asc.getDirection());

        Order desc = dataHashDesc();
        assertEquals(Order.Direction.DESC, desc.getDirection());
    }

    @Test
    void inlineDatum_ordering() {
        Order asc = inlineDatumAsc();
        assertEquals(Order.Field.INLINE_DATUM, asc.getField());
        assertEquals(Order.Direction.ASC, asc.getDirection());

        Order desc = inlineDatumDesc();
        assertEquals(Order.Direction.DESC, desc.getDirection());
    }

    @Test
    void referenceScriptHash_ordering() {
        Order asc = referenceScriptHashAsc();
        assertEquals(Order.Field.REFERENCE_SCRIPT_HASH, asc.getField());
        assertEquals(Order.Direction.ASC, asc.getDirection());

        Order desc = referenceScriptHashDesc();
        assertEquals(Order.Direction.DESC, desc.getDirection());
    }

    @Test
    void txHash_ordering() {
        Order asc = txHashAsc();
        assertEquals(Order.Field.TX_HASH, asc.getField());
        assertEquals(Order.Direction.ASC, asc.getDirection());

        Order desc = txHashDesc();
        assertEquals(Order.Direction.DESC, desc.getDirection());
    }

    @Test
    void outputIndex_ordering() {
        Order asc = outputIndexAsc();
        assertEquals(Order.Field.OUTPUT_INDEX, asc.getField());
        assertEquals(Order.Direction.ASC, asc.getDirection());

        Order desc = outputIndexDesc();
        assertEquals(Order.Direction.DESC, desc.getDirection());
    }

    @Test
    void multiple_ordering_in_spec() {
        UtxoFilterSpec spec = filter(lovelace().gte(1_000_000))
                .orderBy(lovelaceDesc(), outputIndexAsc(), addressDesc())
                .build();

        assertNotNull(spec.selection());
        List<Order> orders = spec.selection().getOrder();
        assertEquals(3, orders.size());
        assertEquals(Order.Field.LOVELACE, orders.get(0).getField());
        assertEquals(Order.Direction.DESC, orders.get(0).getDirection());
        assertEquals(Order.Field.OUTPUT_INDEX, orders.get(1).getField());
        assertEquals(Order.Direction.ASC, orders.get(1).getDirection());
        assertEquals(Order.Field.ADDRESS, orders.get(2).getField());
        assertEquals(Order.Direction.DESC, orders.get(2).getDirection());
    }

    // ========== Integration Tests ==========

    @Test
    void complete_filter_with_all_features() {
        UtxoFilterSpec spec = filter(
                and(
                        or(
                                address().eq("addr1"),
                                address().eq("addr2")
                        ),
                        lovelace().gte(2_000_000),
                        not(dataHash().isNull()),
                        referenceScriptHash().notNull(),
                        outputIndex().lt(100)
                )
        ).orderBy(lovelaceDesc(), addressAsc())
                .limit(50)
                .build();

        assertNotNull(spec);
        assertNotNull(spec.root());
        assertNotNull(spec.selection());

        // Verify structure
        assertTrue(spec.root() instanceof And);
        And rootAnd = (And) spec.root();
        assertEquals(5, rootAnd.getTerms().size());

        // Verify selection
        assertEquals(50, spec.selection().getLimit().intValue());
        assertEquals(2, spec.selection().getOrder().size());
    }

    @Test
    void all_string_fields_in_single_filter() {
        UtxoFilterSpec spec = filter(
                and(
                        address().eq("addr1"),
                        dataHash().ne("0xabc"),
                        inlineDatum().notNull(),
                        referenceScriptHash().eq("script123"),
                        txHash().ne("excluded_tx")
                )
        ).build();

        assertTrue(spec.root() instanceof And);
        And and = (And) spec.root();
        assertEquals(5, and.getTerms().size());

        // Verify all are Comparison nodes with correct field types
        assertTrue(((Comparison) and.getTerms().get(0)).getField() instanceof AddressField);
        assertTrue(((Comparison) and.getTerms().get(1)).getField() instanceof DataHashField);
        assertTrue(((Comparison) and.getTerms().get(2)).getField() instanceof InlineDatumField);
        assertTrue(((Comparison) and.getTerms().get(3)).getField() instanceof ReferenceScriptHashField);
        assertTrue(((Comparison) and.getTerms().get(4)).getField() instanceof TxHashField);
    }

    @Test
    void all_numeric_fields_in_single_filter() {
        UtxoFilterSpec spec = filter(
                and(
                        lovelace().gte(2_000_000),
                        amountUnit("policy123asset456").gt(100),
                        outputIndex().lt(50)
                )
        ).build();

        assertTrue(spec.root() instanceof And);
        And and = (And) spec.root();
        assertEquals(3, and.getTerms().size());

        // Verify all are Comparison nodes
        for (FilterNode term : and.getTerms()) {
            assertTrue(term instanceof Comparison);
            Comparison cmp = (Comparison) term;
            assertTrue(cmp.getField() instanceof AmountQuantityField ||
                      cmp.getField() instanceof OutputIndexField);
        }
    }

    // ========== Edge Cases ==========

    @Test
    void filter_with_single_condition_no_and() {
        UtxoFilterSpec spec = filter(lovelace().gte(1_000_000)).build();

        // Should be a single Comparison, not wrapped in AND
        assertTrue(spec.root() instanceof Comparison);
    }

    @Test
    void empty_and_with_varargs() {
        // Should not throw, creates empty AND
        FilterNode node = and();
        assertTrue(node instanceof And);
        assertEquals(0, ((And) node).getTerms().size());
    }

    @Test
    void empty_or_with_varargs() {
        // Should not throw, creates empty OR
        FilterNode node = or();
        assertTrue(node instanceof Or);
        assertEquals(0, ((Or) node).getTerms().size());
    }

    @Test
    void single_item_and() {
        FilterNode node = and(lovelace().gte(1_000_000));
        assertTrue(node instanceof And);
        assertEquals(1, ((And) node).getTerms().size());
    }

    @Test
    void single_item_or() {
        FilterNode node = or(address().eq("addr1"));
        assertTrue(node instanceof Or);
        assertEquals(1, ((Or) node).getTerms().size());
    }
}
