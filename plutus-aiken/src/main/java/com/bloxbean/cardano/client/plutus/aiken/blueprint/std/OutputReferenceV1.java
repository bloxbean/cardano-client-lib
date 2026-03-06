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
 * Legacy representation of the CIP-57 OutputReference schema (Aiken stdlib v1).
 * <p>
 * In stdlib v1, the {@code aiken/transaction/OutputReference} definition wraps
 * the transaction hash inside a nested {@code TransactionId} constructor:
 * <pre>
 * Constr(0, [Constr(0, [ByteArray(txHash)]), Int(outputIndex)])
 * </pre>
 * This differs from the flat layout used in stdlib v2+ where {@code transaction_id}
 * is a bare {@code ByteArray}:
 * <pre>
 * Constr(0, [ByteArray(txHash), Int(outputIndex)])
 * </pre>
 *
 * @deprecated Stdlib v1 is legacy. Prefer {@link OutputReference} for stdlib v2+ blueprints.
 * @see OutputReference
 */
@Deprecated
public final class OutputReferenceV1 implements Data<OutputReferenceV1> {

    private final byte[] transactionId;
    private final BigInteger outputIndex;

    public OutputReferenceV1(byte[] transactionId, BigInteger outputIndex) {
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId cannot be null").clone();
        this.outputIndex = Objects.requireNonNull(outputIndex, "outputIndex cannot be null");
    }

    public static OutputReferenceV1 of(byte[] transactionId, BigInteger outputIndex) {
        return new OutputReferenceV1(transactionId, outputIndex);
    }

    /**
     * Deserializes a {@link ConstrPlutusData} back into an {@link OutputReferenceV1}.
     * Expects constructor index 0 with two fields: a nested {@code TransactionId}
     * constructor (index 0, single ByteArray field) and an output_index integer.
     */
    public static OutputReferenceV1 fromPlutusData(ConstrPlutusData constr) {
        ConstrPlutusData txIdConstr = (ConstrPlutusData) constr.getData().getPlutusDataList().get(0);
        byte[] txId = ((BytesPlutusData) txIdConstr.getData().getPlutusDataList().get(0)).getValue();
        BigInteger idx = ((BigIntPlutusData) constr.getData().getPlutusDataList().get(1)).getValue();
        return new OutputReferenceV1(txId, idx);
    }

    public byte[] getTransactionId() {
        return transactionId.clone();
    }

    public BigInteger getOutputIndex() {
        return outputIndex;
    }

    /**
     * Serializes to the stdlib v1 nested layout:
     * {@code Constr(0, [Constr(0, [ByteArray(txHash)]), Int(outputIndex)])}.
     */
    @Override
    public ConstrPlutusData toPlutusData() {
        PlutusData txIdBytes = BytesPlutusData.of(transactionId);
        ConstrPlutusData txIdConstr = ConstrPlutusData.of(0, txIdBytes);
        PlutusData idx = BigIntPlutusData.of(outputIndex);
        return ConstrPlutusData.of(0, txIdConstr, idx);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OutputReferenceV1 that)) return false;
        return Arrays.equals(transactionId, that.transactionId) && outputIndex.equals(that.outputIndex);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(transactionId) + outputIndex.hashCode();
    }

    @Override
    public String toString() {
        return "OutputReferenceV1";
    }
}
