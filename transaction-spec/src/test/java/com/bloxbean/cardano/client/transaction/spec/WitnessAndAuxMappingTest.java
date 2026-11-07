package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusV1Script;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.plutus.spec.PlutusV3Script;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.transaction.util.UniqueList;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ADR 0003 M4: what the mappers of the witness set and the aux data keep, read from the reader's views. */
class WitnessAndAuxMappingTest {
    private static final String BODY = "a3" + "0080" + "0180" + "0200";
    private static final String UNITS = "820506";
    private static final String NATIVE = "8200" + "581c" + "11".repeat(28);

    private static byte[] hex(String hex) {
        return HexUtil.decodeHexString(hex);
    }

    private static Transaction withWitnesses(String witnesses) throws Exception {
        return Transaction.deserialize(hex("84" + BODY + witnesses + "f5" + "f6"));
    }

    private static Transaction withAux(String aux) throws Exception {
        return Transaction.deserialize(hex("84" + BODY + "a0" + "f5" + aux));
    }

    private static Map map(String hex) {
        return (Map) CborSerializationUtil.deserialize(hex(hex));
    }

    private static Array array(String hex) {
        return (Array) CborSerializationUtil.deserialize(hex(hex));
    }

    @Test
    void aWitnessSetWithNoKnownFieldIsNull() throws Exception {
        assertThat(withWitnesses("a0").getWitnessSet()).isNull();
        assertThat(withWitnesses("a1" + "0880").getWitnessSet()).isNull();
        assertThat(TransactionWitnessSet.deserialize(map("a0"))).isNull();
    }

    @Test
    void anAbsentFieldIsNullAnEmptyOneKeepsItsDefaultAndOthersAreLists() throws Exception {
        TransactionWitnessSet set = withWitnesses("a2" + "0080" + "0481" + "01").getWitnessSet();
        assertThat(set.getVkeyWitnesses()).isEmpty();
        assertThat(set.getVkeyWitnesses()).isInstanceOf(UniqueList.class);
        assertThat(set.getPlutusDataList()).hasSize(1);
        assertThat(set.getPlutusDataList()).isNotInstanceOf(UniqueList.class);
        assertThat(set.getNativeScripts()).isNull();
        assertThat(set.getBootstrapWitnesses()).isNull();
        assertThat(set.getPlutusV1Scripts()).isNull();
        assertThat(set.getRedeemers()).isNull();
        assertThat(set.getPlutusV2Scripts()).isNull();
        assertThat(set.getPlutusV3Scripts()).isNull();
        // two equal datums stay two
        assertThat(withWitnesses("a1" + "048201" + "01").getWitnessSet().getPlutusDataList()).hasSize(2);
    }

    @Test
    void everyFieldOfAWitnessSet() throws Exception {
        String witnesses = "a8" + "0081" + "82" + "4101" + "4102" + "0181" + NATIVE + "0281" + "84" + "4101" + "4102"
                + "4103" + "4104" + "038141aa" + "048100" + "058184" + "03" + "01" + "02" + UNITS + "068141bb"
                + "078141cc";
        for (TransactionWitnessSet set : new TransactionWitnessSet[]{withWitnesses(witnesses).getWitnessSet(),
                TransactionWitnessSet.deserialize(map(witnesses))}) {
            assertThat(set.getVkeyWitnesses().get(0).getVkey()).containsExactly(1);
            assertThat(set.getVkeyWitnesses().get(0).getSignature()).containsExactly(2);
            assertThat(set.getNativeScripts().get(0).serializeScriptBody()).isEqualTo(hex(NATIVE));
            assertThat(set.getBootstrapWitnesses().get(0).getAttributes()).containsExactly(4);
            assertThat(set.getPlutusV1Scripts()).extracting(PlutusV1Script::getCborHex).containsExactly("41aa");
            assertThat(set.getPlutusV2Scripts()).extracting(PlutusV2Script::getCborHex).containsExactly("41bb");
            assertThat(set.getPlutusV3Scripts()).extracting(PlutusV3Script::getCborHex).containsExactly("41cc");
            assertThat(set.getPlutusDataList()).hasSize(1);
            Redeemer redeemer = set.getRedeemers().get(0);
            assertThat(redeemer.getTag()).isEqualTo(RedeemerTag.Reward);
            assertThat(redeemer.getIndex()).isEqualTo(1);
            assertThat(redeemer.getExUnits().getMem()).isEqualTo(5);
            assertThat(redeemer.getExUnits().getSteps()).isEqualTo(6);
        }
    }

