package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class QuickTxBuilderTest {
    @Mock
    UtxoSupplier utxoSupplier;

    @Mock
    ProtocolParamsSupplier protocolParamsSupplier;

    String sender = "addr_test1qpcf5ursqpwx2tp8maeah00rxxdfpvf8h65k4hk3chac0fvu28duly863yqhgjtl8an2pkksd6mlzv0qv4nejh5u2zjsshr90k";

    @Test
    void whenDonateToTreasury_thenTotalOutputIsEqualsToTotalInput() {

        given(utxoSupplier.getPage(anyString(), anyInt(), anyInt(), any()))
                .willReturn(List.of(Utxo.builder()
                        .address(sender)
                        .txHash("7e1eecf7439fb5119a6762985a61c9fb3ca8158d9fc38361f0c4746430d5e0c7")
                        .outputIndex(0)
                        .amount(List.of(Amount.ada(40000)))
                        .build()));

        given(protocolParamsSupplier.getProtocolParams())
                .willReturn(ProtocolParams.builder()
                        .minFeeA(44)
                        .minFeeB(155381)
                        .minUtxo("1000000")
                        .coinsPerUtxoSize("4312")
                        .minFeeRefScriptCostPerByte(BigDecimal.valueOf(15))
                        .build());

        Tx tx = new Tx()
                .donateToTreasury(adaToLovelace(1000), adaToLovelace(50))
                .from(sender);

        var transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, null)
                .compose(tx)
                .build();

       BigInteger totalOutput = transaction.getBody().getFee()
               .add(transaction.getBody().getDonation())
               .add(transaction.getBody().getOutputs().get(0).getValue().getCoin());

       assertThat(totalOutput).isEqualTo(adaToLovelace(40000));
    }
}
