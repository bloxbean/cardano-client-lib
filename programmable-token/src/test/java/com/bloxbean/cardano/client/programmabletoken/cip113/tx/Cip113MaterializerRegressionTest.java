package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployment;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Exception;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.RegistryNode;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Cip113MaterializerRegressionTest {

    @Test
    void arbitraryAssetNameBytesAreNeverDecodedAndReencoded() {
        String unit = "11".repeat(28) + "3078ff00";
        Amount changed = Cip113TransactionMaterializer.withQuantity(
                Amount.builder().unit(unit).quantity(BigInteger.TEN).build(), BigInteger.ONE);
        assertThat(changed.getUnit()).isEqualTo(unit);
    }

    @Test
    void adaBufferRequiresPositiveLovelace() {
        Cip113TransactionMaterializer materializer = new Cip113TransactionMaterializer(
                deployment(), mock(RegistryLookup.class), mock(UtxoSupplier.class));

        assertThatThrownBy(() -> materializer.withAdaBuffer(
                Amount.builder().unit("11".repeat(28)).quantity(BigInteger.ONE).build()))
                .isInstanceOf(Cip113Exception.class).hasMessageContaining("lovelace");
        assertThatThrownBy(() -> materializer.withAdaBuffer(Amount.lovelace(BigInteger.ZERO)))
                .isInstanceOf(Cip113Exception.class).hasMessageContaining("positive");
    }

    @Test
    void manualInternalConstructionIsWired() {
        Cip113TransactionMaterializer materializer = new Cip113TransactionMaterializer(
                deployment(), mock(RegistryLookup.class), mock(UtxoSupplier.class));
        assertThat(materializer.isWired()).isTrue();
    }

    @Test
    void indexSnapshotDetectsSameSizeReordering() {
        TransactionInput first = TransactionInput.builder().transactionId("11".repeat(32)).index(0).build();
        TransactionInput second = TransactionInput.builder().transactionId("22".repeat(32)).index(0).build();
        Transaction a = Transaction.builder().body(TransactionBody.builder()
                .inputs(List.of(first, second)).build()).build();
        Transaction b = Transaction.builder().body(TransactionBody.builder()
                .inputs(List.of(second, first)).build()).build();

        assertThat(Cip113TransactionMaterializer.indexSensitiveFingerprint(a))
                .isNotEqualTo(Cip113TransactionMaterializer.indexSensitiveFingerprint(b));
    }

    @Test
    void scanningLookupRefreshesEvenWhenPolicyWasAlreadyPresent() {
        UtxoSupplier supplier = mock(UtxoSupplier.class);
        String policy = "33".repeat(28);
        RegistryNode oldNode = node(policy, "44".repeat(28));
        RegistryNode updatedNode = oldNode.toBuilder().globalStateCs("55".repeat(28)).build();
        Utxo oldUtxo = registryUtxo(policy, oldNode, 0);
        Utxo updatedUtxo = registryUtxo(policy, updatedNode, 1);
        when(supplier.getAll(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of(oldUtxo), List.of(updatedUtxo));
        RegistryLookup lookup = new RegistryLookup.Scanning(supplier, deployment());

        assertThat(lookup.byPolicy(policy).orElseThrow().getDatum().getGlobalStateCs()).isEmpty();
        assertThat(lookup.byPolicy(policy).orElseThrow().getDatum().getGlobalStateCs())
                .isEqualTo("55".repeat(28));
        verify(supplier, times(2)).getAll(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void explicitProgrammableTransferNeverFallsBackForAnUnregisteredPolicy() {
        RegistryLookup registry = mock(RegistryLookup.class);
        String policy = "33".repeat(28);
        when(registry.byPolicy(policy)).thenReturn(Optional.empty());
        Cip113TransactionMaterializer materializer = new Cip113TransactionMaterializer(
                deployment(), registry, mock(UtxoSupplier.class));

        assertThatThrownBy(() -> materializer.recordTransferForExtension(policy,
                "addr_test1vpuv7h0p4n0sl3w5c2hx7ypz3e3f0vkd8y6wqddgc7nr2ns9zjhxy",
                Amount.builder().unit(policy + "00").quantity(BigInteger.ONE).build()))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("not registered");
    }

    private static Utxo registryUtxo(String policy, RegistryNode node, int index) {
        return Utxo.builder().txHash(String.format("%064x", index + 1)).outputIndex(index)
                .address(deployment().registryAddress().toBech32())
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(2_000_000)),
                        Amount.builder().unit("aa".repeat(28) + policy)
                                .quantity(BigInteger.ONE).build()))
                .inlineDatum(node.toPlutusData().serializeToHex()).build();
    }

    private static RegistryNode node(String key, String next) {
        Credential credential = Credential.fromScript("66".repeat(28));
        return RegistryNode.builder().key(key).next(next)
                .mintingLogicScript(credential).transferLogicScript(credential)
                .thirdPartyTransferLogicScript(credential).unfrackingLogicScript(credential)
                .globalStateCs("").build();
    }

    private static Cip113Deployment deployment() {
        return Cip113Deployment.builder().network(Networks.testnet())
                .registrySpendScriptHash("77".repeat(28))
                .registryNodeCs("aa".repeat(28)).build();
    }
}
