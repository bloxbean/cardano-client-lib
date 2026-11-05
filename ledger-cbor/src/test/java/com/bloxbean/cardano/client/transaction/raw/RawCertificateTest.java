package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.UnitInterval;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Layouts are the pinned CDDL's. */
class RawCertificateTest {
    private static final String K1 = "11".repeat(28);
    private static final String K2 = "22".repeat(28);
    private static final String V = "33".repeat(32);
    private static final String KEY1 = "581c" + K1;
    private static final String KEY2 = "581c" + K2;
    private static final String VRF = "5820" + V;
    private static final String CRED1 = "8200" + KEY1;
    private static final String CRED2 = "8201" + KEY2;
    private static final String ANCHOR = "82" + "6161" + VRF;
    private static final String REWARD_ACCOUNT = "581de0" + K1;

    private static CborSpan span(String hex) {
        return CborSpan.of(decodeHexString(hex));
    }

    private static RawCertificate of(String hex) {
        return RawCertificate.of(span(hex));
    }

    private static String hex(byte[] bytes) {
        return HexUtil.encodeHexString(bytes);
    }

    @Test
    void stakeRegistrationAndDeregistration() {
        RawCertificate registration = of("82" + "00" + CRED1);
        assertThat(registration.kind()).isEqualTo(CertKind.STAKE_REGISTRATION);
        assertThat(registration.credential().isScript()).isFalse();
        assertThat(hex(registration.credential().hash())).isEqualTo(K1);

        RawCertificate deregistration = of("82" + "01" + CRED2);
        assertThat(deregistration.kind()).isEqualTo(CertKind.STAKE_DEREGISTRATION);
        assertThat(deregistration.credential().isScript()).isTrue();
        assertThat(hex(deregistration.credential().hash())).isEqualTo(K2);
    }

    @Test
    void stakeDelegation() {
        RawCertificate cert = of("83" + "02" + CRED1 + KEY2);
        assertThat(cert.kind()).isEqualTo(CertKind.STAKE_DELEGATION);
        assertThat(hex(cert.poolKeyHash())).isEqualTo(K2);
    }

    @Test
    void poolRegistration() {
        String singleHostAddr = "84" + "00" + "190bb8" + "447f000001" + "50" + "01".repeat(16);
        String singleHostName = "83" + "01" + "f6" + "6161";
        String multiHostName = "82" + "02" + "6162";
        RawCertificate cert = of("8a" + "03" + KEY1 + VRF + "1864" + "1832" + "d81e820103" + REWARD_ACCOUNT
                + "82" + KEY1 + KEY2 + "83" + singleHostAddr + singleHostName + multiHostName
                + "82" + "6163" + "4101");
        assertThat(cert.kind()).isEqualTo(CertKind.POOL_REGISTRATION);
        assertThat(hex(cert.poolKeyHash())).isEqualTo(K1);
        assertThat(hex(cert.vrfKeyHash())).isEqualTo(V);
        assertThat(cert.pledge()).isEqualTo(100);
        assertThat(cert.cost()).isEqualTo(50);
        assertThat(cert.margin()).isEqualTo(new UnitInterval(BigInteger.ONE, BigInteger.valueOf(3)));
        assertThat(hex(cert.rewardAccount())).isEqualTo("e0" + K1);
        assertThat(cert.poolOwners()).extracting(HexUtil::encodeHexString).containsExactly(K1, K2);

        List<RawRelay> relays = cert.relays();
        assertThat(relays.get(0).kind()).isEqualTo(RawRelay.Kind.SINGLE_HOST_ADDR);
        assertThat(relays.get(0).port()).isEqualTo(3000);
        assertThat(hex(relays.get(0).ipv4())).isEqualTo("7f000001");
        assertThat(hex(relays.get(0).ipv6())).isEqualTo("01".repeat(16));
        assertThat(relays.get(1).kind()).isEqualTo(RawRelay.Kind.SINGLE_HOST_NAME);
        assertThat(relays.get(1).port()).isNull();
        assertThat(relays.get(1).dnsName()).isEqualTo("a");
        assertThat(relays.get(2).kind()).isEqualTo(RawRelay.Kind.MULTI_HOST_NAME);
        assertThat(relays.get(2).dnsName()).isEqualTo("b");

        assertThat(cert.poolMetadata().orElseThrow().url()).isEqualTo("c");
        assertThat(hex(cert.poolMetadata().orElseThrow().hash())).isEqualTo("01");
    }

