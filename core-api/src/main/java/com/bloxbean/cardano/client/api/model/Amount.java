package com.bloxbean.cardano.client.api.model;

import com.bloxbean.cardano.client.transaction.util.AssetUtil;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import java.math.BigInteger;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Amount {

    private String unit;
    private BigInteger quantity;

    public static Amount lovelace(BigInteger quantity) {
        return Amount.builder()
                .unit(LOVELACE)
                .quantity(quantity)
                .build();
    }

    public static Amount ada(Double ada) {
        return Amount.builder()
                .unit(LOVELACE)
                .quantity(adaToLovelace(ada))
                .build();
    }

    public static Amount ada(long ada) {
        return ada((double) ada);
    }

    public static Amount asset(String unit, BigInteger quantity) {
        return Amount.builder()
                .unit(unit)
                .quantity(quantity)
                .build();
    }

    public static Amount asset(String unit, long quantity) {
        return Amount.asset(unit, BigInteger.valueOf(quantity));
    }

    public static Amount asset(String policy, String assetName, BigInteger quantity) {
        String unit = AssetUtil.getUnit(policy, assetName);
        return Amount.asset(unit, quantity);
    }

    public static Amount asset(String policy, String assetName, long quantity) {
        return Amount.asset(policy, assetName, BigInteger.valueOf(quantity));
    }
}
