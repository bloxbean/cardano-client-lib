package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.common.MinAdaCalculator;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared constants, builders and assertions for unfrack tests.
 */
final class UnfrackFixtures {
    static final String ADDRESS = "addr_test1qpcf5ursqpwx2tp8maeah00rxxdfpvf8h65k4hk3chac0fvu28duly863yqhgjtl8an2pkksd6mlzv0qv4nejh5u2zjsshr90k";
    static final String RECEIVER = "addr_test1qzllzd3cxvz53k9gkq3n3mpcm6g7kv7rj5yvs88n7xwm3nmcs8dpnr85lclka6sycwccput39p0cffqegn8kkf6euzks6h9ldv";
    static final String POLICY_1 = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8";
    static final String POLICY_2 = "b1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8";
    static final String POLICY_3 = "c1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8";

    static final ProtocolParams PROTOCOL_PARAMS = ProtocolParams.builder().coinsPerUtxoSize("4310").build();
    static final MinAdaCalculator MIN_ADA_CALCULATOR = new MinAdaCalculator(PROTOCOL_PARAMS);

    private UnfrackFixtures() {
    }

    static ChangeSplitRequest request(Value change) {
        return request(change, null, null);
    }

    static ChangeSplitRequest request(Value change, Transaction transaction, UtxoSupplier utxoSupplier) {
        return ChangeSplitRequest.builder()
                .address(ADDRESS)
                .change(change)
                .protocolParams(PROTOCOL_PARAMS)
                .transaction(transaction)
                .utxoSupplier(utxoSupplier)
                .build();
    }

    static MultiAsset multiAsset(String policyId, int count, BigInteger qty) {
        List<Asset> assets = new ArrayList<>();
        IntStream.range(0, count).forEach(i -> assets.add(new Asset("token" + i, qty)));
        return new MultiAsset(policyId, assets);
    }

    /**
     * Policy id derived from an index, e.g. for wallets with many policies.
     */
    static String policy(int index) {
        return String.format("%056x", index + 1);
    }

    static Transaction tx(TransactionOutput... outputs) {
        TransactionBody body = TransactionBody.builder().outputs(new ArrayList<>(List.of(outputs))).build();
        return Transaction.builder().body(body).build();
    }

    static BigInteger minAda(Value value) {
        return MIN_ADA_CALCULATOR.calculateMinAda(new TransactionOutput(ADDRESS, value));
    }

    static BigInteger adaOnlyMinAda() {
        return minAda(Value.fromCoin(BigInteger.valueOf(1_000_000)));
    }

    static void assertAllMeetMinAda(List<Value> values) {
        assertThat(values).isNotEmpty()
                .allSatisfy(v -> assertThat(v.getCoin()).isGreaterThanOrEqualTo(minAda(v)));
    }

    static void assertConserved(Value original, List<Value> pieces) {
        assertThat(ChangeValues.sumUnits(pieces)).isEqualTo(ChangeValues.toUnitMap(original));
    }

    static void assertValid(Value original, List<Value> pieces) {
        assertConserved(original, pieces);
        assertAllMeetMinAda(pieces);
    }

    static List<Value> adaOnly(List<Value> pieces) {
        return pieces.stream().filter(v -> v.getMultiAssets() == null || v.getMultiAssets().isEmpty()).toList();
    }

    static List<Value> withTokens(List<Value> pieces) {
        return pieces.stream().filter(v -> v.getMultiAssets() != null && !v.getMultiAssets().isEmpty()).toList();
    }
}
