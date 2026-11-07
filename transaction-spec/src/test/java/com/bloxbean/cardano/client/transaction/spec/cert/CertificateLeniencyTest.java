package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.transaction.spec.governance.DRepType;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What the certificate models accepted before they moved onto the reader, and keep accepting until a separate decision
 * changes it (ADR 0003 D7, I5): not all of it is valid on chain. {@code CertificateOutcomesTest} pins the same over the
 * dataset; this pins the cases by name.
 */
class CertificateLeniencyTest {
    private static final String KEY = "581c" + "11".repeat(28);
    private static final String HASH32 = "5820" + "33".repeat(32);
    private static final String CRED = "8200" + KEY;
    private static final String POOL = "8a03" + KEY + HASH32 + "%s" + "%s" + "%s" + "581de0" + "11".repeat(28) + "%s"
            + "%s" + "%s";
    private static final String MARGIN = "d81e820103";

    private static DataItem item(String hex) {
        return CborSerializationUtil.deserialize(HexFormat.of().parseHex(hex));
    }

    private static Array array(String hex) {
        return (Array) item(hex);
    }

    private static String pool(String relays, String metadata) {
        return String.format(POOL, "00", "00", MARGIN, "80", relays, metadata);
    }

    @Test
    void aConwayCertificateMayHaveMoreItems() throws Exception {
        RegCert cert = (RegCert) Certificate.deserialize(array("84" + "07" + CRED + "05" + "00"));
        assertThat(cert.getCoin()).isEqualTo(5);
    }

    @Test
    void aShelleyCertificateMayNot() {
        assertThatThrownBy(() -> Certificate.deserialize(array("83" + "00" + CRED + "00")))
                .isInstanceOf(CborDeserializationException.class);
    }

    @Test
    void aConwayModelDoesNotReadItsTag() {
        assertThat(RegCert.deserialize(array("83" + "6161" + CRED + "05")).getCoin()).isEqualTo(5);
        assertThat(UnregCert.deserialize(array("83" + "07" + CRED + "05")).getCoin()).isEqualTo(5);
    }

    @Test
    void aShelleyModelDoes() {
        assertThatThrownBy(() -> StakeRegistration.deserialize(array("82" + "01" + CRED)))
                .isInstanceOf(CborDeserializationException.class);
        assertThatThrownBy(() -> PoolRetirement.deserialize(array("83" + "03" + KEY + "00")))
                .isInstanceOf(CborDeserializationException.class);
    }

    @Test
    void aPositionOfANumberHoldingSomethingElseReadsAsNull() throws Exception {
        assertThat(RegCert.deserialize(array("83" + "07" + CRED + "6161")).getCoin()).isNull();
        assertThat(((RegDRepCert) Certificate.deserialize(array("84" + "10" + CRED + "80" + "f6"))).getCoin()).isNull();
        PoolRegistration pool = (PoolRegistration) Certificate.deserialize(
                array(String.format(POOL, "f6", "bfff", MARGIN, "80", "80", "f6")));
        assertThat(pool.getPledge()).isNull();
        assertThat(pool.getCost()).isNull();
    }

    @Test
    void aByteStringNeedsABignumTag() {
        assertThatThrownBy(() -> RegCert.deserialize(array("83" + "07" + CRED + "4101"))).isInstanceOf(Exception.class);
        assertThat(RegCert.deserialize(array("83" + "07" + CRED + "c24101")).getCoin()).isEqualTo(1);
        assertThat(RegCert.deserialize(array("83" + "07" + CRED + "c34101")).getCoin()).isEqualTo(-2);
        assertThat(RegCert.deserialize(array("83" + "07" + CRED + "c54101")).getCoin()).isNull();
    }

    @Test
    void tagsAreIgnoredWhereTheyDoNotDecideAnything() throws Exception {
        RegCert cert = (RegCert) Certificate.deserialize(array("83" + "d81807" + "d90102" + CRED + "c505"));
        assertThat(cert.getCoin()).isEqualTo(5);
        StakeDelegation delegation = (StakeDelegation) Certificate.deserialize(array("83" + "02" + CRED + "c5" + KEY));
        assertThat(delegation.getStakePoolId().getPoolKeyHash()).hasSize(28);
    }

    @Test
    void aTaggedNullIsNoNull() {
        assertThatThrownBy(() -> Certificate.deserialize(array("83" + "12" + CRED + "c5f6")))
                .isInstanceOf(Exception.class);
    }

