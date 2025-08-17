package com.bloxbean.cardano.client.dsl.groovy

import com.bloxbean.cardano.client.api.model.Amount

/**
 * Groovy extensions for natural amount syntax
 * These are just syntactic sugar on top of the existing Amount class methods
 * Enables syntax like: 5.ada, 100.asset("unit")
 */
class AmountExtensions {
    
    /**
     * Extension method for Number to create ADA amounts
     * Example: 5.ada or 5.5.ada
     * Delegates to Amount.ada()
     */
    static Amount getAda(Number self) {
        return Amount.ada(self.doubleValue())
    }
    
    /**
     * Extension method for Number to create lovelace amounts
     * Example: 5000000.lovelace
     * Delegates to Amount.lovelace()
     */
    static Amount getLovelace(Number self) {
        return Amount.lovelace(BigInteger.valueOf(self.longValue()))
    }
    
    /**
     * Extension method for Number to create asset amounts using full unit
     * Example: 100.asset("policyId.tokenNameHex")
     * Delegates to Amount.asset(unit, quantity)
     */
    static Amount asset(Number self, String unit) {
        return Amount.asset(unit, self.longValue())
    }
    
    /**
     * Extension method for creating asset with separate policy and name
     * Example: 100.asset("policyId", "TokenName")
     * Delegates to Amount.asset(policy, assetName, quantity)
     */
    static Amount asset(Number self, String policyId, String assetName) {
        return Amount.asset(policyId, assetName, self.longValue())
    }
}