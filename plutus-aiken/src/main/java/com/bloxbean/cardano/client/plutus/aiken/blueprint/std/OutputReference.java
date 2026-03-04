package com.bloxbean.cardano.client.plutus.aiken.blueprint.std;

import com.bloxbean.cardano.client.plutus.blueprint.model.Data;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

/**
 * Shared representation of the CIP-57 OutputReference schema (Aiken stdlib v2+).
 * <p>
 * An {@code OutputReference} is a unique reference to an output on-chain.
 * The {@code outputIndex} corresponds to the position in the output list of
 * the transaction (identified by its id) that produced that output.
 * <p>
 * This type matches the flat {@code cardano/transaction/OutputReference} definition
 * emitted by Aiken stdlib v2 and v3, where {@code transaction_id} is a bare
 * {@code ByteArray}. Stdlib v1 used a nested {@code TransactionId} wrapper instead
 * and is not covered by this type.
 */
public final class OutputReference implements Data<OutputReference> {

    private final byte[] transactionId;
    private final BigInteger outputIndex;

    public OutputReference(byte[] transactionId, BigInteger outputIndex) {
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId cannot be null").clone();
        this.outputIndex = Objects.requireNonNull(outputIndex, "outputIndex cannot be null");
    }

    public static OutputReference of(byte[] transactionId, BigInteger outputIndex) {
        return new OutputReference(transactionId, outputIndex);
    }

    /**
     * Deserializes a {@link ConstrPlutusData} back into an {@link OutputReference}.
     * Expects constructor index 0 with two fields: transaction_id (bytes) and output_index (integer).
     */
    public static OutputReference fromPlutusData(ConstrPlutusData constr) {
        byte[] txId = ((BytesPlutusData) constr.getData().getPlutusDataList().get(0)).getValue();
        BigInteger idx = ((BigIntPlutusData) constr.getData().getPlutusDataList().get(1)).getValue();
        return new OutputReference(txId, idx);
    }

    public byte[] getTransactionId() {
        return transactionId.clone();
    }

    public BigInteger getOutputIndex() {
        return outputIndex;
    }

    @Override
    public ConstrPlutusData toPlutusData() {
        PlutusData txId = BytesPlutusData.of(transactionId);
        PlutusData idx = BigIntPlutusData.of(outputIndex);
        return ConstrPlutusData.of(0, txId, idx);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OutputReference)) return false;
        OutputReference that = (OutputReference) o;
        return Arrays.equals(transactionId, that.transactionId) && outputIndex.equals(that.outputIndex);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(transactionId) + outputIndex.hashCode();
    }

    @Override
    public String toString() {
        return "OutputReference";
    }
}
