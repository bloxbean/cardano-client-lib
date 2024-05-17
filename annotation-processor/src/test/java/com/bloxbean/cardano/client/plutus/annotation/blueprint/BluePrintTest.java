package com.bloxbean.cardano.client.plutus.annotation.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.blueprint.model.*;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import static org.junit.Assert.assertEquals;

public class BluePrintTest {

    @Test
    public void bytesTest() {
        Hello_worldDatum hello_worldDatum = new Hello_worldDatum();
        hello_worldDatum.setOwner(new byte[]{-88, -77, -72, 20, -90, 94, 94, 67, 119, 127, -119, -77, 12, -121, -22, -60, -29, 57, 106, -101, -100, 36, -51, 8, -42, 96, -108, -103});
        Hello_worldDatumConverter hello_worldDatumConverter = new Hello_worldDatumConverter();
        ConstrPlutusData plutusData = hello_worldDatumConverter.toPlutusData(hello_worldDatum);

        assertEquals("d8799f581ca8b3b814a65e5e43777f89b30c87eac4e3396a9b9c24cd08d6609499ff", plutusData.serializeToHex());
    }

    @Test
    public void enumTest() {
        Basic_typesDatum basic_typesDatum = new Basic_typesDatum();
        Basic_typesDatumAction basic_typesDatumAction = new Basic_typesDatumAction();
        basic_typesDatumAction.setBasic_typesDatumAction1Burn(new Basic_typesDatumAction1Burn());
        basic_typesDatum.setAction(basic_typesDatumAction);
        basic_typesDatum.setOwner("Hello, World".getBytes(StandardCharsets.UTF_8));

        Basic_typesDatumConverter basic_typesDatumConverter = new Basic_typesDatumConverter();
        ConstrPlutusData plutusData = basic_typesDatumConverter.toPlutusData(basic_typesDatum);

        assertEquals("d8799f4c48656c6c6f2c20576f726c64d87a9fd87a80ffff", plutusData.serializeToHex());
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

    @Test
    public void listTest() {
        List_StrRedeemer list_strRedeemer = new List_StrRedeemer();
        NestedAConstr nestedAConstr = new NestedAConstr();
        nestedAConstr.setMsg("Hello, World");
        list_strRedeemer.setSimpleList(List.of(List.of(nestedAConstr)));

        List_StrRedeemerConverter list_strRedeemerConverter = new List_StrRedeemerConverter();
        ConstrPlutusData plutusData = list_strRedeemerConverter.toPlutusData(list_strRedeemer);

        assertEquals("d8799f9f9fd8799f4c48656c6c6f2c20576f726c64ffffffff", plutusData.serializeToHex());
    }

    @Test
    public void mapTest() {
        MapBPRedeemer mapBPRedeemer = new MapBPRedeemer();
        Map<NestedAConstr, Integer> map = new HashMap<>();
        NestedAConstr nestedAConstr = new NestedAConstr();
        nestedAConstr.setMsg("Hello, World");
        map.put(nestedAConstr, 10);
        mapBPRedeemer.setMap(map);

        MapBPRedeemerConverter mapBPRedeemerConverter = new MapBPRedeemerConverter();
        ConstrPlutusData plutusData = mapBPRedeemerConverter.toPlutusData(mapBPRedeemer);

        assertEquals("d8799fa1d8799f4c48656c6c6f2c20576f726c64ff0aff", plutusData.serializeToHex());
    }


}
