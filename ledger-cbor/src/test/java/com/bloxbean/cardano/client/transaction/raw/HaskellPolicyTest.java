package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link HaskellPolicy}'s rules, moved from Yano by ADR 0005: each case checks the strict transaction read's verdict
 * and the walk's message (the strict read is the walk, so this is not a proof that two readers agree; ADR 0003 I13's
 * proof is met and retired, see ADR 0005 D1); and the rules the decoder version decides.
 */
class HaskellPolicyTest {

    private static final String HASH28 = "581c" + "11".repeat(28);
    private static final String HASH32 = "5820" + "22".repeat(32);
    private static final String INPUTS = "0081" + "82" + HASH32 + "00";
    private static final String OUTPUTS = "0180";
    private static final String FEE = "0200";
    private static final String BODY = "a3" + INPUTS + OUTPUTS + FEE;
    private static final String CRED = "8200" + HASH28;
    private static final String ANCHOR = "82" + "60" + HASH32;

    private static byte[] tx(String body, String witnesses) {
        return HexUtil.decodeHexString("84" + body + witnesses + "f5f6");
    }

    private static boolean rejects(Consumer<byte[]> read, byte[] tx) {
        try {
            read.accept(tx);
            return false;
        } catch (LedgerDecodingException e) {
            // a crash is not a rejection (ADR 0005 section 3, D10): anything else thrown fails the test
            return true;
        }
    }

    /** The walk and the reader agree, and the walk says why it rejects. */
    private static void bothReject(String body, String witnesses, String walkReason) {
        bothReject(tx(body, witnesses), walkReason);
    }

    private static void bothReject(byte[] tx, String walkReason) {
        assertThat(rejects(raw -> HaskellPolicy.CONWAY.transaction(raw), tx)).as("the reader").isTrue();
        assertThatThrownBy(() -> RawTx.walk(tx, Era.Conway, HaskellPolicy.CONWAY))
                .isInstanceOf(LedgerDecodingException.class).hasMessageContaining(walkReason);
    }

    private static void bothAccept(String body, String witnesses) {
        byte[] tx = tx(body, witnesses);
        assertThatCode(() -> HaskellPolicy.CONWAY.transaction(tx)).doesNotThrowAnyException();
        assertThatCode(() -> RawTx.walk(tx, Era.Conway, HaskellPolicy.CONWAY)).doesNotThrowAnyException();
    }

    @Test
    void aBodyWithItsFieldsPasses() {
        bothAccept(BODY, "a0");
        bothAccept("bf" + INPUTS + OUTPUTS + FEE + "ff", "a0");
        bothAccept("a4" + INPUTS + OUTPUTS + FEE + "03" + "00", "a0");
    }

    @Test
    void aBodyWithNoFee() {
        bothReject("a2" + INPUTS + OUTPUTS, "a0", "transaction_body at 1 has no COIN");
    }

    @Test
    void aBodyWithNoInputsOrNoOutputs() {
        bothReject("a2" + OUTPUTS + FEE, "a0", "has no SET");
        bothReject("a2" + INPUTS + FEE, "a0", "has no LIST");
    }

    @Test
    void anUnknownBodyKey() {
        bothReject("a4" + INPUTS + OUTPUTS + FEE + "0c" + "00", "a0", "unknown transaction_body key 0c");
        bothReject("a4" + INPUTS + OUTPUTS + FEE + "17" + "00", "a0", "unknown transaction_body key 17");
    }

    @Test
    void aKeyThatIsNotAnUnsignedInteger() {
        bothReject("a4" + INPUTS + OUTPUTS + FEE + "20" + "00", "a0", "unknown transaction_body key 20");
        bothReject("a4" + INPUTS + OUTPUTS + FEE + "4100" + "00", "a0", "unknown transaction_body key 4100");
        bothReject("a4" + INPUTS + OUTPUTS + FEE + "c203" + "00", "a0", "unknown transaction_body key c203");
    }

