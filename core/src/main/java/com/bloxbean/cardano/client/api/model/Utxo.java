package com.bloxbean.cardano.client.api.model;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.util.AssetUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static java.util.stream.Collectors.groupingBy;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Utxo {

    private String txHash;
    private int outputIndex;
    private String address;
    private List<Amount> amount;
    private String dataHash;
    private String inlineDatum;
    private String referenceScriptHash;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Utxo utxo = (Utxo) o;
        return outputIndex == utxo.outputIndex && txHash.equals(utxo.txHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(txHash, outputIndex);
    }

    public Value toValue() {
        //Get lovelace amount
        Amount loveLaceAmount = amount.stream()
                .filter(amt -> LOVELACE.equals(amt.getUnit()))
                .findFirst()
                .orElse(new Amount(LOVELACE, BigInteger.ZERO)); //TODO throw error

        //Convert non lovelace amount to MultiAsset
        List<MultiAsset> multiAssets = amount.stream()
                .filter(amt -> !LOVELACE.equals(amt.getUnit()))
                .collect(groupingBy(amt -> AssetUtil.getPolicyIdAndAssetName(amt.getUnit())._1))
                .entrySet()
                .stream()
                .map(entry -> {
                    List<Asset> assets = entry.getValue().stream()
                            .map(amount -> new Asset(AssetUtil.getPolicyIdAndAssetName(amount.getUnit())._2, amount.getQuantity()))
                            .collect(Collectors.toList());

                    MultiAsset multiAsset = new MultiAsset();
                    multiAsset.setPolicyId(entry.getKey());
                    multiAsset.setAssets(assets);

                    return multiAsset;
                }).collect(Collectors.toList());

        return new Value(loveLaceAmount.getQuantity(), multiAssets);

    }

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
