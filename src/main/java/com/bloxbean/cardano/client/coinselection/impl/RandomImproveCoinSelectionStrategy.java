package com.bloxbean.cardano.client.coinselection.impl;

import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.coinselection.exception.InputsExhaustedException;
import com.bloxbean.cardano.client.coinselection.exception.InputsLimitExceededException;
import com.bloxbean.cardano.client.coinselection.exception.base.CoinSelectionException;
import com.bloxbean.cardano.client.coinselection.impl.model.SelectionResult;
import com.bloxbean.cardano.client.backend.common.OrderEnum;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.ProtocolParams;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.common.MinAdaCalculator;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.*;

import static java.lang.Math.abs;

//TODO - Add comment

/**
 * This is still in experimental stage. NOT READY for PRODUCTION yet.
 */
@Slf4j
public class RandomImproveCoinSelectionStrategy {

    private final UtxoService utxoService;
    private final ProtocolParams protocolParams;
    private final MinAdaCalculator minAdaCalculator;
    private int utxoFetchSize = 40;

    public RandomImproveCoinSelectionStrategy(UtxoService utxoService, ProtocolParams protocolParams) {
        this.utxoService = utxoService;
        this.protocolParams = protocolParams;
        this.minAdaCalculator = new MinAdaCalculator(protocolParams);
    }

    /**
     * Random-Improve coin selection algorithm
     *
     * @param address Sender's account address
     * @param outputs Set of outputs requested for payment.
     * @param limit   limit on the number of inputs that can be selected.
     * @return a {@link SelectionResult} with the specified Coin Selection.
     */
    public Result<SelectionResult> randomImprove(String address, Set<TransactionOutput> outputs, int limit) {
        return randomImprove(address, outputs, limit, Collections.emptySet());
    }

    /**
     * Random-Improve coin selection algorithm by excluded Utxos
     *
     * @param address      Sender's account address
     * @param outputs      Set of outputs requested for payment.
     * @param limit        limit on the number of inputs that can be selected.
     * @param excludeUtxos UTxO List to Exclude from Fetched List
     * @return a {@link SelectionResult} with the specified Coin Selection.
     */
    public Result<SelectionResult> randomImprove(String address, Set<TransactionOutput> outputs, int limit, Set<Utxo> excludeUtxos) {
        List<Utxo> inputs;
        try {
            inputs = selectUtxos(address, excludeUtxos);
        } catch (ApiException e) {
            return Result.error(e.getMessage());
        }
        if (inputs == null || inputs.isEmpty()) {
            return Result.error("NO_INPUTS");
        }
        UTxOSelection utxoSelection = new UTxOSelection(inputs);
        Value mergedOutputsAmounts = mergeOutputsAmounts(outputs);

        // Explode amount in an array of unique asset amount for comparison's sake
        List<Value> splitOutputsAmounts = splitAmounts(mergedOutputsAmounts);

        // Phase 1: Select enough input
        for (Value splitOutputsAmount : splitOutputsAmounts) {
            createSubSet(utxoSelection, splitOutputsAmount); // Narrow down for NatToken UTxO
            try {
                utxoSelection = select(utxoSelection, splitOutputsAmount, limit);
            } catch (CoinSelectionException e) {
                log.warn(e.getMessage());
                return Result.error(e.getMessage());
            }
        }

        // Phase 2: Improve
        sortAmountList(splitOutputsAmounts, OrderEnum.asc);
        for (Value splitOutputsAmount : splitOutputsAmounts) {
            createSubSet(utxoSelection, splitOutputsAmount); // Narrow down for NatToken UTxO
            Value rangeIdeal = splitOutputsAmount.plus(splitOutputsAmount);
            Value rangeMaximum = rangeIdeal.plus(splitOutputsAmount);
            improve(utxoSelection, splitOutputsAmount, limit - utxoSelection.selection.size(), rangeIdeal, rangeMaximum);
        }

        // Insure change hold enough Ada to cover included native assets and fees
        if (utxoSelection.getRemaining().size() > 0) {
            Value change = utxoSelection.getAmount().minus(mergedOutputsAmounts);

            Value minAmount = Value.builder().coin(minAdaCalculator.calculateMinAda(change.getMultiAssets())).build();
            Integer comparison = compare(change, minAmount);
            if (comparison != null && comparison < 0) {
                // Not enough, add missing amount and run select one last time
                Value minAda = minAmount.minus(Value.builder().coin(change.getCoin()).build()).plus(Value.builder().coin(utxoSelection.getAmount().getCoin()).build());
                createSubSet(utxoSelection, minAda);
                try {
                    utxoSelection = select(utxoSelection, minAda, limit);
                } catch (CoinSelectionException e) {
                    return Result.error(e.getMessage());
                }
            }
        }
        SelectionResult selectionResult = new SelectionResult(utxoSelection.getSelection(), outputs, utxoSelection.getRemaining(), utxoSelection.getAmount(), utxoSelection.getAmount().minus(mergedOutputsAmounts));
        return Result.success("Success").withValue(selectionResult);
    }

