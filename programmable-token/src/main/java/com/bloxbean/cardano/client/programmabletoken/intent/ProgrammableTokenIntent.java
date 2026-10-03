package com.bloxbean.cardano.client.programmabletoken.intent;

import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenTx;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionIntent;

/** Typed semantic intent owned by the Programmable Token extension. */
public interface ProgrammableTokenIntent extends ExtensionIntent {
    @Override
    default String getExtensionId() {
        return ProgrammableTokenTx.EXTENSION_ID;
    }
}
