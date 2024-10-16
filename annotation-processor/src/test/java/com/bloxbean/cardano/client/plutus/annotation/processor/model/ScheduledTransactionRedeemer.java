package com.bloxbean.cardano.client.plutus.annotation.processor.model;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Constr(alternative = 3)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ScheduledTransactionRedeemer implements TankRedeemer {
    private BigInteger inputTankIndex;
    private BatcherAddress batcher;
}
