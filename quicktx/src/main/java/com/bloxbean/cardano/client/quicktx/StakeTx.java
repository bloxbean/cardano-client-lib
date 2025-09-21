package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressType;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.quicktx.intent.*;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.spec.Withdrawal;
import com.bloxbean.cardano.client.transaction.spec.cert.*;
import com.bloxbean.cardano.client.util.Tuple;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Class for Stake delegation specific transactions
 */
@Slf4j
class StakeTx {
    protected List<StakeRegistration> stakeRegistrations;
    protected List<StakeKeyDeregestrationContext> stakeDeRegistrationContexts;
    protected List<StakeDelegationContext> stakeDelegationContexts;
    protected List<WithdrawalContext> withdrawalContexts;
    protected List<PoolRegistrationContext> poolRegistrationContexts;
    protected List<PoolRetirement> poolRetirements;

    // Intention recording support
    protected List<TxIntent> intentions;
    protected boolean intentionRecordingEnabled = false;

    public StakeTx() {
    }

    /**
     * Enable intention recording for YAML serialization support.
     */
    public void enableIntentionRecording() {
        this.intentionRecordingEnabled = true;
        if (this.intentions == null) {
            this.intentions = new ArrayList<>();
        }
    }

    /**
     * Get recorded intentions for YAML serialization.
     */
    public List<TxIntent> getIntentions() {
        return intentions;
    }

    /**
     * Register stake address
     *
     * @param address address to register. Address should have delegation credential. So it should be a base address or stake address.
     * @return T
     */
    public StakeTx registerStakeAddress(@NonNull Address address) {
//        byte[] delegationHash = address.getDelegationCredentialHash()
//                .orElseThrow(() -> new TxBuildException("Invalid stake address. Address does not have delegation credential"));
//
//        StakeCredential stakeCredential = null;
//        if (address.isStakeKeyHashInDelegationPart())
//            stakeCredential = StakeCredential.fromKeyHash(delegationHash);
//        else if (address.isScriptHashInDelegationPart())
//            stakeCredential = StakeCredential.fromScriptHash(delegationHash);
//
//        if (stakeRegistrations == null)
//            stakeRegistrations = new ArrayList<>();
//
//        //-- Stake key registration
//        StakeRegistration stakeRegistration = new StakeRegistration(stakeCredential);
//        stakeRegistrations.add(stakeRegistration);

        // Record intention for YAML serialization if enabled

        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeRegistrationIntent.register(address.toBech32()));


