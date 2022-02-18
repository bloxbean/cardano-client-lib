package com.bloxbean.cardano.client.function.helper;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.BaseTest;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.ProtocolParams;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.common.MinAdaCalculator;
import com.bloxbean.cardano.client.config.Configuration;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.Output;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.PolicyUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static com.bloxbean.cardano.client.common.CardanoConstants.ONE_ADA;
import static com.bloxbean.cardano.client.function.helper.OutputBuilders.createFromOutput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OutputBuildersTest extends BaseTest {

    @Mock
    BackendService backendService;

    @Mock
    EpochService epochService;

    ProtocolParams protocolParams;

    @BeforeEach
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        protocolParamJsonFile = "protocol-params.json";
        protocolParams = (ProtocolParams) loadObjectFromJson("protocol-parameters", ProtocolParams.class);
    }

    @Test
    void createFromOutput_whenOnlyLovelaceAndWithDatumHash() throws ApiException, CborException, CborSerializationException {
        given(backendService.getEpochService()).willReturn(epochService);
        given(epochService.getProtocolParameters()).willReturn(Result.success(protocolParams.toString()).withValue(protocolParams).code(200));

        Output output1 = Output.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .assetName(LOVELACE)
                .qty(BigInteger.valueOf(12000))
                .datum(new BytesPlutusData("hello".getBytes()))
                .build();

        Output output2 = Output.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .assetName(LOVELACE)
                .qty(ONE_ADA.multiply(BigInteger.valueOf(20)))
                .build();

        TxBuilderContext context = new TxBuilderContext(backendService);
        List<TransactionOutput> list = new ArrayList<>();

        output1.outputBuilder()
                .and(output2.outputBuilder())
                .accept(context, list);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getAddress()).isEqualTo(output1.getAddress());
        assertThat(list.get(0).getValue().getCoin()).isEqualTo(ONE_ADA);
        assertThat(list.get(0).getDatumHash()).isEqualTo(new BytesPlutusData("hello".getBytes()).getDatumHashAsBytes());

        assertThat(list.get(1).getAddress()).isEqualTo(output2.getAddress());
        assertThat(list.get(1).getValue().getCoin()).isEqualTo(ONE_ADA.multiply(BigInteger.valueOf(20)));
        assertThat(list.get(1).getDatumHash()).isNull();
    }

    @Test
    void createFromOutput_whenWithMultiAssetsAndWithDatumHash() throws ApiException, CborException, CborSerializationException {
        given(backendService.getEpochService()).willReturn(epochService);
        given(epochService.getProtocolParameters()).willReturn(Result.success(protocolParams.toString()).withValue(protocolParams).code(200));

        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("abc-policy", 1);

        Output output1 = Output.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .assetName(LOVELACE)
                .qty(ONE_ADA.multiply(BigInteger.valueOf(10)))
                .build();

        Output output2 = Output.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .policyId(policy.getPolicyId())
                .assetName("abc")
                .qty(BigInteger.valueOf(1000))
                .build();

        Output output3 = Output.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .assetName(LOVELACE)
                .qty(ONE_ADA.multiply(BigInteger.valueOf(20)))
                .build();

        TxBuilderContext context = new TxBuilderContext(backendService);
        List<TransactionOutput> list = new ArrayList<>();

        output1.outputBuilder()
                .and(output2.outputBuilder())
                .and(output3.outputBuilder())
                .accept(context, list);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getAddress()).isEqualTo(output2.getAddress());
        assertThat(list.get(0).getValue().getCoin()).isEqualTo(ONE_ADA.multiply(BigInteger.valueOf(10)));
        assertThat(list.get(0).getValue().getMultiAssets()).hasSize(1);
        assertThat(list.get(0).getValue().getMultiAssets().get(0).getPolicyId()).isEqualTo(policy.getPolicyId());
        assertThat(list.get(0).getValue().getMultiAssets().get(0).getAssets()).hasSize(1);
        assertThat(list.get(0).getValue().getMultiAssets().get(0).getAssets().get(0).getName()).isEqualTo(output2.getAssetName());
        assertThat(list.get(0).getValue().getMultiAssets().get(0).getAssets().get(0).getValue()).isEqualTo(output2.getQty());

        assertThat(list.get(1).getAddress()).isEqualTo(output3.getAddress());
        assertThat(list.get(1).getValue().getCoin()).isEqualTo(ONE_ADA.multiply(BigInteger.valueOf(20)));
        assertThat(list.get(1).getValue().getMultiAssets()).isNullOrEmpty();
        assertThat(list.get(1).getDatumHash()).isNull();
    }

    @Test
    void createFromMintOutput() throws Exception {
        given(backendService.getEpochService()).willReturn(epochService);
        given(epochService.getProtocolParameters()).willReturn(Result.success(protocolParams.toString()).withValue(protocolParams).code(200));

        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("abc-policy", 1);

        Output output1 = Output.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .policyId(policy.getPolicyId())
                .assetName("abc")
                .qty(BigInteger.valueOf(1000))
                .build();

        Output output2 = Output.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .assetName(LOVELACE)
                .qty(ONE_ADA.multiply(BigInteger.valueOf(20)))
                .build();

        TxBuilderContext context = new TxBuilderContext(backendService);
        List<TransactionOutput> list = new ArrayList<>();

        output1.mintOutputBuilder()
                .and(output2.outputBuilder())
                .accept(context, list);

        MinAdaCalculator minAdaCalculator = new MinAdaCalculator(protocolParams);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getAddress()).isEqualTo(output1.getAddress());
        assertThat(list.get(0).getValue().getCoin()).isEqualTo(minAdaCalculator.calculateMinAda(list.get(0)));
        assertThat(list.get(0).getValue().getMultiAssets()).hasSize(1);
        assertThat(list.get(0).getValue().getMultiAssets().get(0).getPolicyId()).isEqualTo(policy.getPolicyId());
        assertThat(list.get(0).getValue().getMultiAssets().get(0).getAssets()).hasSize(1);
        assertThat(list.get(0).getValue().getMultiAssets().get(0).getAssets().get(0).getName()).isEqualTo(output1.getAssetName());
        assertThat(list.get(0).getValue().getMultiAssets().get(0).getAssets().get(0).getValue()).isEqualTo(output1.getQty());

        assertThat(list.get(1).getAddress()).isEqualTo(output2.getAddress());
        assertThat(list.get(1).getValue().getCoin()).isEqualTo(ONE_ADA.multiply(BigInteger.valueOf(20)));
        assertThat(list.get(1).getValue().getMultiAssets()).isNullOrEmpty();
        assertThat(list.get(1).getDatumHash()).isNull();

        assertThat(context.getMintMultiAssets()).hasSize(1);
        assertThat(context.getMintMultiAssets().get(0).getPolicyId()).isEqualTo(policy.getPolicyId());
        assertThat(context.getMintMultiAssets().get(0).getAssets().get(0).getName()).isEqualTo(output1.getAssetName());
        assertThat(context.getMintMultiAssets().get(0).getAssets().get(0).getValue()).isEqualTo(output1.getQty());

    }

    @Test
    void testCreateFromOutput_whenCustomMinAdaChecker() throws Exception {
        given(backendService.getEpochService()).willReturn(epochService);
        given(epochService.getProtocolParameters()).willReturn(Result.success(protocolParams.toString()).withValue(protocolParams).code(200));

        Output output1 = Output.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .assetName(LOVELACE)
                .qty(BigInteger.valueOf(12000))
                .datum(new BytesPlutusData("hello".getBytes()))
                .build();

        Output output2 = Output.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .assetName(LOVELACE)
                .qty(ONE_ADA.multiply(BigInteger.valueOf(20)))
                .build();

        TxBuilderContext context = new TxBuilderContext(backendService);
        List<TransactionOutput> list = new ArrayList<>();

        createFromOutput(output1, false, (ctx, tout) -> {
            return BigInteger.valueOf(2000);
        }).and(output2.outputBuilder())
                .accept(context, list);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getAddress()).isEqualTo(output1.getAddress());
        assertThat(list.get(0).getValue().getCoin()).isEqualTo(BigInteger.valueOf(14000));
        assertThat(list.get(0).getDatumHash()).isEqualTo(new BytesPlutusData("hello".getBytes()).getDatumHashAsBytes());

        assertThat(list.get(1).getAddress()).isEqualTo(output2.getAddress());
        assertThat(list.get(1).getValue().getCoin()).isEqualTo(ONE_ADA.multiply(BigInteger.valueOf(20)));
        assertThat(list.get(1).getDatumHash()).isNull();
    }

    @Test
    void testCreateFromOutput_withTransactionOutput()throws Exception {
        given(backendService.getEpochService()).willReturn(epochService);
        given(epochService.getProtocolParameters()).willReturn(Result.success(protocolParams.toString()).withValue(protocolParams).code(200));

        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("abc-policy", 1);
        MultiAsset multiAsset1 = MultiAsset.builder()
                .policyId(policy.getPolicyId())
                .assets(List.of(
                        Asset.builder()
                                .name("abc")
                                .value(BigInteger.valueOf(100))
                                .build()
                )).build();

        Policy policy2 = PolicyUtil.createMultiSigScriptAllPolicy("xyz-policy", 1);
        MultiAsset multiAsset2 = MultiAsset.builder()
                .policyId(policy2.getPolicyId())
                .assets(List.of(
                        Asset.builder()
                                .name("xyz")
                                .value(BigInteger.valueOf(200))
                                .build()
                )).build();

        Value value = Value.builder()
                .coin(BigInteger.valueOf(1000))
                .multiAssets(List.of(
                    multiAsset1, multiAsset2
                )).build();

        Integer datum = 200;

        TransactionOutput output1 = TransactionOutput.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .value(value)
                .datum(datum)
                .build();

        TransactionOutput output2 = TransactionOutput.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .value(Value.builder().coin(ONE_ADA.multiply(BigInteger.valueOf(5))).build())
                .build();

        TxBuilderContext context = new TxBuilderContext(backendService);
        List<TransactionOutput> list = new ArrayList<>();

        //Transform
        createFromOutput(output1)
                .and(createFromOutput(output2))
                .accept(context, list);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getAddress()).isEqualTo(output1.getAddress());
        assertThat(list.get(0).getValue().getCoin()).isEqualTo(new MinAdaCalculator(protocolParams).calculateMinAda(output1));
        assertThat(list.get(0).getValue().getMultiAssets()).hasSize(2);
        assertThat(list.get(0).getValue().getMultiAssets()).contains(multiAsset1, multiAsset2);
        assertThat(list.get(0).getDatumHash()).isEqualTo(Configuration.INSTANCE.getPlutusObjectConverter().toPlutusData(datum).getDatumHashAsBytes());

        assertThat(list.get(1).getAddress()).isEqualTo(output2.getAddress());
        assertThat(list.get(1).getValue().getCoin()).isEqualTo(output2.getValue().getCoin());
        assertThat(list.get(1).getValue().getMultiAssets()).isNullOrEmpty();
        assertThat(list.get(1).getDatumHash()).isNull();
    }

    @Test
    void testCreateFromMintOutput_withTransactionOutputAndMintTrue() throws Exception {
        given(backendService.getEpochService()).willReturn(epochService);
        given(epochService.getProtocolParameters()).willReturn(Result.success(protocolParams.toString()).withValue(protocolParams).code(200));

        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("abc-policy", 1);
        MultiAsset multiAsset1 = MultiAsset.builder()
                .policyId(policy.getPolicyId())
                .assets(List.of(
                        Asset.builder()
                                .name("abc")
                                .value(BigInteger.valueOf(100))
                                .build()
                )).build();

        Policy policy2 = PolicyUtil.createMultiSigScriptAllPolicy("xyz-policy", 1);
        MultiAsset multiAsset2 = MultiAsset.builder()
                .policyId(policy2.getPolicyId())
                .assets(List.of(
                        Asset.builder()
                                .name("xyz")
                                .value(BigInteger.valueOf(200))
                                .build()
                )).build();

        Value value = Value.builder()
                .coin(BigInteger.valueOf(1000))
                .multiAssets(List.of(
                        multiAsset1, multiAsset2
                )).build();

        Integer datum = 200;

        TransactionOutput output1 = TransactionOutput.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .value(value)
                .datum(datum)
                .build();

        TransactionOutput output2 = TransactionOutput.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .value(Value.builder().coin(ONE_ADA.multiply(BigInteger.valueOf(5))).build())
                .datumHash("hello".getBytes())
                .build();

        TxBuilderContext context = new TxBuilderContext(backendService);
        List<TransactionOutput> list = new ArrayList<>();

        //Transform
        OutputBuilders.createFromMintOutput(output1)
                .and(OutputBuilders.createFromOutput(output2))
                .accept(context, list);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getAddress()).isEqualTo(output1.getAddress());
        assertThat(list.get(0).getValue().getCoin()).isEqualTo(new MinAdaCalculator(protocolParams).calculateMinAda(output1));
        assertThat(list.get(0).getValue().getMultiAssets()).hasSize(2);
        assertThat(list.get(0).getValue().getMultiAssets()).contains(multiAsset1, multiAsset2);
        assertThat(list.get(0).getDatumHash()).isEqualTo(Configuration.INSTANCE.getPlutusObjectConverter().toPlutusData(datum).getDatumHashAsBytes());

        assertThat(list.get(1).getAddress()).isEqualTo(output2.getAddress());
        assertThat(list.get(1).getValue().getCoin()).isEqualTo(output2.getValue().getCoin());
        assertThat(list.get(1).getValue().getMultiAssets()).isNullOrEmpty();
        assertThat(list.get(1).getDatumHash()).isEqualTo("hello".getBytes());

        assertThat(context.getMintMultiAssets()).hasSize(2);
        assertThat(context.getMintMultiAssets()).contains(multiAsset1, multiAsset2);

    }

    @Test
    void testCreateFromOutput2_whenTransactionOutputAndCustomMinAdaChecker() throws Exception{
        given(backendService.getEpochService()).willReturn(epochService);
        given(epochService.getProtocolParameters()).willReturn(Result.success(protocolParams.toString()).withValue(protocolParams).code(200));

        TransactionOutput output1 = TransactionOutput.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .value(Value.builder()
                    .coin(BigInteger.valueOf(12000)).build()
                )
                .datum("hello")
                .build();

        Output output2 = Output.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .assetName(LOVELACE)
                .qty(ONE_ADA.multiply(BigInteger.valueOf(20)))
                .build();

        TxBuilderContext context = new TxBuilderContext(backendService);
        List<TransactionOutput> list = new ArrayList<>();

        OutputBuilders.createFromOutput(output1, false, (ctx, tout) -> BigInteger.valueOf(2000))
                .and(output2.outputBuilder())
                .accept(context, list);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getAddress()).isEqualTo(output1.getAddress());
        assertThat(list.get(0).getValue().getCoin()).isEqualTo(BigInteger.valueOf(14000));
        assertThat(list.get(0).getDatumHash()).isEqualTo(BytesPlutusData.of("hello").getDatumHashAsBytes());

        assertThat(list.get(1).getAddress()).isEqualTo(output2.getAddress());
        assertThat(list.get(1).getValue().getCoin()).isEqualTo(ONE_ADA.multiply(BigInteger.valueOf(20)));
        assertThat(list.get(1).getDatumHash()).isNull();
    }
}
