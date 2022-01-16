package com.bloxbean.cardano.client.plutus;

import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.PlutusField;
import com.bloxbean.cardano.client.plutus.api.PlutusObjectConverter;
import com.bloxbean.cardano.client.plutus.impl.DefaultPlutusObjectConverter;
import com.bloxbean.cardano.client.transaction.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.transaction.spec.BytesPlutusData;
import com.bloxbean.cardano.client.transaction.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.transaction.spec.ListPlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.Builder;
import lombok.Data;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPlutusObjectConverterTest {

    @Test
    void toPlutusData() throws CborSerializationException {
        A a = A.builder()
                .l(100L)
                .b(BigInteger.valueOf(30000000))
                .name("hello")
                .address("0x5468697320697320612074657374")
                .build();

        PlutusObjectConverter plutusObjectConverter = new DefaultPlutusObjectConverter();
        ConstrPlutusData constrPlutusData = plutusObjectConverter.toPlutusData(a);

        ConstrPlutusData expected = ConstrPlutusData.of(1,
                BigIntPlutusData.of(100L),
                BigIntPlutusData.of(30000000),
                BytesPlutusData.of("hello"),
                BytesPlutusData.of(HexUtil.decodeHexString("0x5468697320697320612074657374")));

        System.out.println(constrPlutusData.serialize());
        assertThat(constrPlutusData.serialize()).isEqualTo(expected.serialize());
    }

    @Test
    void toPlutusData_whenCustomObjField() throws CborSerializationException {
        A a = A.builder()
                .l(100L)
                .b(BigInteger.valueOf(30000000))
                .name("hello")
                .address("0x5468697320697320612074657374")
                .build();

        SuperA sa = SuperA.builder()
                .a(a)
                .c(BigInteger.valueOf(200))
                .country("Africa")
                .days(Arrays.asList("Monday", "Tuesday", "Sunday"))
                .ints(Arrays.asList(4, 5005, 6, 89000000))
                .build();

        PlutusObjectConverter plutusObjectConverter = new DefaultPlutusObjectConverter();
        ConstrPlutusData constrPlutusData = plutusObjectConverter.toPlutusData(sa);

        ConstrPlutusData expected = ConstrPlutusData.of(0,
                ConstrPlutusData.of(1,
                        BigIntPlutusData.of(100L),
                        BigIntPlutusData.of(30000000),
                        BytesPlutusData.of("hello"),
                        BytesPlutusData.of(HexUtil.decodeHexString("0x5468697320697320612074657374"))
                ),
                BigIntPlutusData.of(200),
                BytesPlutusData.of("Africa"),
                ListPlutusData.of(
                        BytesPlutusData.of("Monday"),
                        BytesPlutusData.of("Tuesday"),
                        BytesPlutusData.of("Sunday")),
                ListPlutusData.of(
                        BigIntPlutusData.of(4),
                        BigIntPlutusData.of(5005),
                        BigIntPlutusData.of(6),
                        BigIntPlutusData.of(89000000)
                )
        );

        System.out.println(constrPlutusData.serialize());
        assertThat(constrPlutusData.serialize()).isEqualTo(expected.serialize());
    }
}

@Data
@Builder
@Constr(alternative = 1)
class A {
    @PlutusField
    Long l;

    @PlutusField
    BigInteger b;

    @PlutusField
    String name;

    @PlutusField
    String address;
}

@Data
@Builder
@Constr
class SuperA {

    @PlutusField
    A a;

    @PlutusField
    BigInteger c;

    @PlutusField
    String country;

    @Builder.Default
    @PlutusField
    List<String> days = new ArrayList<>();

    @Builder.Default
    @PlutusField
    List<Integer> ints = new ArrayList<>();

}
