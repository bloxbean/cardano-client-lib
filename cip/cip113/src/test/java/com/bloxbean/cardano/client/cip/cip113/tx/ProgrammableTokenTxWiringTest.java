package com.bloxbean.cardano.client.cip.cip113.tx;

import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.cip.cip113.Cip113Deployment;
import com.bloxbean.cardano.client.cip.cip113.Cip113Exception;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The fluent verbs must not touch the chain. Every dependency here is left null, so any verb that
 * reads the registry or derives a smart-wallet address fails the test outright rather than quietly
 * working because a mock happened to answer.
 */
class ProgrammableTokenTxWiringTest {

    /** Derived rather than pasted, so it is a real address without needing a fixture. */
    static final String ADDR = AddressProvider.getEntAddress(
            Credential.fromKey("22222222222222222222222222222222222222222222222222222222"),
            Networks.testnet()).toBech32();

    static final String POLICY = "11111111111111111111111111111111111111111111111111111111";

    @Test
    void verbsDoNoChainIoBeforeWiring() {
        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .from(ADDR)
                .payToAddress(ADDR, Amount.asset(POLICY, "Tok", BigInteger.ONE))
                .withRedeemer(POLICY, BigIntPlutusData.of(0));

        assertThat(tx.declaredCount()).isEqualTo(3);
    }

    @Test
    void composeInstallsTheDeployment() {
        ProgrammableTokenService service = mock(ProgrammableTokenService.class);
        when(service.deployment()).thenReturn(Cip113Deployment.builder()
                .network(Networks.testnet())
                .programmableLogicBaseHash("f2182b00a37bd746e20575c9af01ab31312213514cd31e872e0a2a3e")
                .build());
        when(service.registryLookup()).thenReturn(mock(RegistryLookup.class));

        ProgrammableBackendService backend = mock(ProgrammableBackendService.class);
        when(backend.getProgrammableTokenService()).thenReturn(service);
        when(backend.getUtxoService()).thenReturn(mock(UtxoService.class));
        when(backend.getEpochService()).thenReturn(mock(EpochService.class));

        ProgrammableTokenTx tx = new ProgrammableTokenTx();
        new ProgrammableQuickTxBuilder(backend).compose(tx);

        verify(service).deployment();
    }

    /**
     * A tx that never reached ProgrammableQuickTxBuilder has declarations that never ran, so the
     * transaction is missing everything programmable about it. That must fail loudly rather than
     * submit. postBalanceTx is the only hook the builder always calls.
     */
    @Test
    void aNeverWiredTxFailsRatherThanBuildingWithoutItsProgrammableContent() {
        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .from(ADDR)
                .payToAddress(ADDR, Amount.asset(POLICY, "Tok", BigInteger.ONE));

        Transaction txn = new Transaction();
        txn.setBody(TransactionBody.builder().build());

        assertThatThrownBy(() -> tx.postBalanceTx(txn))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("never wired")
                .hasMessageContaining("ProgrammableQuickTxBuilder");
    }
}