    @Test
    void anEmptyIndefiniteMapIsAMap() throws Exception {
        MoveInstataneous mir = (MoveInstataneous) Certificate.deserialize(array("82" + "06" + "82" + "00" + "bfff"));
        assertThat(mir.getStakeCredentialCoinMap()).isEmpty();
        assertThat(mir.getAccountingPotCoin()).isNull();
    }

    @Test
    void aMirKeepsAnUnknownPotAsNullAndIgnoresMoreItems() throws Exception {
        MoveInstataneous mir = (MoveInstataneous) Certificate.deserialize(
                array("82" + "06" + "83" + "05" + "01" + "00"));
        assertThat(mir.getPot()).isNull();
        assertThat(mir.getAccountingPotCoin()).isEqualTo(1);
    }

    @Test
    void aDRepMayHaveMoreItems() {
        assertThat(DRep.deserialize(item("82" + "02" + "00")).getType()).isEqualTo(DRepType.ABSTAIN);
        assertThat(DRep.deserialize(item("83" + "00" + KEY + "00")).getType()).isEqualTo(DRepType.ADDR_KEYHASH);
        assertThatThrownBy(() -> DRep.deserialize(item("8100"))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void aCredentialMayNot() {
        assertThatThrownBy(() -> StakeCredential.deserialize(array("83" + "00" + KEY + "00")))
                .isInstanceOf(CborDeserializationException.class);
    }

    @Test
    void aRelayAndPoolMetadataMayHaveMoreItems() throws Exception {
        PoolRegistration pool = (PoolRegistration) Certificate.deserialize(
                array(pool("81" + "83" + "02" + "6161" + "00", "83" + "6161" + "4100" + "00")));
        assertThat(((MultiHostName) pool.getRelays().get(0)).getDnsName()).isEqualTo("a");
        assertThat(pool.getPoolMetadataHash()).isEqualTo("00");
    }

    @Test
    void aRelayMayHaveANullDnsName() throws Exception {
        PoolRegistration pool = (PoolRegistration) Certificate.deserialize(
                array(pool("81" + "82" + "02" + "f6", "f6")));
        assertThat(((MultiHostName) pool.getRelays().get(0)).getDnsName()).isNull();
    }

    @Test
    void anIpv4MappedAddressIsNoIpv6Address() {
        String mapped = "50" + "00".repeat(10) + "ffff" + "7f000001";
        assertThatThrownBy(() -> Certificate.deserialize(array(pool("81" + "84" + "00" + "f6" + "f6" + mapped, "f6"))))
                .isInstanceOf(CborDeserializationException.class);
    }

    @Test
    void aMarginNeedsItsTag() {
        assertThatThrownBy(() -> Certificate.deserialize(
                array(String.format(POOL, "00", "00", "820103", "80", "80", "f6")))).isInstanceOf(Exception.class);
    }

    @Test
    void aMarginMayBeNegative() throws Exception {
        PoolRegistration pool = (PoolRegistration) Certificate.deserialize(array(String.format(POOL, "00", "00",
                "d81e822001", "80", "80", "f6")));
        assertThat(pool.getMargin().getNumerator()).isEqualTo(BigInteger.ONE.negate());
    }

    /** The ledger decodes an anchor with {@code RecD} up to protocol version 11 (IndefiniteLengthArraysTest). */
    @Test
    void anAnchorInACertificateMustHaveADefiniteLength() {
        String indefinite = "9f" + "6161" + HASH32 + "ff";
        assertThatThrownBy(() -> Certificate.deserialize(array("84" + "10" + CRED + "00" + indefinite)))
                .hasStackTraceContaining("expected a definite-length array");
        assertThatThrownBy(() -> Certificate.deserialize(array("83" + "12" + CRED + indefinite)))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> Certificate.deserialize(array("83" + "0f" + CRED + indefinite)))
                .isInstanceOf(Exception.class);
    }

    @Test
    void aSmallUnsignedPositionRejectsWhatDoesNotFitAnInt() {
        assertThatThrownBy(() -> Certificate.deserialize(array("82" + "1a80000000" + CRED)))
                .isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> Certificate.deserialize(array(pool("81" + "83" + "01" + "1a80000000" + "6161", "f6"))))
                .isInstanceOf(CborDeserializationException.class);
    }

    @Test
    void anUnknownTagIsRejected() {
        assertThatThrownBy(() -> Certificate.deserialize(array("821300")))
                .isInstanceOf(CborDeserializationException.class);
    }
}