    /** A Plutus script's model keeps the bytes a tree has: a tag stays, and chunks are joined. */
    @Test
    void aPlutusScriptIsTheEncodingOfItsTree() throws Exception {
        assertThat(withWitnesses("a1" + "0381" + "c1" + "5f" + "41aa" + "ff").getWitnessSet().getPlutusV1Scripts()
                .get(0).getCborHex()).isEqualTo("c141aa");
    }

    @Test
    void redeemerLeniency() throws Exception {
        // an unknown tag is kept as null, and the index is exact
        Redeemer unknown = Redeemer.deserializePreConway(array("84" + "07" + "1bffffffffffffffff" + "00" + UNITS));
        assertThat(unknown.getTag()).isNull();
        assertThat(unknown.getIndex()).isEqualTo(new BigInteger("18446744073709551615"));
        // a field of neither form holds no redeemer, and leaves the default
        assertThat(withWitnesses("a1" + "05" + "00").getWitnessSet().getRedeemers()).isEmpty();
        // Conway's map form: one entry per key, the first position and the last value
        TransactionWitnessSet set = withWitnesses("a1" + "05" + "a3" + "820000" + "82" + "01" + UNITS + "820001" + "82"
                + "02" + UNITS + "821800" + "00" + "82" + "03" + UNITS).getWitnessSet();
        assertThat(set.getRedeemers()).extracting(Redeemer::getIndex).containsExactly(BigInteger.ZERO, BigInteger.ONE);
        assertThat(set.getRedeemers().get(0).getData().serializeToHex()).isEqualTo("03");
        // execution units are definite, unless a tree makes them so
        assertThatThrownBy(() -> withWitnesses("a1" + "0581" + "84" + "00" + "00" + "00" + "9f0506ff"))
                .isInstanceOf(CborDeserializationException.class);
        assertThat(withWitnesses("a1" + "0581" + "84" + "00" + "00" + "00" + "d81e9f0506ff").getWitnessSet()
                .getRedeemers().get(0).getExUnits().getSteps()).isEqualTo(6);
        assertThatThrownBy(() -> withWitnesses("a1" + "05a1" + "820000" + "9f" + "00" + UNITS + "ff"))
                .isInstanceOf(CborDeserializationException.class);
        assertThat(withWitnesses("a1" + "05a1" + "9f0000ff" + "82" + "00" + UNITS).getWitnessSet().getRedeemers())
                .hasSize(1);
    }

