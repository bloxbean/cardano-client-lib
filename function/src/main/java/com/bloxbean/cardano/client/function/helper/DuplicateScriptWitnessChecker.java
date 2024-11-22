package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.spec.Script;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.Try;

import java.util.List;
import java.util.Set;

/**
 * Helper class to provide a {@link TxBuilder} to remove duplicate script witnesses from the transaction.
 * This is useful when you have the same script in witness set and as reference script in transaction input
 * or transaction reference input.
 * In case of such scenario or duplicate witness script, tx submission will fail with error. (ExtraneousScriptWitnessesUTXOW)
 */
public class DuplicateScriptWitnessChecker {

    /**
     * Returns a {@link TxBuilder} to remove duplicate script witnesses from the transaction.
     * It checks if the script is present as reference script bytes in a transaction input or reference input.
     * If yes, it removes the script from witness set.
     *
     * @return TxBuilder
     */
    public static TxBuilder removeDuplicateScriptWitnesses() {
        return (context, txn) -> {
            Set<String> refScriptHashes = context.getRefScriptHashes();

            //Remove duplicate script from witness set
            if (refScriptHashes != null && !refScriptHashes.isEmpty()) {
                removeDuplicateScripts(txn.getWitnessSet().getNativeScripts(), refScriptHashes);
                removeDuplicateScripts(txn.getWitnessSet().getPlutusV1Scripts(), refScriptHashes);
                removeDuplicateScripts(txn.getWitnessSet().getPlutusV2Scripts(), refScriptHashes);
                removeDuplicateScripts(txn.getWitnessSet().getPlutusV3Scripts(), refScriptHashes);
            }

        };
    }

    private static void removeDuplicateScripts(List<? extends Script> scripts, Set<String> refScriptHashes) {
        if (scripts == null || scripts.isEmpty())
            return;

        scripts.removeIf(plutusScript ->
                Try.of(() -> refScriptHashes.contains(HexUtil.encodeHexString(plutusScript.getScriptHash())))
                        .getOrElse(false)
        );
    }
}