    /**
     * Returns Fetch Utxos out of Sender's Account Address after UTxO exclusion.
     *
     * @param address      Sender's account address.
     * @param excludeUtxos List of UTxOs to Exclude from Fetched List.
     * @return {@link Utxo} List out of Sender's Account Address after UTxO exclusion.
     * @throws ApiException on Api Fetch issues
     */
    public List<Utxo> selectUtxos(String address, Set<Utxo> excludeUtxos) throws ApiException {
        List<Utxo> utxos = new ArrayList<>();
        boolean canContinue = true;
        int page = 1;
        while (canContinue) {
            Result<List<Utxo>> result = utxoService.getUtxos(address, getUtxoFetchSize(), page++);
            if (result.code() == 200) {
                List<Utxo> data = result.getValue();
                if (data == null || data.isEmpty())
                    canContinue = false;
                else
                    utxos.addAll(data);
            } else {
                throw new ApiException(String.format("Unable to get enough Utxos for address : %s, reason: %s", address, result.getResponse()));
            }
        }
        utxos.removeAll(excludeUtxos);
        return utxos;
    }

    /**
     * Try to improve selection by increasing input amount in [2x,3x] range.
     *
     * @param utxoSelection The set of selected/available inputs.
     * @param outputAmount  Single compiled output qty requested for payment.
     * @param limit         A limit on the number of inputs that can be selected.
     * @param rangeIdeal    range  Improvement range target values
     * @param rangeMaximum  range  Improvement range target values
     */
    private void improve(UTxOSelection utxoSelection, Value outputAmount, int limit, Value rangeIdeal, Value rangeMaximum) {
        int nbFreeUTxO = utxoSelection.getSubset().size();
        Integer comparison = compare(utxoSelection.getAmount(), rangeIdeal);
        if ((comparison != null && comparison >= 0) || nbFreeUTxO <= 0 || limit <= 0) {
            utxoSelection.getRemaining().addAll(utxoSelection.getSubset());
            utxoSelection.getSubset().clear();
            return;
        }
        utxoSelection.getSubset().remove((int) Math.floor(Math.random() * nbFreeUTxO));
        Utxo utxo;
        if (utxoSelection.getSubset() != null && !utxoSelection.getSubset().isEmpty()) {
            utxo = utxoSelection.getSubset().get(utxoSelection.getSubset().size() - 1);
            Value newAmount = utxo.toValue().plus(outputAmount);
            Integer comparison2 = compare(newAmount, rangeMaximum);
            if (abs(getAmountValue(rangeIdeal).subtract(getAmountValue(newAmount)).longValue()) < abs(getAmountValue(rangeIdeal).subtract(getAmountValue(outputAmount)).longValue()) && comparison2 != null && comparison2 <= 0) {
                utxoSelection.getSelection().add(utxo);
                utxoSelection.amount = utxo.toValue().plus(utxoSelection.amount);
                limit--;
            } else {
                utxoSelection.getRemaining().add(utxo);
            }
        }
        improve(utxoSelection, outputAmount, limit, rangeIdeal, rangeMaximum);
    }

