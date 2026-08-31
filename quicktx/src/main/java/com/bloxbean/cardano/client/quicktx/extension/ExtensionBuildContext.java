package com.bloxbean.cardano.client.quicktx.extension;

import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.quicktx.AbstractTx;
import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Build-local services and reservations shared by all registered extensions. */
@Getter
public final class ExtensionBuildContext {
    private final List<AbstractTx<?>> transactions;
    private final UtxoSupplier utxoSupplier;
    private final ProtocolParamsSupplier protocolParamsSupplier;
    private final Set<String> reservedInputs = new LinkedHashSet<>();

    public ExtensionBuildContext(AbstractTx<?>[] transactions, UtxoSupplier utxoSupplier,
                                 ProtocolParamsSupplier protocolParamsSupplier) {
        this.transactions = List.copyOf(Arrays.asList(transactions));
        this.utxoSupplier = utxoSupplier;
        this.protocolParamsSupplier = protocolParamsSupplier;
    }

    /** Reserve a transaction input for this build. Returns false if another participant owns it. */
    public boolean reserveInput(String txHash, int outputIndex) {
        return reservedInputs.add(txHash.toLowerCase() + "#" + outputIndex);
    }

    public boolean isReserved(String txHash, int outputIndex) {
        return reservedInputs.contains(txHash.toLowerCase() + "#" + outputIndex);
    }

    public Set<String> getReservedInputs() {
        return Collections.unmodifiableSet(reservedInputs);
    }
}
