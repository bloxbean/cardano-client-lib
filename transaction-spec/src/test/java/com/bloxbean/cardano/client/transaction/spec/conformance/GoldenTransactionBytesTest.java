package com.bloxbean.cardano.client.transaction.spec.conformance;

import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Transactions built through the API keep their bytes (ADR 0002, gate G2). {@code golden-transactions.txt} comes from
 * the released CCL 0.8.0-pre7, not from this code. A change to it must be an intended one, named in the release notes.
 */
class GoldenTransactionBytesTest {

    @Test
    void builtTransactionsKeepTheirBytes() throws IOException, CborSerializationException {
        Map<String, String> golden = golden();
        Map<String, Transaction> transactions = GoldenTransactions.all();

        assertThat(golden.keySet()).containsExactlyElementsOf(transactions.keySet());
        for (Map.Entry<String, Transaction> transaction : transactions.entrySet()) {
            assertThat(HexFormat.of().formatHex(transaction.getValue().serialize())).as(transaction.getKey())
                    .isEqualTo(golden.get(transaction.getKey()));
        }
    }

    private static Map<String, String> golden() throws IOException {
        Map<String, String> golden = new LinkedHashMap<>();
        try (InputStream in = GoldenTransactionBytesTest.class
                .getResourceAsStream("/cbor-conformance/golden-transactions.txt")) {
            new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .map(line -> line.split(" "))
                    .forEach(entry -> golden.put(entry[0], entry[1]));
        }
        return golden;
    }
}
