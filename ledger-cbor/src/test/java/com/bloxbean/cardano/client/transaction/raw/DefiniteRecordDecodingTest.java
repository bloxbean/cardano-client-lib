package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Below decoder version 12 a record Haskell decodes with {@code RecD} (or as a tuple) must be a definite-length array:
 * {@code RecD} checks for the end of an indefinite list before it reads any field ({@code decodeCount (RecD cn)},
 * Coders.hs:482: "Excess terms in array"), and a tuple reads its length with {@code decodeListLenOf}. Sums
 * ({@code decodeRecordSum}) and records read with {@code decodeRecordNamed} or {@code CBORGroup} accept either length
 * (cardano-ledger {@code f649f975}; yano #202).
 *
 * <p>The rejected samples marked "corpus" are from cardano-cbor-dataset's Conway corpus ({@code invalid-zap-0},
 * commit {@code 6cd8c928}), which Haskell rejects and the strict readers accepted before #202.</p>
 */
class DefiniteRecordDecodingTest {

    private static final String HASH28 = "581c" + "00".repeat(28);
    private static final String HASH32 = "5820" + "00".repeat(32);
    private static final String ACCOUNT = "581de0" + "00".repeat(28);
    private static final String ANCHOR = "8260" + HASH32;
    private static final String INDEFINITE_ANCHOR = "9f60" + HASH32 + "ff";
    private static final String INPUT = "82" + HASH32 + "00";
    private static final String BODY_ENTRIES = "0081" + INPUT + "0180" + "0200";
    private static final String HALF = "d81e820102";

    private static CborSpan span(String hex) {
        return StrictCbor.span(HexUtil.decodeHexString(hex));
    }

    private static void auxData(CborSpan auxData) {
        HaskellPolicy.CONWAY.walk(FieldType.OPTIONAL_AUXILIARY_DATA, auxData);
    }

    private static void decodes(Runnable read) {
        assertThatCode(read::run).doesNotThrowAnyException();
    }

    private static void refuses(Runnable read) {
        assertThatThrownBy(read::run).isInstanceOf(LedgerDecodingException.class);
    }

    private static void transaction(String body, String witnesses) {
        HaskellPolicy.CONWAY.transaction(HexUtil.decodeHexString("84" + body + witnesses + "f5f6"));
    }

    private static void redeemers(String redeemers) {
        transaction("a3" + BODY_ENTRIES, "a105" + redeemers);
    }

    /** One DRep vote on {@code actionId} with {@code procedure}. */
    private static void vote(String actionId, String procedure) {
        transaction("a4" + BODY_ENTRIES + "13a1" + "8202" + HASH28 + "a1" + actionId + procedure, "a0");
    }

    private static void proposal(String procedure) {
        HaskellPolicy.CONWAY.walk(TxBodyField.PROPOSAL_PROCEDURES.spec().record(), span(procedure));
    }

    private static void action(String action) {
        proposal("8400" + ACCOUNT + action + ANCHOR);
    }

    private static void update(String update) {
        HaskellPolicy.CONWAY.walk(FieldType.PROTOCOL_PARAM_UPDATE, span(update));
    }

    private static void output(String output) {
        HaskellPolicy.CONWAY.walk(FieldType.TRANSACTION_OUTPUT, span(output));
    }

    private static void value(String value) {
        HaskellPolicy.CONWAY.walk(FieldType.VALUE, span(value));
    }

    @Test
    void anAnchorIsARecD() {
        // RecD Anchor (BaseTypes.hs:995-1003)
        decodes(() -> HaskellPolicy.CONWAY.walk(FieldType.ANCHOR, span(ANCHOR)));
        refuses(() -> HaskellPolicy.CONWAY.walk(FieldType.ANCHOR, span(INDEFINITE_ANCHOR)));
    }

    @Test
    void aVotingProcedureIsARecD() {
        // RecD VotingProcedure (Procedures.hs:455-466); the voted GovActionId key is RecD GovActionId (:182-190).
        decodes(() -> vote(INPUT, "8202f6"));
        decodes(() -> vote(INPUT, "8202" + ANCHOR));
        refuses(() -> vote(INPUT, "9f02f6ff")); // corpus voting_procedure 00009
        refuses(() -> vote(INPUT, "8202" + INDEFINITE_ANCHOR));
        refuses(() -> vote(INPUT, "9f029f6058208acee5539785d202f1cb9e2fc55af9de709b1705fee0d133eefe4f400eee3a3dffff"));
        refuses(() -> vote("9f" + HASH32 + "00ff", "8202f6"));
    }

