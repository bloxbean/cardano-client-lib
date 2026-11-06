package com.bloxbean.cardano.client.transaction.util;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.address.util.AddressUtil;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusV1Script;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.plutus.spec.PlutusV3Script;
import com.bloxbean.cardano.client.plutus.spec.PlutusV4Script;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.Script;
import com.bloxbean.cardano.client.transaction.raw.RawAnchor;
import com.bloxbean.cardano.client.transaction.raw.RawCertificate;
import com.bloxbean.cardano.client.transaction.raw.RawCredential;
import com.bloxbean.cardano.client.transaction.raw.RawDRep;
import com.bloxbean.cardano.client.transaction.raw.RawDatum;
import com.bloxbean.cardano.client.transaction.raw.RawItems;
import com.bloxbean.cardano.client.transaction.raw.RawScript;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeCredential;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns the raw views of {@code ledger-cbor} into this module's models. The views cannot do it, as no model is on their
 * classpath (ADR 0003 Q2).
 */
public final class RawModels {
    /**
     * The era the models read a transaction in, which gives its fields their meanings: body key 14 is the required
     * signers, and not what it means after Conway, and parameter update key 17 is the coins per UTxO byte.
     */
    public static final Era READ_IN = Era.Conway;

    private RawModels() {
    }

    /**
     * Turns a view into a model.
     *
     * @param <R> the view
     * @param <M> the model
     */
    public interface Mapper<R, M> {
        M map(R raw) throws CborDeserializationException;
    }

    /**
     * @param raw    views
     * @param mapper what turns one into a model
     * @return the models, in order
     * @throws CborDeserializationException if the mapper rejects a view
     */
    public static <R, M> List<M> mapAll(List<R> raw, Mapper<R, M> mapper) throws CborDeserializationException {
        List<M> models = new ArrayList<>(raw.size());
        for (R item : raw)
            models.add(mapper.map(item));
        return models;
    }

    /**
     * Decodes the data. The model keeps one entry per map key, and re-encoding it may give other bytes; hash with
     * {@link RawDatum#hash()}.
     *
     * @param datum the datum as received
     * @return the data
     * @throws CborDeserializationException if the bytes are not Plutus data
     */
    public static PlutusData toPlutusData(RawDatum datum) throws CborDeserializationException {
        return PlutusData.deserialize(datum.span().bytes());
    }

    /**
     * Decodes the script. Re-encoding a native script model may give other bytes than were received; hash with
     * {@link RawScript#hash()}.
     *
     * @param script the script as received
     * @return a {@link NativeScript}, {@link PlutusV1Script}, {@link PlutusV2Script}, {@link PlutusV3Script} or
     * {@link PlutusV4Script} (experimental: Dijkstra is not final; this may change in any release)
     * @throws CborDeserializationException if the bytes are not a script of this type
     */
    public static Script toScript(RawScript script) throws CborDeserializationException {
        if (script.type() == 0) {
            if (script.span().majorType() != 4)
                throw new CborDeserializationException("Expected a native script at offset " + script.span().offset());
            return NativeScript.deserialize((Array) CborSerializationUtil.deserialize(script.span()));
        }
        if (script.span().majorType() != 2)
            throw new CborDeserializationException(
                    "Expected a Plutus script byte string at offset " + script.span().offset());
        ByteString bytes = (ByteString) CborSerializationUtil.deserialize(script.span());
        switch (script.type()) {
            case 1:
                return PlutusV1Script.deserialize(bytes);
            case 2:
                return PlutusV2Script.deserialize(bytes);
            case 3:
                return PlutusV3Script.deserialize(bytes);
            default:
                return PlutusV4Script.deserialize(bytes);
        }
    }

    /**
     * Decodes the metadata. The model keeps one entry per map key, and re-encoding it may give other bytes.
     *
     * @param metadata the metadata as received, a map
     * @return the metadata
     * @throws com.bloxbean.cardano.client.metadata.exception.MetadataDeSerializationException if a value is not a
     *                                                                                         metadatum
     */
    public static CBORMetadata toMetadata(CborSpan metadata) {
        return CBORMetadata.deserialize((Map) CborSerializationUtil.deserialize(metadata));
    }

    /**
     * Checks a certificate that existed before Conway as its model does, reporting a wrong tag or number of items as
     * that model's checked exception.
     *
     * @param certificate a certificate as received
     * @return the certificate
     * @throws CborDeserializationException if its tag or number of items is not its kind's
     */
    public static RawCertificate exact(RawCertificate certificate) throws CborDeserializationException {
        try {
            return certificate.exact();
        } catch (CborRuntimeException e) {
            throw new CborDeserializationException(certificate.kind() + " deserialization failed", e);
        }
    }

    /**
     * @param credential a credential as received
     * @return the credential
     */
    public static Credential credential(RawCredential credential) {
        return credential.isScript() ? Credential.fromScript(credential.hash()) : Credential.fromKey(credential.hash());
    }

    /**
     * @param credential a credential as received
     * @return the stake credential
     */
    public static StakeCredential stakeCredential(RawCredential credential) {
        return credential.isScript() ? StakeCredential.fromScriptHash(credential.hash())
                : StakeCredential.fromKeyHash(credential.hash());
    }

    /**
     * @param anchor an anchor as received
     * @return the anchor, or null if it is empty
     * @throws CborRuntimeException if it has an indefinite length, which this module's models reject
     */
    public static Anchor anchor(Optional<RawAnchor> anchor) {
        return anchor.map(RawModels::anchor).orElse(null);
    }

    /**
     * @param anchor an anchor as received
     * @return the anchor
     * @throws CborRuntimeException if it has an indefinite length and was read without an era or before Dijkstra:
     *                              the ledger decodes one with {@code RecD} up to protocol version 11 (Conway)
     *                              ({@link RawItems#indefiniteRecordsAllowed(Era)})
     */
    public static Anchor anchor(RawAnchor anchor) {
        if (!RawItems.indefiniteRecordsAllowed(anchor.era()) && anchor.span().isIndefinite())
            throw new CborRuntimeException("Invalid anchor: expected a definite-length array");
        return new Anchor(anchor.url(), anchor.dataHash());
    }

    /**
     * @param rewardAccount a reward account's bytes
     * @return its bech32 address
     * @throws CborRuntimeException if the bytes are not a reward address
     */
    public static String rewardAddress(byte[] rewardAccount) {
        try {
            return AddressUtil.bytesToAddress(rewardAccount);
        } catch (Exception e) {
            throw new CborRuntimeException("Bytes cannot be converted to bech32 reward address", e);
        }
    }

    /**
     * @param drep a DRep as received
     * @return the DRep
     */
    public static DRep drep(RawDRep drep) {
        switch (drep.kind()) {
            case KEY_HASH:
                return DRep.addrKeyHash(drep.hash());
            case SCRIPT_HASH:
                return DRep.scriptHash(drep.hash());
            case ABSTAIN:
                return DRep.abstain();
            default:
                return DRep.noConfidence();
        }
    }
}
