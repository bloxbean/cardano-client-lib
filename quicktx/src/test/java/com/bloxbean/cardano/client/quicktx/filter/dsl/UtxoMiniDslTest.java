package com.bloxbean.cardano.client.quicktx.filter.dsl;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.quicktx.filter.UtxoFilterSpec;
import com.bloxbean.cardano.client.quicktx.filter.ast.FilterNode;
import com.bloxbean.cardano.client.quicktx.filter.runtime.memory.InMemoryUtxoFilterEngine;
import com.bloxbean.cardano.client.quicktx.filter.yaml.UtxoFilterYaml;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static com.bloxbean.cardano.client.quicktx.filter.dsl.UtxoFilters.*;
import static org.junit.jupiter.api.Assertions.*;

class UtxoMiniDslTest {

    private static Utxo utxo(String tx, int ix, String address, String dataHash, long lovelace) {
        return Utxo.builder()
                .txHash(tx)
                .outputIndex(ix)
                .address(address)
                .dataHash(dataHash)
                .amount(Arrays.asList(Amount.lovelace(BigInteger.valueOf(lovelace))))
                .build();
    }

    @Test
    void simple_lovelace_and_dataHash_with_order_limit() throws IOException {
        FilterNode n = and(
                lovelace().gte(2_000_000),
                dataHash().eq("0xabc")
        );

        UtxoFilterSpec spec = Spec.of(n)
                .orderBy(lovelaceDesc())
                .limit(2)
                .build();

        // Equivalent YAML
        String y = String.join("\n",
                "lovelace: { gte: 2000000 }",
                "dataHash: \"0xabc\"",
                "selection:",
                "  order: [ \"lovelace desc\" ]",
                "  limit: 2");
        UtxoFilterSpec parsed = UtxoFilterYaml.parse(y);

        assertEquals(parsed.root(), spec.root());
        assertNotNull(spec.selection());
        assertEquals(2, spec.selection().getLimit().intValue());

        // Evaluate
        List<Utxo> utxos = Arrays.asList(
                utxo("tx1", 0, "addr", "0xabc", 2_000_000),
                utxo("tx2", 0, "addr", "0xabc", 3_000_000),
                utxo("tx3", 0, "addr", "0xdef", 4_000_000)
        );
        List<Utxo> result = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        assertEquals(2, result.size());
        assertEquals("tx2", result.get(0).getTxHash());
        assertEquals("tx1", result.get(1).getTxHash());
    }

    @Test
    void nested_or_and_example() {
        FilterNode n = or(
                and(address().eq("addr1"), lovelace().gte(1_000_000)),
                and(inlineDatum().notNull(), lovelace().gte(2_000_000))
        );

        UtxoFilterSpec spec = Spec.of(n).limitAll().build();
        assertNotNull(spec.root());
    }
}

