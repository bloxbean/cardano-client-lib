package com.bloxbean.cardano.client.plutus.annotation.processor;

import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;

import java.math.BigInteger;

public class BasePlutusDataSerializer {

    protected ConstrPlutusData initConstr(long alternative) {
        return ConstrPlutusData
                .builder()
                .alternative(alternative)
                .data(ListPlutusData.builder().build())
                .build();
    }

    protected PlutusData toPlutusData(Long number) {
        return BigIntPlutusData.of(number);
    }

    protected PlutusData toPlutusData(BigInteger bi) {
        return BigIntPlutusData.of(bi);
    }

    protected PlutusData toPlutusData(Integer i) {
        return BigIntPlutusData.of(i);
    }

    protected PlutusData toPlutusData(byte[] bytes) {
        return BytesPlutusData.of(bytes);
    }

    protected PlutusData toPlutusData(String s) {
        if (s.startsWith("0x") || s.startsWith("0X")) {
            byte[] bytes = HexUtil.decodeHexString(s);
            return BytesPlutusData.of(bytes);
        } else {
            return BytesPlutusData.of(s);
        }
    }

}