    @Test
    void aGovActionIsASumButItsGovActionIdAndConstitutionAreRecDs() {
        decodes(() -> action("8203" + INPUT));
        decodes(() -> action("9f03" + INPUT + "ff")); // Summands "GovAction" (Procedures.hs:876-895)
        // corpus gov_action 00015: no-confidence with [_ txid, index]
        refuses(() -> action("82039f582068e077ec85c10053fbc251e6c2ea77c9c2fd7a0c8bbae5edd1959a49598f623c19ab47ff"));
        refuses(() -> action("9f039f58208d817d0abd86ae335cd6f993d5991e068a10bbb67e873910dbd69a6d11119cc719531bffff"));
        // RecD Constitution (:984-995) and the Anchor inside it
        decodes(() -> action("8305f6" + "82" + ANCHOR + "f6"));
        refuses(() -> action("8305f6" + "9f" + ANCHOR + "f6ff"));
        refuses(() -> action("8305f6" + "82" + INDEFINITE_ANCHOR + "f6"));
        // ProtVer (a CBORGroup), a committee credential (a sum) and a tag-30 rational accept either length.
        decodes(() -> action("8301f6" + "9f0a00ff"));
        decodes(() -> action("8504f6" + "d901029f9f00" + HASH28 + "ffff" + "a0" + "d81e9f0102ff"));
    }

    @Test
    void aProposalProcedureIsARecD() {
        // RecD ProposalProcedure (Procedures.hs:520-535)
        decodes(() -> proposal("8400" + ACCOUNT + "8106" + ANCHOR));
        decodes(() -> proposal("8400" + ACCOUNT + "9f06ff" + ANCHOR));
        refuses(() -> proposal("9f00" + ACCOUNT + "8106" + ANCHOR + "ff"));
        refuses(() -> proposal("8400" + ACCOUNT + "8106" + INDEFINITE_ANCHOR));
        // corpus proposal_procedure 00082: an indefinite anchor
        refuses(() -> proposal("841bfe0a5bc052e29883581df04a09668e29cf32a215f56a15d22013623f99f2d22213e7a34735baaa"
                + "81069f67f2bfb68edaa06a5820e68e1d09d0dc2f4f0156027056540c274e5b90544bec04efe38bf6e44454c896ff"));
    }

    @Test
    void pricesAndExUnitsAreRecDsButVotingThresholdsAreNot() {
        // RecD Prices, RecD ExUnits (Plutus/ExUnits.hs:195-231)
        decodes(() -> update("a113" + "82" + HALF + HALF));
        refuses(() -> update("a113" + "9f" + HALF + HALF + "ff"));
        decodes(() -> update("a114" + "820102"));
        refuses(() -> update("a114" + "9f0102ff"));
        refuses(() -> update("a115" + "9f0102ff"));
        // decodeRecordNamed "PoolVotingThresholds" / "DRepVotingThresholds" (Conway/PParams.hs:366-374, 519-532)
        decodes(() -> update("a11819" + "9f" + HALF.repeat(5) + "ff"));
        decodes(() -> update("a1181a" + "9f" + HALF.repeat(10) + "ff"));
        // corpus protocol_param_update 00028: ExUnits [_ mem, steps] at key 20
        refuses(() -> update("b81b001b932aa57b4dc06f47011b3c240678686dd169021a41ace0ae031a487ca86f0419e75f051bd3aca1"
                + "d9249a36f4061b04879335bbca1648071ac4223df00819e0ab09d81e821bb6af9e11a190f27a1b107e5d98785b420f0ad81e"
                + "82184a1902410bd81e821bfffffffffffffca61bfffffffffffffcca101bfaf389f53a6a6f4e111be54e573a2510925b12a6"
                + "01821b08ce89830c1b7bcc1b0c78a4aa32223ac302823b79aec85bd020a2241b48285686a78ff38f187d9f1b23c0eeddc9e5"
                + "6e1aff189c8018aa9fff18d8823b61fbc4b57ca53af33b2ccd4b8c5b46fe00149f1b5f833f5025504cce1b301f9ad388c236"
                + "1fff15821b493ef9887fcf4ce91b4c9036eaa026f2d9161a7aa64c2717195729181819b06318199fd81e9f19029919034"
                + "3ffd81e9f19011719034dffd81e8212184fd81e821bfffffffffffffcb41bfffffffffffffd42d81e821901fb19027fff18"
                + "1b193053181c1a5768c8e2181d1a4be60e8b181e1b9eda2a57531f3101181f1bc85c2004626de69418201ad1ccff7b"));
    }

