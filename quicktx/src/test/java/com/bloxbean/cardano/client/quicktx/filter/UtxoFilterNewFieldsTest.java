package com.bloxbean.cardano.client.quicktx.filter;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.quicktx.filter.runtime.memory.InMemoryUtxoFilterEngine;
import com.bloxbean.cardano.client.quicktx.filter.yaml.UtxoFilterYaml;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.bloxbean.cardano.client.quicktx.filter.dsl.F.*;
import static com.bloxbean.cardano.client.quicktx.filter.dsl.Filters.and;
import static com.bloxbean.cardano.client.quicktx.filter.dsl.Sel.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for newly added UTXO filter fields:
 * - referenceScriptHash
 * - txHash
 * - outputIndex
 */
class UtxoFilterNewFieldsTest {

    private static Utxo utxo(String tx, int ix, String addr, String refScriptHash) {
        return Utxo.builder()
                .txHash(tx)
                .outputIndex(ix)
                .address(addr)
                .referenceScriptHash(refScriptHash)
                .amount(Arrays.asList(Amount.lovelace(BigInteger.valueOf(1_000_000))))
                .build();
    }

    // ========== referenceScriptHash Tests ==========

    @Test
    void referenceScriptHash_equals() throws IOException {
        String yaml = "referenceScriptHash: \"abc123\"";
        UtxoFilterSpec spec = UtxoFilterYaml.parse(yaml);

        List<Utxo> utxos = new ArrayList<>();
        utxos.add(utxo("tx1", 0, "addr", "abc123"));
        utxos.add(utxo("tx2", 0, "addr", "def456"));
        utxos.add(utxo("tx3", 0, "addr", null));

        List<Utxo> result = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        assertEquals(1, result.size());
        assertEquals("tx1", result.get(0).getTxHash());
    }

