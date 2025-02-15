package com.bloxbean.cardano.client.function.helper;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.api.util.PolicyUtil;
import com.bloxbean.cardano.client.function.BaseTest;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.exception.InsufficientBalanceException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.config.Configuration;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.function.TxInputBuilder;
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.PlutusField;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.ONE_ADA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InputBuildersTest extends BaseTest {
    public static final String LIST_1 = "list1";
    public static final String LIST_2 = "list2";
    public static final String LIST_3 = "list3-multiassets";
    public static final String LIST_4 = "list4-multiassets";
    public static final String LIST_5 = "list5-multiassets-insufficientADA";
    public static final String LIST_6 = "list6-multiassets-insufficientADA_Error";
    public static final String LIST_7 = "list7-insufficient-change-amount";
    public static final String LIST_8 = "list8-insufficient-change-amount-with-native-token";
    public static final String LIST_9 = "list9-merge_outputs_true";
    public static final String LIST_10 = "list10-merge_outputs_false";

    @Mock
    UtxoSupplier utxoSupplier;

    ProtocolParams protocolParams;

    String sender;
    String changeAddress;

    @BeforeEach
    public void setup() throws IOException, ApiException {
        MockitoAnnotations.openMocks(this);

        protocolParamJsonFile = "protocol-params.json";
        protocolParams = (ProtocolParams) loadObjectFromJson("protocol-parameters", ProtocolParams.class);

        utxoJsonFile = "utxos-function.json";

        sender = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        changeAddress = "addr_test1qz7g6c8w6lzhr5weyus79rl4alepskc6u2pfuzkr7s5qad30ry2sf3u3vq0hzkyhht4uwqy8p40xfy5qkgc79xswq5msnaucg2";

        initStubbing();
    }

    void initStubbing() throws ApiException {

    }

    @Test
    void createFromSender_whenLovelaceOnlyOutput() throws IOException, ApiException {
        List<Utxo> utxos = loadUtxos(LIST_1);
        given(utxoSupplier.getPage(anyString(), anyInt(), anyInt(), any())).willReturn(utxos);

        List<TransactionOutput> outputs = new ArrayList<>();

        outputs.add(TransactionOutput.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .value(Value.builder().coin(ONE_ADA.multiply(BigInteger.valueOf(1000))).build())
                .build()
        );
        outputs.add(TransactionOutput.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .value(Value.builder().coin(ONE_ADA.multiply(BigInteger.valueOf(500))).build())
                .build()
        );

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);

        TxInputBuilder.Result inputResult = InputBuilders.createFromSender(sender, changeAddress)
                .apply(context, outputs);

        //assert inputs
        assertThat(inputResult.getInputs()).hasSize(3);
        assertThat(inputResult.getInputs()).contains(
                new TransactionInput("735262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 0),
                new TransactionInput("88c014d348bf1919c78a5cb87a5beed87729ff3f8a2019be040117a41a83e82e", 1),
                new TransactionInput("6674e44f0f03915b1611ce58aaff5f2e52054e1911fbcd0f17dbc205f44763b6", 0)
        );

        assertThat(inputResult.getInputs()).doesNotContain(
                new TransactionInput("a712906ae823ecefe6cab76e5cfd427bd0b6144df10c6f89a56fbdf30fa807f4", 1)
        );

        //assert change output
        BigInteger totalVal = BigInteger.valueOf(1407406 + 994632035 + 1000000000);
        BigInteger amtSent = ONE_ADA.multiply(BigInteger.valueOf(1000 + 500));
        assertThat(inputResult.getChanges()).hasSize(1);
        assertThat(inputResult.getChanges()).contains(
                TransactionOutput.builder()
                        .address(changeAddress)
                        .value(Value.builder()
                                .coin(totalVal.subtract(amtSent))
                                .multiAssets(
                                        List.of(MultiAsset.
                                                builder().policyId("6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7")
                                                .assets(List.of(Asset.builder()
                                                        .name(HexUtil.encodeHexString("".getBytes(StandardCharsets.UTF_8)))
                                                        .value(BigInteger.valueOf(2))
                                                        .build()
                                                )).build()
                                        )
                                )
                                .build()
                        ).build()
        );

        assertThat(context.getUtxos()).hasSize(3);
    }

    @Test
    void createFromSender_whenLovelaceAndMultiAsset() throws Exception {
        List<Utxo> utxos = loadUtxos(LIST_3);
        given(utxoSupplier.getPage(anyString(), anyInt(), anyInt(), any())).willReturn(utxos);

        List<TransactionOutput> outputs = new ArrayList<>();

        String unit = "329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e";
        Tuple<String, String> tuple = AssetUtil.getPolicyIdAndAssetName(unit);
        String policyId = tuple._1;
        String assetName = tuple._2;

        MultiAsset ma = MultiAsset.builder()
                .policyId(policyId)
                .assets(List.of(
                        Asset.builder()
                                .name(assetName)
                                .value(BigInteger.valueOf(10000))
                                .build()
                )).build();

        String unit2 = "329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96926174636f766e";
        Tuple<String, String> tuple2 = AssetUtil.getPolicyIdAndAssetName(unit2);
        String policyId2 = tuple2._1;
        String assetName2 = tuple2._2;

        MultiAsset ma2 = MultiAsset.builder()
                .policyId(policyId2)
                .assets(List.of(
                        Asset.builder()
                                .name(assetName2)
                                .value(BigInteger.valueOf(20000))
                                .build()
                )).build();


        outputs.add(TransactionOutput.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .value(Value.builder().coin(ONE_ADA.multiply(BigInteger.valueOf(50)))
                        .multiAssets(List.of(ma))
                        .build())
                .build()
        );
        outputs.add(TransactionOutput.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .value(Value.builder().coin(ONE_ADA.multiply(BigInteger.valueOf(50)))
                        .multiAssets(List.of(ma2))
                        .build())
                .build()
        );

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);

        TxInputBuilder.Result inputResult = InputBuilders.createFromSender(sender, changeAddress)
                .apply(context, outputs);

        System.out.println(inputResult.getInputs());

        assertThat(inputResult.getInputs()).hasSize(2);
        assertThat(inputResult.getInputs()).contains(
                new TransactionInput("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85", 1),
                new TransactionInput("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c", 1)
        );

        assertThat(inputResult.getChanges()).hasSize(1);
        assertThat(inputResult.getChanges().get(0).getAddress()).isEqualTo(changeAddress);
        assertThat(inputResult.getChanges().get(0).getValue().getCoin()).isEqualTo(BigInteger.valueOf(976666105 + 983172035).subtract(ONE_ADA.multiply(BigInteger.valueOf(100))));

        assertThat(inputResult.getChanges().get(0).getValue().getMultiAssets()).hasSize(2);
        assertThat(inputResult.getChanges().get(0).getValue().getMultiAssets()).contains(
                MultiAsset.builder()
                        .policyId(policyId)
                        .assets(List.of(
                                Asset.builder()
                                        .name(assetName)
                                        .value(BigInteger.valueOf(300000000 - 10000))
                                        .build(),
                                Asset.builder()
                                        .name(assetName2)
                                        .value(BigInteger.valueOf(700000000 - 20000)).build()
                        )).build(),
                MultiAsset.builder()
                        .policyId("6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7")
                        .assets(List.of(
                                Asset.builder()
                                        .name("0x")
                                        .value(BigInteger.valueOf(67000))
                                        .build()
                        )).build()
        );

        assertThat(context.getUtxos()).hasSize(2);
    }

    @Test
    void createFromSender_whenInsufficientADA() throws Exception {
        List<Utxo> utxos = loadUtxos(LIST_6);
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(utxos);
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(1), any())).willReturn(Collections.emptyList());

        List<TransactionOutput> outputs = new ArrayList<>();


        outputs.add(TransactionOutput.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .value(Value.builder().coin(BigInteger.valueOf(300000000))
                        .build())
                .build()
        );
        outputs.add(TransactionOutput.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .value(Value.builder().coin(BigInteger.valueOf(10000))
                        .build())
                .build()
        );

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);

        assertThrows(InsufficientBalanceException.class, () -> {
            TxInputBuilder.Result inputResult = InputBuilders.createFromSender(sender, changeAddress)
                    .apply(context, outputs);
        });
    }

    @Test
    void createFromSender_whenInsufficientToken() throws Exception {
        List<Utxo> utxos = loadUtxos(LIST_6);
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(utxos);
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(1), any())).willReturn(Collections.emptyList());

        Tuple<String, String> tuple = AssetUtil.getPolicyIdAndAssetName("777777d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7");//Unit
        String policyId = tuple._1;
        String assetName = tuple._2;

        List<TransactionOutput> outputs = new ArrayList<>();

        outputs.add(TransactionOutput.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .value(Value.builder().coin(BigInteger.valueOf(300000))
                        .multiAssets(List.of(MultiAsset.builder()
                                .policyId(policyId)
                                .assets(List.of(
                                        Asset.builder()
                                                .name(assetName)
                                                .value(BigInteger.valueOf(10001))
                                                .build()
                                )).build()
                        ))
                        .build())
                .build()
        );
        outputs.add(TransactionOutput.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .value(Value.builder().coin(BigInteger.valueOf(10000))
                        .build())
                .build()
        );

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);

        assertThrows(InsufficientBalanceException.class, () -> {
            TxInputBuilder.Result inputResult = InputBuilders.createFromSender(sender, changeAddress)
                    .apply(context, outputs);
        });
    }

    @Test
    void createFromUtxos() throws Exception {
        List<Utxo> utxos = List.of(
                Utxo.builder()
                        .txHash("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85")
                        .outputIndex(1)
                        .amount(List.of(
                                Amount.builder()
                                        .unit("lovelace")
                                        .quantity(BigInteger.valueOf(10000000)).build(),
                                Amount.builder()
                                        .unit("777777d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7")
                                        .quantity(BigInteger.valueOf(5000)).build()
                        )).build(),
                Utxo.builder()
                        .txHash("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c")
                        .outputIndex(1)
                        .amount(List.of(
                                Amount.builder()
                                        .unit("lovelace")
                                        .quantity(BigInteger.valueOf(5000000)).build(),
                                Amount.builder()
                                        .unit("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e")
                                        .quantity(BigInteger.valueOf(10000)).build(),
                                Amount.builder()
                                        .unit("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96926174636f766e")
                                        .quantity(BigInteger.valueOf(8000)).build()

                        )).build()
        );

        List<TransactionOutput> outputs = new ArrayList<>();
        MultiAsset ma = AssetUtil.getMultiAssetFromUnitAndAmount("777777d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7", BigInteger.valueOf(1000));
        MultiAsset ma1 = AssetUtil.getMultiAssetFromUnitAndAmount("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e", BigInteger.valueOf(2000));

        MultiAsset ma2 = AssetUtil.getMultiAssetFromUnitAndAmount("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e", BigInteger.valueOf(500));

        outputs.add(TransactionOutput.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .value(Value.builder().coin(BigInteger.valueOf(2000000))
                        .multiAssets(List.of(ma, ma1))
                        .build())
                .build()
        );
        outputs.add(TransactionOutput.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .value(Value.builder().coin(BigInteger.valueOf(1000000))
                        .multiAssets(List.of(ma2))
                        .build())
                .build()
        );

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
        TxInputBuilder.Result inputResult = InputBuilders.createFromUtxos(utxos, changeAddress)
                .apply(context, outputs);

        assertThat(inputResult.getInputs()).hasSize(2);
        assertThat(inputResult.getInputs()).contains(
                new TransactionInput("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85", 1),
                new TransactionInput("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c", 1)
        );

        assertThat(inputResult.getChanges()).hasSize(1);
        assertThat(inputResult.getChanges().get(0)).isEqualTo(
                TransactionOutput.builder()
                        .address(changeAddress)
                        .value(Value.builder()
                                .coin(BigInteger.valueOf(12000000))
                                .multiAssets(
                                        List.of(
                                                AssetUtil.getMultiAssetFromUnitAndAmount("777777d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7", BigInteger.valueOf(4000)),

                                                //These two have same policy id
                                                AssetUtil.getMultiAssetFromUnitAndAmount("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e", BigInteger.valueOf(7500))
                                                        .add(AssetUtil.getMultiAssetFromUnitAndAmount("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96926174636f766e", BigInteger.valueOf(8000)))
                                        )
                                )
                                .build())
                        .build()
        );

        assertThat(context.getUtxos()).hasSize(2);
    }

    @Test
    void createFromUtxos_withSupplierDatumHash() {
        List<Utxo> utxos = List.of(
                Utxo.builder()
                        .txHash("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85")
                        .outputIndex(1)
                        .amount(List.of(
                                Amount.builder()
                                        .unit("lovelace")
                                        .quantity(BigInteger.valueOf(10000000)).build()
                        )).build()
        );

        List<TransactionOutput> outputs = new ArrayList<>();

        outputs.add(TransactionOutput.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .value(Value.builder().coin(BigInteger.valueOf(2000000))
                        .build())
                .build()
        );
        outputs.add(TransactionOutput.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .value(Value.builder().coin(BigInteger.valueOf(1000000))
                        .build())
                .build()
        );

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
        TxInputBuilder.Result inputResult = InputBuilders.createFromUtxos(() -> utxos, changeAddress, HexUtil.encodeHexString("somedatum_hash".getBytes(StandardCharsets.UTF_8)))
                .apply(context, outputs);

        assertThat(inputResult.getInputs()).hasSize(1);
        assertThat(inputResult.getInputs()).contains(
                new TransactionInput("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85", 1)
        );

        assertThat(inputResult.getChanges()).hasSize(1);
        assertThat(inputResult.getChanges()).contains(
                TransactionOutput.builder()
                        .address(changeAddress)
                        .value(Value.builder()
                                .coin(BigInteger.valueOf(7000000))
                                .build())
                        .datumHash("somedatum_hash".getBytes(StandardCharsets.UTF_8))
                        .build()
        );

        assertThat(context.getUtxos()).hasSize(1);
    }

    @Test
    void createFromUtxos_withDatumObj() throws CborException, CborSerializationException {
        List<Utxo> utxos = List.of(
                Utxo.builder()
                        .txHash("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85")
                        .outputIndex(1)
                        .amount(List.of(
                                Amount.builder()
                                        .unit("lovelace")
                                        .quantity(BigInteger.valueOf(10000000)).build()
                        )).build()
        );

        List<TransactionOutput> outputs = new ArrayList<>();

        outputs.add(TransactionOutput.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .value(Value.builder().coin(BigInteger.valueOf(2000000))
                        .build())
                .build()
        );
        outputs.add(TransactionOutput.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .value(Value.builder().coin(BigInteger.valueOf(1000000))
                        .build())
                .build()
        );

        A datum = new A();
        datum.name = "John";
        datum.age = 30;

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
        TxInputBuilder.Result inputResult = InputBuilders.createFromUtxos(utxos, changeAddress, datum)
                .apply(context, outputs);

        assertThat(inputResult.getInputs()).hasSize(1);
        assertThat(inputResult.getInputs()).contains(
                new TransactionInput("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85", 1)
        );

        String expectedDatumHash = Configuration.INSTANCE.getPlutusObjectConverter().toPlutusData(datum).getDatumHash();
        assertThat(inputResult.getChanges()).hasSize(1);
        assertThat(inputResult.getChanges()).contains(
                TransactionOutput.builder()
                        .address(changeAddress)
                        .value(Value.builder()
                                .coin(BigInteger.valueOf(7000000))
                                .build())
                        .datumHash(HexUtil.decodeHexString(expectedDatumHash))
                        .build()
        );
    }

    //TODO -- Remove when the deprecated method is removed
    @Test
    void createFromUtxos_whenNoChangeAddress() {
        List<Utxo> utxos = List.of(
                Utxo.builder()
                        .txHash("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85")
                        .outputIndex(1)
                        .amount(List.of(
                                Amount.builder()
                                        .unit("lovelace")
                                        .quantity(BigInteger.valueOf(10000000)).build()
                        )).build()
        );

        List<TransactionOutput> outputs = new ArrayList<>();

        outputs.add(TransactionOutput.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .value(Value.builder().coin(BigInteger.valueOf(2000000))
                        .build())
                .build()
        );
        outputs.add(TransactionOutput.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .value(Value.builder().coin(BigInteger.valueOf(1000000))
                        .build())
                .build()
        );

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
        TxInputBuilder.Result inputResult = InputBuilders.createFromUtxos(utxos)
                .apply(context, outputs);

        assertThat(inputResult.getInputs()).hasSize(1);
        assertThat(inputResult.getInputs()).contains(
                new TransactionInput("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85", 1)
        );

        assertThat(inputResult.getChanges()).hasSize(0);
    }

    //TODO -- Remove when the deprecated method is removed
    @Test
    void createFromUtxos_whenUtxosFromSupplier() {
        Utxo utxo = Utxo.builder()
                .txHash("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85")
                .outputIndex(1)
                .amount(List.of(
                        Amount.builder()
                                .unit("lovelace")
                                .quantity(BigInteger.valueOf(10000000)).build()
                )).build();

        List<TransactionOutput> outputs = new ArrayList<>();

        outputs.add(TransactionOutput.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .value(Value.builder().coin(BigInteger.valueOf(2000000))
                        .build())
                .build()
        );
        outputs.add(TransactionOutput.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .value(Value.builder().coin(BigInteger.valueOf(1000000))
                        .build())
                .build()
        );

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
        TxInputBuilder.Result inputResult = InputBuilders.createFromUtxos(() -> List.of(utxo))
                .apply(context, outputs);

        assertThat(inputResult.getInputs()).hasSize(1);
        assertThat(inputResult.getInputs()).contains(
                new TransactionInput("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85", 1)
        );

        assertThat(inputResult.getChanges()).hasSize(0);
    }

    @Test
    void testSelectorFromUtxos2() {
    }

    @Test
    void collateralFrom() {
    }

    @Test
    void testCollateralFrom() {
    }

    @Test
    void testCollateralFrom1() {
    }

    @Constr
    class A {
        @PlutusField
        String name;

        @PlutusField
        int age;
    }

    @Nested
    class ReferenceInputTests {

        @Test
        void referenceInputsFrom_utxos() {
            List<Utxo> utxos = getUtxos();

            TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);

            Transaction transaction = new Transaction();
            TxBuilder txBuilder = InputBuilders.referenceInputsFromUtxos(utxos);
            txBuilder.apply(context, transaction);

            assertThat(transaction.getBody().getReferenceInputs()).hasSize(2);
            assertThat(transaction.getBody().getReferenceInputs()).hasSameElementsAs(
                    List.of(
                            new TransactionInput("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85", 1),
                            new TransactionInput("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c", 1)
                    )
            );
        }

        @Test
        void referenceInputsFrom_withSupplierParam() {
            Supplier<List<Utxo>> refInputsSupplier = new Supplier<List<Utxo>>() {
                @Override
                public List<Utxo> get() {
                    return getUtxos();
                }
            };

            TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);

            Transaction transaction = new Transaction();
            TxBuilder txBuilder = InputBuilders.referenceInputsFromUtxos(refInputsSupplier);
            txBuilder.apply(context, transaction);

            assertThat(transaction.getBody().getReferenceInputs()).hasSize(2);
            assertThat(transaction.getBody().getReferenceInputs()).hasSameElementsAs(
                    List.of(
                            new TransactionInput("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85", 1),
                            new TransactionInput("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c", 1)
                    )
            );
        }

        @Test
        void referenceInputsFromTxInputs() {
            List<TransactionInput> txInputs = List.of(
                    new TransactionInput("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85", 1),
                    new TransactionInput("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c", 1)
            );

            TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);

            Transaction transaction = new Transaction();
            TxBuilder txBuilder = InputBuilders.referenceInputsFrom(txInputs);
            txBuilder.apply(context, transaction);

            assertThat(transaction.getBody().getReferenceInputs()).hasSize(2);
            assertThat(transaction.getBody().getReferenceInputs()).hasSameElementsAs(
                    List.of(
                            new TransactionInput("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85", 1),
                            new TransactionInput("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c", 1)
                    )
            );
        }

        private List<Utxo> getUtxos() {
            List<Utxo> utxos = List.of(
                    Utxo.builder()
                            .txHash("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85")
                            .outputIndex(1)
                            .amount(List.of(
                                    Amount.builder()
                                            .unit("lovelace")
                                            .quantity(BigInteger.valueOf(10000000)).build(),
                                    Amount.builder()
                                            .unit("777777d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7")
                                            .quantity(BigInteger.valueOf(5000)).build()
                            )).build(),
                    Utxo.builder()
                            .txHash("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c")
                            .outputIndex(1)
                            .amount(List.of(
                                    Amount.builder()
                                            .unit("lovelace")
                                            .quantity(BigInteger.valueOf(5000000)).build(),
                                    Amount.builder()
                                            .unit("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e")
                                            .quantity(BigInteger.valueOf(10000)).build()

                            )).build()
            );
            return utxos;
        }
    }

    @Nested
    class MergeOutputsTest {
        @Test
        void createFromSender_whenMergeOutputsIsTrue() throws IOException, CborSerializationException {
            List<Utxo> utxos = loadUtxos(LIST_9);
            given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(utxos);
            given(utxoSupplier.getPage(anyString(), anyInt(), eq(1), any())).willReturn(Collections.emptyList());

            Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("policy", 1);

            List<MultiAsset> mintAssets = new ArrayList<>();
            List<Asset> assets = new ArrayList<>();
            assets.add(new Asset("asset1", BigInteger.valueOf(1)));
            mintAssets.add(
                    MultiAsset.builder()
                            .policyId(policy.getPolicyId())
                            .assets(assets).build()
            );

            List<TransactionOutput> outputs = new ArrayList<>();
            outputs.add(TransactionOutput.builder()
                    .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                    .value(Value.builder().coin(adaToLovelace(1)).build())
                    .build()
            );
            outputs.add(TransactionOutput.builder()
                    .address(sender)
                    .value(Value.builder().coin(BigInteger.ZERO)
                            .multiAssets(mintAssets).build()).build());

            TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
            context.setMintMultiAssets(mintAssets);

            TxInputBuilder.Result inputResult = InputBuilders.createFromSender(sender, sender)
                    .apply(context, outputs);

            //assert inputs
            assertThat(inputResult.getInputs()).hasSize(1);
            assertThat(outputs.size()).isEqualTo(1);
            assertThat(inputResult.getChanges()).hasSize(1);
            assertThat(inputResult.getChanges().get(0).getAddress()).isEqualTo(sender);
            assertThat(inputResult.getChanges().get(0).getValue().getCoin()).isGreaterThan(adaToLovelace(1));
            assertThat(inputResult.getChanges().get(0).getValue().getMultiAssets()).hasSize(1);

            assertThat(outputs.get(0).getAddress()).isEqualTo("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c");
            assertThat(outputs.get(0).getValue().getCoin()).isEqualTo(adaToLovelace(1));
            assertThat(outputs.get(0).getValue().getMultiAssets()).isEmpty();
        }

        @Test
        void createFromSender_whenMergeOutputsIsFalse() throws IOException, CborSerializationException {
            List<Utxo> utxos = loadUtxos(LIST_10);
            given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(utxos);
            given(utxoSupplier.getPage(anyString(), anyInt(), eq(1), any())).willReturn(Collections.emptyList());

            Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("policy", 1);
            List<MultiAsset> mintAssets = new ArrayList<>();
            List<Asset> assets = new ArrayList<>();
            assets.add(new Asset("asset1", BigInteger.valueOf(1)));
            mintAssets.add(
                    MultiAsset.builder()
                            .policyId(policy.getPolicyId())
                            .assets(assets).build()
            );

            List<TransactionOutput> outputs = new ArrayList<>();
            outputs.add(TransactionOutput.builder()
                    .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                    .value(Value.builder().coin(adaToLovelace(1)).build())
                    .build()
            );
            outputs.add(TransactionOutput.builder()
                    .address(sender)
                    .value(Value.builder().coin(adaToLovelace(1.2))
                            .multiAssets(mintAssets).build()).build());

            TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
            context.mergeOutputs(false);
            context.setMintMultiAssets(mintAssets);

            TxInputBuilder.Result inputResult = InputBuilders.createFromSender(sender, sender)
                    .apply(context, outputs);

            //assert inputs
            assertThat(inputResult.getInputs()).hasSize(1);
            assertThat(outputs.size()).isEqualTo(2);
            assertThat(inputResult.getChanges()).hasSize(1);
            assertThat(inputResult.getChanges().get(0).getAddress()).isEqualTo(sender);
            assertThat(inputResult.getChanges().get(0).getValue().getCoin()).isGreaterThan(adaToLovelace(1));
            assertThat(inputResult.getChanges().get(0).getValue().getMultiAssets()).hasSize(0);

            assertThat(outputs.get(0).getAddress()).isEqualTo("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c");
            assertThat(outputs.get(0).getValue().getCoin()).isEqualTo(adaToLovelace(1));
            assertThat(outputs.get(0).getValue().getMultiAssets()).isEmpty();

            assertThat(outputs.get(1).getAddress()).isEqualTo(sender);
            assertThat(outputs.get(1).getValue().getCoin()).isEqualTo(adaToLovelace(1.2));
            assertThat(outputs.get(1).getValue().getMultiAssets()).hasSize(1);
        }
    }
}
