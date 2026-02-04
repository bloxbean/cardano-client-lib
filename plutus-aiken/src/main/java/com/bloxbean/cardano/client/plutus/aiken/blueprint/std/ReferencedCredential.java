package com.bloxbean.cardano.client.plutus.aiken.blueprint.std;

import com.bloxbean.cardano.client.plutus.blueprint.model.Data;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Shared representation of the CIP-57 Referenced Credential schema.
 */
public interface ReferencedCredential extends Data<ReferencedCredential> {

    /** Inline form referencing the credential directly. */
    final class Inline implements ReferencedCredential {
        private final Credential credential;

        public Inline(Credential credential) {
            this.credential = Objects.requireNonNull(credential, "credential cannot be null");
        }

        public Credential getCredential() {
            return credential;
        }

        @Override
        public ConstrPlutusData toPlutusData() {
            return ConstrPlutusData.of(0, credential.toPlutusData());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Inline)) return false;
            Inline inline = (Inline) o;
            return credential.equals(inline.credential);
        }

        @Override
        public int hashCode() {
            return Objects.hash(credential);
        }

        @Override
        public String toString() {
            return "Inline";
        }
    }

    /** Pointer form referencing a credential via slot/transaction/certificate indices. */
    final class Pointer implements ReferencedCredential {

        private final BigInteger slotNumber;
        private final BigInteger transactionIndex;
        private final BigInteger certificateIndex;

        public Pointer(BigInteger slotNumber, BigInteger transactionIndex, BigInteger certificateIndex) {
            this.slotNumber = Objects.requireNonNull(slotNumber, "slotNumber cannot be null");
            this.transactionIndex = Objects.requireNonNull(transactionIndex, "transactionIndex cannot be null");
            this.certificateIndex = Objects.requireNonNull(certificateIndex, "certificateIndex cannot be null");
        }

        public BigInteger getSlotNumber() {
            return slotNumber;
        }

        public BigInteger getTransactionIndex() {
            return transactionIndex;
        }

        public BigInteger getCertificateIndex() {
            return certificateIndex;
        }

        @Override
        public ConstrPlutusData toPlutusData() {
            PlutusData slot = BigIntPlutusData.of(slotNumber);
            PlutusData tx = BigIntPlutusData.of(transactionIndex);
            PlutusData cert = BigIntPlutusData.of(certificateIndex);
            return ConstrPlutusData.of(1, slot, tx, cert);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pointer)) return false;
            Pointer pointer = (Pointer) o;
            return slotNumber.equals(pointer.slotNumber)
                    && transactionIndex.equals(pointer.transactionIndex)
                    && certificateIndex.equals(pointer.certificateIndex);
        }

        @Override
        public int hashCode() {
            return Objects.hash(slotNumber, transactionIndex, certificateIndex);
        }

        @Override
        public String toString() {
            return "Pointer";
        }
    }
}