    @Test
    void aFieldOutsideItsEra() {
        bothReject("a4" + INPUTS + OUTPUTS + FEE + "06" + "a0", "a0", "transaction_body key 06 is not in Conway");
    }

    @Test
    void anUnknownWitnessKey() {
        bothReject(BODY, "a108" + "80", "unknown transaction_witness_set key 08");
    }

    @Test
    void aRepeatedKey() {
        bothReject("a4" + INPUTS + OUTPUTS + FEE + "02" + "00", "a0", "repeated transaction_body key 02");
        bothReject(BODY, "a2" + "0081" + "82" + "5820" + "33".repeat(32) + "5840" + "44".repeat(64)
                + "0081" + "82" + "5820" + "33".repeat(32) + "5840" + "44".repeat(64),
                "repeated transaction_witness_set key 00");
    }

    @Test
    void equalKeysEncodedDifferently() {
        bothReject("a4" + INPUTS + OUTPUTS + FEE + "1802" + "00", "a0", "repeated transaction_body key 1802");
        bothReject("a4" + INPUTS + OUTPUTS + "1802" + "00" + FEE, "a0", "repeated transaction_body key 02");
        bothReject("a4" + INPUTS + OUTPUTS + FEE + "190002" + "00", "a0", "repeated transaction_body key 190002");
    }

    @Test
    void aNonMinimalKeyIsTheField() {
        bothAccept("a3" + INPUTS + OUTPUTS + "1802" + "00", "a0");
    }

    @Test
    void aTaggedRecord() {
        bothReject("d81e" + BODY, "a0", "transaction_body at 1 is not an untagged map");
        bothReject(BODY, "d81ea0", "transaction_witness_set at");
    }

    @Test
    void auxDataInItsTag() {
        byte[] tagged = HexUtil.decodeHexString("84" + BODY + "a0" + "f5" + "d90103a100a0");
        assertThatCode(() -> HaskellPolicy.CONWAY.transaction(tagged)).doesNotThrowAnyException();
        bothReject(HexUtil.decodeHexString("84" + BODY + "a0" + "f5" + "d90104a100a0"), "expected a map");
        bothReject(HexUtil.decodeHexString("84" + BODY + "a0" + "f5" + "d90103d90103a100a0"),
                "auxiliary_data_map at");
        bothReject(HexUtil.decodeHexString("84" + BODY + "a0" + "f5" + "d90103a10580"),
                "unknown auxiliary_data_map key 05");
        bothReject(HexUtil.decodeHexString("84" + BODY + "a0" + "f5" + "9fa080ff"), "RecD");
    }

    @Test
    void aRedeemerKeyMayRepeat() {
        // [0, 0] and [0, 1800] are one key, and Map.fromList keeps the last value
        String redeemers = "a105a2" + "820000" + "82008201" + "01" + "82001800" + "82008202" + "02";
        bothAccept(BODY, redeemers);
        bothReject(BODY, "a105a1" + "820600" + "82008201" + "01", "redeemer tag 6");
        bothReject(BODY, "a105a1" + "820000" + "9f00820101ff", "REDEEMER_VALUE");
        bothReject(BODY, "a105d9010281" + "84000000820101", "redeemers at");
    }

    @Test
    void anOutputOrRedeemersMapWithAnEightByteHead() {
        // cborg reads ba as TypeMapLen and bb as TypeMapLen64, which the output and redeemers decoders send to their
        // list form (Babbage/TxOut.hs:554-556, Alonzo/TxWits.hs:556-562)
        String output = "00" + "581d60" + "33".repeat(28) + "01" + "00";
        String body = "a3" + INPUTS + "0181%s" + FEE;
        bothAccept(body.formatted("a2" + output), "a0");
        bothAccept(body.formatted("b802" + output), "a0");
        bothAccept(body.formatted("ba00000002" + output), "a0");
        bothReject(body.formatted("bb0000000000000002" + output), "a0", "8-byte head");

        String redeemer = "820000" + "8200820101";
        bothAccept(BODY, "a105" + "ba00000001" + redeemer);
        bothReject(BODY, "a105" + "bb0000000000000001" + redeemer, "8-byte head");
    }

