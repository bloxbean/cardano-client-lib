package com.bloxbean.cardano.client.plutus.aiken.blueprint.std;

import com.bloxbean.cardano.client.plutus.blueprint.model.Data;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

import java.util.Arrays;
import java.util.Objects;

/**
 * Shared representation of the CIP-57 Credential schema.
 */
public interface Credential extends Data<Credential> {

    byte[] getHash();

    static Credential verificationKey(byte[] keyHash) {
        return new VerificationKeyCredential(keyHash);
    }

    static Credential script(byte[] scriptHash) {
        return new ScriptCredential(scriptHash);
    }

    /**
     * Credential backed by a verification key hash.
     */
    final class VerificationKeyCredential implements Credential {
        private final byte[] hash;

        public VerificationKeyCredential(byte[] hash) {
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
            if (!(o instanceof VerificationKeyCredential)) return false;
            VerificationKeyCredential that = (VerificationKeyCredential) o;
            return Arrays.equals(hash, that.hash);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(hash);
        }

        @Override
        public String toString() {
            return "VerificationKeyCredential";
        }
    }

    /**
     * Credential backed by a script hash.
     */
    final class ScriptCredential implements Credential {
        private final byte[] hash;

        public ScriptCredential(byte[] hash) {
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
            if (!(o instanceof ScriptCredential)) return false;
            ScriptCredential that = (ScriptCredential) o;
            return Arrays.equals(hash, that.hash);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(hash);
        }

        @Override
        public String toString() {
            return "ScriptCredential";
        }
    }
}
