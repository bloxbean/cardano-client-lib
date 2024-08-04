package com.bloxbean.cardano.client.quicktx.blueprint.extender;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;

import java.math.BigInteger;
import java.util.List;

/**
 * Extender for deploying the validator script
 * @param <T>
 */
public interface DeployValidatorExtender<T> extends ValidatorExtender {

    /**
     * Create a Tx to deploy the script. This will create an output with 1 lovelace and reference script bytes at script address.
     * This Tx can be composed with other Tx to create the final transaction through {@link QuickTxBuilder#compose}}
     *
     * @param deployerAddr Deployer/Fee payer address
     * @return Tx
     */
    default Tx deployTx(String deployerAddr) {
        try {
            Tx tx = new Tx()
                    .payToAddress(getScriptAddress(), List.of(Amount.lovelace(BigInteger.valueOf(1))), getPlutusScript().scriptRefBytes())
                    .from(deployerAddr);
            return tx;
        } catch (CborSerializationException e) {
            throw new CborRuntimeException(e.getMessage());
        }
    }

    /**
     * Create a {@link com.bloxbean.cardano.client.quicktx.QuickTxBuilder.TxContext} to deploy the script. This will create an output with 1 lovelace and reference script bytes at script address.
     * TxContext can be used to sign and submit the transaction.
     *
     * @param deployerAddr Deployer/Fee payer address
     * @return TxContext
     */
    default QuickTxBuilder.TxContext deploy(String deployerAddr) {
        requireSuppliersNullCheck();

        Tx tx = deployTx(deployerAddr);

        QuickTxBuilder quickTxBuilder =
                new QuickTxBuilder(getUtxoSupplier(), getProtocolParamsSupplier(), getTransactionProcessor());

        return quickTxBuilder.compose(tx);
    }

}
