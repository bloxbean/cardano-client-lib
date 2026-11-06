package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.CostMdls;
import com.bloxbean.cardano.client.plutus.spec.CostModel;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.Language;
import com.bloxbean.cardano.client.plutus.spec.PlutusV4Script;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.Rational;
import com.bloxbean.cardano.client.spec.UnitInterval;
import com.bloxbean.cardano.client.transaction.raw.RawCredential;
import com.bloxbean.cardano.client.transaction.raw.RawProtocolParamUpdate;
import com.bloxbean.cardano.client.transaction.raw.RawTx;
import com.bloxbean.cardano.client.transaction.raw.RawTxBody;
import com.bloxbean.cardano.client.transaction.spec.cert.BlsKey;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.cert.GenesisKeyDelegation;
import com.bloxbean.cardano.client.transaction.spec.cert.MirPot;
import com.bloxbean.cardano.client.transaction.spec.cert.MoveInstataneous;
import com.bloxbean.cardano.client.transaction.spec.cert.PoolRegistration;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeCredential;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeDeregistration;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeRegistration;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.transaction.spec.governance.ProposalProcedure;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.ParameterChangeAction;
import com.bloxbean.cardano.client.transaction.spec.script.RequireGuard;
import com.bloxbean.cardano.client.transaction.util.TransactionUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.transaction.raw.RawCostModels;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.UpdateCommittee;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR 0004: the Dijkstra fields of the models, how Dijkstra writes them (D4), the errors for a field an earlier era has
 * no place for and for a value Dijkstra removed (W4), and reading them back (W5).
 */
class DijkstraModelTest {
    private static final String KEY_A = "11".repeat(28);
    private static final String KEY_B = "22".repeat(28);
    private static final String SCRIPT_C = "33".repeat(28);
    private static final String TX_ID = "44".repeat(32);

    private static final Credential KEY_A_CREDENTIAL = Credential.fromKey(KEY_A);
    private static final Credential SCRIPT_C_CREDENTIAL = Credential.fromScript(SCRIPT_C);

    // ---- W4: a field an earlier era has no place for is an error, not dropped ----

    static Stream<Object[]> dijkstraBodyFields() {
        return Stream.of(
                field("TransactionBody.guards", body -> body.setGuards(List.of(KEY_A_CREDENTIAL))),
                field("TransactionBody.subTransactions", body -> body.setSubTransactions(List.of(subTransaction()))),
                field("TransactionBody.requiredTopLevelGuards", body -> body.setRequiredTopLevelGuards(
                        List.of(new RequiredGuard(KEY_A_CREDENTIAL, null)))),
                field("TransactionBody.directDeposits", body -> body.setDirectDeposits(
                        List.of(new Withdrawal(rewardAddress(KEY_A_CREDENTIAL, false), BigInteger.ONE)))),
                field("TransactionBody.accountBalanceIntervals", body -> body.setAccountBalanceIntervals(
                        List.of(exact(rewardAddress(KEY_A_CREDENTIAL, false), 5)))),
                field("TransactionBody.startingAccountBalanceIntervals", body ->
                        body.setStartingAccountBalanceIntervals(List.of(exact(rewardAddress(KEY_A_CREDENTIAL, false),
                                5)))));
    }

