package com.bloxbean.cardano.client.cip.cip113.tx;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.cip.cip113.Cip113Deployment;

/**
 * A {@link BackendService} that also answers CIP-113 questions.
 *
 * <p>{@code BackendService} is CCL's service locator: one object carrying every read service a
 * transaction builder needs. Programmable tokens add a category of question the standard services
 * cannot answer — is this policy registered, what are its logic scripts, what does this owner hold
 * in their smart wallet — but nothing about that warrants a second object to carry alongside the
 * first. So this extends the locator rather than sitting beside it.</p>
 *
 * <p>The practical consequence is that a programmable backend goes anywhere an ordinary one does,
 * including {@code new QuickTxBuilder(backendService)}:</p>
 *
 * <pre>{@code
 * ProgrammableBackendService backend =
 *         ProgrammableBackendService.wrap(new BFBackendService(url, key), Cip113Deployments.PREVIEW);
 * backend.getProgrammableTokenService().resolveDeployment();
 *
 * new QuickTxBuilder(backend)                       // still just a BackendService
 *         .compose(backend.getProgrammableTokenService().tx()
 *                         .payToAddress(receiver, Amount.asset(policyId, "Demo", 10))
 *                         .from(sender))
 *         .withSigner(SignerProviders.signerFrom(account))
 *         .completeAndWait();
 * }</pre>
 *
 * <p>Implementations decorate rather than replace: {@link #wrap} delegates every inherited service
 * to a real backend, so Blockfrost, Koios, Ogmios and Nexus all work unchanged and no provider
 * needs to know CIP-113 exists.</p>
 */
public interface ProgrammableBackendService extends BackendService {

    /**
     * The CIP-113 read side — registry lookups, smart-wallet balances, policy-id derivation, and
     * {@link ProgrammableTokenService#tx()} for building against this deployment.
     */
    ProgrammableTokenService getProgrammableTokenService();

    /** The deployment this backend reads. */
    default Cip113Deployment getDeployment() {
        return getProgrammableTokenService().deployment();
    }

    /**
     * Wrap an ordinary backend so it can also answer CIP-113 questions.
     *
     * @param delegate   any backend — every inherited service is forwarded to it untouched
     * @param deployment the CIP-113 deployment to read; usually still unresolved, in which case
     *                   call {@link ProgrammableTokenService#resolveDeployment()} once afterwards
     */
    static ProgrammableBackendService wrap(BackendService delegate, Cip113Deployment deployment) {
        return new DefaultProgrammableBackendService(delegate, deployment);
    }
}
