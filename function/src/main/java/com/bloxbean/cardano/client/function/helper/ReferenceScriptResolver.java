package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.api.util.ReferenceScriptUtil;
import com.bloxbean.cardano.client.function.TxBuilder;

/**
 * Helper class to return a {@link TxBuilder} to resolve reference scripts in tx's reference inputs
 */
public class ReferenceScriptResolver {

    public static TxBuilder resolveReferenceScript() {
        return (context, txn) -> {
            var refInputs = txn.getBody().getReferenceInputs();
            if (refInputs == null || refInputs.isEmpty()) {
                return;
            }

            ReferenceScriptUtil.resolveReferenceScripts(context.getUtxoSupplier(), context.getScriptSupplier(), txn)
                    .forEach(script -> {
                        if (script != null) {
                            context.addRefScripts(script);
                        }
                    });
        };
    }
}
