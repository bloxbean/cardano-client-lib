package com.bloxbean.cardano.client.plutus.annotation;

import com.bloxbean.cardano.client.plutus.annotation.model.NestedBasicModel;
import com.bloxbean.cardano.client.plutus.annotation.model.NestedBasicModelConverter;
import com.bloxbean.cardano.client.plutus.annotation.model.Vote;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class NestedBasicModelTest {
    /** Aiken Code
     use aiken/bytearray
     use aiken/cbor.{diagnostic, serialise}
     use aiken/dict.{Dict}
     use aiken/string

     type Vote {
        i: Int,
        v: String,
     }

     type BasicModel {
        i: Int,
         s: String,
         b: ByteArray,
         l: List<List<String>>,
         m: Dict<ByteArray, List<Int>>,
         bool_: Bool,
         opt: Option<List<String>>,
        votes: List<List<Option<Vote>>>,
     }

     test basicModel_test() {
        let map =
            dict.new()
                |> dict.insert(key: "Hello", value: [1, 2, 3], compare: bytearray.compare)
                |> dict.insert(key: "World", value: [4, 5, 6], compare: bytearray.compare)

         let basic_model =
            BasicModel {
                i: 10,
                 s: @"Hello",
                 b: #[1, 2, 3],
                l: [[@"Hello", @"World"], [@"A", @"B"]],
                m: map,
                bool_: True,
                opt: Some([@"Text1", @"Text2"]),
                votes: [[Some(Vote { i: 1, v: @"a" }), Some(Vote { i: 2, v: @"b" })]],
             }

         // let b = False
         let st = diagnostic(basic_model)
         trace st

         let ser = serialise(basic_model)
         trace bytearray.to_hex(ser)

         True
     }

     */
    @Test
    void testNestedBasicModel() {
        NestedBasicModel model = new NestedBasicModel();
        model.setI(10);
        model.setS("Hello");
        model.setB(new byte[] {1,2,3});
        model.setL(Arrays.asList(Arrays.asList("Hello", "World"), Arrays.asList("A", "B")));
        model.setM(Map.of("Hello", List.of(1L, 2L, 3L), "World", List.of(4L,5L,6L)));
        model.setBool(true);
        model.setOpt(Optional.of(List.of("Text1", "Text2")));
        model.setVotes(List.of(List.of(Optional.of(new Vote(1, "a")), Optional.of(new Vote(2, "b")))));

        NestedBasicModelConverter converter = new NestedBasicModelConverter();
        String serHex = converter.serializeToHex(model);

        NestedBasicModel deBasicModel = converter.deserialize(serHex);
        String doubleSerHex = converter.serializeToHex(deBasicModel);

        assertThat(serHex).isEqualTo(doubleSerHex);
        assertThat(deBasicModel).isEqualTo(model);
        String expected = "d8799f0a4548656c6c6f430102039f9f4548656c6c6f45576f726c64ff9f41414142ffffa24548656c6c6f9f010203ff45576f726c649f040506ffd87a80d8799f9f455465787431455465787432ffff9f9fd8799fd8799f014161ffffd8799fd8799f024162ffffffffff";
        assertThat(serHex).isEqualTo(expected);
    }
}