    /**
     * ADR 0003 D7 and D6.3 of ADR 0002: from bytes, a datum knows the pairs it lost. A tree keeps one entry per key and
     * has only a mark that it lost one, and a re-encoded tree no longer has it, so the overloads that take a tree do
     * not report it.
     */
    @Test
    void aDatumFromBytesKnowsThePairsItLost() throws Exception {
        String datum = "a2" + "00" + "00" + "00" + "01";
        TransactionWitnessSet set = withWitnesses("a2" + "0481" + datum + "0581" + "84" + "00" + "00" + datum + UNITS)
                .getWitnessSet();
        assertThat(PlutusData.mapsThatLostPairs(set.getPlutusDataList().get(0))).hasSize(1);
        assertThat(PlutusData.mapsThatLostPairs(set.getRedeemers().get(0).getData())).hasSize(1);

        TransactionWitnessSet fromTree = TransactionWitnessSet.deserialize(map("a1" + "0481" + datum));
        assertThat(PlutusData.mapsThatLostPairs(fromTree.getPlutusDataList().get(0))).isEmpty();
        assertThat(fromTree.getPlutusDataList().get(0)).isEqualTo(set.getPlutusDataList().get(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"00", "4100", "6161", "f7", "c100", "d9010300"})
    void anAuxDataOfNoFormIsRejected(String aux) {
        assertThatThrownBy(() -> withAux(aux)).isInstanceOf(CborDeserializationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "a100" + "d81e" + "00",                     // a tag of no model, in the witness set's metadata
            "d90103a1" + "0081" + "d826" + "00",
            "82" + "a0" + "81" + "d81e" + "00"})
    void whatATreeDoesNotDecodeIsRejected(String aux) {
        assertThatThrownBy(() -> withAux(aux)).isInstanceOf(CborDeserializationException.class);
        assertThatThrownBy(() -> withWitnesses("a1" + "1864" + aux)).isInstanceOf(CborDeserializationException.class);
    }

    @Test
    void everyFormOfAuxData() throws Exception {
        String metadata = "a1" + "01" + "6161";
        AuxiliaryData map = withAux(metadata).getAuxiliaryData();
        assertThat(map.getMetadata().get(BigInteger.ONE)).isEqualTo("a");
        assertThat(map.getNativeScripts()).isEmpty();

        AuxiliaryData array = withAux("82" + metadata + "81" + NATIVE).getAuxiliaryData();
        assertThat(array.getMetadata().get(BigInteger.ONE)).isEqualTo("a");
        assertThat(array.getNativeScripts().get(0).serializeScriptBody()).isEqualTo(hex(NATIVE));

        AuxiliaryData keyed = withAux("d90103" + "a5" + "00" + metadata + "0181" + NATIVE + "028141aa" + "038141bb"
                + "048141cc").getAuxiliaryData();
        assertThat(keyed.getMetadata().get(BigInteger.ONE)).isEqualTo("a");
        assertThat(keyed.getNativeScripts()).hasSize(1);
        assertThat(keyed.getPlutusV1Scripts()).extracting(PlutusV1Script::getCborHex).containsExactly("41aa");
        assertThat(keyed.getPlutusV2Scripts()).extracting(PlutusV2Script::getCborHex).containsExactly("41bb");
        assertThat(keyed.getPlutusV3Scripts()).extracting(PlutusV3Script::getCborHex).containsExactly("41cc");

        assertThat(withAux("d90103a0").getAuxiliaryData().getMetadata()).isNull();
        // a plain map may repeat a label, the last value winning
        assertThat(withAux("a2" + "01" + "00" + "01" + "6161").getAuxiliaryData().getMetadata().get(BigInteger.ONE))
                .isEqualTo("a");
        assertThatThrownBy(() -> withAux("c1" + "a2" + "01" + "00" + "01" + "6161"))
                .isInstanceOf(CborDeserializationException.class);
        assertThat(withAux("f6").getAuxiliaryData()).isNull();
        // the three-item form
        assertThat(Transaction.deserialize(hex("83" + BODY + "a0" + metadata)).getAuxiliaryData().getMetadata())
                .isNotNull();
    }

    @Test
    void theOverloadsOfAuxDataReadAsATransactionDoes() throws Exception {
        String metadata = "a1" + "01" + "6161";
        assertThat(AuxiliaryData.deserialize(map(metadata)).getMetadata().get(BigInteger.ONE)).isEqualTo("a");
        assertThat(AuxiliaryData.deserialize(map("d90103a1" + "028141aa")).getPlutusV1Scripts()).hasSize(1);
        assertThat(AuxiliaryData.deserialize(array("82" + metadata + "80")).getNativeScripts()).isEmpty();
        assertThatThrownBy(() -> AuxiliaryData.deserialize(array("9f" + metadata + "80" + "ff")))
                .isInstanceOf(CborDeserializationException.class);
        assertThatThrownBy(() -> AuxiliaryData.deserialize(array("81" + metadata)))
                .isInstanceOf(CborDeserializationException.class);
    }

    @Test
    void witnessesAreRejectedAsCheckedExceptions() {
        assertThatThrownBy(() -> VkeyWitness.deserialize(array("8100")))
                .isInstanceOf(CborDeserializationException.class);
        assertThatThrownBy(() -> BootstrapWitness.deserialize(array("8100")))
                .isInstanceOf(CborDeserializationException.class);
        assertThatThrownBy(() -> TransactionWitnessSet.deserialize(map("a1" + "0000")))
                .isInstanceOf(CborDeserializationException.class);
    }
}
