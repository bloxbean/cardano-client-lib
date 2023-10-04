package com.bloxbean.cardano.client.plutus.annotation;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.plutus.annotation.model.A;
import com.bloxbean.cardano.client.plutus.annotation.model.Student;
import com.bloxbean.cardano.client.plutus.annotation.model.StudentConverter;
import com.bloxbean.cardano.client.plutus.annotation.model.Subject;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.serializers.PlutusDataJsonConverter;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class StudentTest {

    @Test
    public String serialize() throws Exception {
        Student student = getStudent();

        StudentConverter converter
                = new StudentConverter();
        var constr = converter.toPlutusData(student);
        System.out.println(PlutusDataJsonConverter.toJson(constr));
        System.out.println(HexUtil.encodeHexString(CborSerializationUtil.serialize(constr.serialize())));
        System.out.println(student);
        return HexUtil.encodeHexString(CborSerializationUtil.serialize(constr.serialize()));
    }

    public static Student getStudent() {
        Student student = new Student();
        student.setName("John");
        student.setAge(20);
        student.setGender("M");
        student.setGraduated(true);
        student.setFullTime(true);
        student.setCardanoHolder(Optional.of(true));

        Subject subject1 = new Subject();
        subject1.setName("Maths");
        subject1.setMarks(90);
        subject1.setLogo(new byte[]{1, 2, 3, 4, 5});
        subject1.setId(Optional.of(new byte[]{1, 2}));
        subject1.setNestedList(List.of(List.of(List.of("a", "b", "c"))));
        subject1.setNestedMap(Map.of("a", Map.of(new A("NameB"), "b")));
        subject1.setNestedListMap(List.of(List.of(Map.of("a", List.of(BigInteger.valueOf(1), BigInteger.valueOf(2))))));

        Subject subject2 = new Subject();
        subject2.setName("Science");
        subject2.setMarks(80);
        subject2.setLogo(new byte[]{6, 7, 8, 9, 10});
        subject2.setId(Optional.empty());
        subject2.setNestedList(List.of(List.of(List.of("d", "e", "f"))));
        subject2.setNestedMap(Map.of("d", Map.of(new A("NameE"), "e")));
        subject2.setNestedListMap(List.of(List.of(Map.of("d", List.of(BigInteger.valueOf(3), BigInteger.valueOf(4))))));

        student.setSubjects(List.of(subject1, subject2));
        student.setHobby(Optional.of("Cricket"));

        student.setMarks(Map.of(subject1, 80, subject2, 90));
        return student;
    }

    public void deserialize() throws Exception {
        String serialize = serialize();
        System.out.println(serialize);

        StudentConverter converter
                = new StudentConverter();
        Student student = converter.fromPlutusData(ConstrPlutusData.deserialize(CborSerializationUtil.deserialize(HexUtil.decodeHexString(serialize))));

        System.out.println("Deserialize student");
        System.out.println(student);
    }

    public static void main(String[] args) throws Exception {
        StudentTest test = new StudentTest();
        test.deserialize();
    }
}
