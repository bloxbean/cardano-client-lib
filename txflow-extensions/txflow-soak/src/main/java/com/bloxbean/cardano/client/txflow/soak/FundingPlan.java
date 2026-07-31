package com.bloxbean.cardano.client.txflow.soak;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.common.model.Network;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The funding side of a soak: one account per lane.
 *
 * <p>A lane is a pot of money, and TxStream serialises everything spending from the same pot.
 * So a single-lane soak can never exceed roughly one transaction per block no matter how hard it
 * is driven, and it exercises none of the interesting machinery — cross-lane concurrency,
 * resource-lease contention, or the scheduler's canonical-identity keying. Real soaks want
 * several funding accounts.
 *
 * <p>Each lane gets its own derived account, registered as {@code account://lane-N}, and each
 * item's transaction is built with that lane's {@code fromRef} so lane scope is satisfied by
 * construction rather than by luck.
 */
public final class FundingPlan {

    /** One lane and the account that funds it. */
    public record Lane(int index, String name, String ref, Account account) {
        public String address() {
            return account.baseAddress();
        }
    }

    private final List<Lane> lanes;

    private FundingPlan(List<Lane> lanes) {
        this.lanes = List.copyOf(lanes);
    }

    public static FundingPlan create(Network network, String mnemonic, int laneCount, int baseIndex) {
        List<Lane> lanes = new ArrayList<>(laneCount);
        for (int i = 0; i < laneCount; i++) {
            String name = "lane-" + i;
            lanes.add(new Lane(i, name, "account://" + name,
                    new Account(network, mnemonic, baseIndex + i)));
        }
        return new FundingPlan(lanes);
    }

    public List<Lane> lanes() {
        return lanes;
    }

    public int size() {
        return lanes.size();
    }

    /** Round-robin assignment, so load spreads evenly across funding accounts. */
    public Lane laneFor(long sequence) {
        return lanes.get((int) Math.floorMod(sequence, lanes.size()));
    }

    // ------------------------------------------------------------------ preflight

    /** What the run needs versus what each lane actually holds. */
    public record Preflight(boolean sufficient, BigInteger requiredPerLane,
                            List<String> report, List<String> shortfalls) {
    }

    /**
     * Estimate what each lane needs and compare against its balance.
     *
     * <p>On a devnet the faucet fixes any shortfall automatically. On a public network nobody
     * can top these accounts up but you, so the run refuses to start and prints exactly which
     * address needs how much — discovering that 40 minutes into a soak is a waste of a testnet
     * and an evening.
     */
    public Preflight preflight(BackendService backend, long totalItems,
                               BigInteger averagePayment, BigInteger feeHeadroomPerItem) {
        long perLane = (long) Math.ceil(totalItems / (double) lanes.size());
        BigInteger perItem = averagePayment.add(feeHeadroomPerItem);
        // 20% margin: fees vary, and running dry mid-soak invalidates the run.
        BigInteger required = perItem.multiply(BigInteger.valueOf(perLane))
                .multiply(BigInteger.valueOf(120)).divide(BigInteger.valueOf(100));

        List<String> report = new ArrayList<>();
        List<String> shortfalls = new ArrayList<>();
        boolean sufficient = true;

        for (Lane lane : lanes) {
            BigInteger balance = balanceOf(backend, lane.address());
            boolean ok = balance.compareTo(required) >= 0;
            if (!ok) sufficient = false;
            report.add(String.format("  %-8s %-12s balance %10s ADA   needs %10s ADA   %s",
                    lane.name(), shorten(lane.address()), ada(balance), ada(required),
                    ok ? "OK" : "SHORT"));
            if (!ok) {
                shortfalls.add(String.format("send at least %s ADA to %s   (%s)",
                        ada(required.subtract(balance)), lane.address(), lane.name()));
            }
        }
        return new Preflight(sufficient, required, report, shortfalls);
    }

    /**
     * Full lovelace balance at an address.
     *
     * <p>Uses {@link UtxoSupplier#getAll(String)} rather than
     * {@code UtxoService.getUtxos(address, count, page)}. The latter is a <em>paginated</em>
     * call — {@code count} is the page size — so reading page 1 alone silently caps the answer
     * at 100 UTxOs. That is a trap tailor-made for soak runs, where a recipient accumulates one
     * UTxO per payment and quietly passes the page limit after a few hundred: the balance is
     * then under-reported and the reconciler blames the library for UNDER PAID.
     */
    public static BigInteger balanceOf(BackendService backend, String address) {
        try {
            UtxoSupplier supplier = new DefaultUtxoSupplier(backend.getUtxoService());
            return supplier.getAll(address).stream()
                    .flatMap(utxo -> utxo.getAmount().stream())
                    .filter(amount -> "lovelace".equals(amount.getUnit())
                            && amount.getQuantity() != null)
                    .map(amount -> amount.getQuantity())
                    .reduce(BigInteger.ZERO, BigInteger::add);
        } catch (Exception e) {
            return BigInteger.ZERO;
        }
    }

    public static String ada(BigInteger lovelace) {
        return new BigDecimal(lovelace)
                .divide(BigDecimal.valueOf(1_000_000), 2, RoundingMode.DOWN)
                .toPlainString();
    }

    private static String shorten(String address) {
        if (address == null || address.length() <= 20) return address;
        return address.substring(0, 10) + "..." + address.substring(address.length() - 6);
    }
}