    /**
     * Use randomSelect & descSelect algorithm to select enough UTxO to fulfill requested outputs
     *
     * @param utxoSelection The set of selected/available inputs.
     * @param outputAmount  Single compiled output qty requested for payment.
     * @param limit         A limit on the number of inputs that can be selected.
     * @return UTxOSelection  Successful random utxo selection.
     * @throws InputsLimitExceededException INPUT_LIMIT_EXCEEDED if the number of randomly picked inputs exceed 'limit' parameter.
     * @throws InputsLimitExceededException INPUTS_EXHAUSTED     if all UTxO doesn't hold enough funds to pay for output.
     */
    private UTxOSelection select(UTxOSelection utxoSelection, Value outputAmount, int limit) throws CoinSelectionException {
        try {
            utxoSelection = randomSelect(new UTxOSelection(utxoSelection), // Deep copy in case of fallback needed
                    outputAmount,
                    limit - utxoSelection.getSelection().size()
            );
            return utxoSelection;
        } catch (InputsExhaustedException e) {
            // Limit reached : Fallback on DescOrdAlgo
            return descSelect(utxoSelection, outputAmount);
        } catch (InputsLimitExceededException e) {
            throw e;
        }
    }

    /**
     * Select enough UTxO in DESC order to fulfill requested outputs
     *
     * @param utxoSelection The set of selected/available inputs.
     * @param outputAmount  Single compiled output qty requested for payment.
     * @return UTxOSelection  Successful random utxo selection.
     * @throws InputsExhaustedException INPUTS_EXHAUSTED if all UTxO doesn't hold enough funds to pay for output.
     */
    private UTxOSelection descSelect(UTxOSelection utxoSelection, Value outputAmount) throws InputsExhaustedException {
        // Sort UTxO subset in DESC order for required output unit type
        utxoSelection.subset.sort((a, b) -> searchAmountValue(outputAmount, b.toValue()).subtract(searchAmountValue(outputAmount, a.toValue())).intValue());

        do {
            if (utxoSelection.getSubset().size() <= 0) {
                throw new InputsExhaustedException();
            }
            utxoSelection.getSubset().remove(0);
            if (utxoSelection.getSubset() != null && !utxoSelection.getSubset().isEmpty()) {
                Utxo utxo = utxoSelection.getSubset().get(utxoSelection.getSubset().size() - 1);
                utxoSelection.getSelection().add(utxo);
                utxoSelection.amount = utxo.toValue().plus(utxoSelection.getAmount());
            }
        } while (!isQtyFulfilled(outputAmount, utxoSelection.amount, utxoSelection.getSubset().size() - 1));

        // Quantity is met, return subset into remaining list and return selection
        utxoSelection.getRemaining().addAll(utxoSelection.getSubset());
        utxoSelection.getSubset().clear();
        return utxoSelection;
    }

    /**
     * Search & Return BigInt amount value
     *
     * @param needle   needle
     * @param haystack haystack
     * @return BigInteger
     */
    private BigInteger searchAmountValue(Value needle, Value haystack) {
        BigInteger val = BigInteger.ZERO;
        BigInteger lovelace = needle.getCoin();

        if (lovelace.compareTo(BigInteger.ZERO) > 0) {
            val = haystack.getCoin();
        } else if (needle.getMultiAssets() != null && haystack.getMultiAssets() != null && !needle.getMultiAssets().isEmpty() && !haystack.getMultiAssets().isEmpty()) {
            HashMap<String, HashMap<String, BigInteger>> needleMap = needle.toMap();
            String policyId = needleMap.keySet().iterator().next();
            String assetName = needleMap.get(policyId).keySet().iterator().next();
            HashMap<String, HashMap<String, BigInteger>> haystackMap = haystack.toMap();
            val = haystackMap.get(policyId).get(assetName);
        }
        return val;
    }

