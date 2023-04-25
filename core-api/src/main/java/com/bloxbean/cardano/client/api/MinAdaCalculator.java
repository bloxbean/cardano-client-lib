package com.bloxbean.cardano.client.api;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;

import java.math.BigInteger;
import java.util.List;

public class MinAdaCalculator {
    //Dummy ada value for txout
    public static final BigInteger DUMMY_COIN_VAL = BigInteger.valueOf(1000000);
    //Dummy address
    public static final String DUMMY_ADDRESS = "addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y";

    private BigInteger coinsPerUtxoSize;

    public MinAdaCalculator(ProtocolParams protocolParams) {
        if (protocolParams.getCoinsPerUtxoSize() != null && !protocolParams.getCoinsPerUtxoSize().isEmpty()) {
            this.coinsPerUtxoSize = new BigInteger(protocolParams.getCoinsPerUtxoSize());
        }
    }

    /**
     * Calculate min required ada in {@link TransactionOutput}
     *
     * @param output
     * @return Min required ada (lovelace)
     */
    public BigInteger calculateMinAda(TransactionOutput output) {
        //according to https://hydra.iohk.io/build/15339994/download/1/babbage-changes.pdf
        //See on the page 9 getValue txout
        //getValue txout ≥ inject ( (serSize txout + 160) ∗ coinsPerUTxOByte pp )
        TransactionOutput cloneOutput = output.toBuilder().build();
        if (cloneOutput.getValue().getCoin() == null || cloneOutput.getValue().getCoin().equals(BigInteger.ZERO)
                || cloneOutput.getValue().getCoin().compareTo(BigInteger.valueOf(100000)) == -1) {
            //Workaround
            //If coin == 0, set a dummy value, so that size is calculated properly.
            //As when coin == 0, size calculation is not correct
            Value updatedValue = Value.builder()
                    .coin(DUMMY_COIN_VAL)
                    .multiAssets(output.getValue().getMultiAssets()).build();
            cloneOutput.setValue(updatedValue);
        }
        //Incase a caller invokes this method with address "null"
        if (cloneOutput.getAddress() == null)
            cloneOutput.setAddress(DUMMY_ADDRESS);

        try {
            byte[] serBytes = CborSerializationUtil.serialize(cloneOutput.serialize());
            int serSize = serBytes.length;
            return coinsPerUtxoSize.multiply(BigInteger.valueOf(serSize + 160));
        } catch (CborException | CborSerializationException | AddressExcepion e) {
            throw new CborRuntimeException("Cbor serialization error", e);
        }
    }

    /**
     * This method is deprecated
     * Use {@link MinAdaCalculator#calculateMinAda(List)} instead
     *
     * @param multiAssetList
     * @param hasDataHash
     * @return Min required ada (lovelace)
     */
    @Deprecated
    public BigInteger calculateMinAda(List<MultiAsset> multiAssetList, boolean hasDataHash) {
        return calculateMinAda(multiAssetList);
    }

    /**
     * Calculate min required ada for a TransactionOutput with given multiassets
     *
     * @param multiAssetList
     * @return Min required ada (lovelace)
     */
    public BigInteger calculateMinAda(List<MultiAsset> multiAssetList) {
        //Build dummy transaction output with multiassetList
        Value value = Value.builder()
                .coin(DUMMY_COIN_VAL)
                .multiAssets(multiAssetList).build();
        TransactionOutput txOut = TransactionOutput.builder()
                .address(DUMMY_ADDRESS)
                .value(value).build();

        return calculateMinAda(txOut);
    }
}