    @Test
    void poolRegistrationWithANullMetadataAndATaggedOwnerSet() {
        RawCertificate cert = of("8a" + "03" + KEY1 + VRF + "00" + "00" + "d81e820001" + REWARD_ACCOUNT
                + "d9010281" + KEY1 + "80" + "f6");
        assertThat(cert.poolMetadata()).isEmpty();
        assertThat(cert.relays()).isEmpty();
        assertThat(cert.poolOwners()).hasSize(1);
    }

    @Test
    void poolRetirementKeepsAllSixtyFourBitsOfItsEpoch() {
        assertThat(of("83" + "04" + KEY1 + "1864").epoch()).isEqualTo(100);
        assertThat(of("83" + "04" + KEY1 + "1bffffffffffffffff").epoch()).isEqualTo(-1);
    }

    @Test
    void genesisKeyDelegation() {
        RawCertificate cert = of("84" + "05" + KEY1 + KEY2 + VRF);
        assertThat(cert.kind()).isEqualTo(CertKind.GENESIS_KEY_DELEGATION);
        assertThat(hex(cert.genesisHash())).isEqualTo(K1);
        assertThat(hex(cert.genesisDelegateHash())).isEqualTo(K2);
        assertThat(hex(cert.vrfKeyHash())).isEqualTo(V);
    }

    @Test
    void moveInstantaneousRewardsToAnotherPot() {
        RawMir mir = of("82" + "06" + "82" + "01" + "1864").moveInstantaneousReward();
        assertThat(mir.pot()).isEqualTo(1);
        assertThat(mir.movesToCredentials()).isFalse();
        assertThat(mir.coin()).isEqualTo(100);
    }

    @Test
    void moveInstantaneousRewardsToCredentialsKeepNegativeDeltasAndOrder() {
        RawMir mir = of("82" + "06" + "82" + "00" + "a2" + CRED2 + "01" + CRED1 + "20").moveInstantaneousReward();
        assertThat(mir.pot()).isZero();
        assertThat(mir.movesToCredentials()).isTrue();
        assertThat(mir.targets()).hasSize(2);
        assertThat(mir.targets().get(0).getKey().isScript()).isTrue();
        assertThat(mir.targets().get(0).getValue()).isEqualTo(1);
        assertThat(hex(mir.targets().get(1).getKey().hash())).isEqualTo(K1);
        assertThat(mir.targets().get(1).getValue()).isEqualTo(-1);
    }

    @Test
    void regAndUnregCert() {
        RawCertificate reg = of("83" + "07" + CRED1 + "1864");
        assertThat(reg.kind()).isEqualTo(CertKind.REG_CERT);
        assertThat(reg.coin()).isEqualTo(100);
        assertThat(of("83" + "08" + CRED1 + "00").kind()).isEqualTo(CertKind.UNREG_CERT);
    }

    @Test
    void voteDelegCertWithEveryDRep() {
        RawDRep key = of("83" + "09" + CRED1 + "8200" + KEY2).drep();
        assertThat(key.kind()).isEqualTo(RawDRep.Kind.KEY_HASH);
        assertThat(hex(key.hash())).isEqualTo(K2);
        RawDRep script = of("83" + "09" + CRED1 + "8201" + KEY2).drep();
        assertThat(script.kind()).isEqualTo(RawDRep.Kind.SCRIPT_HASH);
        RawDRep abstain = of("83" + "09" + CRED1 + "8102").drep();
        assertThat(abstain.kind()).isEqualTo(RawDRep.Kind.ABSTAIN);
        assertThatThrownBy(abstain::hash).isInstanceOf(CborRuntimeException.class);
        assertThat(of("83" + "09" + CRED1 + "8103").drep().kind()).isEqualTo(RawDRep.Kind.NO_CONFIDENCE);
    }

    @Test
    void stakeVoteDelegCert() {
        RawCertificate cert = of("84" + "0a" + CRED1 + KEY2 + "8102");
        assertThat(cert.kind()).isEqualTo(CertKind.STAKE_VOTE_DELEG_CERT);
        assertThat(hex(cert.poolKeyHash())).isEqualTo(K2);
        assertThat(cert.drep().kind()).isEqualTo(RawDRep.Kind.ABSTAIN);
    }

    @Test
    void stakeRegDelegCert() {
        RawCertificate cert = of("84" + "0b" + CRED1 + KEY2 + "05");
        assertThat(cert.kind()).isEqualTo(CertKind.STAKE_REG_DELEG_CERT);
        assertThat(hex(cert.poolKeyHash())).isEqualTo(K2);
        assertThat(cert.coin()).isEqualTo(5);
    }

