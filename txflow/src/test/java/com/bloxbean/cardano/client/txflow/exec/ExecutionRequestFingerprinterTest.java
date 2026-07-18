package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.store.PersistedBinding;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionRequestFingerprinterTest {
    @Test
    void canonicalOrderingDoesNotChangeTheDigest() {
        List<PersistedBinding> firstBindings = List.of(
                binding("z", "STRING", "last"), binding("a", "INTEGER", (byte) 7));
        List<PersistedBinding> secondBindings = List.of(
                binding("a", "INTEGER", 7L), binding("z", "STRING", "last"));
        Map<String, String> firstReferences = new LinkedHashMap<>();
        firstReferences.put("z", "vault://z");
        firstReferences.put("a", "vault://a");
        Map<String, String> secondReferences = new LinkedHashMap<>();
        secondReferences.put("a", "vault://a");
        secondReferences.put("z", "vault://z");

        String first = ExecutionRequestFingerprinter.fingerprint("compiled",
                new LinkedHashSet<>(List.of("wallet:z", "wallet:a")), false,
                firstBindings, firstReferences);
        String second = ExecutionRequestFingerprinter.fingerprint("compiled",
                new LinkedHashSet<>(List.of("wallet:a", "wallet:z")), false,
                secondBindings, secondReferences);

        assertEquals(first, second);
        assertTrue(first.matches("[0-9a-f]{64}"));
    }

    @Test
    void punctuationCannotCollapseDistinctResourcesOrSecureReferences() {
        String oneResource = fingerprint(Set.of("a, b"), Map.of("secret", "vault://one"));
        String twoResources = fingerprint(Set.of("a", "b"), Map.of("secret", "vault://one"));
        String oneReference = fingerprint(Set.of("resource"), Map.of("a", "b, c=d"));
        String twoReferences = fingerprint(Set.of("resource"), Map.of("a", "b", "c", "d"));

        assertNotEquals(oneResource, twoResources);
        assertNotEquals(oneReference, twoReferences);
    }

    @Test
    void effectiveBindingChangesAlterTheDigestWithoutEmbeddingSecrets() {
        PersistedBinding firstSecret = new PersistedBinding(
                "token", "STRING", null, "vault://token", "secret-digest-one", "***");
        PersistedBinding secondSecret = new PersistedBinding(
                "token", "STRING", null, "vault://token", "secret-digest-two", "***");

        String first = ExecutionRequestFingerprinter.fingerprint(
                "compiled", Set.of(), false, List.of(firstSecret), Map.of());
        String second = ExecutionRequestFingerprinter.fingerprint(
                "compiled", Set.of(), false, List.of(secondSecret), Map.of());

        assertNotEquals(first, second);
        assertTrue(first.matches("[0-9a-f]{64}"));
    }

    private String fingerprint(Set<String> resources, Map<String, String> references) {
        return ExecutionRequestFingerprinter.fingerprint(
                "compiled", resources, false, List.of(), references);
    }

    private PersistedBinding binding(String name, String type, Object value) {
        return new PersistedBinding(name, type, value, null,
                "fingerprint-" + name, String.valueOf(value));
    }
}
