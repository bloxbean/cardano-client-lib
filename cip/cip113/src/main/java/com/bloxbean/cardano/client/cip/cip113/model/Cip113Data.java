package com.bloxbean.cardano.client.cip.cip113.model;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.address.CredentialType;
import com.bloxbean.cardano.client.cip.cip113.Cip113Exception;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;

import java.math.BigInteger;
import java.util.List;

/**
 * Encoding helpers shared by the CIP-113 datum and redeemer types.
 *
 * <p>TODO replace these hand-written codecs with types generated from the vendored
 * {@code plutus.json} via {@code @Blueprint}. The processor supports sum types, mixed
 * constructor arities and {@code fromPlutusData} decoders, so this whole package becomes
 * generated code — at which point a blueprint change breaks compilation instead of
 * producing silently invalid CBOR. Hand-written for now to keep the first spike moving.</p>
 */
public final class Cip113Data {

    private Cip113Data() {}

    /** {@code Credential}: VerificationKey = constructor 0, Script = constructor 1. */
    public static ConstrPlutusData credential(Credential credential) {
        int alt = credential.getType() == CredentialType.Script ? 1 : 0;
        return ConstrPlutusData.of(alt, BytesPlutusData.of(credential.getBytes()));
    }

    public static Credential toCredential(PlutusData data) {
        ConstrPlutusData c = asConstr(data, "Credential");
        byte[] hash = bytes(field(c, 0));
        if (c.getAlternative() == 0) return Credential.fromKey(hash);
        if (c.getAlternative() == 1) return Credential.fromScript(hash);
        throw new Cip113Exception("Unexpected Credential constructor " + c.getAlternative());
    }

    // ------------------------------------------------------------- accessors

    public static ConstrPlutusData asConstr(PlutusData data, String what) {
        if (!(data instanceof ConstrPlutusData)) {
            throw new Cip113Exception("Expected a constructor for " + what
                    + " but got " + (data == null ? "null" : data.getClass().getSimpleName()));
        }
        return (ConstrPlutusData) data;
    }

    /**
     * A product type with a known constructor index and arity.
     *
     * <p>Checking both matters: a datum with the right field count but the wrong constructor is a
     * different type entirely, and one with extra trailing fields is a later schema version whose
     * meaning we do not know. Either would otherwise decode into a plausible-looking object.</p>
     */
    public static ConstrPlutusData asConstr(PlutusData data, String what, int alternative, int arity) {
        ConstrPlutusData constr = asConstr(data, what);
        if (constr.getAlternative() != alternative) {
            throw new Cip113Exception(what + " must be constructor " + alternative
                    + " but was " + constr.getAlternative());
        }
        int actual = constr.getData().getPlutusDataList().size();
        if (actual != arity) {
            throw new Cip113Exception(what + " must have exactly " + arity + " fields but had "
                    + actual + ". Field order and arity are load-bearing — check the datum against"
                    + " plutus.json.");
        }
        return constr;
    }

    public static PlutusData field(ConstrPlutusData constr, int index) {
        List<PlutusData> fields = constr.getData().getPlutusDataList();
        if (index >= fields.size()) {
            throw new Cip113Exception("Constructor " + constr.getAlternative()
                    + " has " + fields.size() + " fields; wanted index " + index
                    + ". Field order is load-bearing — check the datum against plutus.json.");
        }
        return fields.get(index);
    }

    public static byte[] bytes(PlutusData data) {
        if (!(data instanceof BytesPlutusData)) {
            throw new Cip113Exception("Expected bytes but got "
                    + (data == null ? "null" : data.getClass().getSimpleName()));
        }
        return ((BytesPlutusData) data).getValue();
    }

    public static String hex(PlutusData data) {
        return HexUtil.encodeHexString(bytes(data));
    }

    public static BigInteger integer(PlutusData data) {
        if (!(data instanceof BigIntPlutusData)) {
            throw new Cip113Exception("Expected an integer but got "
                    + (data == null ? "null" : data.getClass().getSimpleName()));
        }
        return ((BigIntPlutusData) data).getValue();
    }

    public static ListPlutusData list(List<PlutusData> items) {
        ListPlutusData l = ListPlutusData.builder().build();
        items.forEach(l::add);
        return l;
    }

    public static BytesPlutusData bytesOfHex(String hex) {
        return BytesPlutusData.of(HexUtil.decodeHexString(hex));
    }

    public static BigIntPlutusData i(int value) {
        return BigIntPlutusData.of(value);
    }
}