    @Test
    void aMultiAssetIsSmallEnoughToCompact() {
        // isMultiAssetSmallEnough: 44 * assets + 28 * policies <= 65535, a multiple of 4 on the left
        String mint = "a4" + INPUTS + OUTPUTS + FEE + "09%s";
        bothAccept(mint.formatted(multiAsset(1483, 10, "01")), "a0");
        bothReject(mint.formatted(multiAsset(1485, 7, "01")), "a0", "too big to compact");

        String output = "a2" + "00" + "581d60" + "33".repeat(28) + "01" + "8200%s";
        String body = "a3" + INPUTS + "0181" + output + FEE;
        bothAccept(body.formatted(multiAsset(1483, 10, "01")), "a0");
        bothReject(body.formatted(multiAsset(1485, 7, "01")), "a0", "too big to compact");
    }

    /** A multi-asset of {@code assets} two-byte names spread over {@code policies} policies, each of one amount. */
    private static String multiAsset(int assets, int policies, String amount) {
        StringBuilder cbor = new StringBuilder(mapHead(policies));
        for (int policy = 0; policy < policies; policy++) {
            int names = assets / policies + (policy < assets % policies ? 1 : 0);
            cbor.append("581c").append("%056x".formatted(policy)).append(mapHead(names));
            for (int name = 0; name < names; name++) {
                cbor.append("42").append("%04x".formatted(name)).append(amount);
            }
        }
        return cbor.toString();
    }

    private static String mapHead(int size) {
        if (size < 24) {
            return "%02x".formatted(0xa0 + size);
        }
        return size < 256 ? "b8%02x".formatted(size) : "b9%04x".formatted(size);
    }

    @Test
    void aTransactionWithoutItsValidityFlag() {
        bothReject(HexUtil.decodeHexString("83" + BODY + "a0" + "f6"), "pre_alonzo_transaction item at 1 is not in");
    }

    @Test
    void aCertificateIsWalkedInTheBody() {
        bothAccept("a4" + INPUTS + OUTPUTS + FEE + "0481" + "8200" + CRED, "a0");
        bothReject("a4" + INPUTS + OUTPUTS + FEE + "0480", "a0", "Certificates");
        bothReject("a4" + INPUTS + OUTPUTS + FEE + "0482" + "8200" + CRED + "8200" + CRED, "a0",
                "duplicate certificate at");
        bothReject("a4" + INPUTS + OUTPUTS + FEE + "0481" + "8200" + "8200" + "581b" + "11".repeat(27), "a0",
                "key hash of 27 bytes");
        bothReject("a4" + INPUTS + OUTPUTS + FEE + "0481" + "8205" + CRED, "a0", "certificate key 05");
    }

    @Test
    void aValueTooDeepForARecursiveReaderIsRejected() {
        String deep = "81".repeat(200_000) + "00";
        bothReject("a4" + INPUTS + OUTPUTS + FEE + "0481" + "830f" + CRED + deep, "a0", "");
        bothReject("a4" + INPUTS + OUTPUTS + FEE + "0481" + "8200" + deep, "a0", "");
    }

    private static void certificate(HaskellPolicy policy, String certificate) {
        policy.walk(CertKind.RECORD, StrictCbor.span(HexUtil.decodeHexString(certificate)));
    }

