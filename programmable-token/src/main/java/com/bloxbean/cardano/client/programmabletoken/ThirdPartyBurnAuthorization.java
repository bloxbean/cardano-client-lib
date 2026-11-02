package com.bloxbean.cardano.client.programmabletoken;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import lombok.Value;

/**
 * The two authorizations a third-party burn carries, kept distinct.
 *
 * <p>{@code thirdPartyRedeemer} reaches the token's third-party logic, which authorises acting on
 * a holder's smart wallet without the holder; {@code issuanceRedeemer} reaches the token's minting
 * logic, which authorises the negative mint. When a token uses one credential for both roles the
 * ledger carries a single withdrawal, so the two redeemers must then be equal.</p>
 *
 * <p>It carries no signer. Whatever key the token's third-party or minting logic checks (an admin
 * or power-user key, typically) is added by the caller with {@code withRequiredSigners(...)} and
 * signed for, exactly as for a seizure.</p>
 */
@Value(staticConstructor = "of")
public class ThirdPartyBurnAuthorization {
    PlutusData thirdPartyRedeemer;
    PlutusData issuanceRedeemer;
}
