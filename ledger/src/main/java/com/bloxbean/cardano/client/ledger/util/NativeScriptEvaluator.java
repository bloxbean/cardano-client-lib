package com.bloxbean.cardano.client.ledger.util;

import com.bloxbean.cardano.client.transaction.spec.script.*;

import java.util.Set;

/**
 * Evaluates native scripts against provided VKey witness hashes and validity interval.
 * <p>
 * Evaluation rules:
 * <ul>
 *   <li>{@link ScriptPubkey} — key hash must be in the witness set</li>
 *   <li>{@link ScriptAll} — all children must evaluate true (empty = true)</li>
 *   <li>{@link ScriptAny} — at least one child must evaluate true (empty = false)</li>
 *   <li>{@link ScriptAtLeast} — at least N children must evaluate true</li>
 *   <li>{@link RequireTimeAfter} — tx validityStartInterval ≥ script's slot</li>
 *   <li>{@link RequireTimeBefore} — tx ttl ≤ script's slot</li>
 * </ul>
 * <p>
 * Reference: Haskell Shelley.runNativeScript, Scalus NativeScriptsValidator
 */
public class NativeScriptEvaluator {

    private NativeScriptEvaluator() {}

    /**
     * Evaluate a native script.
     *
     * @param script           the native script to evaluate
     * @param witnessKeyHashes set of VKey hashes (hex) present in the transaction witnesses
     * @param validityStart    the transaction's validity start interval (null if not set)
     * @param ttl              the transaction's TTL (null if not set)
     * @return true if the script is satisfied
     */
    public static boolean evaluate(NativeScript script,
                                   Set<String> witnessKeyHashes,
                                   Long validityStart,
                                   Long ttl) {
        if (script instanceof ScriptPubkey s) {
            return witnessKeyHashes.contains(s.getKeyHash());

        } else if (script instanceof ScriptAll s) {
            if (s.getScripts() == null || s.getScripts().isEmpty()) return true;
            return s.getScripts().stream()
                    .allMatch(child -> evaluate(child, witnessKeyHashes, validityStart, ttl));

        } else if (script instanceof ScriptAny s) {
            if (s.getScripts() == null || s.getScripts().isEmpty()) return false;
            return s.getScripts().stream()
                    .anyMatch(child -> evaluate(child, witnessKeyHashes, validityStart, ttl));

        } else if (script instanceof ScriptAtLeast s) {
            if (s.getScripts() == null) return s.getRequired().intValue() <= 0;
            long satisfied = s.getScripts().stream()
                    .filter(child -> evaluate(child, witnessKeyHashes, validityStart, ttl))
                    .count();
            return satisfied >= s.getRequired().longValue();

        } else if (script instanceof RequireTimeAfter s) {
            // Script valid after slot S: tx must prove lower bound ≥ S
            // validityStartInterval is the lower bound of the validity interval
            return validityStart != null && validityStart >= s.getSlot().longValue();

        } else if (script instanceof RequireTimeBefore s) {
            // Script valid before slot S: tx must prove upper bound ≤ S
            // ttl is the upper bound of the validity interval
            return ttl != null && ttl <= s.getSlot().longValue();
        }

        return false;
    }
}
