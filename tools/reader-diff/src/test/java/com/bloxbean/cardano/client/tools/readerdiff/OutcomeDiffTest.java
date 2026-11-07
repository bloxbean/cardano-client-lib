package com.bloxbean.cardano.client.tools.readerdiff;

import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.transaction.raw.RawBlock;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutcomeDiffTest {
    private static final byte[] NO_INPUT = new byte[0];

    private static OutcomeDiff.Result compare(String oldOutcome, String newOutcome, String... allowed) {
        return OutcomeDiff.compare("probe", NO_INPUT, oldOutcome, newOutcome, allow(allowed));
    }

    private static Allow allow(String... entries) {
        return Allow.of(List.of(entries));
    }

    @Test
    void findsOneChangedByteInANestedFieldOfARealTransaction() throws Exception {
        byte[] txBytes = RawBlock.of(Fixtures.block()).txBytes(0);
        CanonicalJson json = new CanonicalJson();
        String original = "A:" + json.write(Transaction.deserialize(txBytes));

        Transaction changed = Transaction.deserialize(txBytes);
        changed.getWitnessSet().getVkeyWitnesses().get(0).getVkey()[31] ^= 1;
        OutcomeDiff.Result result = compare(original, "A:" + json.write(changed));

        assertEquals(OutcomeDiff.DIFFERENT, result.pair());
        assertEquals("witnessSet.vkeyWitnesses[0].vkey", result.first().path());
        assertEquals(List.of("witnessSet.vkeyWitnesses[0].vkey"), result.unlisted());
        assertEquals(OutcomeDiff.EQUAL, compare(original, original).pair());
    }

    @Test
    void classifiesOutcomePairs() {
        assertEquals("A/R", compare("A:{}", "R:java.lang.Exception").pair());
        assertEquals("R/A", compare("R:java.lang.Exception", "A:{}").pair());
        assertEquals("R/R", compare("R:java.lang.Exception", "R:java.lang.Error").pair());
        assertEquals("C/C", compare("C:java.lang.StackOverflowError", "C:java.lang.StackOverflowError").pair());
        assertEquals("A/C", compare("A:{}", "C:java.lang.StackOverflowError").pair());
        assertEquals("U/U", compare("U:x", "U:x").pair());
    }

    @Test
    void reportsAMissingFieldAndAShorterArray() {
        OutcomeDiff.Result missing = compare("A:{\"a\":{\"b\":1,\"c\":2}}", "A:{\"a\":{\"b\":1}}");
        assertEquals("a.c", missing.first().path());
        assertEquals("<missing>", missing.first().newValue());

        OutcomeDiff.Result shorter = compare("A:{\"a\":[1,2]}", "A:{\"a\":[1]}");
        assertEquals("a[1]", shorter.first().path());
    }

    @Test
    void doesNotDropAnAllowedField() {
        OutcomeDiff.Result result = compare("A:{\"h\":{\"hash\":\"aa\"}}", "A:{\"h\":{\"hash\":\"bb\"}}", "h.hash");
        assertEquals(OutcomeDiff.ALLOWED, result.pair());
        assertEquals(List.of("h.hash"), result.allowed());
    }

    @Test
    void anUnlistedDifferenceNextToAnAllowedOneStillCounts() {
        OutcomeDiff.Result result = compare("A:{\"h\":\"aa\",\"x\":1}", "A:{\"h\":\"bb\",\"x\":2}", "h");
        assertEquals(OutcomeDiff.DIFFERENT, result.pair());
        assertEquals("x", result.first().path());
        assertEquals(List.of("h"), result.allowed());
    }

    @Test
    void anAllowedPathMatchesAnyArrayIndex() {
        OutcomeDiff.Result result = compare("A:{\"a\":[{\"h\":1},{\"h\":2}]}", "A:{\"a\":[{\"h\":1},{\"h\":3}]}",
                "a[*].h");
        assertEquals(OutcomeDiff.ALLOWED, result.pair());
        assertEquals(List.of("a[*].h"), result.allowed());
    }

    @Test
    void checksAnExpectedValueWhetherOrNotTheSidesDiffer() throws Exception {
        byte[] block = Fixtures.block();
        String hash = HexFormat.of().formatHex(Blake2bUtil.blake2bHash256(Blocks.headerBytes(block)));
        String wrong = "00".repeat(32);
        Allow allow = allow("h.hash expected=block-header-hash");

        OutcomeDiff.Result corrected = OutcomeDiff.compare("yaci-block", block, blockHash(wrong), blockHash(hash), allow);
        assertEquals(OutcomeDiff.ALLOWED, corrected.pair());
        assertTrue(corrected.checks().getFirst().newMatches());
        assertEquals(false, corrected.checks().getFirst().oldMatches());

        OutcomeDiff.Result broken = OutcomeDiff.compare("yaci-block", block, blockHash(hash), blockHash(wrong), allow);
        assertEquals(OutcomeDiff.EXPECTED_MISMATCH, broken.pair());
        assertEquals("h.hash", broken.first().path());

        OutcomeDiff.Result bothRight = OutcomeDiff.compare("yaci-block", block, blockHash(hash), blockHash(hash), allow);
        assertEquals(OutcomeDiff.EQUAL, bothRight.pair());
        assertTrue(bothRight.checks().getFirst().newMatches());

        OutcomeDiff.Result bothWrong = OutcomeDiff.compare("yaci-block", block, blockHash(wrong), blockHash(wrong), allow);
        assertEquals(OutcomeDiff.EXPECTED_MISMATCH, bothWrong.pair());

        OutcomeDiff.Result otherProbe = OutcomeDiff.compare("ccl-tx", block, blockHash(wrong), blockHash(wrong), allow);
        assertEquals(OutcomeDiff.EQUAL, otherProbe.pair());
        assertEquals(List.of(), otherProbe.checks());

        OutcomeDiff.Result noHeader = OutcomeDiff.compare("yaci-block", new byte[]{1}, blockHash(wrong), blockHash(wrong),
                allow);
        assertEquals(OutcomeDiff.EQUAL, noHeader.pair());
        assertEquals(null, noHeader.checks().getFirst().expected());
    }

    private static String blockHash(String hash) {
        return "A:{\"h\":{\"hash\":\"" + hash + "\"}}";
    }
}
