package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.codec.FlowFormat;
import com.bloxbean.cardano.client.txflow.codec.FlowParseOptions;
import com.bloxbean.cardano.client.txflow.codec.FlowSchemaVersion;
import com.bloxbean.cardano.client.txflow.codec.FlowWriteOptions;
import com.bloxbean.cardano.client.txflow.codec.TxFlowCodec;
import com.bloxbean.cardano.client.txflow.compile.FlowCompilationRequest;
import com.bloxbean.cardano.client.txflow.compile.TxFlowCompiler;
import com.bloxbean.cardano.client.txflow.model.FlowBindings;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ADR 0008 invariant I2: flows that use no QuickTx extension compile, write and snapshot
 * byte-identically to the pre-extension implementation. The expected digests were captured from
 * {@code dfd8540d} before any ADR 0008 change (and updated for the portable {@code v1alpha1 -> v1}
 * {@code api_version} promotion) and must never be regenerated from the code under test.
 */
class ExtensionFreeGoldenTest {
    private static final String PORTABLE = """
            api_version: txflow.cardano-client.dev/v1
            kind: TxFlow
            metadata: {name: typed-payment, version: "1.0.0"}
            spec:
              network: preview
              parameters:
                beneficiary: {type: address, required: true}
                amount: {type: integer, minimum: 1000000, maximum: 10000000}
              execution:
                mode: PIPELINED
              steps:
                - id: prepare
                  outputs:
                    staging:
                      select: {output_index: 0}
                      expect: exactly_one
                  transaction:
                    tx:
                      from_ref: account://sender
                      intents:
                        - type: payment
                          address: '${{ inputs.beneficiary }}'
                          amounts:
                            - unit: lovelace
                              quantity: '${{ inputs.amount }}'
                    context:
                      valid_to_slot: 5000
                - id: audit
                  needs: [prepare]
                  funding_from: [prepare]
                  transaction:
                    tx:
                      inputs:
                        - type: collect_from
                          refs:
                            - flow_output: {step: prepare, output: staging}
                      intents: []
            """;

    private static final FlowBindings BINDINGS = FlowBindings.builder()
            .put("beneficiary", "addr_test1qpz")
            .put("amount", 5_000_000L)
            .build();

    private static TxFlow javaFlow() {
        Tx first = new Tx()
                .payToAddress("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qqhds8jw7",
                        Amount.ada(2))
                .payToAddress("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qqhds8jw7",
                        Amount.asset("4b3459fd18a1dbabe207cd19c9951a9fac9f5c0f9c384e3d97efba26", "746f6b656e", BigInteger.TEN))
                .attachMetadata(com.bloxbean.cardano.client.metadata.MetadataBuilder.createMetadata()
                        .put(674, "golden"))
                .from("addr_test1vzpwq95z3xyum8vqndgdd9mdnmafh3djcxnc6jemlgdmswcve6tkw");
        Tx second = new Tx()
                .payToAddress("addr_test1vzpwq95z3xyum8vqndgdd9mdnmafh3djcxnc6jemlgdmswcve6tkw",
                        Amount.ada(1))
                .fromRef("account://sender");
        return TxFlow.builder("java-golden")
                .withDescription("golden")
                .addStep(FlowStep.builder("first")
                        .withTxPlan(TxPlan.from(first).validTo(9000L).feePayerRef("account://payer"))
                        .build())
                .addStep(FlowStep.builder("second")
                        .needs("first")
                        .fundsFrom("first")
                        .withTxPlan(TxPlan.from(second).withSigner("account://sender"))
                        .build())
                .build();
    }

    @Test
    void portableCompiledFingerprintIsUnchanged() {
        TxFlow parsed = TxFlowCodec.standard().parse(PORTABLE, FlowParseOptions.serverDefaults()).requireFlow();
        String fingerprint = new TxFlowCompiler().compile(FlowCompilationRequest.of(parsed, BINDINGS))
                .requireCompiledFlow().getFingerprint();
        assertGolden("portable.fingerprint", "270a822adef574ab2b6c748e13e21d489189f9b7ad1f58bd60e522ad9a55aa22", fingerprint);
    }

    @Test
    void portableWriteIsUnchanged() {
        TxFlow parsed = TxFlowCodec.standard().parse(PORTABLE, FlowParseOptions.serverDefaults()).requireFlow();
        assertGolden("portable.yaml", "2f300c655c049993b31fb8c2ed0d77956337bbaf3fabba050e262eb21c325eb3", sha256(TxFlowCodec.standard().write(parsed,
                FlowWriteOptions.of(FlowFormat.YAML, FlowSchemaVersion.V1))));
        assertGolden("portable.json", "1a53ad8d26345f7ff0581aa41578f82e8f7b5aefd9e8defbf9610e5b2d7f76a8", sha256(TxFlowCodec.standard().write(parsed,
                FlowWriteOptions.of(FlowFormat.JSON, FlowSchemaVersion.V1))));
    }

    @Test
    void javaCompiledFingerprintAndWriteAreUnchanged() {
        TxFlow flow = javaFlow();
        assertGolden("java.fingerprint", "4527afe538142db9daa4a6d15b09b71e2dac53dbef9f52f449498c2bfa34f1fc", new TxFlowCompiler()
                .compile(FlowCompilationRequest.of(flow, FlowBindings.empty()))
                .requireCompiledFlow().getFingerprint());
        assertGolden("java.yaml", "76d3362af20d4c222cd5c38f23bd93ddfb3a22d987add0fc871f1ce95388fcd5", sha256(TxFlowCodec.standard().write(flow,
                FlowWriteOptions.of(FlowFormat.YAML, FlowSchemaVersion.V1))));
        assertGolden("java.legacy-yaml", "dd94e0e7a75f63ccde03af30e6203c00a2fa358914a21ea44fdeb51811bde64d", sha256(flow.toYaml()));
    }

    /**
     * Number-like strings in an extension-free flow are written unquoted, as before; only flows
     * with extension bindings quote them.
     */
    @Test
    void numberLikeStringsAreWrittenAsBefore() {
        TxFlow flow = TxFlow.builder("123")
                .withDefinitionVersion("1")
                .addAnnotation("number", "456")
                .addStep(FlowStep.builder("789")
                        .withTxPlan(TxPlan.from(new Tx()
                                .payToAddress("addr_test1vzpwq95z3xyum8vqndgdd9mdnmafh3djcxnc6jemlgdmswcve6tkw", Amount.ada(1))
                                .fromRef("account://sender")).withSigner("account://sender"))
                        .build())
                .build();
        assertGolden("number-like.yaml", "f74f45f5fcfaf2e91e788761edff44b4176b14aefac864f87c736be764060040", sha256(TxFlowCodec.standard().write(flow,
                FlowWriteOptions.of(FlowFormat.YAML, FlowSchemaVersion.V1))));
    }

    @Test
    void planSnapshotsAreUnchanged() {
        TxFlow flow = javaFlow();
        FlowExecutionContext context = new FlowExecutionContext(flow.getId());
        assertGolden("snapshot.first", "f534964532e456690c838f93eba41e16ba3318197c80d876db9d986806470551", sha256(context.snapshotPlan(flow.getSteps().get(0).getTxPlan())));
        assertGolden("snapshot.second", "bc28a497dc6993fe41b67b2823385fe5a84d3b7892734bcb0e641f23c3213aa2", sha256(context.snapshotPlan(flow.getSteps().get(1).getTxPlan())));
    }

    private static void assertGolden(String name, String expected, String actual) {
        assertEquals(expected, actual, name);
    }

    private static String sha256(String value) {
        try {
            return HexUtil.encodeHexString(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
