package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Rational;
import com.bloxbean.cardano.client.spec.UnitInterval;
import com.bloxbean.cardano.client.transaction.spec.governance.DRepVotingThresholds;
import com.bloxbean.cardano.client.transaction.spec.governance.PoolVotingThresholds;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ADR 0002 D5: no key Conway defines is lost, and existing builder calls still compile. */
class ProtocolParamUpdateConwayTest {

    private static final String THIRD = "d81e820103";

    @Test
    void everyConwayKeyRoundTrips() throws Exception {
        byte[] update = hex("a9" + "1819" + rationals(5) + "181a" + rationals(10) + "181b07" + "181c1affffffff"
                + "181d1a80000000" + "181e01" + "181f02" + "18201a80000001" + "1821" + "d81e820f01");

        ProtocolParamUpdate decoded = ProtocolParamUpdate.deserialize(CborSerializationUtil.deserialize(update));

        assertThat(decoded.getCommitteeMinSize()).isEqualTo(7);
        assertThat(decoded.getCommitteeMaxTermLength()).isEqualTo(0xffffffffL);
        assertThat(decoded.getGovActionLifetime()).isEqualTo(1L << 31);
        assertThat(decoded.getGovActionDeposit()).isEqualTo(BigInteger.ONE);
        assertThat(decoded.getDrepDeposit()).isEqualTo(BigInteger.TWO);
        assertThat(decoded.getDrepActivity()).isEqualTo((1L << 31) + 1);
        assertThat(decoded.getMinFeeRefScriptCostPerByte())
                .isEqualTo(new Rational(BigInteger.valueOf(15), BigInteger.ONE));
        assertThat(CborSerializationUtil.serialize(decoded.serialize(), false)).isEqualTo(update);
    }

    @Test
    void thresholdsReadEachPositionInCddlOrder() throws Exception {
        byte[] update = hex("a2" + "1819" + rationals(5) + "181a" + rationals(10));

        ProtocolParamUpdate decoded = ProtocolParamUpdate.deserialize(CborSerializationUtil.deserialize(update));

        PoolVotingThresholds pool = decoded.getPoolVotingThresholds();
        assertThat(List.of(pool.getMotionNoConfidence(), pool.getCommitteeNormal(), pool.getCommitteeNoConfidence(),
                pool.getHardForkInitiation(), pool.getPpSecurityGroup())).isEqualTo(intervals(5));
        DRepVotingThresholds drep = decoded.getDrepVotingThresholds();
        assertThat(List.of(drep.getMotionNoConfidence(), drep.getCommitteeNormal(), drep.getCommitteeNoConfidence(),
                drep.getUpdateToConstitution(), drep.getHardForkInitiation(), drep.getPpNetworkGroup(),
                drep.getPpEconomicGroup(), drep.getPpTechnicalGroup(), drep.getPpGovGroup(),
                drep.getTreasuryWithdrawal())).isEqualTo(intervals(10));
    }

    @Test
    void widenedFieldsKeepTheirWholeValue() throws Exception {
        byte[] update = hex("a3" + "021a80000000" + "031a80000001" + "071affffffff");

        ProtocolParamUpdate decoded = ProtocolParamUpdate.deserialize(CborSerializationUtil.deserialize(update));

        assertThat(decoded.getMaxBlockSize()).isEqualTo(1L << 31);
        assertThat(decoded.getMaxTxSize()).isEqualTo((1L << 31) + 1);
        assertThat(decoded.getMaxEpoch()).isEqualTo(0xffffffffL);
        assertThat(CborSerializationUtil.serialize(decoded.serialize(), false)).isEqualTo(update);
    }

    @Test
    void existingBuilderCallsStillCompile() {
        Integer none = null;
        ProtocolParamUpdate update = ProtocolParamUpdate.builder()
                .maxBlockSize(null).maxTxSize(none).maxEpoch(Integer.valueOf(18))
                .build();

        assertThat(update.getMaxBlockSize()).isNull();
        assertThat(update.getMaxTxSize()).isNull();
        assertThat(update.getMaxEpoch()).isEqualTo(18L);
        assertThat(ProtocolParamUpdate.builder().maxBlockSize(90112).build().getMaxBlockSize()).isEqualTo(90112L);
        assertThat(ProtocolParamUpdate.builder().maxBlockSizeLong(0xffffffffL).build().getMaxBlockSize())
                .isEqualTo(0xffffffffL);
    }

