package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

//import com.bloxbean.cardano.client.plutus.annotation.blueprint.model.*;
//import com.bloxbean.cardano.client.plutus.annotation.blueprint.model.Datum;
//import com.bloxbean.cardano.client.plutus.annotation.blueprint.model.DatumAction;
//import com.bloxbean.cardano.client.plutus.annotation.blueprint.model.DatumAction1Burn;
//import com.bloxbean.cardano.client.plutus.annotation.blueprint.model.DatumConverter;
//import com.bloxbean.cardano.client.plutus.annotation.blueprint.model.Redeemer;
//import com.bloxbean.cardano.client.plutus.annotation.blueprint.model.RedeemerConverter;
//import com.bloxbean.cardano.client.plutus.annotation.blueprint.model.Redeemer;
//import com.bloxbean.cardano.client.plutus.annotation.blueprint.model.RedeemerConverter;
//import com.bloxbean.cardano.client.plutus.annotation.blueprint.model.ComplexStructuresRedeemerNestedA;
//import com.bloxbean.cardano.client.plutus.annotation.blueprint.model.ComplexStructuresRedeemerNestedANestedB;
//import com.test.hello.HelloWorldDatum;
//import com.test.hello.HelloWorldDatumConverter;
//import com.test.hello.HelloWorldRedeemer;
//import com.test.hello.HelloWorldRedeemerConverter;
//import com.bloxbean.cardano.client.plutus.annotation.blueprint.model.ListStrRedeemer;
//import com.bloxbean.cardano.client.plutus.annotation.blueprint.model.MapbpRedeemer;
//import com.bloxbean.cardano.client.plutus.annotation.blueprint.model.MapbpRedeemerConverter;
//import com.bloxbean.cardano.client.plutus.annotation.blueprint.model.NestedAConstr;
//import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
//import org.junit.jupiter.api.Test;
//
//import java.nio.charset.StandardCharsets;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;


import static org.junit.Assert.assertEquals;