    @Test
    void voteRegDelegCert() {
        RawCertificate cert = of("84" + "0c" + CRED1 + "8103" + "05");
        assertThat(cert.kind()).isEqualTo(CertKind.VOTE_REG_DELEG_CERT);
        assertThat(cert.drep().kind()).isEqualTo(RawDRep.Kind.NO_CONFIDENCE);
        assertThat(cert.coin()).isEqualTo(5);
    }

    @Test
    void stakeVoteRegDelegCert() {
        RawCertificate cert = of("85" + "0d" + CRED1 + KEY2 + "8102" + "05");
        assertThat(cert.kind()).isEqualTo(CertKind.STAKE_VOTE_REG_DELEG_CERT);
        assertThat(hex(cert.poolKeyHash())).isEqualTo(K2);
        assertThat(cert.drep().kind()).isEqualTo(RawDRep.Kind.ABSTAIN);
        assertThat(cert.coin()).isEqualTo(5);
    }

    @Test
    void committeeCerts() {
        RawCertificate auth = of("83" + "0e" + CRED1 + CRED2);
        assertThat(auth.kind()).isEqualTo(CertKind.AUTH_COMMITTEE_HOT_CERT);
        assertThat(hex(auth.credential().hash())).isEqualTo(K1);
        assertThat(hex(auth.hotCredential().hash())).isEqualTo(K2);

        RawCertificate resign = of("83" + "0f" + CRED1 + ANCHOR);
        assertThat(resign.kind()).isEqualTo(CertKind.RESIGN_COMMITTEE_COLD_CERT);
        assertThat(resign.anchor().orElseThrow().url()).isEqualTo("a");
        assertThat(hex(resign.anchor().orElseThrow().dataHash())).isEqualTo(V);
        assertThat(of("83" + "0f" + CRED1 + "f6").anchor()).isEmpty();
    }

    @Test
    void drepCerts() {
        RawCertificate reg = of("84" + "10" + CRED1 + "05" + ANCHOR);
        assertThat(reg.kind()).isEqualTo(CertKind.REG_DREP_CERT);
        assertThat(reg.coin()).isEqualTo(5);
        assertThat(reg.anchor()).isPresent();
        assertThat(of("84" + "10" + CRED1 + "05" + "f6").anchor()).isEmpty();

        RawCertificate unreg = of("83" + "11" + CRED1 + "05");
        assertThat(unreg.kind()).isEqualTo(CertKind.UNREG_DREP_CERT);
        assertThat(unreg.coin()).isEqualTo(5);

        RawCertificate update = of("83" + "12" + CRED1 + ANCHOR);
        assertThat(update.kind()).isEqualTo(CertKind.UPDATE_DREP_CERT);
        assertThat(update.anchor()).isPresent();
    }

    /** ADR 0003 D5: what the reader accepts and leaves to a policy. */
    @Test
    void acceptsIndefiniteLengthsNonMinimalIntegersAndChunkedStrings() {
        RawCertificate cert = of("9f" + "1800" + "9f" + "1a00000000" + "5f58" + "1c" + K1 + "ff" + "ff" + "ff");
        assertThat(cert.kind()).isEqualTo(CertKind.STAKE_REGISTRATION);
        assertThat(cert.credential().isScript()).isFalse();
        assertThat(hex(cert.credential().hash())).isEqualTo(K1);
    }

    @Test
    void acceptsAnyHashLengthAndAKindInAnyEra() {
        // a view has no era: tag 5 reads though it ended at Babbage, and a walk reports that (see below)
        assertThat(hex(of("84" + "05" + "4101" + "4102" + "4103").genesisHash())).isEqualTo("01");
    }

