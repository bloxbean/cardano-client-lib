package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.api.util.ReferenceScriptUtil;
import com.bloxbean.cardano.client.function.TxBuilder;

/**
 * Helper class to return a {@link TxBuilder} to resolve reference scripts in tx's reference inputs and inputs
 */
public class ReferenceScriptResolver {

    public static TxBuilder resolveReferenceScript() {
        return (context, txn) -> {
            var inputUtxos = context.getUtxos();

            ReferenceScriptUtil.resolveReferenceScripts(context.getUtxoSupplier(), context.getScriptSupplier(), txn, inputUtxos)
                    .forEach(script -> {
                        if (script != null) {
                            context.addRefScripts(script);
                        }
                    });
        };
    }
}