    @Test
    void definiteByteStringsAndAnchorsEndBelowDecoderVersion12() {
        String chunked = "5f" + "5801" + "11" + "581b" + "11".repeat(27) + "ff";
        String keyDelegation = "8302" + CRED;
        HaskellPolicy eleven = new HaskellPolicy(11);
        HaskellPolicy twelve = new HaskellPolicy(12);

        assertThatThrownBy(() -> certificate(eleven, keyDelegation + chunked))
                .isInstanceOf(LedgerDecodingException.class).hasMessageContaining("indefinite-length byte string");
        assertThatCode(() -> certificate(twelve, keyDelegation + chunked)).doesNotThrowAnyException();

        String update = "8312" + CRED;
        assertThatCode(() -> certificate(eleven, update + ANCHOR)).doesNotThrowAnyException();
        assertThatThrownBy(() -> certificate(eleven, update + "9f" + "60" + HASH32 + "ff"))
                .isInstanceOf(LedgerDecodingException.class).hasMessageContaining("indefinite-length array");
        assertThatCode(() -> certificate(twelve, update + "9f" + "60" + HASH32 + "ff")).doesNotThrowAnyException();
    }

    @Test
    void aMarginOfBignumsEndsAtDecoderVersion12() {
        String pool = "8a03" + HASH28 + HASH32 + "0000" + "d81e82" + "c24101" + "02" + "581de0" + "44".repeat(28)
                + "80" + "80" + "f6";
        assertThatCode(() -> certificate(new HaskellPolicy(11), pool)).doesNotThrowAnyException();
        assertThatThrownBy(() -> certificate(new HaskellPolicy(12), pool)).isInstanceOf(LedgerDecodingException.class);
    }

    private static final String KEY = "5820" + "55".repeat(32);
    private static final String SIGMA = "5840" + "66".repeat(64);
    private static final String VRF = "82" + "5840" + "33".repeat(64) + "5850" + "44".repeat(80);
    private static final String OPCERT = "84" + KEY + "00" + "00" + SIGMA;
    private static final String KES_SIGNATURE = "5901c0" + "77".repeat(448);

    /** {@code [block_number, slot, prev_hash, issuer, vrf_vkey, vrf_result, size, hash, opcert, protocol_version]} */
    private static String nestedBody(String number, String prev, String vrf, String size, String opcert,
                                      String version) {
        return "8a" + number + "00" + prev + KEY + KEY + vrf + size + HASH32 + opcert + version;
    }

    private static final String BODY_10 = nestedBody("00", "f6", VRF, "00", OPCERT, "820a00");

    private static void decodes(String header) {
        assertThatCode(() -> HaskellPolicy.header(StrictCbor.span(HexUtil.decodeHexString(header))))
                .doesNotThrowAnyException();
    }

    private static void refuses(String header, String reason) {
        assertThatThrownBy(() -> HaskellPolicy.header(StrictCbor.span(HexUtil.decodeHexString(header))))
                .isInstanceOf(LedgerDecodingException.class).hasMessageContaining(reason);
    }

    @Test
    void aHeaderIsReadWithHaskellsBounds() {
        decodes("82" + BODY_10 + KES_SIGNATURE);
        decodes("82" + nestedBody("00", HASH32, VRF, "1affffffff", OPCERT, "820d1affffffff") + KES_SIGNATURE);
        decodes("82" + nestedBody("1bffffffffffffffff", "f6", VRF, "00",
                "84" + KEY + "1bffffffffffffffff" + "1bffffffffffffffff" + SIGMA, "820a00") + KES_SIGNATURE);

        refuses("82" + nestedBody("20", "f6", VRF, "00", OPCERT, "820a00") + KES_SIGNATURE, "unsigned integer");
        refuses("82" + nestedBody("00", "40", VRF, "00", OPCERT, "820a00") + KES_SIGNATURE, "hash of 0 bytes");
        refuses("82" + nestedBody("00", "f6", VRF, "1b0000000100000000", OPCERT, "820a00") + KES_SIGNATURE, "exceeds");
        refuses("82" + nestedBody("00", "f6", VRF, "00", OPCERT, "820e00") + KES_SIGNATURE, "exceeds");
        refuses("82" + nestedBody("00", "f6", VRF, "00", OPCERT, "820a1b0000000100000000") + KES_SIGNATURE, "exceeds");
        refuses("82" + BODY_10 + "5901bf" + "77".repeat(447), "KES signature of 447 bytes");
        refuses("8a" + BODY_10.substring(2), "");
    }