    @Test
    void redeemerExUnitsAndMapValuesAreDefiniteButRedeemerElementsAndKeysAreNot() {
        // List form: an element is decodeRecordNamed "Redeemer" (Alonzo/TxWits.hs:584-592), its ExUnits RecD.
        decodes(() -> redeemers("9f84001a6a6b0b2842195f821b789349560658ec531b404cf7e6528147c1ff"));
        decodes(() -> redeemers("819f000000820101ff"));
        refuses(() -> redeemers("9f84001a6a6b0b2842195f9f1b789349560658ec531b404cf7e6528147c1ffff")); // corpus 00003
        refuses(() -> redeemers("8184021ad9aa6250c2409f1b793ee4d5d64e21321b7b4b5cc6362584daff")); // corpus redeemer 21
        // Map form: the key is a CBORGroup, the value a (Data, ExUnits) tuple (decodeListLenOf 2).
        decodes(() -> redeemers("a1820000" + "8200820101"));
        decodes(() -> redeemers("a19f0000ff" + "8200820101"));
        refuses(() -> redeemers("a1820000" + "9f00820101ff"));
        refuses(() -> redeemers("a1820000" + "82009f0101ff"));
        refuses(() -> redeemers("bf82051a2de2c8e49f5f476aba7b342efc82ff821b1040e0a7e2b9fa9d1b0a9f073ac8dfeeeaffff"));
    }

    @Test
    void redeemerExUnitsAreAtMostInt64() {
        // decNat: "values must not exceed maxBound :: Int64" (Plutus/ExUnits.hs:203-212), as in a parameter update
        decodes(() -> redeemers("8184000000" + "821b7fffffffffffffff00"));
        refuses(() -> redeemers("8184000000" + "821b800000000000000000"));
        refuses(() -> redeemers("8184000000" + "82001b8000000000000000"));
    }

    @Test
    void aMultiAssetValueIsARecDButALegacyOutputIsNot() {
        String assets = "a1" + HASH28 + "a14001";
        // RecD MaryValue below version 12 (Mary/Value.hs:298-310)
        decodes(() -> value("8201" + assets));
        refuses(() -> value("9f01" + assets + "ff"));
        // the legacy TxOut is read with decodeListLenOrIndef (Babbage/TxOut.hs:553-576)
        decodes(() -> output("9f581d60" + "00".repeat(28) + "01ff"));
        decodes(() -> output("82581d60" + "00".repeat(28) + "8201" + assets));
        // corpus transaction_output 00014: a legacy output whose value is [_ coin, {_}]
        String output00014 = "9f583920ce1601c72695306e4875141127a07f86d34c01c6ae537053f2bdef7b4a92fc7ea9143ffb"
                + "68d36495d14fd9a6e2bccb28c97a57f3e3157b659f1bf60f5cb03c4cb211bfffff58206e97a436a598a96407d262b73aa4e3"
                + "1a1158df6fe62f77ddcecce6865dbc95a5ff";
        refuses(() -> output(output00014));
    }

    @Test
    void allegraAuxiliaryDataIsARecD() {
        // RecD AlonzoTxAuxDataRaw below version 12 (Alonzo/TxAuxData.hs:245-251); decodeRecordNamed only from 12.
        // cardano-cbor-dataset labels auxiliary_data/valid/00003 and 00025, indefinite [metadata, scripts], valid:
        // its labels come from the test-only decoder (testlib Test/Cardano/Ledger/Alonzo/Binary/Annotator.hs:118-136).
        decodes(() -> auxData(span("82a080")));
        refuses(() -> auxData(span("9fa080ff")));
        // the metadata map and the script list inside it may be indefinite (decodeMap, decodeStrictSeq)
        decodes(() -> auxData(span("82bf01a0ff9fff")));
    }
}
