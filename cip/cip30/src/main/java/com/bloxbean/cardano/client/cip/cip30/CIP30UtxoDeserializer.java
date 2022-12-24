package com.bloxbean.cardano.client.cip.cip30;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.util.HexUtil;

import java.util.List;

/**
 * Deserialize cbor from CIP30's wallet.getUtxos()
 */
class CIP30UtxoDeserializer {

    /**
     * Deserialize cbor from CIP30's wallet.getUtxos() method
     * @param bytes utxo cbor bytes from wallet.getUtxos()
     * @return Utxo
     *
     */
    public static Utxo deserialize(byte[] bytes) throws CborDeserializationException {
        Utxo utxo = new Utxo();
        try {
            List<DataItem> dataItemList = CborDecoder.decode(bytes);
            if (dataItemList.size() != 1) {
                throw new CborDeserializationException("Invalid no of items");
            }
            Array array = (Array) dataItemList.get(0);
            List<DataItem> utxoItems = array.getDataItems();
            if (utxoItems.size() < 2) {
                throw new CborDeserializationException("Invalid no of items");
            }
            Array txArray = (Array) utxoItems.get(0);
            List<DataItem> txDataItems = txArray.getDataItems();
            if (txDataItems.size() < 2) {
                throw new CborDeserializationException("Invalid no of items");
            }
            utxo.setTxHash(HexUtil.encodeHexString(((ByteString) txDataItems.get(0)).getBytes()));
            utxo.setOutputIndex(((UnsignedInteger) txDataItems.get(1)).getValue().intValue());

            TransactionOutput transactionOutput = TransactionOutput.deserialize(utxoItems.get(1));
            utxo.setAddress(transactionOutput.getAddress());
            utxo.setAmount(transactionOutput.getValue().toAmountList());
            if (transactionOutput.getInlineDatum() != null) {
                utxo.setInlineDatum(transactionOutput.getInlineDatum().serializeToHex());
            }
            if (transactionOutput.getDatumHash() != null) {
                utxo.setDataHash(HexUtil.encodeHexString(transactionOutput.getDatumHash()));
            }
            if (transactionOutput.getScriptRef() != null) {
                utxo.setReferenceScriptHash(HexUtil.encodeHexString(transactionOutput.getScriptRef()));
            }
            return utxo;
        } catch (Exception e) {
            throw new CborDeserializationException("CBOR deserialization failed", e);
        }
    }
}
