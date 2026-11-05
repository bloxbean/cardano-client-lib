package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.address.util.AddressUtil;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.spec.Script;
import com.bloxbean.cardano.client.transaction.raw.RawOutput;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder=true)
public class TransactionOutput {
    private static final int INDEFINITE_ARRAY = 0x9f;
    private static final int DEFINITE_PAIR = 0x82;

    private String address;
    private Value value;
    private byte[] datumHash;

    //transaction_output = [address, amount : value, ? datum_hash : $hash32]

    //babbage
    private PlutusData inlineDatum;
    private byte[] scriptRef;

    //The inline datum's original tag-24 bytes, not part of the value of this output
    @Getter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private final DecodedDatum decodedDatum = new DecodedDatum();

    //TODO -- Remove later if not required
    //serialize to Alonzo era (legacy) format
    //boolean alonzoEraFormat;

    public TransactionOutput(String address, Value value) {
        this.address = address;
        this.value = value;
    }

    /**
     * @return a shallow copy that writes the same bytes, as {@code toBuilder().build()} would not for a decoded datum
     */
    public TransactionOutput copy() {
        TransactionOutput copy = toBuilder().build();
        copy.decodedDatum.datum = decodedDatum.datum;
        copy.decodedDatum.bytes = decodedDatum.bytes;
        copy.decodedDatum.mapsThatLostPairs = decodedDatum.mapsThatLostPairs;
        return copy;
    }

    //custom setters
    public void setScriptRef(byte[] scriptRef) {
        this.scriptRef = scriptRef;
    }

    //custom setters
    public void setScriptRef(Script script) {
        if (script == null)
            return;

        try {
            this.scriptRef = script.scriptRefBytes();
        } catch (CborSerializationException e) {
            throw new CborRuntimeException(e);
        }
    }

    public DataItem serialize() throws CborSerializationException, AddressExcepion {
        return serialize(Value::serialize);
    }

    /**
     * Serialize with the value's assets in the order the Haskell ledger writes, see
     * {@link Value#serializeInLedgerOrder()}
     */
    public DataItem serializeInLedgerOrder() throws CborSerializationException, AddressExcepion {
        return serialize(Value::serializeInLedgerOrder);
    }

    private DataItem serialize(Function<Value, DataItem> valueWriter)
            throws CborSerializationException, AddressExcepion {
// TODO -- Remove later if not required.
//        if (alonzoEraFormat) {
//            if (datum == null && scriptRef == null)
//                return serializeAlonzo();
//            else
//                throw new CborSerializationException("Legacy format (Alonzo era) can't have datum or script_ref value set");
//
//        }
        //For now, serialize to legacy format if inlineDatum and scriptRef are not set
        if (inlineDatum == null && scriptRef == null)
            return serializeAlonzo(valueWriter);
        else {
            return serializePostAlonzo(valueWriter);
        }
    }

    private Map serializePostAlonzo(Function<Value, DataItem> valueWriter) throws AddressExcepion, CborSerializationException {
        Map map = new Map();

        byte[] addressByte = AddressUtil.addressToBytes(address);
        map.put(new UnsignedInteger(0), new ByteString(addressByte));

        if(value == null)
            throw new CborSerializationException("Value cannot be null");
        map.put(new UnsignedInteger(1), valueWriter.apply(value));

        if (datumHash != null && inlineDatum != null)
            throw new CborSerializationException("Only one can be set. datumHash or datum");

        Array datumArray = new Array();
        if(datumHash != null) {
            datumArray.add(new UnsignedInteger(0));
            datumArray.add(new ByteString(datumHash));
            map.put(new UnsignedInteger(2), datumArray);
        } else if(inlineDatum != null) {
            ByteString inlineDatumBS = new ByteString(inlineDatumBytes());
            inlineDatumBS.setTag(new Tag(24));
            datumArray.add(new UnsignedInteger(1));
            datumArray.add(inlineDatumBS);
            map.put(new UnsignedInteger(2), datumArray);
        }

        if (scriptRef != null) {
            ByteString scriptRefBS = new ByteString(scriptRefBytes());
            scriptRefBS.setTag(new Tag(24)); //tag 6.24
            map.put(new UnsignedInteger(3), scriptRefBS);
        }

        return map;
    }

