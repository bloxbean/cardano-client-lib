package com.bloxbean.cardano.client.plutus.annotation.processor;

import com.bloxbean.cardano.client.plutus.annotation.processor.model.PairModel;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.converter.PairModelConverter;
import com.bloxbean.cardano.client.plutus.blueprint.type.Pair;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class PairModelTest {

    @Test
    void testBasicModel() {
        Pair<Map<BigInteger, String>, String> pair1 = new Pair<>(Map.of(BigInteger.valueOf(100), "Hello", BigInteger.valueOf(200), "Hello2"), "World");
        Pair<String, Map<BigInteger, String>> pair2 = new Pair<>("Hello", Map.of(BigInteger.valueOf(100), "World", BigInteger.valueOf(300), "World3"));
        Pair<Map<String, byte[]>, Map<BigInteger, String>> pair3 = new Pair<>(Map.of("Hello", new byte[] {1,2,3}), Map.of(BigInteger.valueOf(400), "World4"));
        Pair<Map<String, byte[]>, List<BigInteger>> pair4 = new Pair<>(Map.of("Hello", new byte[] {1,2,3}), List.of(BigInteger.valueOf(100), BigInteger.valueOf(200)));
        Pair<Map<List<String>, byte[]>, List<BigInteger>> pair5 = new Pair<>(Map.of(List.of("Hello"), new byte[] {1,2,3}), List.of(BigInteger.valueOf(100), BigInteger.valueOf(200)));

        PairModel pairModel = new PairModel();
        pairModel.setPair1(pair1);
        pairModel.setPair2(pair2);
        pairModel.setPair3(pair3);
        pairModel.setPair4(pair4);
        pairModel.setPair5(pair5);

        PairModelConverter pairModelConverter = new PairModelConverter();
        String serHex = pairModelConverter.serializeToHex(pairModel);
        PairModel dePairModel = pairModelConverter.deserialize(serHex);

        String doubleSerHex = pairModelConverter.serializeToHex(dePairModel);

        assertThat(doubleSerHex).isEqualTo(serHex);
    }
}
