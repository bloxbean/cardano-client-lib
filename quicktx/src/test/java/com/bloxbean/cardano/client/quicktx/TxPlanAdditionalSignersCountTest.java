package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

/**
 * additional_signers_count in a TxPlan must reach fee calculation exactly like
 * TxContext.additionalSignersCount(int).
 */
@ExtendWith(MockitoExtension.class)
class TxPlanAdditionalSignersCountTest extends QuickTxBaseTest {

    @Mock
    private UtxoSupplier utxoSupplier;
    private ProtocolParamsSupplier protocolParamsSupplier;
    @Mock
    private TransactionProcessor transactionProcessor;

    String sender = new Account().baseAddress();
    String receiver = new Account().baseAddress();

    @BeforeEach
    @SneakyThrows
    public void setup() {
        MockitoAnnotations.openMocks(this);
        protocolParamJsonFile = "protocol-params.json";
        ProtocolParams protocolParams = (ProtocolParams) loadObjectFromJson("protocol-parameters", ProtocolParams.class);
        protocolParamsSupplier = () -> protocolParams;

        List<Utxo> utxos = List.of(Utxo.builder()
                .address(sender)
                .txHash(generateRandomHexValue(64))
                .outputIndex(0)
                .amount(List.of(Amount.lovelace(adaToLovelace(50))))
                .build());
        given(utxoSupplier.getPage(eq(sender), anyInt(), any(), any())).willAnswer(invocation -> {
            int page = invocation.getArgument(2);
            return page == 0 ? utxos : Collections.emptyList();
        });
    }

    private Tx paymentTx() {
        return new Tx()
                .payToAddress(receiver, Amount.ada(10))
                .from(sender);
    }

    private QuickTxBuilder builder() {
        return new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
    }

    @Test
    void additionalSignersCountFromYamlPlanIsUsedInFeeCalculation() {
        BigInteger baseFee = builder().compose(TxPlan.from(paymentTx()))
                .build().getBody().getFee();
        BigInteger javaFee = builder().compose(paymentTx())
                .additionalSignersCount(2)
                .build().getBody().getFee();

        String yaml = TxPlan.from(paymentTx()).additionalSignersCount(2).toYaml();
        BigInteger planFee = builder().compose(TxPlan.from(yaml))
                .build().getBody().getFee();

        assertThat(planFee).isGreaterThan(baseFee);
        assertThat(planFee).isEqualTo(javaFee);
    }

    @Test
    void additionalSignersCountOnTxContextOverridesPlanValue() {
        BigInteger baseFee = builder().compose(paymentTx())
                .build().getBody().getFee();

        BigInteger overriddenFee = builder().compose(TxPlan.from(paymentTx()).additionalSignersCount(2))
                .additionalSignersCount(0)
                .build().getBody().getFee();

        assertThat(overriddenFee).isEqualTo(baseFee);
    }
}
