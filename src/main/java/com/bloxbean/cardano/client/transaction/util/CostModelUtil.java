package com.bloxbean.cardano.client.transaction.util;

import com.bloxbean.cardano.client.transaction.spec.CostMdls;
import com.bloxbean.cardano.client.transaction.spec.CostModel;
import com.bloxbean.cardano.client.transaction.spec.Language;

public class CostModelUtil {

    private final static long[] plutusV1Costs = new long[]
            {197209, 0, 1, 1, 396231, 621, 0, 1, 150000, 1000, 0, 1, 150000, 32,
                    2477736, 29175, 4, 29773, 100, 29773, 100, 29773, 100, 29773, 100, 29773,
                    100, 29773, 100, 100, 100, 29773, 100, 150000, 32, 150000, 32, 150000, 32,
                    150000, 1000, 0, 1, 150000, 32, 150000, 1000, 0, 8, 148000, 425507, 118,
                    0, 1, 1, 150000, 1000, 0, 8, 150000, 112536, 247, 1, 150000, 10000, 1,
                    136542, 1326, 1, 1000, 150000, 1000, 1, 150000, 32, 150000, 32, 150000,
                    32, 1, 1, 150000, 1, 150000, 4, 103599, 248, 1, 103599, 248, 1, 145276,
                    1366, 1, 179690, 497, 1, 150000, 32, 150000, 32, 150000, 32, 150000, 32,
                    150000, 32, 150000, 32, 148000, 425507, 118, 0, 1, 1, 61516, 11218, 0, 1,
                    150000, 32, 148000, 425507, 118, 0, 1, 1, 148000, 425507, 118, 0, 1, 1,
                    2477736, 29175, 4, 0, 82363, 4, 150000, 5000, 0, 1, 150000, 32, 197209, 0,
                    1, 1, 150000, 32, 150000, 32, 150000, 32, 150000, 32, 150000, 32, 150000,
                    32, 150000, 32, 3345831, 1, 1};

    public static CostModel plutusV1CostModel = new CostModel(Language.PLUTUS_V1, plutusV1Costs);
    public static CostModel plutusV2CostModel = null; //TODO

    public static CostMdls getDefaultCostMdls() {
        CostMdls costMdls = new CostMdls();
        costMdls.add(plutusV1CostModel);

        return costMdls;
    }

    public static byte[] getDefaultLanguageViewsEncoding() {
        return getDefaultCostMdls().getLanguageViewEncoding();
    }
}
