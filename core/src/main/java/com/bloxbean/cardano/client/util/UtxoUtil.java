package com.bloxbean.cardano.client.util;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

@Slf4j
@Deprecated
/**
 * @deprecated Use {@link com.bloxbean.cardano.client.api.util.UtxoUtil} instead
 */
public class UtxoUtil {

    /**
     * Copy utxo content to TransactionOutput
     *
     * @param output
     * @param utxo
     */
    public static void copyUtxoValuesToOutput(TransactionOutput output, Utxo utxo) {
        utxo.getAmount().forEach(utxoAmt -> { //For each amt in utxo
            String utxoUnit = utxoAmt.getUnit();
            BigInteger utxoQty = utxoAmt.getQuantity();
            if (utxoUnit.equals(LOVELACE)) {
                BigInteger existingCoin = output.getValue().getCoin();
                if (existingCoin == null) existingCoin = BigInteger.ZERO;
                output.getValue().setCoin(existingCoin.add(utxoQty));
            } else {
                Tuple<String, String> policyIdAssetName = AssetUtil.getPolicyIdAndAssetName(utxoUnit);

                //Find if the policy id is available
                Optional<MultiAsset> multiAssetOptional =
                        output.getValue().getMultiAssets().stream().filter(ma -> policyIdAssetName._1.equals(ma.getPolicyId())).findFirst();
                if (multiAssetOptional.isPresent()) {
                    Optional<Asset> assetOptional = multiAssetOptional.get().getAssets().stream()
                            .filter(ast -> policyIdAssetName._2.equals(ast.getName()))
                            .findFirst();
                    if (assetOptional.isPresent()) {
                        BigInteger changeVal = assetOptional.get().getValue().add(utxoQty);
                        assetOptional.get().setValue(changeVal);
                    } else {
                        Asset asset = new Asset(policyIdAssetName._2, utxoQty);
                        multiAssetOptional.get().getAssets().add(asset);
                    }
                } else {
                    Asset asset = new Asset(policyIdAssetName._2, utxoQty);
                    MultiAsset multiAsset = new MultiAsset(policyIdAssetName._1, new ArrayList<>(Arrays.asList(asset)));
                    output.getValue().getMultiAssets().add(multiAsset);
                }
            }
        });

        //Remove any empty MultiAssets
        List<MultiAsset> multiAssets = output.getValue().getMultiAssets();
        List<MultiAsset> markedForRemoval = new ArrayList<>();
        if (multiAssets != null && multiAssets.size() > 0) {
            multiAssets.forEach(ma -> {
                if (ma.getAssets() == null || ma.getAssets().size() == 0)
                    markedForRemoval.add(ma);
            });

            if (markedForRemoval != null && !markedForRemoval.isEmpty()) multiAssets.removeAll(markedForRemoval);
        }
    }

    /**
     * Get a set of PubKeyHash of owners from a given set of utxos. Script utxos are ignored.
     *
     * @param utxos
     * @return Set of PubKeyHash
     */
    public static Set<String> getOwnerPubKeyHashes(@NonNull Set<Utxo> utxos) {
        Set<String> pubKeyHashes = new HashSet<>();
        for (Utxo utxo : utxos) {
            if (utxo.getAddress() == null || utxo.getAddress().isEmpty()) {
                log.warn("Null address in utxo : TxHash=" + utxo.getTxHash()
                        + ", Index=" + utxo.getOutputIndex());
                continue;
            }

            try {
                Address address = new Address(utxo.getAddress());
                //If PubKeyHash in Payment part
                if (AddressProvider.isPubKeyHashInPaymentPart(address)) {
                    AddressProvider.getPaymentCredentialHash(address)
                            .ifPresent(bytes -> pubKeyHashes.add(HexUtil.encodeHexString(bytes)));
                }
            } catch (Exception e) {
                if (log.isDebugEnabled())
                    log.warn("Unable to parse the address. Probably a Byron address. " + utxo.getAddress());
            }
        }
        return pubKeyHashes;
    }

    /**
     * Get list of Byron address (if any) of owners from the utxos set.
     * @param utxos
     * @return List of Byron addresses
     */
    public static Set<String> getByronAddressOwners(@NonNull Set<Utxo> utxos) {
        return utxos.stream().filter(utxo -> utxo.getAddress() != null && !utxo.getAddress().isEmpty()
                                && !utxo.getAddress().startsWith("addr") && !utxo.getAddress().startsWith("stake"))
                .map(utxo -> utxo.getAddress())
                .collect(Collectors.toSet());
    }

    /**
     * Get the no of required signers for the utxos set based on owners of utxos.
     *
     * @return
     */
    public static int getNoOfRequiredSigners(@NonNull Set<Utxo> utxos) {
        Set<String> pubKeyHashes = getOwnerPubKeyHashes(utxos);
        Set<String> byronOwners = getByronAddressOwners(utxos);

        return pubKeyHashes.size() + byronOwners.size();
    }
}
