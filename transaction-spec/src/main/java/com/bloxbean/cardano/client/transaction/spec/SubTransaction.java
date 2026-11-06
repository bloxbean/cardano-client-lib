package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.raw.RawAuxData;
import com.bloxbean.cardano.client.transaction.raw.RawTx;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Optional;

import static com.bloxbean.cardano.client.transaction.util.SerializationUtil.requireDijkstra;

/**
 * A sub-transaction of a Dijkstra body (key 23),
 * {@code [sub_transaction_body, transaction_witness_set, auxiliary_data / nil]}. Its body is a {@link TransactionBody}
 * without a fee, collateral, a collateral return, total collateral, sub-transactions or starting intervals: writing one
 * with any of them is an error. Its id is the hash of its body.
 * <p>
 * Experimental: Dijkstra is not final; this may change in any release.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubTransaction {
    @Builder.Default
    private TransactionBody body = new TransactionBody();

    @Builder.Default
    private TransactionWitnessSet witnessSet = new TransactionWitnessSet();

    private AuxiliaryData auxiliaryData;

    /**
     * Writes the sub-transaction, which exists only in Dijkstra. If it has auxiliary data and no aux data hash, the
     * hash is set on its body.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @param era the era to write it in
     * @return {@code [body, witnesses, aux / nil]}
     * @throws CborSerializationException if the era is before Dijkstra, it cannot be written, or its body has a field
     *                                    of the top level only
     */
    public Array serialize(Era era) throws CborSerializationException {
        requireDijkstra(era, "SubTransaction");
        return Transaction.serialize(body, true, witnessSet, auxiliaryData, null, era);
    }

    static SubTransaction from(RawTx raw, Era era) throws CborDeserializationException {
        SubTransaction subTransaction = new SubTransaction();
        subTransaction.setBody(TransactionBody.from(raw.body(era)));
        subTransaction.setWitnessSet(TransactionWitnessSet.from(raw.witnessSet(era)));
        Optional<RawAuxData> auxiliaryData = raw.auxData(era);
        if (auxiliaryData.isPresent())
            subTransaction.setAuxiliaryData(AuxiliaryData.from(auxiliaryData.get()));
        return subTransaction;
    }
}