public class BluePrintTest {

//    @Test
//    public void bytesTest() {
//        HelloWorldDatum helloWorldDatum = new HelloWorldDatum();
//        helloWorldDatum.setOwner(new byte[]{-88, -77, -72, 20, -90, 94, 94, 67, 119, 127, -119, -77, 12, -121, -22, -60, -29, 57, 106, -101, -100, 36, -51, 8, -42, 96, -108, -103});
//        HelloWorldDatumConverter hello_worldDatumConverter = new HelloWorldDatumConverter();
//        ConstrPlutusData plutusData = hello_worldDatumConverter.toPlutusData(helloWorldDatum);
//
//        assertEquals("d8799f581ca8b3b814a65e5e43777f89b30c87eac4e3396a9b9c24cd08d6609499ff", plutusData.serializeToHex());
//    }
//
//    @Test
//    public void enumTest() {
//        BasicTypesDatum basic_typesDatum = new BasicTypesDatum();
//        BasicTypesDatumAction basic_typesDatumAction = new BasicTypesDatumAction();
//        basic_typesDatumAction.setBasicTypesDatumAction1Burn(new BasicTypesDatumAction1Burn());
//        basic_typesDatum.setAction(basic_typesDatumAction);
//        basic_typesDatum.setOwner("Hello, World".getBytes(StandardCharsets.UTF_8));
//
//        BasicTypesDatumConverter basic_typesDatumConverter = new BasicTypesDatumConverter();
//        ConstrPlutusData plutusData = basic_typesDatumConverter.toPlutusData(basic_typesDatum);
//
//        assertEquals("d8799f4c48656c6c6f2c20576f726c64d87a9fd87a80ffff", plutusData.serializeToHex());
//    }
//
//    @Test
//    public void fillRedeemer() {
//        HelloWorldRedeemer hello_worldRedeemer = new HelloWorldRedeemer();
//        hello_worldRedeemer.setMsg("Hello, World".getBytes(StandardCharsets.UTF_8));
//
//        HelloWorldRedeemerConverter hello_worldRedeemerConverter = new HelloWorldRedeemerConverter();
//        ConstrPlutusData plutusData = hello_worldRedeemerConverter.toPlutusData(hello_worldRedeemer);
//
//        assertEquals("d8799f4c48656c6c6f2c20576f726c64ff", plutusData.serializeToHex());
//    }
//
//    @Test
//    public void integerTest() {
//        BasicTypesRedeemer basic_typesRedeemer = new BasicTypesRedeemer();
//        basic_typesRedeemer.setMsg("Hello, World");
//        basic_typesRedeemer.setCounter(10);
//
//        BasicTypesRedeemerConverter basic_typesRedeemerConverter = new BasicTypesRedeemerConverter();
//        ConstrPlutusData plutusData = basic_typesRedeemerConverter.toPlutusData(basic_typesRedeemer);
//
//        assertEquals("d8799f4c48656c6c6f2c20576f726c640aff", plutusData.serializeToHex());
//    }
//
//    @Test
//    public void nestedTypeTest() {
//        ComplexStructuresRedeemer complex_structuresRedeemer = new ComplexStructuresRedeemer();
//        complex_structuresRedeemer.setOwner("Hello, World".getBytes(StandardCharsets.UTF_8));
//        ComplexStructuresRedeemerNestedA a = new ComplexStructuresRedeemerNestedA();
//        a.setMsg("Hello, World from A");
//        a.setCount(10);
//
//        ComplexStructuresRedeemerNestedANestedB b = new ComplexStructuresRedeemerNestedANestedB();
//        b.setCount2(20);
//        b.setMsg2("Hello, World from B");
//        a.setNestedB(b);
//        complex_structuresRedeemer.setNestedA(a);
//
//        ComplexStructuresRedeemerConverter complex_structuresRedeemerConverter = new ComplexStructuresRedeemerConverter();
//        ConstrPlutusData plutusData = complex_structuresRedeemerConverter.toPlutusData(complex_structuresRedeemer);
//        String s = plutusData.serializeToHex();
//        assertEquals("d8799f4c48656c6c6f2c20576f726c64d8799f0a5348656c6c6f2c20576f726c642066726f6d2041d8799f145348656c6c6f2c20576f726c642066726f6d2042ffffff", plutusData.serializeToHex());
//    }
//
//    @Test
//    public void listTest() {
//        ListStrRedeemer list_strRedeemer = new ListStrRedeemer();
//        NestedAConstr nestedAConstr = new NestedAConstr();
//        nestedAConstr.setMsg("Hello, World");
//        list_strRedeemer.setSimpleList(List.of(List.of(nestedAConstr)));
//
//        ListStrRedeemerConverter list_strRedeemerConverter = new ListStrRedeemerConverter();
//        ConstrPlutusData plutusData = list_strRedeemerConverter.toPlutusData(list_strRedeemer);
//
//        assertEquals("d8799f9f9fd8799f4c48656c6c6f2c20576f726c64ffffffff", plutusData.serializeToHex());
//    }
//
//    @Test
//    public void mapTest() {
//        MapbpRedeemer mapBPRedeemer = new MapbpRedeemer();
//        Map<NestedAConstr, Integer> map = new HashMap<>();
//        NestedAConstr nestedAConstr = new NestedAConstr();
//        nestedAConstr.setMsg("Hello, World");
//        map.put(nestedAConstr, 10);
//        mapBPRedeemer.setMap(map);
//
//        MapbpRedeemerConverter mapBPRedeemerConverter = new MapbpRedeemerConverter();
//        ConstrPlutusData plutusData = mapBPRedeemerConverter.toPlutusData(mapBPRedeemer);
//
//        assertEquals("d8799fa1d8799f4c48656c6c6f2c20576f726c64ff0aff", plutusData.serializeToHex());
//    }


}
