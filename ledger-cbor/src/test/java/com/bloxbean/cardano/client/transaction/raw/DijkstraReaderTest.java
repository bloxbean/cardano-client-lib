package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ADR 0003 D4a: Dijkstra's block, transaction forms, sub-transactions and new fields, each read and walked. */
class DijkstraReaderTest {
    private static final String H28 = "581c" + "11".repeat(28);
    private static final String H28_B = "581c" + "12".repeat(28);
    private static final String H32 = "5820" + "22".repeat(32);
    private static final String INPUT = "82" + H32 + "05";
    private static final String BODY = "a3" + "00" + "81" + INPUT + "01" + "80" + "02" + "1864";
    private static final String SUB_BODY = "a2" + "00" + "81" + INPUT + "01" + "80";
    private static final String SUB_TX = "83" + SUB_BODY + "a0" + "f6";
    private static final String VALID = "84" + BODY + "a0" + "f6" + "f5";
    private static final String INVALID = "84" + BODY + "a0" + "f6" + "f4";
    private static final String BLS_KEY = "82" + "5860" + "aa".repeat(96) + "5830" + "bb".repeat(48);
    private static final String POOL_REST = "1a000f4240" + "1903e8" + "d81e820105" + ra("e1") + "d90102" + "81"
            + H28_B + "80" + "f6";
    private static final String POOL = "8a" + "03" + H28 + H32 + POOL_REST;

    private static final FormatPolicy STRICT = StrictLedgerPolicy.INSTANCE;

    private static String ra(String header) {
        return "581d" + header + "33".repeat(28);
    }

    private static byte[] bytes(String hex) {
        return HexUtil.decodeHexString(hex);
    }

    private static CborSpan span(String hex) {
        return CborSpan.of(bytes(hex));
    }

    private static String hex(byte[] bytes) {
        return HexUtil.encodeHexString(bytes);
    }

    private static String block(String body) {
        return "8208" + "82" + RawHeaderTest.LEIOS_HEADER + body;
    }

    private static RawTxBody dijkstra(String body) {
        return RawTx.of(bytes("83" + body + "a0" + "f6")).body(Era.Dijkstra);
    }

    // ---- the block ----

    @Test
    void aUnifiedBlockReadsEachTransactionWholeWithItsFlag() {
        String body = "83" + "82" + VALID + INVALID + "f6" + "f6";
        RawBlock block = RawBlock.of(bytes(block(body)));
        assertThat(block.era()).isEqualTo(Era.Dijkstra);
        assertThat(block.txCount()).isEqualTo(2);
        assertThat(block.invalidTxIndexes()).containsExactly(1);
        assertThat(block.tx(0).isValid()).isTrue();
        assertThat(block.tx(1).isValid()).isFalse();
        assertThat(block.tx(1).txId()).isEqualTo(Blake2bUtil.blake2bHash256(bytes(BODY)));
        // as submitted: the flag is the block's, so the form has none (Dijkstra Tx.hs, toCBORForMempoolSubmission)
        assertThat(hex(block.txBytes(1))).isEqualTo("83" + BODY + "a0" + "f6");
        assertThat(block.tx(1).span().bytes()).isEqualTo(block.txBytes(1));
        assertThat(block.bodyHash()).isEqualTo(Blake2bUtil.blake2bHash256(bytes(body)));
        assertThat(block.rawHeader().leiosCertInBody()).contains(true);
        assertThat(block.invalidTransactions()).isEmpty();
        assertThat(block.leiosCertificate()).isEmpty();
        assertThat(block.perasCertificate()).isEmpty();
        assertThatThrownBy(block::transactionBodies).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(block::transactionWitnessSets).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(block::auxiliaryDataSet).isInstanceOf(UnsupportedOperationException.class);
        assertThat(hex(block.transaction(1).bytes())).isEqualTo(INVALID);
        assertThat(block.transaction(1).get(0).bytes()).isEqualTo(block.tx(1).body().bytes());
    }