    private static Object[] field(String name, Consumer<TransactionBody> setter) {
        return new Object[]{name, setter};
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dijkstraBodyFields")
    void aDijkstraBodyFieldCannotBeWrittenBeforeDijkstra(String name, Consumer<TransactionBody> setter)
            throws Exception {
        TransactionBody body = minimalBody();
        setter.accept(body);

        for (Era era : List.of(Era.Babbage, Era.Conway))
            assertThatThrownBy(() -> body.serialize(era)).as(name).isInstanceOf(CborSerializationException.class)
                    .hasMessageContaining(name);
        assertThatThrownBy(body::serialize).as(name).hasMessageContaining(name);
        assertThatThrownBy(() -> Transaction.builder().body(body).build().serialize()).as(name)
                .hasStackTraceContaining(name);
        assertThat(body.serialize(Era.Dijkstra)).as(name).isNotNull();
    }

    @Test
    void plutusV4ScriptsCannotBeWrittenBeforeDijkstra() throws Exception {
        TransactionWitnessSet witnessSet = TransactionWitnessSet.builder().plutusV4Scripts(List.of(v4Script())).build();
        AuxiliaryData auxiliaryData = AuxiliaryData.builder().plutusV4Scripts(List.of(v4Script())).build();

        assertThatThrownBy(() -> witnessSet.serialize(Era.Conway))
                .hasMessageContaining("TransactionWitnessSet.plutusV4Scripts");
        assertThatThrownBy(() -> auxiliaryData.serialize(Era.Conway))
                .hasMessageContaining("AuxiliaryData.plutusV4Scripts");
        assertThatThrownBy(() -> auxiliaryData.getAuxiliaryDataHash(Era.Conway))
                .hasStackTraceContaining("AuxiliaryData.plutusV4Scripts");
        assertThat(hex(witnessSet.serialize(Era.Dijkstra))).isEqualTo("a108d901028144" + "01020304");
        assertThat(hex(auxiliaryData.serialize(Era.Dijkstra))).isEqualTo("d90103a1058144" + "01020304");
    }

    @Test
    void aBlsKeyCannotBeWrittenBeforeDijkstra() {
        PoolRegistration pool = pool(blsKey());

        assertThatThrownBy(() -> pool.serialize(Era.Conway)).hasMessageContaining("PoolRegistration.blsKey");
    }

    static Stream<Object[]> dijkstraParameters() {
        return Stream.of(
                parameter(34, "maxRefScriptSizePerBlock", b -> b.maxRefScriptSizePerBlock(1L)),
                parameter(35, "maxRefScriptSizePerTx", b -> b.maxRefScriptSizePerTx(1L)),
                parameter(36, "refScriptCostStride", b -> b.refScriptCostStride(1L)),
                parameter(37, "refScriptCostMultiplier", b -> b.refScriptCostMultiplier(rational(3, 2))),
                parameter(38, "maxPledgeLeverage", b -> b.maxPledgeLeverage(Optional.of(rational(3, 2)))),
                parameter(39, "minPoolMargin", b -> b.minPoolMargin(unit(1, 2))),
                parameter(40, "leiosAnnouncementPeriodLength", b -> b.leiosAnnouncementPeriodLength(1L)),
                parameter(41, "leiosVotePeriodLength", b -> b.leiosVotePeriodLength(1L)),
                parameter(42, "leiosDiffusionPeriodLength", b -> b.leiosDiffusionPeriodLength(1L)),
                parameter(43, "leiosCommitteeSize", b -> b.leiosCommitteeSize(1)),
                parameter(44, "leiosQuorumStakeThreshold", b -> b.leiosQuorumStakeThreshold(unit(1, 2))),
                parameter(45, "maxEndorserBlockReferencesSize", b -> b.maxEndorserBlockReferencesSize(1L)),
                parameter(46, "maxEndorserBlockTxsSize", b -> b.maxEndorserBlockTxsSize(1L)),
                parameter(47, "maxEndorserBlockExUnits", b -> b.maxEndorserBlockExUnits(
                        new ExUnits(BigInteger.ONE, BigInteger.TWO))),
                parameter(48, "maxRefScriptSizePerEndorserBlock", b -> b.maxRefScriptSizePerEndorserBlock(1L)),
                parameter(49, "perasMinCandidateBlockAge", b -> b.perasMinCandidateBlockAge(1L)),
                parameter(50, "perasHealingFactor", b -> b.perasHealingFactor(rational(3, 2))),
                parameter(51, "perasCertBoost", b -> b.perasCertBoost(1)),
                parameter(52, "perasTargetCommitteeSize", b -> b.perasTargetCommitteeSize(1)),
                parameter(53, "perasBootstrapRound", b -> b.perasBootstrapRound(Optional.of(1L))),
                parameter(54, "perasQuorumThresholdSafetyMargin", b -> b.perasQuorumThresholdSafetyMargin(
                        unit(1, 2))),
                parameter(55, "refInputsCostPerMultiAssetPolicy", b -> b.refInputsCostPerMultiAssetPolicy(
                        BigInteger.ONE)),
                parameter(56, "refInputsCostPerDatumByte", b -> b.refInputsCostPerDatumByte(BigInteger.ONE)));
    }

    private static Object[] parameter(int key, String name,
                                      Consumer<ProtocolParamUpdate.ProtocolParamUpdateBuilder> setter) {
        ProtocolParamUpdate.ProtocolParamUpdateBuilder builder = ProtocolParamUpdate.builder();
        setter.accept(builder);
        return new Object[]{key, name, builder.build()};
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("dijkstraParameters")
    void aDijkstraParameterIsWrittenOnlyInDijkstra(int key, String name, ProtocolParamUpdate update) throws Exception {
        String field = "ProtocolParamUpdate." + name;
        ProposalProcedure proposal = proposal(update);
        TransactionBody body = minimalBody();
        body.setProposalProcedures(List.of(proposal));

        assertThatThrownBy(update::serialize).hasStackTraceContaining(field);
        assertThatThrownBy(() -> update.serialize(Era.Conway)).hasStackTraceContaining(field);
        assertThatThrownBy(proposal::serialize).hasStackTraceContaining(field);
        assertThatThrownBy(() -> body.serialize(Era.Conway)).hasStackTraceContaining(field);

        Map written = (Map) update.serialize(Era.Dijkstra);
        assertThat(written.getKeys()).containsExactly(new UnsignedInteger(key));
        DataItem action = ((Array) proposal.serialize(Era.Dijkstra)).getDataItems().get(2);
        assertThat(((Array) action).getDataItems().get(2)).isEqualTo(written);
        Array proposals = (Array) body.serialize(Era.Dijkstra).get(new UnsignedInteger(20));
        assertThat(((Array) proposals.getDataItems().get(0)).getDataItems().get(2)).isEqualTo(action);
    }

    // ---- W4: a value Dijkstra removed is an error in Dijkstra ----

    static Stream<Object[]> removedCertificates() {
        StakeCredential credential = StakeCredential.fromKeyHash(HexUtil.decodeHexString(KEY_A));
        return Stream.of(
                new Object[]{"StakeRegistration certificate (tag 0)", new StakeRegistration(credential)},
                new Object[]{"StakeDeregistration certificate (tag 1)", new StakeDeregistration(credential)},
                new Object[]{"GenesisKeyDelegation certificate (tag 5)", new GenesisKeyDelegation(
                        HexUtil.decodeHexString(KEY_A), HexUtil.decodeHexString(KEY_B),
                        HexUtil.decodeHexString(TX_ID))},
                new Object[]{"MoveInstataneous certificate (tag 6)", new MoveInstataneous(MirPot.RESERVES,
                        BigInteger.ONE, null)});
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("removedCertificates")
    void aCertificateDijkstraRemovedCannotBeWrittenInDijkstra(String name, Certificate certificate) throws Exception {
        TransactionBody body = minimalBody();
        body.setCerts(List.of(certificate));

        assertThatThrownBy(() -> certificate.serialize(Era.Dijkstra)).hasMessageContaining(name);
        assertThatThrownBy(() -> body.serialize(Era.Dijkstra)).hasMessageContaining(name);
        assertThat(certificate.serialize(Era.Conway)).isEqualTo(certificate.serialize());
    }

    @Test
    void aTransactionMarkedInvalidCannotBeWrittenInDijkstra() throws Exception {
        Transaction transaction = Transaction.builder().body(minimalBody()).isValid(false).build();

        assertThatThrownBy(() -> transaction.serialize(Era.Dijkstra))
                .hasStackTraceContaining("A transaction marked invalid");
        assertThat(transaction.serialize(Era.Conway)).isEqualTo(transaction.serialize());
    }

    // ---- values written as given in any era (ADR 0004 D1) ----

    @Test
    void verbatimValuesAreWrittenInConwayAsBefore() throws Exception {
        RequireGuard guard = new RequireGuard(SCRIPT_C_CREDENTIAL);
        Redeemer redeemer = Redeemer.builder().tag(RedeemerTag.Guarding).index(BigInteger.ZERO)
                .data(BigIntPlutusData.of(1)).exUnits(new ExUnits(BigInteger.ONE, BigInteger.ONE)).build();
        TransactionWitnessSet witnessSet = TransactionWitnessSet.builder().nativeScripts(List.of(guard))
                .redeemers(List.of(redeemer)).build();
        TransactionOutput output = TransactionOutput.builder().address(rewardAddress(KEY_A_CREDENTIAL, false))
                .value(Value.builder().coin(BigInteger.ONE).build()).scriptRef(v4Script().scriptRefBytes()).build();
        CostMdls costModels = new CostMdls();
        costModels.add(new CostModel(Language.PLUTUS_V4, new long[]{1, 2}));

        assertThat(hex(guard.serializeAsDataItem())).isEqualTo("8206" + "8201581c" + SCRIPT_C);
        assertThat(HexUtil.encodeHexString(guard.getScriptHash()))
                .isEqualTo(HexUtil.encodeHexString(Blake2bUtil.blake2bHash224(
                        HexUtil.decodeHexString("00" + "8206" + "8201581c" + SCRIPT_C))));
        assertThat(hex(witnessSet.serialize(Era.Conway))).isEqualTo("a2" + "01d90102818206" + "8201581c" + SCRIPT_C
                + "05a1" + "820600" + "82" + "01" + "820101");
        assertThat(hex(((Map) output.serialize()).get(new UnsignedInteger(3)))).isEqualTo("d81847" + "820444"
                + "01020304");
        assertThat(hex(costModels.serialize())).isEqualTo("a103820102");
        assertThat(HexUtil.encodeHexString(costModels.getLanguageViewEncoding())).isEqualTo("a103820102");
        assertThat(HexUtil.encodeHexString(v4Script().getScriptHash())).isEqualTo(HexUtil.encodeHexString(
                Blake2bUtil.blake2bHash224(HexUtil.decodeHexString("04"
                        + "01020304"))));
    }

    // ---- W1: a Conway parameter update with a language-3 cost model, read without an era ----

    @Test
    void aLanguageThreeCostModelReadWithoutAnEraStaysUnknown() throws Exception {
        byte[] update = HexUtil.decodeHexString("a1" + "12" + "a2" + "02820506" + "03820102");

        ProtocolParamUpdate conway = ProtocolParamUpdate.deserialize(CborSerializationUtil.deserialize(update));
        assertThat(conway.getCostModels().get(Language.PLUTUS_V4)).isNull();
        assertThat(conway.getCostModels().getUnknownCostModels()).containsOnlyKeys(3);
        assertThat(CborSerializationUtil.serialize(conway.serialize())).isEqualTo(update);
        assertThat(HexUtil.encodeHexString(conway.getCostModels().getLanguageViewEncoding())).isEqualTo("a102820506");

        ProtocolParamUpdate dijkstra = ProtocolParamUpdate.from(
                RawProtocolParamUpdate.lenient(CborSpan.of(update), Era.Dijkstra));
        assertThat(dijkstra.getCostModels().get(Language.PLUTUS_V4).getCosts()).containsExactly(1, 2);
        assertThat(dijkstra.getCostModels().getUnknownCostModels()).isEmpty();
        assertThat(CborSerializationUtil.serialize(dijkstra.serialize(Era.Dijkstra))).isEqualTo(update);
        assertThat(HexUtil.encodeHexString(dijkstra.getCostModels().getLanguageViewEncoding()))
                .isEqualTo("a2" + "02820506" + "03820102");
    }

    // ---- D4: how Dijkstra is written ----

    @Test
    void guardsAreCredentialsInASetInListOrder() throws Exception {
        TransactionBody body = minimalBody();
        body.setGuards(List.of(KEY_A_CREDENTIAL, SCRIPT_C_CREDENTIAL));

        assertThat(hex(body.serialize(Era.Dijkstra).get(new UnsignedInteger(14))))
                .isEqualTo("d9010282" + "8200581c" + KEY_A + "8201581c" + SCRIPT_C);
    }

    @Test
    void requiredSignersInDijkstraAreTheConwayBytes() throws Exception {
        TransactionBody body = minimalBody();
        body.setRequiredSigners(List.of(HexUtil.decodeHexString(KEY_A)));

        assertThat(hex(body.serialize(Era.Dijkstra))).isEqualTo(hex(body.serialize(Era.Conway)));
        assertThat(hex(body.serialize(Era.Dijkstra).get(new UnsignedInteger(14))))
                .isEqualTo("d9010281581c" + KEY_A);

        body.setGuards(List.of(KEY_A_CREDENTIAL));
        assertThatThrownBy(() -> body.serialize(Era.Dijkstra))
                .hasMessageContaining("requiredSigners and guards both fill key 14");
    }

    @Test
    void subTransactionsAreAPlainArrayOfThreeItemsWithoutAFee() throws Exception {
        TransactionBody body = minimalBody();
        SubTransaction first = subTransaction();
        SubTransaction second = subTransaction();
        second.getBody().setTtl(9);
        body.setSubTransactions(List.of(first, second));

        String sub = "83" + "a2" + "00d9010280" + "0180" + "a0" + "f6";
        String subWithTtl = "83" + "a3" + "00d9010280" + "0180" + "0309" + "a0" + "f6";
        assertThat(hex(body.serialize(Era.Dijkstra).get(new UnsignedInteger(23))))
                .isEqualTo("82" + sub + subWithTtl);
    }

    static Stream<Object[]> topLevelOnlyFields() {
        return Stream.of(
                field("fee", body -> body.setFee(BigInteger.ONE)),
                field("collateral", body -> body.setCollateral(List.of(new TransactionInput(TX_ID, 0)))),
                field("collateralReturn", body -> body.setCollateralReturn(TransactionOutput.builder()
                        .address(rewardAddress(KEY_A_CREDENTIAL, false)).value(Value.builder().coin(BigInteger.ONE)
                                .build()).build())),
                field("totalCollateral", body -> body.setTotalCollateral(BigInteger.ONE)),
                field("subTransactions", body -> body.setSubTransactions(List.of(subTransaction()))),
                field("startingAccountBalanceIntervals", body -> body.setStartingAccountBalanceIntervals(
                        List.of(exact(rewardAddress(KEY_A_CREDENTIAL, false), 1)))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("topLevelOnlyFields")
    void aSubTransactionBodyHasNoTopLevelOnlyField(String name, Consumer<TransactionBody> setter) {
        SubTransaction sub = subTransaction();
        setter.accept(sub.getBody());

        assertThatThrownBy(() -> sub.serialize(Era.Dijkstra)).hasMessageContaining("A sub-transaction body has no "
                + name);
    }

    @Test
    void requiredTopLevelGuardsAreInHaskellsCredentialOrder() throws Exception {
        TransactionBody body = minimalBody();
        body.setRequiredTopLevelGuards(List.of(new RequiredGuard(KEY_A_CREDENTIAL, BigIntPlutusData.of(42)),
                new RequiredGuard(SCRIPT_C_CREDENTIAL, null)));

        // a script before a key, though a key's tag (0) is the lower
        assertThat(hex(body.serialize(Era.Dijkstra).get(new UnsignedInteger(24))))
                .isEqualTo("a2" + "8201581c" + SCRIPT_C + "f6" + "8200581c" + KEY_A + "182a");
    }

    @Test
    void mapsKeyedByRewardAccountAreInHaskellsOrder() throws Exception {
        String mainnetKey = rewardAddress(KEY_A_CREDENTIAL, true);
        String testnetKey = rewardAddress(KEY_A_CREDENTIAL, false);
        String testnetScript = rewardAddress(SCRIPT_C_CREDENTIAL, false);
        TransactionBody body = minimalBody();
        body.setDirectDeposits(List.of(new Withdrawal(mainnetKey, BigInteger.ONE),
                new Withdrawal(testnetKey, BigInteger.TWO), new Withdrawal(testnetScript, BigInteger.TEN)));
        body.setAccountBalanceIntervals(List.of(exact(mainnetKey, 1), bounds(testnetKey, 2L, null),
                bounds(testnetScript, null, 3L)));
        body.setStartingAccountBalanceIntervals(List.of(bounds(mainnetKey, 4L, 5L), exact(testnetScript, 6)));

        // testnet before mainnet, then a script before a key: not the order of the encoded accounts (e0, e1, f0)
        Map map = body.serialize(Era.Dijkstra);
        assertThat(hex(map.get(new UnsignedInteger(25)))).isEqualTo("a3" + "581df0" + SCRIPT_C + "0a"
                + "581de0" + KEY_A + "02" + "581de1" + KEY_A + "01");
        assertThat(hex(map.get(new UnsignedInteger(26)))).isEqualTo("a3" + "581df0" + SCRIPT_C + "82f603"
                + "581de0" + KEY_A + "8202f6" + "581de1" + KEY_A + "01");
        assertThat(hex(map.get(new UnsignedInteger(27)))).isEqualTo("a2" + "581df0" + SCRIPT_C + "06"
                + "581de1" + KEY_A + "820405");
    }

    @Test
    void anIntervalIsExactOrHasABound() {
        TransactionBody body = minimalBody();
        String account = rewardAddress(KEY_A_CREDENTIAL, false);

        body.setAccountBalanceIntervals(List.of(bounds(account, null, null)));
        assertThatThrownBy(() -> body.serialize(Era.Dijkstra))
                .hasMessageContaining("exact, inclusiveLower and exclusiveUpper are all null");
        body.setAccountBalanceIntervals(List.of(new AccountBalanceInterval(account, BigInteger.ONE, BigInteger.ONE,
                null)));
        assertThatThrownBy(() -> body.serialize(Era.Dijkstra)).hasMessageContaining("exact and a bound are both set");
    }

    @Test
    void aPoolRegistrationHasTenFieldsWithABlsKeyAndNineWithout() throws Exception {
        Array withKey = pool(blsKey()).serialize(Era.Dijkstra);
        Array without = pool(null).serialize(Era.Dijkstra);

        assertThat(withKey.getDataItems()).hasSize(11);
        assertThat(hex(withKey.getDataItems().get(3))).isEqualTo("825860" + "aa".repeat(96) + "5830" + "bb".repeat(48));
        assertThat(without.getDataItems()).hasSize(10);
        assertThat(without).isEqualTo(pool(null).serialize(Era.Conway));
        withKey.getDataItems().remove(3);
        assertThat(withKey).isEqualTo(without);
    }

    @Test
    void parameterFiftyThreeAndThirtyEightHaveThreeStates() throws Exception {
        assertThat(hex(ProtocolParamUpdate.builder().build().serialize(Era.Dijkstra))).isEqualTo("a0");
        assertThat(hex(ProtocolParamUpdate.builder().perasBootstrapRound(Optional.empty()).build()
                .serialize(Era.Dijkstra))).isEqualTo("a11835f6");
        assertThat(hex(ProtocolParamUpdate.builder().perasBootstrapRound(Optional.of(7L)).build()
                .serialize(Era.Dijkstra))).isEqualTo("a1183507");
        assertThat(hex(ProtocolParamUpdate.builder().maxPledgeLeverage(Optional.empty()).build()
                .serialize(Era.Dijkstra))).isEqualTo("a11826f6");
        assertThat(hex(ProtocolParamUpdate.builder().maxPledgeLeverage(Optional.of(rational(3, 2))).build()
                .serialize(Era.Dijkstra))).isEqualTo("a11826d81e820302");

        for (String update : List.of("a11835f6", "a1183507", "a11826f6", "a11826d81e820302", "a0")) {
            ProtocolParamUpdate read = ProtocolParamUpdate.from(RawProtocolParamUpdate.lenient(
                    CborSpan.of(HexUtil.decodeHexString(update)), Era.Dijkstra));
            assertThat(hex(read.serialize(Era.Dijkstra))).isEqualTo(update);
        }
        ProtocolParamUpdate absent = ProtocolParamUpdate.from(RawProtocolParamUpdate.lenient(
                CborSpan.of(HexUtil.decodeHexString("a0")), Era.Dijkstra));
        assertThat(absent.getPerasBootstrapRound()).isNull();
        assertThat(absent.getMaxPledgeLeverage()).isNull();
    }

    @Test
    void aDijkstraTransactionHasThreeItemsAndItsEraDrivesSerialize() throws Exception {
        Transaction transaction = Transaction.builder().body(minimalBody()).build();
        byte[] conway = transaction.serialize();

        assertThat(conway[0]).isEqualTo((byte) 0x84);
        assertThat(transaction.serialize(Era.Conway)).isEqualTo(conway);
        assertThat(HexUtil.encodeHexString(transaction.serialize(Era.Dijkstra)))
                .isEqualTo("83" + hex(minimalBody().serialize(Era.Dijkstra)) + "a0f6");
        transaction.setEra(Era.Dijkstra);
        assertThat(transaction.serialize()).isEqualTo(transaction.serialize(Era.Dijkstra));
    }

    // ---- W5: written in Dijkstra and read back with the reader in Dijkstra, the same values ----

    @Test
    void everyNewFieldReadsBackWithTheSameValuesAndTheReadersId() throws Exception {
        Transaction transaction = everyNewField();
        byte[] bytes = transaction.serialize();

        Transaction read = Transaction.deserialize(bytes, Era.Dijkstra);
        assertThat(read.getEra()).isEqualTo(Era.Dijkstra);
        TransactionBody body = read.getBody();
        TransactionBody original = transaction.getBody();
        assertThat(body.getGuards()).isEqualTo(original.getGuards());
        assertThat(body.getRequiredTopLevelGuards()).isEqualTo(original.getRequiredTopLevelGuards());
        assertThat(body.getDirectDeposits()).isEqualTo(original.getDirectDeposits());
        assertThat(body.getAccountBalanceIntervals()).isEqualTo(original.getAccountBalanceIntervals());
        assertThat(body.getStartingAccountBalanceIntervals()).isEqualTo(original.getStartingAccountBalanceIntervals());
        assertThat(body.getSubTransactions()).hasSize(1);
        SubTransaction sub = body.getSubTransactions().get(0);
        assertThat(sub.getBody().getGuards()).isEqualTo(List.of(SCRIPT_C_CREDENTIAL));
        assertThat(sub.getWitnessSet().getPlutusV4Scripts()).isEqualTo(List.of(v4Script()));
        assertThat(sub.getWitnessSet().getNativeScripts()).isEqualTo(List.of(new RequireGuard(KEY_A_CREDENTIAL)));
        assertThat(sub.getAuxiliaryData().getPlutusV4Scripts()).isEqualTo(List.of(v4Script()));
        assertThat(read.getWitnessSet().getPlutusV4Scripts()).isEqualTo(List.of(v4Script()));
        assertThat(read.getWitnessSet().getRedeemers()).extracting(Redeemer::getTag)
                .containsExactly(RedeemerTag.Guarding);
        assertThat(read.getAuxiliaryData().getPlutusV4Scripts()).isEqualTo(List.of(v4Script()));
        assertThat(((PoolRegistration) body.getCerts().get(0)).getBlsKey()).isEqualTo(blsKey());
        ProtocolParamUpdate update = ((ParameterChangeAction) body.getProposalProcedures().get(0).getGovAction())
                .getProtocolParamUpdate();
        assertThat(update).isEqualTo(everyParameter());
        assertThat(read.serialize()).isEqualTo(bytes);

        RawTx raw = RawTx.of(bytes);
        RawTxBody rawBody = raw.body(Era.Dijkstra);
        assertThat(rawBody.guards().orElseThrow()).extracting(RawCredential::isScript).containsExactly(false, true);
        assertThat(rawBody.subTransactions()).hasSize(1);
        assertThat(rawBody.subTransactions().get(0).body(Era.Dijkstra).fee()).isEmpty();
        assertThat(rawBody.directDeposits()).hasSize(2);
        assertThat(raw.witnessSet(Era.Dijkstra).plutusV4Scripts().orElseThrow()).hasSize(1);
        assertThat(HexUtil.encodeHexString(raw.txId())).isEqualTo(TransactionUtil.getTxHash(transaction))
                .isEqualTo(TransactionUtil.getTxHash(read));
        assertThat(HexUtil.encodeHexString(rawBody.subTransactions().get(0).txId())).isEqualTo(HexUtil
                .encodeHexString(Blake2bUtil.blake2bHash256(CborSerializationUtil
                        .serialize(sub.serialize(Era.Dijkstra).getDataItems().get(0)))));
    }

    @Test
    void conwayReadingIsUnchanged() throws Exception {
        Transaction transaction = everyNewField();
        // Conway reads key 14 as key hashes and a pool registration as nine fields
        transaction.getBody().setGuards(null);
        transaction.getBody().setCerts(new ArrayList<>());
        Transaction conway = Transaction.deserialize(transaction.serialize());

        assertThat(conway.getEra()).isNull();
        assertThat(conway.getBody().getSubTransactions()).isNull();
        assertThat(conway.getBody().getDirectDeposits()).isNull();
        assertThat(conway.getWitnessSet().getPlutusV4Scripts()).isNull();
        assertThat(conway.getAuxiliaryData().getPlutusV4Scripts()).isNull();
        assertThat(((ParameterChangeAction) conway.getBody().getProposalProcedures().get(0).getGovAction())
                .getProtocolParamUpdate()).isEqualTo(ProtocolParamUpdate.builder().build());
    }

    @Test
    void aSubTransactionCannotBeWrittenBeforeDijkstra() {
        assertThatThrownBy(() -> new SubTransaction().serialize(Era.Conway)).hasMessageContaining("SubTransaction");
    }

    @Test
    void aKnownCostModelReplacesAnUnknownOneOfItsLanguage() throws Exception {
        CostMdls costModels = CostMdls.deserialize(CborSerializationUtil.deserialize(HexUtil.decodeHexString(
                "a1038101")));
        assertThat(costModels.getUnknownCostModels()).containsOnlyKeys(3);
        costModels.add(new CostModel(Language.PLUTUS_V4, new long[]{2}));

        assertThat(costModels.getUnknownCostModels()).isEmpty();
        assertThat(hex(costModels.serialize())).isEqualTo("a1038102");
        assertThat(HexUtil.encodeHexString(costModels.getLanguageViewEncoding())).isEqualTo("a1038102");
        // without an era, as without one: language 3 stays unknown
        assertThat(CostMdls.from(RawCostModels.lenient(CborSpan.of(HexUtil.decodeHexString("a1038101"))), null)
                .getUnknownCostModels()).containsOnlyKeys(3);
    }

    /** Before Dijkstra a body writes its proposals as before ADR 0004: in the configured era. */
    @Test
    void aBabbageBodyWritesItsProposalsAsBefore() throws Exception {
        TransactionBody body = minimalBody();
        body.setProposalProcedures(List.of(ProposalProcedure.builder().deposit(BigInteger.TEN)
                .rewardAccount(rewardAddress(KEY_A_CREDENTIAL, false))
                .govAction(UpdateCommittee.builder().quorumThreshold(unit(1, 2)).build())
                .anchor(new Anchor("https://a", HexUtil.decodeHexString(TX_ID))).build()));

        // e59e9356's bytes: the members to remove are a set with tag 258, though the body's sets have none
        assertThat(hex(body.serialize(Era.Babbage))).isEqualTo("a40081825820" + TX_ID + "00018002001481840a581de0"
                + KEY_A + "8504f6d9010280a0d81e820102826968747470733a2f2f615820" + TX_ID);
    }

    /** The ledger writes Dijkstra aux data as the tag-259 map, metadata alone too; earlier eras as before. */
    @Test
    void dijkstraAuxDataIsAlwaysTheTag259Map() throws Exception {
        CBORMetadata metadata = new CBORMetadata();
        metadata.put(BigInteger.ZERO, BigInteger.ONE);
        AuxiliaryData auxiliaryData = AuxiliaryData.builder().metadata(metadata).build();

        assertThat(hex(auxiliaryData.serialize(Era.Dijkstra))).isEqualTo("d90103a100a10001");
        assertThat(hex(auxiliaryData.serialize(Era.Conway))).isEqualTo("a10001");
        assertThat(auxiliaryData.getAuxiliaryDataHash(Era.Dijkstra))
                .isEqualTo(Blake2bUtil.blake2bHash256(HexUtil.decodeHexString("d90103a100a10001")));
        // empty metadata is left out in Dijkstra, as the ledger leaves it out
        AuxiliaryData empty = AuxiliaryData.builder().metadata(new CBORMetadata()).build();
        assertThat(hex(empty.serialize(Era.Dijkstra))).isEqualTo("d90103a0");
        assertThat(hex(empty.serialize(Era.Conway))).isEqualTo("a0");
    }

    /** Keys 38 and 53 in JSON: absent is left out, nil is null, a value is the value. */
    @Test
    void aNilParameterIsNullInJson() throws Exception {
        ProtocolParamUpdate update = ProtocolParamUpdate.builder().perasBootstrapRound(Optional.empty())
                .maxPledgeLeverage(Optional.of(rational(3, 2))).build();
        JsonNode json = new ObjectMapper().readTree(JsonUtil.getPrettyJson(update));

        assertThat(json.has("perasBootstrapRound")).isTrue();
        assertThat(json.get("perasBootstrapRound").isNull()).isTrue();
        assertThat(json.get("maxPledgeLeverage").get("numerator").asInt()).isEqualTo(3);
        assertThat(json.has("perasCertBoost")).isFalse();
        assertThat(new ObjectMapper().readTree(JsonUtil.getPrettyJson(ProtocolParamUpdate.builder()
                .perasBootstrapRound(Optional.of(7L)).build())).get("perasBootstrapRound").asLong()).isEqualTo(7);
    }

    // ---- version 12: records the ledger reads as definite or indefinite ----

    private static final String H28 = "581c" + KEY_A;
    private static final String H32 = "5820" + TX_ID;
    private static final String INDEFINITE_VOTES = "a1" + "8200" + H28 + "a1" + "9f" + H32 + "00" + "ff"
            + "9f" + "01" + "9f" + "6a" + HexUtil.encodeHexString("https://ab".getBytes()) + H32 + "ff" + "ff";

    @Test
    void aDijkstraTransactionMayHaveIndefiniteRecords() throws Exception {
        String value = "9f" + "01" + "a1" + H28 + "a1" + "41aa" + "01" + "ff";
        String body = "a4" + "00" + "81" + "82" + H32 + "00" + "01" + "81" + "82" + "581d60" + KEY_A + value
                + "02" + "00" + "13" + INDEFINITE_VOTES;
        String witnessSet = "a1" + "05" + "a1" + "820000" + "9f" + "00" + "9f0102ff" + "ff";
        byte[] tx = HexUtil.decodeHexString("83" + body + witnessSet + "f6");

        Transaction read = Transaction.deserialize(tx, Era.Dijkstra);
        assertThat(read.getBody().getOutputs().get(0).getValue().getCoin()).isEqualTo(BigInteger.ONE);
        var procedure = read.getBody().getVotingProcedures().getVoting().values().iterator().next().values()
                .iterator().next();
        assertThat(procedure.getAnchor().getAnchorUrl()).isEqualTo("https://ab");
        assertThat(read.getWitnessSet().getRedeemers().get(0).getExUnits())
                .isEqualTo(new ExUnits(BigInteger.ONE, BigInteger.TWO));
        // without an era, and in Conway, as before: the ledger reads these records with RecD up to version 11
        assertThatThrownBy(() -> Transaction.deserialize(tx)).hasStackTraceContaining("definite-length");
        assertThatThrownBy(() -> Transaction.deserialize(tx, Era.Conway)).hasStackTraceContaining("definite-length");
    }

    @Test
    void aSubTransactionIsReadAsLenientlyAsItsTransaction() throws Exception {
        String sub = "83" + "a3" + "00" + "80" + "01" + "80" + "13" + INDEFINITE_VOTES + "a0" + "f6";
        String body = "a4" + "00" + "81" + "82" + H32 + "00" + "01" + "80" + "02" + "00" + "17" + "81" + sub;

        Transaction read = Transaction.deserialize(HexUtil.decodeHexString("83" + body + "a0" + "f6"), Era.Dijkstra);
        assertThat(read.getBody().getSubTransactions().get(0).getBody().getVotingProcedures().getVoting()).hasSize(1);
    }

    // ---- fixtures ----

    private static Transaction everyNewField() throws Exception {
        TransactionBody body = minimalBody();
        body.setGuards(List.of(KEY_A_CREDENTIAL, SCRIPT_C_CREDENTIAL));
        SubTransaction sub = subTransaction();
        sub.getBody().setGuards(List.of(SCRIPT_C_CREDENTIAL));
        sub.setWitnessSet(TransactionWitnessSet.builder().plutusV4Scripts(List.of(v4Script()))
                .nativeScripts(List.of(new RequireGuard(KEY_A_CREDENTIAL))).build());
        sub.setAuxiliaryData(AuxiliaryData.builder().plutusV4Scripts(List.of(v4Script())).build());
        body.setSubTransactions(List.of(sub));
        body.setRequiredTopLevelGuards(List.of(new RequiredGuard(SCRIPT_C_CREDENTIAL, BigIntPlutusData.of(7)),
                new RequiredGuard(KEY_A_CREDENTIAL, null)));
        body.setDirectDeposits(List.of(new Withdrawal(rewardAddress(SCRIPT_C_CREDENTIAL, false), BigInteger.TWO),
                new Withdrawal(rewardAddress(KEY_A_CREDENTIAL, false), BigInteger.ONE)));
        body.setAccountBalanceIntervals(List.of(bounds(rewardAddress(SCRIPT_C_CREDENTIAL, false), 1L, 9L),
                exact(rewardAddress(KEY_A_CREDENTIAL, true), 3)));
        body.setStartingAccountBalanceIntervals(List.of(bounds(rewardAddress(KEY_A_CREDENTIAL, false), null, 8L)));
        body.setCerts(List.of(pool(blsKey())));
        body.setProposalProcedures(List.of(proposal(everyParameter())));

        Redeemer redeemer = Redeemer.builder().tag(RedeemerTag.Guarding).index(BigInteger.ZERO)
                .data(BigIntPlutusData.of(1)).exUnits(new ExUnits(BigInteger.ONE, BigInteger.ONE)).build();
        return Transaction.builder().era(Era.Dijkstra).body(body)
                .witnessSet(TransactionWitnessSet.builder().plutusV4Scripts(List.of(v4Script()))
                        .redeemers(new ArrayList<>(List.of(redeemer))).build())
                .auxiliaryData(AuxiliaryData.builder().plutusV4Scripts(List.of(v4Script())).build()).build();
    }

    private static ProtocolParamUpdate everyParameter() {
        ProtocolParamUpdate.ProtocolParamUpdateBuilder builder = ProtocolParamUpdate.builder();
        dijkstraParameters().forEach(parameter -> {
            ProtocolParamUpdate one = (ProtocolParamUpdate) parameter[2];
            copy(one, builder);
        });
        return builder.build();
    }

    // Each update sets one field: copy it.
    private static void copy(ProtocolParamUpdate one, ProtocolParamUpdate.ProtocolParamUpdateBuilder builder) {
        set(one.getMaxRefScriptSizePerBlock(), builder::maxRefScriptSizePerBlock);
        set(one.getMaxRefScriptSizePerTx(), builder::maxRefScriptSizePerTx);
        set(one.getRefScriptCostStride(), builder::refScriptCostStride);
        set(one.getRefScriptCostMultiplier(), builder::refScriptCostMultiplier);
        set(one.getMaxPledgeLeverage(), builder::maxPledgeLeverage);
        set(one.getMinPoolMargin(), builder::minPoolMargin);
        set(one.getLeiosAnnouncementPeriodLength(), builder::leiosAnnouncementPeriodLength);
        set(one.getLeiosVotePeriodLength(), builder::leiosVotePeriodLength);
        set(one.getLeiosDiffusionPeriodLength(), builder::leiosDiffusionPeriodLength);
        set(one.getLeiosCommitteeSize(), builder::leiosCommitteeSize);
        set(one.getLeiosQuorumStakeThreshold(), builder::leiosQuorumStakeThreshold);
        set(one.getMaxEndorserBlockReferencesSize(), builder::maxEndorserBlockReferencesSize);
        set(one.getMaxEndorserBlockTxsSize(), builder::maxEndorserBlockTxsSize);
        set(one.getMaxEndorserBlockExUnits(), builder::maxEndorserBlockExUnits);
        set(one.getMaxRefScriptSizePerEndorserBlock(), builder::maxRefScriptSizePerEndorserBlock);
        set(one.getPerasMinCandidateBlockAge(), builder::perasMinCandidateBlockAge);
        set(one.getPerasHealingFactor(), builder::perasHealingFactor);
        set(one.getPerasCertBoost(), builder::perasCertBoost);
        set(one.getPerasTargetCommitteeSize(), builder::perasTargetCommitteeSize);
        set(one.getPerasBootstrapRound(), builder::perasBootstrapRound);
        set(one.getPerasQuorumThresholdSafetyMargin(), builder::perasQuorumThresholdSafetyMargin);
        set(one.getRefInputsCostPerMultiAssetPolicy(), builder::refInputsCostPerMultiAssetPolicy);
        set(one.getRefInputsCostPerDatumByte(), builder::refInputsCostPerDatumByte);
    }

    private static <T> void set(T value, Consumer<T> setter) {
        if (value != null)
            setter.accept(value);
    }

    private static TransactionBody minimalBody() {
        TransactionBody body = new TransactionBody();
        body.setInputs(new ArrayList<>(List.of(new TransactionInput(TX_ID, 0))));
        return body;
    }

    private static SubTransaction subTransaction() {
        return new SubTransaction();
    }

    private static PlutusV4Script v4Script() {
        PlutusV4Script script = new PlutusV4Script();
        script.setCborHex("4401020304");
        return script;
    }

    private static BlsKey blsKey() {
        return new BlsKey(HexUtil.decodeHexString("aa".repeat(96)), HexUtil.decodeHexString("bb".repeat(48)));
    }

    private static PoolRegistration pool(BlsKey blsKey) {
        return PoolRegistration.builder().operator(HexUtil.decodeHexString(KEY_A))
                .vrfKeyHash(HexUtil.decodeHexString(TX_ID)).pledge(BigInteger.ONE).cost(BigInteger.TWO)
                .margin(unit(1, 2)).rewardAccount("e0" + KEY_A).poolOwners(Set.of(KEY_B)).relays(List.of())
                .blsKey(blsKey).build();
    }

    private static ProposalProcedure proposal(ProtocolParamUpdate update) {
        return ProposalProcedure.builder().deposit(BigInteger.TEN).rewardAccount(rewardAddress(KEY_A_CREDENTIAL, false))
                .govAction(ParameterChangeAction.builder().protocolParamUpdate(update).build())
                .anchor(new Anchor("https://a", HexUtil.decodeHexString(TX_ID))).build();
    }

    private static String rewardAddress(Credential credential, boolean mainnet) {
        return AddressProvider.getRewardAddress(credential, mainnet ? Networks.mainnet() : Networks.testnet())
                .toBech32();
    }

    private static AccountBalanceInterval exact(String rewardAddress, long coin) {
        return new AccountBalanceInterval(rewardAddress, BigInteger.valueOf(coin), null, null);
    }

    private static AccountBalanceInterval bounds(String rewardAddress, Long lower, Long upper) {
        return new AccountBalanceInterval(rewardAddress, null, lower == null ? null : BigInteger.valueOf(lower),
                upper == null ? null : BigInteger.valueOf(upper));
    }

    private static Rational rational(long numerator, long denominator) {
        return new Rational(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }

    private static UnitInterval unit(long numerator, long denominator) {
        return new UnitInterval(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }

    private static String hex(DataItem item) throws Exception {
        return HexUtil.encodeHexString(CborSerializationUtil.serialize(item));
    }
}
