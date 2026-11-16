package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.EraSerializationConfig;
import com.bloxbean.cardano.client.transaction.raw.RawAuxData;
import com.bloxbean.cardano.client.transaction.raw.RawTx;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import static com.bloxbean.cardano.client.transaction.util.SerializationUtil.isDijkstra;
import static com.bloxbean.cardano.client.transaction.util.SerializationUtil.requireBeforeDijkstra;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Slf4j
public class Transaction {
    private Era era;

    @Builder.Default
    private TransactionBody body = new TransactionBody();

    @Builder.Default
    private TransactionWitnessSet witnessSet = new TransactionWitnessSet();

    @Builder.Default
    private boolean isValid = true;
    private AuxiliaryData auxiliaryData;

    /**
     * Set metadata
     * @deprecated
     * This method should not be used.
     * <p> Use AuxiliaryData#setMetadata(Metadata) (Metadata)} instead</p>
     * @param metadata
     */
    @Deprecated
    public void setMetadata(Metadata metadata) {
        if (metadata == null)
            return;

        if (auxiliaryData == null)
            this.auxiliaryData = new AuxiliaryData();

        auxiliaryData.setMetadata(metadata);
    }

    /**
     * Get metadata
     * @deprecated
     * This method should not be used.
     *  <p> Use AuxiliaryData#getMetadata() instead</p>
     * @return
     */
    @Deprecated
    @JsonIgnore
    public Metadata getMetadata() {
        if (auxiliaryData != null)
            return auxiliaryData.getMetadata();
        else
            return null;
    }

    public byte[] serialize() throws CborSerializationException {
        return serialize(era != null? era : EraSerializationConfig.INSTANCE.getEra());
    }

    /**
     * Writes the transaction in an era. Before Dijkstra it is {@code [body, witnesses, isValid, aux / nil]}; from
     * Dijkstra {@code [body, witnesses, aux / nil]}, as a node submits and relays one, and a transaction marked invalid
     * cannot be written. A field the era has no place for is an error, not dropped (ADR 0004 D1). If the transaction
     * has auxiliary data and its body no aux data hash, the hash is set on the body.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @param era the era to write it in
     * @return the transaction
     * @throws CborSerializationException if it cannot be written in the era
     */
    public byte[] serialize(Era era) throws CborSerializationException {
        try {
            if (!isValid)
                requireBeforeDijkstra(era, "A transaction marked invalid");
            //Any sorting requirement in Map is handled in that serialization() method if SortedMap is used
            return CborSerializationUtil.serialize(serialize(body, false, witnessSet, auxiliaryData, isValid, era));
        } catch (Exception e) {
            throw new CborSerializationException("CBOR Serialization failed", e);
        }
    }

    /**
     * {@code [body, witnesses, aux / nil]}, with the validity flag third before Dijkstra when there is one: of a
     * transaction, or with no flag, of a sub-transaction.
     */
    static Array serialize(TransactionBody body, boolean subTransaction, TransactionWitnessSet witnessSet,
                           AuxiliaryData auxiliaryData, Boolean isValid, Era era) throws CborSerializationException {
        try {
            if (auxiliaryData != null && body.getAuxiliaryDataHash() == null) {
                byte[] auxiliaryDataHash = auxiliaryData.getAuxiliaryDataHash(era);
                body.setAuxiliaryDataHash(auxiliaryDataHash);
            }

            Array array = new Array();
            array.add(body.serialize(era, subTransaction));

            //witness
            array.add(witnessSet != null ? witnessSet.serialize(era) : new Map());

            if (isValid != null && !isDijkstra(era))
                array.add(isValid ? SimpleValue.TRUE : SimpleValue.FALSE);

            //Auxiliary Data
            array.add(auxiliaryData != null ? auxiliaryData.serialize(era) : SimpleValue.NULL);
            return array;
        } catch (AddressExcepion e) {
            throw new CborSerializationException("CBOR Serialization failed", e);
        }
    }

    public String serializeToHex() throws CborSerializationException {
        try {
            byte[] bytes = serialize();
            return HexUtil.encodeHexString(bytes);
        } catch (Exception ex) {
            throw new CborSerializationException("CBOR serialization exception", ex);
        }
    }

