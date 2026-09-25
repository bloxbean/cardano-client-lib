package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.transaction.spec.ChangeOutput;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * CIP-2 "self-organisation": for each payment in the transaction, one ADA-only change piece of about the same size is
 * created. Over time the wallet fills up with UTxOs matching its typical payment sizes, so a later payment of a similar
 * size can be funded by a single UTxO.
 * <ul>
 *     <li>Payments are the non-change outputs of the transaction, largest first. Their ADA amount is used.</li>
 *     <li>A payment below min-ada, or one that would leave a remainder below min-ada, is skipped.</li>
 *     <li>The remainder is one more ADA-only piece. At most {@code maxPieces} pieces in total.</li>
 * </ul>
 * See <a href="https://cips.cardano.org/cip/CIP-0002">CIP-2</a>, section "Self organisation".
 */
@Getter
@ToString
public class PaymentSizedStrategy extends AbstractChangeSplitStrategy {
    public static final int DEFAULT_MAX_PIECES = 5;

    private final int maxPieces;

    public PaymentSizedStrategy() {
        this(null, null);
    }

    /**
     * @param maxPieces     max number of ADA-only pieces, including the remainder. Default: 5
     * @param tokenBundling token bundling. Default: {@link ByteBudgetBundling}
     */
    @Builder
    public PaymentSizedStrategy(Integer maxPieces, TokenBundlingStrategy tokenBundling) {
        super(tokenBundling);
        this.maxPieces = maxPieces != null ? maxPieces : DEFAULT_MAX_PIECES;
        if (this.maxPieces < 1)
            throw new IllegalArgumentException("maxPieces must be >= 1");
    }

    @Override
    protected List<BigInteger> splitAda(BigInteger lovelace, ChangeSplitRequest request) {
        BigInteger minAda = request.adaOnlyMinAda();
        List<BigInteger> pieces = new ArrayList<>();
        BigInteger remaining = lovelace;

        for (BigInteger payment : paymentAmounts(request)) {
            if (pieces.size() >= maxPieces - 1)
                break;
            if (payment.compareTo(minAda) < 0 || remaining.subtract(payment).compareTo(minAda) < 0)
                continue;
            pieces.add(payment);
            remaining = remaining.subtract(payment);
        }

        pieces.add(remaining);
        return pieces;
    }

    private static List<BigInteger> paymentAmounts(ChangeSplitRequest request) {
        List<BigInteger> amounts = new ArrayList<>();
        if (request.getTransaction() == null || request.getTransaction().getBody() == null
                || request.getTransaction().getBody().getOutputs() == null)
            return amounts;

        for (TransactionOutput output : request.getTransaction().getBody().getOutputs()) {
            if (output instanceof ChangeOutput || output.getValue() == null)
                continue;
            BigInteger coin = ChangeValues.coinOf(output.getValue());
            if (coin.signum() > 0)
                amounts.add(coin);
        }
        amounts.sort(Comparator.reverseOrder());
        return amounts;
    }
}
