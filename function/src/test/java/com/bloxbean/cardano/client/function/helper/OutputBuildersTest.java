package com.bloxbean.cardano.client.function.helper;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.function.BaseTest;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.common.MinAdaCalculator;
import com.bloxbean.cardano.client.config.Configuration;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.Output;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.api.util.PolicyUtil;
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

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static com.bloxbean.cardano.client.common.CardanoConstants.ONE_ADA;
import static com.bloxbean.cardano.client.function.helper.OutputBuilders.createFromOutput;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class OutputBuildersTest extends BaseTest {

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
    void createFromOutput_whenOnlyLovelaceAndWithDatumHash() throws ApiException, CborException, CborSerializationException {
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

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
        List<TransactionOutput> list = new ArrayList<>();

        output1.outputBuilder()
                .and(output2.outputBuilder())
                .accept(context, list);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getAddress()).isEqualTo(output1.getAddress());
        assertThat(list.get(0).getValue().getCoin()).isGreaterThan(BigInteger.valueOf(900000)); //approx min ada value
        assertThat(list.get(0).getDatumHash()).isEqualTo(new BytesPlutusData("hello".getBytes()).getDatumHashAsBytes());

        assertThat(list.get(1).getAddress()).isEqualTo(output2.getAddress());
        assertThat(list.get(1).getValue().getCoin()).isEqualTo(ONE_ADA.multiply(BigInteger.valueOf(20)));
        assertThat(list.get(1).getDatumHash()).isNull();
    }

    @Test
    void createFromOutput_whenWithMultiAssetsAndWithDatumHash() throws CborException, CborSerializationException {
        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("abc-policy", 1);

        Output output1 = Output.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .assetName(LOVELACE)
                .qty(ONE_ADA.multiply(BigInteger.valueOf(10)))
                .datum(new BytesPlutusData("hello".getBytes()))
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

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
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
        assertThat(list.get(0).getValue().getMultiAssets().get(0).getAssets().get(0).getName()).isEqualTo(HexUtil.encodeHexString(output2.getAssetName().getBytes(), true));
        assertThat(list.get(0).getValue().getMultiAssets().get(0).getAssets().get(0).getValue()).isEqualTo(output2.getQty());
        assertThat(list.get(0).getDatumHash()).isEqualTo(new BytesPlutusData("hello".getBytes()).getDatumHashAsBytes());

        assertThat(list.get(1).getAddress()).isEqualTo(output3.getAddress());
        assertThat(list.get(1).getValue().getCoin()).isEqualTo(ONE_ADA.multiply(BigInteger.valueOf(20)));
        assertThat(list.get(1).getValue().getMultiAssets()).isNullOrEmpty();
        assertThat(list.get(1).getDatumHash()).isNull();
    }

    @Test
    void createFromOutput_whenOnlyLovelaceAndInlineDatumAndScriptRef() throws ApiException, CborException, CborSerializationException {
        Output output1 = Output.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .assetName(LOVELACE)
                .qty(BigInteger.valueOf(12000))
                .datum(new BytesPlutusData("hello".getBytes()))
                .inlineDatum(true)
                .scriptRef(PlutusV2Script.builder().cborHex("49480100002221200101").build())
                .build();

        Output output2 = Output.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .assetName(LOVELACE)
                .qty(ONE_ADA.multiply(BigInteger.valueOf(20)))
                .build();

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
        List<TransactionOutput> list = new ArrayList<>();

        output1.outputBuilder()
                .and(output2.outputBuilder())
                .accept(context, list);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getAddress()).isEqualTo(output1.getAddress());
        assertThat(list.get(0).getValue().getCoin()).isGreaterThan(BigInteger.valueOf(900000)); //approx min ada value
        assertThat(list.get(0).getInlineDatum()).isEqualTo(new BytesPlutusData("hello".getBytes()));
        assertThat(list.get(0).getScriptRef()).isEqualTo(new byte[]{-126, 2, 73, 72, 1, 0, 0, 34, 33, 32, 1, 1});
        assertThat(list.get(0).getDatumHash()).isNull();

        assertThat(list.get(1).getAddress()).isEqualTo(output2.getAddress());
        assertThat(list.get(1).getValue().getCoin()).isEqualTo(ONE_ADA.multiply(BigInteger.valueOf(20)));
        assertThat(list.get(1).getInlineDatum()).isNull();
        assertThat(list.get(1).getScriptRef()).isNull();
        assertThat(list.get(1).getDatumHash()).isNull();
    }

    @Test
    void createFromOutput_whenWithMultiAssetsAndWithInlineDatumAndScriptRef() throws ApiException, CborException, CborSerializationException {
        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("abc-policy", 1);

        Output output1 = Output.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .assetName(LOVELACE)
                .qty(ONE_ADA.multiply(BigInteger.valueOf(10)))
                .datum(new BytesPlutusData("hello".getBytes()))
                .inlineDatum(true)
                .scriptRef(PlutusV2Script.builder().cborHex("49480100002221200101").build())
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

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
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
        assertThat(list.get(0).getValue().getMultiAssets().get(0).getAssets().get(0).getName()).isEqualTo(HexUtil.encodeHexString(output2.getAssetName().getBytes(), true));
        assertThat(list.get(0).getValue().getMultiAssets().get(0).getAssets().get(0).getValue()).isEqualTo(output2.getQty());
        assertThat(list.get(0).getInlineDatum()).isEqualTo(new BytesPlutusData("hello".getBytes()));
        assertThat(list.get(0).getScriptRef()).isEqualTo(new byte[]{-126, 2, 73, 72, 1, 0, 0, 34, 33, 32, 1, 1});
        assertThat(list.get(1).getDatumHash()).isNull();

        assertThat(list.get(1).getAddress()).isEqualTo(output3.getAddress());
        assertThat(list.get(1).getValue().getCoin()).isEqualTo(ONE_ADA.multiply(BigInteger.valueOf(20)));
        assertThat(list.get(1).getValue().getMultiAssets()).isNullOrEmpty();
        assertThat(list.get(1).getInlineDatum()).isNull();
        assertThat(list.get(1).getScriptRef()).isNull();
        assertThat(list.get(1).getDatumHash()).isNull();
    }

    @Test
    void createFromOutput_whenWithMultiAssetsAndWithInlineDatumAndScriptRef_inlineDatumInSecondOutputForSameReceiver() throws ApiException, CborException, CborSerializationException {
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
                .datum(new BytesPlutusData("hello".getBytes()))
                .inlineDatum(true)
                .scriptRef(PlutusV2Script.builder().cborHex("49480100002221200101").build())
                .build();

        Output output3 = Output.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .assetName(LOVELACE)
                .qty(ONE_ADA.multiply(BigInteger.valueOf(20)))
                .datum(new BytesPlutusData("hello world".getBytes()))
                .inlineDatum(true)
                .build();

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
        List<TransactionOutput> list = new ArrayList<>();

        output1.outputBuilder()
                .and(output2.outputBuilder())
                .and(output3.outputBuilder())
                .accept(context, list);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getInlineDatum()).isEqualTo(new BytesPlutusData("hello".getBytes()));
        assertThat(list.get(0).getScriptRef()).isEqualTo(new byte[]{-126, 2, 73, 72, 1, 0, 0, 34, 33, 32, 1, 1});
        assertThat(list.get(1).getDatumHash()).isNull();

        assertThat(list.get(1).getAddress()).isEqualTo(output3.getAddress());
        assertThat(list.get(1).getInlineDatum()).isEqualTo(new BytesPlutusData("hello world".getBytes()));
        assertThat(list.get(1).getScriptRef()).isNull();
        assertThat(list.get(1).getDatumHash()).isNull();
    }

    @Test
    void createFromOutput_whenWithMultiAssetsAndWithInlineDatumAndScriptRef_ignoreInlineDatumInSecondOutputForSameReceiver()
            throws CborSerializationException {
        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("abc-policy", 1);

        Output output1 = Output.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .assetName(LOVELACE)
                .qty(ONE_ADA.multiply(BigInteger.valueOf(10)))
                .datum(new BytesPlutusData("hello".getBytes()))
                .inlineDatum(true)
                .scriptRef(PlutusV2Script.builder().cborHex("49480100002221200101").build())
                .build();

        Output output2 = Output.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .policyId(policy.getPolicyId())
                .assetName("abc")
                .qty(BigInteger.valueOf(1000))
                .datum(new BytesPlutusData("test".getBytes()))
                .inlineDatum(true)
                .scriptRef(PlutusV2Script.builder().cborHex("49480100002221200122").build())
                .build();

        Output output3 = Output.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .assetName(LOVELACE)
                .qty(ONE_ADA.multiply(BigInteger.valueOf(20)))
                .build();

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
        List<TransactionOutput> list = new ArrayList<>();

        output1.outputBuilder()
                .and(output2.outputBuilder())
                .and(output3.outputBuilder())
                .accept(context, list);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getInlineDatum()).isEqualTo(new BytesPlutusData("hello".getBytes()));
        assertThat(list.get(0).getScriptRef()).isEqualTo(new byte[]{-126, 2, 73, 72, 1, 0, 0, 34, 33, 32, 1, 1});
        assertThat(list.get(1).getDatumHash()).isNull();

        assertThat(list.get(1).getAddress()).isEqualTo(output3.getAddress());
        assertThat(list.get(1).getInlineDatum()).isNull();
        assertThat(list.get(1).getScriptRef()).isNull();
        assertThat(list.get(1).getDatumHash()).isNull();
    }

    @Test
    void createFromOutput_whenWithMultiAssetsAndWithDatumHash_dataumHashInSecondOutputForSameReceiver() throws ApiException, CborException, CborSerializationException {
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
                .datum(new BytesPlutusData("hello".getBytes()))
                .build();

        Output output3 = Output.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .assetName(LOVELACE)
                .qty(ONE_ADA.multiply(BigInteger.valueOf(20)))
                .datum(new BytesPlutusData("test".getBytes()))
                .build();

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
        List<TransactionOutput> list = new ArrayList<>();

        output1.outputBuilder()
                .and(output2.outputBuilder())
                .and(output3.outputBuilder())
                .accept(context, list);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getDatumHash()).isEqualTo(new BytesPlutusData("hello".getBytes()).getDatumHashAsBytes());

        assertThat(list.get(1).getAddress()).isEqualTo(output3.getAddress());
        assertThat(list.get(1).getDatumHash()).isEqualTo(new BytesPlutusData("test".getBytes()).getDatumHashAsBytes());

    }

    @Test
    void createFromMintOutput() throws Exception {
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

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
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
        assertThat(list.get(0).getValue().getMultiAssets().get(0).getAssets().get(0).getName()).isEqualTo(HexUtil.encodeHexString(output1.getAssetName().getBytes(), true));
        assertThat(list.get(0).getValue().getMultiAssets().get(0).getAssets().get(0).getValue()).isEqualTo(output1.getQty());

        assertThat(list.get(1).getAddress()).isEqualTo(output2.getAddress());
        assertThat(list.get(1).getValue().getCoin()).isEqualTo(ONE_ADA.multiply(BigInteger.valueOf(20)));
        assertThat(list.get(1).getValue().getMultiAssets()).isNullOrEmpty();
        assertThat(list.get(1).getDatumHash()).isNull();

        assertThat(context.getMintMultiAssets()).hasSize(1);
        assertThat(context.getMintMultiAssets().get(0).getPolicyId()).isEqualTo(policy.getPolicyId());
        assertThat(context.getMintMultiAssets().get(0).getAssets().get(0).getName()).isEqualTo(HexUtil.encodeHexString(output1.getAssetName().getBytes(), true));
        assertThat(context.getMintMultiAssets().get(0).getAssets().get(0).getValue()).isEqualTo(output1.getQty());

    }

    @Test
    void testCreateFromOutput_whenCustomMinAdaChecker() throws Exception {
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

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
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
    void testCreateFromOutput_withTransactionOutput() throws Exception {
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
                .datumHash(BigIntPlutusData.of(datum).getDatumHashAsBytes())
                .build();

        TransactionOutput output2 = TransactionOutput.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .value(Value.builder().coin(ONE_ADA.multiply(BigInteger.valueOf(5))).build())
                .build();

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
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
    void testCreateFromOutput_withTransactionOutput_withInlineDatumAndScriptRef() throws Exception {
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

        TransactionOutput output1 = TransactionOutput.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .value(value)
                .inlineDatum(BigIntPlutusData.of(200))
                .scriptRef(PlutusV2Script.builder().cborHex("49480100002221200101").build())
                .build();

        TransactionOutput output2 = TransactionOutput.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .value(Value.builder().coin(ONE_ADA.multiply(BigInteger.valueOf(5))).build())
                .inlineDatum(BigIntPlutusData.of(400))
                .build();

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
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
        assertThat(list.get(0).getDatumHash()).isNull();
        assertThat(list.get(0).getInlineDatum()).isEqualTo(BigIntPlutusData.of(200));
        assertThat(list.get(0).getScriptRef()).isEqualTo(new byte[]{-126, 2, 73, 72, 1, 0, 0, 34, 33, 32, 1, 1});

        assertThat(list.get(1).getAddress()).isEqualTo(output2.getAddress());
        assertThat(list.get(1).getValue().getCoin()).isEqualTo(output2.getValue().getCoin());
        assertThat(list.get(1).getValue().getMultiAssets()).isNullOrEmpty();
        assertThat(list.get(1).getDatumHash()).isNull();
        assertThat(list.get(1).getInlineDatum()).isEqualTo(BigIntPlutusData.of(400));
    }

    @Test
    void testCreateFromMintOutput_withTransactionOutputAndMintTrue() throws Exception {
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
                .datumHash(BigIntPlutusData.of(datum).getDatumHashAsBytes())
                .build();

        TransactionOutput output2 = TransactionOutput.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .value(Value.builder().coin(ONE_ADA.multiply(BigInteger.valueOf(5))).build())
                .datumHash("hello".getBytes())
                .build();

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
        List<TransactionOutput> list = new ArrayList<>();

        //Transform
        OutputBuilders.createFromMintOutput(output1)
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
        assertThat(list.get(1).getDatumHash()).isEqualTo("hello".getBytes());

        assertThat(context.getMintMultiAssets()).hasSize(2);
        assertThat(context.getMintMultiAssets()).contains(multiAsset1, multiAsset2);

    }

    @Test
    void testCreateFromOutput2_whenTransactionOutputAndCustomMinAdaChecker() throws Exception {
        TransactionOutput output1 = TransactionOutput.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .value(Value.builder()
                        .coin(BigInteger.valueOf(12000)).build()
                )
                .datumHash(BytesPlutusData.of("hello").getDatumHashAsBytes())
                .build();

        Output output2 = Output.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .assetName(LOVELACE)
                .qty(ONE_ADA.multiply(BigInteger.valueOf(20)))
                .build();

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
        List<TransactionOutput> list = new ArrayList<>();

        createFromOutput(output1, false, (ctx, tout) -> BigInteger.valueOf(2000))
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

    @Test
    void createFromOutput_whenMergeOutputs_isFalse() throws CborException, CborSerializationException {
        Output output1 = Output.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .assetName(LOVELACE)
                .qty(adaToLovelace(10))
                .datum(new BytesPlutusData("output1".getBytes()))
                .build();

        Output output2 = Output.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .assetName(LOVELACE)
                .qty(ONE_ADA.multiply(BigInteger.valueOf(20)))
                .build();

        Output output3 = Output.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .assetName(LOVELACE)
                .qty(adaToLovelace(40))
                .datum(new BytesPlutusData("hello".getBytes())) //This should be ignored as output1 already has a datum
                .inlineDatum(true)
                .build();

        Output output4 = Output.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .assetName(LOVELACE)
                .qty(adaToLovelace(0.5))
                .datum(new BytesPlutusData("output4".getBytes()))
                .build();

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
        context.mergeOutputs(false);
        List<TransactionOutput> list = new ArrayList<>();

        output1.outputBuilder()
                .and(output2.outputBuilder())
                .and(output3.outputBuilder())
                .and(output4.outputBuilder())
                .accept(context, list);

        assertThat(list).hasSize(4);
        assertThat(list.get(0).getAddress()).isEqualTo(output1.getAddress());
        assertThat(list.get(0).getValue().getCoin()).isEqualTo(adaToLovelace(10));
        assertThat(list.get(0).getDatumHash()).isEqualTo(new BytesPlutusData("output1".getBytes()).getDatumHashAsBytes());

        assertThat(list.get(1).getAddress()).isEqualTo(output2.getAddress());
        assertThat(list.get(1).getValue().getCoin()).isEqualTo(ONE_ADA.multiply(BigInteger.valueOf(20)));
        assertThat(list.get(1).getDatumHash()).isNull();
        assertThat(list.get(1).getInlineDatum()).isNull();

        assertThat(list.get(2).getAddress()).isEqualTo(output3.getAddress());
        assertThat(list.get(2).getValue().getCoin()).isEqualTo(adaToLovelace(40));
        assertThat(list.get(2).getDatumHash()).isNull();
        assertThat(list.get(2).getInlineDatum()).isEqualTo(new BytesPlutusData("hello".getBytes()));

        assertThat(list.get(3).getAddress()).isEqualTo(output4.getAddress());
        assertThat(list.get(3).getValue().getCoin()).isGreaterThan(adaToLovelace(1));
        assertThat(list.get(3).getDatumHash()).isEqualTo(new BytesPlutusData("output4".getBytes()).getDatumHashAsBytes());
        assertThat(list.get(3).getInlineDatum()).isNull();
    }

    @Test
    void createFromOutput_whenMergeOutputs_isTrue() throws CborException, CborSerializationException {
        Output output1 = Output.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .assetName(LOVELACE)
                .qty(adaToLovelace(10))
                .datum(new BytesPlutusData("output1".getBytes()))
                .build();

        Output output2 = Output.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .assetName(LOVELACE)
                .qty(ONE_ADA.multiply(BigInteger.valueOf(20)))
                .build();

        Output output3 = Output.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .assetName(LOVELACE)
                .qty(adaToLovelace(40))
                .datum(new BytesPlutusData("hello".getBytes()))
                .inlineDatum(true)
                .build();

        Output output4 = Output.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .assetName(LOVELACE)
                .qty(adaToLovelace(0.5))
                .datum(new BytesPlutusData("output4".getBytes()))
                .inlineDatum(true)
                .build();

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
        List<TransactionOutput> list = new ArrayList<>();

        output1.outputBuilder()
                .and(output2.outputBuilder())
                .and(output3.outputBuilder())
                .and(output4.outputBuilder())
                .accept(context, list);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getAddress()).isEqualTo(output1.getAddress());
        assertThat(list.get(0).getValue().getCoin()).isEqualTo(adaToLovelace(50));
        assertThat(list.get(0).getDatumHash()).isEqualTo(new BytesPlutusData("output1".getBytes()).getDatumHashAsBytes());

        assertThat(list.get(1).getAddress()).isEqualTo(output2.getAddress());
        assertThat(list.get(1).getValue().getCoin()).isEqualTo(adaToLovelace(20.5));
        assertThat(list.get(1).getDatumHash()).isNull();
        assertThat(list.get(1).getInlineDatum()).isEqualTo(new BytesPlutusData("output4".getBytes()));
    }

    @Test
    void createFromOutput_withMultiAsset_isMergeOutputsTrue() throws CborSerializationException {
        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("abc-policy", 1);
        Policy policy2 = PolicyUtil.createMultiSigScriptAllPolicy("xyz-policy", 1);

        Output output1 = Output.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .assetName(LOVELACE)
                .qty(adaToLovelace(10))
                .build();

        Output output2 = Output.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .policyId(policy.getPolicyId())
                .assetName("abc")
                .qty(BigInteger.valueOf(1000))
                .datum(new BytesPlutusData("output2".getBytes()))
                .inlineDatum(true)
                .scriptRef(PlutusV2Script.builder().cborHex("49480100002221200101").build())
                .build();

        Output output3 = Output.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .policyId(policy.getPolicyId())
                .assetName("xyz")
                .qty(BigInteger.valueOf(2000))
                .datum(new BytesPlutusData("output3".getBytes())) //This should be ignored as it's already set in output2
                .inlineDatum(true)
                .build();

        Output output4 = Output.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .policyId(policy2.getPolicyId())
                .assetName("xyz")
                .qty(BigInteger.valueOf(7000))
                .datum(new BytesPlutusData("output4".getBytes())) //This should be ignored as it's already set in output2
                .inlineDatum(true)
                .build();

        Output output5 = Output.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .assetName(LOVELACE)
                .qty(adaToLovelace(20))
                .build();

        Output output6 = Output.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .policyId(policy2.getPolicyId())
                .assetName("xyz")
                .qty(BigInteger.valueOf(50))
                .build();

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
        List<TransactionOutput> list = new ArrayList<>();

        output1.outputBuilder()
                .and(output2.outputBuilder())
                .and(output3.outputBuilder())
                .and(output4.outputBuilder())
                .and(output5.outputBuilder())
                .and(output6.outputBuilder())
                .accept(context, list);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getAddress()).isEqualTo(output1.getAddress());
        assertThat(list.get(0).getValue().getCoin()).isEqualTo(adaToLovelace(10));
        assertThat(list.get(0).getValue().getMultiAssets()).containsExactlyInAnyOrder(
                MultiAsset.builder()
                        .policyId(policy.getPolicyId())
                        .assets(List.of(
                                new Asset("abc", BigInteger.valueOf(1000)),
                                new Asset("xyz", BigInteger.valueOf(2000))
                        )).build(),
                MultiAsset.builder()
                        .policyId(policy2.getPolicyId())
                        .assets(List.of(
                                new Asset("xyz", BigInteger.valueOf(7000))
                        )).build()
        );
        assertThat(list.get(0).getInlineDatum()).isEqualTo(new BytesPlutusData("output2".getBytes()));
        assertThat(list.get(0).getScriptRef()).isEqualTo(new byte[]{-126, 2, 73, 72, 1, 0, 0, 34, 33, 32, 1, 1});
        assertThat(list.get(0).getDatumHash()).isNull();

        assertThat(list.get(1).getAddress()).isEqualTo(output5.getAddress());
        assertThat(list.get(1).getValue().getCoin()).isEqualTo(adaToLovelace(20));
        assertThat(list.get(1).getValue().getMultiAssets()).containsExactly(
                MultiAsset.builder()
                        .policyId(policy2.getPolicyId())
                        .assets(List.of(
                                new Asset("xyz", BigInteger.valueOf(50))
                        )).build()
        );

        assertThat(list.get(1).getInlineDatum()).isNull();
        assertThat(list.get(1).getScriptRef()).isNull();
        assertThat(list.get(1).getDatumHash()).isNull();
    }

    @Test
    void createFromOutput_withMultiAsset_isMergeOutputsFalse() throws CborSerializationException {
        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("abc-policy", 1);
        Policy policy2 = PolicyUtil.createMultiSigScriptAllPolicy("xyz-policy", 1);

        Output output1 = Output.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .assetName(LOVELACE)
                .qty(adaToLovelace(10))
                .build();

        Output output2 = Output.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .policyId(policy.getPolicyId())
                .assetName("abc")
                .qty(BigInteger.valueOf(1000))
                .datum(new BytesPlutusData("output2".getBytes()))
                .inlineDatum(true)
                .scriptRef(PlutusV2Script.builder().cborHex("49480100002221200101").build())
                .build();

        Output output3 = Output.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .policyId(policy.getPolicyId())
                .assetName("xyz")
                .qty(BigInteger.valueOf(2000))
                .datum(new BytesPlutusData("output3".getBytes())) //This should be ignored as it's already set in output2
                .inlineDatum(true)
                .build();

        Output output4 = Output.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .policyId(policy2.getPolicyId())
                .assetName("xyz")
                .qty(BigInteger.valueOf(7000))
                .datum(new BytesPlutusData("output4".getBytes())) //This should be ignored as it's already set in output2
                .inlineDatum(true)
                .build();

        Output output5 = Output.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .assetName(LOVELACE)
                .qty(adaToLovelace(20))
                .build();

        Output output6 = Output.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .policyId(policy2.getPolicyId())
                .assetName("xyz")
                .qty(BigInteger.valueOf(50))
                .build();

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
        context.mergeOutputs(false);
        List<TransactionOutput> list = new ArrayList<>();

        output1.outputBuilder()
                .and(output2.outputBuilder())
                .and(output3.outputBuilder())
                .and(output4.outputBuilder())
                .and(output5.outputBuilder())
                .and(output6.outputBuilder())
                .accept(context, list);

        assertThat(list).hasSize(6);

        assertThat(list.get(0).getAddress()).isEqualTo(output1.getAddress());
        assertThat(list.get(0).getValue().getCoin()).isEqualTo(adaToLovelace(10));
        assertThat(list.get(0).getValue().getMultiAssets()).isEmpty();
        assertThat(list.get(0).getInlineDatum()).isNull();
        assertThat(list.get(0).getScriptRef()).isNull();
        assertThat(list.get(0).getDatumHash()).isNull();

        assertThat(list.get(1).getAddress()).isEqualTo(output1.getAddress());
        assertThat(list.get(1).getValue().getCoin()).isGreaterThan(adaToLovelace(1));
        assertThat(list.get(1).getValue().getCoin()).isLessThan(adaToLovelace(2)); //min ada
        assertThat(list.get(1).getValue().getMultiAssets()).containsExactly(
                MultiAsset.builder()
                        .policyId(policy.getPolicyId())
                        .assets(List.of(
                                new Asset("abc", BigInteger.valueOf(1000))
                        )).build()
        );
        assertThat(list.get(1).getInlineDatum()).isEqualTo(new BytesPlutusData("output2".getBytes()));
        assertThat(list.get(1).getScriptRef()).isEqualTo(new byte[]{-126, 2, 73, 72, 1, 0, 0, 34, 33, 32, 1, 1});
        assertThat(list.get(1).getDatumHash()).isNull();

        assertThat(list.get(2).getAddress()).isEqualTo(output3.getAddress());
        assertThat(list.get(2).getValue().getCoin()).isGreaterThan(adaToLovelace(1));
        assertThat(list.get(2).getValue().getCoin()).isLessThan(adaToLovelace(2)); //min ada
        assertThat(list.get(2).getValue().getMultiAssets()).containsExactly(
                MultiAsset.builder()
                        .policyId(policy.getPolicyId())
                        .assets(List.of(
                                new Asset("xyz", BigInteger.valueOf(2000))
                        )).build()
        );
        assertThat(list.get(2).getInlineDatum()).isEqualTo(new BytesPlutusData("output3".getBytes()));
        assertThat(list.get(2).getScriptRef()).isNull();
        assertThat(list.get(2).getDatumHash()).isNull();

        assertThat(list.get(3).getAddress()).isEqualTo(output4.getAddress());
        assertThat(list.get(3).getValue().getCoin()).isGreaterThan(adaToLovelace(1));
        assertThat(list.get(3).getValue().getCoin()).isLessThan(adaToLovelace(2)); //min ada
        assertThat(list.get(3).getValue().getMultiAssets()).containsExactly(
                MultiAsset.builder()
                        .policyId(policy2.getPolicyId())
                        .assets(List.of(
                                new Asset("xyz", BigInteger.valueOf(7000))
                        )).build()
        );
        assertThat(list.get(3).getInlineDatum()).isEqualTo(new BytesPlutusData("output4".getBytes()));
        assertThat(list.get(3).getScriptRef()).isNull();
        assertThat(list.get(3).getDatumHash()).isNull();

        assertThat(list.get(4).getAddress()).isEqualTo(output5.getAddress());
        assertThat(list.get(4).getValue().getCoin()).isEqualTo(adaToLovelace(20));
        assertThat(list.get(4).getValue().getMultiAssets()).isEmpty();
        assertThat(list.get(4).getInlineDatum()).isNull();
        assertThat(list.get(4).getScriptRef()).isNull();
        assertThat(list.get(4).getDatumHash()).isNull();


        assertThat(list.get(5).getAddress()).isEqualTo(output5.getAddress());
        assertThat(list.get(5).getValue().getCoin()).isGreaterThan(adaToLovelace(1));
        assertThat(list.get(5).getValue().getCoin()).isLessThan(adaToLovelace(2)); //min ada
        assertThat(list.get(5).getValue().getMultiAssets()).containsExactly(
                MultiAsset.builder()
                        .policyId(policy2.getPolicyId())
                        .assets(List.of(
                                new Asset("xyz", BigInteger.valueOf(50))
                        )).build()
        );
        assertThat(list.get(5).getInlineDatum()).isNull();
        assertThat(list.get(5).getScriptRef()).isNull();
        assertThat(list.get(5).getDatumHash()).isNull();
    }

    @Test
    void testCreateFromOutput2_isMergeOutputsTrue() throws Exception {
        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("abc-policy", 1);

        TransactionOutput output1 = TransactionOutput.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .value(Value.builder()
                        .coin(adaToLovelace(10))
                        .multiAssets(List.of(
                                MultiAsset.builder()
                                        .policyId(policy.getPolicyId())
                                        .assets(List.of(
                                                new Asset("abc", BigInteger.valueOf(1000))
                                        )).build()
                        )).build()
                )
                .datumHash(BytesPlutusData.of("output1").getDatumHashAsBytes())
                .scriptRef(new byte[]{1, 2, 3, 4})
                .build();

        Output output2 = Output.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .assetName(LOVELACE)
                .qty(adaToLovelace(210))
                .build();

        TransactionOutput output3 = TransactionOutput.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .value(Value.builder()
                        .coin(adaToLovelace(20))
                        .multiAssets(List.of(
                                MultiAsset.builder()
                                        .policyId(policy.getPolicyId())
                                        .assets(List.of(
                                                new Asset("abc", BigInteger.valueOf(2000))
                                        )).build(),
                                MultiAsset.builder()
                                        .policyId(policy.getPolicyId())
                                        .assets(List.of(
                                                new Asset("xyz", BigInteger.valueOf(50))
                                        )).build()
                        )).build()
                )
                .datumHash(new BytesPlutusData("output3".getBytes()).getDatumHashAsBytes())
                .build();

        TransactionOutput output4 = TransactionOutput.builder()
                .address("addr_test1qq8phk43ndg0zf2l4xc5vd7gu4f85swkm3dy7fjmfkf6q249ygmm3ascevccsq5l5ym6khc3je5plx9t5vsa06jvlzls8el07z")
                .value(Value.builder()
                        .coin(adaToLovelace(20)).build()
                )
                .inlineDatum(new BytesPlutusData("output4".getBytes()))
                .build();

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
        List<TransactionOutput> list = new ArrayList<>();

        createFromOutput(output1)
                .and(output2.outputBuilder())
                .and(createFromOutput(output3))
                .and(createFromOutput(output4))
                .accept(context, list);

        assertThat(list).hasSize(3);
        assertThat(list.get(0).getAddress()).isEqualTo(output1.getAddress());
        assertThat(list.get(0).getValue().getCoin()).isEqualTo(adaToLovelace(30));
        assertThat(list.get(0).getValue().getMultiAssets()).containsOnly(
                MultiAsset.builder()
                        .policyId(policy.getPolicyId())
                        .assets(List.of(
                                new Asset("abc", BigInteger.valueOf(3000)),
                                new Asset("xyz", BigInteger.valueOf(50))
                        )).build()
        );
        assertThat(list.get(0).getDatumHash()).isEqualTo(BytesPlutusData.of("output1").getDatumHashAsBytes());
        assertThat(list.get(0).getScriptRef()).isEqualTo(new byte[]{1, 2, 3, 4});

        assertThat(list.get(1).getAddress()).isEqualTo(output2.getAddress());
        assertThat(list.get(1).getValue().getCoin()).isEqualTo(adaToLovelace(210));
        assertThat(list.get(1).getDatumHash()).isNull();
        assertThat(list.get(1).getInlineDatum()).isNull();

        assertThat(list.get(2).getAddress()).isEqualTo(output4.getAddress());
        assertThat(list.get(2).getValue().getCoin()).isEqualTo(adaToLovelace(20));
        assertThat(list.get(2).getDatumHash()).isNull();
        assertThat(list.get(2).getInlineDatum()).isEqualTo(new BytesPlutusData("output4".getBytes()));
    }

    @Test
    void testCreateFromOutput2_isMergeOutputsFalse() throws Exception {
        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("abc-policy", 1);

        TransactionOutput output1 = TransactionOutput.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .value(Value.builder()
                        .coin(adaToLovelace(10))
                        .multiAssets(List.of(
                                MultiAsset.builder()
                                        .policyId(policy.getPolicyId())
                                        .assets(List.of(
                                                new Asset("abc", BigInteger.valueOf(1000))
                                        )).build()
                        )).build()
                )
                .datumHash(BytesPlutusData.of("output1").getDatumHashAsBytes())
                .scriptRef(new byte[]{1, 2, 3, 4})
                .build();

        Output output2 = Output.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .assetName(LOVELACE)
                .qty(adaToLovelace(210))
                .build();

        TransactionOutput output3 = TransactionOutput.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .value(Value.builder()
                        .coin(adaToLovelace(20))
                        .multiAssets(List.of(
                                MultiAsset.builder()
                                        .policyId(policy.getPolicyId())
                                        .assets(List.of(
                                                new Asset("abc", BigInteger.valueOf(2000)),
                                                new Asset("xyz", BigInteger.valueOf(50))
                                        )).build()
                        )).build()
                )
                .inlineDatum(new BytesPlutusData("output3".getBytes()))
                .build();

        TransactionOutput output4 = TransactionOutput.builder()
                .address("addr_test1qq8phk43ndg0zf2l4xc5vd7gu4f85swkm3dy7fjmfkf6q249ygmm3ascevccsq5l5ym6khc3je5plx9t5vsa06jvlzls8el07z")
                .value(Value.builder()
                        .coin(adaToLovelace(20)).build()
                )
                .inlineDatum(new BytesPlutusData("output4".getBytes()))
                .build();

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
        context.mergeOutputs(false);
        List<TransactionOutput> list = new ArrayList<>();

        createFromOutput(output1)
                .and(output2.outputBuilder())
                .and(createFromOutput(output3))
                .and(createFromOutput(output4))
                .accept(context, list);

        assertThat(list).hasSize(4);
        assertThat(list.get(0).getAddress()).isEqualTo(output1.getAddress());
        assertThat(list.get(0).getValue().getCoin()).isEqualTo(adaToLovelace(10));
        assertThat(list.get(0).getValue().getMultiAssets()).containsOnly(
                MultiAsset.builder()
                        .policyId(policy.getPolicyId())
                        .assets(List.of(
                                new Asset("abc", BigInteger.valueOf(1000))
                        )).build()
        );
        assertThat(list.get(0).getDatumHash()).isEqualTo(BytesPlutusData.of("output1").getDatumHashAsBytes());
        assertThat(list.get(0).getScriptRef()).isEqualTo(new byte[]{1, 2, 3, 4});

        assertThat(list.get(1).getAddress()).isEqualTo(output2.getAddress());
        assertThat(list.get(1).getValue().getCoin()).isEqualTo(adaToLovelace(210));
        assertThat(list.get(1).getDatumHash()).isNull();
        assertThat(list.get(1).getInlineDatum()).isNull();

        assertThat(list.get(2).getAddress()).isEqualTo(output1.getAddress());
        assertThat(list.get(2).getValue().getCoin()).isEqualTo(adaToLovelace(20));
        assertThat(list.get(2).getValue().getMultiAssets()).containsExactlyInAnyOrder(
                MultiAsset.builder()
                        .policyId(policy.getPolicyId())
                        .assets(List.of(
                                new Asset("abc", BigInteger.valueOf(2000)),
                                new Asset("xyz", BigInteger.valueOf(50))
                        )).build()
        );
        assertThat(list.get(2).getInlineDatum()).isEqualTo(BytesPlutusData.of("output3"));
        assertThat(list.get(2).getDatumHash()).isNull();
        assertThat(list.get(2).getScriptRef()).isNull();

        assertThat(list.get(3).getAddress()).isEqualTo(output4.getAddress());
        assertThat(list.get(3).getValue().getCoin()).isEqualTo(adaToLovelace(20));
        assertThat(list.get(3).getDatumHash()).isNull();
        assertThat(list.get(3).getInlineDatum()).isEqualTo(new BytesPlutusData("output4".getBytes()));
    }


}
