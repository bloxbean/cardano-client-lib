package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeCredential;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeDeregistration;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeRegistration;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;

/**
 * Abstract class for Stake delegation specific transactions
 * @param <T>
 */
@Slf4j
public abstract class StakeTx<T> extends AbstractTx<T> {
    public static final BigInteger STAKE_KEY_REG_DEPOSIT = adaToLovelace(2.0);
    protected List<StakeRegistration> stakeRegistrations;
    protected List<StakeDeregistration> stakeDeRegistrations;

    /**
     * Register stake address
     * @param address address to register. Address should have delegation credential. So it should be a base address or stake address.
     * @return T
     */
    public T registerStakeAddress(@NonNull String address) {
        return registerStakeAddress(new Address(address));
    }

    /**
     * Register stake address
     * @param address address to register. Address should have delegation credential. So it should be a base address or stake address.
     * @return T
     */
    public T registerStakeAddress(@NonNull Address address) {
        byte[] delegationHash = address.getDelegationCredential()
                .orElseThrow(() -> new TxBuildException("Invalid stake address. Address does not have delegation credential"));

        StakeCredential stakeCredential = null;
        if (address.isStakeKeyHashInDelegationPart())
            stakeCredential = StakeCredential.fromKeyHash(delegationHash);
        else if (address.isScriptHashInDelegationPart())
            stakeCredential = StakeCredential.fromScriptHash(delegationHash);

        if (stakeRegistrations == null)
            stakeRegistrations = new ArrayList<>();

        //-- Stake key registration
        StakeRegistration stakeRegistration = new StakeRegistration(stakeCredential);
        stakeRegistrations.add(stakeRegistration);

        return (T) this;
    }

    /**
     * De-register stake address
     * @param address address to de-register. Address should have delegation credential. So it should be a base address or stake address.
     * @return T
     */
    public T deregisterStakeAddress(@NonNull String address) {
        return deregisterStakeAddress(new Address(address));
    }

    /**
     * De-register stake address
     * @param address address to de-register. Address should have delegation credential. So it should be a base address or stake address.
     * @return T
     */
    public T deregisterStakeAddress(@NonNull Address address) {
        byte[] delegationHash = address.getDelegationCredential()
                .orElseThrow(() -> new TxBuildException("Invalid stake address. Address does not have delegation credential"));

        StakeCredential stakeCredential = null;
        if (address.isStakeKeyHashInDelegationPart())
            stakeCredential = StakeCredential.fromKeyHash(delegationHash);
        else if (address.isScriptHashInDelegationPart())
            stakeCredential = StakeCredential.fromScriptHash(delegationHash);

        if (stakeDeRegistrations == null)
            stakeDeRegistrations = new ArrayList<>();

        //-- Stake key de-registration
        StakeDeregistration stakeDeregistration = new StakeDeregistration(stakeCredential);
        stakeDeRegistrations.add(stakeDeregistration);

        return (T) this;
    }


    @Override
    TxBuilder complete() {
        if ((stakeRegistrations == null || stakeRegistrations.size() == 0)
                && (stakeDeRegistrations == null || stakeDeRegistrations.size() == 0))
            return super.complete();

        if (stakeRegistrations != null && stakeRegistrations.size() > 0) {
            //Dummy pay to address to add deposit
            Amount totalStakeDepositAmount = Amount.lovelace(STAKE_KEY_REG_DEPOSIT.multiply(BigInteger.valueOf(stakeRegistrations.size())));
            payToAddress(getFromAddress(), totalStakeDepositAmount);
        }

        if (stakeDeRegistrations != null && stakeDeRegistrations.size() > 0 && (outputs == null || outputs.size() == 0)) {
            payToAddress(getFromAddress(), Amount.ada(1.0)); //Dummy output to sender address to trigger input selection
        }

        TxBuilder txBuilder = super.complete();

        txBuilder = buildStakeAddressRegistration(txBuilder);
        txBuilder = buildStakeAddressDeRegistration(txBuilder);

        return txBuilder;
    }

    private TxBuilder buildStakeAddressRegistration(TxBuilder txBuilder) {
        if (stakeRegistrations == null || stakeRegistrations.size() == 0)
            return txBuilder;

        String fromAddress = getFromAddress();

        txBuilder = txBuilder.andThen((context, txn) -> {
            if (stakeRegistrations == null || stakeRegistrations.size() == 0) {
                return;
            }

            //Add stake registration certificate
            List<Certificate> certificates = txn.getBody().getCerts();
            if (certificates == null) {
                certificates = new ArrayList<>();
                txn.getBody().setCerts(certificates);
            }

            certificates.addAll(stakeRegistrations);

            String keyDeposit = context.getProtocolParams().getKeyDeposit();
            BigInteger stakeKeyDeposit = new BigInteger(keyDeposit);
            BigInteger totalStakeKeyDeposit = stakeKeyDeposit.multiply(BigInteger.valueOf(stakeRegistrations.size()));
            log.debug("Total stakekey registration deposit: " + totalStakeKeyDeposit);

            txn.getBody().getOutputs()
                    .stream().filter(to -> to.getAddress().equals(fromAddress) && to.getValue().getCoin().compareTo(totalStakeKeyDeposit) >= 0)
                    .sorted((o1, o2) -> o2.getValue().getCoin().compareTo(o1.getValue().getCoin()))
                    .findFirst()
                    .ifPresentOrElse(to -> {
                        //Remove the deposit amount from the from addres output
                        to.getValue().setCoin(to.getValue().getCoin().subtract(totalStakeKeyDeposit));

                        if (to.getValue().getCoin().equals(BigInteger.ZERO) && to.getValue().getMultiAssets() == null && to.getValue().getMultiAssets().size() == 0) {
                            txn.getBody().getOutputs().remove(to);
                        }
                    }, () -> {
                        throw new TxBuildException("Output for from address not found to remove deposit amount: " + fromAddress);
                    });
        });
        return txBuilder;
    }

    private TxBuilder buildStakeAddressDeRegistration(TxBuilder txBuilder) {
        if (stakeDeRegistrations == null || stakeDeRegistrations.size() == 0)
            return txBuilder;

        String changeAddress = getChangeAddress();

        txBuilder = txBuilder.andThen((context, txn) -> {
            if (stakeDeRegistrations == null || stakeDeRegistrations.size() == 0) {
                return;
            }

            //Add stake de-registration certificates
            List<Certificate> certificates = txn.getBody().getCerts();
            if (certificates == null) {
                certificates = new ArrayList<>();
                txn.getBody().setCerts(certificates);
            }

            certificates.addAll(stakeDeRegistrations);

            BigInteger totalStakeKeyDeposit = STAKE_KEY_REG_DEPOSIT.multiply(BigInteger.valueOf(stakeDeRegistrations.size()));
            log.debug("Total stake key deposit to refund: " + totalStakeKeyDeposit);

            txn.getBody().getOutputs()
                    .stream().filter(to -> to.getAddress().equals(changeAddress))
                    .findFirst()
                    .ifPresentOrElse(to -> {
                        //Add deposit amount to the change address
                        to.getValue().setCoin(to.getValue().getCoin().add(totalStakeKeyDeposit));
                    }, () -> {
                        TransactionOutput transactionOutput = new TransactionOutput(changeAddress, Value.builder().coin(totalStakeKeyDeposit).build());
                        txn.getBody().getOutputs().add(transactionOutput);
                    });
        });
        return txBuilder;
    }
}
