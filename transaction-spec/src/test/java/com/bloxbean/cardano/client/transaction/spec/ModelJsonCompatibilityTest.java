package com.bloxbean.cardano.client.transaction.spec;

import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.spec.UnitInterval;
import com.bloxbean.cardano.client.transaction.spec.cert.PoolRegistration;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR 0004 W1: the JSON of models built with the API before ADR 0004 is what e59e9356 wrote, as the fields ADR 0004
 * added are left out while they are null.
 */
class ModelJsonCompatibilityTest {
    private static final String CAPTURED = "/compat/adr0004-model-json.txt";

    @Test
    void jsonOfModelsBuiltAsBeforeIsUnchanged() throws Exception {
        ProtocolParamUpdate update = ProtocolParamUpdate.builder().minFeeA(BigInteger.ONE).maxTxSize(16384)
                .maxTxExUnits(new ExUnits(BigInteger.ONE, BigInteger.TWO)).build();
        PoolRegistration pool = PoolRegistration.builder().operator(HexUtil.decodeHexString("11".repeat(28)))
                .vrfKeyHash(HexUtil.decodeHexString("22".repeat(32))).pledge(BigInteger.ONE).cost(BigInteger.TWO)
                .margin(new UnitInterval(BigInteger.ONE, BigInteger.TWO)).rewardAccount("e0" + "33".repeat(28))
                .poolOwners(Set.of("44".repeat(28))).relays(List.of()).build();
        TransactionBody body = TransactionBody.builder()
                .inputs(List.of(new TransactionInput("55".repeat(32), 0))).fee(BigInteger.TEN).ttl(5)
                .certs(List.of(pool)).build();
        Transaction transaction = Transaction.builder().body(body)
                .witnessSet(TransactionWitnessSet.builder().build())
                .auxiliaryData(AuxiliaryData.builder().build()).build();
        Map<String, String> captured = captured();

        assertThat(JsonUtil.getPrettyJson(update)).isEqualTo(captured.get("UPDATE"));
        assertThat(JsonUtil.getPrettyJson(pool)).isEqualTo(captured.get("POOL"));
        assertThat(JsonUtil.getPrettyJson(body)).isEqualTo(captured.get("BODY"));
        assertThat(transaction.toJson()).isEqualTo(captured.get("TX"));
    }

    private static Map<String, String> captured() throws IOException {
        try (InputStream in = ModelJsonCompatibilityTest.class.getResourceAsStream(CAPTURED)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .collect(Collectors.toMap(line -> line.substring(0, line.indexOf('=')),
                            line -> line.substring(line.indexOf('=') + 1).replace("\\n", "\n")));
        }
    }
}
