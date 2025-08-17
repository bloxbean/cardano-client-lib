package com.bloxbean.cardano.client.dsl;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.dsl.context.TxExecutionContext;
import com.bloxbean.cardano.client.dsl.intention.TxIntention;
import com.bloxbean.cardano.client.dsl.model.TransactionDocument;
import com.bloxbean.cardano.client.dsl.serialization.YamlSerializer;
import com.bloxbean.cardano.client.quicktx.AbstractTx;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TxDslBuilder - V3 Context Wrapper Pattern
 *
 * Thin wrapper that delegates to QuickTxBuilder while providing context support.
 * This design maximizes code reuse by directly delegating to existing QuickTxBuilder
 * instead of reimplementing its functionality.
 */
public class TxDslBuilder {

    private final QuickTxBuilder quickTxBuilder;
    private final TxExecutionContext executionContext;
    private final Map<String, Object> variables;

    public TxDslBuilder(BackendService backendService) {
        this.quickTxBuilder = new QuickTxBuilder(backendService);
        this.executionContext = new TxExecutionContext();
        this.variables = new HashMap<>();
    }

    // Context configuration methods - return this for fluent chaining

    public TxDslBuilder feePayer(String address) {
        executionContext.setFeePayer(address);
        return this;
    }

    public TxDslBuilder collateralPayer(String address) {
        executionContext.setCollateralPayer(address);
        return this;
    }

    public TxDslBuilder utxoSelectionStrategy(String strategyKey) {
        executionContext.setUtxoSelectionStrategy(strategyKey);
        return this;
    }

    public TxDslBuilder signer(String signerKey) {
        executionContext.setSigner(signerKey);
        return this;
    }

    // Core delegation method - reuses QuickTxBuilder.compose()
    public QuickTxBuilder.TxContext compose(AbstractTx... txs) {
        // Direct delegation to existing QuickTxBuilder
        QuickTxBuilder.TxContext txContext = quickTxBuilder.compose(txs);

        // Apply context with variable resolution
        return applyContext(txContext);
    }

    // Convenience method for TxDsl objects
    public QuickTxBuilder.TxContext compose(TxDsl... txDsls) {
        // Extract variables from TxDsl objects
        extractVariables(txDsls);

        // Convert to AbstractTx array
        AbstractTx[] txs = Arrays.stream(txDsls)
            .map(TxDsl::unwrap)
            .toArray(AbstractTx[]::new);

        return compose(txs);
    }

    // YAML integration
    public QuickTxBuilder.TxContext composeFromYaml(String yaml) {
        TransactionDocument doc = YamlSerializer.deserialize(yaml);

        // Extract TxDsl objects from transaction list
        TxDsl[] txDsls = extractTxDslsFromDocument(doc);

        // Apply context from YAML
        applyContextFromDocument(doc);

        return compose(txDsls);
    }

    private QuickTxBuilder.TxContext applyContext(QuickTxBuilder.TxContext txContext) {
        // Apply context with variable resolution
        return executionContext.applyTo(txContext, variables);
    }

    private void extractVariables(TxDsl[] txDsls) {
        for (TxDsl txDsl : txDsls) {
            // Extract variables from each TxDsl
            if (txDsl.getVariables() != null) {
                variables.putAll(txDsl.getVariables());
            }
        }
    }

    private TxDsl[] extractTxDslsFromDocument(TransactionDocument doc) {
        if (doc.getTransaction() == null || doc.getTransaction().isEmpty()) {
            return new TxDsl[0];
        }

        // Convert each transaction entry to TxDsl
        List<TxDsl> txDsls = new ArrayList<>();
        for (TransactionDocument.TxEntry txEntry : doc.getTransaction()) {
            if (txEntry.getTx() != null && txEntry.getTx().getIntentions() != null) {
                TxDsl txDsl = new TxDsl();

                // Apply each intention to build the transaction
                for (TxIntention intention : txEntry.getTx().getIntentions()) {
                    intention.apply(txDsl);
                }

                // Add document variables to this TxDsl
                if (doc.getVariables() != null) {
                    for (Map.Entry<String, Object> variable : doc.getVariables().entrySet()) {
                        txDsl.withVariable(variable.getKey(), variable.getValue());
                    }
                }

                txDsls.add(txDsl);
            }
        }

        return txDsls.toArray(new TxDsl[0]);
    }

    private void applyContextFromDocument(TransactionDocument doc) {
        if (doc.getContext() == null) {
            return;
        }

        TransactionDocument.ContextSection contextSection = doc.getContext();

        // Apply context properties to this TxDslBuilder
        if (contextSection.getFeePayer() != null) {
            this.feePayer(contextSection.getFeePayer());
        }

        if (contextSection.getCollateralPayer() != null) {
            this.collateralPayer(contextSection.getCollateralPayer());
        }

        if (contextSection.getUtxoSelectionStrategy() != null) {
            this.utxoSelectionStrategy(contextSection.getUtxoSelectionStrategy());
        }

        if (contextSection.getSigner() != null) {
            this.signer(contextSection.getSigner());
        }

        // Also add document variables to our variables map for resolution
        if (doc.getVariables() != null) {
            this.variables.putAll(doc.getVariables());
        }
    }
}