    /** A block transaction is a span: its records are not read, so a repeated key is the caller's to judge. */
    @Test
    void aUnifiedBlocksTransactionIsItsSpanAsEncoded() {
        String repeated = "84" + "a4" + BODY.substring(2) + "0218c8" + "a0" + "f6" + "f5";
        RawBlock block = RawBlock.of(bytes(block("83" + "81" + repeated + "f6" + "f6")));
        assertThat(hex(block.transaction(0).bytes())).isEqualTo(repeated);
        assertThatThrownBy(() -> block.tx(0)).isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void aUnifiedBlockHasItsCertificatesAndAuxData() {
        String withAux = "84" + BODY + "a0" + "a100a0" + "f5";
        String certificate = "82" + "41c0" + "5830" + "cc".repeat(48);
        String body = "83" + "9f" + withAux + "ff" + certificate + "4401020304";
        RawBlock block = RawBlock.of(bytes(block(body)));
        assertThat(block.txCount()).isEqualTo(1);
        assertThat(hex(block.tx(0).auxData().orElseThrow().bytes())).isEqualTo("a100a0");
        assertThat(hex(block.txBytes(0))).isEqualTo("83" + BODY + "a0" + "a100a0");
        RawLeiosCertificate leios = block.leiosCertificate().orElseThrow();
        assertThat(hex(leios.signers())).isEqualTo("c0");
        assertThat(hex(leios.signature())).isEqualTo("cc".repeat(48));
        assertThat(hex(block.perasCertificate().orElseThrow().bytes())).isEqualTo("4401020304");
        assertThat(block.bodyHash()).isEqualTo(Blake2bUtil.blake2bHash256(bytes(body)));
        assertThat(block.invalidTxIndexes()).isEmpty();

        assertThatThrownBy(() -> RawBlock.of(bytes(block("83" + "80" + "41c0" + "f6"))).leiosCertificate())
                .isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void aUnifiedBlockOfTheWrongShapeIsRejected() {
        List<String> bodies = List.of(
                "80", // not three items
                "82" + "80" + "f6",
                "c1" + "83" + "80" + "f6" + "f6", // tagged
                "83" + "a0" + "f6" + "f6", // transactions not an array
                "83" + "81" + "83" + BODY + "a0" + "f6" + "f6" + "f6", // a transaction of three items
                "83" + "81" + "84" + BODY + "a0" + "f5" + "f6" + "f6" + "f6", // the flag third, as submitted
                "83" + "81" + "84" + BODY + "a0" + "f6" + "00" + "f6" + "f6");
        for (String body : bodies) {
            assertThatThrownBy(() -> RawBlock.of(bytes(block(body)))).as(body)
                    .isInstanceOf(CborRuntimeException.class);
        }
        assertThatThrownBy(() -> RawBlock.of(bytes("8208" + "83" + RawHeaderTest.LEIOS_HEADER + "80" + "80")))
                .isInstanceOf(CborRuntimeException.class).hasMessageContaining("Dijkstra block has 2 items");
    }

    // ---- the transaction forms ----

    @Test
    void bothFormsOfFourItemsAreToldApartByTheirBoolean() {
        RawTx blockForm = RawTx.of(bytes(INVALID));
        RawTx legacy = RawTx.of(bytes("84" + BODY + "a0" + "f4" + "f6"));
        for (RawTx tx : List.of(blockForm, legacy, RawTx.lenient(bytes(INVALID)),
                RawTx.lenient(bytes("84" + BODY + "a0" + "f4" + "f6")))) {
            assertThat(tx.isValid()).isFalse();
            assertThat(tx.auxData()).isEmpty();
            assertThat(tx.txId()).isEqualTo(Blake2bUtil.blake2bHash256(bytes(BODY)));
        }
        String flagLastWithAux = "84" + BODY + "a0" + "a0" + "f5";
        assertThat(hex(RawTx.of(bytes(flagLastWithAux)).auxData().orElseThrow().bytes())).isEqualTo("a0");
        assertThat(hex(RawTx.lenient(bytes(flagLastWithAux)).auxData().orElseThrow().bytes())).isEqualTo("a0");
        assertThat(RawTx.of(bytes(flagLastWithAux)).isValid()).isTrue();
        for (String neither : List.of("84" + BODY + "a0" + "a0" + "a0", "84" + BODY + "a0" + "f6" + "f6")) {
            assertThatThrownBy(() -> RawTx.of(bytes(neither))).isInstanceOf(CborRuntimeException.class);
            assertThatThrownBy(() -> RawTx.lenient(bytes(neither))).isInstanceOf(CborRuntimeException.class);
        }
    }

    @Test
    void aWalkPicksTheFormsTableByArityAndFlag() {
        assertThat(firstEnter(VALID, Era.Dijkstra)).startsWith("enter block_transaction");
        assertThat(firstEnter("84" + BODY + "a0" + "f5" + "f6", Era.Dijkstra)).startsWith("enter transaction ");
        // the form without a flag has rows to Mary and again from Dijkstra
        assertThat(firstEnter("83" + BODY + "a0" + "f6", Era.Dijkstra)).startsWith("enter pre_alonzo_transaction");
        assertThat(firstEnter("83" + BODY + "a0" + "f6", Era.Conway)).startsWith("enter pre_alonzo_transaction");
        RawTx.walk(bytes(VALID), Era.Dijkstra, STRICT);
        RawTx.walk(bytes("83" + BODY + "a0" + "f6"), Era.Dijkstra, STRICT);
        RawTx.walk(bytes("84" + BODY + "a0" + "f5" + "f6"), Era.Dijkstra, STRICT);
        // outside its eras, a form is out of era
        assertThatThrownBy(() -> RawTx.walk(bytes(VALID), Era.Conway, STRICT))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> RawTx.walk(bytes("83" + BODY + "a0" + "f6"), Era.Conway, STRICT))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String firstEnter(String tx, Era era) {
        RecordingPolicy policy = new RecordingPolicy();
        RawTx.walk(bytes(tx), era, policy);
        return policy.events.get(0);
    }

    // ---- sub-transactions ----

    @Test
    void subTransactionsReadAsTransactionsAndWalkWithTheirOwnTable() {
        String body = "a4" + "00" + "81" + INPUT + "01" + "80" + "02" + "1864" + "17" + "81" + SUB_TX;
        RawTxBody top = dijkstra(body);
        List<RawTx> subs = top.subTransactions();
        assertThat(subs).hasSize(1);
        RawTx sub = subs.get(0);
        assertThat(sub.txId()).isEqualTo(Blake2bUtil.blake2bHash256(bytes(SUB_BODY)));
        assertThat(sub.isValid()).isTrue();
        assertThat(hex(sub.span().bytes())).isEqualTo(SUB_TX);
        assertThat(sub.body(Era.Dijkstra).fee()).isEmpty();
        assertThat(dijkstra(BODY).subTransactions()).isEmpty();
        // a sub-transaction body needs no fee
        RawTx.walk(bytes("83" + body + "a0" + "f6"), Era.Dijkstra, STRICT);
        TxBodyField.SUB_RECORD.walk(span(SUB_BODY), Era.Dijkstra, STRICT);

        assertThatThrownBy(() -> dijkstra(body.replace(SUB_TX, "84" + SUB_BODY + "a0" + "f6" + "f5"))
                .subTransactions()).isInstanceOf(CborRuntimeException.class);
    }

    /** A key only a top-level body may have is unknown in a sub-transaction body, as the ledger reads it. */
    @Test
    void aTopLevelOnlyKeyInASubTransactionBodyIsUnknown() {
        String withFee = "a3" + "00" + "81" + INPUT + "01" + "80" + "02" + "1864";
        String body = "a4" + "00" + "81" + INPUT + "01" + "80" + "02" + "1864" + "17" + "81" + "83" + withFee + "a0f6";
        RecordingPolicy policy = new RecordingPolicy();
        RawTx.walk(bytes("83" + body + "a0" + "f6"), Era.Dijkstra, policy);
        assertThat(policy.events).contains("entry sub_transaction_body null key=02 value=1864 occurrence=0");
        assertThat(policy.events).contains("entry transaction_body 2:COIN key=02 value=1864 occurrence=0");
        assertThatThrownBy(() -> RawTx.walk(bytes("83" + body + "a0" + "f6"), Era.Dijkstra, STRICT))
                .isInstanceOf(IllegalStateException.class);

        for (TxBodyField field : TxBodyField.values()) {
            boolean top = List.of(2L, 13L, 16L, 17L, 23L, 27L).contains(field.key());
            assertThat(field.topLevelOnly()).as(field.name()).isEqualTo(top);
            assertThat(TxBodyField.SUB_RECORD.fields().contains(field.spec())).as(field.name()).isEqualTo(!top);
        }
        assertThat(TxBodyField.SUB_RECORD.field(2, Era.Dijkstra)).isNull();
    }

    // ---- the body's new fields ----

    @Test
    void guardsAreKeyHashesOrCredentials() {
        RawTxBody keyHashes = dijkstra(BODY.replaceFirst("^a3", "a4") + "0e" + "d90102" + "82" + H28 + H28_B);
        List<RawCredential> guards = keyHashes.guards().orElseThrow();
        assertThat(guards).extracting(RawCredential::isScript).containsExactly(false, false);
        assertThat(hex(guards.get(1).hash())).isEqualTo("12".repeat(28));
        // key 14 is guards in Dijkstra, and required signers to Conway
        assertThat(keyHashes.requiredSigners()).isEmpty();
        assertThat(RawTx.of(bytes("83" + BODY.replaceFirst("^a3", "a4") + "0e" + "d90102" + "82" + H28 + H28_B
                + "a0f6")).body(Era.Conway).requiredSigners().orElseThrow()).hasSize(2);

        RawTxBody credentials = dijkstra(BODY.replaceFirst("^a3", "a4") + "0e" + "82" + "8200" + H28 + "8201" + H28_B);
        assertThat(credentials.guards().orElseThrow()).extracting(RawCredential::isScript).containsExactly(false,
                true);
        RawTx.walk(bytes("83" + BODY.replaceFirst("^a3", "a4") + "0e" + "82" + "8200" + H28 + "8201" + H28_B
                + "a0f6"), Era.Dijkstra, STRICT);
        assertThat(dijkstra(BODY).guards()).isEmpty();
    }

    @Test
    void requiredTopLevelGuardsDirectDepositsAndIntervalsRead() {
        String guards = "1818" + "a2" + "8201" + H28 + "f6" + "8200" + H28_B + "182a";
        String deposits = "1819" + "a1" + ra("e1") + "1903e8";
        String intervals = "181a" + "a3" + ra("e0") + "05" + ra("e1") + "82" + "01" + "f6" + ra("f0") + "82f6f6";
        String starting = "181b" + "a1" + ra("e1") + "82" + "f6" + "0a";
        String body = "a7" + BODY.substring(2) + guards + deposits + intervals + starting;
        RawTxBody read = dijkstra(body);

        List<RawTopLevelGuard> required = read.requiredTopLevelGuards();
        assertThat(required).hasSize(2);
        assertThat(required.get(0).credential().isScript()).isTrue();
        assertThat(required.get(0).data()).isNull();
        assertThat(hex(required.get(1).data().span().bytes())).isEqualTo("182a");

        RawWithdrawal deposit = read.directDeposits().get(0);
        assertThat(hex(deposit.rewardAccount())).isEqualTo("e1" + "33".repeat(28));
        assertThat(deposit.coin()).isEqualTo(BigInteger.valueOf(1000));

        List<RawAccountBalanceInterval> balances = read.accountBalanceIntervals();
        assertThat(balances).hasSize(3);
        assertThat(balances.get(0).exact()).isEqualTo(BigInteger.valueOf(5));
        assertThat(balances.get(0).lower()).isNull();
        assertThat(balances.get(1).exact()).isNull();
        assertThat(balances.get(1).lower()).isEqualTo(BigInteger.ONE);
        assertThat(balances.get(1).upper()).isNull();
        // both bounds null is read: rejecting it is policy (D5)
        assertThat(balances.get(2).lower()).isNull();
        assertThat(balances.get(2).upper()).isNull();
        RawAccountBalanceInterval startingBalance = read.startingAccountBalanceIntervals().get(0);
        assertThat(startingBalance.lower()).isNull();
        assertThat(startingBalance.upper()).isEqualTo(BigInteger.TEN);

        RawTx.walk(bytes("83" + body + "a0f6"), Era.Dijkstra, STRICT);
        for (String bad : List.of("181a" + "a1" + ra("e1") + "8101", "181a" + "a1" + ra("e1") + "40")) {
            RawTxBody broken = dijkstra("a4" + BODY.substring(2) + bad);
            assertThatThrownBy(broken::accountBalanceIntervals).isInstanceOf(CborRuntimeException.class);
        }
        assertThat(dijkstra(BODY).requiredTopLevelGuards()).isEmpty();
        assertThat(dijkstra(BODY).directDeposits()).isEmpty();
        assertThat(dijkstra(BODY).accountBalanceIntervals()).isEmpty();
        assertThat(dijkstra(BODY).startingAccountBalanceIntervals()).isEmpty();
    }

    // ---- scripts ----

    /** ADR 0003 I14: Plutus V4 is read in Dijkstra, and a Conway witness set's key 8 changes nothing. */
    @Test
    void plutusV4ScriptsAreReadFromDijkstraOnly() {
        String witnesses = "a2" + "07" + "81" + "4101" + "08" + "81" + "4102";
        RawTx tx = RawTx.of(bytes("83" + BODY + witnesses + "f6"));
        List<RawScript> dijkstra = tx.witnessSet(Era.Dijkstra).scripts();
        assertThat(dijkstra).extracting(RawScript::type).containsExactly(3, 4);
        assertThat(dijkstra.get(1).hash()).isEqualTo(Blake2bUtil.blake2bHash224(bytes("0402")));
        assertThat(tx.witnessSet(Era.Dijkstra).plutusV4Scripts().orElseThrow()).hasSize(1);
        assertThat(tx.witnessSet(Era.Conway).scripts()).extracting(RawScript::type).containsExactly(3);
        assertThat(tx.scripts()).extracting(RawScript::type).containsExactly(3);
        RawTx.walk(bytes("83" + BODY + witnesses + "f6"), Era.Dijkstra, STRICT);
        assertThatThrownBy(() -> RawTx.walk(bytes("83" + BODY + witnesses + "f6"), Era.Conway, STRICT))
                .isInstanceOf(IllegalStateException.class);

        String aux = "d90103" + "a2" + "04" + "81" + "4101" + "05" + "81" + "4102";
        RawTx withAux = RawTx.of(bytes("83" + BODY + "a0" + aux));
        assertThat(withAux.auxData(Era.Dijkstra).orElseThrow().scripts()).extracting(RawScript::type)
                .containsExactly(3, 4);
        assertThat(withAux.auxData(Era.Dijkstra).orElseThrow().plutusV4Scripts()).hasSize(1);
        assertThat(withAux.auxScripts()).extracting(RawScript::type).containsExactly(3);

        String output = "a3" + "00" + "581d61" + "44".repeat(28) + "01" + "00" + "03" + "d818" + "44" + "8204" + "4102";
        String withRef = "a3" + "00" + "81" + INPUT + "01" + "81" + output + "02" + "1864";
        assertThat(RawTx.of(bytes("83" + withRef + "a0f6")).outputs().get(0).scriptRef().orElseThrow().type())
                .isEqualTo(4);
        assertThatThrownBy(() -> new RawScript(5, span("4102"))).isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void redeemersAreAMapFromDijkstra() {
        assertThat(RawRedeemer.RECORD.field(4, Era.Conway).validIn(Era.Conway)).isTrue();
        assertThat(RawRedeemer.RECORD.field(4, Era.Dijkstra).validIn(Era.Dijkstra)).isFalse();
        assertThat(RawRedeemer.RECORD.field(5, Era.Dijkstra).validIn(Era.Dijkstra)).isTrue();
    }

    // ---- pool registration with a BLS key ----

    /** With an era from Dijkstra, the item after the VRF key hash decides, as the ledger peeks at it (StakePool.hs). */
    @Test
    void aDijkstraPoolRegistrationHasABlsSlotWhenItsFourthItemIsAnArrayOrNull() {
        String withKey = "8b" + "03" + H28 + H32 + BLS_KEY + POOL_REST;
        String withNull = "8b" + "03" + H28 + H32 + "f6" + POOL_REST;
        for (String cert : List.of(POOL, withKey, withNull)) {
            RawCertificate read = RawCertificate.of(span(cert), Era.Dijkstra);
            assertThat(read.kind()).isEqualTo(CertKind.POOL_REGISTRATION);
            assertThat(read.pledge()).isEqualTo(BigInteger.valueOf(1_000_000));
            assertThat(read.cost()).isEqualTo(BigInteger.valueOf(1000));
            assertThat(read.poolOwners()).hasSize(1);
            assertThat(read.poolMetadata()).isEmpty();
            CertKind.RECORD.walk(span(cert), Era.Dijkstra, STRICT);
            read.walk(Era.Dijkstra, STRICT);
        }
        assertThat(RawCertificate.of(span(POOL), Era.Dijkstra).blsKey()).isEmpty();
        assertThat(RawCertificate.of(span(withNull), Era.Dijkstra).blsKey()).isEmpty();
        RawBlsKey key = RawCertificate.of(span(withKey), Era.Dijkstra).blsKey().orElseThrow();
        assertThat(hex(key.publicKey())).isEqualTo("aa".repeat(96));
        assertThat(hex(key.proofOfPossession())).isEqualTo("bb".repeat(48));
        RecordingPolicy policy = new RecordingPolicy();
        CertKind.RECORD.walk(span(withKey), Era.Dijkstra, policy);
        assertThat(policy.events).anyMatch(event -> event.startsWith("enter bls_key"))
                .contains("check OPTIONAL_BLS_KEY " + BLS_KEY);
        // a fourth item of another kind is no slot: eleven items are one too many
        String eleven = "8b" + "03" + H28 + H32 + POOL_REST + "00";
        assertThatThrownBy(() -> RawCertificate.of(span(eleven), Era.Dijkstra))
                .isInstanceOf(CborRuntimeException.class).hasMessageContaining("10 items, found 11");
        assertThat(dijkstra("a4" + BODY.substring(2) + "04" + "81" + withKey).certificates().get(0).blsKey())
                .isPresent();
    }

    /** The ledger reads these records with RecD to version 11 and as definite or indefinite from 12 (Dijkstra). */
    @Test
    void aLenientReadInDijkstraAcceptsIndefiniteRecords() {
        assertThat(RawItems.indefiniteRecordsAllowed(null)).isFalse();
        assertThat(RawItems.indefiniteRecordsAllowed(Era.Conway)).isFalse();
        assertThat(RawItems.indefiniteRecordsAllowed(Era.Dijkstra)).isTrue();
        String value = "9f" + "01" + "a1" + H28 + "a1" + "41aa" + "01" + "ff";
        String body = "a3" + "00" + "81" + INPUT + "01" + "81" + "82" + ra("60") + value + "02" + "00";
        RawTx tx = RawTx.lenient(bytes("83" + body + "a0" + "f6"));
        assertThat(tx.body(Era.Dijkstra).outputs().get(0).amount().orElseThrow().coin()).isEqualTo(BigInteger.ONE);
        assertThatThrownBy(() -> tx.body(Era.Conway).outputs().get(0).amount())
                .isInstanceOf(CborRuntimeException.class).hasMessageContaining("definite-length");
        assertThatThrownBy(() -> RawValue.lenient(span(value))).isInstanceOf(CborRuntimeException.class);
        String id = "9f" + H32 + "00" + "ff";
        assertThatThrownBy(() -> RawGovActionId.lenient(span(id))).isInstanceOf(CborRuntimeException.class);
        assertThat(RawGovActionId.lenient(span(id), Era.Dijkstra).index()).isZero();
    }

    /** Read leniently in Dijkstra, as the models read it, a pool registration has its BLS key slot. */
    @Test
    void aLenientDijkstraPoolRegistrationHasItsBlsKey() {
        String withKey = "8b" + "03" + H28 + H32 + BLS_KEY + POOL_REST;
        RawCertificate read = RawCertificate.lenient(CertKind.POOL_REGISTRATION, span(withKey), Era.Dijkstra);
        assertThat(hex(read.blsKey().orElseThrow().publicKey())).isEqualTo("aa".repeat(96));
        assertThat(read.pledge()).isEqualTo(BigInteger.valueOf(1_000_000));
        assertThat(RawCertificate.lenient(CertKind.POOL_REGISTRATION, span(POOL), Era.Dijkstra).blsKey()).isEmpty();
        // without an era, by position as before: the BLS key is no slot, so the pledge position holds it
        assertThat(RawCertificate.lenient(CertKind.POOL_REGISTRATION, span(withKey), null).blsKey()).isEmpty();
        assertThat(RawCertificate.lenient(CertKind.POOL_REGISTRATION, span(withKey), null).pledge()).isNull();
    }

    /** With no era or an era before Dijkstra, a pool registration reads as it always has. */
    @Test
    void aPoolRegistrationWithoutDijkstraHasNoBlsSlot() {
        // a legacy registration with one trailing item, read by position as the models read it
        String trailing = "8b" + "03" + "40" + "40" + "1864" + "1832" + "d81e820103" + "40" + "80" + "80" + "f6" + "00";
        for (RawCertificate read : List.of(RawCertificate.lenient(CertKind.POOL_REGISTRATION, span(trailing)),
                RawCertificate.lenient(span(trailing)).orElseThrow())) {
            assertThat(read.pledge()).isEqualTo(BigInteger.valueOf(100));
            assertThat(read.cost()).isEqualTo(BigInteger.valueOf(50));
            assertThat(read.margin().getNumerator()).isEqualTo(BigInteger.ONE);
            assertThat(read.margin().getDenominator()).isEqualTo(BigInteger.valueOf(3));
            assertThat(read.blsKey()).isEmpty();
        }
        String withNull = "8b" + "03" + H28 + H32 + "f6" + POOL_REST;
        String withKey = "8b" + "03" + H28 + H32 + BLS_KEY + POOL_REST;
        for (Era era : new Era[]{null, Era.Conway}) {
            for (String cert : List.of(withNull, withKey)) {
                assertThatThrownBy(() -> RawCertificate.of(span(cert), era)).isInstanceOf(CborRuntimeException.class)
                        .hasMessageContaining("A member 3 has 10 items, found 11");
            }
        }
        assertThatThrownBy(() -> RawCertificate.of(span(withNull))).isInstanceOf(CborRuntimeException.class);
        assertThat(RawCertificate.of(span(POOL)).blsKey()).isEmpty();
        CertKind.RECORD.walk(span(POOL), Era.Conway, STRICT);
        assertThatThrownBy(() -> CertKind.RECORD.walk(span(withKey), Era.Conway, STRICT))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- a later era's keys, in an earlier era (ADR 0003 D4a) ----

    /** A key of Dijkstra is unknown in Conway, as Conway's ledger reads it: reported with no field, not entered. */
    @Test
    void dijkstraKeysAreUnknownInAConwayWalk() {
        String conwayBody = "a4" + "00" + "80" + "01" + "80" + "02" + "00" + "17" + "00";
        RawTx.walk(bytes("83" + conwayBody + "a0f6"), Era.Conway, FormatPolicy.ACCEPT_ALL);
        RecordingPolicy policy = new RecordingPolicy();
        TxBodyField.RECORD.walk(span(conwayBody), Era.Conway, policy);
        assertThat(policy.events).contains("entry transaction_body null key=17 value=00 occurrence=0")
                .doesNotContain("check NONEMPTY_SET 00");
        // arbitrary values at every key Dijkstra adds
        String everyKey = "a8" + "00" + "80" + "01" + "80" + "02" + "00" + "17" + "00" + "1818" + "6161" + "1819" + "f5"
                + "181a" + "40" + "181b" + "a0";
        RawTx.walk(bytes("83" + everyKey + "a0f6"), Era.Conway, FormatPolicy.ACCEPT_ALL);
        RecordingPolicy unknown = new RecordingPolicy();
        TxBodyField.RECORD.walk(span(everyKey), Era.Conway, unknown);
        assertThat(unknown.events.stream().filter(event -> event.startsWith("entry transaction_body null")))
                .hasSize(5);
        String witnesses = "a1" + "08" + "00";
        String aux = "d90103" + "a1" + "05" + "00";
        RawTx.walk(bytes("83" + BODY + witnesses + aux), Era.Conway, FormatPolicy.ACCEPT_ALL);
        RecordingPolicy others = new RecordingPolicy();
        RawTx.walk(bytes("83" + BODY + witnesses + aux), Era.Conway, others);
        assertThat(others.events).contains("entry transaction_witness_set null key=08 value=00 occurrence=0",
                "entry auxiliary_data_map null key=05 value=00 occurrence=0");
        // in Dijkstra, the same values are walked, and the walk cannot enter them
        assertThatThrownBy(() -> RawTx.walk(bytes("83" + conwayBody + "a0f6"), Era.Dijkstra, FormatPolicy.ACCEPT_ALL))
                .isInstanceOf(CborRuntimeException.class);
    }

    /** The rule is for records of fields: a map of like entries keeps its one row in every era, as before D4a. */
    @Test
    void aMapOfLikeEntriesKeepsItsRowInEarlierEras() {
        // the map form of redeemers in Alonzo: its value is walked, and one that is no array cannot be entered
        assertThatThrownBy(() -> WitnessSetField.RECORD.walk(span("a105a182000000"), Era.Alonzo,
                FormatPolicy.ACCEPT_ALL)).isInstanceOf(CborRuntimeException.class)
                .hasMessageContaining("A redeemer_value is an array");
        RecordingPolicy policy = new RecordingPolicy();
        WitnessSetField.RECORD.walk(span("a105a18200008200820000"), Era.Babbage, policy);
        assertThat(policy.events).contains("entry redeemers_map 0:REDEEMER_VALUE key=820000 value=8200820000 "
                + "occurrence=0", "check PLUTUS_DATA 00", "check EX_UNITS 820000");
    }

    /** The accessors of Dijkstra's fields read nothing in an earlier era; key 14 stays required signers in Conway. */
    @Test
    void dijkstraAccessorsReadNothingBeforeDijkstra() {
        String body = "a7" + BODY.substring(2) + "0e" + "d90102" + "82" + H28 + H28_B + "17" + "81" + SUB_TX
                + "1819" + "a1" + ra("e1") + "01" + "181a" + "a1" + ra("e1") + "01";
        String witnesses = "a1" + "08" + "81" + "4102";
        String aux = "d90103" + "a1" + "05" + "81" + "4102";
        RawTx tx = RawTx.of(bytes("83" + body + witnesses + aux));
        RawTxBody conway = tx.body(Era.Conway);
        assertThat(conway.guards()).isEmpty();
        assertThat(conway.requiredSigners().orElseThrow()).hasSize(2);
        assertThat(conway.subTransactions()).isEmpty();
        assertThat(conway.directDeposits()).isEmpty();
        assertThat(conway.accountBalanceIntervals()).isEmpty();
        assertThat(tx.witnessSet(Era.Conway).plutusV4Scripts()).isEmpty();
        assertThat(tx.witnessSet(Era.Conway).scripts()).isEmpty();
        assertThat(tx.auxData(Era.Conway).orElseThrow().plutusV4Scripts()).isEmpty();

        RawTxBody dijkstra = tx.body(Era.Dijkstra);
        assertThat(dijkstra.guards().orElseThrow()).hasSize(2);
        assertThat(dijkstra.requiredSigners()).isEmpty();
        assertThat(dijkstra.subTransactions()).hasSize(1);
        assertThat(dijkstra.directDeposits()).hasSize(1);
        assertThat(dijkstra.accountBalanceIntervals()).hasSize(1);
        assertThat(tx.witnessSet(Era.Dijkstra).scripts()).hasSize(1);
        assertThat(tx.auxData(Era.Dijkstra).orElseThrow().scripts()).hasSize(1);
    }

    /** Guards may be empty and have no tag 258, as the ledger's decodeGuards reads them (code over CDDL). */
    @Test
    void guardsMayBeEmpty() {
        assertThat(TxBodyField.GUARDS.spec().type()).isEqualTo(FieldType.SET);
        assertThat(dijkstra("a4" + BODY.substring(2) + "0e" + "80").guards().orElseThrow()).isEmpty();
        RawTx.walk(bytes("83" + "a4" + BODY.substring(2) + "0e" + "d90102" + "80" + "a0f6"), Era.Dijkstra, STRICT);
    }

    // ---- parameter updates ----

    @Test
    void parameterKeys34To56AreDijkstras() {
        for (int key = 34; key <= 56; key++) {
            FieldSpec row = ProtocolParamField.RECORD.field(key, Era.Dijkstra);
            assertThat(row.validIn(Era.Dijkstra)).as("key " + key).isTrue();
            assertThat(row.validIn(Era.Conway)).as("key " + key).isFalse();
        }
        assertThat(ProtocolParamField.RECORD.field(53, Era.Dijkstra).type()).isEqualTo(FieldType.OPTIONAL_UINT32);
        String update = "a4" + "1825" + "d81e820102" + "1826" + "f6" + "1835" + "f6" + "1830" + "1a000186a0";
        ProtocolParamField.RECORD.walk(span(update), Era.Dijkstra, STRICT);
        RawProtocolParamUpdate dijkstra = RawProtocolParamUpdate.of(span(update), Era.Dijkstra);
        assertThat(dijkstra.refScriptCostMultiplier().orElseThrow().getDenominator()).isEqualTo(BigInteger.TWO);
        assertThat(dijkstra.maxPledgeLeverage()).contains(Optional.empty());
        assertThat(dijkstra.perasBootstrapRound()).contains(OptionalLong.empty());
        assertThat(dijkstra.maxRefScriptSizePerEndorserBlock()).hasValue(100_000);
        assertThat(dijkstra.minPoolMargin()).isEmpty();
        assertThat(RawProtocolParamUpdate.of(span("a1" + "1835" + "07"), Era.Dijkstra).perasBootstrapRound())
                .contains(OptionalLong.of(7));
        RawProtocolParamUpdate conway = RawProtocolParamUpdate.of(span(update), Era.Conway);
        assertThat(conway.refScriptCostMultiplier()).isEmpty();
        assertThat(conway.maxPledgeLeverage()).isEmpty();
        assertThat(conway.perasBootstrapRound()).isEmpty();
        assertThat(conway.maxRefScriptSizePerEndorserBlock()).isEmpty();
        assertThatThrownBy(() -> ProtocolParamField.RECORD.walk(span(update), Era.Conway, STRICT))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- Leios ----

    @Test
    void endorserBlockReferencesKeepTheirWireOrderAndHashTheirBytes() {
        String references = "a2" + "5820" + "02".repeat(32) + "1864" + "5820" + "01".repeat(32) + "05";
        RawEbReferences read = RawEbReferences.of(bytes(references));
        assertThat(read.entries()).extracting(entry -> hex(entry.txHash())).containsExactly("02".repeat(32),
                "01".repeat(32));
        assertThat(read.entries()).extracting(RawEbReferences.Entry::size).containsExactly(100L, 5L);
        assertThat(read.hash()).isEqualTo(Blake2bUtil.blake2bHash256(bytes(references)));
        String indefinite = "bf" + references.substring(2) + "ff";
        assertThat(RawEbReferences.of(bytes(indefinite)).entries()).hasSize(2);
        assertThat(RawEbReferences.of(bytes(indefinite)).hash()).isNotEqualTo(read.hash());
        assertThatThrownBy(() -> RawEbReferences.of(bytes("80"))).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawEbReferences.of(bytes("a1" + "01" + "02")).entries())
                .isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void aLeiosCertificateIsTwoByteStrings() {
        RawLeiosCertificate certificate = RawLeiosCertificate.of(span("82" + "42c0c0" + "5830" + "dd".repeat(48)));
        assertThat(hex(certificate.signers())).isEqualTo("c0c0");
        assertThat(certificate.signature()).hasSize(48);
        for (String bad : List.of("81" + "40", "83" + "40" + "40" + "40", "82" + "00" + "40", "a0"))
            assertThatThrownBy(() -> RawLeiosCertificate.of(span(bad))).isInstanceOf(CborRuntimeException.class);
    }
}
