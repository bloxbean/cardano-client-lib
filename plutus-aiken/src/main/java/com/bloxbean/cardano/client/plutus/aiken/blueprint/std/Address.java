package com.bloxbean.cardano.client.plutus.aiken.blueprint.std;

import com.bloxbean.cardano.client.plutus.blueprint.model.Data;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

import java.util.Objects;
import java.util.Optional;

/**
 * Shared representation of the CIP-57 Address schema.
 */
public final class Address implements Data<Address> {

    private final Credential paymentCredential;
    private final Optional<ReferencedCredential> stakeCredential;

    public Address(Credential paymentCredential, Optional<ReferencedCredential> stakeCredential) {
        this.paymentCredential = Objects.requireNonNull(paymentCredential, "paymentCredential cannot be null");
        this.stakeCredential = Objects.requireNonNull(stakeCredential, "stakeCredential cannot be null");
    }

    public Credential getPaymentCredential() {
        return paymentCredential;
    }

    public Optional<ReferencedCredential> getStakeCredential() {
        return stakeCredential;
    }

    @Override
    public ConstrPlutusData toPlutusData() {
        PlutusData payment = paymentCredential.toPlutusData();
        PlutusData stake = stakeCredential
                .map(ReferencedCredential::toPlutusData)
                .map(data -> ConstrPlutusData.of(0, data))
                .orElseGet(() -> ConstrPlutusData.of(1));

        return ConstrPlutusData.of(0, payment, stake);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Address)) return false;
        Address address = (Address) o;

        return paymentCredential.equals(address.paymentCredential) && stakeCredential.equals(address.stakeCredential);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paymentCredential, stakeCredential);
    }

    @Override
    public String toString() {
        return "Address";
    }

}
