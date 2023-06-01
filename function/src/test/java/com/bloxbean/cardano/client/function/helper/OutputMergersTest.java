package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.transaction.spec.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.ONE_ADA;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutputMergersTest {
    @Mock
    UtxoSupplier utxoSupplier;

    @Mock
    ProtocolParams protocolParams;

    @BeforeEach
    public void setup() throws IOException, ApiException {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void mergeOutputs() {
        String receiver1 = "addr_test1qpstze8klh30rt5vz6cw6pz4ztjs04y22a2x77yhenarxvwga4dulkd2070d93xwhnaj4d5mhkxn3fkpzzzj3qespmlsskd4ev";
        String receiver2 = "addr_test1qzllzd3cxvz53k9gkq3n3mpcm6g7kv7rj5yvs88n7xwm3nmcs8dpnr85lclka6sycwccput39p0cffqegn8kkf6euzks6h9ldv";
        String changeAddress = "addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka";

        //In
        Transaction transaction = new Transaction();
        TransactionInput ti1 = new TransactionInput("735262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 0);
        TransactionInput ti2 = new TransactionInput("982262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 1);
        transaction.getBody().getInputs().addAll(List.of(ti1, ti2));

        //Outputs
        TransactionOutput to1 = TransactionOutput.builder()
                .address(receiver1)
                .value(Value.builder().coin(ONE_ADA.multiply(BigInteger.valueOf(3))).build())
                .build();
        TransactionOutput to2 = TransactionOutput.builder()
                .address(receiver2)
                .value(Value.builder().coin(ONE_ADA.multiply(BigInteger.valueOf(4))).build())
                .build();
        TransactionOutput to3 = TransactionOutput.builder()
                .address(changeAddress)
                .value(Value.builder().coin(BigInteger.valueOf(948000)).build()) //Insufficient ada
                .build();

        //Output multiassets
        MultiAsset multiAsset1 = MultiAsset.builder()
                .policyId("3a888d65f16790950a72daee1f63aa05add6d268434107cfa5b67712")
                .assets(List.of(
                        new Asset("asset1", BigInteger.valueOf(100)),
                        new Asset("asset2", BigInteger.valueOf(200))
                ))
                .build();

        MultiAsset multiAsset2 = MultiAsset.builder()
                .policyId("2b888d65f16790950a72daee1f63aa05add6d268434107cfa5b67712")
                .assets(List.of(
                        new Asset("asset3", BigInteger.valueOf(500)),
                        new Asset("asset4", BigInteger.valueOf(600))
                ))
                .build();

        MultiAsset multiAsset3 = MultiAsset.builder()
                .policyId("3a888d65f16790950a72daee1f63aa05add6d268434107cfa5b67712")
                .assets(List.of(
                        new Asset("asset1", BigInteger.valueOf(20))
                ))
                .build();

        TransactionOutput to4 = TransactionOutput.builder()
                .address(changeAddress)
                .value(Value.builder().coin(adaToLovelace(5))
                        .multiAssets(List.of(
                                multiAsset1,
                                multiAsset2
                        ))
                        .build()) //Insufficient ada
                .build();

        TransactionOutput to5 = TransactionOutput.builder()
                .address(changeAddress)
                .value(Value.builder().coin(BigInteger.valueOf(-2000000))
                        .multiAssets(List.of(multiAsset3))
                        .build()) //Insufficient ada
                .build();
        transaction.getBody().getOutputs().addAll(List.of(to1, to2, to3, to4, to5));

        TxBuilderContext txBuilderContext = TxBuilderContext.init(utxoSupplier, protocolParams);
        OutputMergers.mergeOutputsForAddress(changeAddress).apply(txBuilderContext, transaction);

        //Asserts
        assertThat(transaction.getBody().getOutputs()).hasSize(3);
        assertThat(transaction.getBody().getOutputs().get(2).getAddress()).isEqualTo(changeAddress);
        assertThat(transaction.getBody().getOutputs().get(2).getValue().getCoin()).isEqualTo(BigInteger.valueOf(948000)
                .add(adaToLovelace(5))
                .add(BigInteger.valueOf(-2000000)));
        assertThat(transaction.getBody().getOutputs().get(2).getValue().getMultiAssets()).hasSize(2);
        assertThat(transaction.getBody().getOutputs().get(2).getValue().getMultiAssets()).contains(
                MultiAsset.builder()
                        .policyId("3a888d65f16790950a72daee1f63aa05add6d268434107cfa5b67712")
                        .assets(List.of(
                                new Asset("asset1", BigInteger.valueOf(120)),
                                new Asset("asset2", BigInteger.valueOf(200))
                        ))
                        .build(),
                MultiAsset.builder()
                        .policyId("2b888d65f16790950a72daee1f63aa05add6d268434107cfa5b67712")
                        .assets(List.of(
                                new Asset("asset3", BigInteger.valueOf(500)),
                                new Asset("asset4", BigInteger.valueOf(600))
                        ))
                        .build()
        );

    }
}
