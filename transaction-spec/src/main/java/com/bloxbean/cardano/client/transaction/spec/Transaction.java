package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.common.cbor.custom.EncodedKeyMap;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.EraSerializationConfig;
import com.bloxbean.cardano.client.transaction.raw.RawTx;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

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
            List<DataItem> dataItemList = CborSerializationUtil.deserializeAll(bytes);
            if (dataItemList.size() != 1 || !(dataItemList.get(0) instanceof Array))
                throw new CborDeserializationException("A transaction is one CBOR array");
            List<DataItem> txnItems = withoutBreak(((Array) dataItemList.get(0)).getDataItems());

            Transaction transaction = new Transaction();
            DataItem auxiliaryDataDI;
            if (txnItems.size() == 3) {
                transaction.setValid(true);
                auxiliaryDataDI = txnItems.get(2);
            } else if (txnItems.size() == 4) {
                DataItem isValidDI = txnItems.get(2);
                if (!SimpleValue.TRUE.equals(isValidDI) && !SimpleValue.FALSE.equals(isValidDI))
                    throw new CborDeserializationException("isValid must be a bool");
                transaction.setValid(SimpleValue.TRUE.equals(isValidDI));
                auxiliaryDataDI = txnItems.get(3);
            } else {
                throw new CborDeserializationException("A transaction has 3 or 4 items, found " + txnItems.size()
                        + (txnItems.size() == 2 ? " (Byron transactions are not supported)" : ""));
            }

            Map bodyDI = record(txnItems.get(0), "transaction body");
            DataItem outputs = bodyDI.get(new UnsignedInteger(1));
            if (outputs instanceof Array) {
                for (DataItem output : ((Array) outputs).getDataItems())
                    if (output instanceof Map)
                        record(output, "output");
            }
            DataItem collateralReturn = bodyDI.get(new UnsignedInteger(16));
            if (collateralReturn instanceof Map)
                record(collateralReturn, "collateral return");

            transaction.setWitnessSet(TransactionWitnessSet.deserialize(record(txnItems.get(1), "witness set")));
            if (auxiliaryDataDI instanceof Map) {
                if (auxiliaryDataDI.getTag() != null)
                    record(auxiliaryDataDI, "aux data");
                transaction.setAuxiliaryData(AuxiliaryData.deserialize((Map) auxiliaryDataDI));
            } else if (auxiliaryDataDI instanceof Array) {
                transaction.setAuxiliaryData(AuxiliaryData.deserialize((Array) auxiliaryDataDI));
            } else if (auxiliaryDataDI != SimpleValue.NULL) {
                throw new CborDeserializationException("Unknown aux data shape: " + auxiliaryDataDI.getMajorType());
            }
            transaction.setBody(TransactionBody.deserialize(bodyDI));
            return transaction;
        } catch (Exception e) {
            throw new CborDeserializationException("CBOR deserialization failed", e);
        }
    }

    // A CDDL record: a map that does not repeat a key (the decoder keeps a repeated key once, and notes it).
    private static Map record(DataItem item, String what) throws CborDeserializationException {
        if (!(item instanceof Map))
            throw new CborDeserializationException("The " + what + " is a map, found " + item.getMajorType());
        if (item instanceof EncodedKeyMap && ((EncodedKeyMap) item).hasRepeatedKey())
            throw new CborDeserializationException("The " + what + " repeats a key");
        return (Map) item;
    }

    // The items of an array, without the BREAK that ends an indefinite one.
    static List<DataItem> withoutBreak(List<DataItem> items) {
        int size = items.size();
        return size > 0 && items.get(size - 1) == Special.BREAK ? items.subList(0, size - 1) : items;
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
