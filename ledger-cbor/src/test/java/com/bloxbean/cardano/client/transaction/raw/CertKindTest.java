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
        Set<Era> conway = EnumSet.of(Era.Conway);
        for (CertKind kind : CertKind.values()) {
            Set<Era> eras = kind.tag() <= 4 ? shelleyToConway : kind.tag() <= 6 ? shelleyToBabbage : conway;
            for (Era era : Era.values())
                assertThat(kind.member().validIn(era)).as(kind + " in " + era).isEqualTo(eras.contains(era));
        }
        assertThat(CertKind.values()).hasSize(19);
        assertThat(CertKind.RECORD.fields()).hasSize(19);
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
