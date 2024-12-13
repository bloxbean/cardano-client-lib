package com.bloxbean.cardano.client.account;

import com.bloxbean.cardano.client.address.CredentialType;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.cardano.client.governance.keys.CommitteeColdKey;
import com.bloxbean.cardano.client.governance.keys.CommitteeHotKey;
import com.bloxbean.cardano.client.governance.keys.DRepKey;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

//https://github.com/cardano-foundation/CIPs/blob/master/CIP-0105/test-vectors/
public class AccountCIP105KeysTest {

    @ParameterizedTest
    @MethodSource("testVectorsProvider")
    void testDRepSigningKey(CIP105TestVector testVector) {
        DRepKey drepKey = deriveAccount(testVector).drepKey();

        assertThat(HexUtil.encodeHexString(drepKey.signingKey())).isEqualTo(testVector.getDrepSigningKey());
        assertThat(drepKey.bech32SigningKey()).isEqualTo(testVector.getDrepSigningKeyBech32());
    }

    @ParameterizedTest
    @MethodSource("testVectorsProvider")
    void testDRepExtendedSigningKey(CIP105TestVector testVector) {
        var account = deriveAccount(testVector);
        DRepKey drepKey = account.drepKey();

        assertThat(HexUtil.encodeHexString(drepKey.extendedSigningKey())).isEqualTo(testVector.getDrepExtendedSigningKey());
        assertThat(drepKey.bech32ExtendedSigningKey()).isEqualTo(testVector.getDrepExtendedSigningKeyBech32());
    }

    @ParameterizedTest
    @MethodSource("testVectorsProvider")
    void testDRepVerificationKey(CIP105TestVector testVector) {
        var account = deriveAccount(testVector);
        DRepKey drepKey = account.drepKey();

        assertThat(HexUtil.encodeHexString(drepKey.verificationKey())).isEqualTo(testVector.getDrepVerificationKey());
        assertThat(drepKey.bech32VerificationKey()).isEqualTo(testVector.getDrepVerificationKeyBech32());
    }

    @ParameterizedTest
    @MethodSource("testVectorsProvider")
    void testDRepExtendedVerificationKey(CIP105TestVector testVector) {
        var account = deriveAccount(testVector);
        DRepKey drepKey = account.drepKey();

        assertThat(HexUtil.encodeHexString(drepKey.extendedVerificationKey())).isEqualTo(testVector.getDrepExtendedVerificationKey());
        assertThat(drepKey.bech32ExtendedVerificationKey()).isEqualTo(testVector.getDrepExtendedVerificationKeyBech32());
    }

    @ParameterizedTest
    @MethodSource("testVectorsProvider")
    void testDRepVerificationKeyHash(CIP105TestVector testVector) {
        var account = deriveAccount(testVector);
        DRepKey drepKey = account.drepKey();

        assertThat(HexUtil.encodeHexString(drepKey.verificationKeyHash())).isEqualTo(testVector.getDrepVerificationKeyHash());
        assertThat(drepKey.bech32VerificationKeyHash()).isEqualTo(testVector.getDrepVkhBech32());
    }

    //The following two tests are not Account api specific. But it's here for completeness of TestVector
    @ParameterizedTest
    @MethodSource("testVectorsProvider")
    void testDrepIdScriptHash1(CIP105TestVector testVector) {
        var drepScriptHash = DRepKey.bech32ScriptHash(HexUtil.decodeHexString(testVector.getDrepScriptHash1()));
        assertThat(drepScriptHash).isEqualTo(testVector.getDrepScriptHash1Bech32());
    }

    @ParameterizedTest
    @MethodSource("testVectorsProvider")
    void testDrepIdScriptHash2(CIP105TestVector testVector) {
        var drepScriptHash = DRepKey.bech32ScriptHash(HexUtil.decodeHexString(testVector.getDrepScriptHash2()));
        assertThat(drepScriptHash).isEqualTo(testVector.getDrepScriptHash2Bech32());
    }

    //-- Constitutional Committee Cold Tests

