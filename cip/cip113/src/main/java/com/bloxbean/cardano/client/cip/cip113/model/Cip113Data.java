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
 * <p><b>Why these are hand-written rather than generated.</b> {@code @Blueprint} can generate
 * these types from the vendored {@code plutus.json}, and the worry that motivates it is real: a
 * constructor index or field order that drifts from the blueprint still produces perfectly
 * well-formed CBOR, so the failure surfaces as a validator rejecting a transaction with no trace.
 * But generation would replace this module's public model — {@code RegistryNode} and friends,
 * Lombok values with builders, used throughout — with a marker interface plus per-constructor
 * variant classes, {@code impl/} data classes and separate {@code converter/} classes in a
 * generated sub-package. No module in this repository wires the annotation processor into a main
 * source set, so CIP-113 would be the first, and the benefit is drift-detection rather than a
 * better API.</p>
 *
 * <p>{@code BlueprintCodecAgreementTest} buys that drift-detection outright: it reads the same
 * vendored blueprint and asserts every constructor index, field name and field order these codecs
 * encode by hand. Re-vendoring a blueprint that changes shape fails the build. If the module ever
 * wants the generated types for their own sake, that test is the thing which makes the migration
 * safe rather than the thing it replaces.</p>
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