    /**
     * Randomly select enough UTxO to fulfill requested outputs
     *
     * @param utxoSelection The set of selected/available inputs.
     * @param outputAmount  Single compiled output qty requested for payment.
     * @param limit         A limit on the number of inputs that can be selected.
     * @return uTxOSelection Successful random utxo selection.
     * @throws InputsLimitExceededException INPUT_LIMIT_EXCEEDED if the number of randomly picked inputs exceed 'limit' parameter.
     * @throws InputsExhaustedException     INPUTS_EXHAUSTED     if all UTxO doesn't hold enough funds to pay for output.
     */
    private UTxOSelection randomSelect(UTxOSelection utxoSelection, Value outputAmount, int limit) throws CoinSelectionException {
        int nbFreeUTxO = utxoSelection.getSubset().size();
        // If quantity is met, return subset into remaining list and exit
        if (isQtyFulfilled(outputAmount, utxoSelection.getAmount(), nbFreeUTxO)) {
            utxoSelection.getRemaining().addAll(utxoSelection.getSubset());
            utxoSelection.getSubset().clear();
            return utxoSelection;
        }
        if (limit <= 0) {
            throw new InputsLimitExceededException();
        }
        if (nbFreeUTxO <= 0) {
            throw new InputsExhaustedException();
        }
        utxoSelection.getSubset().remove((int) Math.floor(Math.random() * nbFreeUTxO));
        Utxo utxo;
        if (utxoSelection.getSubset() != null && !utxoSelection.getSubset().isEmpty()) {
            utxo = utxoSelection.getSubset().get(utxoSelection.getSubset().size() - 1);
            utxoSelection.getSelection().add(utxo);
            utxoSelection.setAmount(utxo.toValue().plus(utxoSelection.getAmount()));
        }
        return randomSelect(utxoSelection, outputAmount, limit - 1);
    }

    /**
     * Is Quantity Fulfilled Condition.
     *
     * @param outputAmount    Single compiled output qty requested for payment.
     * @param cumulatedAmount Single compiled accumulated UTxO qty.
     * @param nbFreeUTxO      Number of free UTxO available.
     * @return boolean
     */
    private boolean isQtyFulfilled(Value outputAmount, Value cumulatedAmount, int nbFreeUTxO) {
        Value amount = outputAmount;

        if (outputAmount.getMultiAssets() != null || outputAmount.getMultiAssets().isEmpty()) {
            Value minAmount = Value.builder().coin(minAdaCalculator.calculateMinAda(cumulatedAmount.getMultiAssets())).build();
            // Lovelace min amount to cover assets and number of output need to be met
            Integer comparison = compare(cumulatedAmount, minAmount);
            if (comparison != null && comparison < 0) return false;

            // Try covering the max fees
            if (nbFreeUTxO > 0) {
                BigInteger maxFee = BigInteger.valueOf(protocolParams.getMinFeeA()).multiply(BigInteger.valueOf(protocolParams.getMaxTxSize()).add(BigInteger.valueOf(protocolParams.getMinFeeB())));

                Value maxFeeValue = Value.builder().coin(maxFee).build();

                amount = amount.plus(maxFeeValue);
            }
        }
        Integer comparison2 = compare(cumulatedAmount, amount);
        return comparison2 != null && comparison2 >= 0;
    }

    /**
     * Narrow down remaining UTxO set in case of native token, useful set for lovelace
     *
     * @param utxoSelection The set of selected/available inputs.
     * @param output        Single compiled output qty requested for payment.
     */
    private void createSubSet(UTxOSelection utxoSelection, Value output) {
        if (output.getCoin().compareTo(BigInteger.ONE) < 0) {
            List<Utxo> subset = new ArrayList<>();
            List<Utxo> remaining = new ArrayList<>();
            for (Utxo utxo : utxoSelection.getRemaining()) {
                if (compare(utxo.toValue(), output) != null) {
                    subset.add(utxo);
                } else {
                    remaining.add(utxo);
                }
            }
            utxoSelection.setSubset(subset);
            utxoSelection.setRemaining(remaining);
        } else {
            utxoSelection.setSubset(utxoSelection.getRemaining().subList(0, utxoSelection.getRemaining().size()));
        }
    }

