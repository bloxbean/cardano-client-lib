package com.bloxbean.cardano.client.programmabletoken.cip113.model;

import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import lombok.Value;

/**
 * Datum of the issuance-template UTxO: the prefix and postfix of the {@code issuance_mint}
 * script with its parameter hole punched out.
 *
 * <p>Constructor 0, two fields. Not exposed by any validator's top-level schema — it comes
 * from {@code lib/types.ak} — which is why this one stays hand-written even after the rest
 * of this package is generated. Its token name is {@code "IssuanceCborHex"}.</p>
 */
@Value
public class IssuanceCborHex {
    byte[] prefixCborHex;
    byte[] postfixCborHex;

    public static IssuanceCborHex fromPlutusData(PlutusData data) {
        ConstrPlutusData c = Cip113Data.asConstr(data, "IssuanceCborHex", 0, 2);
        return new IssuanceCborHex(
                Cip113Data.bytes(Cip113Data.field(c, 0)),
                Cip113Data.bytes(Cip113Data.field(c, 1)));
    }
}