    /** The regression of ADR 0003 M2: a key in chunks has the right length once joined, and Haskell refuses it. */
    @Test
    void aHeadersByteStringsAreDefinite() {
        String chunkedKey = "5f" + KEY + "ff";
        refuses("82" + nestedBody("00", "f6", VRF, "00", "84" + chunkedKey + "0000" + SIGMA, "820a00") + KES_SIGNATURE,
                "indefinite-length byte string");
        refuses("82" + "8a" + "00" + "00" + "f6" + KEY + chunkedKey + VRF + "00" + HASH32 + OPCERT + "820a00"
                + KES_SIGNATURE, "indefinite-length byte string");
        refuses("82" + nestedBody("00", "f6", VRF, "00", "84" + KEY + "0000" + "5f" + SIGMA + "ff", "820a00")
                + KES_SIGNATURE, "indefinite-length byte string");
    }

    /** {@code OutputVRF} takes any byte string (DecCBOR.hs:564-566); the proof is fixed in size, the pair definite. */
    @Test
    void aVrfResultHasAnyOutputAndAFixedProof() {
        decodes("82" + nestedBody("00", "f6", "82" + "40" + "5850" + "44".repeat(80), "00", OPCERT, "820a00")
                + KES_SIGNATURE);
        refuses("82" + nestedBody("00", "f6", "82" + "40" + "584f" + "44".repeat(79), "00", OPCERT, "820a00")
                + KES_SIGNATURE, "VRF proof of 79 bytes");
        refuses("82" + nestedBody("00", "f6", "9f" + "40" + "5850" + "44".repeat(80) + "ff", "00", OPCERT, "820a00")
                + KES_SIGNATURE, "VRF result");
    }

    /** {@code decodeRecordNamed} at cardano-ledger 6176e413, of which the dataset holds such headers as valid. */
    @Test
    void aHeaderAndItsBodyMayBeOfEitherLength() {
        decodes("9f" + BODY_10 + KES_SIGNATURE + "ff");
        decodes("82" + "9f" + BODY_10.substring(2) + "ff" + KES_SIGNATURE);
    }

    @Test
    void aHeaderHasTheLayoutItsBodyHas() {
        decodes("82" + "8f" + "00" + "00" + "f6" + KEY + KEY + VRF + VRF + "00" + HASH32 + KEY + "00" + "00" + SIGMA
                + "0200" + KES_SIGNATURE);
        refuses("82" + "89" + BODY_10.substring(2, BODY_10.length() - "820a00".length()) + KES_SIGNATURE, "15 or 10");
        refuses("82" + "8a" + "00".repeat(8) + "00" + "00" + KES_SIGNATURE, "");
    }

    @Test
    void aSetRepeatsNoItemHoweverItIsEncoded() {
        String input = "82" + HASH32 + "05";
        bothReject("a3" + "0082" + input + input + OUTPUTS + FEE, "a0", "duplicate transaction_input");
        bothReject("a3" + "0082" + input + "9f" + HASH32 + "1805" + "ff" + OUTPUTS + FEE, "a0",
                "duplicate transaction_input");
        bothAccept("a3" + "0082" + input + "82" + HASH32 + "06" + OUTPUTS + FEE, "a0");

        String signer = "581c" + "88".repeat(28);
        bothReject("a4" + INPUTS + OUTPUTS + FEE + "0e82" + signer + signer, "a0", "duplicate addr_keyhash");
        bothReject("a4" + INPUTS + OUTPUTS + FEE + "0e82" + signer + "59001c" + "88".repeat(28), "a0",
                "duplicate addr_keyhash");
    }