    @Test
    void rejectsTheWrongContainerAndTheWrongArity() {
        for (String certificate : new String[]{"00", "a0", "80", "8113", "8120", "8100", "8301" + CRED1 + "00"})
            assertThatThrownBy(() -> of(certificate)).as(certificate).isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void rejectsWhatItReadsWhenItIsNotWhatTheTableSays() {
        assertThatThrownBy(() -> of("82" + "00" + "00").credential()).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> of("82" + "00" + "8300" + KEY1 + "00").credential())
                .isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> of("82" + "00" + "8202" + KEY1).credential()).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> of("83" + "07" + CRED1 + "6161").coin()).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> of("83" + "09" + CRED1 + "820200").drep()).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> of("83" + "0f" + CRED1 + "8100").anchor()).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> of("83" + "04" + KEY1 + "20").epoch()).isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void aKindHasOnlyItsOwnItems() {
        RawCertificate cert = of("82" + "00" + CRED1);
        assertThatThrownBy(cert::coin).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(cert::hotCredential).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(cert::drep).isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void readingIsLazy() {
        RawCertificate cert = of("83" + "07" + "00" + "05");
        assertThat(cert.coin()).isEqualTo(5);
        assertThatThrownBy(cert::credential).isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void marginIsTag30OnTwoIntegers() {
        String pool = "8a" + "03" + KEY1 + VRF + "00" + "00" + "%s" + REWARD_ACCOUNT + "80" + "80" + "f6";
        assertThat(of(String.format(pool, "d81e9f0102ff")).margin())
                .isEqualTo(new UnitInterval(BigInteger.ONE, BigInteger.TWO));
        assertThat(of(String.format(pool, "d90102d81e820102")).margin().getDenominator()).isEqualTo(2);
        assertThatThrownBy(() -> of(String.format(pool, "820102")).margin()).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> of(String.format(pool, "d81e8101")).margin())
                .isInstanceOf(CborRuntimeException.class);
    }

    /** ADR 0003 D7: what {@link RawCertificate#lenient} keeps and {@link RawCertificate#of} does not. */
    @Test
    void lenientReadsByPositionAlone() {
        RawCertificate wrongTag = RawCertificate.lenient(CertKind.REG_CERT, span("83" + "6161" + CRED1 + "05"));
        assertThat(wrongTag.coin()).isEqualTo(5);
        assertThatThrownBy(wrongTag::exact).isInstanceOf(CborRuntimeException.class);

        RawCertificate tooLong = RawCertificate.lenient(CertKind.REG_CERT, span("84" + "07" + CRED1 + "05" + "00"));
        assertThat(tooLong.coin()).isEqualTo(5);
        assertThatThrownBy(tooLong::exact).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> of("84" + "07" + CRED1 + "05" + "00")).isInstanceOf(CborRuntimeException.class);

        assertThat(RawCertificate.lenient(CertKind.REG_CERT, span("83" + "07" + CRED1 + "6161")).coin()).isNull();

        assertThatThrownBy(() -> RawCertificate.lenient(CertKind.REG_CERT, span("82" + "07" + CRED1)))
                .isInstanceOf(CborRuntimeException.class);
    }

    /** ADR 0003 D6: a certificate outside its era is the walk's to report. */
    @Test
    void walkReportsAnEraAndAnArityToThePolicy() {
        RecordingPolicy policy = new RecordingPolicy();
        CertKind.RECORD.walk(span("820500"), Era.Conway, policy);
        assertThat(policy.events).contains("entry certificate 5:MEMBER key=05 value=820500 occurrence=0",
                "entry member 5 1:HASH28 key=null value=00 occurrence=0",
                "missing member 5 2:HASH28", "missing member 5 3:HASH32");
    }

    @Test
    void walkGoesThroughNestedRecords() {
        RecordingPolicy policy = new RecordingPolicy();
        CertKind.RECORD.walk(span("84" + "09" + CRED1 + "8102" + "00"), Era.Conway, policy);
        assertThat(policy.events).contains(
                "entry credential 0:MEMBER key=00 value=" + CRED1 + " occurrence=0",
                "check HASH28 " + KEY1,
                "entry drep 2:MEMBER key=02 value=8102 occurrence=0",
                "entry member 9 null key=null value=00 occurrence=0");
    }

    @Test
    void walkGoesThroughEveryNestedRecordOfAPool() {
        String relay = "83" + "01" + "19ffff" + "6161";
        RecordingPolicy policy = new RecordingPolicy();
        CertKind.RECORD.walk(span("8a" + "03" + KEY1 + VRF + "00" + "00" + "d81e820001" + REWARD_ACCOUNT + "80"
                + "81" + relay + "82" + "6161" + "4100"), Era.Conway, policy);
        assertThat(policy.events).contains(
                "check KEY_HASH_SET 80",
                "entry relay 1:MEMBER key=01 value=" + relay + " occurrence=0",
                "check OPTIONAL_PORT 19ffff",
                "check DNS_NAME 6161",
                "check URL 6161",
                "check BYTES 4100");
    }
}
