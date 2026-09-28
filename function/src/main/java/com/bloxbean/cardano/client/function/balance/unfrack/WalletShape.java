package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.api.model.Amount;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

/**
 * Target shape of a wallet, used by {@link Unfrack}: how ADA-only UTxOs should look ({@link AdaShape}), how tokens are
 * grouped ({@link TokenBundlingStrategy}) and whether small UTxOs are merged ({@link Consolidation}).
 * Use a profile (e.g. {@link #hygiene()}) or the builder. Profiles are explained in the unfracking ADR.
 */
@Value
@Builder(toBuilder = true)
public class WalletShape {

    /**
     * Default: 3 ADA-only UTxOs of at least 50 ADA.
     */
    @NonNull
    @Builder.Default
    AdaShape ada = AdaShape.lanes(3, Amount.ada(50));

    /**
     * Default: {@link ByteBudgetBundling} with 1,000 bytes.
     */
    @NonNull
    @Builder.Default
    TokenBundlingStrategy tokens = new ByteBudgetBundling();

    /**
     * Default: no consolidation.
     */
    @NonNull
    @Builder.Default
    Consolidation consolidation = Consolidation.none();

    /**
     * Everyday wallet: 3 ADA-only UTxOs of at least 50 ADA, tokens bundled by size, up to 3 small UTxOs merged per
     * transaction.
     */
    public static WalletShape hygiene() {
        return WalletShape.builder()
                .consolidation(Consolidation.opportunistic(3))
                .build();
    }

    /**
     * Service or bot: {@code lanes} ADA-only UTxOs of at least {@code laneSize}, so up to {@code lanes} payments of
     * that size can be funded by separate UTxOs. No consolidation.
     */
    public static WalletShape throughput(int lanes, Amount laneSize) {
        return WalletShape.builder()
                .ada(AdaShape.lanes(lanes, laneSize))
                .build();
    }

    /**
     * Token holder: 2 ADA-only UTxOs of at least 20 ADA, and up to 20 UTxOs of at most 10 ADA merged per transaction
     * to reclaim ADA locked next to tokens.
     */
    public static WalletShape collector() {
        return WalletShape.builder()
                .ada(AdaShape.lanes(2, Amount.ada(20)))
                .consolidation(Consolidation.opportunistic(20, Amount.ada(10)))
                .build();
    }

    /**
     * DEX, marketplace or token staking: one policy per UTxO ({@link PolicyBundling}), 3 ADA-only UTxOs of at least
     * 50 ADA, no consolidation.
     */
    public static WalletShape dex() {
        return WalletShape.builder()
                .tokens(new PolicyBundling())
                .build();
    }

    /**
     * Rules only, for transactions built for someone else's wallet (e.g. a dApp building for a CIP-30 wallet) without
     * knowing its intent: tokens bundled by size and kept apart from ADA, ADA in one output, no consolidation.
     */
    public static WalletShape minimal() {
        return WalletShape.builder()
                .ada(AdaShape.single())
                .build();
    }

    /**
     * No wallet view (hardware or offline signing): ADA split by 50/15/10/10/5/5/5 % above 100 ADA, tokens bundled by
     * size, no consolidation. Doesn't read the wallet.
     */
    public static WalletShape offline() {
        return WalletShape.builder()
                .ada(AdaShape.percentages(Amount.ada(100), 50, 15, 10, 10, 5, 5, 5))
                .build();
    }
}
