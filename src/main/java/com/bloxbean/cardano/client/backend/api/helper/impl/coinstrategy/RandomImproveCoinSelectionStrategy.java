package com.bloxbean.cardano.client.backend.api.helper.impl.coinstrategy;

import com.bloxbean.cardano.client.backend.api.helper.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.backend.api.helper.impl.coinstrategy.exception.InputsExhaustedException;
import com.bloxbean.cardano.client.backend.api.helper.impl.coinstrategy.exception.InputsLimitExceededException;
import com.bloxbean.cardano.client.backend.api.helper.impl.coinstrategy.exception.base.CoinSelectionException;
import com.bloxbean.cardano.client.backend.api.helper.impl.coinstrategy.model.SelectionResult;
import com.bloxbean.cardano.client.backend.common.OrderEnum;
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

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static java.lang.Math.abs;

/**
 * <h2>Random-Improve</h2>
 * <p>The <strong>Random-Improve</strong> coin selection algorithm works in <em>two phases</em>:</p>
 * <ul>
 * <li><p>In the <strong>first phase</strong>, the algorithm iterates through each of the
 * <a href="#requested-output-set">requested outputs</a> in <em>descending order of coin
 * value</em>, from <em>largest</em> to <em>smallest</em>. For each output, the algorithm
 * repeatedly selects entries <em>at random</em> from the <a href="#initial-utxo-set">initial UTxO
 * set</a>, until each requested output has been associated
 * with a set of UTxO entries whose <em>total value</em> is enough to pay for that
 * output.</p></li>
 * <li><p>In the <strong>second phase</strong>, the algorithm attempts to <em>expand</em> each
 * existing UTxO selection with <em>additional</em> values taken at random from the
 * <a href="#initial-utxo-set">initial UTxO set</a>, to the point where the total value
 * of each selection is as close as possible to <em>twice</em> the value of its
 * associated output.</p></li>
 * </ul>
 * <p>After the above phases are complete, for each output of value
 * <strong><em>v</em></strong><sub>output</sub> and accompanying UTxO selection of value
 * <strong><em>v</em></strong><sub>selection</sub>, the algorithm generates a <em>single</em> change output
 * of value <strong><em>v</em></strong><sub>change</sub>, where:</p>
 * <blockquote>
 *   <p><strong><em>v</em></strong><sub>change</sub>
 *     = <strong><em>v</em></strong><sub>selection</sub>
 *     − <strong><em>v</em></strong><sub>output</sub></p>
 * </blockquote>
 * <p>Since the goal of the second phase was to expand each selection to the point
 * where its total value is <em>approximately twice</em> the value of its associated
 * output, this corresponds to a change output whose target value is
 * <em>approximately equal</em> to the value of the output itself:</p>
 * <blockquote>
 *   <p><strong><em>v</em></strong><sub>change</sub>
 *     = <strong><em>v</em></strong><sub>selection</sub>
 *     − <strong><em>v</em></strong><sub>output</sub></p>
 *   <p><strong><em>v</em></strong><sub>change</sub>
 *     ≈ <span>2</span><strong><em>v</em></strong><sub>output</sub>
 *     − <strong><em>v</em></strong><sub>output</sub></p>
 *   <p><strong><em>v</em></strong><sub>change</sub>
 *     ≈ <strong><em>v</em></strong><sub>output</sub></p>
 * </blockquote>
 * <h2 id="motivatingprinciples">Motivating Principles</h2>
 * <p>There are several motivating principles behind the design of the algorithm.</p>
 * <h3 id="principle1dustmanagement">Principle 1: Dust Management</h3>
 * <p>The probability that random selection will choose dust entries from a UTxO
 * set <em>increases</em> with the proportion of dust in the set.</p>
 * <p>Therefore, for a UTxO set with a large amount of dust, there's a high
 * probability that a random subset will include a large amount of dust.</p>
 * <p>Over time, selecting entries randomly in this way will tend to <em>limit</em> the
 * amount of dust that accumulates in the UTxO set.</p>
 * <h3 id="principle2changemanagement">Principle 2: Change Management</h3>
 * <p>As mentioned in the <a href="#goals-of-coin-selection-algorithms">Goals</a> section, it is
 * desirable that coin selection algorithms, over time, are able to create UTxO
 * sets that have <em>useful</em> outputs: outputs that will allow us to process future
 * payments with a <em>reasonably small</em> number of inputs.</p>
 * <p>If for each payment request of value <strong><em>v</em></strong> we create a change output of
 * <em>roughly</em> the same value <strong><em>v</em></strong>, then we will end up with a distribution of
 * change values that matches the typical value distribution of payment
 * requests.</p>
 * <blockquote>
 *   <p>:bulb: <strong>Example</strong></p>
 *   <p>Alice often buys bread and other similar items that cost around €1.00 each.</p>
 *   <p>When she instructs her wallet software to make a payment for around
 *   €1.00, the software attempts to select a set of unspent transaction outputs
 *   with a total value of around €2.00.</p>
 *   <p>As she frequently makes payments for similar amounts, transactions created by
 *   her wallet will also frequently produce change coins of around €1.00 in value.</p>
 *   <p>Over time, her wallet will self-organize to contain multiple coins of around
 *   €1.00, which are useful for the kinds of payments that Alice frequently makes.</p>
 * </blockquote>
 * <h3 id="principle3performancemanagement">Principle 3: Performance Management</h3>
 * <p>Searching the UTxO set for additional entries to <em>improve</em> our change outputs
 * is <em>only</em> useful if the UTxO set contains entries that are sufficiently
 * small enough. But it is precisely when the UTxO set contains many small
 * entries that it is less likely for a randomly-chosen UTxO entry to push the
 * total above the upper bound.</p>
 * <h4 id="cardinality">Cardinality</h4>
 * <p>The Random-Improve algorithm imposes the following cardinality restriction:</p>
 * <ul>
 * <li>Each entry from the <a href="#initial-utxo-set">initial UTxO set</a> is used to pay
 * for <em>at most one</em> output from the <a href="#requested-output-set">requested output
 * set</a>.</li>
 * </ul>
 * <p>As a result of this restriction, the algorithm will fail with a <a href="#utxo-not-fragmented-enough">UTxO Not
 * Fragmented Enough</a> error if the number of entries
 * in the <a href="#initial-utxo-set">initial UTxO set</a> is <em>smaller than</em> the number of
 * entries in the <a href="#requested-output-set">requested output set</a>.</p>
 * <h4 id="state-1">State</h4>
 * <p>At all stages of processing, the algorithm maintains the following pieces of
 * state:</p>
 * <ol>
 * <li><h4 id="availableutxoset">Available UTxO Set</h4>
 * <p>This is initially equal to the <a href="#initial-utxo-set">initial UTxO set</a>.</p></li>
 * <li><h4 id="accumulatedcoinselection">Accumulated Coin Selection</h4>
 * <p>The accumulated coin selection is a <a href="#coin-selection">coin selection</a> where
 * all fields are initially equal to the <em>empty set</em>.</p></li>
 * </ol>
 * <h4 id="computation-1">Computation</h4>
 * <p>The algorithm proceeds in two phases.</p>
 * <h5 id="phase1randomselection">Phase 1: Random Selection</h5>
 * <p>In this phase, the algorithm iterates through each of the <a href="#requested-output-set">requested
 * outputs</a> in descending order of coin value, from
 * largest to smallest.</p>
 * <p>For each output of value <strong><em>v</em></strong>, the algorithm repeatedly selects entries at
 * <strong>random</strong> from the <a href="#available-utxo-set">available UTxO set</a>, until the <em>total
 * value</em> of selected entries is greater than or equal to <strong><em>v</em></strong>. The selected
 * entries are then <em>associated with</em> that output, and <em>removed</em> from the
 * <a href="#available-utxo-set">available UTxO set</a>.</p>
 * <p>This phase ends when <em>every</em> output has been associated with a selection of
 * UTxO entries.</p>
 * <h5 id="phase2improvement">Phase 2: Improvement</h5>
 * <p>In this phase, the algorithm attempts to improve upon each of the UTxO
 * selections made in the previous phase, by conservatively expanding the
 * selection made for each output in order to generate improved change
 * values.</p>
 * <p>During this phase, the algorithm:</p>
 * <ul>
 * <li><p>processes outputs in <em>ascending order of coin value</em>.</p></li>
 * <li><p>continues to select values from the <a href="#available-utxo-set">available UTxO
 * set</a>.</p></li>
 * <li><p>incrementally populates the
 * <a href="#accumulated-coin-selection-1">accumulated coin selection</a>.</p></li>
 * </ul>
 * <p>For each output of value <strong><em>v</em></strong>, the algorithm:</p>
 * <ol>
 * <li><p><strong>Calculates a <em>target range</em></strong> for the total value of inputs used to
 *  pay for that output, defined by the triplet:</p>
 * <p>(<em>minimum</em>, <em>ideal</em>, <em>maximum</em>) =
 *  (<strong><em>v</em></strong>, <span>2</span><strong><em>v</em></strong>, <span>3</span><strong><em>v</em></strong>)</p></li>
 * <li><p><strong>Attempts to improve upon the existing UTxO selection</strong> for that output,
 *  by repeatedly selecting additional entries at random from the <a href="#available-utxo-set">available
 *  UTxO set</a>, stopping when the selection can be
 *  improved upon no further.</p>
 * <p>A selection with value <strong><em>v</em><sub>1</sub></strong> is considered to be an
 *  <em>improvement</em> over a selection with value <strong><em>v</em><sub>0</sub></strong> if <strong>all</strong>
 *  of the following conditions are satisfied:</p>
 * <ul>
 * <li><p><strong>Condition 1</strong>: we have moved closer to the <em>ideal</em> value:</p>
 * <p>abs (<em>ideal</em> − <strong><em>v</em><sub>1</sub></strong>) &lt;
 * abs (<em>ideal</em> − <strong><em>v</em><sub>0</sub></strong>)</p></li>
 * <li><p><strong>Condition 2</strong>: we have not exceeded the <em>maximum</em> value:</p>
 * <p><strong><em>v</em><sub>1</sub></strong> ≤ <em>maximum</em></p></li>
 * <li><p><strong>Condition 3</strong>: when counting cumulatively across all outputs
 * considered so far, we have not selected more than the <em>maximum</em> number
 * of UTxO entries specified by <a href="#maximum-input-count">Maximum Input
 * Count</a>.</p></li></ul></li>
 * <li><p><strong>Creates a <em>change value</em></strong> for the output, equal to the total value
 *  of the <em>improved UTxO selection</em> for that output minus the value <strong><em>v</em></strong>
 *  of that output.</p></li>
 * <li><p><strong>Updates the <a href="#accumulated-coin-selection-1">accumulated coin
 *  selection</a></strong>:</p>
 * <ul>
 * <li>Adds the <em>output</em> to the <em>outputs</em> field;</li>
 * <li>Adds the <em>improved UTxO selection</em> to the <em>inputs</em> field;</li>
 * <li>Adds the <em>change value</em> to the <em>change values</em> field.</li></ul></li>
 * </ol>
 * <p>This phase ends when every output has been processed, <strong>or</strong> when the
 * <a href="#available-utxo-set">available UTxO set</a> has been exhausted, whichever occurs
 * sooner.</p>
 * <h4 id="termination">Termination</h4>
 * <p>When both phases are complete, the algorithm terminates.</p>
 * <p>The <a href="#accumulated-coin-selection-1">accumulated coin selection</a> is returned
 * to the caller as the <a href="#coin-selection">coin selection</a> result.</p>
 * <p>The <a href="#available-utxo-set">available UTxO set</a> is returned to the caller as the
 * <a href="#remaining-utxo-set">remaining UTxO set</a> result.</p>
 * <h2 id="referenceimplementations">Reference Implementations</h2>
 * <h3 id="randomimprove-1">Random-Improve</h3>
 * <p>Reference implementations of the <a href="#random-improve">Random-Improve</a> algorithm
 * are available in the following languages:</p>
 * <ul>
 *     <li>Language: <strong>Haskell</strong></li>
 *     <li>Documentation: <a href="https://hackage.haskell.org/package/cardano-coin-selection/docs/Cardano-CoinSelection-Algorithm-RandomImprove.html">Documentation</a></li>
 *     <li>Source: <a href="https://hackage.haskell.org/package/cardano-coin-selection/docs/src/Cardano.CoinSelection.Algorithm.RandomImprove.html">Source</a></li>
 * </ul>
 * <h2 id="externalresources"><a href="https://iohk.io/en/blog/posts/2018/07/03/self-organisation-in-coin-selection/">External Resource</a></h2>
 * <blockquote>
 *   <p>This article introduces the <a href="#random-improve">Random-Improve</a> coin selection
 *   algorithm, invented by <a href="http://www.edsko.net/">Edsko de Vries</a>.</p>
 *   <p>It describes the three principles of self-organisation that inform the
 *   algorithm's design, and provides experimental evidence to demonstrate the
 *   algorithm's effectiveness at maintaining healthy UTxO sets over time.</p>
 * </blockquote>
 */
