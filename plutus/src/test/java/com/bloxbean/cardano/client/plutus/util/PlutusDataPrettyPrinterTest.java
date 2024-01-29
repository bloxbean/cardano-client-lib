package com.bloxbean.cardano.client.plutus.util;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlutusDataPrettyPrinterTest {

    @Test
    void toJson_metadataInDatum() throws Exception {
        String datumHex = "d8799fa6446e616d654c54657374313233546f6b656e45696d6167655835697066733a2f2f516d5763636a566279694d6a394637775278725053743554387953687974714331646f5132585072457838485379496d65646961747970654a696d6167652f6a706567496d65646961547970654a696d6167652f6a7065674b6465736372697074696f6e404566696c65739fa3496d65646961747970654a696d6167652f6a706567446e616d654a696d6167652f6a706567437372635835697066733a2f2f516d5763636a566279694d6a394637775278725053743554387953687974714331646f5132585072457838485379ff01ff";
        PlutusData plutusData = PlutusData.deserialize(HexUtil.decodeHexString(datumHex));

        String json = PlutusDataPrettyPrinter.toJson(plutusData);
        assertThat(json).isNotNull();
    }

    @Test
    void toJson_regularDatum() throws Exception {
        String datumHex = "d87a9fd8799f582064eec48044b99a9a559373872caeeada11922d168811592e34e8a7496500501e1b0000000ba43b74011b0000000ba43b7401d8799f0001ff0000d87a80d8799f58206508e5f0299de83ea613a0e2b22a9e9a396742d8d4895aa33d567f6172df0fd9ffffff";
        PlutusData plutusData = PlutusData.deserialize(HexUtil.decodeHexString(datumHex));

        String json = PlutusDataPrettyPrinter.toJson(plutusData);
        assertThat(json).isNotNull();
    }
}
