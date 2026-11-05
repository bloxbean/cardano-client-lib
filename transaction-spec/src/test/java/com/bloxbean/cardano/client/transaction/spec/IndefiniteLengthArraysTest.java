package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.plutus.spec.ExUnitPrices;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.cert.PoolRetirement;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeCredential;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeDeregistration;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeRegistration;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.transaction.spec.governance.Constitution;
import com.bloxbean.cardano.client.transaction.spec.governance.ProposalProcedure;
import com.bloxbean.cardano.client.transaction.spec.governance.VotingProcedure;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovActionId;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.HardForkInitiationAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.ParameterChangeAction;
import com.bloxbean.cardano.client.util.HexUtil;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Indefinite-length arrays, as cardano-ledger decodes them (issue #717). Most arrays may have either length: sums
 * ({@code Summands}, {@code decodeRecordSum}) and hand-written record decoders read the list header with
 * {@code decodeListLenOrIndef}. Up to protocol version 11 (Conway), a record decoded with Coders' {@code RecD} reads
 * the header before its fields ({@code decodeCount (RecD _)}), and a tuple with {@code decodeListLenOf}, so those must
 * have a definite length.
 * The credential, input, datum option, redeemer, hard fork and rational samples are valid ones of
 * cardano-cbor-dataset's Conway corpus.
 */
class IndefiniteLengthArraysTest {
    private static final String KEY_HASH = "4a3a1c86cef3b1d2c03ad7787c622683cc6dcfc6d12c1ab4a9fbc8f1";
    private static final String HASH28 = "581c" + KEY_HASH;
    private static final String HASH32 = "5820" + "11".repeat(32);
    private static final String ADDRESS = "581d60" + KEY_HASH;
    private static final String REWARD_ACCOUNT = "581de0" + KEY_HASH;
    private static final String ANCHOR = "82" + "6161" + HASH32;

    private static DataItem item(String hex) {
        return CborSerializationUtil.deserialize(HexUtil.decodeHexString(hex));
    }

    private static Array array(String hex) {
        return (Array) item(hex);
    }

    @Test
    void credential() throws Exception {
        StakeCredential credential = StakeCredential.deserialize(array("9f00" + HASH28 + "ff"));
        assertThat(credential.getHash()).isEqualTo(HexUtil.decodeHexString(KEY_HASH));
    }

    @Test
    void transactionInput() throws Exception {
        TransactionInput input = TransactionInput.deserialize(
                array("9f58202471829e3537dc64aec144b2db5c918bacb68ed664ef79fb6428ac39c4796a1819192fff"));
        assertThat(input.getTransactionId())
                .isEqualTo("2471829e3537dc64aec144b2db5c918bacb68ed664ef79fb6428ac39c4796a18");
        assertThat(input.getIndex()).isEqualTo(6447);
    }

    @Test
    void transactionOutputInArrayForm() throws Exception {
        TransactionOutput output = TransactionOutput.deserialize(item("9f" + ADDRESS + "1a000f4240" + "ff"));
        assertThat(output.getValue().getCoin()).isEqualTo(BigInteger.valueOf(1_000_000));
    }

    @Test
    void datumOption() throws Exception {
        // [_ 1, #6.24(h'80')]: an inline datum, the empty list
        TransactionOutput output = TransactionOutput.deserialize(
                item("a3" + "00" + ADDRESS + "0100" + "02" + "9f01d8184180ff"));
        assertThat(((ListPlutusData) output.getInlineDatum()).getPlutusDataList()).isEmpty();
    }

    @Test
    void redeemer() throws Exception {
        Redeemer redeemer = Redeemer.deserializePreConway(
                array("9f041aab118a95a0821b4ae291dc755f43231b16b1656b5d58ff35ff"));
        assertThat(redeemer.getTag()).isEqualTo(RedeemerTag.Voting);
        assertThat(redeemer.getIndex()).isEqualTo(BigInteger.valueOf(0xab118a95L));
        assertThat(redeemer.getExUnits().getMem()).isEqualTo(new BigInteger("4ae291dc755f4323", 16));
    }

    @Test
    void protocolVersionOfAHardForkAction() throws Exception {
        HardForkInitiationAction action = (HardForkInitiationAction) GovAction.deserialize(array("8301f69f0b1a9f67d35aff"));
        assertThat(action.getProtocolVersion()).isEqualTo(new ProtocolVersion(11, 0x9f67d35aL));
    }

    @Test
    void certificates() throws Exception {
        assertThat(Certificate.deserialize(array("9f00" + "8200" + HASH28 + "ff"))).isInstanceOf(StakeRegistration.class);
        assertThat(Certificate.deserialize(array("9f01" + "9f00" + HASH28 + "ff" + "ff")))
                .isInstanceOf(StakeDeregistration.class);
        PoolRetirement retirement = (PoolRetirement) Certificate.deserialize(array("9f04" + HASH28 + "190100" + "ff"));
        assertThat(retirement.getEpoch()).isEqualTo(256);
    }

    /**
     * A tag 30 rational over an indefinite array: cardano-ledger's decodeRationalWithTag reads the array with
     * decodeListLenOrIndef.
     */
    @Test
    void rationalInAParameterUpdate() throws Exception {
        ProtocolParamUpdate update = ProtocolParamUpdate.deserialize(
                item("a1" + "09" + "d81e9f1b7ab36c7dc0f2c5041b5e41c33bd6c7a4f4ff"));
        assertThat(update.getPoolPledgeInfluence().getNumerator()).isEqualTo(new BigInteger("7ab36c7dc0f2c504", 16));
        assertThat(update.getPoolPledgeInfluence().getDenominator()).isEqualTo(new BigInteger("5e41c33bd6c7a4f4", 16));
    }

    @Test
    void recordsReadTheirDefiniteForm() throws Exception {
        assertThat(GovActionId.deserialize(item("82" + HASH32 + "00")).getGovActionIndex()).isZero();
        assertThat(Anchor.deserialize(array(ANCHOR)).getAnchorUrl()).isEqualTo("a");
        assertThat(Constitution.deserialize(array("82" + ANCHOR + "f6")).getScripthash()).isNull();
        assertThat(VotingProcedure.deserialize(array("8200f6")).getAnchor()).isNull();
        assertThat(ProposalProcedure.deserialize(item("84" + "00" + REWARD_ACCOUNT + "8106" + ANCHOR))
                .getAnchor()).isNotNull();
        assertThat(ExUnits.deserialize(array("820102")).getSteps()).isEqualTo(BigInteger.TWO);
        assertThat(ExUnitPrices.deserialize(item("82d81e820102d81e820103")).getStepPrice().getDenominator())
                .isEqualTo(BigInteger.valueOf(3));
        assertThat(Value.deserialize(item("8201a0")).getCoin()).isEqualTo(BigInteger.ONE);
        assertThat(Redeemer.deserialize(array("820000"), array("82a0820102")).getExUnits().getMem())
                .isEqualTo(BigInteger.ONE);
        assertThat(Update.deserialize(item("82a000")).getEpoch()).isZero();
    }

    /**
     * Records decoded with RecD, and the redeemer value (a tuple), reject an indefinite length: GovActionId,
     * VotingProcedure, ProposalProcedure and Constitution (Conway Governance.Procedures), Anchor (BaseTypes), ExUnits
     * and Prices (Plutus.ExUnits), the array form of MaryValue, Shelley's Update.
     */
    @Test
    void recordsRejectTheIndefiniteForm() {
        assertRejected(() -> GovActionId.deserialize(item("9f" + HASH32 + "00ff")));
        assertRejected(() -> Anchor.deserialize(array("9f" + "6161" + HASH32 + "ff")));
        assertRejected(() -> Constitution.deserialize(array("9f" + ANCHOR + "f6ff")));
        assertRejected(() -> VotingProcedure.deserialize(array("9f00f6ff")));
        assertRejected(() -> ProposalProcedure.deserialize(item("9f" + "00" + REWARD_ACCOUNT + "8106" + ANCHOR + "ff")));
        assertRejected(() -> ExUnits.deserialize(array("9f0102ff")));
        assertRejected(() -> ExUnitPrices.deserialize(item("9fd81e820102d81e820103ff")));
        assertRejected(() -> Value.deserialize(item("9f01a0ff")));
        assertRejected(() -> Redeemer.deserialize(array("820000"), array("9fa0820102ff")));
        assertRejected(() -> Update.deserialize(item("9fa000ff")));
        // nested: the anchor of a vote, the id of a parameter change (a sum, which may be indefinite)
        assertRejected(() -> VotingProcedure.deserialize(array("8200" + "9f" + "6161" + HASH32 + "ff")));
        assertRejected(() -> GovAction.deserialize(array("9f00" + "9f" + HASH32 + "00ff" + "a0" + "f6" + "ff")));
    }

    @Test
    void parameterChangeMayBeIndefinite() throws Exception {
        ParameterChangeAction action = (ParameterChangeAction) GovAction.deserialize(
                array("9f00" + "82" + HASH32 + "00" + "a0" + "f6" + "ff"));
        assertThat(action.getPrevGovActionId().getGovActionIndex()).isZero();
    }

    private static void assertRejected(ThrowingCallable decode) {
        assertThatThrownBy(decode).hasStackTraceContaining("expected a definite-length array");
    }
}