    @Test
    @SuppressWarnings("deprecation")
    void oldConstructorStillWorks() {
        ProtocolParamUpdate update = new ProtocolParamUpdate(null, null, 1, null, null, null, null, 3, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertThat(update.getMaxBlockSize()).isEqualTo(1L);
        assertThat(update.getMaxTxSize()).isNull();
        assertThat(update.getMaxEpoch()).isEqualTo(3L);
    }

    @Test
    void thresholdsTakeEitherLengthFromBothDecoders() throws Exception {
        byte[] update = hex("a11819" + "9f" + THIRD.repeat(5) + "ff");

        assertThat(ProtocolParamUpdate.deserialize(CborSerializationUtil.deserialize(update))
                .getPoolVotingThresholds().getPpSecurityGroup()).isEqualTo(third());
        assertThat(ProtocolParamUpdate.deserialize(CborDecoder.decode(update).get(0))
                .getPoolVotingThresholds().getPpSecurityGroup()).isEqualTo(third());
    }

    @Test
    void thresholdsMustBeComplete() {
        DataItem shortList = CborSerializationUtil.deserialize(hex("a1181a" + "89" + THIRD.repeat(9)));

        assertThatThrownBy(() -> ProtocolParamUpdate.deserialize(shortList)).isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void narrowFieldsDoNotWrap() {
        DataItem update = CborSerializationUtil.deserialize(hex("a1041b0000000100000005"));

        assertThatThrownBy(() -> ProtocolParamUpdate.deserialize(update)).isInstanceOf(CborRuntimeException.class);
    }

    /** Each 32-bit field: its key, and a builder for it. */
    private static java.util.Map<Integer, Function<Long, ProtocolParamUpdate>> word32Fields() {
        java.util.Map<Integer, Function<Long, ProtocolParamUpdate>> fields = new TreeMap<>();
        fields.put(2, v -> ProtocolParamUpdate.builder().maxBlockSizeLong(v).build());
        fields.put(3, v -> ProtocolParamUpdate.builder().maxTxSizeLong(v).build());
        fields.put(7, v -> ProtocolParamUpdate.builder().maxEpochLong(v).build());
        fields.put(22, v -> ProtocolParamUpdate.builder().maxValSize(v).build());
        fields.put(28, v -> ProtocolParamUpdate.builder().committeeMaxTermLength(v).build());
        fields.put(29, v -> ProtocolParamUpdate.builder().govActionLifetime(v).build());
        fields.put(32, v -> ProtocolParamUpdate.builder().drepActivity(v).build());
        return fields;
    }

    private static String keyHex(int key) {
        return key < 24 ? String.format("%02x", key) : "18" + String.format("%02x", key);
    }

    @Test
    void aNegativeValueInA32BitFieldIsNotWritten() {
        word32Fields().forEach((key, build) -> assertThatThrownBy(() -> build.apply(-1L).serialize())
                .as("key " + key).isInstanceOf(CborSerializationException.class));
        assertThatThrownBy(() -> ProtocolParamUpdate.builder().maxBlockSize(-1).build().serialize())
                .isInstanceOf(CborSerializationException.class);
    }

    @Test
    void a32BitFieldHoldsItsWholeRange() throws Exception {
        for (var field : word32Fields().entrySet()) {
            byte[] zero = hex("a1" + keyHex(field.getKey()) + "00");
            byte[] top = hex("a1" + keyHex(field.getKey()) + "1affffffff");

            assertThat(CborSerializationUtil.serialize(field.getValue().apply(0L).serialize(), false)).isEqualTo(zero);
            assertThat(CborSerializationUtil.serialize(field.getValue().apply(0xffffffffL).serialize(), false))
                    .isEqualTo(top);
            assertThat(CborSerializationUtil.serialize(
                    ProtocolParamUpdate.deserialize(CborSerializationUtil.deserialize(top)).serialize(), false))
                    .isEqualTo(top);
        }
    }

    @Test
    void aValueA32BitFieldCannotHoldIsNotRead() {
        for (int key : word32Fields().keySet()) {
            for (String value : List.of("1b0000000100000000", "1bffffffffffffffff", "20", "c24100")) {
                DataItem update = CborSerializationUtil.deserialize(hex("a1" + keyHex(key) + value));

                assertThatThrownBy(() -> ProtocolParamUpdate.deserialize(update))
                        .as("key " + key + " value " + value).isInstanceOf(CborRuntimeException.class);
            }
        }
    }

    @Test
    void builtThresholdsSerializeInCddlOrder() throws Exception {
        List<UnitInterval> i = intervals(10);
        byte[] pool = CborSerializationUtil.serialize(PoolVotingThresholds.builder().motionNoConfidence(i.get(0))
                .committeeNormal(i.get(1)).committeeNoConfidence(i.get(2)).hardForkInitiation(i.get(3))
                .ppSecurityGroup(i.get(4)).build().serialize(), false);
        byte[] drep = CborSerializationUtil.serialize(DRepVotingThresholds.builder().motionNoConfidence(i.get(0))
                .committeeNormal(i.get(1)).committeeNoConfidence(i.get(2)).updateToConstitution(i.get(3))
                .hardForkInitiation(i.get(4)).ppNetworkGroup(i.get(5)).ppEconomicGroup(i.get(6))
                .ppTechnicalGroup(i.get(7)).ppGovGroup(i.get(8)).treasuryWithdrawal(i.get(9)).build().serialize(),
                false);

        assertThat(pool).isEqualTo(hex(rationals(5)));
        assertThat(drep).isEqualTo(hex(rationals(10)));
    }

    /** @return an array of the given number of distinct rationals, 1/21, 2/21 and so on */
    private static String rationals(int count) {
        StringBuilder array = new StringBuilder(count == 5 ? "85" : "8a");
        for (int i = 1; i <= count; i++) {
            array.append("d81e82").append(String.format("%02x", i)).append("15");
        }
        return array.toString();
    }

    private static List<UnitInterval> intervals(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> new UnitInterval(BigInteger.valueOf(i), BigInteger.valueOf(21))).toList();
    }

    private static UnitInterval third() {
        return new UnitInterval(BigInteger.ONE, BigInteger.valueOf(3));
    }

    private static byte[] hex(String hex) {
        return HexFormat.of().parseHex(hex);
    }
}