@Slf4j
public class RandomImproveCoinSelectionStrategy implements UtxoSelectionStrategy {

    private final ProtocolParams protocolParams;
    private final MinAdaCalculator minAdaCalculator;

    public RandomImproveCoinSelectionStrategy(ProtocolParams protocolParams) {
        this.protocolParams = protocolParams;
        this.minAdaCalculator = new MinAdaCalculator(protocolParams);
    }

    /**
     * Random-Improve coin selection algorithm
     *
     * @param inputs  - The set of inputs available for selection.
     * @param outputs - The set of outputs requested for payment.
     * @param limit   - A limit on the number of inputs that can be selected.
     * @return a {@link SelectionResult} with the specified Coin Selection.
     */
    public Result<SelectionResult> randomImprove(List<Utxo> inputs, List<TransactionOutput> outputs, int limit) {
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
        splitOutputsAmounts = sortAmountList(splitOutputsAmounts, OrderEnum.asc);
        for (Value splitOutputsAmount : splitOutputsAmounts) {
            createSubSet(utxoSelection, splitOutputsAmount); // Narrow down for NatToken UTxO
            Value rangeIdeal = splitOutputsAmount.plus(splitOutputsAmount);
            Value rangeMaximum = rangeIdeal.plus(splitOutputsAmount);
            improve(utxoSelection, splitOutputsAmount, limit - utxoSelection.selection.size(), rangeIdeal, rangeMaximum);
        }

        // Insure change hold enough Ada to cover included native assets and fees
        if (utxoSelection.getRemaining().size() > 0) {
            Value change = utxoSelection.getAmount().minus(mergedOutputsAmounts);

            Value minAmount = new Value(minAdaCalculator.calculateMinAda(change.getMultiAssets()), null);
            Integer comparison = compare(change, minAmount);
            if (comparison != null && comparison < 0) {
                // Not enough, add missing amount and run select one last time
                Value minAda = minAmount.minus(new Value(change.getCoin(), new ArrayList<>())).plus(new Value(utxoSelection.getAmount().getCoin(), new ArrayList<>()));
                createSubSet(utxoSelection, minAda);
                try {
                    utxoSelection = select(utxoSelection, minAda, limit);
                } catch (CoinSelectionException e) {
                    return Result.error(e.getMessage());
                }
            }
        }
        return Result.success("Success")
                .withValue(new SelectionResult(utxoSelection.getSelection(), outputs, utxoSelection.getRemaining(), utxoSelection.getAmount(), utxoSelection.getAmount().minus(mergedOutputsAmounts)));
    }

