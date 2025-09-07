package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import lombok.Getter;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight recorder that captures transaction building operations as intentions.
 * This is designed to be embedded directly in Tx/ScriptTx classes for optional recording.
 *
 * Much simpler than wrapper classes - just records intentions as methods are called.
 */
@Getter
public class TxRecorder {
    private final String planType;
    private final List<TxIntention> intentions = new ArrayList<>();
    private final Map<String, Object> variables = new HashMap<>();
    private boolean enabled = true;

    // Attributes that will be extracted from Tx state
    private String fromAddress;
    private String changeAddress;

    public TxRecorder(String planType) {
        this.planType = planType;
    }

    /**
     * Record a payment operation.
     */
    public void recordPayment(String address, List<Amount> amounts, PlutusData datum, String datumHash) {
        if (!enabled) return;

        PaymentIntention intention = PaymentIntention.builder()
            .address(address)
            .amounts(amounts)
            .datumHex(datum != null ? datum.serializeToHex() : null)
            .datumHash(datumHash)
            .build();
        intentions.add(intention);
    }

    /**
     * Record a simple payment operation.
     */
    public void recordPayment(String address, Amount amount) {
        if (!enabled) return;
        recordPayment(address, List.of(amount), null, null);
    }

    /**
     * Record a treasury donation.
     */
    public void recordDonation(BigInteger currentTreasuryValue, BigInteger donationAmount) {
        if (!enabled) return;
        intentions.add(DonationIntention.of(currentTreasuryValue, donationAmount));
    }

    // Stake methods removed - not used in simplified approach

    /**
     * Record script collection (for ScriptTx).
     */
    public void recordScriptCollect(Utxo utxo, PlutusData redeemer, PlutusData datum) {
        if (!enabled) return;
        intentions.add(ScriptCollectFromIntention.collectFrom(utxo, redeemer, datum));
    }

    /**
     * Record script collection (for ScriptTx).
     */
    public void recordScriptCollect(List<Utxo> utxos, PlutusData redeemer, PlutusData datum) {
        if (!enabled) return;
        intentions.add(ScriptCollectFromIntention.collectFrom(utxos, redeemer, datum));
    }

    /**
     * Set from address (configuration).
     */
    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    /**
     * Set change address (configuration).
     */
    public void setChangeAddress(String changeAddress) {
        this.changeAddress = changeAddress;
    }

    /**
     * Add a variable for plan parameterization.
     */
    public TxRecorder addVariable(String name, Object value) {
        variables.put(name, value);
        return this;
    }

    /**
     * Enable or disable recording.
     */
    public TxRecorder setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Build a TxPlan from recorded operations.
     */
    public TxPlan buildPlan() {
        PlanAttributes attributes = PlanAttributes.builder()
            .from(fromAddress)
            .changeAddress(changeAddress)
            .build();

        return TxPlan.builder()
            .type(planType)
            .version("1.0")
            .attributes(attributes)
            .intentions(new ArrayList<>(intentions))
            .variables(new HashMap<>(variables))
            .build();
    }

    /**
     * Reset the recorder.
     */
    public void reset() {
        intentions.clear();
        variables.clear();
        fromAddress = null;
        changeAddress = null;
    }

    /**
     * Get the number of recorded intentions.
     */
    public int getIntentionCount() {
        return intentions.size();
    }
}