    @Test
    void aPoolRepeatsNoOwner() {
        String owner = "581c" + "88".repeat(28);
        String pool = "8a03" + HASH28 + HASH32 + "0000" + "d81e820102" + "581de0" + "44".repeat(28);
        bothAccept("a4" + INPUTS + OUTPUTS + FEE + "0481" + pool + "81" + owner + "80" + "f6", "a0");
        bothReject("a4" + INPUTS + OUTPUTS + FEE + "0481" + pool + "82" + owner + owner + "80" + "f6", "a0",
                "duplicate key hash");
    }

    @Test
    void anItemOfASetTooDeepForARecursiveReaderIsRejected() {
        String deep = "81".repeat(200_000) + "00";
        bothReject("a3" + "0082" + deep + deep + OUTPUTS + FEE, "a0", "");
        bothReject("a3" + "0082" + "82" + deep + "00" + "82" + deep + "00" + OUTPUTS + FEE, "a0", "nested");
    }

    @Test
    void anIndexOfAnInputIsAWord16() {
        bothAccept("a3" + "0081" + "82" + HASH32 + "19ffff" + OUTPUTS + FEE, "a0");
        bothReject("a3" + "0081" + "82" + HASH32 + "1a00010000" + OUTPUTS + FEE, "a0", "exceeds");
    }

    private static final String ACCOUNT = "581de0" + "44".repeat(28);
    private static final String ACTION_ID = "82" + HASH32 + "00";

    /** A body with one proposal of {@code action}. */
    private static String proposing(String action) {
        return "a4" + INPUTS + OUTPUTS + FEE + "1481" + "8400" + ACCOUNT + action + ANCHOR;
    }

    @Test
    void votersAndActionIdsRepeatNoKeyHoweverItIsEncoded() {
        String voter = "8202" + HASH28;
        String longVoter = "820259001c" + "11".repeat(28);
        String vote = "8200f6";
        String votes = "a4" + INPUTS + OUTPUTS + FEE + "13";
        bothAccept(votes + "a1" + voter + "a1" + ACTION_ID + vote, "a0");
        bothReject(votes + "a2" + voter + "a1" + ACTION_ID + vote + longVoter + "a1" + ACTION_ID + vote, "a0",
                "duplicate voter");
        bothReject(votes + "a1" + voter + "a2" + ACTION_ID + vote + "82" + "590020" + "22".repeat(32) + "00" + vote,
                "a0", "duplicate gov_action_id");
        bothReject(votes + "a1" + voter + "a1" + ACTION_ID + "8203f6", "a0", "exceeds 2");
        bothReject(votes + "a1" + voter + "a0", "a0", "must be non-empty");
        bothReject(votes + "a1" + voter + "a1" + ACTION_ID + "9f00f6ff", "a0", "VOTING_PROCEDURE is a RecD record");
    }

    @Test
    void proposalsRepeatNoneAsDecodedValues() {
        String cred = "8200" + HASH28;
        String other = "8200581c" + "33".repeat(28);
        String body = "a4" + INPUTS + OUTPUTS + FEE + "1482";
        String removals = "8400" + ACCOUNT + "8504f6" + "82" + cred + other + "a0d81e820101" + ANCHOR;
        String reordered = "8400" + ACCOUNT + "8504f6" + "82" + other + cred + "a0d81e820101" + ANCHOR;
        bothReject(body + removals + reordered, "a0", "duplicate proposal_procedure");
        String update = "8400" + ACCOUNT + "8400f6" + "a2" + "0301" + "0401" + "f6" + ANCHOR;
        String updateReordered = "8400" + ACCOUNT + "8400f6" + "a2" + "0401" + "0301" + "f6" + ANCHOR;
        bothReject(body + update + updateReordered, "a0", "duplicate proposal_procedure");
        bothAccept(body + removals + update, "a0");
    }

