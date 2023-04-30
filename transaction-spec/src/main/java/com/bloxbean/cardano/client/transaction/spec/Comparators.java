package com.bloxbean.cardano.client.transaction.spec;

import java.util.Comparator;

/**
 * Comparators used during cbor serialization
 */
class Comparators {

    public static Comparator<Asset> assetComparator = (asset1, asset2) -> {
        byte[] o1 = asset1.getNameAsBytes();
        byte[] o2 = asset2.getNameAsBytes();

        if (o1.length < o2.length) {
            return -1;
        }
        if (o1.length > o2.length) {
            return 1;
        }
        for (int i = 0; i < o1.length; i++) {
            if (o1[i] < o2[i]) {
                return -1;
            }
            if (o1[i] > o2[i]) {
                return 1;
            }
        }
        return 0;
    };

}
