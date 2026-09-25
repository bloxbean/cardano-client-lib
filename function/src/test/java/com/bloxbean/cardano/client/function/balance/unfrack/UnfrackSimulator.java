package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.ChangeOutput;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static com.bloxbean.cardano.client.function.balance.unfrack.UnfrackFixtures.*;

/**
 * Replays a wallet workload through {@link Unfrack} with a given strategy and collects wallet shape metrics.
 * <p>
 * Model: one address, serial transactions, largest-first input selection (CCL default), fixed fee, ADA payments to
 * another address, and optional token airdrops into the wallet. The real {@link Unfrack} (including its result checks)
 * shapes the change of every transaction.
 */
final class UnfrackSimulator {
    static final BigInteger FEE = BigInteger.valueOf(200_000);
    /** Min change kept by input selection, so that change is always a valid output. */
    static final BigInteger MIN_CHANGE = BigInteger.valueOf(1_000_000);

    private UnfrackSimulator() {
    }

    @FunctionalInterface
    interface PaymentGenerator {
        BigInteger next(Random random);
    }

    /**
     * @param airdropEvery every n-th transaction the wallet first receives a UTxO with a new single-token policy;
     *                     0 disables airdrops
     */
    record Workload(String name, int transactions, Value initialValue, PaymentGenerator payments,
                    int airdropEvery) {
    }

    record Result(String strategy, String workload, int transactions, int finalUtxos, int maxUtxos,
                  int maxAdaOnlyUtxos, double avgInputs, double avgChangeOutputs, double avgFundable,
                  double tokenChurn, double avgTokensMoved, BigInteger adaInTokenUtxos, BigInteger finalBalance,
                  BigInteger expectedBalance, long finalTokenUnits, long expectedTokenUnits) {
    }

    private record Coin(String txHash, int index, Value value) {
        boolean hasTokens() {
            return value.getMultiAssets() != null && value.getMultiAssets().stream()
                    .anyMatch(ma -> ma.getAssets() != null && !ma.getAssets().isEmpty());
        }

        BigInteger lovelace() {
            return value.getCoin();
        }
    }

    static Result run(String strategyName, ChangeSplitStrategy strategy, Workload workload, long seed) {
        Random random = new Random(seed);
        List<Coin> wallet = new ArrayList<>();
        wallet.add(new Coin(hash("genesis", 0), 0, workload.initialValue()));

        Unfrack unfrack = new Unfrack(strategy);
        TxBuilderContext context = new TxBuilderContext(new WalletSupplier(wallet), PROTOCOL_PARAMS);

        BigInteger expectedBalance = workload.initialValue().getCoin();
        long expectedTokenUnits = tokenUnits(List.of(workload.initialValue()));
        int transactions = 0, maxUtxos = 1, maxAdaOnly = 1, churned = 0;
        long inputs = 0, changeOutputs = 0, fundable = 0, tokensMoved = 0;

        for (int t = 0; t < workload.transactions(); t++) {
            if (workload.airdropEvery() > 0 && t % workload.airdropEvery() == 0) {
                Value airdrop = airdrop(t);
                wallet.add(new Coin(hash("airdrop", t), 0, airdrop));
                expectedBalance = expectedBalance.add(airdrop.getCoin());
                expectedTokenUnits++;
            }

            BigInteger payment = workload.payments().next(random);
            BigInteger needed = payment.add(FEE);
            BigInteger fundingTarget = needed.add(MIN_CHANGE);

            fundable += wallet.stream().filter(c -> !c.hasTokens() && c.lovelace().compareTo(fundingTarget) >= 0).count();

            List<Coin> selected = selectLargestFirst(wallet, fundingTarget);
            if (selected == null)
                break;
            if (selected.stream().anyMatch(Coin::hasTokens))
                churned++;
            tokensMoved += tokenUnits(selected.stream().map(Coin::value).toList());

            Value inputValue = selected.stream().map(Coin::value).reduce(Value.fromCoin(BigInteger.ZERO), Value::add);
            Value change = new Value(inputValue.getCoin().subtract(needed), inputValue.getMultiAssets());

            List<TransactionInput> txInputs = selected.stream()
                    .map(c -> new TransactionInput(c.txHash(), c.index()))
                    .toList();
            List<TransactionOutput> outputs = new ArrayList<>(List.of(
                    new TransactionOutput(RECEIVER, Value.fromCoin(payment)),
                    new ChangeOutput(ADDRESS, change)));
            Transaction tx = Transaction.builder()
                    .body(TransactionBody.builder().inputs(new ArrayList<>(txInputs)).outputs(outputs).build())
                    .build();

            unfrack.apply(context, tx);

            wallet.removeAll(selected);
            String txHash = hash("tx", t);
            List<TransactionOutput> result = tx.getBody().getOutputs();
            for (int i = 0; i < result.size(); i++) {
                if (ADDRESS.equals(result.get(i).getAddress())) {
                    wallet.add(new Coin(txHash, i, result.get(i).getValue()));
                    changeOutputs++;
                }
            }

            expectedBalance = expectedBalance.subtract(needed);
            inputs += selected.size();
            transactions++;
            maxUtxos = Math.max(maxUtxos, wallet.size());
            maxAdaOnly = Math.max(maxAdaOnly, (int) wallet.stream().filter(c -> !c.hasTokens()).count());
        }

        BigInteger balance = wallet.stream().map(Coin::lovelace).reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger adaInTokenUtxos = wallet.stream().filter(Coin::hasTokens).map(Coin::lovelace)
                .reduce(BigInteger.ZERO, BigInteger::add);
        int n = Math.max(transactions, 1);

        return new Result(strategyName, workload.name(), transactions, wallet.size(), maxUtxos, maxAdaOnly,
                inputs / (double) n, changeOutputs / (double) n, fundable / (double) n, churned / (double) n,
                tokensMoved / (double) n, adaInTokenUtxos, balance, expectedBalance,
                tokenUnits(wallet.stream().map(Coin::value).toList()), expectedTokenUnits);
    }

