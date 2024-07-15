package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;

import java.math.BigInteger;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;

/**
 * Output builder for deposit refund for different types of deposits
 */
class DepositRefundOutputBuilder {
    private static final BigInteger DUMMY_MIN_OUTPUT_VAL = adaToLovelace(1);

    public static TxOutputBuilder createFromDepositRefundContext(DepositRefundContext depositRefundContext) {
        return (context, outputs) -> {
            var depositType = depositRefundContext.getDepositType();
            BigInteger totalDepositRefund = getAmount(context.getProtocolParams(), depositType,
                    BigInteger.valueOf(depositRefundContext.getCount()));

            outputs.add(new TransactionOutput(depositRefundContext.getAddress(), Value.builder().coin(totalDepositRefund).build()));
        };
    }

    private static BigInteger getAmount(ProtocolParams protocolParams, DepositRefundType depositType, BigInteger count) {
        switch (depositType) {
            case STAKE_KEY_REGISTRATION:
                return new BigInteger(protocolParams.getKeyDeposit()).multiply(count);
            case POOL_REGISTRATION:
                return new BigInteger(protocolParams.getPoolDeposit()).multiply(count);
            case DREP_REGISTRATION:
                return protocolParams.getDrepDeposit().multiply(count);
            case GOV_ACTION:
                return protocolParams.getGovActionDeposit().multiply(count);
            case STAKE_KEY_DEREGISTRATION:
            case DREP_DEREGISTRATION:
            case DELGATION:
            case WITHDRAWAL:
            case POOL_RETIREMENT:
                return DUMMY_MIN_OUTPUT_VAL; //This is just to trigger input selection
            default:
                return BigInteger.ZERO;
        }
    }
}
