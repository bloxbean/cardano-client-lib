package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.BaseTest;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.ProtocolParams;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.function.MinAdaChecker;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.PolicyUtil;
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

import static com.bloxbean.cardano.client.common.CardanoConstants.ONE_ADA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MinAdaCheckersTest extends BaseTest {

    @Mock
    BackendService backendService;

    @Mock
    EpochService epochService;
    ProtocolParams protocolParams;

    @BeforeEach
    public void setup() throws IOException, ApiException {
        MockitoAnnotations.openMocks(this);

        protocolParamJsonFile = "protocol-params.json";
        protocolParams = (ProtocolParams) loadObjectFromJson("protocol-parameters", ProtocolParams.class);

        given(backendService.getEpochService()).willReturn(epochService);
        given(epochService.getProtocolParameters()).willReturn(Result.success(protocolParams.toString()).withValue(protocolParams).code(200));
    }

    @Test
    void minAdaChecker() {
        TxBuilderContext context = new TxBuilderContext(backendService);
        TransactionOutput output = TransactionOutput.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .value(Value.builder().coin(BigInteger.valueOf(1000)).build())
                .build();

        MinAdaChecker minAdaChecker = MinAdaCheckers.minAdaChecker();
        BigInteger additionalAda = minAdaChecker.apply(context, output);

        assertThat(additionalAda).isEqualTo(ONE_ADA.subtract(BigInteger.valueOf(1000)));
    }

    @Test
    void minAdaChecker_whenNoAdditional() {
        TxBuilderContext context = new TxBuilderContext(backendService);
        TransactionOutput output = TransactionOutput.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .value(Value.builder().coin(ONE_ADA.multiply(BigInteger.valueOf(5))).build())
                .build();

        MinAdaChecker minAdaChecker = MinAdaCheckers.minAdaChecker();
        BigInteger additionalAda = minAdaChecker.apply(context, output);

        assertThat(additionalAda).isEqualTo(BigInteger.ZERO);
    }

    //TODO -- Uncomment after min ada PR to fix datum hash issue
//    @Test
    void minAdaChecker_withMultiAsset() throws Exception {
        TxBuilderContext context = new TxBuilderContext(backendService);

        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("xyz-policy", 1);
        MultiAsset multiAsset = MultiAsset.builder()
                .policyId(policy.getPolicyId())
                .assets(List.of(
                        Asset.builder()
                                .name("xyz")
                                .value(BigInteger.valueOf(200))
                                .build()
                )).build();

        Integer datum = 200;

        TransactionOutput output = TransactionOutput.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .value(Value.builder()
                        .coin(ONE_ADA)
                        .multiAssets(List.of(multiAsset))
                        .build()
                )
                .datum(datum)
                .build();

        MinAdaChecker minAdaChecker = MinAdaCheckers.minAdaChecker();
        BigInteger additionalAda = minAdaChecker.apply(context, output);

        assertThat(additionalAda).isEqualTo(BigInteger.valueOf(344798));
    }
}
