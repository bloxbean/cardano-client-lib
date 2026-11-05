package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.EraSerializationConfig;
import com.bloxbean.cardano.client.transaction.raw.RawAuxData;
import com.bloxbean.cardano.client.transaction.raw.RawTx;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

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

    private byte[] serialize(Era era) throws CborSerializationException {
        try {
            if (auxiliaryData != null && body.getAuxiliaryDataHash() == null) {
                byte[] auxiliaryDataHash = auxiliaryData.getAuxiliaryDataHash(era);
                body.setAuxiliaryDataHash(auxiliaryDataHash);
            }

            Array array = new Array();
            Map bodyMap = body.serialize(era);
            array.add(bodyMap);

            //witness
            if (witnessSet != null) {
                Map witnessMap = witnessSet.serialize(era);
                array.add(witnessMap);
            } else {
                Map witnessMap = new Map();
                array.add(witnessMap);
            }

            if(isValid)
                array.add(SimpleValue.TRUE);
            else
                array.add(SimpleValue.FALSE);

            //Auxiliary Data
            if (auxiliaryData != null) {
                DataItem auxDataMap = auxiliaryData.serialize(era);
                array.add(auxDataMap);
            } else
                array.add(SimpleValue.NULL);

            //Any sorting requirement in Map is handled in that serialization() method if SortedMap is used
            return CborSerializationUtil.serialize(array);
        } catch (Exception e) {
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
     *
     * @param bytes the transaction
     * @return the transaction
     * @throws CborDeserializationException if the bytes are not a well-formed Shelley-family transaction
     */
    public static Transaction deserialize(byte[] bytes) throws CborDeserializationException {
        try {
            RawTx raw = RawTx.lenient(bytes);

            Transaction transaction = new Transaction();
            transaction.setValid(raw.isValid());
            transaction.setWitnessSet(TransactionWitnessSet.from(raw.witnessSet(TransactionBody.READ_IN)));
            Optional<RawAuxData> auxiliaryData = raw.auxData(TransactionBody.READ_IN);
            if (auxiliaryData.isPresent())
                transaction.setAuxiliaryData(AuxiliaryData.from(auxiliaryData.get()));
            transaction.setBody(TransactionBody.from(raw));
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
