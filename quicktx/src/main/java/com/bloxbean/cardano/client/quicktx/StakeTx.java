package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.spec.cert.*;
import lombok.AllArgsConstructor;
import lombok.Data;
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
    //TODO -- Read from protocol params
    public static final BigInteger STAKE_KEY_REG_DEPOSIT = adaToLovelace(2.0);
    public static final Amount DUMMY_MIN_OUTPUT_VAL = Amount.ada(1.0);

    protected List<StakeRegistration> stakeRegistrations;
    protected List<StakeKeyDeregestrationContext> stakeDeRegistrationContexts;
    protected List<StakeDelegationContext> stakeDelegationContexts;

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
     * De-register stake address. The key deposit will be refunded to the change address or fee payer if change address is not specified
     * @param address address to de-register. Address should have delegation credential. So it should be a base address or stake address.
     * @return T
     */
    public T deregisterStakeAddress(@NonNull String address) {
        return deregisterStakeAddress(new Address(address));
    }

    /**
     * De-register stake address. The key deposit will be refunded to the change address or fee payer if change address is not specified
     * @param address address to de-register. Address should have delegation credential. So it should be a base address or stake address.
     * @return T
     */
    public T deregisterStakeAddress(@NonNull Address address) {
        return deregisterStakeAddress(address, null, null);
    }

    /**
     * De-register stake address. The key deposit will be refunded to the refund address.
     * @param address address to de-register. Address should have delegation credential. So it should be a base address or stake address.
     * @param refundAddr refund address
     * @return T
     */
    public T deregisterStakeAddress(@NonNull String address, @NonNull String refundAddr) {
        return deregisterStakeAddress(new Address(address), null, refundAddr);
    }

    /**
     * De-register stake address. The key deposit will be refunded to the refund address.
     * @param address address to de-register. Address should have delegation credential. So it should be a base address or stake address.
     * @param refundAddr refund address
     * @return T
     */
    public T deregisterStakeAddress(@NonNull Address address, @NonNull String refundAddr) {
        return deregisterStakeAddress(address, null, refundAddr);
    }

    /**
     * De-register stake address. The key deposit will be refunded to the change address or fee payer if change address is not specified.
     * @param address address to de-register. Address should have delegation credential. So it should be a base address or stake address.
     * @param redeemer redeemer to use if the address is a script address
     * @return T
     */
    public T deregisterStakeAddress(@NonNull String address, PlutusData redeemer) {
        return deregisterStakeAddress(new Address(address), redeemer, null);
    }

    /**
     * De-register stake address. The key deposit will be refunded to the change address or fee payer if change address is not specified.
     * @param address address to de-register. Address should have delegation credential. So it should be a base address or stake address.
     * @param redeemer redeemer to use if the address is a script address
     * @return T
     */
    public T deregisterStakeAddress(@NonNull Address address, PlutusData redeemer) {
        return deregisterStakeAddress(address, redeemer, null);
    }

    /**
     * De-register stake address. The key deposit will be refunded to the refund address.
     * @param address address to de-register. Address should have delegation credential. So it should be a base address or stake address.
     * @param redeemer redeemer to use if the address is a script address
     * @param refundAddr refund address
     * @return T
     */
    public T deregisterStakeAddress(@NonNull String address, PlutusData redeemer, String refundAddr) {
        return deregisterStakeAddress(new Address(address), redeemer, refundAddr);
    }

    /**
     * De-register stake address. The key deposit will be refunded to the refund address.
     * @param address address to de-register. Address should have delegation credential. So it should be a base address or stake address.
     * @param redeemer redeemer to use if the address is a script address
     * @param refundAddr refund address
     * @return T
     */
    public T deregisterStakeAddress(@NonNull Address address, PlutusData redeemer, String refundAddr) {
        byte[] delegationHash = address.getDelegationCredential()
                .orElseThrow(() -> new TxBuildException("Invalid stake address. Address does not have delegation credential"));

        StakeCredential stakeCredential = null;
        if (address.isStakeKeyHashInDelegationPart())
            stakeCredential = StakeCredential.fromKeyHash(delegationHash);
        else if (address.isScriptHashInDelegationPart())
            stakeCredential = StakeCredential.fromScriptHash(delegationHash);

        if (stakeDeRegistrationContexts == null)
            stakeDeRegistrationContexts = new ArrayList<>();

        //-- Stake key de-registration
        StakeDeregistration stakeDeregistration = new StakeDeregistration(stakeCredential);
        Redeemer _redeemer = null;
        if (redeemer != null) {
            _redeemer = Redeemer.builder()
                    .tag(RedeemerTag.Cert)
                    .data(redeemer)
                    .index(BigInteger.valueOf(1)) //dummy value
                    .exUnits(ExUnits.builder()
                            .mem(BigInteger.valueOf(10000)) // Some dummy value
                            .steps(BigInteger.valueOf(1000))
                            .build())
                    .build();
        }

        StakeKeyDeregestrationContext stakeKeyDeregestrationContext = new StakeKeyDeregestrationContext(stakeDeregistration, _redeemer, refundAddr);
        stakeDeRegistrationContexts.add(stakeKeyDeregestrationContext);

        return (T) this;
    }

    /**
     * Delegate stake address to a stake pool
     * @param address address to delegate. Address should have delegation credential. So it should be a base address or stake address.
     * @param poolId stake pool id Bech32 or hex encoded
     * @return T
     */
    public T delegateTo(@NonNull String address, @NonNull String poolId) {
        return delegateTo(new Address(address), poolId, null);
    }

    /**
     * Delegate stake address to a stake pool
     * @param address address to delegate. Address should have delegation credential. So it should be a base address or stake address.
     * @param poolId stake pool id Bech32 or hex encoded
     * @return T
     */
    public T delegateTo(@NonNull Address address, @NonNull String poolId) {
        return delegateTo(address, poolId, null);
    }

    /**
     * Delegate stake address to a stake pool
     * @param address address to delegate. Address should have delegation credential. So it should be a base address or stake address.
     * @param poolId stake pool id Bech32 or hex encoded
     * @param redeemer redeemer to use if the address is a script address
     * @return T
     */
    public T delegateTo(@NonNull String address, @NonNull String poolId, PlutusData redeemer) {
        return delegateTo(new Address(address), poolId, redeemer);
    }

    /**
     * Delegate stake address to a stake pool
     * @param address address to delegate. Address should have delegation credential. So it should be a base address or stake address.
     * @param poolId stake pool id Bech32 or hex encoded
     * @param redeemer redeemer to use if the address is a script address
     * @return T
     */
    public T delegateTo(@NonNull Address address, @NonNull String poolId, PlutusData redeemer) {
        byte[] delegationHash = address.getDelegationCredential()
                .orElseThrow(() -> new TxBuildException("Invalid stake address. Address does not have delegation credential"));

        StakeCredential stakeCredential = null;
        if (address.isStakeKeyHashInDelegationPart())
            stakeCredential = StakeCredential.fromKeyHash(delegationHash);
        else if (address.isScriptHashInDelegationPart())
            stakeCredential = StakeCredential.fromScriptHash(delegationHash);

        if (stakeDelegationContexts == null)
            stakeDelegationContexts = new ArrayList<>();

        StakePoolId stakePoolId;
        if (poolId.startsWith("pool")) {
            stakePoolId = StakePoolId.fromBech32PoolId(poolId);
        } else {
            stakePoolId = StakePoolId.fromHexPoolId(poolId);
        }

        //-- Stake delegation
        StakeDelegation stakeDelegation = new StakeDelegation(stakeCredential, stakePoolId);
        Redeemer _redeemer = null;
        if (redeemer != null) {
            _redeemer = Redeemer.builder()
                    .tag(RedeemerTag.Cert)
                    .data(redeemer)
                    .index(BigInteger.valueOf(1)) //dummy value
                    .exUnits(ExUnits.builder()
                            .mem(BigInteger.valueOf(10000)) // Some dummy value
                            .steps(BigInteger.valueOf(1000))
                            .build())
                    .build();
        }

        StakeDelegationContext stakeDelegationContext = new StakeDelegationContext(stakeDelegation, _redeemer);
        stakeDelegationContexts.add(stakeDelegationContext);

        return (T) this;

    }

    @Override
    TxBuilder complete() {
        if ((stakeRegistrations == null || stakeRegistrations.size() == 0)
                && (stakeDeRegistrationContexts == null || stakeDeRegistrationContexts.size() == 0)
                && (stakeDelegationContexts == null || stakeDelegationContexts.size() == 0))
            return super.complete();

        if (stakeRegistrations != null && stakeRegistrations.size() > 0) {
            //Dummy pay to address to add deposit
            Amount totalStakeDepositAmount = Amount.lovelace(STAKE_KEY_REG_DEPOSIT.multiply(BigInteger.valueOf(stakeRegistrations.size())));
            payToAddress(getFromAddress(), totalStakeDepositAmount);
        }

        if (stakeDeRegistrationContexts != null && stakeDeRegistrationContexts.size() > 0 && (outputs == null || outputs.size() == 0)) {
            payToAddress(getFromAddress(), DUMMY_MIN_OUTPUT_VAL); //Dummy output to sender address to trigger input selection
        }

        if (stakeDelegationContexts != null && stakeDelegationContexts.size() > 0 && (outputs == null || outputs.size() == 0)) {
            payToAddress(getFromAddress(), DUMMY_MIN_OUTPUT_VAL); //Dummy output to sender address to trigger input selection
        }

        TxBuilder txBuilder = super.complete();

        txBuilder = buildStakeAddressRegistration(txBuilder);
        txBuilder = buildStakeAddressDeRegistration(txBuilder);
        txBuilder = buildStakeDelegation(txBuilder);

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
        if (stakeDeRegistrationContexts == null || stakeDeRegistrationContexts.size() == 0)
            return txBuilder;

        String changeAddress = getChangeAddress();

        txBuilder = txBuilder.andThen((context, txn) -> {
            if (stakeDeRegistrationContexts == null || stakeDeRegistrationContexts.size() == 0) {
                return;
            }

            //Add stake de-registration certificates
            List<Certificate> certificates = txn.getBody().getCerts();
            if (certificates == null) {
                certificates = new ArrayList<>();
                txn.getBody().setCerts(certificates);
            }

            if (txn.getWitnessSet() == null) {
                txn.setWitnessSet(new TransactionWitnessSet());
            }

            for (StakeKeyDeregestrationContext stakeKeyDeregestrationContext: stakeDeRegistrationContexts) {
                certificates.add(stakeKeyDeregestrationContext.getStakeDeregistration());

                if (stakeKeyDeregestrationContext.refundAddress == null)
                    stakeKeyDeregestrationContext.refundAddress = changeAddress;

                if (stakeKeyDeregestrationContext.redeemer != null) {
                    //Add redeemer to witness set
                    Redeemer redeemer = stakeKeyDeregestrationContext.redeemer;
                    redeemer.setIndex(BigInteger.valueOf(certificates.size() - 1));
                    txn.getWitnessSet().getRedeemers().add(redeemer);
                }

                //Add deposit refund
                txn.getBody().getOutputs()
                        .stream().filter(to -> to.getAddress().equals(stakeKeyDeregestrationContext.refundAddress))
                        .findFirst()
                        .ifPresentOrElse(to -> {
                            //Add deposit amount to the change address
                            to.getValue().setCoin(to.getValue().getCoin().add(STAKE_KEY_REG_DEPOSIT));
                        }, () -> {
                            TransactionOutput transactionOutput = new TransactionOutput(stakeKeyDeregestrationContext.refundAddress,
                                    Value.builder().coin(STAKE_KEY_REG_DEPOSIT).build());
                            txn.getBody().getOutputs().add(transactionOutput);
                        });
            }
        });
        return txBuilder;
    }

    private TxBuilder buildStakeDelegation(TxBuilder txBuilder) {
        if (stakeDelegationContexts == null || stakeDelegationContexts.size() == 0)
            return txBuilder;

        txBuilder = txBuilder.andThen((context, txn) -> {
            if (stakeDelegationContexts == null || stakeDelegationContexts.size() == 0) {
                return;
            }

            //Add stake delegation certificates
            List<Certificate> certificates = txn.getBody().getCerts();
            if (certificates == null) {
                certificates = new ArrayList<>();
                txn.getBody().setCerts(certificates);
            }

            if (txn.getWitnessSet() == null) {
                txn.setWitnessSet(new TransactionWitnessSet());
            }

            for (StakeDelegationContext stakeDelegationContext: stakeDelegationContexts) {
                certificates.add(stakeDelegationContext.getStakeDelegation());

                if (stakeDelegationContext.redeemer != null) {
                    //Add redeemer to witness set
                    Redeemer redeemer = stakeDelegationContext.redeemer;
                    redeemer.setIndex(BigInteger.valueOf(certificates.size() - 1));
                    txn.getWitnessSet().getRedeemers().add(redeemer);
                }
            }
        });
        return txBuilder;
    }

    @Data
    @AllArgsConstructor
    static class StakeKeyDeregestrationContext {
        private StakeDeregistration stakeDeregistration;
        private Redeemer redeemer;
        private String refundAddress;
    }

    @Data
    @AllArgsConstructor
    static class StakeDelegationContext {
        private StakeDelegation stakeDelegation;
        private Redeemer redeemer;
    }
}
