package com.bloxbean.cardano.client.quicktx.verifiers;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Verifier;

import java.util.function.Predicate;

/**
 * Helper class to create verifiers
 */
public class TxVerifiers {

    /**
     * Verifier to verify the output amount for a given address
     * @param address address to verify
     * @param amount amount to verify
     * @return Verifier
     */
    public static Verifier outputAmountVerifier(String address, Amount amount) {
        return new OutputAmountVerifier(address, amount, null);
    }

    /**
     * Verifier to verify the output amount for a given address
     * @param address address to verify
     * @param amount amount to verify
     * @param customMsg custom message to throw in case of verification failure
     * @return Verifier
     */
    public static Verifier outputAmountVerifier(String address, Amount amount, String customMsg) {
        return new OutputAmountVerifier(address, amount, customMsg);
    }

    /**
     * Creates a verifier to validate the output amount for a specific address and unit with the given condition.
     *
     * @param address the address for which the output amount is to be verified
     * @param unit the unit of the output amount to be verified (e.g., lovelace, asset unit)
     * @param condition a predicate defining the condition that the amount must satisfy
     * @return an instance of Verifier to validate the output amount based on the provided parameters
     */
    public static Verifier outputAmountVerifier(String address, String unit, Predicate<Amount> condition) {
        return new OutputAmountVerifier(address, unit, condition, null);
    }
}
