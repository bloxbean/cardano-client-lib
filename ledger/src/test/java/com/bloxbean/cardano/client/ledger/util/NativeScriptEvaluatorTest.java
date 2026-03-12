package com.bloxbean.cardano.client.ledger.util;

import com.bloxbean.cardano.client.transaction.spec.script.*;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NativeScriptEvaluatorTest {

    private static final String KEY_HASH_1 = "aabbccdd00112233445566778899aabb00112233445566778899aabb";
    private static final String KEY_HASH_2 = "11223344556677889900aabbccddeeff00112233445566778899aabb";
    private static final String KEY_HASH_3 = "ff223344556677889900aabbccddeeff00112233445566778899aabb";

    @Test
    void scriptPubkey_keyPresent_shouldPass() {
        ScriptPubkey script = new ScriptPubkey(KEY_HASH_1);
        assertThat(NativeScriptEvaluator.evaluate(script, Set.of(KEY_HASH_1), null, null)).isTrue();
    }

    @Test
    void scriptPubkey_keyAbsent_shouldFail() {
        ScriptPubkey script = new ScriptPubkey(KEY_HASH_1);
        assertThat(NativeScriptEvaluator.evaluate(script, Set.of(KEY_HASH_2), null, null)).isFalse();
    }

    @Test
    void scriptAll_allPresent_shouldPass() {
        ScriptAll script = new ScriptAll();
        script.addScript(new ScriptPubkey(KEY_HASH_1));
        script.addScript(new ScriptPubkey(KEY_HASH_2));

        assertThat(NativeScriptEvaluator.evaluate(script, Set.of(KEY_HASH_1, KEY_HASH_2), null, null))
                .isTrue();
    }

    @Test
    void scriptAll_oneMissing_shouldFail() {
        ScriptAll script = new ScriptAll();
        script.addScript(new ScriptPubkey(KEY_HASH_1));
        script.addScript(new ScriptPubkey(KEY_HASH_2));

        assertThat(NativeScriptEvaluator.evaluate(script, Set.of(KEY_HASH_1), null, null)).isFalse();
    }

    @Test
    void scriptAll_empty_shouldPass() {
        ScriptAll script = new ScriptAll();
        assertThat(NativeScriptEvaluator.evaluate(script, Set.of(), null, null)).isTrue();
    }

    @Test
    void scriptAny_onePresent_shouldPass() {
        ScriptAny script = new ScriptAny();
        script.addScript(new ScriptPubkey(KEY_HASH_1));
        script.addScript(new ScriptPubkey(KEY_HASH_2));

        assertThat(NativeScriptEvaluator.evaluate(script, Set.of(KEY_HASH_2), null, null)).isTrue();
    }

    @Test
    void scriptAny_nonePresent_shouldFail() {
        ScriptAny script = new ScriptAny();
        script.addScript(new ScriptPubkey(KEY_HASH_1));
        script.addScript(new ScriptPubkey(KEY_HASH_2));

        assertThat(NativeScriptEvaluator.evaluate(script, Set.of(KEY_HASH_3), null, null)).isFalse();
    }

    @Test
    void scriptAny_empty_shouldFail() {
        ScriptAny script = new ScriptAny();
        assertThat(NativeScriptEvaluator.evaluate(script, Set.of(), null, null)).isFalse();
    }

    @Test
    void scriptAtLeast_enoughPresent_shouldPass() {
        ScriptAtLeast script = new ScriptAtLeast(BigInteger.valueOf(2));
        script.addScript(new ScriptPubkey(KEY_HASH_1));
        script.addScript(new ScriptPubkey(KEY_HASH_2));
        script.addScript(new ScriptPubkey(KEY_HASH_3));

        assertThat(NativeScriptEvaluator.evaluate(script, Set.of(KEY_HASH_1, KEY_HASH_3), null, null))
                .isTrue();
    }

    @Test
    void scriptAtLeast_notEnough_shouldFail() {
        ScriptAtLeast script = new ScriptAtLeast(BigInteger.valueOf(2));
        script.addScript(new ScriptPubkey(KEY_HASH_1));
        script.addScript(new ScriptPubkey(KEY_HASH_2));
        script.addScript(new ScriptPubkey(KEY_HASH_3));

        assertThat(NativeScriptEvaluator.evaluate(script, Set.of(KEY_HASH_1), null, null)).isFalse();
    }

    @Test
    void requireTimeAfter_lowerBoundSufficient_shouldPass() {
        RequireTimeAfter script = new RequireTimeAfter(BigInteger.valueOf(100));
        // validityStart >= 100
        assertThat(NativeScriptEvaluator.evaluate(script, Set.of(), 100L, null)).isTrue();
        assertThat(NativeScriptEvaluator.evaluate(script, Set.of(), 200L, null)).isTrue();
    }

    @Test
    void requireTimeAfter_lowerBoundInsufficient_shouldFail() {
        RequireTimeAfter script = new RequireTimeAfter(BigInteger.valueOf(100));
        assertThat(NativeScriptEvaluator.evaluate(script, Set.of(), 99L, null)).isFalse();
    }

    @Test
    void requireTimeAfter_noLowerBound_shouldFail() {
        RequireTimeAfter script = new RequireTimeAfter(BigInteger.valueOf(100));
        assertThat(NativeScriptEvaluator.evaluate(script, Set.of(), null, null)).isFalse();
    }

    @Test
    void requireTimeBefore_upperBoundSufficient_shouldPass() {
        RequireTimeBefore script = new RequireTimeBefore(BigInteger.valueOf(500));
        // ttl <= 500
        assertThat(NativeScriptEvaluator.evaluate(script, Set.of(), null, 500L)).isTrue();
        assertThat(NativeScriptEvaluator.evaluate(script, Set.of(), null, 300L)).isTrue();
    }

    @Test
    void requireTimeBefore_upperBoundInsufficient_shouldFail() {
        RequireTimeBefore script = new RequireTimeBefore(BigInteger.valueOf(500));
        assertThat(NativeScriptEvaluator.evaluate(script, Set.of(), null, 501L)).isFalse();
    }

    @Test
    void requireTimeBefore_noUpperBound_shouldFail() {
        RequireTimeBefore script = new RequireTimeBefore(BigInteger.valueOf(500));
        assertThat(NativeScriptEvaluator.evaluate(script, Set.of(), null, null)).isFalse();
    }

    @Test
    void nestedScript_complex() {
        // all(sig(key1), any(sig(key2), sig(key3)))
        ScriptAll script = new ScriptAll();
        script.addScript(new ScriptPubkey(KEY_HASH_1));

        ScriptAny inner = new ScriptAny();
        inner.addScript(new ScriptPubkey(KEY_HASH_2));
        inner.addScript(new ScriptPubkey(KEY_HASH_3));
        script.addScript(inner);

        // key1 + key3 satisfies
        assertThat(NativeScriptEvaluator.evaluate(script, Set.of(KEY_HASH_1, KEY_HASH_3), null, null))
                .isTrue();

        // key1 only — inner any fails
        assertThat(NativeScriptEvaluator.evaluate(script, Set.of(KEY_HASH_1), null, null))
                .isFalse();
    }
}
