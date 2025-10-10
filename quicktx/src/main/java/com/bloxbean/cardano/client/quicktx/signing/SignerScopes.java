package com.bloxbean.cardano.client.quicktx.signing;

/**
 * Known signer scope identifiers used across QuickTx.
 * Values are stored in normalized lower-case form.
 */
public final class SignerScopes {
    public static final String PAYMENT = "payment";
    public static final String STAKE = "stake";
    public static final String DREP = "drep";
    public static final String COMMITTEE_COLD = "committeecold";
    public static final String COMMITTEE_HOT = "committeehot";
    public static final String POLICY = "policy";

    private SignerScopes() {
    }
}
