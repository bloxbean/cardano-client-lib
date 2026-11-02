package com.bloxbean.cardano.client.quicktx.extension;

import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.quicktx.AbstractTx;
import com.bloxbean.cardano.client.quicktx.intent.TxIntent;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Build-local services and reservations shared by all registered extensions.
 *
 * <p>Also owns the <i>prepared intents</i>: ordinary core intents an extension generates for a
 * source transaction during {@link TxBuildExtension#prepare}. They are an overlay that exists for
 * this build only — the authored intent list of the source transaction is never modified, so a
 * plan can be built again from the same semantic declarations.</p>
 */
@Getter
public final class ExtensionBuildContext {
    private final List<AbstractTx<?>> transactions;
    private final UtxoSupplier utxoSupplier;
    private final ProtocolParamsSupplier protocolParamsSupplier;
    private final Set<String> reservedInputs = new LinkedHashSet<>();
    private final Map<AbstractTx<?>, List<TxIntent>> preparedIntents = new IdentityHashMap<>();

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

    /**
     * Contribute a generated core intent to one source transaction for this build only.
     *
     * <p>Prepared intents are evaluated together with the authored intents — output calculation,
     * input construction, intent application, deposits and script detection — but they are never
     * added to the source transaction, so its serialized plan is unchanged by building it.</p>
     */
    public void addPreparedIntent(AbstractTx<?> transaction, TxIntent intent) {
        if (!transactions.contains(transaction))
            throw new IllegalArgumentException("transaction is not part of this build");
        if (intent == null) throw new IllegalArgumentException("intent is required");
        preparedIntents.computeIfAbsent(transaction, key -> new ArrayList<>()).add(intent);
    }

    /** The intents prepared for one source transaction so far, in contribution order. */
    public List<TxIntent> preparedIntents(AbstractTx<?> transaction) {
        List<TxIntent> prepared = preparedIntents.get(transaction);
        return prepared == null ? List.of() : List.copyOf(prepared);
    }

    /**
     * Return only typed semantic intents owned by the requested extension.
     * Build extensions should use this view instead of scanning or casting unrelated intents.
     */
    public List<ExtensionIntent> extensionIntents(String extensionId) {
        if (extensionId == null || extensionId.isBlank())
            throw new IllegalArgumentException("extensionId is required");
        return transactions.stream()
                .flatMap(transaction -> transaction.getIntentions().stream())
                .filter(ExtensionIntent.class::isInstance)
                .map(ExtensionIntent.class::cast)
                .filter(intent -> extensionId.equals(intent.getExtensionId()))
                .toList();
    }

    /** Return the requested extension's typed intents from one composed transaction fragment. */
    public List<ExtensionIntent> extensionIntents(AbstractTx<?> transaction, String extensionId) {
        if (!transactions.contains(transaction))
            throw new IllegalArgumentException("transaction is not part of this build");
        return transaction.getIntentions().stream()
                .filter(ExtensionIntent.class::isInstance)
                .map(ExtensionIntent.class::cast)
                .filter(intent -> extensionId.equals(intent.getExtensionId()))
                .toList();
    }
}
