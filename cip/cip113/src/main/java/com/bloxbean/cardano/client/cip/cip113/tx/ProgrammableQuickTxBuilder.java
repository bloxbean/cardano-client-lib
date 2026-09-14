package com.bloxbean.cardano.client.cip.cip113.tx;

import com.bloxbean.cardano.client.quicktx.AbstractTx;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;

import java.util.List;

/**
 * A {@link QuickTxBuilder} that knows about CIP-113.
 *
 * <p>{@code TxBuilderContext} carries a UTxO supplier, protocol parameters and a script supplier,
 * but nothing about a CIP-113 deployment — and it is not constructed until after every
 * {@code complete()} has run in any case. So a programmable transaction cannot discover its own
 * deployment, and something has to hand it over. {@code compose(...)} is where that happens.</p>
 *
 * <p>The timing works out because {@code _build()} — and therefore {@code complete()} — does not
 * run until {@code TxContext.build()}. Installing the deployment at compose time is comfortably
 * ahead of the point where it is needed.</p>
 *
 * <pre>{@code
 * new ProgrammableQuickTxBuilder(backend)
 *         .compose(new ProgrammableTokenTx()
 *                          .from(sender)
 *                          .payToAddress(receiver, Amount.asset(policyId, "MyToken", 10))
 *                          .withRedeemer(policyId, myRedeemer))
 *         .withSigner(SignerProviders.signerFrom(account))
 *         .completeAndWait();
 * }</pre>
 *
 * <p>Ordinary {@code Tx} and {@code ScriptTx} instances pass through untouched, so a mixed
 * composition behaves exactly as it would under a plain {@code QuickTxBuilder}.</p>
 */
public class ProgrammableQuickTxBuilder extends QuickTxBuilder {

    private final ProgrammableTokenService service;

    public ProgrammableQuickTxBuilder(ProgrammableBackendService backend) {
        super(backend);
        this.service = backend.getProgrammableTokenService();
    }

    @Override
    public TxContext compose(AbstractTx... txs) {
        if (txs != null)
            for (AbstractTx tx : txs) wire(tx);
        return dropWitnessesCoveredByReferences(super.compose(txs));
    }

    /**
     * The plan route needs its own override.
     *
     * <p>{@code compose(TxPlan)} builds a {@code TxContext} directly from {@code plan.getTxs()};
     * it does not route through {@link #compose(AbstractTx...)}, so a programmable transaction
     * carried in a plan would otherwise reach the builder unwired. The two other plan overloads
     * both delegate here, so this one override covers all three.</p>
     */
    @Override
    public TxContext compose(TxPlan plan) {
        if (plan != null) {
            List<AbstractTx<?>> txs = plan.getTxs();
            if (txs != null) txs.forEach(this::wire);
        }
        return dropWitnessesCoveredByReferences(super.compose(plan));
    }

    /**
     * Drop witness copies of scripts a reference input already carries.
     *
     * <p>A programmable transaction attaches the base script and its delegates so CCL emits their
     * redeemers, and separately references the deployment's published copies. Both is correct up
     * to this point; carrying the bytes as well as the pointer is not. CCL's duplicate-witness
     * check does the removal but is opt-in, and for this builder it always applies — the
     * references are ours, deliberately added.</p>
     */
    private TxContext dropWitnessesCoveredByReferences(TxContext context) {
        return context.removeDuplicateScriptWitnesses(true);
    }

    private void wire(AbstractTx<?> tx) {
        if (tx instanceof ProgrammableTokenTx)
            ((ProgrammableTokenTx) tx).wire(service, getUtxoSupplier());
    }

}
