package com.bloxbean.cardano.client.backend.api.helper.impl;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.backend.api.helper.UtxoTransactionBuilder;
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelectionStrategyImpl;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.backend.exception.InsufficientBalanceException;
import com.bloxbean.cardano.client.backend.model.Amount;
import com.bloxbean.cardano.client.backend.model.ProtocolParams;
import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.common.CardanoConstants;
import com.bloxbean.cardano.client.common.MinAdaCalculator;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.transaction.model.MintTransaction;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.AssetUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.bloxbean.cardano.client.util.Tuple;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

@Slf4j
public class UtxoTransactionBuilderImpl implements UtxoTransactionBuilder {

    /**
     * This `case` class defines the transaction grouping mechanism for collating
     * multiple transaction in just one output.
     * Currently, in Cardano, sender, receive and datum (hash) must be the same in order for
     * multiple transaction to be collated together.
     */
    @Data
    @AllArgsConstructor
    private class PaymentTransactionGroupingKey {
        private Account sender;
        private String receiver;
        private String datumHash;
    }

    private UtxoSelectionStrategy utxoSelectionStrategy;

    /**
     * Create a {@link UtxoTransactionBuilder} with {@link DefaultUtxoSelectionStrategyImpl}
     *
     * @param utxoService
     */
    public UtxoTransactionBuilderImpl(UtxoService utxoService) {
        this.utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoService);
    }

    /**
     * Create a {@link UtxoTransactionBuilder} with custom {@link UtxoSelectionStrategy}
     *
     * @param utxoSelectionStrategy
     */
    public UtxoTransactionBuilderImpl(UtxoSelectionStrategy utxoSelectionStrategy) {
        this.utxoSelectionStrategy = utxoSelectionStrategy;
    }

    /**
     * Set a custom UtxoSelectionStrategy
     *
     * @param utxoSelectionStrategy
     */
    @Override
    public void setUtxoSelectionStrategy(UtxoSelectionStrategy utxoSelectionStrategy) {
        this.utxoSelectionStrategy = utxoSelectionStrategy;
    }

    /**
     * Get current {@link UtxoSelectionStrategy}
     *
     * @return
     */
    public UtxoSelectionStrategy getUtxoSelectionStrategy() {
        return this.utxoSelectionStrategy;
    }

    /**
     * Build Transaction
     *
     * @param transactions
     * @param detailsParams
     * @return
     * @throws ApiException
     */
    @Override
    public Transaction buildTransaction(List<PaymentTransaction> transactions, TransactionDetailsParams detailsParams,
                                        Metadata metadata, ProtocolParams protocolParams) throws ApiException {

        List<TransactionInput> transactionInputs = new ArrayList<>();
        List<TransactionOutput> transactionOutputs = new ArrayList<>();

        //Get map for sender -> totalAmt, unit
        Multimap<String, Amount> senderAmountsMap = calculateRequiredBalancesForSenders(transactions);

        //Populate sender/utxos map if  utxos are provided by the client application as part of PaymentTransaction
        Map<String, Set<Utxo>> senderToUtxoMap = getSenderToUtxosMapFromTransactions(transactions);

        //Get sender -> utxos map based on the unit and total qty requirement
        //Assumption: If Utxos are provided as part PaymentTransaction, then all PaymentTransactions will have Utxos list,
        // so we dont need to find utxos
        if (senderToUtxoMap.size() == 0) {
            senderToUtxoMap = getSenderToUtxosMap(senderAmountsMap);
        }

        BigInteger totalFee = BigInteger.valueOf(0);
        Map<String, BigInteger> senderMiscCostMap = new HashMap<>(); //Misc cost of sender, mini ada

        //Create output for receivers and calculate total fees/cost for each sender
        totalFee = createReceiverOutputsAndPopulateCostV2(transactions, detailsParams, totalFee, transactionOutputs, senderMiscCostMap, protocolParams);

        //Check if min cost is there in all selected Utxos
        for (String sender : senderMiscCostMap.keySet()) {
            checkAndAddAdditionalUtxosIfMinCostIsNotMet(senderToUtxoMap, senderMiscCostMap, sender);
        }

        //Go through sender Utxos, Build Inputs first from Utxos and then change outputs
        //Sender and it's utxos
        senderToUtxoMap.forEach((sender, utxoSet) -> {
            try {
                buildOuputsForSenderFromUtxos(sender, utxoSet, transactionInputs, transactionOutputs, senderAmountsMap,
                        senderMiscCostMap, detailsParams, protocolParams);
            } catch (ApiException e) {
                log.error("Error building transaction outputs", e);
                throw new ApiRuntimeException("Error building transaction outputs", e);
            }
        });

        TransactionBody transactionBody = TransactionBody.builder()
                .inputs(transactionInputs)
                .outputs(transactionOutputs)
                .fee(totalFee)
                .ttl(detailsParams.getTtl())
                .validityStartInterval(detailsParams.getValidityStartInterval())
                .build();

        AuxiliaryData auxiliaryData = AuxiliaryData.builder()
                .metadata(metadata)
                .build();

        return Transaction.builder()
                .body(transactionBody)
                .auxiliaryData(auxiliaryData)
                .build();
    }

    /**
     * Get Utxos for the address by unit and amount
     *
     * @param address
     * @param unit
     * @param amount
     * @return
     * @throws ApiException
     */
    @Override
    public List<Utxo> getUtxos(String address, String unit, BigInteger amount) throws ApiException {
        return getUtxos(address, unit, amount, Collections.EMPTY_SET);
    }

    @Override
    public Transaction buildMintTokenTransaction(MintTransaction mintTransaction, TransactionDetailsParams detailsParams, Metadata metadata, ProtocolParams protocolParams) throws ApiException {
        String sender = mintTransaction.getSender().baseAddress();

        String receiver = mintTransaction.getReceiver();
        if (receiver == null || receiver.isEmpty())
            receiver = mintTransaction.getSender().baseAddress();

        BigInteger minAmount = createDummyOutputAndCalculateMinAdaForTxnOutput(receiver,
                mintTransaction.getMintAssets(), protocolParams);
        //getMinimumLovelaceForMultiAsset(detailsParams).multiply(BigInteger.valueOf(totalAssets));
        BigInteger totalCost = minAmount.add(mintTransaction.getFee());

        //Get utxos from the transaction request if available
        List<Utxo> utxos = mintTransaction.getUtxosToInclude();

        //If no utxos found as part of request, then fetch from backend
        if (utxos == null || utxos.size() == 0) {
            utxos = getUtxos(sender, LOVELACE, totalCost);
            if (utxos.size() == 0)
                throw new InsufficientBalanceException("Not enough utxos found to cover balance : " + totalCost + " lovelace");
        }

        List<TransactionInput> inputs = new ArrayList<>();
        List<TransactionOutput> outputs = new ArrayList<>();

        //Create single TxnOutput for the sender
        TransactionOutput transactionOutput = new TransactionOutput();
        transactionOutput.setAddress(sender);
        Value senderValue = Value.builder()
                .coin(BigInteger.ZERO)
                .multiAssets(new ArrayList<>())
                .build();
        transactionOutput.setValue(senderValue);

        //Keep a flag to make sure fee is already deducted
        boolean feeDeducted = false;
        for (Utxo utxo : utxos) {
            //create input for this utxo
            TransactionInput transactionInput = new TransactionInput(utxo.getTxHash(), utxo.getOutputIndex());
            inputs.add(transactionInput);

            copyUtxoValuesToChangeOutput(transactionOutput, utxo);

          /*  //Deduct fee from sender's output if applicable
            BigInteger lovelaceValue = transactionOutput.getValue().getCoin();
            if(feeDeducted) { //fee + min amount required for new multiasset output
                transactionOutput.getValue().setCoin(lovelaceValue);
            } else {
                BigInteger remainingAmount = lovelaceValue.subtract(amount);
                if(remainingAmount.compareTo(BigInteger.ZERO) == 1) { //Positive value
                    transactionOutput.getValue().setCoin(remainingAmount); //deduct requirement amt (fee + min amount)
                    feeDeducted = true;
                }
            }*/
        }

        //Deduct fee + minCost in a MA output
        BigInteger remainingAmount = transactionOutput.getValue().getCoin().subtract(totalCost);
        transactionOutput.getValue().setCoin(remainingAmount); //deduct requirement amt (fee + min amount)

        //Check if minimum Ada is not met. Topup
        //Transaction will fail if minimun ada not there. So try to get some additional utxos
        verifyMinAdaInOutputAndUpdateIfRequired(inputs, transactionOutput, detailsParams, utxos, protocolParams);

        outputs.add(transactionOutput);

        //Create a separate output for minted assets
        //Create output
        TransactionOutput mintedTransactionOutput = new TransactionOutput();
        mintedTransactionOutput.setAddress(receiver);
        Value value = Value.builder()
                .coin(minAmount)
                .multiAssets(new ArrayList<>())
                .build();
        mintedTransactionOutput.setValue(value);
        for (MultiAsset ma : mintTransaction.getMintAssets()) {
            mintedTransactionOutput.getValue().getMultiAssets().add(ma);
        }

        //Add datum hash. Should be used only for receiver as script address
        if (mintTransaction.getDatumHash() != null && !mintTransaction.getDatumHash().isEmpty()) {
            mintedTransactionOutput.setDatumHash(HexUtil.decodeHexString(mintTransaction.getDatumHash()));
        }

        outputs.add(mintedTransactionOutput);

        TransactionBody body = TransactionBody
                .builder()
                .inputs(inputs)
                .outputs(outputs)
                .fee(mintTransaction.getFee())
                .ttl(detailsParams.getTtl())
                .validityStartInterval(detailsParams.getValidityStartInterval())
                .mint(mintTransaction.getMintAssets())
                .build();

        if (log.isDebugEnabled())
            log.debug(JsonUtil.getPrettyJson(body));

        AuxiliaryData auxiliaryData = AuxiliaryData.builder()
                .metadata(metadata)
                .build();

        Transaction transaction = Transaction.builder()
                .body(body)
                .auxiliaryData(auxiliaryData)
                .build();

        return transaction;
    }

    private void verifyMinAdaInOutputAndUpdateIfRequired(List<TransactionInput> inputs, TransactionOutput transactionOutput,
                                                         TransactionDetailsParams detailsParams, Collection<Utxo> excludeUtxos, ProtocolParams protocolParams) throws ApiException {
        BigInteger minRequiredLovelaceInOutput =
                new MinAdaCalculator(protocolParams).calculateMinAda(transactionOutput);

        //Create another copy of the list
        List<Utxo> ignoreUtxoList = excludeUtxos.stream().collect(Collectors.toList());

        while (transactionOutput.getValue().getCoin() != null
                && minRequiredLovelaceInOutput.compareTo(transactionOutput.getValue().getCoin()) == 1) {
            //Get utxos
            List<Utxo> additionalUtxos = getUtxos(transactionOutput.getAddress(), LOVELACE, minRequiredLovelaceInOutput,
                    new HashSet(ignoreUtxoList));

            if (additionalUtxos == null || additionalUtxos.size() == 0) {
                if (log.isDebugEnabled()) {
                    log.debug("Not enough utxos found to cover minimum lovelace in an output");
                }
                break;
            }

            if (log.isDebugEnabled())
                log.debug("Additional Utxos found: " + additionalUtxos);
            for (Utxo addUtxo : additionalUtxos) {
                TransactionInput addTxnInput = TransactionInput.builder()
                        .transactionId(addUtxo.getTxHash())
                        .index(addUtxo.getOutputIndex())
                        .build();
                inputs.add(addTxnInput);

                //Update change output
                copyUtxoValuesToChangeOutput(transactionOutput, addUtxo);
            }
            ignoreUtxoList.addAll(additionalUtxos);

            //Calculate final minReq balance in output, if still doesn't satisfy, continue again
            minRequiredLovelaceInOutput =
                    new MinAdaCalculator(protocolParams).calculateMinAda(transactionOutput);
        }
    }

    private List<Utxo> getUtxos(String address, String unit, BigInteger amount, Set<Utxo> excludeUtxos) throws ApiException {
        return utxoSelectionStrategy.selectUtxos(address, unit, amount, excludeUtxos);
    }

    private void checkAndAddAdditionalUtxosIfMinCostIsNotMet(Map<String, Set<Utxo>> senderToUtxoMap,
                                                             Map<String, BigInteger> senderMiscCostMap, String sender) throws ApiException {
        BigInteger minCost = senderMiscCostMap.get(sender);
        Set<Utxo> utxos = senderToUtxoMap.getOrDefault(sender, new HashSet<>());

        BigInteger totalLoveLace = BigInteger.ZERO;
        for (Utxo utxo : utxos) {
            Optional<Amount> optional = utxo.getAmount().stream().filter(amt -> LOVELACE.equals(amt.getUnit())).findFirst();
            if (optional.isPresent()) {
                totalLoveLace = totalLoveLace.add(optional.get().getQuantity());
            }
        }

        if (totalLoveLace == null) totalLoveLace = BigInteger.ZERO;

        if (minCost != null && totalLoveLace.compareTo(minCost) != 1) {
            BigInteger additionalAmt = minCost.subtract(totalLoveLace).add(BigInteger.ONE); //add one for safer side
            List<Utxo> additionalUtxos = getUtxos(sender, LOVELACE, additionalAmt);
            if (additionalUtxos == null || additionalUtxos.size() == 0)
                throw new ApiException(String.format("No utxos found for address for additional amount: %s, unit: %s, amount: %s", sender, LOVELACE, additionalAmt));

            utxos.addAll(additionalUtxos);
        }
    }

    private Map<String, Set<Utxo>> getSenderToUtxosMapFromTransactions(List<PaymentTransaction> transactions) {
        Map<String, Set<Utxo>> senderToUtxoMap = new HashMap<>();
        for (PaymentTransaction paymentTransaction : transactions) {
            if (paymentTransaction.getUtxosToInclude() != null && paymentTransaction.getUtxosToInclude().size() > 0) {
                String senderAddress = paymentTransaction.getSender().baseAddress();
                Set<Utxo> utxos = senderToUtxoMap.get(senderAddress);
                if (utxos == null) {
                    utxos = new HashSet<>(paymentTransaction.getUtxosToInclude());
                    senderToUtxoMap.put(senderAddress, utxos);
                } else {
                    utxos.addAll(paymentTransaction.getUtxosToInclude());
                }
            }
        }

        return senderToUtxoMap;
    }

    private Map<String, Set<Utxo>> getSenderToUtxosMap(Multimap<String, Amount> senderAmountsMap) throws ApiException {
        Map<String, Set<Utxo>> senderToUtxoMap = new HashMap<>();
        for (String sender : senderAmountsMap.keySet()) { //Get all Utxos for all transactions
            Collection<Amount> amts = senderAmountsMap.get(sender);
            if (amts == null || amts.size() == 0) continue;

            Set<Utxo> utxoSet = new HashSet<>();
            for (Amount amt : amts) {
                //Get utxos
                List<Utxo> utxos = getUtxos(sender, amt.getUnit(), amt.getQuantity());
                if (utxos == null || utxos.size() == 0)
                    throw new ApiException("No utxos found for address : " + sender);
                utxos.forEach(utxo -> {
                    utxoSet.add(utxo);
                });
            }
            senderToUtxoMap.put(sender, utxoSet);
        }

        return senderToUtxoMap;
    }

    private BigInteger createReceiverOutputsAndPopulateCostV2(List<PaymentTransaction> transactions, TransactionDetailsParams detailsParams, BigInteger totalFee,
                                                              List<TransactionOutput> transactionOutputs, Map<String, BigInteger> senderMiscCostMap, ProtocolParams protocolParams) {

        List<Tuple<TransactionOutput, BigInteger>> outputs = transactions
                .stream()
                .collect(Collectors.groupingBy(paymentTransaction -> new PaymentTransactionGroupingKey(paymentTransaction.getSender(), paymentTransaction.getReceiver(), paymentTransaction.getDatumHash())))
                .entrySet()
                .stream()
                .map(entry -> {
                    Value value = entry
                            .getValue()
                            .stream()
                            .map(tx -> {
                                if (LOVELACE.equals(tx.getUnit())) {
                                    return Value.builder().coin(tx.getAmount()).multiAssets(Arrays.asList()).build();
                                } else {
                                    Tuple<String, String> policyIdAssetName = AssetUtil.getPolicyIdAndAssetName(tx.getUnit());
                                    Asset asset = new Asset(policyIdAssetName._2, tx.getAmount());
                                    MultiAsset multiAsset = new MultiAsset(policyIdAssetName._1, Arrays.asList(asset));
                                    return Value.builder().coin(BigInteger.ZERO).multiAssets(Arrays.asList(multiAsset)).build();
                                }
                            })
                            .reduce(Value.builder().coin(BigInteger.ZERO).build(), Value::plus);

                    // TxOut before min ada adjustment
                    TransactionOutput draftTxOut = TransactionOutput.builder().address(entry.getKey().getReceiver()).value(value).build();

                    // Calculate required minAda
                    BigInteger minRequiredAda = new MinAdaCalculator(protocolParams).calculateMinAda(draftTxOut);

                    // Get the max between the minAda and what the user wanted to send
                    BigInteger actualCoin = minRequiredAda.max(draftTxOut.getValue().getCoin());

                    // The final value to send (value is ada + all multi assets)
                    Value finalValue = Value.builder().coin(actualCoin).multiAssets(draftTxOut.getValue().getMultiAssets()).build();

                    // Sum user's fee (if specified)
                    BigInteger fees = entry.getValue().stream().map(PaymentTransaction::getFee).reduce(BigInteger.ZERO, BigInteger::add);

                    // Costs
                    BigInteger existingMiscCost = senderMiscCostMap.getOrDefault(entry.getKey().getSender().baseAddress(), BigInteger.ZERO);

                    // Calculating if extra costs are required (diff between minAda and actual ada, and add to costs)
                    BigInteger additionalCost = actualCoin.subtract(draftTxOut.getValue().getCoin());
                    existingMiscCost = existingMiscCost.add(additionalCost);

                    BigInteger costs = entry.getValue().stream().map(PaymentTransaction::getFee).reduce(BigInteger.ZERO, BigInteger::add);
                    existingMiscCost = existingMiscCost.add(costs);

                    // Update costs per (sender) base address
                    senderMiscCostMap.put(entry.getKey().getSender().baseAddress(), existingMiscCost);

                    byte[] datumHash = null;
                    if (!Strings.isNullOrEmpty(entry.getKey().getDatumHash())) {
                        datumHash = HexUtil.decodeHexString(entry.getKey().getDatumHash());
                    }

                    return new Tuple<>(TransactionOutput.builder().address(entry.getKey().getReceiver()).value(finalValue).datumHash(datumHash).build(), fees);
                })
                .collect(Collectors.toList());


        totalFee = totalFee.add(transactions.stream().map(PaymentTransaction::getFee).reduce(BigInteger.ZERO, BigInteger::add));

        transactionOutputs.addAll(outputs.stream().map(tuple -> tuple._1).collect(Collectors.toList()));

        return totalFee;
    }


    /**
     * Deprecated, see createReceiverOutputsAndPopulateCostV2
     *
     * @param transaction
     * @param detailsParams
     * @param totalFee
     * @param transactionOutputs
     * @param senderMiscCostMap
     * @param protocolParams
     * @return
     */
    @Deprecated
    private BigInteger createReceiverOutputsAndPopulateCost(PaymentTransaction transaction, TransactionDetailsParams detailsParams, BigInteger totalFee,
                                                            List<TransactionOutput> transactionOutputs, Map<String, BigInteger> senderMiscCostMap, ProtocolParams protocolParams) {
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

            //Dummy value for min required ada calculation
            outputBuilder.value(new Value(BigInteger.ZERO, Arrays.asList(multiAsset)));
            //Calculate required minAda
            BigInteger minRequiredAda =
                    new MinAdaCalculator(protocolParams).calculateMinAda(outputBuilder.build());

            //set minRequiredAdaToValue
            outputBuilder.value(new Value(minRequiredAda, Arrays.asList(multiAsset)));

            existingMiscCost = existingMiscCost.add(minRequiredAda);
        }

        existingMiscCost = existingMiscCost.add(transaction.getFee());
        senderMiscCostMap.put(transaction.getSender().baseAddress(), existingMiscCost);

        totalFee = totalFee.add(transaction.getFee());

        //Add datum hash. Should be used only for receiver as script address
        if (transaction.getDatumHash() != null && !transaction.getDatumHash().isEmpty()) {
            outputBuilder.datumHash(HexUtil.decodeHexString(transaction.getDatumHash()));
        }

        transactionOutputs.add(outputBuilder.build());
        return totalFee;
    }

    private void buildOuputsForSenderFromUtxos(String sender, Set<Utxo> utxoSet, List<TransactionInput> transactionInputs,
                                               List<TransactionOutput> transactionOutputs, Multimap<String, Amount> senderAmountsMap,
                                               Map<String, BigInteger> senderMiscCostMap, TransactionDetailsParams detailsParams, ProtocolParams protocolParams) throws ApiException {
        TransactionOutput changeOutput = new TransactionOutput(sender, new Value());
        //Initial sender txnoutput with negative amount
        senderAmountsMap.get(sender).stream()
                .forEach(amount -> {
                    if (LOVELACE.equals(amount.getUnit())) {
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

        if ((changeOutput.getValue().getCoin() != null && changeOutput.getValue().getCoin().compareTo(BigInteger.ZERO) == 1) ||
                (changeOutput.getValue().getMultiAssets().size() > 0)) {

            //deduct misc cost (fee + min ada value)
            BigInteger misCostVal = senderMiscCostMap.get(changeOutput.getAddress());
            BigInteger afterMisCost = changeOutput.getValue().getCoin().subtract(misCostVal);
            changeOutput.getValue().setCoin(afterMisCost);

            //Check if minimum Ada is not met. Topup
            //Transaction will fail if minimum ada not there. So try to get some additional utxos
            verifyMinAdaInOutputAndUpdateIfRequired(transactionInputs, changeOutput, detailsParams, utxoSet, protocolParams);

            //If changeOutput value is not zero or there are multi-assets, then add to change output
            if (BigInteger.ZERO.compareTo(changeOutput.getValue().getCoin()) != 0
                    || (changeOutput.getValue().getMultiAssets() != null && changeOutput.getValue().getMultiAssets().size() > 0)) {
                transactionOutputs.add(changeOutput);
            }

            if (BigInteger.ZERO.compareTo(changeOutput.getValue().getCoin()) == 0 &&
                    changeOutput.getValue().getMultiAssets() != null && changeOutput.getValue().getMultiAssets().size() > 0) {
                log.warn("The sender address balance cannot be zero as the sender has {} native token(s).", changeOutput.getValue().getMultiAssets().size());
            }
        }
    }

    //TODO remove later
    /*
    private void checkAdditionalUtxoIfRequired(Set<Utxo> utxoSet, List<TransactionInput> transactionInputs, TransactionDetailsParams detailsParams, TransactionOutput changeOutput) throws ApiException {
        BigInteger minRequiredLovelaceInOutput = new MinAdaCalculator(detailsParams.getMinUtxoValue()).calculateMinAda(changeOutput);//getMinUtxoValue(detailsParams);
        if(changeOutput.getValue().getCoin() != null && minRequiredLovelaceInOutput.compareTo(changeOutput.getValue().getCoin()) == 1) {
            //Get utxos
            List<Utxo> additionalUtxos = getUtxos(changeOutput.getAddress(), LOVELACE, minRequiredLovelaceInOutput, utxoSet);
            if(additionalUtxos == null || additionalUtxos.size() == 0)
                throw new InsufficientBalanceException("Not enough utxos found to cover minimum lovelace in an ouput");

            if(log.isDebugEnabled())
                log.debug("Additional Utxos found: " + additionalUtxos);
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
    }*/

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
        if (multiAssets != null && multiAssets.size() > 0) {
            multiAssets.forEach(ma -> {
                if (ma.getAssets() == null || ma.getAssets().size() == 0)
                    markedForRemoval.add(ma);
            });

            multiAssets.removeAll(markedForRemoval);
        }
    }

    private Multimap<String, Amount> calculateRequiredBalancesForSenders(List<PaymentTransaction> transactions) {
        Multimap<String, Amount> senderAmountMap = ArrayListMultimap.create();
        for (PaymentTransaction transaction : transactions) {
            String sender = transaction.getSender().baseAddress();
            String unit = transaction.getUnit();
            BigInteger amount = transaction.getAmount();

            addAmountToSenderAmountMap(senderAmountMap, sender, unit, amount);
        }

        return senderAmountMap;
    }

    private void addAmountToSenderAmountMap(Multimap<String, Amount> senderAmountMap, String sender, String unit, BigInteger amount) {
        Collection<Amount> amounts = senderAmountMap.get(sender);
        if (amounts != null && amounts.size() > 0) {
            Optional<Amount> existingAmtOptional = amounts.stream().filter(amt -> unit.equals(amt.getUnit())).findFirst();

            if (existingAmtOptional.isPresent()) {
                Amount existingAmt = existingAmtOptional.get();
                existingAmt.setQuantity(existingAmt.getQuantity().add(amount));
            } else {
                senderAmountMap.put(sender, new Amount(unit, amount));
            }
        } else {
            senderAmountMap.put(sender, new Amount(unit, amount));
        }
    }

    private BigInteger createDummyOutputAndCalculateMinAdaForTxnOutput(String address, List<MultiAsset> multiAssets, ProtocolParams protocolParams) {
        TransactionOutput txnOutput = new TransactionOutput();
        //Dummy address
        txnOutput.setAddress(address);
        txnOutput.setValue(new Value(BigInteger.ZERO, multiAssets));

        return new MinAdaCalculator(protocolParams).calculateMinAda(txnOutput);
    }

}
