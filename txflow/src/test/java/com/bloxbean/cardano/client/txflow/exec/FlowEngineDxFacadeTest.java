package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.BlockService;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.quicktx.signing.SignerRegistry;
import com.bloxbean.cardano.hdwallet.Wallet;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowEngineDxFacadeTest {

    @Test
    void backendBuilderUsesStandardAdaptersAndKeepsExecutorCallerOwned() {
        BackendService backend = mock(BackendService.class);
        when(backend.getUtxoService()).thenReturn(mock(UtxoService.class));
        when(backend.getEpochService()).thenReturn(mock(EpochService.class));
        when(backend.getTransactionService()).thenReturn(mock(TransactionService.class));
        when(backend.getBlockService()).thenReturn(mock(BlockService.class));
        Executor executor = Runnable::run;

        FlowEngine engine = FlowEngine.builder(backend).executor(executor).build();

        assertSame(executor, engine.executionExecutor());
        assertThrows(NullPointerException.class, () -> FlowEngine.builder((BackendService) null));
    }

    @Test
    void accountAndWalletConveniencesValidateSchemesAndDuplicates() {
        Account account = mock(Account.class);
        Wallet wallet = mock(Wallet.class);

        assertDoesNotThrow(() -> engineBuilder()
                .account("account://sender", account)
                .wallet("wallet://treasury", wallet)
                .executor(Runnable::run)
                .build());

        IllegalArgumentException accountScheme = assertThrows(IllegalArgumentException.class,
                () -> engineBuilder().account("wallet://sender", account));
        assertTrue(accountScheme.getMessage().contains("account://"));

        IllegalArgumentException walletScheme = assertThrows(IllegalArgumentException.class,
                () -> engineBuilder().wallet("account://treasury", wallet));
        assertTrue(walletScheme.getMessage().contains("wallet://"));

        IllegalArgumentException nonCanonical = assertThrows(IllegalArgumentException.class,
                () -> engineBuilder().account("ACCOUNT://sender", account));
        assertTrue(nonCanonical.getMessage().contains("use 'account://sender'"));

        FlowEngine.Builder duplicate = engineBuilder().account("account://sender", account);
        IllegalArgumentException duplicateFailure = assertThrows(IllegalArgumentException.class,
                () -> duplicate.account("account://sender", account));
        assertTrue(duplicateFailure.getMessage().contains("already registered"));
    }

    @Test
    void explicitAndConvenienceSignerRegistriesConflictInEitherCallOrder() {
        Account account = mock(Account.class);
        SignerRegistry registry = ref -> java.util.Optional.empty();

        IllegalStateException explicitLast = assertThrows(IllegalStateException.class,
                () -> engineBuilder()
                        .account("account://sender", account)
                        .signerRegistry(registry)
                        .executor(Runnable::run)
                        .build());
        assertTrue(explicitLast.getMessage().contains("cannot be combined"));

        IllegalStateException explicitFirst = assertThrows(IllegalStateException.class,
                () -> engineBuilder()
                        .signerRegistry(registry)
                        .account("account://sender", account)
                        .executor(Runnable::run)
                        .build());
        assertTrue(explicitFirst.getMessage().contains("cannot be combined"));
    }

    private FlowEngine.Builder engineBuilder() {
        return FlowEngine.builder(mock(UtxoSupplier.class),
                mock(ProtocolParamsSupplier.class),
                mock(TransactionProcessor.class),
                mock(ChainDataSupplier.class));
    }
}
