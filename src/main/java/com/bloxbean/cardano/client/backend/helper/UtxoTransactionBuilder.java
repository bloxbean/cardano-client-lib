package com.bloxbean.cardano.client.backend.helper;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.api.AssetService;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.impl.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.model.*;
import com.bloxbean.cardano.client.backend.model.request.PaymentTransaction;
import com.bloxbean.cardano.client.common.CardanoConstants;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.transaction.model.*;
import com.bloxbean.cardano.client.transaction.model.Asset;
import com.bloxbean.cardano.client.util.AssetUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.Tuple;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static com.bloxbean.cardano.client.common.CardanoConstants.ONE_ADA;

public class UtxoTransactionBuilder {

    private final UtxoService utxoService;
    private final TransactionService transactionService;

    public UtxoTransactionBuilder(UtxoService utxoService, TransactionService transactionService) {
        this.utxoService = utxoService;
        this.transactionService = transactionService;
    }

    /**
     *  Build Transaction
     * @param transactions
     * @param detailsParams
     * @return
     * @throws ApiException
     * @throws AddressExcepion
     */
    public Transaction buildTransaction(List<PaymentTransaction> transactions, TransactionDetailsParams detailsParams) throws ApiException,
            AddressExcepion {

        List<TransactionInput> transactionInputs = new ArrayList<>();
        List<TransactionOutput> transactionOutputs = new ArrayList<>();
        BigInteger totalFee = BigInteger.ZERO;
        for(PaymentTransaction transaction: transactions) {
            List<Utxo> utxos = getUtxos(transaction.getSender().baseAddress(), transaction.getUnit(), transaction.getAmount());
            BigInteger totalInAmount = BigInteger.ZERO;
            totalFee = totalFee.add(transaction.getFee());

            List<Amount> otherAssetsAmount = new ArrayList<>();
            for(Utxo utxo: utxos) {
                TransactionInput transactionInput = TransactionInput.builder()
                        .transactionId(utxo.getTxHash())
                        .index(utxo.getOutputIndex())
                        .build();
                transactionInputs.add(transactionInput);

                Optional<Amount> utxoAmount = utxo.getAmount().stream().filter(amt -> amt.getUnit().equals(transaction.getUnit())).findFirst();
                if(utxoAmount.isPresent())
                    totalInAmount = totalInAmount.add(utxoAmount.get().getQuantity()); //TODO

                //Check if other units or assets are there in utxo
                List<Amount> remainingUtxoAmts
                        = utxo.getAmount().stream().filter(amt -> !amt.getUnit().equals(transaction.getUnit())).collect(Collectors.toList());
                otherAssetsAmount.addAll(remainingUtxoAmts);
            }

            TransactionOutput.TransactionOutputBuilder outputBuilder = TransactionOutput.builder()
                    .address(transaction.getReceiver());
            if(CardanoConstants.LOVELACE.equals(transaction.getUnit())) {
                    outputBuilder.value(new Value(transaction.getAmount(), null));
            } else {
                // multiAsset.setAssets(new Ass);
                Tuple<String, String> policyIdAssetName = AssetUtil.getPolicyIdAndAssetName(transaction.getUnit());
                Asset asset = new Asset(policyIdAssetName._2, transaction.getAmount());
                MultiAsset multiAsset = new MultiAsset(policyIdAssetName._1, Arrays.asList(asset));

                outputBuilder.value(new Value(getMinimumAda(), Arrays.asList(multiAsset))); //Add min ADA value for multi asset
            }

            transactionOutputs.add(outputBuilder.build());

            BigInteger changeAmt = null;
            BigInteger multiAssetAmt = null;

            Value changeVal = new Value();
            if(CardanoConstants.LOVELACE.equals(transaction.getUnit())) {
                changeAmt = totalInAmount.subtract(transaction.getAmount()).subtract(transaction.getFee());
                changeVal.setCoin(changeAmt);
            } else { //multi-asset txn
                multiAssetAmt = totalInAmount.subtract(transaction.getAmount());

                Tuple<String, String> policyIdAssetName = AssetUtil.getPolicyIdAndAssetName(transaction.getUnit());
                Asset asset = new Asset(policyIdAssetName._2, multiAssetAmt);
                MultiAsset multiAsset = new MultiAsset(policyIdAssetName._1, new ArrayList(Arrays.asList(asset)));
                changeVal.getMultiAssets().add(multiAsset);
            }

            for(Amount otherAmt: otherAssetsAmount) {
                if(LOVELACE.equals(otherAmt.getUnit())) {
                    changeVal.setCoin(otherAmt.getQuantity().subtract(transaction.getFee()).subtract(getMinimumAda()));
                } else {
                    String assetId = otherAmt.getUnit();
                    BigInteger quantity = otherAmt.getQuantity();

                    Tuple<String, String> policyIdAssetName = AssetUtil.getPolicyIdAndAssetName(assetId);
                    Asset asset = Asset.builder()
                            .name(policyIdAssetName._2)
                            .value(quantity)
                            .build();

                    //Find if the policy id is available
                    Optional<MultiAsset> multiAssetOptional=
                            changeVal.getMultiAssets().stream().filter(ma -> policyIdAssetName._1.equals(ma.getPolicyId())).findFirst();
                    if(multiAssetOptional.isPresent()) {
                        multiAssetOptional.get().getAssets().add(asset);
                    } else {
                        List<Asset> assets = new ArrayList<>();
                        assets.add(asset);
                        MultiAsset multiAsset = new MultiAsset(policyIdAssetName._1, assets);
                        changeVal.getMultiAssets().add(multiAsset);
                    }
                }
            }

            if(changeAmt != BigInteger.ZERO || BigInteger.ZERO.compareTo(changeAmt) < 0) {
                TransactionOutput changeOutput = TransactionOutput.builder()
                        .address(transaction.getSender().baseAddress())
                        .value(changeVal)
                        .build();

                transactionOutputs.add(changeOutput);
            }
        }

        TransactionBody transactionBody = TransactionBody.builder()
                .inputs(transactionInputs)
                .outputs(transactionOutputs)
                .fee(totalFee)
                .ttl(detailsParams.getTtl())
                .build();

        Transaction transaction = Transaction.builder()
                .body(transactionBody)
                .build();

        return transaction;

    }

