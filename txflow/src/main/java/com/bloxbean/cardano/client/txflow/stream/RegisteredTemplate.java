package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.txflow.TxFlow;

import java.util.List;

/**
 * A parameterized portable {@link TxFlow} definition registered on a stream
 * builder under a template id, compiled, validated, and fingerprinted <b>once</b>
 * so every {@link TxWorkItem.Kind#TEMPLATE template item} referencing it is a
 * cheap parameterized invocation of the same held definition rather than a fresh
 * per-item flow (ADR 0004, iteration 3).
 *
 * <p>The portable encoding and fingerprint are computed at stream {@code build()}
 * time. Registration also rejects a non-portable template there — typed at build
 * time, not per item — so an invocation never fails a compile it cannot recover
 * from. The definition must be the SAME across restarts (like lane config): a
 * durable stream persists only the template <em>reference</em> per item and
 * re-resolves it against the re-registered template on re-attach.</p>
 *
 * <p><b>The {@link TxFlow} definition is held by reference</b> (mirroring how a
 * {@link TxWorkItem} payload is held): its portable encoding and fingerprint are
 * frozen at registration, but the same live object is used at every dispatch to
 * build the execution request. Mutating the definition after {@code build()}
 * therefore diverges the executed flow from the frozen fingerprint/encoding —
 * behaviour is undefined. Treat a registered template definition as frozen.</p>
 */
final class RegisteredTemplate {
    private final String templateId;
    private final TxFlow definition;
    private final String portableFlow;
    private final String fingerprint;
    private final String terminalStepId;

    RegisteredTemplate(String templateId, TxFlow definition, String portableFlow,
                       String fingerprint, String terminalStepId) {
        this.templateId = templateId;
        this.definition = definition;
        this.portableFlow = portableFlow;
        this.fingerprint = fingerprint;
        this.terminalStepId = terminalStepId;
    }

    String templateId() {
        return templateId;
    }

    TxFlow definition() {
        return definition;
    }

    String portableFlow() {
        return portableFlow;
    }

    String fingerprint() {
        return fingerprint;
    }

    /**
     * The projection anchor step id: the definition's last declared step, a
     * real step of the flow. A template item is a single-member, whole-flow
     * execution, so item status derives from the flow's overall state; this
     * step id anchors the write-ahead binding and the re-attach reconstruction
     * (which requires a real step of the persisted flow).
     */
    String terminalStepId() {
        return terminalStepId;
    }

    /**
     * The definition's declared parameters, exposed for documentation and
     * diagnostics; the engine's compiler validates the item's bindings against
     * them at dispatch.
     *
     * @return declared parameter names
     */
    List<String> parameterNames() {
        return List.copyOf(definition.getParameters().keySet());
    }
}
