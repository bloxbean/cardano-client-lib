package com.bloxbean.cardano.client.function.helper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.plutus.spec.PlutusV3Script;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashSet;
import java.util.List;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DuplicateScriptWitnessCheckerTest {

    @Test
    public void testRemoveDuplicateScriptWitnesses_WithNoDuplicates() {
        TxBuilderContext context = Mockito.mock(TxBuilderContext.class);

        given(context.getRefScriptHashes()).willReturn(new HashSet<>(List.of("abc123", "def234")));

        Transaction txn = new Transaction();

        TransactionBody transactionBody = new TransactionBody();
        transactionBody.setInputs(List.of(
                new TransactionInput("tx1", 0),
                new TransactionInput("tx2", 1)
        ));

        transactionBody.setReferenceInputs(List.of(
                new TransactionInput("tx3", 0),
                new TransactionInput("tx4", 1)
        ));

        var script1 = PlutusV3Script.builder()
                .cborHex("aabbccded")
                .build();

        var script2 = PlutusV3Script.builder()
                .cborHex("eeffgg")
                .build();

        TransactionWitnessSet witnessSet = new TransactionWitnessSet();
        witnessSet.getPlutusV3Scripts().add(script1);
        witnessSet.getPlutusV3Scripts().add(script2);

        txn.setWitnessSet(witnessSet);

        DuplicateScriptWitnessChecker.removeDuplicateScriptWitnesses().apply(context, txn);

        assertEquals(2, witnessSet.getPlutusV3Scripts().size());
    }


    @Test
    public void testRemoveDuplicateScriptWitnesses_WithDuplicates() throws CborSerializationException {
        TxBuilderContext context = Mockito.mock(TxBuilderContext.class);

        given(context.getRefScriptHashes()).willReturn(new HashSet<>(List.of("226e32faa80a26810392fda6d559c7ed4721a65ce1c9d4ef3e1c87b4",
                "186e32faa80a26810392fda6d559c7ed4721a65ce1c9d4ef3e1c87b4")));

        Transaction txn = new Transaction();

        TransactionBody transactionBody = new TransactionBody();
        transactionBody.setInputs(List.of(
                new TransactionInput("tx1", 0),
                new TransactionInput("tx2", 1)
        ));

        transactionBody.setReferenceInputs(List.of(
                new TransactionInput("tx3", 0),
                new TransactionInput("tx4", 1)
        ));

        var script1 = PlutusV3Script.builder()
                .cborHex("46450101002499")
                .build();

        var script2 = PlutusV3Script.builder()
                .cborHex("46450101002498")
                .build();

        TransactionWitnessSet witnessSet = new TransactionWitnessSet();
        witnessSet.getPlutusV3Scripts().add(script1);
        witnessSet.getPlutusV3Scripts().add(script2);

        txn.setWitnessSet(witnessSet);

        DuplicateScriptWitnessChecker.removeDuplicateScriptWitnesses().apply(context, txn);

        assertEquals(1, witnessSet.getPlutusV3Scripts().size());
    }

}
