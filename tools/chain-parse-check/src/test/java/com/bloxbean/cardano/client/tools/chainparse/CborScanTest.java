package com.bloxbean.cardano.client.tools.chainparse;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CborScanTest {

    /** The scan calls Plutus data canonical exactly when the model re-encodes it to the same bytes. */
    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "01, true",
            "d86682188080, true",                 // constructor 128, general form
            "9f01ff, true",                       // the model keeps an indefinite list
            "d8799f01ff, true",                   // ... and indefinite constructor fields
            "d8668218809f01ff, true",             // ... also inside the general form
            "c24101, false",                      // 1 as a bignum: the model writes 01
            "c34101, false",                      // -2 as a bignum: 21
            "c2480100000000000000, false",        // 2^56: a CBOR integer
            "c34900ffffffffffffffff, false",      // a leading zero: 3bffffffffffffffff
            "d8669f188080ff, false",              // indefinite [alternative, fields]: written definite
            "9fff, false",                        // an empty indefinite list is written 80
            "63616263, false",                    // a text string is written as bytes
            "d87901, false",                      // a constructor tag on an integer is dropped
    })
    void canonicalMatchesTheModel(String hex, boolean canonical) throws Exception {
        byte[] bytes = HexUtil.decodeHexString(hex);

        assertEquals(canonical, CborScan.scan(bytes, true).canonical);
        assertEquals(canonical, hex.equals(PlutusData.deserialize(bytes).serializeToHex()));
    }

    /** Integers outside -2^64..2^64-1 in minimal bytes, as plutus-core encodeData writes them (and the model, #693). */
    @Test
    void minimalBignumsAreCanonical() throws Exception {
        for (String hex : List.of("c249010000000000000000", "c349010000000000000000", // 2^64, -2^64 - 1
                "c25840" + "01" + "00".repeat(63))) {                              // 2^504
            assertTrue(CborScan.scan(HexUtil.decodeHexString(hex), true).canonical, hex);
            assertEquals(hex, PlutusData.deserialize(HexUtil.decodeHexString(hex)).serializeToHex(), hex);
        }
    }
}