    /**
     * Decodes a transaction: {@code [body, witnesses, aux / null]} (any era, valid) or
     * {@code [body, witnesses, isValid, aux / null]}, with aux data in any shape: a metadata map (Shelley),
     * {@code [metadata, [* native_script]]} (Allegra, Mary) or {@code #6.259({...})} (Alonzo on). Decoding is iterative,
     * so content of any nesting depth decodes on any thread. A record (the body, the witness set, an output in map form,
     * the tag-259 aux data) that repeats a key is rejected, as the ledger rejects it.
     * <p>
     * The model keeps one entry per map key in Plutus data and metadata, and serializes in CCL's encoding, so a received
     * transaction may re-encode to other bytes. Take its hashes from the original bytes with {@link RawTx}.
     * <p>
     * The fields are read as Conway reads them, and the era is left unset, unless the bytes have a field or form only
     * a later era has: then they are read in the earliest era that has a place for all of them, as
     * {@link #deserialize(byte[], Era)} reads them, and that era is set ({@link RawTx#eraOf}). A Dijkstra transaction
     * with a direct deposit, say, keeps it. A form only a later era allows that its tables do not show, an
     * indefinite-length record, is rejected as Conway rejects it: read such bytes with {@link #deserialize(byte[], Era)}
     * (ADR 0004 D5a). Experimental: Dijkstra is not final; this may change in any release.
     *
     * @param bytes the transaction
     * @return the transaction
     * @throws CborDeserializationException if the bytes are not a well-formed Shelley-family transaction
     */
    public static Transaction deserialize(byte[] bytes) throws CborDeserializationException {
        Era era;
        try {
            era = RawTx.eraOf(bytes, RawModels.READ_IN);
        } catch (RuntimeException e) {
            throw new CborDeserializationException("CBOR deserialization failed", e);
        }
        return deserialize(bytes, era, era != RawModels.READ_IN);
    }

    /**
     * Decodes a transaction as {@link #deserialize(byte[])} does, with its fields read in an era, and sets the era, so
     * that {@link #serialize()} writes it in the same era. In Dijkstra it also reads the transaction form of a block,
     * {@code [body, witnesses, aux / null, isValid]}, the Dijkstra fields and Plutus V4 scripts.
     * <p>
     * A model keeps no original bytes, so writing it again can give another body hash: take a received transaction's
     * id from its original bytes with {@link RawTx} (ADR 0004 D5).
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @param bytes the transaction
     * @param era   the era to read it in
     * @return the transaction, with the era set
     * @throws CborDeserializationException if the bytes are not a well-formed Shelley-family transaction
     */
    public static Transaction deserialize(byte[] bytes, Era era) throws CborDeserializationException {
        return deserialize(bytes, era, true);
    }

    private static Transaction deserialize(byte[] bytes, Era era, boolean setEra) throws CborDeserializationException {
        try {
            RawTx raw = RawTx.lenient(bytes);

            Transaction transaction = new Transaction();
            if (setEra)
                transaction.setEra(era);
            transaction.setValid(raw.isValid());
            transaction.setWitnessSet(TransactionWitnessSet.from(raw.witnessSet(era)));
            Optional<RawAuxData> auxiliaryData = raw.auxData(era);
            if (auxiliaryData.isPresent())
                transaction.setAuxiliaryData(AuxiliaryData.from(auxiliaryData.get()));
            transaction.setBody(TransactionBody.from(raw.body(era)));
            return transaction;
        } catch (Exception e) {
            throw new CborDeserializationException("CBOR deserialization failed", e);
        }
    }

    public String toJson() {
        return JsonUtil.getPrettyJson(this);
    }

    // This customization is not required after metadata (deprecated) method is removed in future release.
    public static class TransactionBuilder {
        private AuxiliaryData auxiliaryData;

        /**
         * Set metadata
         * @deprecated
         * This method should not be used
         * <p> Use TransactionBuilder#auxiliaryData(AuxiliaryData) (AuxiliaryData)} instead</p>
         * @param metadata
         * @return
         */
        @Deprecated
        public TransactionBuilder metadata(Metadata metadata) {
            if (metadata == null)
                return this;

            if (auxiliaryData == null)
                auxiliaryData = new AuxiliaryData();

            auxiliaryData.setMetadata(metadata);

            return this;
        }
    }
}