    /**
     * Try to improve selection by increasing input amount in [2x,3x] range.
     *
     * @param utxoSelection - The set of selected/available inputs.
     * @param outputAmount  - Single compiled output qty requested for payment.
     * @param limit         - A limit on the number of inputs that can be selected.
     * @param rangeIdeal    range - Improvement range target values
     * @param rangeMaximum  range - Improvement range target values
     */
    private void improve(UTxOSelection utxoSelection, Value outputAmount, int limit, Value rangeIdeal, Value rangeMaximum) {
        int nbFreeUTxO = utxoSelection.getSubset().size();
        Integer comparison = compare(utxoSelection.getAmount(), rangeIdeal);
        if ((comparison != null && comparison >= 0) || nbFreeUTxO <= 0 || limit <= 0) {
            utxoSelection.getRemaining().addAll(utxoSelection.getSubset());
            utxoSelection.getSubset().clear();
            return;
        }
        utxoSelection.getSubset().remove(Math.floor(Math.random() * nbFreeUTxO));
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
     * @param utxoSelection - The set of selected/available inputs.
     * @param outputAmount  - Single compiled output qty requested for payment.
     * @param limit         - A limit on the number of inputs that can be selected.
     * @return UTxOSelection - Successful random utxo selection.
     * @throws InputsLimitExceededException INPUT_LIMIT_EXCEEDED if the number of randomly picked inputs exceed 'limit' parameter.
     * @throws InputsLimitExceededException INPUTS_EXHAUSTED     if all UTxO doesn't hold enough funds to pay for output.
     */
    private UTxOSelection select(UTxOSelection utxoSelection, Value outputAmount, int limit) throws CoinSelectionException {
        try {
            utxoSelection = randomSelect(new UTxOSelection(utxoSelection), // Deep copy in case of fallback needed
                    outputAmount,
                    limit - utxoSelection.getSelection().size()
            );
        } catch (InputsExhaustedException e) {
            // Limit reached : Fallback on DescOrdAlgo
            utxoSelection = descSelect(utxoSelection, outputAmount);
        } catch (InputsLimitExceededException e) {
            throw e;
        }
        return utxoSelection;
    }

    /**
     * Select enough UTxO in DESC order to fulfill requested outputs
     *
     * @param utxoSelection - The set of selected/available inputs.
     * @param outputAmount  - Single compiled output qty requested for payment.
     * @return UTxOSelection - Successful random utxo selection.
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
     * @param needle   - needle
     * @param haystack - haystack
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
     * @param utxoSelection - The set of selected/available inputs.
     * @param outputAmount  - Single compiled output qty requested for payment.
     * @param limit         - A limit on the number of inputs that can be selected.
     * @return uTxOSelection - Successful random utxo selection.
     * @throws InputsLimitExceededException INPUT_LIMIT_EXCEEDED if the number of randomly picked inputs exceed 'limit' parameter.
     * @throws InputsExhaustedException INPUTS_EXHAUSTED     if all UTxO doesn't hold enough funds to pay for output.
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
        utxoSelection.getSubset().remove(Math.floor(Math.random() * nbFreeUTxO));
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
     * @param outputAmount    - Single compiled output qty requested for payment.
     * @param cumulatedAmount - Single compiled accumulated UTxO qty.
     * @param nbFreeUTxO      - Number of free UTxO available.
     * @return boolean
     */
    private boolean isQtyFulfilled(Value outputAmount, Value cumulatedAmount, int nbFreeUTxO) {
        Value amount = outputAmount;

        if (outputAmount.getMultiAssets() != null || outputAmount.getMultiAssets().isEmpty()) {
            Value minAmount = new Value(minAdaCalculator.calculateMinAda(cumulatedAmount.getMultiAssets()), null);
            // Lovelace min amount to cover assets and number of output need to be met
            Integer comparison = compare(cumulatedAmount, minAmount);
            if (comparison != null && comparison < 0) return false;

            // Try covering the max fees
            if (nbFreeUTxO > 0) {
                BigInteger maxFee = BigInteger.valueOf(protocolParams.getMinFeeA()).multiply(BigInteger.valueOf(protocolParams.getMaxTxSize()).add(BigInteger.valueOf(protocolParams.getMinFeeB())));

                Value maxFeeValue = new Value(maxFee, null);

                amount = amount.plus(maxFeeValue);
            }
        }
        Integer comparison2 = compare(cumulatedAmount, amount);
        return comparison2 != null && comparison2 >= 0;
    }

    /**
     * Narrow down remaining UTxO set in case of native token, useful set for lovelace
     *
     * @param utxoSelection - The set of selected/available inputs.
     * @param output        - Single compiled output qty requested for payment.
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
     * @param group     - group
     * @param candidate - candidate
     * @return {int} - -1 group lower, 0 equal, 1 group higher, undefined if no match
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
        return gQty.compareTo(cQty) >= 0 ? (gQty.compareTo(cQty) == 0 ? 0 : 1) : -1;
    }

    private BigInteger getCoin(HashMap<String, HashMap<String, BigInteger>> map) {
        BigInteger coin = BigInteger.ZERO;
        for (HashMap<String, BigInteger> assetsMaps : map.values()) {
            for (Map.Entry<String, BigInteger> assets : assetsMaps.entrySet()) {
                if (assets.getKey().equals(LOVELACE)) {
                    coin = coin.add(assets.getValue());
                }
            }
        }
        return coin;
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
                    Asset _asset = new Asset(asset.getName(), asset.getValue());
                    MultiAsset _multiAsset = new MultiAsset(multiAsset.getPolicyId(), Arrays.asList(_asset));
                    Value _value = new Value(BigInteger.ZERO, Arrays.asList(_multiAsset));
                    splitAmounts.add(_value);
                }
            }
        }
        // Order assets by qty DESC
        splitAmounts = sortAmountList(splitAmounts, OrderEnum.desc);
        splitAmounts.add(new Value(amounts.getCoin(), new ArrayList<>()));
        return splitAmounts;
    }

    /**
     * Sort a mismatched AmountList ASC/DESC
     *
     * @param amountList - Set of mismatched amounts to be sorted.
     * @param sortOrder  [sortOrder=ASC] - Order
     * @return {AmountList} - The sorted AmountList
     */
    private List<Value> sortAmountList(List<Value> amountList, OrderEnum sortOrder) {
        amountList.sort((a, b) -> {
            BigInteger sortInt = sortOrder == OrderEnum.desc ? BigInteger.valueOf(-1) : BigInteger.valueOf(1);
            return getAmountValue(a).subtract(getAmountValue(b)).multiply(sortInt).intValue();
        });
        return amountList;
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
    private Value mergeOutputsAmounts(List<TransactionOutput> outputs) {
        Value mergedOutputsValue = new Value(BigInteger.ZERO, new ArrayList<>());
        for (TransactionOutput transactionOutput : outputs) {
            mergedOutputsValue.plus(transactionOutput.getValue());
        }
        return mergedOutputsValue;
    }

    @Override
    public List<Utxo> selectUtxos(String address, String unit, BigInteger amount, Set<Utxo> excludeUtxos) {
        return null;
    }

    @Override
    public List<Utxo> selectUtxos(String address, String unit, BigInteger amount, String datumHash, Set<Utxo> excludeUtxos) {
        return null;
    }

    @Override
    public boolean ignoreUtxosWithDatumHash() {
        return false;
    }

    @Override
    public void setIgnoreUtxosWithDatumHash(boolean ignoreUtxosWithDatumHash) {

    }

    @Data
    private static class UTxOSelection {

        private List<Utxo> selection = new ArrayList<>();
        private List<Utxo> remaining;
        private List<Utxo> subset = new ArrayList<>();
        private Value amount = new Value(BigInteger.ZERO, new ArrayList<>());

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
