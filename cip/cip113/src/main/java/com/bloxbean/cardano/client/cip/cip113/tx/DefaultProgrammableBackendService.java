package com.bloxbean.cardano.client.cip.cip113.tx;

import com.bloxbean.cardano.client.backend.api.AccountService;
import com.bloxbean.cardano.client.backend.api.AddressService;
import com.bloxbean.cardano.client.backend.api.AssetService;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.BlockService;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.api.MetadataService;
import com.bloxbean.cardano.client.backend.api.NetworkInfoService;
import com.bloxbean.cardano.client.backend.api.PoolService;
import com.bloxbean.cardano.client.backend.api.ScriptService;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.cip.cip113.Cip113Deployment;

import java.util.Objects;

/**
 * Adds CIP-113 to any existing backend by delegation.
 *
 * <p>Every inherited service is forwarded untouched, so this composes with whatever provider the
 * caller already uses instead of competing with it — there is no {@code BlockfrostProgrammable...}
 * to write, and a new provider gains CIP-113 support the day it implements {@link BackendService}.
 * The {@code default} helpers on {@code BackendService} are deliberately not overridden: they are
 * defined in terms of the primitive getters below, so they compose correctly on their own.</p>
 */
public class DefaultProgrammableBackendService implements ProgrammableBackendService {

    private final BackendService delegate;
    private final ProgrammableTokenService programmableTokenService;

    public DefaultProgrammableBackendService(BackendService delegate, Cip113Deployment deployment) {
        this.delegate = Objects.requireNonNull(delegate, "delegate backend must not be null");
        this.programmableTokenService = new DefaultProgrammableTokenService(delegate, deployment);
    }

    /**
     * Wrap a backend around an already-built CIP-113 service.
     *
     * <p>Useful when the service has already resolved its deployment, or is a custom
     * implementation reading from an indexer rather than by scanning.</p>
     */
    public DefaultProgrammableBackendService(BackendService delegate,
                                             ProgrammableTokenService programmableTokenService) {
        this.delegate = Objects.requireNonNull(delegate, "delegate backend must not be null");
        this.programmableTokenService =
                Objects.requireNonNull(programmableTokenService, "token service must not be null");
    }

    @Override
    public ProgrammableTokenService getProgrammableTokenService() {
        return programmableTokenService;
    }

    // ------------------------------------------------------------------ delegation

    @Override
    public AssetService getAssetService() {
        return delegate.getAssetService();
    }

    @Override
    public BlockService getBlockService() {
        return delegate.getBlockService();
    }

    @Override
    public NetworkInfoService getNetworkInfoService() {
        return delegate.getNetworkInfoService();
    }

    @Override
    public PoolService getPoolService() {
        return delegate.getPoolService();
    }

    @Override
    public TransactionService getTransactionService() {
        return delegate.getTransactionService();
    }

    @Override
    public UtxoService getUtxoService() {
        return delegate.getUtxoService();
    }

    @Override
    public AddressService getAddressService() {
        return delegate.getAddressService();
    }

    @Override
    public AccountService getAccountService() {
        return delegate.getAccountService();
    }

    @Override
    public EpochService getEpochService() {
        return delegate.getEpochService();
    }

    @Override
    public MetadataService getMetadataService() {
        return delegate.getMetadataService();
    }

    @Override
    public ScriptService getScriptService() {
        return delegate.getScriptService();
    }
}