    /**
     * Compare a candidate value to the one in a group if present
     *
     * @param group     group
     * @param candidate candidate
     * @return {int} -1 group lower, 0 equal, 1 group higher, undefined if no match
     */
    private Integer compare(Value group, Value candidate) {
        BigInteger gQty = group.getCoin();
        BigInteger cQty = candidate.getCoin();

        if (candidate.getMultiAssets() != null && !candidate.getMultiAssets().isEmpty()) {
            HashMap<String, HashMap<String, BigInteger>> candidateMultiAssets = candidate.toMap();
            String cScriptHash = candidateMultiAssets.keySet().iterator().next();
            String cAssetName = candidateMultiAssets.get(cScriptHash).keySet().iterator().next();
            HashMap<String, HashMap<String, BigInteger>> groupMultiAssets = group.toMap();
            if (groupMultiAssets != null && !groupMultiAssets.isEmpty()) {
                if (groupMultiAssets.get(cScriptHash) != null && groupMultiAssets.get(cScriptHash).get(cAssetName) != null) {
                    gQty = groupMultiAssets.get(cScriptHash).get(cAssetName);
                    cQty = candidateMultiAssets.get(cScriptHash).get(cAssetName);
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
        if (gQty.compareTo(cQty) >= 0) {
            return (gQty.compareTo(cQty) == 0 ? 0 : 1);
        } else {
            return -1;
        }
    }

    /**
     * Split amounts contained in a single {Value} object in separate {Value} objects
     *
     * @param amounts - Set of amounts to be split.
     * @return List<Value>
     */
    private List<Value> splitAmounts(Value amounts) {
        List<Value> splitAmounts = new ArrayList<>();
        List<MultiAsset> mA = amounts.getMultiAssets();
        if (mA != null && !mA.isEmpty()) {
            for (MultiAsset multiAsset : mA) {
                for (Asset asset : multiAsset.getAssets()) {
                    Asset newAsset = new Asset(asset.getName(), asset.getValue());
                    MultiAsset newMultiAsset = new MultiAsset(multiAsset.getPolicyId(), Arrays.asList(newAsset));
                    Value value = Value.builder().coin(BigInteger.ZERO).multiAssets(Arrays.asList(newMultiAsset)).build();
                    splitAmounts.add(value);
                }
            }
        }
        // Order assets by qty DESC
        sortAmountList(splitAmounts, OrderEnum.desc);
        splitAmounts.add(Value.builder().coin(amounts.getCoin()).build());
        return splitAmounts;
    }

    /**
     * Sort a mismatched AmountList ASC/DESC
     *
     * @param amountList - Set of mismatched amounts to be sorted.
     * @param sortOrder  [sortOrder=ASC] - Order
     */
    private void sortAmountList(List<Value> amountList, OrderEnum sortOrder) {
        amountList.sort((a, b) -> {
            BigInteger sortInt = sortOrder == OrderEnum.desc ? BigInteger.valueOf(-1) : BigInteger.valueOf(1);
            return getAmountValue(a).subtract(getAmountValue(b)).multiply(sortInt).intValue();
        });
    }

    /**
     * Return BigInt amount value
     *
     * @param amount - amount
     * @return BigInteger
     */
    private BigInteger getAmountValue(Value amount) {
        BigInteger val = BigInteger.ZERO;
        BigInteger lovelace = amount.getCoin();

        if (lovelace.compareTo(BigInteger.ZERO) > 0) {
            val = lovelace;
        } else if (amount.getMultiAssets() != null && !amount.getMultiAssets().isEmpty()) {
            HashMap<String, HashMap<String, BigInteger>> multiAssetMap = amount.toMap();
            String policyId = multiAssetMap.keySet().iterator().next();
            String assetName = multiAssetMap.get(policyId).keySet().iterator().next();
            val = multiAssetMap.get(policyId).get(assetName);
        }
        return val;
    }

    /**
     * Compile all required outputs to a flat amounts list
     *
     * @param outputs - The set of outputs requested for payment.
     * @return Value - The compiled set of amounts requested for payment.
     */
    private Value mergeOutputsAmounts(Set<TransactionOutput> outputs) {
        Value mergedOutputsValue = Value.builder().coin(BigInteger.ZERO).build();
        for (TransactionOutput transactionOutput : outputs) {
            mergedOutputsValue = mergedOutputsValue.plus(transactionOutput.getValue());
        }
        return mergedOutputsValue;
    }

    public int getUtxoFetchSize() {
        return utxoFetchSize;
    }

    public void setUtxoFetchSize(int utxoFetchSize) {
        this.utxoFetchSize = utxoFetchSize;
    }

    @Data
    private static class UTxOSelection {

        private List<Utxo> selection = new ArrayList<>();
        private List<Utxo> remaining;
        private List<Utxo> subset = new ArrayList<>();
        private Value amount = Value.builder().coin(BigInteger.ZERO).build();

        public UTxOSelection(List<Utxo> remaining) {
            this.remaining = remaining;
        }

        public UTxOSelection(UTxOSelection uTxOSelection) {
            setSelection(uTxOSelection.getSelection());
            setRemaining(uTxOSelection.getRemaining());
            setSubset(uTxOSelection.getSubset());
            setAmount(uTxOSelection.getAmount());
        }
    }
}
