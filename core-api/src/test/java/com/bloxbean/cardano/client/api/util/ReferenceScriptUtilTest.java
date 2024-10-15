package com.bloxbean.cardano.client.api.util;

import com.bloxbean.cardano.client.api.ScriptSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ReferenceScriptUtilTest {

    @Mock
    private UtxoSupplier utxoSupplier;

    @Mock
    private ScriptSupplier scriptSupplier;

    @Test
    void totalRefScriptsSizeInRefInputs() throws CborSerializationException {

        var transactionBody = TransactionBody.builder()
                .referenceInputs(List.of(
                        new TransactionInput("tx1", 0),
                        new TransactionInput("tx2", 1),
                        new TransactionInput("tx3", 2)
                )).build();

        var transaction = Transaction.builder()
                .body(transactionBody)
                .build();

        given(utxoSupplier.getTxOutput("tx1", 0))
                .willReturn(Optional.of(Utxo.builder()
                        .referenceScriptHash("script1")
                        .build()));

        given(utxoSupplier.getTxOutput("tx2", 1))
                .willReturn(Optional.of(Utxo.builder()
                        .referenceScriptHash("script2")
                        .build()));

        given(utxoSupplier.getTxOutput("tx3", 2))
                .willReturn(Optional.of(Utxo.builder()
                        .referenceScriptHash("script3")
                        .build()));

        var script1 = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("4c4b0100002223300214a22941")
                .build();
        var script2 = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("4c4b0100002223300214a229414c4b0100002223300214a229414c4b0100002223300214a229414c4b0100002223300214a229414c4b0100002223300214a22941")
                .build();
        var script3 = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("4c4b0100002223300214a229414c4b0100002223300214a229414c4b0100002223300214a229414c4b0100002223300214a229414c4b0100002223300214a229414c4b0100002223300214a22941")
                .build();

        given(scriptSupplier.getScript("script1")).willReturn(
                Optional.of(script1));
        given(scriptSupplier.getScript("script2")).willReturn(
                Optional.of(script2));
        given(scriptSupplier.getScript("script3")).willReturn(
                Optional.of(script3));

        var refScriptsSize = ReferenceScriptUtil.totalRefScriptsSizeInRefInputs(utxoSupplier, scriptSupplier, transaction);

        long expectedSize = script1.scriptRefBytes().length + script2.scriptRefBytes().length + script3.scriptRefBytes().length;

        assertThat(refScriptsSize).isEqualTo(expectedSize);
    }

    @Test
    void totalRefScriptsSizeInRefInputs_includesNoScriptInTx() throws CborSerializationException {

        var transactionBody = TransactionBody.builder()
                .referenceInputs(List.of(
                        new TransactionInput("tx1", 0),
                        new TransactionInput("tx2", 1),
                        new TransactionInput("tx3", 2)
                )).build();

        var transaction = Transaction.builder()
                .body(transactionBody)
                .build();

        given(utxoSupplier.getTxOutput("tx1", 0))
                .willReturn(Optional.of(Utxo.builder()
                        .referenceScriptHash("script1")
                        .build()));

        given(utxoSupplier.getTxOutput("tx2", 1))
                .willReturn(Optional.empty());

        given(utxoSupplier.getTxOutput("tx3", 2))
                .willReturn(Optional.of(Utxo.builder()
                        .referenceScriptHash("script3")
                        .build()));

        var script1 = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("4c4b0100002223300214a22941")
                .build();
        var script3 = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("4c4b0100002223300214a229414c4b0100002223300214a229414c4b0100002223300214a229414c4b0100002223300214a229414c4b0100002223300214a229414c4b0100002223300214a22941")
                .build();

        given(scriptSupplier.getScript("script1")).willReturn(
                Optional.of(script1));
        given(scriptSupplier.getScript("script3")).willReturn(
                Optional.of(script3));

        var refScriptsSize = ReferenceScriptUtil.totalRefScriptsSizeInRefInputs(utxoSupplier, scriptSupplier, transaction);

        long expectedSize = script1.scriptRefBytes().length + script3.scriptRefBytes().length;

        assertThat(refScriptsSize).isEqualTo(expectedSize);
    }

    @Test
    void totalRefScriptsSizeInRefInputs_singleScript() throws CborSerializationException {

        var transactionBody = TransactionBody.builder()
                .referenceInputs(List.of(
                        new TransactionInput("tx1", 0)
                )).build();

        var transaction = Transaction.builder()
                .body(transactionBody)
                .build();

        given(utxoSupplier.getTxOutput("tx1", 0))
                .willReturn(Optional.of(Utxo.builder()
                        .referenceScriptHash("script1")
                        .build()));

        var script1 = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("4c4b0100002223300214a22941")
                .build();

        given(scriptSupplier.getScript("script1")).willReturn(
                Optional.of(script1));

        var refScriptsSize = ReferenceScriptUtil.totalRefScriptsSizeInRefInputs(utxoSupplier, scriptSupplier, transaction);

        long expectedSize = script1.scriptRefBytes().length;

        assertThat(refScriptsSize).isEqualTo(expectedSize);
    }

    @Test
    void totalRefScriptsSizeInRefInputs_noScript() throws CborSerializationException {

        var transactionBody = TransactionBody.builder()
                .referenceInputs(List.of(
                        new TransactionInput("tx1", 0)
                )).build();

        var transaction = Transaction.builder()
                .body(transactionBody)
                .build();

        given(utxoSupplier.getTxOutput("tx1", 0))
                .willReturn(Optional.of(Utxo.builder()
                        .build()));

        var refScriptsSize = ReferenceScriptUtil.totalRefScriptsSizeInRefInputs(utxoSupplier, scriptSupplier, transaction);

        assertThat(refScriptsSize).isEqualTo(0);
    }
}