        return this;
    }

    /**
     * De-register stake address. The key deposit will be refunded to the refund address.
     *
     * @param address    address to de-register. Address should have delegation credential. So it should be a base address or stake address.
     * @param redeemer   redeemer to use if the address is a script address
     * @param refundAddr refund address
     * @return T
     */
    public StakeTx deregisterStakeAddress(@NonNull Address address, PlutusData redeemer, String refundAddr) {
        byte[] delegationHash = address.getDelegationCredentialHash()
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

        // Record intention for YAML serialization if enabled
        if (intentionRecordingEnabled) {
            if (intentions == null) intentions = new ArrayList<>();
            if (refundAddr != null) {
                intentions.add(StakeDeregistrationIntent.deregister(address.toBech32(), refundAddr));
            } else {
                intentions.add(StakeDeregistrationIntent.deregister(address.toBech32()));
            }
        }

        return this;
    }

    /**
     * Delegate stake address to a stake pool
     *
     * @param address  address to delegate. Address should have delegation credential. So it should be a base address or stake address.
     * @param poolId   stake pool id Bech32 or hex encoded
     * @param redeemer redeemer to use if the address is a script address
     * @return T
     */
    public StakeTx delegateTo(@NonNull Address address, @NonNull String poolId, PlutusData redeemer) {
        byte[] delegationHash = address.getDelegationCredentialHash()
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

        // Record intention for YAML serialization if enabled
        if (intentionRecordingEnabled) {
            if (intentions == null) intentions = new ArrayList<>();
            intentions.add(StakeDelegationIntent.delegateTo(address.toBech32(), poolId));
        }

        return this;
    }

    /**
     * Withdraw rewards from a reward address
     *
     * @param address  reward address
     * @param amount   amount to withdraw
     * @param redeemer redeemer to use if the address is a script address
     * @param receiver receiver address
     * @return StakeTx
     */
    public StakeTx withdraw(@NonNull Address address, @NonNull BigInteger amount, PlutusData redeemer, String receiver) {
        if (AddressType.Reward != address.getAddressType())
            throw new TxBuildException("Invalid address type. Only reward address can be used for withdrawal");

        if (withdrawalContexts == null)
            withdrawalContexts = new ArrayList<>();

        Redeemer _redeemer = null;
        if (redeemer != null) {
            _redeemer = Redeemer.builder()
                    .tag(RedeemerTag.Reward)
                    .data(redeemer)
                    .index(BigInteger.valueOf(1)) //dummy value
                    .exUnits(ExUnits.builder()
                            .mem(BigInteger.valueOf(10000)) // Some dummy value
                            .steps(BigInteger.valueOf(1000))
                            .build())
                    .build();
        }

        withdrawalContexts.add(new WithdrawalContext(new Withdrawal(address.toBech32(), amount), _redeemer, receiver));

        // Record intention for YAML serialization if enabled
        if (intentionRecordingEnabled) {
            if (intentions == null) intentions = new ArrayList<>();
            if (receiver != null) {
                intentions.add(StakeWithdrawalIntent.withdraw(address.toBech32(), amount, receiver));
            } else {
                intentions.add(StakeWithdrawalIntent.withdraw(address.toBech32(), amount));
            }
        }

        return this;
    }

    /**
     * Register stake pool
     *
     * @param poolRegistration pool registration certificate
     * @return StakeTx
     */
    public StakeTx registerPool(@NonNull PoolRegistration poolRegistration) {
        if (poolRegistrationContexts == null)
            poolRegistrationContexts = new ArrayList<>();

        poolRegistrationContexts.add(new PoolRegistrationContext(poolRegistration, false));

        // Record intention for YAML serialization if enabled
        if (intentionRecordingEnabled) {
            if (intentions == null) intentions = new ArrayList<>();
            intentions.add(PoolRegistrationIntent.register(poolRegistration));
        }

        return this;
    }

    /**
     * Update stake pool
     *
     * @param poolRegistration pool registration certificate
     * @return StakeTx
     */
    public StakeTx updatePool(@NonNull PoolRegistration poolRegistration) {
        if (poolRegistrationContexts == null)
            poolRegistrationContexts = new ArrayList<>();

        poolRegistrationContexts.add(new PoolRegistrationContext(poolRegistration, true));

        // Record intention for YAML serialization if enabled
        if (intentionRecordingEnabled) {
            if (intentions == null) intentions = new ArrayList<>();
            intentions.add(PoolRegistrationIntent.update(poolRegistration));
        }

        return this;
    }

    /**
     * Retire a stake pool
     * @param poolId Pool id Bech32 or hex encoded pool key hash
     * @param epoch Epoch to retire the pool
     * @return StakeTx
     */
    public StakeTx retirePool(@NonNull String poolId, int epoch) {
        if (epoch <= 0)
            throw new TxBuildException("Invalid epoch. Epoch should be greater than current epoch");

        if (poolRetirements == null)
            poolRetirements = new ArrayList<>();

        byte[] poolKeyHash;
        if (poolId.startsWith("pool")) {
            poolKeyHash = StakePoolId.fromBech32PoolId(poolId).getPoolKeyHash();
        } else {
            poolKeyHash = StakePoolId.fromHexPoolId(poolId).getPoolKeyHash();
        }

        poolRetirements.add(new PoolRetirement(poolKeyHash, epoch));

        // Record intention for YAML serialization if enabled
        if (intentionRecordingEnabled) {
            if (intentions == null) intentions = new ArrayList<>();
            intentions.add(PoolRetirementIntent.retire(poolId, epoch));
        }

        return this;
    }

    /**
     * Return TxBuilder, payments to build a stake transaction
     *
     * @param fromAddress
     * @return Tuple<List<PaymentContext>, TxBuilder>
     */
    Tuple<List<DepositRefundContext>, TxBuilder> build(String fromAddress, String changeAddress) {
        List<DepositRefundContext> paymentContexts = buildStakePayments(fromAddress, changeAddress);

        TxBuilder txBuilder = (context, txn) -> {
        };
        txBuilder = buildStakeAddressRegistration(txBuilder, fromAddress);
        txBuilder = buildStakeAddressDeRegistration(txBuilder, changeAddress);
        txBuilder = buildStakeDelegation(txBuilder);
        txBuilder = buildWithdrawal(txBuilder, changeAddress);
        txBuilder = buildPoolRegistration(txBuilder, fromAddress);
        txBuilder = buildPoolRetirement(txBuilder);

        return new Tuple<>(paymentContexts, txBuilder);
    }

    private List<DepositRefundContext> buildStakePayments(String fromAddress, String changeAddress) {
        List<DepositRefundContext> depositRefundContexts = new ArrayList<>();
        if ((stakeRegistrations == null || stakeRegistrations.size() == 0)
                && (stakeDeRegistrationContexts == null || stakeDeRegistrationContexts.size() == 0)
                && (stakeDelegationContexts == null || stakeDelegationContexts.size() == 0)
                && (withdrawalContexts == null || withdrawalContexts.size() == 0)
                && (poolRegistrationContexts == null || poolRegistrationContexts.size() == 0)
                && (poolRetirements == null || poolRetirements.size() == 0)) {
            return depositRefundContexts;
        }

        if (stakeRegistrations != null && stakeRegistrations.size() > 0) {
            depositRefundContexts.add(new DepositRefundContext(fromAddress, DepositRefundType.STAKE_KEY_REGISTRATION, stakeRegistrations.size()));
        }

        if (stakeDeRegistrationContexts != null && stakeDeRegistrationContexts.size() > 0) {
            //Dummy output to sender fromAddress to trigger input selection
            depositRefundContexts.add(new DepositRefundContext(fromAddress, DepositRefundType.STAKE_KEY_DEREGISTRATION));
        }

        if (stakeDelegationContexts != null && stakeDelegationContexts.size() > 0) {
            depositRefundContexts.add(new DepositRefundContext(fromAddress, DepositRefundType.DELGATION));
        }

        if (withdrawalContexts != null && withdrawalContexts.size() > 0) {
            //Dummy output to sender fromAddress to trigger input selection
            depositRefundContexts.add(new DepositRefundContext(fromAddress, DepositRefundType.WITHDRAWAL));
        }

        if (poolRegistrationContexts != null && poolRegistrationContexts.size() > 0) {
            List<PoolRegistration> poolRegistrations = poolRegistrationContexts.stream()
                    .filter(poolRegistrationContext -> !poolRegistrationContext.isUpdate())
                    .map(PoolRegistrationContext::getPoolRegistration)
                    .collect(Collectors.toList());

            if (poolRegistrations.size() > 0) {
                depositRefundContexts.add(new DepositRefundContext(fromAddress, DepositRefundType.POOL_REGISTRATION, poolRegistrations.size()));
            }
        }

        if (poolRetirements != null && poolRetirements.size() > 0) {
            //Dummy output to sender fromAddress to trigger input selection
            depositRefundContexts.add(new DepositRefundContext(fromAddress, DepositRefundType.POOL_RETIREMENT));
        }

        return depositRefundContexts;
    }

    private TxBuilder buildStakeAddressRegistration(TxBuilder txBuilder, String fromAddress) {
        if (stakeRegistrations == null || stakeRegistrations.size() == 0)
            return txBuilder;

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

    private TxBuilder buildStakeAddressDeRegistration(TxBuilder txBuilder, String fromAddress) {
        if (stakeDeRegistrationContexts == null || stakeDeRegistrationContexts.size() == 0)
            return txBuilder;

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

            var stakeKeyRegDeposit = new BigInteger(context.getProtocolParams().getKeyDeposit());

            for (StakeKeyDeregestrationContext stakeKeyDeregestrationContext : stakeDeRegistrationContexts) {
                certificates.add(stakeKeyDeregestrationContext.getStakeDeregistration());

                if (stakeKeyDeregestrationContext.refundAddress == null)
                    stakeKeyDeregestrationContext.refundAddress = fromAddress;

                if (stakeKeyDeregestrationContext.redeemer != null) {
                    //Add redeemer to witness set
                    Redeemer redeemer = stakeKeyDeregestrationContext.redeemer;
                    redeemer.setIndex(certificates.size() - 1);
                    txn.getWitnessSet().getRedeemers().add(redeemer);
                }

                //Add deposit refund
                txn.getBody().getOutputs()
                        .stream().filter(to -> to.getAddress().equals(stakeKeyDeregestrationContext.refundAddress))
                        .findFirst()
                        .ifPresentOrElse(to -> {
                            //Add deposit amount to the change address
                            to.getValue().setCoin(to.getValue().getCoin().add(stakeKeyRegDeposit));
                        }, () -> {
                            TransactionOutput transactionOutput = new TransactionOutput(stakeKeyDeregestrationContext.refundAddress,
                                    Value.builder().coin(stakeKeyRegDeposit).build());
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

            for (StakeDelegationContext stakeDelegationContext : stakeDelegationContexts) {
                certificates.add(stakeDelegationContext.getStakeDelegation());

                if (stakeDelegationContext.redeemer != null) {
                    //Add redeemer to witness set
                    Redeemer redeemer = stakeDelegationContext.redeemer;
                    redeemer.setIndex(certificates.size() - 1);
                    txn.getWitnessSet().getRedeemers().add(redeemer);
                }
            }
        });
        return txBuilder;
    }

    private TxBuilder buildWithdrawal(TxBuilder txBuilder, String changeAddress) {
        if (withdrawalContexts == null || withdrawalContexts.size() == 0)
            return txBuilder;

        txBuilder = txBuilder.andThen((context, txn) -> {
            if (withdrawalContexts == null || withdrawalContexts.size() == 0) {
                return;
            }

            if (txn.getBody().getWithdrawals() == null || txn.getBody().getWithdrawals().isEmpty())
                txn.getBody().setWithdrawals(new ArrayList<>());

            for (WithdrawalContext withdrawalContext : withdrawalContexts) {
                txn.getBody().getWithdrawals().add(withdrawalContext.getWithdrawal());
                if (withdrawalContext.receiver == null)
                    withdrawalContext.receiver = changeAddress;

                if (withdrawalContext.redeemer != null) {
                    //Add redeemer to witness set
                    Redeemer redeemer = withdrawalContext.redeemer;
                    redeemer.setIndex(txn.getBody().getWithdrawals().size() - 1);
                    txn.getWitnessSet().getRedeemers().add(redeemer);
                }

                //Add withdrawal amount
                txn.getBody().getOutputs()
                        .stream().filter(to -> to.getAddress().equals(withdrawalContext.receiver))
                        .findFirst()
                        .ifPresentOrElse(to -> {
                            //Add withdrawal amount to the withdrawal receiver address
                            to.getValue().setCoin(to.getValue().getCoin().add(withdrawalContext.withdrawal.getCoin()));
                        }, () -> {
                            TransactionOutput transactionOutput = new TransactionOutput(withdrawalContext.receiver,
                                    Value.builder().coin(withdrawalContext.withdrawal.getCoin()).build());
                            txn.getBody().getOutputs().add(transactionOutput);
                        });
            }
        });
        return txBuilder;
    }

    private TxBuilder buildPoolRegistration(TxBuilder txBuilder, String fromAddress) {
        if (poolRegistrationContexts == null || poolRegistrationContexts.isEmpty())
            return txBuilder;

        txBuilder = txBuilder.andThen((context, txn) -> {
            if (poolRegistrationContexts == null || poolRegistrationContexts.isEmpty()) {
                return;
            }

            //Add pool registration certificate
            List<Certificate> certificates = txn.getBody().getCerts();
            if (certificates == null) {
                certificates = new ArrayList<>();
                txn.getBody().setCerts(certificates);
            }

            List<PoolRegistration> poolRegistrations = poolRegistrationContexts.stream()
                    .filter(poolRegistrationContext -> !poolRegistrationContext.isUpdate())
                    .map(PoolRegistrationContext::getPoolRegistration)
                    .collect(Collectors.toList());

            List<PoolRegistration> poolUpdates = poolRegistrationContexts.stream()
                    .filter(PoolRegistrationContext::isUpdate)
                    .map(PoolRegistrationContext::getPoolRegistration)
                    .collect(Collectors.toList());

            if (poolRegistrations.size() > 0)
                certificates.addAll(poolRegistrations);

            if (poolUpdates.size() > 0)
                certificates.addAll(poolUpdates);

            String poolDeposit = context.getProtocolParams().getPoolDeposit();
            BigInteger poolRegDeposit = new BigInteger(poolDeposit);
            BigInteger totalPoolRegistrationDeposit = poolRegDeposit.multiply(BigInteger.valueOf(poolRegistrations.size()));
            log.debug("Total pool registration deposit: " + totalPoolRegistrationDeposit);

            if (poolRegistrations.size() > 0) {
                txn.getBody().getOutputs()
                        .stream().filter(to -> to.getAddress().equals(fromAddress)
                                && to.getValue().getCoin().compareTo(totalPoolRegistrationDeposit) >= 0)
                        .sorted((o1, o2) -> o2.getValue().getCoin().compareTo(o1.getValue().getCoin()))
                        .findFirst()
                        .ifPresentOrElse(to -> {
                            //Remove the deposit amount from the from address output
                            to.getValue().setCoin(to.getValue().getCoin().subtract(totalPoolRegistrationDeposit));

                            if (to.getValue().getCoin().equals(BigInteger.ZERO)
                                    && to.getValue().getMultiAssets() == null
                                    && to.getValue().getMultiAssets().size() == 0) {
                                txn.getBody().getOutputs().remove(to);
                            }
                        }, () -> {
                            throw new TxBuildException("Output for from address not found to remove deposit amount: " + fromAddress);
                        });
            }
        });
        return txBuilder;
    }

    private TxBuilder buildPoolRetirement(TxBuilder txBuilder) {
        if (poolRetirements == null || poolRetirements.size() == 0)
            return txBuilder;

        txBuilder = txBuilder.andThen((context, txn) -> {
            if (poolRetirements == null || poolRetirements.size() == 0) {
                return;
            }

            //Add pool retirement certificates
            List<Certificate> certificates = txn.getBody().getCerts();
            if (certificates == null) {
                certificates = new ArrayList<>();
                txn.getBody().setCerts(certificates);
            }

            txn.getBody().getCerts().addAll(poolRetirements);
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

    @Data
    @AllArgsConstructor
    static class WithdrawalContext {
        private Withdrawal withdrawal;
        private Redeemer redeemer;
        private String receiver;
    }

    @Data
    @AllArgsConstructor
    static class PoolRegistrationContext {
        private PoolRegistration poolRegistration;
        private boolean update;
    }

    @Data
    @AllArgsConstructor
    static class PaymentContext {
        private String address;
        private Amount amount;
    }
}
