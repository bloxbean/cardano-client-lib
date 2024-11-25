package com.bloxbean.cardano.client.api.helper.impl;

import com.bloxbean.cardano.client.api.exception.InsufficientBalanceException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.common.CardanoConstants;
import com.bloxbean.cardano.client.common.MinAdaCalculator;
import com.bloxbean.cardano.client.transaction.model.MintTransaction;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.model.TransactionRequest;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.Triple;
import com.bloxbean.cardano.client.util.Tuple;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class UtxoTransactionBodyBuilder {

    public static TransactionBody buildMintBody(MintTransaction request,
                                                TransactionDetailsParams detailsParams,
                                                ProtocolParams protocolParams,
                                                UtxoSelectionStrategy selectionStrategy) {
        String sender = request.getSender().baseAddress();

        String receiver = request.getReceiver();
        if (receiver == null){
            receiver = sender;
        }

        var minBaseAmount = new MinAdaCalculator(protocolParams)
                .calculateMinAda(request.getMintAssets());
        BigInteger totalCost = minBaseAmount.add(request.getFee());
        // determine UTXOs for min cost
        var utxos = request.getUtxosToInclude() != null ? new HashSet<>(request.getUtxosToInclude()) : new HashSet<Utxo>();
        if(utxos.isEmpty()){
            utxos.addAll(selectionStrategy.select(sender, new Amount(CardanoConstants.LOVELACE, totalCost), Collections.emptySet()));
        }
        if (utxos.isEmpty()){
            throw new InsufficientBalanceException("Not enough utxos found to cover mint balance: " + totalCost + " lovelace");
        }

        List<TransactionInput> transactionInputs = new ArrayList<>();
        List<TransactionOutput> transactionOutputs = new ArrayList<>();

        // add input transaction for each utxo
        for(var utxo : utxos){
            TransactionInput transactionInput = new TransactionInput(utxo.getTxHash(), utxo.getOutputIndex());
            transactionInputs.add(transactionInput);
        }
        // add change transactions
        CalculatedChangeTransactions calculatedChangeTransactions = calculateChangeTransactions(sender, Collections.singletonList(new Amount(CardanoConstants.LOVELACE, BigInteger.ZERO)), utxos, totalCost, protocolParams, selectionStrategy);
        transactionOutputs.addAll(calculatedChangeTransactions.getTransactionOutputs());
        transactionInputs.addAll(calculatedChangeTransactions.getTransactionInputs());
        utxos.addAll(calculatedChangeTransactions.getAdditionalUtxos());

        //Create a separate output for minted assets
        //Create output
        TransactionOutput mintedTransactionOutput = new TransactionOutput(receiver,
                new Value(minBaseAmount,
                          request.getMintAssets()));
        mintedTransactionOutput.setDatumHash(request.getDatumHash() != null ? HexUtil.decodeHexString(request.getDatumHash()) : null);

        transactionOutputs.add(mintedTransactionOutput);

        return new TransactionBody(transactionInputs,
                transactionOutputs,
                request.getFee(),
                detailsParams != null ? detailsParams.getTtl() : 0L,
                null,
                null,
                null,
                null,
                detailsParams != null ? detailsParams.getValidityStartInterval() : 0L,
                request.getMintAssets(),
                null,
                null,
                null,
                detailsParams != null ? detailsParams.getNetworkId() : null,
                null,
                null,
                null, null, null, null, null);
    }

    public static TransactionBody buildTransferBody(List<PaymentTransaction> requests,
                                                    TransactionDetailsParams detailParams,
                                                    ProtocolParams protocolParams,
                                                    UtxoSelectionStrategy selectionStrategy){
        // Create output for receivers and calculate total fees/cost for each sender
        var calculatedOutputs = calculateOutputs(requests, protocolParams);

        // calculate for each sender how much amount is required
        var requestedAmountPerSender = getRequestedAmountPerSender(requests);

        // Get sender -> utxos map based on the unit and total qty requirement
        var utxosPerSender = getUtxosPerSenderFromRequests(requests);
        if(utxosPerSender.isEmpty()){
            var costPerSender = calculatedOutputs.getMiscCostPerSender();
            var requestedAmountPerSenderIncludingCost = mergeCosts(costPerSender, requestedAmountPerSender);
            utxosPerSender.putAll(getUtxosPerSender(requestedAmountPerSenderIncludingCost,
                                                    selectionStrategy));
        }

        // Check if min cost is there in all selected Utxos and add additional UTXOs if it isn't
        for(String sender: calculatedOutputs.getMiscCostPerSender().keySet()) {
            BigInteger minCost = calculatedOutputs.getMiscCostPerSender().get(sender);
            Set<Utxo> utxos = utxosPerSender.computeIfAbsent(sender, key -> new HashSet<>());
            Set<Utxo> additionalUtxos = calculateAdditionalUtxos(sender, minCost, utxos, selectionStrategy);
            utxos.addAll(additionalUtxos);
        }

        // handle change (to send back to self)
        // Go through sender Utxos, Build Inputs first from Utxos and then change outputs
        var transactionOutputs = new ArrayList<>(calculatedOutputs.getTransactionOutputs());
        var transactionInputs = new ArrayList<TransactionInput>();
        for(var entry : utxosPerSender.entrySet()){
            String sender = entry.getKey(); //Sender and it's utxos
            Set<Utxo> usedUtxos = entry.getValue();
            // add input transaction for each utxo
            for(var utxo : usedUtxos){
                TransactionInput transactionInput = new TransactionInput(utxo.getTxHash(), utxo.getOutputIndex());
                transactionInputs.add(transactionInput);
            }
            // add change transactions
            CalculatedChangeTransactions calculatedChangeTransactions = calculateChangeTransactions(sender, requestedAmountPerSender.get(sender), usedUtxos, calculatedOutputs.getMiscCostPerSender().get(sender), protocolParams, selectionStrategy);
            transactionOutputs.addAll(calculatedChangeTransactions.getTransactionOutputs());
            transactionInputs.addAll(calculatedChangeTransactions.getTransactionInputs());
            usedUtxos.addAll(calculatedChangeTransactions.getAdditionalUtxos());
        }

        return new TransactionBody(transactionInputs,
                transactionOutputs,
                calculatedOutputs.getTotalFee(),
                detailParams != null ? detailParams.getTtl() : 0L,
                null,
                null,
                null,
                null,
                detailParams != null ? detailParams.getValidityStartInterval() : 0L,
                null,
                null,
                null,
                null,
                detailParams != null ? detailParams.getNetworkId() : null,
                null,
                null,
                null, null, null, null, null);
    }

    private static Map<String, List<Amount>> mergeCosts(Map<String, BigInteger> costPerSender, Map<String, List<Amount>> requestedAmountPerSender){
        if(costPerSender == null || costPerSender.isEmpty()){
            return requestedAmountPerSender;
        }
        if(requestedAmountPerSender == null || requestedAmountPerSender.isEmpty()){
            return costPerSender.entrySet().stream()
                    .map(entry -> new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), Collections.singletonList(new Amount(CardanoConstants.LOVELACE, entry.getValue()))))
                    .collect(Collectors.toMap(AbstractMap.SimpleImmutableEntry::getKey, AbstractMap.SimpleImmutableEntry::getValue));
        }
        return Stream.concat(requestedAmountPerSender.entrySet().stream(),
                             costPerSender.entrySet().stream()
                                          .map(entry -> new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), Collections.singletonList(new Amount(CardanoConstants.LOVELACE, entry.getValue())))))
                     .collect(Collectors.groupingBy(Map.Entry::getKey,
                              Collectors.flatMapping(entry -> entry.getValue().stream(), Collectors.toList())))
                     .entrySet().stream()
                     .map(entry -> new AbstractMap.SimpleImmutableEntry<>(entry.getKey(),
                                            entry.getValue().stream()
                                                 .collect(Collectors.groupingBy(Amount::getUnit,
                                                          Collectors.reducing(BigInteger.ZERO,
                                                                  Amount::getQuantity,
                                                                              BigInteger::add)))
                                                 .entrySet().stream().map(val -> new Amount(val.getKey(), val.getValue()))
                                                 .collect(Collectors.toList())))
                .collect(Collectors.toMap(AbstractMap.SimpleImmutableEntry::getKey, AbstractMap.SimpleImmutableEntry::getValue));
    }

    // initialize base + multi assets with negative amount (based on what was requested)
    private static OutputAmount initChangeAmount(List<Amount> requestedAmounts){
        BigInteger baseAmount = requestedAmounts.stream()
                .filter(amount -> CardanoConstants.LOVELACE.equals(amount.getUnit()))
                .map(amount -> amount.getQuantity())
                .reduce(BigInteger.ZERO, BigInteger::subtract);
        List<MultiAsset> multiAssets = requestedAmounts.stream()
                .filter(amount -> !CardanoConstants.LOVELACE.equals(amount.getUnit()))
                .map(amount -> new Tuple<>(AssetUtil.getPolicyIdAndAssetName(amount.getUnit()), amount.getQuantity()))
                // group by policy id
                .collect(Collectors.groupingBy(tuple -> tuple._1._1,
                         Collectors.groupingBy(tuple -> tuple._1._2,
                         Collectors.reducing(
                                 BigInteger.ZERO,
                                 tuple -> tuple._2,
                                 BigInteger::add))
                         )).entrySet().stream()
                .map(entry -> new MultiAsset(entry.getKey(), entry.getValue().entrySet().stream()
                        .map(valueEntry -> new Asset(valueEntry.getKey(), valueEntry.getValue().negate()))
                        .collect(Collectors.toList())))
                .collect(Collectors.toList());
        return new OutputAmount(baseAmount, multiAssets);
    }
    private static Asset toAsset(Amount amount){
        return amount != null ? new Asset(AssetUtil.getPolicyIdAndAssetName(amount.getUnit())._2, amount.getQuantity()) : null;
    }
    private static OutputAmount applyUtxoToChangeAmount(OutputAmount outputAmount, Set<Utxo> utxos){
        BigInteger baseAmount = outputAmount.getBaseAmount();
        List<MultiAsset> multiAssets = outputAmount.getMultiAssets();
        for(var utxo : utxos){
            for(var utxoAmt : utxo.getAmount()) { //For each amt in utxo
                String utxoUnit = utxoAmt.getUnit();
                BigInteger utxoQty = utxoAmt.getQuantity();
                if (utxoUnit.equals(CardanoConstants.LOVELACE)) {
                    baseAmount = baseAmount.add(utxoQty);
                } else {
                    Tuple<String, String> policyIdAssetName = AssetUtil.getPolicyIdAndAssetName(utxoUnit);
                    //Find if the policy id is available
                    var currentMultiAssets = new ArrayList<>(multiAssets);
                    var matchingMultiAsset = multiAssets.stream()
                            .filter(ma -> policyIdAssetName._1.equals(ma.getPolicyId())).findFirst();
                    if(matchingMultiAsset.isPresent()){
                        var assetAmountsForPolicy = new ArrayList<>(matchingMultiAsset.get().getAssets());
                        var matchingAsset = matchingMultiAsset.get().getAssets()
                                .stream().filter(assetAmount -> policyIdAssetName._2.equals(assetAmount.getName()))
                                .findFirst();
                        if(matchingAsset.isPresent()){
                            // update matchingAsset
                            assetAmountsForPolicy.remove(matchingAsset.get());
                            assetAmountsForPolicy.add(matchingAsset.get().add(toAsset(utxoAmt)));
                        }else{
                            // add new asset to matchingMultiAsset
                            assetAmountsForPolicy.add(new Asset(policyIdAssetName._2, utxoQty));
                        }
                        currentMultiAssets.remove(matchingMultiAsset.get());
                        currentMultiAssets.add(new MultiAsset(matchingMultiAsset.get().getPolicyId(), assetAmountsForPolicy));
                        multiAssets.clear();
                        multiAssets.addAll(currentMultiAssets);
                    }else{
                        // add new
                        multiAssets.add(AssetUtil.getMultiAssetFromUnitAndAmount(utxoUnit, utxoQty));
                    }
                }
            }
            //Remove any empty MultiAssets
            multiAssets = multiAssets.stream()
                    .map(assets -> new MultiAsset(assets.getPolicyId(),
                                                  assets.getAssets() != null
                                                    ? assets.getAssets().stream()
                                                                            .filter(asset -> asset.getValue() != null && !BigInteger.ZERO.equals(asset.getValue()))
                                                                            .collect(Collectors.toList())
                                                    : Collections.emptyList()))
                    .filter(assets -> assets.getAssets() != null && !assets.getAssets().isEmpty())
                    .collect(Collectors.toList());
        }
        return new OutputAmount(baseAmount, multiAssets);
    }
    private static CalculatedChangeTransactions calculateChangeTransactions(String sender, List<Amount> requestedAmounts, Set<Utxo> usedUtxos, BigInteger miscCost, ProtocolParams protocolParams, UtxoSelectionStrategy selectionStrategy){
        var transactionOutputs = new ArrayList<TransactionOutput>();
        var transactionInputs = new ArrayList<TransactionInput>();
        var additionalUtxos = new ArrayList<Utxo>();
        usedUtxos = new HashSet<>(usedUtxos);

        // initialize base + multi assets with negative amount (based on what was requested)
        var changeAmount = initChangeAmount(requestedAmounts);
        // then add each utxo to negative amounts
        changeAmount = applyUtxoToChangeAmount(changeAmount, usedUtxos);
        // now we have the change amounts in baseAmount and multiAssets (for 1 sender)
        if(changeAmount.getBaseAmount().compareTo(BigInteger.ZERO) > 0
                || !changeAmount.getMultiAssets().isEmpty()) {

            //deduct misc cost (fee + min ada value)
            changeAmount = new OutputAmount(changeAmount.getBaseAmount().subtract(miscCost), changeAmount.getMultiAssets());
            //Check if minimum Ada is not met. Topup
            //Transaction will fail if minimun ada not there. So try to get some additional utxos
            if(changeAmount.getBaseAmount().compareTo(BigInteger.ZERO) != 0
                    || !changeAmount.getMultiAssets().isEmpty()) {
                BigInteger minRequiredLovelaceInOutput =
                        new MinAdaCalculator(protocolParams)
                                .calculateMinAda(new TransactionOutput(sender, new Value(changeAmount.getBaseAmount(), changeAmount.getMultiAssets())));
                // then select UTXO we can use for this extra output
                while(minRequiredLovelaceInOutput.compareTo(changeAmount.getBaseAmount()) > 0) {
                    //Get utxos
                    var allUsedUtxos = new HashSet<>(usedUtxos);
                    allUsedUtxos.addAll(additionalUtxos);
                    Set<Utxo> newUtxos = calculateUtxos(sender, Collections.singletonList(new Amount(CardanoConstants.LOVELACE, minRequiredLovelaceInOutput.subtract(changeAmount.getBaseAmount()))), allUsedUtxos, selectionStrategy);
                    if(newUtxos.isEmpty()) {
                        if(log.isDebugEnabled()) {
                            log.warn("Not enough utxos found to cover minimum lovelace in an output");
                        }
                        throw new InsufficientBalanceException("Not enough utxos found to cover minimum lovelace in an output");
                    }

                    if(log.isDebugEnabled()){
                        log.debug("Additional Utoxs found: " + newUtxos);
                    }

                    for(Utxo addUtxo: newUtxos) {
                        TransactionInput addTxnInput = new TransactionInput(addUtxo.getTxHash(), addUtxo.getOutputIndex());
                        transactionInputs.add(addTxnInput);
                    }
                    changeAmount = applyUtxoToChangeAmount(changeAmount, newUtxos);
                    additionalUtxos.addAll(newUtxos);

                    //Calculate final minReq balance in output, if still doesn't satisfy, continue again
                    minRequiredLovelaceInOutput =
                            new MinAdaCalculator(protocolParams)
                                    .calculateMinAda(new TransactionOutput(sender, new Value(changeAmount.getBaseAmount(), changeAmount.getMultiAssets())));
                }
            }

            //If changeoutput value is not zero or there are multi-assets, then add to change output
            if(BigInteger.ZERO.compareTo(changeAmount.getBaseAmount()) < 0 || !changeAmount.getMultiAssets().isEmpty()) {
                transactionOutputs.add(new TransactionOutput(sender, new Value(changeAmount.getBaseAmount(), changeAmount.getMultiAssets())));
            }

            if(BigInteger.ZERO.compareTo(changeAmount.getBaseAmount()) == 0 &&
                    !changeAmount.getMultiAssets().isEmpty()) {
                log.warn("The sender address balance cannot be zero as the sender has {} native token(s).", changeAmount.getMultiAssets().size());
            }
        }

        return new CalculatedChangeTransactions(transactionInputs, transactionOutputs, additionalUtxos);
    }

    private static Set<Utxo> calculateAdditionalUtxos(String sender, BigInteger required, Set<Utxo> currentUtxos, UtxoSelectionStrategy selectionStrategy){
        BigInteger totalLoveLace = currentUtxos.stream()
                .flatMap(utxo -> utxo.getAmount().stream())
                .filter(amt -> CardanoConstants.LOVELACE.equals(amt.getUnit()))
                .map(Amount::getQuantity)
                .reduce(BigInteger.ZERO, BigInteger::add);

        if(required != null && totalLoveLace.compareTo(required) < 0) {
            BigInteger additionalAmt = required.subtract(totalLoveLace).add(BigInteger.ONE); //add one for safer side
            return calculateUtxos(sender, Collections.singletonList(new Amount(CardanoConstants.LOVELACE, additionalAmt)), currentUtxos, selectionStrategy);
        }
        return Collections.emptySet();
    }
    private static Set<Utxo> calculateUtxos(String sender, List<Amount> required, Set<Utxo> currentUtxos, UtxoSelectionStrategy selectionStrategy){
        // remove already used utxos
        var newUtxos = selectionStrategy.select(sender, required, currentUtxos);

        if(newUtxos == null || newUtxos.isEmpty()){
            throw new InsufficientBalanceException(String.format("No utxos found for address for additional amount: %s, unit: %s, amount: %s", sender, CardanoConstants.LOVELACE, required));
        }
        return newUtxos;
    }
    private static boolean isEqualString(String s1, String s2){
        if (s1 == s2) {
            return true;
        }
        if (s1 == null || s2 == null) {
            return false;
        }
        return s1.equals(s2);
    }
    private static OutputAmount groupRequestedOutput(List<PaymentTransaction> requests){
        BigInteger baseAmount = BigInteger.ZERO;
        List<MultiAsset> multiAssets = new ArrayList<>();
        for(var transaction : requests){
            if (CardanoConstants.LOVELACE.equals(transaction.getUnit())) {
                baseAmount = baseAmount.add(transaction.getAmount());
            } else {
                Tuple<String, String> policyIdAssetName = AssetUtil.getPolicyIdAndAssetName(transaction.getUnit());
                Amount asset = new Amount(policyIdAssetName._2, transaction.getAmount());

                var existingMultiAsset = multiAssets.stream()
                        .filter(ma -> isEqualString(ma.getPolicyId(), policyIdAssetName._1))
                        .findFirst();
                if(existingMultiAsset.isPresent()){
                    var allAssetsInMulti = new ArrayList<>(existingMultiAsset.get().getAssets());
                    var existingAmount = allAssetsInMulti.stream()
                            .filter(assetAmount -> isEqualString(assetAmount.getName(), asset.getUnit()))
                            .findFirst();
                    if(existingAmount.isPresent()){
                        // adjust existing amount
                        allAssetsInMulti.remove(existingAmount.get());
                        allAssetsInMulti.add(toAsset(new Amount(asset.getUnit(), existingAmount.get().getValue() != null ? existingAmount.get().getValue().add(asset.getQuantity()) : asset.getQuantity())));
                    }else{
                        // add new amount
                        allAssetsInMulti.add(toAsset(asset));
                    }
                    // remove existing multi asset
                    multiAssets.remove(existingMultiAsset.get());
                    // add new one
                    multiAssets.add(new MultiAsset(policyIdAssetName._1, allAssetsInMulti));
                }else{
                    // add new multi asset
                    multiAssets.add(AssetUtil.getMultiAssetFromUnitAndAmount(transaction.getUnit(), transaction.getAmount()));
                }
            }
        }
        return new OutputAmount(baseAmount, multiAssets);
    }
    private static TransactionOutput calculateOutputForGroup(PaymentTransactionGroupingKey groupingKey, List<PaymentTransaction> requests, OutputAmount groupedOutputAmount, ProtocolParams protocolParams){
        // Calculate required minAda
        BigInteger minRequiredAda = new MinAdaCalculator(protocolParams)
                .calculateMinAda(new TransactionOutput(groupingKey.getReceiver(), new Value(groupedOutputAmount.getBaseAmount(), groupedOutputAmount.getMultiAssets())));

        // Get the max between the minAda and what the user wanted to send
        BigInteger actualCoin = minRequiredAda.max(groupedOutputAmount.getBaseAmount());

        // The final value to send (value is ada + all multi assets)
        var outputAmount = new OutputAmount(actualCoin, groupedOutputAmount.getMultiAssets());

        byte[] datumHash = null;
        if (groupingKey.getDatumHash() != null) {
            datumHash = HexUtil.decodeHexString(groupingKey.getDatumHash());
        }
        var output = new TransactionOutput(groupingKey.getReceiver(), new Value(outputAmount.getBaseAmount(), outputAmount.getMultiAssets()));
        output.setDatumHash(datumHash);
        return output;
    }
    private static BigInteger calculateAdditionalCostForGroup(List<PaymentTransaction> requests, OutputAmount groupedOutputAmount, ProtocolParams protocolParams){
        // Calculate required minAda
        BigInteger minRequiredAda = new MinAdaCalculator(protocolParams)
                .calculateMinAda(new TransactionOutput(null, new Value(groupedOutputAmount.getBaseAmount(), groupedOutputAmount.getMultiAssets())));
        // Get the max between the minAda and what the user wanted to send
        BigInteger actualCoin = minRequiredAda.max(groupedOutputAmount.getBaseAmount());
        // The final value to send (value is ada + all multi assets)
        var outputAmount = new OutputAmount(actualCoin, groupedOutputAmount.getMultiAssets());
        // Sum user's fee (if specified)
        BigInteger fees = requests.stream().map(TransactionRequest::getFee)
                .filter(Objects::nonNull)
                .reduce(BigInteger.ZERO, BigInteger::add);
        // Calculating if extra costs are required (diff between minAda and actual ada, and add to costs) + add fee
        return actualCoin.subtract(groupedOutputAmount.getBaseAmount())
                         .add(fees);
    }

    private static CalculatedOutputs calculateOutputs(List<PaymentTransaction> requests, ProtocolParams protocolParams){
        // group tx by key (sender - receiver - datumhash)
        var grouped = requests
                .stream()
                .collect(Collectors.groupingBy(paymentTransaction -> new PaymentTransactionGroupingKey(paymentTransaction.getSender().baseAddress(), paymentTransaction.getReceiver(), paymentTransaction.getDatumHash())))
                .entrySet().stream()
                // group requested outputs
                .map(entry -> new Triple<>(entry.getKey(), entry.getValue(), groupRequestedOutput(entry.getValue())))
                .collect(Collectors.toList());
        // tx outputs
        var outputs = grouped.stream()
                             .map(triple -> calculateOutputForGroup(triple._1, triple._2, triple._3, protocolParams))
                             .collect(Collectors.toList());
        // fees + non requested min ada requirements
        var miscCostsPerSender = grouped.stream()
               .map(triple -> new Tuple<>(triple._1.getSender(), calculateAdditionalCostForGroup(triple._2, triple._3, protocolParams)))
               .collect(Collectors.groupingBy(tuple -> tuple._1,
                        Collectors.reducing(BigInteger.ZERO,
                                tuple -> tuple._2,
                                            BigInteger::add)));
        var totalFee = requests.stream()
                .filter(request -> request.getFee() != null)
                .map(TransactionRequest::getFee)
                .reduce(BigInteger.ZERO, BigInteger::add);
        return new CalculatedOutputs(miscCostsPerSender, outputs, totalFee);
    }
    private static Map<String, Set<Utxo>> getUtxosPerSender(Map<String, List<Amount>> requestedAmountPerSender,
                                                            UtxoSelectionStrategy selectionStrategy) {
        return requestedAmountPerSender.entrySet().stream()
                .map(entry -> new AbstractMap.SimpleImmutableEntry<>(entry.getKey(),
                                        selectionStrategy.select(entry.getKey(), entry.getValue(), Collections.emptySet())))
                .collect(Collectors.toMap(AbstractMap.SimpleImmutableEntry::getKey, AbstractMap.SimpleImmutableEntry::getValue));
    }
    private static Map<String, List<Amount>> getRequestedAmountPerSender(List<PaymentTransaction> requests) {
        // group by sender
        return requests.stream()
                .collect(Collectors.groupingBy(req -> req.getSender().baseAddress(),
                         Collectors.groupingBy(
                                 PaymentTransaction::getUnit,
                                Collectors.reducing(
                                        BigInteger.ZERO,
                                        PaymentTransaction::getAmount,
                                        BigInteger::add))))
                .entrySet().stream()
                .map(entry -> new AbstractMap.SimpleImmutableEntry<>(entry.getKey(),
                                                            entry.getValue().entrySet()
                                                                 .stream().map(pair -> new Amount(pair.getKey(),
                                                                                                       pair.getValue()))
                                                                 .filter(amount -> amount.getQuantity() != null && amount.getQuantity().compareTo(BigInteger.ZERO) > 0)
                                                                 .collect(Collectors.toList())))
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .collect(Collectors.toMap(AbstractMap.SimpleImmutableEntry::getKey, AbstractMap.SimpleImmutableEntry::getValue));
    }
    private static Map<String, Set<Utxo>> getUtxosPerSenderFromRequests(List<? extends TransactionRequest> transactions) {
        return transactions.stream()
                .filter(tx -> tx.getUtxosToInclude() != null && !tx.getUtxosToInclude().isEmpty())
                .collect(Collectors.groupingBy(req -> req.getSender().baseAddress()))
                .entrySet().stream()
                .map(entry -> new Tuple<>(entry.getKey(), entry.getValue().stream()
                                              .flatMap(tx -> tx.getUtxosToInclude().stream())
                                              .collect(Collectors.toSet())))
                .collect(Collectors.toMap(tuple -> tuple._1, tuple -> tuple._2));
    }

    /**
     * Private data holders
     */
    @Getter
    @ToString
    @EqualsAndHashCode
    private static class OutputAmount {
        private final BigInteger baseAmount;
        private final List<MultiAsset> multiAssets = new ArrayList<>();

        public OutputAmount(BigInteger baseAmount, List<MultiAsset> multiAssets) {
            this.baseAmount = baseAmount;
            if(multiAssets != null){
                this.multiAssets.addAll(multiAssets);
            }
        }
    }
    @Getter
    @ToString
    @EqualsAndHashCode
    private static class CalculatedChangeTransactions{
        private final List<TransactionInput> transactionInputs = new ArrayList<>();
        private final List<TransactionOutput> transactionOutputs = new ArrayList<>();
        private final List<Utxo> additionalUtxos = new ArrayList<>();

        public CalculatedChangeTransactions(List<TransactionInput> transactionInputs, List<TransactionOutput> transactionOutputs, List<Utxo> additionalUtxos) {
            if(transactionInputs != null){
                this.transactionInputs.addAll(transactionInputs);
            }
            if(transactionOutputs != null){
                this.transactionOutputs.addAll(transactionOutputs);
            }
            if(additionalUtxos != null){
                this.additionalUtxos.addAll(additionalUtxos);
            }
        }
    }
    @Getter
    @ToString
    @EqualsAndHashCode
    private static class CalculatedOutputs{
        private final Map<String, BigInteger> miscCostPerSender = new HashMap<>(); //Misc cost of sender, mini ada
        private final BigInteger totalFee;
        private final List<TransactionOutput> transactionOutputs = new ArrayList<>();

        public CalculatedOutputs(Map<String, BigInteger> miscCostPerSender, List<TransactionOutput> transactionOutputs, BigInteger totalFee) {
            this.totalFee = totalFee;
            if(miscCostPerSender != null){
                this.miscCostPerSender.putAll(miscCostPerSender);
            }
            if(transactionOutputs != null){
                this.transactionOutputs.addAll(transactionOutputs);
            }
        }
    }
    @Getter
    @ToString
    @EqualsAndHashCode
    private static class PaymentTransactionGroupingKey {
        private final String sender;
        private final String receiver;
        private final String datumHash;

        public PaymentTransactionGroupingKey(String sender, String receiver, String datumHash) {
            this.sender = sender;
            this.receiver = receiver;
            this.datumHash = datumHash;
        }
    }
}
