package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.raw.LedgerDecodingException.Reason;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Native scripts ({@code TimelockRaw}), scripts ({@code [language, script]}), auxiliary data ({@code AlonzoTxAuxData}
 * and {@code decodeMetadatum} at protocol versions 9–11) as {@link HaskellPolicy} decodes them. Moved from Yano's
 * ledger-rules (ADR 0005 D2); evaluating and hashing a script stay there.
 */
class WitnessDecodingTest {

    private static final String KEY = "11".repeat(28);

    private static void script(String hex) {
        HaskellPolicy.CONWAY.walk(FieldType.NATIVE_SCRIPT, HexUtil.decodeHexString(hex));
    }

    @Test
    void timelocksOfEveryConwayTypeDecode() {
        for (String script : List.of("8200581c" + KEY, "820180", "820280", "83030080", "830320" + "80",
                "83030281" + "8200581c" + KEY, "9f00581c" + KEY + "ff", "9f019f9f00581c" + KEY + "ffffff",
                "82041864", "82051864", "82051bffffffffffffffff", "83033b7fffffffffffffff80",
                "8303" + "1b7fffffffffffffff" + "80")) {
            assertThatCode(() -> script(script)).as(script).doesNotThrowAnyException();
        }
    }

    @Test
    void malformedTimelocksDoNotDecode() {
        assertThatThrownBy(() -> script("820658" + "1c" + KEY)).isInstanceOf(LedgerDecodingException.class);
        for (String bad : List.of("80", "9fff", "8207581c" + KEY, "8100", "830100" + "80", "8200581c" + KEY + "00",
                "9f00581c" + KEY + "00ff", "8200d818581c" + KEY, "c1820180", "8201a0", "8201d9010280",
                "8204c24101", "8204" + "20", "8303" + "1b8000000000000000" + "80", "8303c2410180",
                "8303" + "3b8000000000000000" + "80", "820181" + "8200581b" + KEY.substring(2),
                "8200581c" + KEY + "ff")) {
            assertThatThrownBy(() -> script(bad)).as(bad).isInstanceOf(LedgerDecodingException.class);
        }
        assertThatThrownBy(() -> script("8300581c" + KEY + "00")).isInstanceOf(LedgerDecodingException.class);
        assertThatThrownBy(() -> script("82005801" + "00")).isInstanceOf(LedgerDecodingException.class);
        // A key hash is a definite-length byte string below decoder version 12, although CCL joins the chunks.
        assertThatThrownBy(() -> script("82005f" + "580e" + KEY.substring(0, 28) + "580e" + KEY.substring(28) + "ff"))
                .isInstanceOf(LedgerDecodingException.class).hasMessageContaining("indefinite-length byte string");
        assertThatThrownBy(() -> script("8201815f" + "580e" + KEY.substring(0, 28) + "580e" + KEY.substring(28)
                + "ff")).isInstanceOf(LedgerDecodingException.class);
    }

    @Test
    void auxiliaryDataDecodesAsConway() {
        // Shelley map, Allegra array, Alonzo tag 259 with PlutusV3 scripts
        assertThatCode(() -> validate("a1186763616263")).doesNotThrowAnyException();
        assertThatCode(() -> validate("82a080")).doesNotThrowAnyException();
        // {0: [], 1: [], 2: 0}, {}, true, aux: the auxiliary data's scripts come from CCL's view
        RawTx alonzo = HaskellPolicy.CONWAY.transaction(HexUtil.decodeHexString("84" + "a3008001800200" + "a0"
                + "f5" + "d90103a2" + "00a0" + "0481" + "4401020304"));
        assertThat(alonzo.auxScripts()).singleElement().satisfies(s -> assertThat(s.type()).isEqualTo(3));
        // the auxiliary data's scripts decode as Haskell decodes them: a native script of an unknown type, a chunked
        // Plutus script
        for (String aux : List.of("82a081" + "8206581c" + KEY, "d90103a1" + "0481" + "5f4101ff")) {
            assertThatThrownBy(() -> HaskellPolicy.CONWAY.transaction(HexUtil.decodeHexString("84" + "a3008001800200"
                    + "a0" + "f5" + aux))).as(aux).isInstanceOf(LedgerDecodingException.class);
        }

        // decodeMetadatum: at most 64 bytes of bytes or text (the concatenation for indefinite strings)
        assertThatCode(() -> validate("a1015840" + "00".repeat(64))).doesNotThrowAnyException();
        assertDecodingFailure("a1015841" + "00".repeat(65), "64 bytes");
        assertDecodingFailure("a1017841" + "61".repeat(65), "64 bytes");
        assertDecodingFailure("a1017f7820" + "61".repeat(32) + "7821" + "61".repeat(33) + "ff", "64 bytes");
        assertDecodingFailure("a10162c328", "UTF-8");
        // integers of at most 64 bits; no big-number tags
        assertThatCode(() -> validate("a1013bffffffffffffffff")).doesNotThrowAnyException();
        assertDecodingFailure("a101c249010000000000000000", "Unsupported");
        // no duplicate label; no PlutusV4 (key 5) or unknown key; Allegra form has exactly two fields
        assertDecodingFailure("a201010101", "duplicate metadata label");
        assertDecodingFailure("d90103a1" + "0580", "unknown auxiliary_data_map key 05");
        assertDecodingFailure("d90103a1" + "0680", "unknown auxiliary_data_map key 06");
        assertDecodingFailure("d90103a2" + "00a0" + "00a0", "repeated auxiliary_data_map key 00");
        assertDecodingFailure("83a08080", "unknown auxiliary_data_array item");
    }