    /**
     * Get Utxos for the address by unit and amount
     * @param address
     * @param unit
     * @param amount
     * @return
     * @throws ApiException
     */
    public List<Utxo> getUtxos(String address, String unit, BigInteger amount) throws ApiException {
        BigInteger totalUtxoAmount = BigInteger.valueOf(0);
        List<Utxo> selectedUtxos = new ArrayList<>();
        boolean canContinue = true;
        int i = 0;

        boolean isLovlace = CardanoConstants.LOVELACE.equals(unit)? true : false;
        while(canContinue) {
            Result<List<Utxo>> result = utxoService.getUtxos(address, 40, i++);
            if(result.code() == 200) {
                List<Utxo> data = result.getValue();
                if(data == null || data.isEmpty())
                    canContinue = false;

                for(Utxo utxo: data) {
                    List<Amount> utxoAmounts = utxo.getAmount();

                    boolean unitFound = false;
                    for(Amount amt: utxoAmounts) {
                        if(unit.equals(amt.getUnit())) {
                            totalUtxoAmount = totalUtxoAmount.add(amt.getQuantity());
                            unitFound = true;
                        }
                    }

                    if(unitFound)
                        selectedUtxos.add(utxo);

                    if(totalUtxoAmount.compareTo(amount) == 1) {
                        canContinue = false;
                        break;
                    }
                }
            } else {
                canContinue = false;
            }
        }

        return selectedUtxos;
    }

    private BigInteger getMinimumAda() {
        return BigInteger.valueOf(2000000);
    }

}
