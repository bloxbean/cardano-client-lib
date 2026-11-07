package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CertKindTest {

    @Test
    void everyTagHasItsEras() {
        Set<Era> shelleyToBabbage = EnumSet.range(Era.Shelley, Era.Babbage);
        Set<Era> shelleyToConway = EnumSet.range(Era.Shelley, Era.Conway);
        Set<Era> shelleyOn = EnumSet.allOf(Era.class);
        Set<Era> conwayOn = EnumSet.range(Era.Conway, Era.Dijkstra);
        for (CertKind kind : CertKind.values()) {
            // Dijkstra drops tags 0, 1, 5 and 6 (Dijkstra/TxCert.hs)
            Set<Era> eras = kind.tag() <= 1 ? shelleyToConway : kind.tag() <= 4 ? shelleyOn
                    : kind.tag() <= 6 ? shelleyToBabbage : conwayOn;
            for (Era era : Era.values())
                assertThat(kind.member().validIn(era)).as(kind + " in " + era).isEqualTo(eras.contains(era));
        }
        for (Era era : Era.values()) {
            assertThat(CertKind.POOL_REGISTRATION_WITH_BLS_KEY.validIn(era)).as("BLS key in " + era)
                    .isEqualTo(era == Era.Dijkstra);
        }
        assertThat(CertKind.values()).hasSize(19);
        assertThat(CertKind.RECORD.fields()).hasSize(20);
    }

    @Test
    void readsTheKindFromItsTag() {
        for (CertKind kind : CertKind.values())
            assertThat(CertKind.of(span(String.format("81%s", tag(kind.tag()))))).contains(kind);
        assertThat(CertKind.of(span("8113"))).isEmpty();
        // non-minimal, indefinite and tagged forms name the same kind
        assertThat(CertKind.of(span("9f1a00000007ff"))).contains(CertKind.REG_CERT);
        assertThat(CertKind.of(span("81d81800"))).contains(CertKind.STAKE_REGISTRATION);
    }

    @Test
    void aTagThatIsNoSmallUnsignedIntegerIsRejected() {
        for (String certificate : new String[]{"80", "00", "a0", "8120", "81c24100", "811a80000000", "816161"}) {
            assertThatThrownBy(() -> CertKind.of(span(certificate))).as(certificate)
                    .isInstanceOf(CborRuntimeException.class);
        }
    }

    private static String tag(int tag) {
        return tag < 24 ? String.format("%02x", tag) : String.format("18%02x", tag);
    }

    private static CborSpan span(String hex) {
        return CborSpan.of(HexUtil.decodeHexString(hex));
    }
}
