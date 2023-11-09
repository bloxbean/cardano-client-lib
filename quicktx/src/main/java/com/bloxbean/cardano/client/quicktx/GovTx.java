package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.cert.RegDrepCert;
import com.bloxbean.cardano.client.transaction.spec.cert.UnregDrepCert;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.util.Tuple;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;

@Slf4j
public class GovTx {
    //TODO -- Read from protocol
    public static final BigInteger DREP_REG_DEPOSIT = adaToLovelace(2.0);
    public static final Amount DUMMY_MIN_OUTPUT_VAL = Amount.ada(1.0);

    protected List<RegDrepCert> drepRegistrations;
    protected List<DRepDeregestrationContext> dRepDeregestrationContexts;


    public GovTx registerDRep(@NonNull Credential drepCredential, Anchor anchor) {
        var regDRepCert = RegDrepCert.builder()
                .drepCredential(drepCredential)
                .anchor(anchor)
                .coin(DREP_REG_DEPOSIT)
                .build();

        if (drepRegistrations == null)
            drepRegistrations = new ArrayList<>();

        drepRegistrations.add(regDRepCert);
        return this;
    }

    public GovTx unregisterDRep(@NonNull Credential drepCredential, String refundAddress, BigInteger refundAmount) {
        if (refundAmount == null)
            refundAmount = DREP_REG_DEPOSIT;

        var unregDRepCert = UnregDrepCert.builder()
                .drepCredential(drepCredential)
                .coin(refundAmount)
                .build();

        if (dRepDeregestrationContexts == null)
            dRepDeregestrationContexts = new ArrayList<>();

        dRepDeregestrationContexts.add(new DRepDeregestrationContext(unregDRepCert, refundAddress, refundAmount));
        return this;
    }

    /**
     * Return TxBuilder, payments to build a drep transaction
     *
     * @param fromAddress
     * @return Tuple<List<PaymentContext>, TxBuilder>
     */
    Tuple<List<GovTx.PaymentContext>, TxBuilder> build(String fromAddress, String changeAddress) {
        List<GovTx.PaymentContext> paymentContexts = buildGovernancePayments(fromAddress, changeAddress);

        TxBuilder txBuilder = (context, txn) -> {
        };
        txBuilder = buildDRepRegistration(txBuilder, fromAddress);
        txBuilder = buildDRepDeRegistration(txBuilder, fromAddress);

        return new Tuple<>(paymentContexts, txBuilder);
    }

    private List<GovTx.PaymentContext> buildGovernancePayments(String fromAddress, String changeAddress) {
        List<GovTx.PaymentContext> paymentContexts = new ArrayList<>();
        if ((drepRegistrations == null || drepRegistrations.size() == 0)
                && (dRepDeregestrationContexts == null || dRepDeregestrationContexts.size() == 0)) {
            return paymentContexts;
        }

        if (drepRegistrations != null && drepRegistrations.size() > 0) {
            //Dummy pay to fromAddress to add deposit
            Amount totalDRepRegistrationDepositAmount = Amount.lovelace(DREP_REG_DEPOSIT.multiply(BigInteger.valueOf(drepRegistrations.size())));
            paymentContexts.add(new GovTx.PaymentContext(fromAddress, totalDRepRegistrationDepositAmount));
        }

        if (dRepDeregestrationContexts != null && dRepDeregestrationContexts.size() > 0) {
            paymentContexts.add(new GovTx.PaymentContext(fromAddress, DUMMY_MIN_OUTPUT_VAL)); //Dummy output to sender fromAddress to trigger input selection
        }

        return paymentContexts;
    }

    private TxBuilder buildDRepRegistration(TxBuilder txBuilder, String fromAddress) {
        if (drepRegistrations == null || drepRegistrations.size() == 0)
            return txBuilder;

        txBuilder = txBuilder.andThen((context, txn) -> {
            if (drepRegistrations == null || drepRegistrations.size() == 0) {
                return;
            }

            //Add DRep registration certificate
            List<Certificate> certificates = txn.getBody().getCerts();
            if (certificates == null) {
                certificates = new ArrayList<>();
                txn.getBody().setCerts(certificates);
            }

            certificates.addAll(drepRegistrations);

            String drepRegDepositParam = DREP_REG_DEPOSIT.toString();//context.getProtocolParams().getKeyDeposit(); //TODO -- Get protocol param
            BigInteger drepRegDeposit = new BigInteger(drepRegDepositParam);
            BigInteger totalDRepRegDeposit = drepRegDeposit.multiply(BigInteger.valueOf(drepRegistrations.size()));
            log.debug("Total stakekey registration deposit: " + totalDRepRegDeposit);

            txn.getBody().getOutputs()
                    .stream().filter(to -> to.getAddress().equals(fromAddress) && to.getValue().getCoin().compareTo(totalDRepRegDeposit) >= 0)
                    .sorted((o1, o2) -> o2.getValue().getCoin().compareTo(o1.getValue().getCoin()))
                    .findFirst()
                    .ifPresentOrElse(to -> {
                        //Remove the deposit amount from the from addres output
                        to.getValue().setCoin(to.getValue().getCoin().subtract(totalDRepRegDeposit));

                        if (to.getValue().getCoin().equals(BigInteger.ZERO) && to.getValue().getMultiAssets() == null && to.getValue().getMultiAssets().size() == 0) {
                            txn.getBody().getOutputs().remove(to);
                        }
                    }, () -> {
                        throw new TxBuildException("Output for from address not found to remove deposit amount: " + fromAddress);
                    });
        });
        return txBuilder;
    }

    private TxBuilder buildDRepDeRegistration(TxBuilder txBuilder, String fromAddress) {
        if (dRepDeregestrationContexts == null || dRepDeregestrationContexts.size() == 0)
            return txBuilder;

        txBuilder = txBuilder.andThen((context, txn) -> {
            if (dRepDeregestrationContexts == null || dRepDeregestrationContexts.size() == 0) {
                return;
            }

            //Add DRep de-registration certificates
            List<Certificate> certificates = txn.getBody().getCerts();
            if (certificates == null) {
                certificates = new ArrayList<>();
                txn.getBody().setCerts(certificates);
            }

            if (txn.getWitnessSet() == null) {
                txn.setWitnessSet(new TransactionWitnessSet());
            }

            for (GovTx.DRepDeregestrationContext dRepDeregestrationContext : dRepDeregestrationContexts) {
                certificates.add(dRepDeregestrationContext.getUnregDrepCert());

                if (dRepDeregestrationContext.refundAddress == null)
                    dRepDeregestrationContext.refundAddress = fromAddress;

                //Add deposit refund
                txn.getBody().getOutputs()
                        .stream().filter(to -> to.getAddress().equals(dRepDeregestrationContext.refundAddress))
                        .findFirst()
                        .ifPresentOrElse(to -> {
                            //Add deposit amount to the change address
                            to.getValue().setCoin(to.getValue().getCoin().add(dRepDeregestrationContext.refundAmount));
                        }, () -> {
                            TransactionOutput transactionOutput = new TransactionOutput(dRepDeregestrationContext.refundAddress,
                                    Value.builder().coin(dRepDeregestrationContext.refundAmount).build());
                            txn.getBody().getOutputs().add(transactionOutput);
                        });
            }
        });
        return txBuilder;
    }

    @Data
    @AllArgsConstructor
    static class DRepDeregestrationContext {
        private UnregDrepCert unregDrepCert;
        private String refundAddress;
        private BigInteger refundAmount;
    }

    @Data
    @AllArgsConstructor
    static class PaymentContext {
        private String address;
        private Amount amount;
    }

}