    @Test
    void metadataOfAnyDepthIsCheckedWithoutRecursion() {
        // {1: [[[ ... [0] ... ]]]} nested 100,000 deep, and an indefinite map whose break follows a key
        int depth = 100_000;
        assertThatCode(() -> validate("a101" + "81".repeat(depth) + "00")).doesNotThrowAnyException();
        assertDecodingFailure("a101" + "81".repeat(depth) + "c0" + "00", "Unsupported");
        assertDecodingFailure("a101bf01ff", "BREAK");
    }

    private static void validate(String hex) {
        HaskellPolicy.CONWAY.walk(FieldType.OPTIONAL_AUXILIARY_DATA, HexUtil.decodeHexString(hex));
    }

    private static void assertDecodingFailure(String hex, String message) {
        assertThatThrownBy(() -> validate(hex)).isInstanceOf(LedgerDecodingException.class)
                .hasMessageContaining(message);
    }

    @Test
    void aGuardIsNotAConwayNativeScript() {
        // [6, [0, keyhash]]: Dijkstra's RequireGuard, which CCL's decoder reads in any era and TimelockRaw does not
        String guard = "8206" + "8200581c" + KEY;
        String body = "a3" + "0081" + "825820" + "00".repeat(32) + "00" + "0180" + "0200";
        String scriptRef = "d818" + "5824" + "8200" + guard;
        // Conway's policy refuses it alone and nested in all, any and at-least, at any depth
        for (String script : List.of(guard, "820181" + guard, "820281" + guard, "83030181" + guard,
                "820181" + "820181" + "820281" + guard)) {
            assertThatThrownBy(() -> HaskellPolicy.CONWAY.walk(FieldType.NATIVE_SCRIPT,
                    StrictCbor.span(HexUtil.decodeHexString(script)))).as(script)
                    .isInstanceOf(LedgerDecodingException.class).hasMessageContaining("type 6");
        }
        // in a transaction's witness set, its auxiliary data (both forms), and an output's reference script
        String output = "a3" + "00581d60" + "00".repeat(28) + "0100" + "03" + scriptRef;
        for (String tx : List.of(
                "84" + body + "a1" + "0181" + guard + "f5f6",
                "84" + body + "a1" + "0181" + "820181" + guard + "f5f6",
                "84" + body + "a0" + "f5" + "82a081" + guard,
                "84" + body + "a0" + "f5" + "d90103a1" + "0181" + guard,
                "84" + "a3" + "0081" + "825820" + "00".repeat(32) + "00" + "0181" + output + "0200" + "a0f5f6")) {
            assertThatThrownBy(() -> HaskellPolicy.CONWAY.transaction(HexUtil.decodeHexString(tx))).as(tx)
                    .isInstanceOf(LedgerDecodingException.class).hasMessageContaining("type 6");
        }
        // the checker is era-aware: Dijkstra reads a guard, at any depth
        for (String script : List.of(guard, "820181" + guard, "8206" + "8201581c" + KEY, "8206" + "9f00581c" + KEY + "ff")) {
            assertThatCode(() -> NativeScriptChecker.check(StrictCbor.span(HexUtil.decodeHexString(script)),
                    Era.Dijkstra, true)).as(script).doesNotThrowAnyException();
        }
        for (String bad : List.of("8206" + "8202581c" + KEY, "8206" + "8300581c" + KEY + "00", "8206" + "581c" + KEY)) {
            assertThatThrownBy(() -> NativeScriptChecker.check(StrictCbor.span(HexUtil.decodeHexString(bad)),
                    Era.Dijkstra, true)).as(bad).isInstanceOf(LedgerDecodingException.class);
        }
        // a script reference's script, read alone, is refused the same way
        assertThatThrownBy(() -> HaskellPolicy.CONWAY.script(HexUtil.decodeHexString("8200" + guard)))
                .isInstanceOf(LedgerDecodingException.class).hasMessageContaining("type 6")
                .extracting(e -> ((LedgerDecodingException) e).reason()).isEqualTo(Reason.SCRIPT);
        // types 0 to 5 are unchanged
        assertThatCode(() -> script("820181" + "8200581c" + KEY)).doesNotThrowAnyException();
        assertThatCode(() -> HaskellPolicy.CONWAY.transaction(HexUtil.decodeHexString("84" + body + "a1" + "0181"
                + "8200581c" + KEY + "f5f6"))).doesNotThrowAnyException();
    }

    @Test
    void aScriptIsALanguageAndItsScript() {
        HaskellPolicy p = HaskellPolicy.CONWAY;
        assertThat(p.script(HexUtil.decodeHexString("8203" + "46450101002499")).type()).isEqualTo(3);
        assertThat(p.script(HexUtil.decodeHexString("8200" + "8200581c" + KEY)).type()).isZero();
        assertThat(p.script(HexUtil.decodeHexString("8201" + "40")).type()).isEqualTo(1);
        for (String bad : List.of("8204" + "40", "83034000", "8103", "a0", "8203" + "5f4101ff", "8203" + "60",
                "8220" + "40", "8203" + "c240", "8203" + "46450101002499" + "00")) {
            assertThatThrownBy(() -> p.script(HexUtil.decodeHexString(bad))).as(bad)
                    .isInstanceOf(LedgerDecodingException.class);
        }
    }
}
