package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.transaction.spec.ChangeOutput;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.function.balance.unfrack.UnfrackPlannerTest.*;
import static org.assertj.core.api.Assertions.assertThat;

class UnfrackTest {
    static final String RECEIVER = "addr_test1qzllzd3cxvz53k9gkq3n3mpcm6g7kv7rj5yvs88n7xwm3nmcs8dpnr85lclka6sycwccput39p0cffqegn8kkf6euzks6h9ldv";

    ProtocolParams protocolParams = ProtocolParams.builder().coinsPerUtxoSize("4310").build();
    TxBuilderContext context = new TxBuilderContext(null, protocolParams);

    @Test
    void changeOutput_splitInPlace_otherOutputsKeepIndexes() {
        TransactionOutput payment = new TransactionOutput(RECEIVER, Value.fromCoin(adaToLovelace(10)));
        Value changeValue = new Value(adaToLovelace(1002), List.of(
                multiAsset(POLICY_1, 3, BigInteger.ONE),
                multiAsset(POLICY_2, 1, BigInteger.valueOf(1000))));
        TransactionOutput metadataOutput = new TransactionOutput(RECEIVER, Value.fromCoin(adaToLovelace(2)));
        Transaction tx = tx(payment, new ChangeOutput(ADDRESS, changeValue), metadataOutput);

        new Unfrack().apply(context, tx);

        List<TransactionOutput> outputs = tx.getBody().getOutputs();
        assertThat(outputs).hasSize(3 + 8); // 2 bundles + 7 ada slices, one of them in place
        assertThat(outputs.get(0)).isSameAs(payment);
        assertThat(outputs.get(2)).isSameAs(metadataOutput);
        assertThat(outputs.get(1)).isInstanceOf(ChangeOutput.class);
        // appended right after the in-place piece: the two token bundles, each holding only its min ada
        assertThat(outputs.get(3).getValue().getMultiAssets()).isNotEmpty();
        assertThat(outputs.get(4).getValue().getMultiAssets()).isNotEmpty();
        // fee bearing piece: largest ada slice (50% of ada left after bundles) + 2 ADA fee reserve
        BigInteger remainingAda = adaToLovelace(1000)
                .subtract(outputs.get(3).getValue().getCoin())
                .subtract(outputs.get(4).getValue().getCoin());
        assertThat(outputs.get(1).getValue().getCoin())
                .isEqualTo(remainingAda.multiply(BigInteger.valueOf(50)).divide(BigInteger.valueOf(100)).add(adaToLovelace(2)));
        assertThat(outputs.get(1).getValue().getMultiAssets()).isNullOrEmpty();
        assertThat(outputs.get(1).getValue().getCoin())
                .isEqualTo(outputs.stream().map(o -> o.getValue().getCoin()).max(BigInteger::compareTo).orElseThrow());

        List<Value> changePieces = new ArrayList<>();
        changePieces.add(outputs.get(1).getValue());
        outputs.subList(3, outputs.size()).forEach(o -> {
            assertThat(o).isInstanceOf(ChangeOutput.class);
            assertThat(o.getAddress()).isEqualTo(ADDRESS);
            changePieces.add(o.getValue());
        });
        assertConserved(changeValue, changePieces);
    }

    @Test
    void smallChange_untouched() {
        ChangeOutput change = new ChangeOutput(ADDRESS, Value.fromCoin(adaToLovelace(50)));
        Transaction tx = tx(change);

        new Unfrack().apply(context, tx);

        assertThat(tx.getBody().getOutputs()).containsExactly(change);
    }

    @Test
    void nonChangeOutputs_andChangeWithDatum_untouched() {
        TransactionOutput payment = new TransactionOutput(ADDRESS, Value.fromCoin(adaToLovelace(1000)));
        ChangeOutput changeWithDatum = new ChangeOutput(ADDRESS, Value.fromCoin(adaToLovelace(1000)));
        changeWithDatum.setInlineDatum(BigIntPlutusData.of(42));
        Transaction tx = tx(payment, changeWithDatum);

        new Unfrack().apply(context, tx);

        assertThat(tx.getBody().getOutputs()).containsExactly(payment, changeWithDatum);
    }

    private static Transaction tx(TransactionOutput... outputs) {
        TransactionBody body = TransactionBody.builder().outputs(new ArrayList<>(List.of(outputs))).build();
        return Transaction.builder().body(body).build();
    }
}
