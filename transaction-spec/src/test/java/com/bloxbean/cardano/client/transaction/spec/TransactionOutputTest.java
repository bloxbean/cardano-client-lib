package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusV1Script;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionOutputTest {

    @Test
    void serDeSerWithInlineDatum() throws AddressExcepion, CborSerializationException, CborException, CborDeserializationException {
        List<MultiAsset> testMultiAssets = Arrays.asList(MultiAsset.builder().policyId("0df4e527fb4ed572c6aca78a0e641701c70715261810fa6ee98db9ef")
                .assets(Arrays.asList(Asset.builder().name("asset_name").value(BigInteger.valueOf(123456L)).build())).build());
        Value value = Value.builder().coin(BigInteger.valueOf(1200000000L)).multiAssets(testMultiAssets).build();

        TransactionOutput txOutput = TransactionOutput.builder()
                .address("addr_test1vrw6vsvwwe9vwupyfkkeweh23ztd6n0vfydwk823esdz6pc4xqcd5")
                .value(value)
                .inlineDatum(BigIntPlutusData.of(4000))
                .build();

        String serHex = HexUtil.encodeHexString(CborSerializationUtil.serialize(txOutput.serialize()));

        TransactionOutput deTxOut = TransactionOutput.deserialize(CborDecoder.decode(HexUtil.decodeHexString(serHex)).get(0));
        String doubleSerHex = HexUtil.encodeHexString(CborSerializationUtil.serialize(deTxOut.serialize()));

        assertThat(doubleSerHex).isEqualTo(serHex);
    }

    @Test
    void deSerWithInlineDatum() throws CborDeserializationException {
        String serHex = "a300581d60dda6418e764ac770244dad9766ea8896dd4dec491aeb1d51cc1a2d0701821a47868c00a1581c0df4e527fb4ed572c6aca78a0e641701c70715261810fa6ee98db9efa14a61737365745f6e616d651a0001e240028201d81843190fa0";

        TransactionOutput txOutput =
                TransactionOutput.deserialize(CborSerializationUtil.deserialize(HexUtil.decodeHexString(serHex)));

        assertThat(txOutput.getAddress()).isEqualTo("addr_test1vrw6vsvwwe9vwupyfkkeweh23ztd6n0vfydwk823esdz6pc4xqcd5");
        assertThat(txOutput.getValue().getCoin()).isEqualTo(1200000000L);
        assertThat(txOutput.getValue().getMultiAssets()).hasSize(1);
        assertThat(txOutput.getInlineDatum()).isEqualTo(BigIntPlutusData.of(4000));
    }

    @Test
    void serDeSerWithScriptRef() throws AddressExcepion, CborSerializationException, CborException, CborDeserializationException {
        List<MultiAsset> testMultiAssets = Arrays.asList(MultiAsset.builder().policyId("0df4e527fb4ed572c6aca78a0e641701c70715261810fa6ee98db9ef")
                .assets(Arrays.asList(Asset.builder().name("asset_name").value(BigInteger.valueOf(123456L)).build())).build());
        Value value = Value.builder().coin(BigInteger.valueOf(1200000000L)).multiAssets(testMultiAssets).build();
        PlutusV1Script plutusScript = PlutusV1Script.builder()
                .type("PlutusScriptV1")
                .cborHex("4e4d01000033222220051200120011")
                .build();

        TransactionOutput txOutput = TransactionOutput.builder()
                .address("addr_test1vrw6vsvwwe9vwupyfkkeweh23ztd6n0vfydwk823esdz6pc4xqcd5")
                .value(value)
                .scriptRef(plutusScript.serialize())
                .build();

        String serHex = HexUtil.encodeHexString(CborSerializationUtil.serialize(txOutput.serialize()));

        TransactionOutput deTxOut = TransactionOutput.deserialize(CborDecoder.decode(HexUtil.decodeHexString(serHex)).get(0));
        String doubleSerHex = HexUtil.encodeHexString(CborSerializationUtil.serialize(deTxOut.serialize()));

        assertThat(doubleSerHex).isEqualTo(serHex);
        System.out.println(serHex);
    }

    @Test
    void serDeSerWithScript() throws AddressExcepion, CborSerializationException, CborException, CborDeserializationException {
        List<MultiAsset> testMultiAssets = Arrays.asList(MultiAsset.builder().policyId("0df4e527fb4ed572c6aca78a0e641701c70715261810fa6ee98db9ef")
                .assets(Arrays.asList(Asset.builder().name("asset_name").value(BigInteger.valueOf(123456L)).build())).build());
        Value value = Value.builder().coin(BigInteger.valueOf(1200000000L)).multiAssets(testMultiAssets).build();
        PlutusV1Script plutusScript = PlutusV1Script.builder()
                .type("PlutusScriptV1")
                .cborHex("4e4d01000033222220051200120011")
                .build();

        TransactionOutput txOutput = TransactionOutput.builder()
                .address("addr_test1vrw6vsvwwe9vwupyfkkeweh23ztd6n0vfydwk823esdz6pc4xqcd5")
                .value(value)
                .scriptRef(plutusScript)
                .build();

        String serHex = HexUtil.encodeHexString(CborSerializationUtil.serialize(txOutput.serialize()));

        TransactionOutput deTxOut = TransactionOutput.deserialize(CborDecoder.decode(HexUtil.decodeHexString(serHex)).get(0));
        String doubleSerHex = HexUtil.encodeHexString(CborSerializationUtil.serialize(deTxOut.serialize()));

        assertThat(doubleSerHex).isEqualTo(serHex);
        assertThat(serHex).isEqualTo("a300581d60dda6418e764ac770244dad9766ea8896dd4dec491aeb1d51cc1a2d0701821a47868c00a1581c0df4e527fb4ed572c6aca78a0e641701c70715261810fa6ee98db9efa14a61737365745f6e616d651a0001e24003d8185182014e4d01000033222220051200120011");
    }

}
