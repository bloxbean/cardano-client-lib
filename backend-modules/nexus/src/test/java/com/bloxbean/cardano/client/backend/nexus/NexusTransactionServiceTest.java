package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.backend.api.transaction.model.Amount;
import adlabs.nexus.client.backend.api.transaction.model.Datum;
import adlabs.nexus.client.backend.api.transaction.model.ExecutionUnit;
import adlabs.nexus.client.backend.api.transaction.model.PlutusScriptInput;
import adlabs.nexus.client.backend.api.transaction.model.PlutusScriptRedeemer;
import adlabs.nexus.client.backend.api.transaction.model.Purpose;
import adlabs.nexus.client.backend.api.transaction.model.Transaction;
import adlabs.nexus.client.backend.api.transaction.model.TransactionUtxos;
import adlabs.nexus.client.backend.api.transaction.model.TxIO;
import adlabs.nexus.client.backend.api.transaction.model.TxPlutusContract;
import adlabs.nexus.client.backend.api.transaction.model.TxWithdrawal;
import adlabs.nexus.client.backend.api.transaction.model.Utxo;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.backend.model.TxContentRedeemers;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NexusTransactionServiceTest {

    @Test
    void submitTransaction_hexEncodesAndReturnsHash() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.transaction.TransactionService.class);
        byte[] cbor = new byte[]{0x01, 0x02, (byte) 0xff};
        String hex = HexUtil.encodeHexString(cbor);
        when(sdkSvc.submitTransaction(eq(adlabs.nexus.client.util.Network.MAINNET), eq(hex)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, "txhash"));

        var svc = new NexusTransactionService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<String> r = svc.submitTransaction(cbor);

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).isEqualTo("txhash");
        verify(sdkSvc, times(1)).submitTransaction(adlabs.nexus.client.util.Network.MAINNET, hex);
    }

    @Test
    void submitTransaction_error_propagates() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.transaction.TransactionService.class);
        when(sdkSvc.submitTransaction(any(), any())).thenReturn(
                adlabs.nexus.client.backend.api.base.Result.error(400, "bad cbor"));

        var svc = new NexusTransactionService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<String> r = svc.submitTransaction(new byte[]{0x00});

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(400);
    }

    @Test
    void submitTransaction_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.transaction.TransactionService.class);
        when(sdkSvc.submitTransaction(any(), any()))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("boom"));

        var svc = new NexusTransactionService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);

        assertThatThrownBy(() -> svc.submitTransaction(new byte[]{0x00}))
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("boom");
    }

    @Test
    void getTransaction_maps() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.transaction.TransactionService.class);
        Transaction tx = Transaction.builder()
                .txHash("txh1")
                .blockHash("blkh1")
                .blockHeight(100L)
                .txTimestamp(1234L)
                .absoluteSlot(999L)
                .fee("170000")
                .deposit("0")
                .txSize(300)
                .invalidBefore("10")
                .invalidAfter("2000")
                .inputs(List.of(
                        TxIO.builder().txHash("in-txh").txIndex(0).build()))
                .outputs(List.of(
                        TxIO.builder().txHash("txh1").txIndex(0).build(),
                        TxIO.builder().txHash("txh1").txIndex(1).build()))
                .withdrawals(List.of(TxWithdrawal.builder().amount("500").stakeAddr("stake1").build()))
                .assetsMinted(List.of())
                .build();
        when(sdkSvc.getTransaction(eq(adlabs.nexus.client.util.Network.MAINNET), eq("txh1")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, tx));

        var svc = new NexusTransactionService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<TransactionContent> r = svc.getTransaction("txh1");

        assertThat(r.isSuccessful()).isTrue();
        TransactionContent tc = r.getValue();
        assertThat(tc.getHash()).isEqualTo("txh1");
        assertThat(tc.getBlock()).isEqualTo("blkh1");
        assertThat(tc.getBlockHeight()).isEqualTo(100L);
        assertThat(tc.getBlockTime()).isEqualTo(1234L);
        assertThat(tc.getSlot()).isEqualTo(999L);
        assertThat(tc.getFees()).isEqualTo("170000");
        assertThat(tc.getDeposit()).isEqualTo("0");
        assertThat(tc.getSize()).isEqualTo(300);
        assertThat(tc.getInvalidBefore()).isEqualTo("10");
        assertThat(tc.getInvalidHereafter()).isEqualTo("2000");
        assertThat(tc.getIndex()).isNull();
        assertThat(tc.getValidContract()).isNull();
        assertThat(tc.getUtxoCount()).isEqualTo(3);
        assertThat(tc.getWithdrawalCount()).isEqualTo(1);
        assertThat(tc.getAssetMintOrBurnCount()).isEqualTo(0);
    }

    @Test
    void getTransaction_error_propagates() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.transaction.TransactionService.class);
        when(sdkSvc.getTransaction(any(), any())).thenReturn(
                adlabs.nexus.client.backend.api.base.Result.error(404, "not found"));

        var svc = new NexusTransactionService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<TransactionContent> r = svc.getTransaction("missing");

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(404);
    }

    @Test
    void getTransactionUtxos_maps() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.transaction.TransactionService.class);
        Utxo input = Utxo.builder()
                .txHash("in-tx").outputIndex(0).address("addr_in")
                .amount(List.of(Amount.builder().unit("lovelace").quantity("1000000").build()))
                .build();
        Utxo output = Utxo.builder()
                .txHash("txh1").outputIndex(0).address("addr_out")
                .amount(List.of(Amount.builder().unit("lovelace").quantity("900000").build()))
                .dataHash("dh1").inlineDatum("d8799f00ff").referenceScriptHash("rsh1")
                .build();
        TransactionUtxos txUtxos = TransactionUtxos.builder()
                .hash("txh1")
                .inputs(List.of(input))
                .outputs(List.of(output))
                .build();
        when(sdkSvc.getTransactionUtxos(eq(adlabs.nexus.client.util.Network.MAINNET), eq("txh1")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, txUtxos));

        var svc = new NexusTransactionService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<TxContentUtxo> r = svc.getTransactionUtxos("txh1");

        assertThat(r.isSuccessful()).isTrue();
        TxContentUtxo utxo = r.getValue();
        assertThat(utxo.getInputs()).hasSize(1);
        assertThat(utxo.getInputs().get(0).getAddress()).isEqualTo("addr_in");
        assertThat(utxo.getInputs().get(0).getAmount()).hasSize(1);
        assertThat(utxo.getInputs().get(0).getAmount().get(0).getUnit()).isEqualTo("lovelace");
        assertThat(utxo.getInputs().get(0).getAmount().get(0).getQuantity()).isEqualTo("1000000");

        assertThat(utxo.getOutputs()).hasSize(1);
        var out = utxo.getOutputs().get(0);
        assertThat(out.getAddress()).isEqualTo("addr_out");
        assertThat(out.getAmount()).hasSize(1);
        assertThat(out.getAmount().get(0).getUnit()).isEqualTo("lovelace");
        assertThat(out.getAmount().get(0).getQuantity()).isEqualTo("900000");
        assertThat(out.getOutputIndex()).isEqualTo(0);
        assertThat(out.getDataHash()).isEqualTo("dh1");
        assertThat(out.getInlineDatum()).isEqualTo("d8799f00ff");
        assertThat(out.getReferenceScriptHash()).isEqualTo("rsh1");
    }

    @Test
    void getTransactionUtxos_error_propagates() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.transaction.TransactionService.class);
        when(sdkSvc.getTransactionUtxos(any(), any())).thenReturn(
                adlabs.nexus.client.backend.api.base.Result.error(404, "not found"));

        var svc = new NexusTransactionService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<TxContentUtxo> r = svc.getTransactionUtxos("missing");

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(404);
    }

    @Test
    void getTransactions_loopsPerHashAndCollects() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.transaction.TransactionService.class);
        Transaction tx1 = Transaction.builder().txHash("h1").outputs(List.of()).build();
        Transaction tx2 = Transaction.builder().txHash("h2").outputs(List.of()).build();
        when(sdkSvc.getTransaction(eq(adlabs.nexus.client.util.Network.MAINNET), eq("h1")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, tx1));
        when(sdkSvc.getTransaction(eq(adlabs.nexus.client.util.Network.MAINNET), eq("h2")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, tx2));

        var svc = new NexusTransactionService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<List<TransactionContent>> r = svc.getTransactions(List.of("h1", "h2"));

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).hasSize(2);
        assertThat(r.getValue().get(0).getHash()).isEqualTo("h1");
        assertThat(r.getValue().get(1).getHash()).isEqualTo("h2");
        verify(sdkSvc, times(1)).getTransaction(adlabs.nexus.client.util.Network.MAINNET, "h1");
        verify(sdkSvc, times(1)).getTransaction(adlabs.nexus.client.util.Network.MAINNET, "h2");
    }

    @Test
    void getTransactions_bailsOnFirstFailure() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.transaction.TransactionService.class);
        Transaction tx1 = Transaction.builder().txHash("h1").outputs(List.of()).build();
        when(sdkSvc.getTransaction(eq(adlabs.nexus.client.util.Network.MAINNET), eq("h1")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, tx1));
        when(sdkSvc.getTransaction(eq(adlabs.nexus.client.util.Network.MAINNET), eq("h2")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.error(404, "not found"));

        var svc = new NexusTransactionService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<List<TransactionContent>> r = svc.getTransactions(List.of("h1", "h2"));

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(404);
    }

    @Test
    void getTransactionRedeemers_maps() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.transaction.TransactionService.class);
        TxPlutusContract contract = TxPlutusContract.builder()
                .scriptHash("s1")
                .input(PlutusScriptInput.builder()
                        .redeemer(PlutusScriptRedeemer.builder()
                                .purpose(Purpose.SPEND)
                                .fee("1000")
                                .unit(ExecutionUnit.builder().mem(500).steps(1000000L).build())
                                .datum(Datum.builder().hash("d1").build())
                                .build())
                        .build())
                .build();
        Transaction tx = Transaction.builder()
                .txHash("txh1")
                .plutusContracts(List.of(contract))
                .build();
        when(sdkSvc.getTransaction(eq(adlabs.nexus.client.util.Network.MAINNET), eq("txh1")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, tx));

        var svc = new NexusTransactionService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<List<TxContentRedeemers>> r = svc.getTransactionRedeemers("txh1");

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).hasSize(1);
        TxContentRedeemers redeemer = r.getValue().get(0);
        assertThat(redeemer.getTxIndex()).isEqualTo(0);
        assertThat(redeemer.getPurpose()).isEqualTo(RedeemerTag.Spend);
        assertThat(redeemer.getScriptHash()).isEqualTo("s1");
        assertThat(redeemer.getFee()).isEqualTo("1000");
        assertThat(redeemer.getUnitMem()).isEqualTo("500");
        assertThat(redeemer.getUnitSteps()).isEqualTo("1000000");
        assertThat(redeemer.getDatumHash()).isEqualTo("d1");
        assertThat(redeemer.getRedeemerDataHash()).isNull();
    }

    @Test
    void getTransactionRedeemers_nullInput_doesNotNpe() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.transaction.TransactionService.class);
        TxPlutusContract contract = TxPlutusContract.builder()
                .scriptHash("s2")
                .input(null)
                .build();
        Transaction tx = Transaction.builder()
                .txHash("txh1")
                .plutusContracts(List.of(contract))
                .build();
        when(sdkSvc.getTransaction(eq(adlabs.nexus.client.util.Network.MAINNET), eq("txh1")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, tx));

        var svc = new NexusTransactionService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<List<TxContentRedeemers>> r = svc.getTransactionRedeemers("txh1");

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).hasSize(1);
        TxContentRedeemers redeemer = r.getValue().get(0);
        assertThat(redeemer.getTxIndex()).isEqualTo(0);
        assertThat(redeemer.getScriptHash()).isEqualTo("s2");
        assertThat(redeemer.getPurpose()).isNull();
        assertThat(redeemer.getFee()).isNull();
        assertThat(redeemer.getUnitMem()).isNull();
        assertThat(redeemer.getUnitSteps()).isNull();
        assertThat(redeemer.getDatumHash()).isNull();
        assertThat(redeemer.getRedeemerDataHash()).isNull();
    }

    @Test
    void getTransactionRedeemers_emptyPlutusContracts_returnsEmptyList() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.transaction.TransactionService.class);
        Transaction tx = Transaction.builder().txHash("txh1").plutusContracts(List.of()).build();
        when(sdkSvc.getTransaction(eq(adlabs.nexus.client.util.Network.MAINNET), eq("txh1")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, tx));

        var svc = new NexusTransactionService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<List<TxContentRedeemers>> r = svc.getTransactionRedeemers("txh1");

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).isEmpty();
    }

    @Test
    void getTransactionRedeemers_nullPlutusContracts_returnsEmptyList() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.transaction.TransactionService.class);
        Transaction tx = Transaction.builder().txHash("txh1").plutusContracts(null).build();
        when(sdkSvc.getTransaction(eq(adlabs.nexus.client.util.Network.MAINNET), eq("txh1")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, tx));

        var svc = new NexusTransactionService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<List<TxContentRedeemers>> r = svc.getTransactionRedeemers("txh1");

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).isEmpty();
    }

    @Test
    void getTransactionRedeemers_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.transaction.TransactionService.class);
        when(sdkSvc.getTransaction(any(), any()))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("boom"));

        var svc = new NexusTransactionService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);

        assertThatThrownBy(() -> svc.getTransactionRedeemers("txh1"))
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("boom");
    }
}
