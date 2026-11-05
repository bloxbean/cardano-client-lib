package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.transaction.spec.cert.PoolRetirement;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.transaction.spec.governance.Voter;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovActionId;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Positions the ledger defines as unsigned accept a CBOR unsigned integer and nothing else: neither a negative integer
 * nor a bignum, even one holding a small value, and not a value the model's int cannot hold.
 */
class UnsignedPositionsTest {
    private static final String HASH28 = "581c" + "00".repeat(28);
    private static final String HASH32 = "5820" + "00".repeat(32);
    private static final String ABOVE_INT = "1a80000000";

    private interface Reader {
        void read(byte[] cbor) throws Exception;
    }

    /** @param format the CBOR with %s for the position; {@code control} is an unsigned value valid there */
    private record Position(String name, String format, String control, boolean holdsAnInt, Reader reader) {
        byte[] with(String value) {
            return HexFormat.of().parseHex(String.format(format, value));
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static DataItem item(byte[] cbor) {
        return CborSerializationUtil.deserialize(cbor);
    }

    static List<Position> positions() {
        return List.of(
                new Position("GovActionId index", "82" + HASH32 + "%s", "00", true, c -> GovActionId.deserialize(item(c))),
                new Position("Update epoch", "82a0%s", "00", true, c -> Update.deserialize(item(c))),
                new Position("Voter key", "82%s" + HASH28, "00", true, c -> Voter.deserialize((Array) item(c))),
                new Position("DRep key", "82%s" + HASH28, "00", true, c -> DRep.deserialize((Array) item(c))),
                new Position("NativeScript reference type", "82%s8200" + HASH28, "00", true,
                        NativeScript::deserializeScriptRef),
                new Position("PlutusScript reference type", "82%s4100", "01", true, PlutusScript::deserializeScriptRef),
                new Position("Redeemer tag", "84%s0000820000", "00", true, c -> Redeemer.deserialize((Array) item(c))),
                new Position("ProtocolVersion major", "82%s00", "00", true, c -> ProtocolVersion.deserialize((Array) item(c))),
                new Position("PoolRetirement epoch", "8304" + HASH28 + "%s", "00", false,
                        c -> PoolRetirement.deserialize((Array) item(c))));
    }

    @ParameterizedTest
    @MethodSource("positions")
    void anUnsignedIntegerIsAccepted(Position position) {
        assertThatCode(() -> position.reader().read(position.with(position.control()))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @MethodSource("positions")
    void aNegativeIntegerIsRejected(Position position) {
        assertThatThrownBy(() -> position.reader().read(position.with("20"))).isInstanceOf(Exception.class);
    }

    @ParameterizedTest
    @MethodSource("positions")
    void aBignumHoldingASmallValueIsRejected(Position position) {
        assertThatThrownBy(() -> position.reader().read(position.with("c24100"))).isInstanceOf(Exception.class);
    }

    @ParameterizedTest
    @MethodSource("positions")
    void aValueTheModelCannotHoldIsRejected(Position position) {
        String tooBig = position.holdsAnInt() ? ABOVE_INT : "c249010000000000000000";

        assertThatThrownBy(() -> position.reader().read(position.with(tooBig))).isInstanceOf(Exception.class);
    }
}
