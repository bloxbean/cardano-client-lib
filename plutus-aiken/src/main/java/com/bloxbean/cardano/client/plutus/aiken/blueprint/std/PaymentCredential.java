package com.bloxbean.cardano.client.plutus.aiken.blueprint.std;

import com.bloxbean.cardano.client.plutus.blueprint.model.Data;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

import java.util.Arrays;
import java.util.Objects;

/**
 * Shared representation of the CIP-57 Credential schema (Aiken stdlib v2+).
 * <p>
 * Uses variant names {@code VerificationKey} / {@code Script} introduced in
 * stdlib v2, replacing the {@code VerificationKeyCredential} / {@code ScriptCredential}
 * names used in stdlib v1.
 * <p>
 * Covers both titled schemas emitted by stdlib v2 and v3 blueprints:
 * <ul>
 *   <li>{@code cardano/address/Credential} (title "Credential")</li>
 *   <li>{@code cardano/address/PaymentCredential} (title "PaymentCredential")</li>
 * </ul>
 * The two are structurally identical; only the title differs. Stdlib v2 uses bare
 * hash refs ({@code VerificationKeyHash}, {@code ScriptHash}) while stdlib v3 uses
 * namespaced refs ({@code aiken/crypto/VerificationKeyHash}, {@code aiken/crypto/ScriptHash}),
 * but both map to this type.
 */
public interface PaymentCredential extends Data<PaymentCredential> {

    byte[] getHash();

    static PaymentCredential verificationKey(byte[] keyHash) {
        return new VerificationKey(keyHash);
    }

    static PaymentCredential script(byte[] scriptHash) {
        return new Script(scriptHash);
    }

    /**
     * Deserializes a {@link ConstrPlutusData} back into a {@link PaymentCredential}.
     * Alternative 0 -> {@link VerificationKey}, alternative 1 -> {@link Script}.
     */
    static PaymentCredential fromPlutusData(ConstrPlutusData constr) {
        byte[] hash = ((BytesPlutusData) constr.getData().getPlutusDataList().get(0)).getValue();
        return switch ((int) constr.getAlternative()) {
            case 0 -> new VerificationKey(hash);
            case 1 -> new Script(hash);
            default -> throw new IllegalArgumentException("Invalid PaymentCredential alternative: " + constr.getAlternative());
        };
    }

    /**
     * Credential backed by a verification key hash.
     */
    final class VerificationKey implements PaymentCredential {
        private final byte[] hash;

        public VerificationKey(byte[] hash) {
            this.hash = Objects.requireNonNull(hash, "hash cannot be null").clone();
        }

        @Override
        public byte[] getHash() {
            return hash.clone();
        }

        @Override
        public ConstrPlutusData toPlutusData() {
            PlutusData bytes = BytesPlutusData.of(hash);
            return ConstrPlutusData.of(0, bytes);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VerificationKey)) return false;
            VerificationKey that = (VerificationKey) o;
            return Arrays.equals(hash, that.hash);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(hash);
        }

        @Override
        public String toString() {
            return "VerificationKey";
        }
    }

    /**
     * Credential backed by a script hash.
     */
    final class Script implements PaymentCredential {
        private final byte[] hash;

        public Script(byte[] hash) {
            this.hash = Objects.requireNonNull(hash, "hash cannot be null").clone();
        }

        @Override
        public byte[] getHash() {
            return hash.clone();
        }

        @Override
        public ConstrPlutusData toPlutusData() {
            PlutusData bytes = BytesPlutusData.of(hash);
            return ConstrPlutusData.of(1, bytes);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Script)) return false;
            Script that = (Script) o;
            return Arrays.equals(hash, that.hash);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(hash);
        }

        @Override
        public String toString() {
            return "Script";
        }
    }
}
