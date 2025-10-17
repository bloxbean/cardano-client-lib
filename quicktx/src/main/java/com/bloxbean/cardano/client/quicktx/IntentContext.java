package com.bloxbean.cardano.client.quicktx;

import lombok.Builder;
import lombok.Data;

/**
 * Execution context for intention processing in the self-processing architecture.
 * Provides only runtime addresses needed by intentions during building.
 *
 * IntentContext is different from QuickTxBuilder.TxContext:
 * - IntentContext: Runtime execution data for intention processing
 * - TxContext: Transaction building configuration (fee payer, signers, etc.)
 */
@Data
@Builder
public class IntentContext {

    /**
     * Default from address for intentions that need a source address.
     */
    private String fromAddress;

    /**
     * Default change address for transaction building.
     */
    private String changeAddress;

    /**
     * Factory method to create empty IntentContext.
     */
    public static IntentContext empty() {
        return IntentContext.builder().build();
    }
}