    @ParameterizedTest
    @MethodSource("testVectorsProvider")
    void testConstitutionalCommitteeColdSigningKey(CIP105TestVector testVector) {
        var committeeColdKey = deriveAccount(testVector).committeeColdKey();

        assertThat(HexUtil.encodeHexString(committeeColdKey.signingKey())).isEqualTo(testVector.getCommitteeColdSigningKey());
        assertThat(committeeColdKey.bech32SigningKey()).isEqualTo(testVector.getCommitteeColdSigningKeyBech32());
    }

    @ParameterizedTest
    @MethodSource("testVectorsProvider")
    void testConstitutionalCommitteeColdVerificationKey(CIP105TestVector testVector) {
        var committeeColdKey = deriveAccount(testVector).committeeColdKey();

        assertThat(HexUtil.encodeHexString(committeeColdKey.verificationKey())).isEqualTo(testVector.getCommitteeColdVerificationKey());
        assertThat(committeeColdKey.bech32VerificationKey()).isEqualTo(testVector.getCommitteeColdVerificationKeyBech32());
    }

    @ParameterizedTest
    @MethodSource("testVectorsProvider")
    void testConstitutionalCommitteeColdExtendedSigningKey(CIP105TestVector testVector) {
        var committeeColdKey = deriveAccount(testVector).committeeColdKey();

        assertThat(HexUtil.encodeHexString(committeeColdKey.extendedSigningKey())).isEqualTo(testVector.getCommitteeColdExtendedSigningKey());
        assertThat(committeeColdKey.bech32ExtendedSigningKey()).isEqualTo(testVector.getCommitteeColdExtendedSigningKeyBech32());
    }

    @ParameterizedTest
    @MethodSource("testVectorsProvider")
    void testConstitutionalCommitteeColdExtendedVerificationKey(CIP105TestVector testVector) {
        var committeeColdKey = deriveAccount(testVector).committeeColdKey();

        assertThat(HexUtil.encodeHexString(committeeColdKey.extendedVerificationKey())).isEqualTo(testVector.getCommitteeColdExtendedVerificationKey());
        assertThat(committeeColdKey.bech32ExtendedVerificationKey()).isEqualTo(testVector.getCommitteeColdExtendedVerificationKeyBech32());
    }

    @ParameterizedTest
    @MethodSource("testVectorsProvider")
    void testConstitutionalCommitteeColdVerificationKeyHash(CIP105TestVector testVector) {
        var committeeColdKey = deriveAccount(testVector).committeeColdKey();

        assertThat(HexUtil.encodeHexString(committeeColdKey.verificationKeyHash())).isEqualTo(testVector.getCommitteeColdVerificationKeyHash());
        assertThat(committeeColdKey.bech32VerificationKeyHash()).isEqualTo(testVector.getCommitteeColdVkhBech32());
    }

    @ParameterizedTest
    @MethodSource("testVectorsProvider")
    void testConstitutionalCommitteeColdScriptHash1(CIP105TestVector testVector) {
        var bech32ScriptHash = CommitteeColdKey.bech32ScriptHash(testVector.getColdScriptHash1());

        assertThat(bech32ScriptHash).isEqualTo(testVector.getColdScriptHash1Bech32());
    }

    @ParameterizedTest
    @MethodSource("testVectorsProvider")
    void testConstitutionalCommitteeColdScriptHash2(CIP105TestVector testVector) {
        var bech32ScriptHash = CommitteeColdKey.bech32ScriptHash(testVector.getColdScriptHash2());

        assertThat(bech32ScriptHash).isEqualTo(testVector.getColdScriptHash2Bech32());
    }

    @ParameterizedTest
    @MethodSource("testVectorsProvider")
    void testConstitutionalCommitteeHotSigningKey(CIP105TestVector testVector) {
        var committeeHotKey = deriveAccount(testVector).committeeHotKey();

        assertThat(HexUtil.encodeHexString(committeeHotKey.signingKey())).isEqualTo(testVector.getCommitteeHotSigningKey());
        assertThat(committeeHotKey.bech32SigningKey()).isEqualTo(testVector.getCommitteeHotSigningKeyBech32());
    }

    @ParameterizedTest
    @MethodSource("testVectorsProvider")
    void testConstitutionalCommitteeHotVerificationKey(CIP105TestVector testVector) {
        var committeeHotKey = deriveAccount(testVector).committeeHotKey();

        assertThat(HexUtil.encodeHexString(committeeHotKey.verificationKey())).isEqualTo(testVector.getCommitteeHotVerificationKey());
        assertThat(committeeHotKey.bech32VerificationKey()).isEqualTo(testVector.getCommitteeHotVerificationKeyBech32());
    }

