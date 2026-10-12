package com.bloxbean.cardano.client.transaction.spec.script;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Evaluates a native script as the ledger does ({@code evalTimelock}, cardano-ledger allegra {@code Scripts.hs}),
 * without recursion, so scripts of any nesting depth evaluate on any thread:
 * <ul>
 *     <li>{@link ScriptPubkey}: its key hash is one of the transaction's vkey witness key hashes;</li>
 *     <li>{@link ScriptAll}: every sub-script holds (true when there are none);</li>
 *     <li>{@link ScriptAny}: some sub-script holds (false when there are none);</li>
 *     <li>{@link ScriptAtLeast}: at least {@code required} sub-scripts hold, so a {@code required} of 0 or less
 *     always holds and one above the number of sub-scripts never does;</li>
 *     <li>{@link RequireTimeAfter} (RequireTimeStart): the transaction has a lower validity bound, and the script's
 *     slot is at most that bound;</li>
 *     <li>{@link RequireTimeBefore} (RequireTimeExpire): the transaction has an upper validity bound
 *     (invalid-hereafter), and that bound is at most the script's slot.</li>
 * </ul>
 * A script received in a transaction is decoded from its original bytes with
 * {@code NativeScript.deserialize((Array) CborSerializationUtil.deserialize(bytes))}.
 */
public final class NativeScriptEvaluator {

    private NativeScriptEvaluator() {
    }

    /**
     * @param script           the script
     * @param vkeyHashesHex    the key hashes (Blake2b-224 of the verification key, lower-case hex) of the
     *                         transaction's vkey witnesses
     * @param invalidBefore    the transaction's lower validity bound (slot), or null if it has none
     * @param invalidHereafter the transaction's upper validity bound (slot), or null if it has none
     * @return whether the script holds
     */
    public static boolean evaluate(NativeScript script, Set<String> vkeyHashesHex, Long invalidBefore, Long invalidHereafter) {
        Objects.requireNonNull(vkeyHashesHex, "vkeyHashesHex");
        Frame top = null;
        NativeScript current = script;
        while (true) {
            boolean holds;
            List<NativeScript> children = NativeScriptCodec.scripts(current);
            if (children != null) {
                Frame frame = new Frame(top, current, children);
                if (frame.decided == null) {
                    top = frame;
                    current = frame.next();
                    continue;
                }
                holds = frame.decided;
            } else {
                holds = holds(current, vkeyHashesHex, invalidBefore, invalidHereafter);
            }
            // hand the result up until a frame needs another sub-script
            while (true) {
                if (top == null)
                    return holds;
                top.accept(holds);
                if (top.decided == null) {
                    current = top.next();
                    break;
                }
                holds = top.decided;
                top = top.parent;
            }
        }
    }

    private static boolean holds(NativeScript script, Set<String> vkeyHashesHex, Long invalidBefore, Long invalidHereafter) {
        if (script instanceof ScriptPubkey) {
            String keyHash = Objects.requireNonNull(((ScriptPubkey) script).getKeyHash(), "ScriptPubkey key hash");
            return vkeyHashesHex.contains(keyHash);
        }
        if (script instanceof RequireTimeAfter) {
            BigInteger slot = Objects.requireNonNull(((RequireTimeAfter) script).getSlot(), "RequireTimeAfter slot");
            return invalidBefore != null && slot.compareTo(unsigned(invalidBefore)) <= 0;
        }
        if (script instanceof RequireTimeBefore) {
            BigInteger slot = Objects.requireNonNull(((RequireTimeBefore) script).getSlot(), "RequireTimeBefore slot");
            return invalidHereafter != null && unsigned(invalidHereafter).compareTo(slot) <= 0;
        }
        throw new IllegalArgumentException("Not a native script that can be evaluated: " + script);
    }

    // Slots are unsigned 64-bit numbers.
    private static BigInteger unsigned(long slot) {
        return slot >= 0 ? BigInteger.valueOf(slot) : new BigInteger(Long.toUnsignedString(slot));
    }

    // An all, any or atLeast being evaluated: decided is set as soon as the result is known, and the remaining
    // sub-scripts are skipped, as the ledger does.
    private static final class Frame {
        final Frame parent;
        private final NativeScript script;
        private final List<NativeScript> scripts;
        private int index;
        private BigInteger stillRequired;
        Boolean decided;

        Frame(Frame parent, NativeScript script, List<NativeScript> scripts) {
            this.parent = parent;
            this.script = script;
            this.scripts = scripts;
            if (script instanceof ScriptAtLeast) {
                BigInteger required = ((ScriptAtLeast) script).getRequired();
                stillRequired = required != null ? required : BigInteger.ZERO;
                if (stillRequired.signum() <= 0)
                    decided = true;
            }
            if (decided == null && scripts.isEmpty())
                decided = script instanceof ScriptAll;
        }

        NativeScript next() {
            return scripts.get(index++);
        }

        void accept(boolean holds) {
            if (script instanceof ScriptAll) {
                if (!holds)
                    decided = false;
            } else if (script instanceof ScriptAny) {
                if (holds)
                    decided = true;
            } else if (holds) {
                stillRequired = stillRequired.subtract(BigInteger.ONE);
                if (stillRequired.signum() <= 0)
                    decided = true;
            }
            if (decided == null && index == scripts.size())
                decided = script instanceof ScriptAll; // all held; none of any held; too few of atLeast held
        }
    }
}
