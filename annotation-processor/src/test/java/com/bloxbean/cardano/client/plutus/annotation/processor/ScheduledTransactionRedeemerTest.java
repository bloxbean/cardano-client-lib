package com.bloxbean.cardano.client.plutus.annotation.processor;

import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.BatcherAddress;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.ScheduledTransactionRedeemer;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.converter.ScheduledTransactionRedeemerConverter;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class ScheduledTransactionRedeemerTest {

    @Test
    public void testSerDeser() throws CborDeserializationException {
        var scheduledTransactionRedeemer = ScheduledTransactionRedeemer.builder()
                .inputTankIndex(BigInteger.valueOf(1))
                .batcher(new BatcherAddress("addr1abc"))
                .build();

        var scheduledTransactionRedeemerConverter = new ScheduledTransactionRedeemerConverter();
        var serHex = scheduledTransactionRedeemerConverter.serializeToHex(scheduledTransactionRedeemer);

        var deScheduledTransactionRedeemer = scheduledTransactionRedeemerConverter.deserialize(serHex);
        var doubleSerHex = scheduledTransactionRedeemerConverter.serializeToHex(deScheduledTransactionRedeemer);

        var constrPlutusData = (ConstrPlutusData) PlutusData.deserialize(HexUtil.decodeHexString(serHex));
        var childConstrPlutusData = (ConstrPlutusData) constrPlutusData.getData().getPlutusDataList().get(1);

        assertThat(constrPlutusData.getAlternative()).isEqualTo(3);
        assertThat(childConstrPlutusData.getAlternative()).isEqualTo(1);
        assertThat(doubleSerHex).isEqualTo(serHex);
        assertThat(deScheduledTransactionRedeemer).isEqualTo(scheduledTransactionRedeemer);
    }
}
