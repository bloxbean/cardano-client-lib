package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.common.MinAdaCalculator;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

import java.math.BigInteger;

/**
 * Input of a {@link ChangeSplitStrategy}.
 */
@Getter
@Builder
public class ChangeSplitRequest {
    private static final BigInteger ONE_ADA = BigInteger.valueOf(1_000_000L);

    /**
     * Change address. All pieces are sent to this address.
     */
    @NonNull
    private final String address;

    /**
     * Change value to split. Already excludes the fee reserve of {@link Unfrack}.
     */
    @NonNull
    private final Value change;

    @NonNull
    private final ProtocolParams protocolParams;

    /**
     * Transaction being built, for strategies that look at payment outputs. Must not be modified. May be null.
     */
    private final Transaction transaction;

    /**
     * UTxO supplier, for strategies that look at the wallet's existing UTxOs. May be null.
     */
    private final UtxoSupplier utxoSupplier;

    /**
     * @return min-ada of an output with the given value at the change address
     */
    public BigInteger minAda(Value value) {
        return new MinAdaCalculator(protocolParams).calculateMinAda(new TransactionOutput(address, value));
    }

    /**
     * @return min-ada of an ADA-only output at the change address
     */
    public BigInteger adaOnlyMinAda() {
        return minAda(Value.fromCoin(ONE_ADA));
    }
}
