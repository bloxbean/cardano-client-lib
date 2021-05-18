package com.bloxbean.cardano.client.backend.api.helper;

import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.backend.exception.InsufficientBalanceException;
import com.bloxbean.cardano.client.backend.model.Amount;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.backend.model.request.PaymentTransaction;
import com.bloxbean.cardano.client.common.CardanoConstants;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.transaction.model.*;
import com.bloxbean.cardano.client.util.AssetUtil;
import com.bloxbean.cardano.client.util.Tuple;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static com.bloxbean.cardano.client.common.CardanoConstants.ONE_ADA;

public class UtxoTransactionBuilder {
    private Logger LOG = LoggerFactory.getLogger(UtxoTransactionBuilder.class);

    private final UtxoService utxoService;
    private final TransactionService transactionService;

    public UtxoTransactionBuilder(UtxoService utxoService, TransactionService transactionService) {
        this.utxoService = utxoService;
        this.transactionService = transactionService;
    }

    /**
     * Build Transaction
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

        //Get map for sender -> totalAmt, unit
        Multimap<String, Amount> senderAmountsMap = calculateRequiredBalancesForSenders(transactions);

        //Get sender -> utxos map based on the unit and total qty requirement
        Map<String, Set<Utxo>> senderToUtxoMap = getSenderToUtxosMap(senderAmountsMap);

        BigInteger totalFee = BigInteger.valueOf(0);
        Map<String, BigInteger> senderMiscCostMap = new HashMap<>(); //Misc cost of sender, mini ada

        //Create output for receivers and calculate total fees/cost for each sender
        for(PaymentTransaction transaction: transactions) {
            totalFee = createReceiverOutputsAndPopulateCost(transaction, detailsParams, totalFee, transactionOutputs, senderMiscCostMap);
        }

        //Check if min cost is there in all selected Utxos
        for(String sender: senderMiscCostMap.keySet()) {
            checkAndAddAdditionalUtxosIfMinCostIsNotMet(senderToUtxoMap, senderMiscCostMap, sender);
        }

        //Go through sender Utxos, Build Inputs first from Utxos and then change outputs
        senderToUtxoMap.entrySet().forEach( entry ->{
            String sender = entry.getKey(); //Sender and it's utxos
            Set<Utxo> utxoSet = entry.getValue();
            try {
                buildOuputsForSenderFromUtxos(sender, utxoSet, transactionInputs, transactionOutputs, senderAmountsMap,
                        senderMiscCostMap, detailsParams);
            } catch (ApiException e) {
                LOG.error("Error builiding transaction outputs", e);
                throw new ApiRuntimeException("Error building transaction outputs", e);
            }
        });

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
        return getUtxos(address, unit, amount, Collections.EMPTY_SET);
    }

    private List<Utxo> getUtxos(String address, String unit, BigInteger amount, Set<Utxo> excludeUtxos) throws ApiException {
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
                    if(excludeUtxos.contains(utxo))
                        continue;

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
                throw new ApiException(String.format("Unable to get Utxos for address : %s, reason: %s", address, result.getResponse()));
            }
        }

        return selectedUtxos;
    }

    private void checkAndAddAdditionalUtxosIfMinCostIsNotMet(Map<String, Set<Utxo>> senderToUtxoMap, Map<String, BigInteger> senderMiscCostMap, String sender) throws ApiException {
        BigInteger minCost = senderMiscCostMap.get(sender);
        Set<Utxo> utxos = senderToUtxoMap.getOrDefault(sender, new HashSet());

        BigInteger totalLoveLace = BigInteger.ZERO;
        for(Utxo utxo: utxos) {
            Optional<Amount> optional = utxo.getAmount().stream().filter(amt -> LOVELACE.equals(amt.getUnit())).findFirst();
            if(optional.isPresent()) {
                totalLoveLace = totalLoveLace.add(optional.get().getQuantity());
            }
        }

        if(totalLoveLace == null) totalLoveLace = BigInteger.ZERO;

        if(minCost != null && totalLoveLace.compareTo(minCost) != 1) {
            BigInteger additionalAmt = minCost.subtract(totalLoveLace).add(BigInteger.ONE); //add one for safer side
            List<Utxo> additionalUtxos = getUtxos(sender, LOVELACE, additionalAmt);
            if(additionalUtxos == null || additionalUtxos.size() == 0)
                throw new ApiException(String.format("No utxos found for address for additional amount: %s, unit: %s, amount: %s", sender, LOVELACE, additionalAmt));

            utxos.addAll(additionalUtxos);
        }
    }

    private Map<String, Set<Utxo>> getSenderToUtxosMap(Multimap<String, Amount> senderAmountsMap) throws ApiException {
        Map<String, Set<Utxo>> senderToUtxoMap = new HashMap<>();
        for(String sender: senderAmountsMap.keySet()) { //Get all Utxos for all transactions
            Collection<Amount> amts = senderAmountsMap.get(sender);
            if(amts == null || amts.size() == 0) continue;

            Set<Utxo> utxoSet = new HashSet<>();
            for(Amount amt: amts) {
                //Get utxos
                List<Utxo> utxos = getUtxos(sender, amt.getUnit(), amt.getQuantity());
                if(utxos == null || utxos.size() == 0)
                    throw new ApiException("No utxos found for address : " + sender);
                utxos.forEach(utxo -> {
                    utxoSet.add(utxo);
                });
            }
            senderToUtxoMap.put(sender, utxoSet);
        }

        return senderToUtxoMap;
    }

    private BigInteger createReceiverOutputsAndPopulateCost(PaymentTransaction transaction, TransactionDetailsParams detailsParams, BigInteger totalFee,
                                                            List<TransactionOutput> transactionOutputs, Map<String, BigInteger> senderMiscCostMap) {
        //Sender misc cost
        BigInteger existingMiscCost = senderMiscCostMap.getOrDefault(transaction.getSender().baseAddress(), BigInteger.ZERO);

        //Main output
        TransactionOutput.TransactionOutputBuilder outputBuilder = TransactionOutput.builder()
                .address(transaction.getReceiver());
        if (CardanoConstants.LOVELACE.equals(transaction.getUnit())) {
            outputBuilder.value(new Value(transaction.getAmount(), null));
        } else {
            Tuple<String, String> policyIdAssetName = AssetUtil.getPolicyIdAndAssetName(transaction.getUnit());
            Asset asset = new Asset(policyIdAssetName._2, transaction.getAmount());
            MultiAsset multiAsset = new MultiAsset(policyIdAssetName._1, Arrays.asList(asset));

            outputBuilder.value(new Value(getMinimumLovelaceForMultiAsset(detailsParams), Arrays.asList(multiAsset))); //Add min ADA value for multi asset

            existingMiscCost = existingMiscCost.add(getMinimumLovelaceForMultiAsset(detailsParams));
        }

        existingMiscCost = existingMiscCost.add(transaction.getFee());
        senderMiscCostMap.put(transaction.getSender().baseAddress(), existingMiscCost);

        totalFee = totalFee.add(transaction.getFee());
        transactionOutputs.add(outputBuilder.build());
        return totalFee;
    }

    private void buildOuputsForSenderFromUtxos(String sender, Set<Utxo> utxoSet, List<TransactionInput> transactionInputs,
                                               List<TransactionOutput> transactionOutputs, Multimap<String, Amount> senderAmountsMap,
                                               Map<String, BigInteger> senderMiscCostMap, TransactionDetailsParams detailsParams) throws ApiException {
        TransactionOutput changeOutput = new TransactionOutput(sender, new Value());
        //Initial sender txnoutput with negative amount
        senderAmountsMap.get(sender).stream()
                .forEach(amount -> {
                    if(LOVELACE.equals(amount.getUnit())) {
                        changeOutput.getValue().setCoin(BigInteger.ZERO.subtract(amount.getQuantity()));
                    } else {
                        Tuple<String, String> policyIdAssetName = AssetUtil.getPolicyIdAndAssetName(amount.getUnit());
                        Asset asset = new Asset(policyIdAssetName._2, BigInteger.ZERO.subtract(amount.getQuantity()));
                        MultiAsset multiAsset = new MultiAsset(policyIdAssetName._1, new ArrayList<>(Arrays.asList(asset)));
                        changeOutput.getValue().getMultiAssets().add(multiAsset);
                    }
                });

        utxoSet.forEach(utxo -> {
            TransactionInput transactionInput = TransactionInput.builder()
                    .transactionId(utxo.getTxHash())
                    .index(utxo.getOutputIndex())
                    .build();
            transactionInputs.add(transactionInput);

            //Calculate change amount
            copyUtxoValuesToChangeOutput(changeOutput, utxo);
        });

        if((changeOutput.getValue().getCoin() != null && changeOutput.getValue().getCoin().compareTo(BigInteger.ZERO) == 1) ||
                (changeOutput.getValue().getMultiAssets().size() > 0)) {

            //deduct misc cost (fee + min ada value)
            BigInteger misCostVal = senderMiscCostMap.get(changeOutput.getAddress());
            BigInteger afterMisCost = changeOutput.getValue().getCoin().subtract(misCostVal);
            changeOutput.getValue().setCoin(afterMisCost);

            //Check if minimum Ada is not met. Topup
            //Transaction will fail if minimun ada not there. So try to get some additiona utxos
            BigInteger minRequiredLovelaceInOutput = getMinimumLovelaceInOutput(detailsParams);
            if(changeOutput.getValue().getCoin() != null && minRequiredLovelaceInOutput.compareTo(changeOutput.getValue().getCoin()) == 1) {
                //Get utxos
                List<Utxo> additionalUtxos = getUtxos(changeOutput.getAddress(), LOVELACE, minRequiredLovelaceInOutput,  utxoSet);
                if(additionalUtxos == null || additionalUtxos.size() == 0)
                    throw new InsufficientBalanceException("Not enough utxos found to cover minimum lovelace in an ouput");

                System.out.println("additional Utoxs found: " + additionalUtxos);
                //Add to input
                Utxo utxo = additionalUtxos.get(0);
                TransactionInput transactionInput = TransactionInput.builder()
                        .transactionId(utxo.getTxHash())
                        .index(utxo.getOutputIndex())
                        .build();
                transactionInputs.add(transactionInput);

                //Update change output
                copyUtxoValuesToChangeOutput(changeOutput, utxo);
            }

            transactionOutputs.add(changeOutput);
        }
    }

    private void copyUtxoValuesToChangeOutput(TransactionOutput changeOutput, Utxo utxo) {
        utxo.getAmount().forEach(utxoAmt -> { //For each amt in utxo
            String utxoUnit = utxoAmt.getUnit();
            BigInteger utxoQty = utxoAmt.getQuantity();
            if (utxoUnit.equals(LOVELACE)) {
                BigInteger existingCoin = changeOutput.getValue().getCoin();
                if (existingCoin == null) existingCoin = BigInteger.ZERO;
                changeOutput.getValue().setCoin(existingCoin.add(utxoQty));
            } else {
                Tuple<String, String> policyIdAssetName = AssetUtil.getPolicyIdAndAssetName(utxoUnit);

                //Find if the policy id is available
                Optional<MultiAsset> multiAssetOptional =
                        changeOutput.getValue().getMultiAssets().stream().filter(ma -> policyIdAssetName._1.equals(ma.getPolicyId())).findFirst();
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
                    changeOutput.getValue().getMultiAssets().add(multiAsset);
                }
            }
        });

        //Remove any empty MultiAssets
        List<MultiAsset> multiAssets = changeOutput.getValue().getMultiAssets();
        List<MultiAsset> markedForRemoval = new ArrayList<>();
        if(multiAssets != null && multiAssets.size() > 0) {
            multiAssets.forEach(ma -> {
                if(ma.getAssets() == null || ma.getAssets().size() == 0)
                    markedForRemoval.add(ma);
            });
        }
        multiAssets.removeAll(markedForRemoval);
    }

    private Multimap<String, Amount> calculateRequiredBalancesForSenders(List<PaymentTransaction> transactions) {
        Multimap<String, Amount> senderAmountMap = ArrayListMultimap.create();
        for(PaymentTransaction transaction: transactions) {
            String sender = transaction.getSender().baseAddress();
            String unit = transaction.getUnit();
            BigInteger amount = transaction.getAmount();

            addAmountToSenderAmountMap(senderAmountMap, sender, unit, amount);
        }

        return senderAmountMap;
    }

    private void addAmountToSenderAmountMap(Multimap<String, Amount> senderAmountMap, String sender, String unit, BigInteger amount) {
        Collection<Amount> amounts = senderAmountMap.get(sender);
        if(amounts != null && amounts.size() > 0) {
            Optional<Amount> existingAmtOptional = amounts.stream().filter(amt -> unit.equals(amt.getUnit())).findFirst();

            if(existingAmtOptional.isPresent()) {
                Amount existingAmt = existingAmtOptional.get();
                existingAmt.setQuantity(existingAmt.getQuantity().add(amount));
            } else {
                senderAmountMap.put(sender, new Amount(unit, amount));
            }
        } else {
            senderAmountMap.put(sender, new Amount(unit, amount));
        }
    }

    private BigInteger getMinimumLovelaceForMultiAsset(TransactionDetailsParams transactionDetailsParams) {
        if(transactionDetailsParams != null && transactionDetailsParams.getMinLovelaceForMultiAsset() != null)
            return transactionDetailsParams.getMinLovelaceForMultiAsset();
        else
            return BigInteger.valueOf(2000000);
    }

    private BigInteger getMinimumLovelaceInOutput(TransactionDetailsParams transactionDetailsParams) {
        if(transactionDetailsParams != null && transactionDetailsParams.getMinLovelaceInOuput() != null)
            return transactionDetailsParams.getMinLovelaceInOuput();
        else
            return ONE_ADA;
    }

}
