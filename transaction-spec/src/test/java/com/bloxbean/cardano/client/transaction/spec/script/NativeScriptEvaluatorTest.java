package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link NativeScriptEvaluator} against the ledger's {@code evalTimelock} (cardano-ledger allegra {@code Scripts.hs}),
 * transliterated in {@link #evalTimelock}: every form and boundary, seeded random scripts, signatures and validity
 * intervals, and every witness script of the real transactions, which the ledger accepted.
 */
class NativeScriptEvaluatorTest {
    private static final String A = "aa".repeat(28);
    private static final String B = "bb".repeat(28);

    private static boolean evaluate(NativeScript script, Set<String> keys, Long invalidBefore, Long invalidHereafter) {
        boolean result = NativeScriptEvaluator.evaluate(script, keys, invalidBefore, invalidHereafter);
        assertThat(result).as("evalTimelock on %s", script).isEqualTo(evalTimelock(script, keys, invalidBefore, invalidHereafter));
        return result;
    }

    @Test
    void signatures() {
        assertThat(evaluate(new ScriptPubkey(A), Set.of(A), null, null)).isTrue();
        assertThat(evaluate(new ScriptPubkey(A), Set.of(B), null, null)).isFalse();
        assertThat(evaluate(new ScriptPubkey(A), Set.of(), null, null)).isFalse();
    }

    @Test
    void allAnyAndAtLeastOfNothing() {
        assertThat(evaluate(new ScriptAll(), Set.of(), null, null)).isTrue();
        assertThat(evaluate(new ScriptAny(), Set.of(), null, null)).isFalse();
        assertThat(evaluate(new ScriptAtLeast(0), Set.of(), null, null)).isTrue();
        assertThat(evaluate(new ScriptAtLeast(-1), Set.of(), null, null)).isTrue();
        assertThat(evaluate(new ScriptAtLeast(1), Set.of(), null, null)).isFalse();
        assertThat(evaluate(new ScriptAtLeast(Long.MIN_VALUE), Set.of(), null, null)).isTrue();
    }

    @Test
    void atLeastCountsSatisfiedScripts() {
        ScriptAtLeast two = new ScriptAtLeast(2).addScript(new ScriptPubkey(A)).addScript(new ScriptPubkey(B));
        assertThat(evaluate(two, Set.of(A), null, null)).isFalse();
        assertThat(evaluate(two, Set.of(A, B), null, null)).isTrue();
        ScriptAtLeast three = new ScriptAtLeast(3).addScript(new ScriptPubkey(A)).addScript(new ScriptPubkey(B));
        assertThat(evaluate(three, Set.of(A, B), null, null)).isFalse(); // m above the number of scripts never holds
        ScriptAtLeast negative = new ScriptAtLeast(-5).addScript(new ScriptPubkey(A));
        assertThat(evaluate(negative, Set.of(), null, null)).isTrue();
        ScriptAtLeast huge = new ScriptAtLeast(BigInteger.TEN.pow(30)).addScript(new ScriptPubkey(A));
        assertThat(evaluate(huge, Set.of(A), null, null)).isFalse();
        ScriptAtLeast unset = new ScriptAtLeast();
        unset.getScripts().add(new ScriptPubkey(A));
        assertThat(NativeScriptEvaluator.evaluate(unset, Set.of(), null, null)).isTrue(); // as serialized: m = 0
    }

    @Test
    void timeStartNeedsALowerBoundAtOrAfterTheSlot() {
        RequireTimeAfter after = new RequireTimeAfter(10);
        assertThat(evaluate(after, Set.of(), null, null)).isFalse(); // lteNegInfty: no lower bound is -infinity
        assertThat(evaluate(after, Set.of(), null, 100L)).isFalse();
        assertThat(evaluate(after, Set.of(), 9L, null)).isFalse();
        assertThat(evaluate(after, Set.of(), 10L, null)).isTrue();
        assertThat(evaluate(after, Set.of(), 11L, null)).isTrue();
        // slots are unsigned 64-bit
        assertThat(evaluate(new RequireTimeAfter(BigInteger.ONE.shiftLeft(63)), Set.of(), -1L, null)).isTrue();
        assertThat(evaluate(new RequireTimeAfter(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)), Set.of(), Long.MAX_VALUE, null)).isFalse();
    }

    @Test
    void timeExpireNeedsAnUpperBoundAtOrBeforeTheSlot() {
        RequireTimeBefore before = new RequireTimeBefore(10);
        assertThat(evaluate(before, Set.of(), null, null)).isFalse(); // ltePosInfty: no upper bound is +infinity
        assertThat(evaluate(before, Set.of(), 0L, null)).isFalse();
        assertThat(evaluate(before, Set.of(), null, 11L)).isFalse();
        assertThat(evaluate(before, Set.of(), null, 10L)).isTrue();
        assertThat(evaluate(before, Set.of(), null, 9L)).isTrue();
        assertThat(evaluate(new RequireTimeBefore(5), Set.of(), null, -1L)).isFalse();
    }

    @Test
    void nestedScripts() {
        NativeScript script = new ScriptAll()
                .addScript(new ScriptAny().addScript(new ScriptPubkey(A)).addScript(new ScriptPubkey(B)))
                .addScript(new ScriptAtLeast(1).addScript(new RequireTimeAfter(5)).addScript(new RequireTimeBefore(50)));
        assertThat(evaluate(script, Set.of(B), 5L, null)).isTrue();
        assertThat(evaluate(script, Set.of(B), null, 50L)).isTrue();
        assertThat(evaluate(script, Set.of(B), 4L, 51L)).isFalse();
        assertThat(evaluate(script, Set.of(), 5L, 50L)).isFalse();
    }

    @Test
    void malformedModelsAreRejected() {
        assertThatThrownBy(() -> NativeScriptEvaluator.evaluate(new ScriptPubkey(), Set.of(), null, null))
                .isInstanceOf(NullPointerException.class);
        ScriptAll withNull = new ScriptAll();
        withNull.getScripts().add(null);
        assertThatThrownBy(() -> NativeScriptEvaluator.evaluate(withNull, Set.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void randomScriptsEvaluateAsTheLedger() throws Exception {
        RandomNativeScript generator = new RandomNativeScript(681_687);
        Random random = new Random(687);
        int held = 0;
        for (int i = 0; i < 5_000; i++) {
            NativeScript script = NativeScript.deserialize((Array) CborSerializationUtil.deserialize(generator.next(5)));
            for (int j = 0; j < 8; j++) {
                Set<String> keys = new HashSet<>();
                for (String key : RandomNativeScript.KEY_HASHES)
                    if (random.nextBoolean())
                        keys.add(key);
                Long invalidBefore = random.nextInt(4) == 0 ? null : (long) random.nextInt(22);
                Long invalidHereafter = random.nextInt(4) == 0 ? null : (long) random.nextInt(22);
                if (evaluate(script, keys, invalidBefore, invalidHereafter))
                    held++;
            }
        }
        assertThat(held).isBetween(1_000, 39_000);
    }

    /**
     * The ledger evaluated every witness native script of every real transaction (from Alonzo on, a script that is not
     * needed is rejected, so every witness script is evaluated), so each holds for the transaction's vkey witnesses and
     * validity interval.
     */
    @Test
    void realWitnessScriptsHold() throws Exception {
        int evaluated = 0;
        int needSignatures = 0;
        for (NativeScriptCorpus.WitnessScripts tx : NativeScriptCorpus.witnessScripts()) {
            for (byte[] cbor : tx.scripts()) {
                NativeScript script = NativeScript.deserialize((Array) CborSerializationUtil.deserialize(cbor));
                assertThat(NativeScriptEvaluator.evaluate(script, tx.vkeyHashes(), tx.invalidBefore(), tx.invalidHereafter()))
                        .as(tx.toString()).isTrue();
                if (!NativeScriptEvaluator.evaluate(script, Set.of(), tx.invalidBefore(), tx.invalidHereafter()))
                    needSignatures++;
                evaluated++;
            }
        }
        assertThat(evaluated).isGreaterThan(10);
        assertThat(needSignatures).isGreaterThan(evaluated / 2);
        System.out.println("real witness native scripts evaluated: " + evaluated + ", failing without signatures: " + needSignatures);
    }

    // evalTimelock, transliterated (recursive, for shallow scripts only).
    static boolean evalTimelock(NativeScript script, Set<String> vhks, Long txStart, Long txExp) {
        if (script instanceof ScriptPubkey)
            return vhks.contains(((ScriptPubkey) script).getKeyHash());
        if (script instanceof RequireTimeAfter)
            return txStart != null && ((RequireTimeAfter) script).getSlot().compareTo(unsigned(txStart)) <= 0;
        if (script instanceof RequireTimeBefore)
            return txExp != null && unsigned(txExp).compareTo(((RequireTimeBefore) script).getSlot()) <= 0;
        if (script instanceof ScriptAll)
            return ((ScriptAll) script).getScripts().stream().allMatch(s -> evalTimelock(s, vhks, txStart, txExp));
        if (script instanceof ScriptAny)
            return ((ScriptAny) script).getScripts().stream().anyMatch(s -> evalTimelock(s, vhks, txStart, txExp));
        ScriptAtLeast atLeast = (ScriptAtLeast) script;
        return isValidMOf(atLeast.getRequired(), atLeast.getScripts(), 0, vhks, txStart, txExp);
    }

    private static boolean isValidMOf(BigInteger n, List<NativeScript> ts, int i, Set<String> vhks, Long txStart, Long txExp) {
        if (i == ts.size())
            return n.signum() <= 0;
        return n.signum() <= 0 || (evalTimelock(ts.get(i), vhks, txStart, txExp)
                ? isValidMOf(n.subtract(BigInteger.ONE), ts, i + 1, vhks, txStart, txExp)
                : isValidMOf(n, ts, i + 1, vhks, txStart, txExp));
    }

    private static BigInteger unsigned(long slot) {
        return new BigInteger(Long.toUnsignedString(slot));
    }
}
