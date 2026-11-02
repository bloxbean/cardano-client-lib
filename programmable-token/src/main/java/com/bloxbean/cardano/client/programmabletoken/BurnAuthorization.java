package com.bloxbean.cardano.client.programmabletoken;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import lombok.Value;

/** Keeps transfer and issuance authorization distinct for a programmable burn. */
@Value(staticConstructor = "of")
public class BurnAuthorization {
    PlutusData transferRedeemer;
    PlutusData issuanceRedeemer;
}
