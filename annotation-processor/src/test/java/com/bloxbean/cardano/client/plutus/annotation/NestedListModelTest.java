package com.bloxbean.cardano.client.plutus.annotation;

import com.bloxbean.cardano.client.plutus.annotation.model.A;
import com.bloxbean.cardano.client.plutus.annotation.model.NestedModel;
import com.bloxbean.cardano.client.plutus.annotation.model.NestedModelConverter;
import com.bloxbean.cardano.client.plutus.annotation.model.Subject;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class NestedListModelTest {

    @Test
    public void serialize() {
        var model = new NestedModel();
        //write code to initialize model
        model.setStudent(StudentTest.getStudent());
        model.setOpt(List.of(Optional.of("Hello"), Optional.of("World")));
        model.setOptionalString(Optional.of(List.of("Hello", "World")));
        model.setField1(Optional.of(List.of("Hello", "World")));
        model.setField2(Optional.of(Map.of("Hello", BigInteger.valueOf(100), "World", BigInteger.valueOf(200))));
        model.setField3(Optional.of(List.of(Map.of("Hello", BigInteger.valueOf(100), "World", BigInteger.valueOf(200)))));
        model.setField4(Optional.of(List.of(List.of(Map.of(BigInteger.valueOf(100), Map.of("Hello", BigInteger.valueOf(100), "World", BigInteger.valueOf(200)))))));
        model.setField5(Optional.of(Map.of("Hello", List.of(BigInteger.valueOf(100), BigInteger.valueOf(200)), "World", List.of(BigInteger.valueOf(300), BigInteger.valueOf(400)))));
        model.setField6(Optional.of(Map.of("Hello", List.of(Map.of("Hello".getBytes(), BigInteger.valueOf(100)), Map.of("World".getBytes(), BigInteger.valueOf(200))), "World", List.of(Map.of("Hello".getBytes(), BigInteger.valueOf(300)), Map.of("World".getBytes(), BigInteger.valueOf(400))))));
        model.setField7(Optional.of(Map.of("Hello", "World")));
        model.setField8(Optional.of(List.of(List.of(List.of("Hello", "World")))));
        model.setField9(List.of("Hello", "World"));
        model.setField10(List.of(List.of(List.of("Hello", "World"))));
        model.setField11(List.of(List.of(List.of("Hello", "World"))));
        model.setField12(List.of(Map.of("Hello", "World")));
        model.setField13(List.of(List.of(Map.of("Hello", BigInteger.valueOf(100), "World", BigInteger.valueOf(200)))));
        model.setField14(Map.of("Hello", BigInteger.valueOf(100), "World", BigInteger.valueOf(200)));
        model.setField15(Map.of(Map.of("Hello", "World"), Map.of(Long.valueOf(100), BigInteger.valueOf(200))));
        model.setField16(Map.of("Hello", Map.of("World", BigInteger.valueOf(100))));
        model.setField17(Map.of("Hello", List.of(Map.of("World", List.of("Hello", "World")))));
        model.setField18(Optional.of(List.of(List.of(List.of(dummySubject())))));

        model.setNestedOpt(List.of(Optional.of("hi"), Optional.of("there")));
        model.setNestedOpt1(List.of(Optional.of(List.of(StudentTest.getStudent(), StudentTest.getStudent()))));
        model.setNestedOpt2(Map.of(Optional.of("one"), BigInteger.ONE, Optional.of("two"), BigInteger.TWO));

        model.setNestedOpt3(Map.of(Optional.of(StudentTest.getStudent()), BigInteger.TWO));
        model.setNestedOpt4(Map.of(BigInteger.ONE, Optional.of(StudentTest.getStudent())));

        //Serialize
        NestedModelConverter converter = new NestedModelConverter();
        String serializedHex = converter.serializeToHex(model);

        //Deserialize
        NestedModel deModel = converter.deserialize(serializedHex);

        byte[] doubleSerialize = converter.serialize(deModel);
        NestedModel doubleDeModel = converter.deserialize(doubleSerialize);

        assertThat(serializedHex).isEqualTo(HexUtil.encodeHexString(doubleSerialize));
    }

    protected static Subject dummySubject() {
        Subject subject1 = new Subject();
        subject1.setName("Maths");
        subject1.setMarks(90);
        subject1.setId(Optional.of(new byte[]{1, 2, 3}));
        subject1.setLogo(new byte[]{1, 2, 3, 4, 5});
        subject1.setNestedList(List.of(List.of(List.of("a", "b", "c"))));
        subject1.setNestedMap(Map.of("a", Map.of(new A("NameB"), "b")));
        subject1.setNestedListMap(List.of(List.of(Map.of("a", List.of(BigInteger.valueOf(1), BigInteger.valueOf(2))))));
        return subject1;
    }

}
