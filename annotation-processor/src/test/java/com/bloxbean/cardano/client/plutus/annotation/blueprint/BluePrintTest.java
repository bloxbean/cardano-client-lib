package com.bloxbean.cardano.client.plutus.annotation.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.blueprint.Complex_structuresRedeemerNestedA;
import com.bloxbean.cardano.client.plutus.annotation.blueprint.model.Hello_worldDatum;
import com.bloxbean.cardano.client.plutus.annotation.blueprint.model.Hello_worldDatumConverter;
import com.bloxbean.cardano.client.plutus.annotation.blueprint.model.Hello_worldRedeemer;
import com.bloxbean.cardano.client.plutus.annotation.blueprint.model.Hello_worldRedeemerConverter;
import com.bloxbean.cardano.client.plutus.annotation.blueprint.model.Basic_typesRedeemer;
import com.bloxbean.cardano.client.plutus.annotation.blueprint.model.Basic_typesRedeemerConverter;
import com.bloxbean.cardano.client.plutus.annotation.blueprint.Complex_structuresRedeemer;
import com.bloxbean.cardano.client.plutus.annotation.blueprint.Complex_structuresRedeemerConverter;
import com.bloxbean.cardano.client.plutus.annotation.blueprint.Complex_structuresRedeemerNestedANestedB;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class BluePrintTest {

    @Test
    public void test() throws ClassNotFoundException {
//        Para

        System.out.println("");
    }


    @Test
    public void fillDatum() {
        Hello_worldDatum hello_worldDatum = new Hello_worldDatum();
        hello_worldDatum.setOwner(new byte[]{-88, -77, -72, 20, -90, 94, 94, 67, 119, 127, -119, -77, 12, -121, -22, -60, -29, 57, 106, -101, -100, 36, -51, 8, -42, 96, -108, -103});
        Hello_worldDatumConverter hello_worldDatumConverter = new Hello_worldDatumConverter();
        ConstrPlutusData plutusData = hello_worldDatumConverter.toPlutusData(hello_worldDatum);

        assertEquals("d8799f581ca8b3b814a65e5e43777f89b30c87eac4e3396a9b9c24cd08d6609499ff", plutusData.serializeToHex());
    }

    @Test
    public void fillRedeemer() {
        Hello_worldRedeemer hello_worldRedeemer = new Hello_worldRedeemer();
        hello_worldRedeemer.setMsg("Hello, World".getBytes(StandardCharsets.UTF_8));
        Hello_worldRedeemerConverter hello_worldRedeemerConverter = new Hello_worldRedeemerConverter();
        ConstrPlutusData plutusData = hello_worldRedeemerConverter.toPlutusData(hello_worldRedeemer);

        assertEquals("d8799f4c48656c6c6f2c20576f726c64ff", plutusData.serializeToHex());
    }

    @Test
    public void integerTest() {
        Basic_typesRedeemer basic_typesRedeemer = new Basic_typesRedeemer();
        basic_typesRedeemer.setMsg("Hello, World");
        basic_typesRedeemer.setCounter(10);

        Basic_typesRedeemerConverter basic_typesRedeemerConverter = new Basic_typesRedeemerConverter();
        ConstrPlutusData plutusData = basic_typesRedeemerConverter.toPlutusData(basic_typesRedeemer);

        assertEquals("d8799f4c48656c6c6f2c20576f726c640aff", plutusData.serializeToHex());
    }

    @Test
    public void nestedTypeTest() {
        Complex_structuresRedeemer complex_structuresRedeemer = new Complex_structuresRedeemer();
        complex_structuresRedeemer.setOwner("Hello, World".getBytes(StandardCharsets.UTF_8));
        Complex_structuresRedeemerNestedA a = new Complex_structuresRedeemerNestedA();
        a.setMsg("Hello, World from A");
        a.setCount(10);

        Complex_structuresRedeemerNestedANestedB b = new Complex_structuresRedeemerNestedANestedB();
        b.setCount2(20);
        b.setMsg2("Hello, World from B");
        a.setNestedB(b);
        complex_structuresRedeemer.setNestedA(a);

        Complex_structuresRedeemerConverter complex_structuresRedeemerConverter = new Complex_structuresRedeemerConverter();
        ConstrPlutusData plutusData = complex_structuresRedeemerConverter.toPlutusData(complex_structuresRedeemer);
        String s = plutusData.serializeToHex();
        assertEquals("d8799f4c48656c6c6f2c20576f726c64d8799f0a5348656c6c6f2c20576f726c642066726f6d2041d8799f145348656c6c6f2c20576f726c642066726f6d2042ffffff", plutusData.serializeToHex());
    }


}
