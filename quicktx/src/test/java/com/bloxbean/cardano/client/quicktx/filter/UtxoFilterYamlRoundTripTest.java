package com.bloxbean.cardano.client.quicktx.filter;

import com.bloxbean.cardano.client.quicktx.filter.ast.*;
import com.bloxbean.cardano.client.quicktx.filter.yaml.UtxoFilterYaml;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UtxoFilterYamlRoundTripTest {

    @Test
    void toNode_and_back_produces_equivalent_spec() {
        FilterNode filter = new And(Arrays.asList(
                new Comparison(AddressField.INSTANCE, CmpOp.EQ, Value.ofString("addr_test1xyz")),
                new Comparison(new AmountQuantityField("lovelace"), CmpOp.GTE, Value.ofInteger(BigInteger.valueOf(2_000_000)))
        ));

        Selection selection = Selection.of(List.of(
                Order.lovelace(Order.Direction.DESC)
        ), 2);

        UtxoFilterSpec spec = ImmutableUtxoFilterSpec.builder(filter)
                .selection(selection)
                .build();

        ObjectNode node = UtxoFilterYaml.toNode(spec);
        UtxoFilterSpec spec2 = UtxoFilterYaml.parseNode(node);

        assertEquals(filter, spec2.root());
        assertNotNull(spec2.selection());
        assertEquals(2, spec2.selection().getLimit());
        assertEquals(1, spec2.selection().getOrder().size());
        assertEquals(Order.Field.LOVELACE, spec2.selection().getOrder().get(0).getField());
        assertEquals(Order.Direction.DESC, spec2.selection().getOrder().get(0).getDirection());
    }
}