    @Test
    void referenceScriptHash_notEquals() throws IOException {
        String yaml = "referenceScriptHash: { ne: \"abc123\" }";
        UtxoFilterSpec spec = UtxoFilterYaml.parse(yaml);

        List<Utxo> utxos = new ArrayList<>();
        utxos.add(utxo("tx1", 0, "addr", "abc123"));
        utxos.add(utxo("tx2", 0, "addr", "def456"));
        utxos.add(utxo("tx3", 0, "addr", null));

        List<Utxo> result = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(u -> u.getTxHash().equals("tx2")));
        assertTrue(result.stream().anyMatch(u -> u.getTxHash().equals("tx3")));
    }

    @Test
    void referenceScriptHash_isNull() throws IOException {
        String yaml = "referenceScriptHash: null";
        UtxoFilterSpec spec = UtxoFilterYaml.parse(yaml);

        List<Utxo> utxos = new ArrayList<>();
        utxos.add(utxo("tx1", 0, "addr", "abc123"));
        utxos.add(utxo("tx2", 0, "addr", null));
        utxos.add(utxo("tx3", 0, "addr", null));

        List<Utxo> result = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        assertEquals(2, result.size());
        assertEquals("tx2", result.get(0).getTxHash());
        assertEquals("tx3", result.get(1).getTxHash());
    }

    @Test
    void referenceScriptHash_notNull() throws IOException {
        String yaml = "referenceScriptHash: { ne: null }";
        UtxoFilterSpec spec = UtxoFilterYaml.parse(yaml);

        List<Utxo> utxos = new ArrayList<>();
        utxos.add(utxo("tx1", 0, "addr", "abc123"));
        utxos.add(utxo("tx2", 0, "addr", null));
        utxos.add(utxo("tx3", 0, "addr", "def456"));

        List<Utxo> result = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(u -> u.getTxHash().equals("tx1")));
        assertTrue(result.stream().anyMatch(u -> u.getTxHash().equals("tx3")));
    }

    @Test
    void referenceScriptHash_dsl() {
        List<Utxo> utxos = new ArrayList<>();
        utxos.add(utxo("tx1", 0, "addr", "abc123"));
        utxos.add(utxo("tx2", 0, "addr", null));
        utxos.add(utxo("tx3", 0, "addr", "def456"));

        // Test eq
        var spec1 = com.bloxbean.cardano.client.quicktx.filter.dsl.Spec.of(
                referenceScriptHash().eq("abc123")
        ).build();
        var result1 = InMemoryUtxoFilterEngine.filter(utxos, spec1.root(), spec1.selection());
        assertEquals(1, result1.size());
        assertEquals("tx1", result1.get(0).getTxHash());

        // Test notNull
        var spec2 = com.bloxbean.cardano.client.quicktx.filter.dsl.Spec.of(
                referenceScriptHash().notNull()
        ).build();
        var result2 = InMemoryUtxoFilterEngine.filter(utxos, spec2.root(), spec2.selection());
        assertEquals(2, result2.size());
    }

    @Test
    void referenceScriptHash_yamlRoundTrip() throws IOException {
        var spec = com.bloxbean.cardano.client.quicktx.filter.dsl.Spec.of(
                referenceScriptHash().notNull()
        ).build();

        var node = UtxoFilterYaml.toNode(spec);
        var spec2 = UtxoFilterYaml.parseNode(node);

        assertEquals(spec.root(), spec2.root());
    }

    // ========== txHash Tests ==========

    @Test
    void txHash_equals() throws IOException {
        String yaml = "txHash: \"abc123\"";
        UtxoFilterSpec spec = UtxoFilterYaml.parse(yaml);

        List<Utxo> utxos = new ArrayList<>();
        utxos.add(utxo("abc123", 0, "addr", null));
        utxos.add(utxo("def456", 0, "addr", null));
        utxos.add(utxo("abc123", 1, "addr", null));

        List<Utxo> result = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(u -> u.getTxHash().equals("abc123")));
    }

    @Test
    void txHash_notEquals() throws IOException {
        String yaml = "txHash: { ne: \"abc123\" }";
        UtxoFilterSpec spec = UtxoFilterYaml.parse(yaml);

        List<Utxo> utxos = new ArrayList<>();
        utxos.add(utxo("abc123", 0, "addr", null));
        utxos.add(utxo("def456", 0, "addr", null));
        utxos.add(utxo("abc123", 1, "addr", null));

        List<Utxo> result = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        assertEquals(1, result.size());
        assertEquals("def456", result.get(0).getTxHash());
    }

    @Test
    void txHash_dsl() {
        List<Utxo> utxos = new ArrayList<>();
        utxos.add(utxo("abc123", 0, "addr", null));
        utxos.add(utxo("def456", 0, "addr", null));
        utxos.add(utxo("abc123", 1, "addr", null));

        var spec = com.bloxbean.cardano.client.quicktx.filter.dsl.Spec.of(
                txHash().eq("abc123")
        ).build();

        var result = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(u -> u.getTxHash().equals("abc123")));
    }

    @Test
    void txHash_yamlRoundTrip() throws IOException {
        var spec = com.bloxbean.cardano.client.quicktx.filter.dsl.Spec.of(
                txHash().eq("abc123")
        ).build();

        var node = UtxoFilterYaml.toNode(spec);
        var spec2 = UtxoFilterYaml.parseNode(node);

        assertEquals(spec.root(), spec2.root());
    }

    // ========== outputIndex Tests ==========

    @Test
    void outputIndex_equals() throws IOException {
        String yaml = "outputIndex: 0";
        UtxoFilterSpec spec = UtxoFilterYaml.parse(yaml);

        List<Utxo> utxos = new ArrayList<>();
        utxos.add(utxo("tx1", 0, "addr", null));
        utxos.add(utxo("tx1", 1, "addr", null));
        utxos.add(utxo("tx1", 2, "addr", null));

        List<Utxo> result = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        assertEquals(1, result.size());
        assertEquals(0, result.get(0).getOutputIndex());
    }

    @Test
    void outputIndex_allOperators() throws IOException {
        List<Utxo> utxos = new ArrayList<>();
        utxos.add(utxo("tx1", 0, "addr", null));
        utxos.add(utxo("tx1", 1, "addr", null));
        utxos.add(utxo("tx1", 2, "addr", null));
        utxos.add(utxo("tx1", 5, "addr", null));

        // gt
        var specGt = UtxoFilterYaml.parse("outputIndex: { gt: 1 }");
        var resultGt = InMemoryUtxoFilterEngine.filter(utxos, specGt.root(), specGt.selection());
        assertEquals(2, resultGt.size());

        // gte
        var specGte = UtxoFilterYaml.parse("outputIndex: { gte: 2 }");
        var resultGte = InMemoryUtxoFilterEngine.filter(utxos, specGte.root(), specGte.selection());
        assertEquals(2, resultGte.size());

        // lt
        var specLt = UtxoFilterYaml.parse("outputIndex: { lt: 2 }");
        var resultLt = InMemoryUtxoFilterEngine.filter(utxos, specLt.root(), specLt.selection());
        assertEquals(2, resultLt.size());

        // lte
        var specLte = UtxoFilterYaml.parse("outputIndex: { lte: 1 }");
        var resultLte = InMemoryUtxoFilterEngine.filter(utxos, specLte.root(), specLte.selection());
        assertEquals(2, resultLte.size());

        // ne
        var specNe = UtxoFilterYaml.parse("outputIndex: { ne: 1 }");
        var resultNe = InMemoryUtxoFilterEngine.filter(utxos, specNe.root(), specNe.selection());
        assertEquals(3, resultNe.size());
    }

    @Test
    void outputIndex_dsl() {
        List<Utxo> utxos = new ArrayList<>();
        utxos.add(utxo("tx1", 0, "addr", null));
        utxos.add(utxo("tx1", 1, "addr", null));
        utxos.add(utxo("tx1", 2, "addr", null));
        utxos.add(utxo("tx1", 5, "addr", null));

        // Test lt
        var spec = com.bloxbean.cardano.client.quicktx.filter.dsl.Spec.of(
                outputIndex().lt(2)
        ).build();

        var result = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(u -> u.getOutputIndex() < 2));
    }

    @Test
    void outputIndex_yamlRoundTrip() throws IOException {
        var spec = com.bloxbean.cardano.client.quicktx.filter.dsl.Spec.of(
                outputIndex().gte(5)
        ).build();

        var node = UtxoFilterYaml.toNode(spec);
        var spec2 = UtxoFilterYaml.parseNode(node);

        assertEquals(spec.root(), spec2.root());
    }

    // ========== Combined Tests ==========

    @Test
    void specificUtxo_byTxHashAndOutputIndex() throws IOException {
        String yaml = String.join("\n",
                "txHash: \"abc123\"",
                "outputIndex: 1"
        );
        UtxoFilterSpec spec = UtxoFilterYaml.parse(yaml);

        List<Utxo> utxos = new ArrayList<>();
        utxos.add(utxo("abc123", 0, "addr", null));
        utxos.add(utxo("abc123", 1, "addr", null));
        utxos.add(utxo("def456", 1, "addr", null));

        List<Utxo> result = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        assertEquals(1, result.size());
        assertEquals("abc123", result.get(0).getTxHash());
        assertEquals(1, result.get(0).getOutputIndex());
    }

    @Test
    void newFields_combinedWithExisting() throws IOException {
        String yaml = String.join("\n",
                "address: \"addr_test1\"",
                "referenceScriptHash: { ne: null }",
                "outputIndex: { lt: 5 }"
        );
        UtxoFilterSpec spec = UtxoFilterYaml.parse(yaml);

        List<Utxo> utxos = new ArrayList<>();
        utxos.add(Utxo.builder().txHash("tx1").outputIndex(0).address("addr_test1")
                .referenceScriptHash("script1").amount(Arrays.asList(Amount.lovelace(BigInteger.valueOf(1_000_000)))).build());
        utxos.add(Utxo.builder().txHash("tx2").outputIndex(1).address("addr_test1")
                .referenceScriptHash(null).amount(Arrays.asList(Amount.lovelace(BigInteger.valueOf(1_000_000)))).build());
        utxos.add(Utxo.builder().txHash("tx3").outputIndex(10).address("addr_test1")
                .referenceScriptHash("script2").amount(Arrays.asList(Amount.lovelace(BigInteger.valueOf(1_000_000)))).build());
        utxos.add(Utxo.builder().txHash("tx4").outputIndex(2).address("addr_other")
                .referenceScriptHash("script3").amount(Arrays.asList(Amount.lovelace(BigInteger.valueOf(1_000_000)))).build());

        List<Utxo> result = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        assertEquals(1, result.size());
        assertEquals("tx1", result.get(0).getTxHash());
    }

    @Test
    void newFields_dslCombined() {
        List<Utxo> utxos = new ArrayList<>();
        utxos.add(utxo("abc123", 0, "addr", "script1"));
        utxos.add(utxo("abc123", 1, "addr", null));
        utxos.add(utxo("def456", 2, "addr", "script2"));

        var spec = com.bloxbean.cardano.client.quicktx.filter.dsl.Spec.of(
                and(
                        txHash().eq("abc123"),
                        referenceScriptHash().notNull()
                )
        ).build();

        var result = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        assertEquals(1, result.size());
        assertEquals(0, result.get(0).getOutputIndex());
    }

    // ========== Ordering Tests ==========

    @Test
    void referenceScriptHash_ordering() throws IOException {
        String yaml = String.join("\n",
                "selection:",
                "  order: [ \"referenceScriptHash asc\" ]"
        );
        UtxoFilterSpec spec = UtxoFilterYaml.parse(yaml);

        List<Utxo> utxos = new ArrayList<>();
        utxos.add(utxo("tx1", 0, "addr", "zzz"));
        utxos.add(utxo("tx2", 0, "addr", "aaa"));
        utxos.add(utxo("tx3", 0, "addr", "mmm"));

        List<Utxo> result = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        assertEquals(3, result.size());
        assertEquals("aaa", result.get(0).getReferenceScriptHash());
        assertEquals("mmm", result.get(1).getReferenceScriptHash());
        assertEquals("zzz", result.get(2).getReferenceScriptHash());
    }

    @Test
    void outputIndex_ordering() throws IOException {
        String yaml = String.join("\n",
                "selection:",
                "  order: [ \"outputIndex desc\" ]",
                "  limit: 2"
        );
        UtxoFilterSpec spec = UtxoFilterYaml.parse(yaml);

        List<Utxo> utxos = new ArrayList<>();
        utxos.add(utxo("tx1", 0, "addr", null));
        utxos.add(utxo("tx1", 5, "addr", null));
        utxos.add(utxo("tx1", 2, "addr", null));

        List<Utxo> result = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        assertEquals(2, result.size());
        assertEquals(5, result.get(0).getOutputIndex());
        assertEquals(2, result.get(1).getOutputIndex());
    }

    @Test
    void newFields_ordering_dsl() {
        List<Utxo> utxos = new ArrayList<>();
        utxos.add(utxo("tx1", 0, "addr", "zzz"));
        utxos.add(utxo("tx2", 5, "addr", "aaa"));
        utxos.add(utxo("tx3", 2, "addr", "mmm"));

        var spec = com.bloxbean.cardano.client.quicktx.filter.dsl.Spec.of(
                referenceScriptHash().notNull()
        ).orderBy(referenceScriptHashAsc(), outputIndexDesc()).build();

        var result = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        assertEquals(3, result.size());
        assertEquals("aaa", result.get(0).getReferenceScriptHash());
        assertEquals(5, result.get(0).getOutputIndex());
    }
}