    @Test
    void aGovernanceActionIsReadWithHaskellsBounds() {
        String cred = "8200" + HASH28;
        bothAccept(proposing("8301f6" + "820c00"), "a0");
        bothReject(proposing("8301f6" + "820d00"), "a0", "exceeds the maximum expected version of 12");
        bothReject(proposing("8504f6" + "82" + cred + "820059001c" + "11".repeat(28) + "a0d81e820101"), "a0",
                "duplicate credential");
        bothReject(proposing("8504f6" + "80" + "a2" + cred + "01" + "820059001c" + "11".repeat(28) + "01"
                + "d81e820101"), "a0", "duplicate credential");
        bothReject(proposing("8302" + "a1" + ACCOUNT + "01" + "5820" + "33".repeat(32)), "a0", "script hash");
        bothReject(proposing("8305f6" + "9f" + ANCHOR + "f6" + "ff"), "a0", "CONSTITUTION is a RecD record");
        bothReject("a4" + INPUTS + OUTPUTS + FEE + "1481" + "9f00" + ACCOUNT + "8106" + ANCHOR + "ff", "a0",
                "proposal_procedure is a RecD record");
    }

    @Test
    void aParameterUpdateIsReadByItsTable() {
        bothAccept(proposing("8400f6" + "a0" + "f6"), "a0");
        bothReject(proposing("8400f6" + "a1" + "0e" + "820a00" + "f6"), "a0", "is not in Conway");
        bothReject(proposing("8400f6" + "a1" + "12" + "a1" + "190100" + "80" + "f6"), "a0", "exceeds 255");
        bothReject(proposing("8400f6" + "a1" + "12" + "a1" + "00" + "81" + "1b8000000000000000" + "f6"), "a0",
                "overflow");
        bothReject(proposing("8400f6" + "a1" + "13" + "9f" + "d81e820101" + "d81e820101" + "ff" + "f6"), "a0",
                "EX_UNIT_PRICES is a RecD record");
        bothReject(proposing("8400f6" + "a1" + "09" + "d81e820100" + "f6"), "a0", "denominator");
    }

    /**
     * Haskell decodes a Conway transaction at {@code eraProtVerLow ConwayEra}, 9, at every Conway protocol version, so
     * auxiliary data {@code [metadata, scripts]} ({@code RecD}, Alonzo/TxAuxData.hs:220, 246-252) must be definite; it
     * may be indefinite only from 12 ({@code decodeDijkstra}).
     */
    @Test
    void aConwayTransactionDecodesAtItsEraVersionWhereAuxDataIsDefinite() {
        HaskellPolicy conway = HaskellPolicy.forEra(Era.Conway);
        assertThat(conway).isSameAs(HaskellPolicy.CONWAY);

        CborSpan definite = StrictCbor.span(HexUtil.decodeHexString("82" + "a0" + "80"));
        CborSpan indefinite = StrictCbor.span(HexUtil.decodeHexString("9f" + "a0" + "80" + "ff"));
        assertThatCode(() -> conway.walk(FieldType.OPTIONAL_AUXILIARY_DATA, definite)).doesNotThrowAnyException();
        assertThatThrownBy(() -> conway.walk(FieldType.OPTIONAL_AUXILIARY_DATA, indefinite))
                .isInstanceOf(LedgerDecodingException.class).hasMessageContaining("auxiliary data [metadata, scripts]");
        assertThatCode(() -> new HaskellPolicy(12).walk(FieldType.OPTIONAL_AUXILIARY_DATA, indefinite))
                .doesNotThrowAnyException();

        byte[] tx = HexUtil.decodeHexString("84" + BODY + "a0" + "f5" + "9f" + "a0" + "80" + "ff");
        assertThatThrownBy(() -> HaskellPolicy.CONWAY.transaction(tx)).isInstanceOf(LedgerDecodingException.class);
    }

    @Test
    void anEraWithoutRulesIsRefusedNotReadAsConway() {
        assertThatThrownBy(() -> HaskellPolicy.forEra(Era.Babbage)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Babbage");
    }

    @Test
    void decoderVersionsBelowConwayAreNotKnown() {
        assertThatThrownBy(() -> new HaskellPolicy(8)).isInstanceOf(IllegalArgumentException.class);
    }
}
