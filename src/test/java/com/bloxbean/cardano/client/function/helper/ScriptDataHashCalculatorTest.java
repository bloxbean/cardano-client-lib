package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.BaseTest;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.transaction.spec.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ScriptDataHashCalculatorTest extends BaseTest {

    @Mock
    UtxoSupplier utxoSupplier;

    ProtocolParams protocolParams;

    @BeforeEach
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        protocolParamJsonFile = "protocol-params.json";
        protocolParams = (ProtocolParams) loadObjectFromJson("protocol-parameters", ProtocolParams.class);
    }

    @Test
    void calculateScriptDataHash() {
        Transaction transaction = createTx();
        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
        ScriptDataHashCalculator.calculateScriptDataHash()
                .apply(context, transaction);

        assertThat(transaction.getBody().getScriptDataHash()).isNotNull();
        assertThat(transaction.getBody().getScriptDataHash()).hasSizeGreaterThan(0);
    }

    @Test
    void testCalculateScriptDataHash() {
        Transaction transaction = createTx();
        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
        ScriptDataHashCalculator.calculateScriptDataHash(context, transaction);

        assertThat(transaction.getBody().getScriptDataHash()).isNotNull();
        assertThat(transaction.getBody().getScriptDataHash()).hasSizeGreaterThan(0);
    }

    private Transaction createTx() {
        PlutusV2Script plutusScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();
        Redeemer redeemer = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .data(BigIntPlutusData.of(300))
                .index(BigInteger.valueOf(0))
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(1700))
                        .steps(BigInteger.valueOf(476468)).build()
                ).build();
        PlutusData datum = BytesPlutusData.of("hello");

        Transaction transaction = Transaction.builder()
                .body(new TransactionBody())
                .witnessSet(TransactionWitnessSet.builder()
                        .plutusV2Scripts(List.of(plutusScript))
                        .redeemers(List.of(redeemer))
                        .plutusDataList(List.of(datum))
                        .build()
                ).build();

        return transaction;
    }
}