    /**
     * The datum's original bytes while it is the object decoded from them and still represents them, as Haskell keeps
     * them, or else its encoding (ADR 0002 D6.3).
     */
    private byte[] inlineDatumBytes() throws CborSerializationException {
        try {
            if (decodedDatum.datum == inlineDatum && decodedDatum.bytes != null
                    && PlutusData.representsBytes(inlineDatum, decodedDatum.bytes, decodedDatum.mapsThatLostPairs))
                return decodedDatum.bytes;
            return CborSerializationUtil.serialize(inlineDatum.serialize());
        } catch (CborException | CborDeserializationException e) {
            throw new CborSerializationException("Cbor serialization error", e);
        }
    }

    /**
     * A script reference whose outer array is indefinite is written with a definite one and its inner bytes kept, as
     * Haskell re-encodes it (ADR 0002 D6.2). The field keeps what it holds, and anything else is written as it is.
     */
    private byte[] scriptRefBytes() {
        if (scriptRef.length == 0 || (scriptRef[0] & 0xff) != INDEFINITE_ARRAY)
            return scriptRef;
        List<CborSpan> items;
        try {
            items = CborSpan.of(scriptRef).items();
        } catch (CborRuntimeException e) {
            return scriptRef;
        }
        if (items.size() != 2)
            return scriptRef;
        ByteArrayOutputStream definite = new ByteArrayOutputStream(scriptRef.length);
        definite.write(DEFINITE_PAIR);
        definite.writeBytes(items.get(0).bytes());
        definite.writeBytes(items.get(1).bytes());
        return definite.toByteArray();
    }

    private Array serializeAlonzo(Function<Value, DataItem> valueWriter) throws CborSerializationException, AddressExcepion {
        Array array = new Array();
        byte[] addressByte = AddressUtil.addressToBytes(address);
        array.add(new ByteString(addressByte));

        if(value == null)
            throw new CborSerializationException("Value cannot be null");
        array.add(valueWriter.apply(value));

        if(datumHash != null)
            array.add(new ByteString(datumHash));

        return array;
    }

    public static TransactionOutput deserialize(DataItem dataItem) throws CborDeserializationException {
        try {
            return from(RawOutput.lenient(RawModels.span(dataItem)));
        } catch (CborRuntimeException e) {
            throw new CborDeserializationException("TransactionOutput deserialization failed", e);
        }
    }

    static TransactionOutput from(RawOutput raw) throws CborDeserializationException {
        TransactionOutput output = new TransactionOutput();
        Optional<byte[]> address = raw.addressBytes();
        if (address.isPresent()) {
            try {
                output.setAddress(AddressUtil.bytesToAddress(address.get()));
            } catch (Exception e) {
                throw new CborDeserializationException("Bytes cannot be converted to bech32 address", e);
            }
        }
        output.setValue(raw.amount().map(Value::from).orElse(null));
        output.setDatumHash(raw.datumHash().orElse(null));
        Optional<byte[]> inlineDatum = raw.inlineDatumBytes();
        if (inlineDatum.isPresent()) {
            output.inlineDatum = PlutusData.deserialize(inlineDatum.get());
            output.decodedDatum.datum = output.inlineDatum;
            output.decodedDatum.bytes = inlineDatum.get();
            output.decodedDatum.mapsThatLostPairs = PlutusData.mapsThatLostPairs(output.inlineDatum);
        }
        raw.scriptRefBytes().ifPresent(scriptRef -> output.setScriptRef(scriptRef));
        return output;
    }

    @Override
    public String toString() {
        return "TransactionOutput{" +
                "address='" + address + '\'' +
                ", value=" + value +
                ", datumHash=" + (datumHash == null? null : HexUtil.encodeHexString(datumHash)) +
                ", inlineDatum=" + (inlineDatum == null? null: inlineDatum.toString()) +
                ", scriptRef=" + (scriptRef == null? null: scriptRef.toString()) +
                '}';
    }

    public static class TransactionOutputBuilder {
        public TransactionOutputBuilder scriptRef(byte[] scriptRef) {
            this.scriptRef = scriptRef;
            return this;
        }

        public TransactionOutputBuilder scriptRef(Script script) {
            if (script == null)
                return this;

            try {
                this.scriptRef = script.scriptRefBytes();
            } catch (CborSerializationException e) {
                throw new CborRuntimeException(e);
            }

            return this;
        }
    }

    private static final class DecodedDatum {
        private PlutusData datum;
        private byte[] bytes;
        private List<?> mapsThatLostPairs;
    }
}
