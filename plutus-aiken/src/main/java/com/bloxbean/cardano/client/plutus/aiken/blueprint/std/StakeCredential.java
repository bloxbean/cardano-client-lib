package com.bloxbean.cardano.client.plutus.aiken.blueprint.std;

import com.bloxbean.cardano.client.plutus.blueprint.model.Data;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Shared representation of the CIP-57 StakeCredential schema (Aiken stdlib v2+).
 * <p>
 * Matches the {@code cardano/address/StakeCredential} definition emitted by
 * stdlib v2 and v3 blueprints. The Inline variant references
 * {@code cardano/address/Credential} which maps to {@link PaymentCredential}.
 * <p>
 * This type replaces the stdlib v1 {@code Referenced<Credential>} pattern
 * (title "Referenced", namespace {@code aiken/transaction/credential/}).
 * The schema structure is identical in stdlib v2 and v3.
 */
public interface StakeCredential extends Data<StakeCredential> {

    /**
     * Deserializes a {@link ConstrPlutusData} back into a {@link StakeCredential}.
     * Alternative 0 -> {@link Inline}, alternative 1 -> {@link Pointer}.
     */
    static StakeCredential fromPlutusData(ConstrPlutusData constr) {
        return switch ((int) constr.getAlternative()) {
            case 0 -> new Inline(PaymentCredential.fromPlutusData((ConstrPlutusData) constr.getData().getPlutusDataList().get(0)));
            case 1 -> new Pointer(
                    ((BigIntPlutusData) constr.getData().getPlutusDataList().get(0)).getValue(),
                    ((BigIntPlutusData) constr.getData().getPlutusDataList().get(1)).getValue(),
                    ((BigIntPlutusData) constr.getData().getPlutusDataList().get(2)).getValue()
            );
            default -> throw new IllegalArgumentException("Invalid StakeCredential alternative: " + constr.getAlternative());
        };
    }

    /** Inline form referencing the credential directly. */
    final class Inline implements StakeCredential {
        private final PaymentCredential credential;

        public Inline(PaymentCredential credential) {
            this.credential = Objects.requireNonNull(credential, "credential cannot be null");
        }

        public PaymentCredential getCredential() {
            return credential;
        }

        @Override
        public ConstrPlutusData toPlutusData() {
            return ConstrPlutusData.of(0, credential.toPlutusData());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Inline inline)) return false;
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
    final class Pointer implements StakeCredential {

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
            if (!(o instanceof Pointer pointer)) return false;
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