    @ParameterizedTest
    @MethodSource("testVectorsProvider")
    void testConstitutionalCommitteeHotExtendedSigningKey(CIP105TestVector testVector) {
        var committeeHotKey = deriveAccount(testVector).committeeHotKey();

        assertThat(HexUtil.encodeHexString(committeeHotKey.extendedSigningKey())).isEqualTo(testVector.getCommitteeHotExtendedSigningKey());
        assertThat(committeeHotKey.bech32ExtendedSigningKey()).isEqualTo(testVector.getCommitteeHotExtendedSigningKeyBech32());
    }

    @ParameterizedTest
    @MethodSource("testVectorsProvider")
    void testConstitutionalCommitteeHotExtendedVerificationKey(CIP105TestVector testVector) {
        var committeeHotKey = deriveAccount(testVector).committeeHotKey();

        assertThat(HexUtil.encodeHexString(committeeHotKey.extendedVerificationKey())).isEqualTo(testVector.getCommitteeHotExtendedVerificationKey());
        assertThat(committeeHotKey.bech32ExtendedVerificationKey()).isEqualTo(testVector.getCommitteeHotExtendedVerificationKeyBech32());
    }

    @ParameterizedTest
    @MethodSource("testVectorsProvider")
    void testConstitutionalCommitteeHotVerificationKeyHash(CIP105TestVector testVector) {
        var committeeHotKey = deriveAccount(testVector).committeeHotKey();

        assertThat(HexUtil.encodeHexString(committeeHotKey.verificationKeyHash())).isEqualTo(testVector.getCommitteeHotVerificationKeyHash());
        assertThat(committeeHotKey.bech32VerificationKeyHash()).isEqualTo(testVector.getCommitteeHotVkhBech32());
    }

    @ParameterizedTest
    @MethodSource("testVectorsProvider")
    void testConstitutionalCommitteeHotScriptHash1(CIP105TestVector testVector) {
        var bech32ScriptHash = CommitteeHotKey.bech32ScriptHash(testVector.getCcHotScriptHash1());

        assertThat(bech32ScriptHash).isEqualTo(testVector.getCcHotScriptHash1Bech32());
    }

    @ParameterizedTest
    @MethodSource("testVectorsProvider")
    void testConstitutionalCommitteeHotScriptHash2(CIP105TestVector testVector) {
        var bech32ScriptHash = CommitteeHotKey.bech32ScriptHash(testVector.getCcHotScriptHash2());

        assertThat(bech32ScriptHash).isEqualTo(testVector.getCcHotScriptHash2Bech32());
    }

    //Additional tests
    @ParameterizedTest
    @MethodSource("testVectorsProvider")
    void testCommitteeColdCredential(CIP105TestVector testVector) {
        var committeeColdCredential = deriveAccount(testVector).committeeColdCredential();

        assertThat(committeeColdCredential.getType()).isEqualTo(CredentialType.Key);
        assertThat(HexUtil.encodeHexString(committeeColdCredential.getBytes())).isEqualTo(testVector.getCommitteeColdVerificationKeyHash());
    }

    @ParameterizedTest
    @MethodSource("testVectorsProvider")
    void testCommitteeHotCredential(CIP105TestVector testVector) {
        var committeeHotCredential = deriveAccount(testVector).committeeHotCredential();

        assertThat(committeeHotCredential.getType()).isEqualTo(CredentialType.Key);
        assertThat(HexUtil.encodeHexString(committeeHotCredential.getBytes())).isEqualTo(testVector.getCommitteeHotVerificationKeyHash());
    }

    private static Stream<CIP105TestVector> testVectorsProvider() {
        return Stream.of(
            CIP105TestVector.testVector1(),
            CIP105TestVector.testVector2(),
            CIP105TestVector.testVector3(),
            CIP105TestVector.testVector4()
        );
    }

    private Account deriveAccount(CIP105TestVector testVector) {
        DerivationPath derivationPath2 = DerivationPath.createDRepKeyDerivationPathForAccount(testVector.getAccount());
        return new Account(Networks.mainnet(), testVector.getMnemonic(), derivationPath2);
    }

}

