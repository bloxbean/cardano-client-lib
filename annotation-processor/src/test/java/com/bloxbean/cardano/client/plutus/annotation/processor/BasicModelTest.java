package com.bloxbean.cardano.client.plutus.annotation.processor;


import com.bloxbean.cardano.client.plutus.annotation.processor.model.BasicModel;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.converter.BasicModelConverter;
import com.bloxbean.cardano.client.plutus.blueprint.type.Pair;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class BasicModelTest {
    @Test
    void testBasicModel() {
        BasicModel basicModel = new BasicModel();
        basicModel.setI(10);
        basicModel.setS("Hello");
        basicModel.setB(new byte[] {1,2,3});
        basicModel.setL(java.util.Arrays.asList("Hello", "World"));
        basicModel.setM(java.util.Map.of("Hello", java.math.BigInteger.valueOf(100), "World", java.math.BigInteger.valueOf(200)));
        basicModel.setBool(true);
        basicModel.setOpt(Optional.of("Optional Text"));
        basicModel.setPair1(new Pair<>(java.math.BigInteger.valueOf(100), "Hello"));

        BasicModelConverter basicModelConverter = new BasicModelConverter();
        String serHex = basicModelConverter.serializeToHex(basicModel);

        BasicModel deBasicModel = basicModelConverter.deserialize(serHex);
        String doubleSerHex = basicModelConverter.serializeToHex(deBasicModel);

        assertThat(deBasicModel).isEqualTo(basicModel);

        assertThat(serHex).isEqualTo(doubleSerHex);
        assertThat(deBasicModel).isEqualTo(basicModel);
        String expected = "d8799f0a4548656c6c6f430102039f4548656c6c6f45576f726c64ffa24548656c6c6f186445576f726c6418c8d87a80d8799f4d4f7074696f6e616c2054657874ff9f18644548656c6c6fffff";
        assertThat(serHex).isEqualTo(expected);
    }
}
