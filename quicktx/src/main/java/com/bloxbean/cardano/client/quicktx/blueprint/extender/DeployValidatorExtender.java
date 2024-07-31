package com.bloxbean.cardano.client.quicktx.blueprint.extender;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;

import java.math.BigInteger;
import java.util.List;

public interface DeployValidatorExtender<T> extends ValidatorExtender {

    /**
     * Create a Tx to deploy the script. This will create an output with 1 lovelace and reference script bytes at script address.
     * This Tx can be composed with other Tx to create the final transaction through {@link QuickTxBuilder#compose}}
     *
     * @param feePayerAddr Fee and min lovelace value payer address
     * @return Tx
     */
    default Tx deployTx(String feePayerAddr) {
        try {
            Tx tx = new Tx()
                    .payToAddress(getScriptAddress(), List.of(Amount.lovelace(BigInteger.valueOf(1))), getPlutusScript().scriptRefBytes())
                    .from(feePayerAddr);
            return tx;
        } catch (CborSerializationException e) {
            throw new CborRuntimeException(e.getMessage());
        }
    }
}