    private static long tokenUnits(List<Value> values) {
        return ChangeValues.sumUnits(values).entrySet().stream()
                .filter(e -> !"lovelace".equals(e.getKey()))
                .mapToLong(e -> e.getValue().longValueExact())
                .sum();
    }

    private static List<Coin> selectLargestFirst(List<Coin> wallet, BigInteger target) {
        List<Coin> sorted = new ArrayList<>(wallet);
        sorted.sort(Comparator.comparing(Coin::lovelace).reversed()
                .thenComparing(Coin::txHash).thenComparingInt(Coin::index));
        List<Coin> selected = new ArrayList<>();
        BigInteger sum = BigInteger.ZERO;
        for (Coin coin : sorted) {
            if (sum.compareTo(target) >= 0)
                break;
            selected.add(coin);
            sum = sum.add(coin.lovelace());
        }
        return sum.compareTo(target) >= 0 ? selected : null;
    }

    private static Value airdrop(int t) {
        MultiAsset token = new MultiAsset(policy(t + 10_000), new ArrayList<>(List.of(new Asset("airdrop", BigInteger.ONE))));
        BigInteger coin = minAda(new Value(BigInteger.valueOf(1_000_000), List.of(token))).add(BigInteger.valueOf(500_000));
        return new Value(coin, new ArrayList<>(List.of(token)));
    }

    private static String hash(String prefix, int n) {
        return String.format("%064x", (long) (prefix.hashCode() & 0xffff) << 32 | n);
    }

    /**
     * Exposes the simulated wallet as a {@link UtxoSupplier} for wallet-aware strategies.
     */
    private record WalletSupplier(List<Coin> wallet) implements UtxoSupplier {

        @Override
        public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
            return page == null || page == 0 ? getAll(address) : List.of();
        }

        @Override
        public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {
            return wallet.stream().filter(c -> c.txHash().equals(txHash) && c.index() == outputIndex)
                    .findFirst().map(WalletSupplier::toUtxo);
        }

        @Override
        public List<Utxo> getAll(String address) {
            return ADDRESS.equals(address) ? wallet.stream().map(WalletSupplier::toUtxo).toList() : List.of();
        }

        private static Utxo toUtxo(Coin coin) {
            List<Amount> amounts = new ArrayList<>();
            amounts.add(new Amount(LOVELACE, coin.lovelace()));
            if (coin.value().getMultiAssets() != null) {
                for (MultiAsset ma : coin.value().getMultiAssets())
                    for (Asset asset : ma.getAssets())
                        amounts.add(Amount.asset(ma.getPolicyId() + asset.getNameAsHex().replace("0x", ""), asset.getValue()));
            }
            return Utxo.builder().txHash(coin.txHash()).outputIndex(coin.index()).address(ADDRESS).amount(amounts).build();
        }
    }
}
