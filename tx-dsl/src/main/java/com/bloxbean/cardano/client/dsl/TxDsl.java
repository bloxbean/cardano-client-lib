package com.bloxbean.cardano.client.dsl;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.dsl.intention.DonationIntention;
import com.bloxbean.cardano.client.dsl.intention.MintingIntention;
import com.bloxbean.cardano.client.dsl.intention.PaymentIntention;
import com.bloxbean.cardano.client.dsl.intention.StakeDelegationIntention;
import com.bloxbean.cardano.client.dsl.intention.StakeDeregistrationIntention;
import com.bloxbean.cardano.client.dsl.intention.StakeRegistrationIntention;
import com.bloxbean.cardano.client.dsl.intention.StakeWithdrawalIntention;
import com.bloxbean.cardano.client.dsl.intention.DRepRegistrationIntention;
import com.bloxbean.cardano.client.dsl.intention.DRepDeregistrationIntention;
import com.bloxbean.cardano.client.dsl.intention.DRepUpdateIntention;
import com.bloxbean.cardano.client.dsl.intention.GovernanceProposalIntention;
import com.bloxbean.cardano.client.dsl.intention.GovernanceVoteIntention;
import com.bloxbean.cardano.client.dsl.intention.VotingDelegationIntention;
import com.bloxbean.cardano.client.dsl.intention.TxIntention;
import com.bloxbean.cardano.client.dsl.intention.IntentionHelper;
import com.bloxbean.cardano.client.dsl.model.TransactionDocument;
import com.bloxbean.cardano.client.dsl.serialization.YamlSerializer;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.spec.Script;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.cert.PoolRegistration;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.transaction.spec.governance.Vote;
import com.bloxbean.cardano.client.transaction.spec.governance.Voter;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovActionId;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import lombok.NonNull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DSL for building Cardano transactions with serialization capabilities.
 * This class uses lazy initialization - it only stores intentions and creates the Tx object
 * when unwrap() is called, allowing for flexible variable resolution.
 */
public class TxDsl {
    private final List<TxIntention> intentions;
    private final Map<String, Object> variables;

    // Transaction attributes (separate from intentions)
    private String fromAddress;
    private String fromWalletKey;
    private String changeAddress;
    private List<TransactionDocument.UtxoInput> collectFromInputs;

    /**
     * Creates a new TxDsl instance.
     */
    public TxDsl() {
        this.intentions = new ArrayList<>();
        this.variables = new HashMap<>();
    }

    /**
     * Private constructor with pre-initialized variables.
     */
    private TxDsl(Map<String, Object> variables) {
        this.intentions = new ArrayList<>();
        this.variables = new HashMap<>(variables);
    }

    /**
     * Creates a new TxDsl instance with the specified variables.
     * Variables will be resolved lazily when unwrap() is called.
     *
     * @param variables the variables to use for substitution
     * @return new TxDsl instance with variables
     */
    public static TxDsl withVariables(Map<String, Object> variables) {
        return new TxDsl(variables);
    }

    /**
     * Creates a new TxDsl instance with a single variable.
     *
     * @param key the variable name
     * @param value the variable value
     * @return new TxDsl instance with the variable
     */
    public static TxDsl withVariables(String key, Object value) {
        return new TxDsl(Map.of(key, value));
    }

    /**
     * Add a variable to this TxDsl instance.
     *
     * @param key the variable name
     * @param value the variable value
     * @return this TxDsl for method chaining
     */
    public TxDsl withVariable(String key, Object value) {
        this.variables.put(key, value);
        return this;
    }

    /**
     * Builds and returns the Tx instance by applying all stored intentions.
     *
     * @return the built Tx instance
     */
    public Tx unwrap() {
        return unwrap(null);
    }

    /**
     * Builds and returns the Tx instance with variable resolution.
     *
     * @param runtimeVariables additional variables to merge with instance variables
     * @return the built Tx instance with resolved variables
     */
    public Tx unwrap(Map<String, Object> runtimeVariables) {
        Tx tx = new Tx();

        // Merge runtime variables with instance variables (runtime takes precedence)
        Map<String, Object> allVariables = new HashMap<>(this.variables);
        if (runtimeVariables != null) {
            allVariables.putAll(runtimeVariables);
        }

        // Apply the from attribute if set
        if (fromAddress != null) {
            String resolvedFrom = IntentionHelper.resolveVariable(fromAddress, allVariables);
            tx.from(resolvedFrom);
        }

        // Apply collect from if set (needs UTXO reconstruction from references)

        // Apply all intentions directly to the Tx object
        for (TxIntention intention : intentions) {
        intention.apply(tx, allVariables);
        }

        return tx;
    }


    /**
     * Pay to an address with a specific amount.
     *
     * @param address the receiver address
     * @param amount the amount to send
     * @return this TxDsl for method chaining
     */
    public TxDsl payToAddress(String address, Amount amount) {
        intentions.add(new PaymentIntention(address, amount));
        return this;
    }

    /**
     * Pay to an address with multiple amounts.
     *
     * @param address the receiver address
     * @param amounts the amounts to send
     * @return this TxDsl for method chaining
     */
    public TxDsl payToAddress(String address, List<Amount> amounts) {
        intentions.add(new PaymentIntention(address, amounts));
        return this;
    }

    /**
     * Pay to an address with multiple amounts and an attached script.
     *
     * @param address the receiver address
     * @param amounts the amounts to send
     * @param script the script to attach as reference
     * @return this TxDsl for method chaining
     */
    public TxDsl payToAddress(String address, List<Amount> amounts, Script script) {
        intentions.add(PaymentIntention.builder()
            .address(address)
            .amounts(amounts)
            .script(script)
            .build());
        return this;
    }

    /**
     * Pay to an address with a single amount and an attached script.


     * @param address the receiver address
     * @param amount the amount to send
     * @param script the script to attach as reference
     * @return this TxDsl for method chaining
     */
    public TxDsl payToAddress(String address, Amount amount, Script script) {
        intentions.add(PaymentIntention.builder()
            .address(address)
            .amounts(List.of(amount))
            .script(script)
            .build());
        return this;
    }

    /**
     * Pay to an address with multiple amounts and attached script reference bytes.


     * @param address the receiver address
     * @param amounts the amounts to send
     * @param scriptRefBytes the script reference bytes to attach
     * @return this TxDsl for method chaining
     */
    public TxDsl payToAddress(String address, List<Amount> amounts, byte[] scriptRefBytes) {
        intentions.add(PaymentIntention.builder()
            .address(address)
            .amounts(amounts)
            .scriptRefBytes(scriptRefBytes)
            .build());
        return this;
    }

    /**
     * Pay to a contract address with a specific amount and datum.


     * @param address the contract address
     * @param amount the amount to send
     * @param datum the datum to attach
     * @return this TxDsl for method chaining
     */
    public TxDsl payToContract(String address, Amount amount, PlutusData datum) {
        intentions.add(PaymentIntention.builder()
            .address(address)
            .amounts(List.of(amount))
            .datumHex(datum.serializeToHex())
            .build());
        return this;
    }

    /**
     * Pay to a contract address with multiple amounts and datum.


     * @param address the contract address
     * @param amounts the amounts to send
     * @param datum the datum to attach
     * @return this TxDsl for method chaining
     */
    public TxDsl payToContract(String address, List<Amount> amounts, PlutusData datum) {
        intentions.add(PaymentIntention.builder()
            .address(address)
            .amounts(amounts)
            .datumHex(datum.serializeToHex())
            .build());
        return this;
    }

    /**
     * Pay to a contract address with a specific amount and datum hash.


     * @param address the contract address
     * @param amount the amount to send
     * @param datumHash the datum hash
     * @return this TxDsl for method chaining
     */
    public TxDsl payToContract(String address, Amount amount, String datumHash) {
        intentions.add(PaymentIntention.builder()
            .address(address)
            .amounts(List.of(amount))
            .datumHash(datumHash)
            .build());
        return this;
    }

    /**
     * Pay to a contract address with multiple amounts and datum hash.


     * @param address the contract address
     * @param amounts the amounts to send
     * @param datumHash the datum hash
     * @return this TxDsl for method chaining
     */
    public TxDsl payToContract(String address, List<Amount> amounts, String datumHash) {
        intentions.add(PaymentIntention.builder()
            .address(address)
            .amounts(amounts)
            .datumHash(datumHash)
            .build());
        return this;
    }

    /**
     * Pay to a contract address with multiple amounts, datum, and reference script.


     * @param address the contract address
     * @param amounts the amounts to send
     * @param datum the datum to attach
     * @param refScript the reference script
     * @return this TxDsl for method chaining
     */
    public TxDsl payToContract(String address, List<Amount> amounts, PlutusData datum, Script refScript) {
        intentions.add(PaymentIntention.builder()
            .address(address)
            .amounts(amounts)
            .datumHex(datum.serializeToHex())
            .refScript(refScript)
            .build());
        return this;
    }

    /**
     * Pay to a contract address with a single amount, datum, and reference script.


     * @param address the contract address
     * @param amount the amount to send
     * @param datum the datum to attach
     * @param refScript the reference script
     * @return this TxDsl for method chaining
     */
    public TxDsl payToContract(String address, Amount amount, PlutusData datum, Script refScript) {
        intentions.add(PaymentIntention.builder()
            .address(address)
            .amounts(List.of(amount))
            .datumHex(datum.serializeToHex())
            .refScript(refScript)
            .build());
        return this;
    }

    /**
     * Pay to a contract address with multiple amounts, datum, and script reference bytes.


     * @param address the contract address
     * @param amounts the amounts to send
     * @param datum the datum to attach
     * @param scriptRefBytes the script reference bytes
     * @return this TxDsl for method chaining
     */
    public TxDsl payToContract(String address, List<Amount> amounts, PlutusData datum, byte[] scriptRefBytes) {
        intentions.add(PaymentIntention.builder()
            .address(address)
            .amounts(amounts)
            .datumHex(datum.serializeToHex())
            .scriptRefBytes(scriptRefBytes)
            .build());
        return this;
    }

    /**
     * Set the sender address for the transaction.


     * @param sender the sender address
     * @return this TxDsl for method chaining
     */
    public TxDsl from(String sender) {
        this.fromAddress = sender;
        return this;
    }

    /**
     * Donate to treasury from the current treasury value.


     * @param currentTreasuryValue the current treasury value
     * @param donationAmount the amount to donate
     * @return this TxDsl for method chaining
     */
    public TxDsl donateToTreasury(BigInteger currentTreasuryValue, BigInteger donationAmount) {
        intentions.add(new DonationIntention(currentTreasuryValue, donationAmount));
        return this;
    }

    /**
     * Collect from specific UTXOs as transaction inputs.


     * @param utxos list of UTXOs to collect from
     * @return this TxDsl for method chaining
     */
    public TxDsl collectFrom(List<Utxo> utxos) {
        // Store UTXO references (only txHash + outputIndex) as attributes
        if (collectFromInputs == null) {
            collectFromInputs = new ArrayList<>();
        }
        for (Utxo utxo : utxos) {
            collectFromInputs.add(new TransactionDocument.UtxoInput(
                utxo.getTxHash(),
                utxo.getOutputIndex()
            ));
        }
        // Note: The actual UTXOs will need to be passed during execution
        // For now we just store the references for serialization
        return this;
    }

    /**
     * Collect from specific UTXOs as transaction inputs.


     * @param utxos set of UTXOs to collect from
     * @return this TxDsl for method chaining
     */
    public TxDsl collectFrom(Set<Utxo> utxos) {
        // Store UTXO references (only txHash + outputIndex) as attributes
        if (collectFromInputs == null) {
            collectFromInputs = new ArrayList<>();
        }
        for (Utxo utxo : utxos) {
            collectFromInputs.add(new TransactionDocument.UtxoInput(
                utxo.getTxHash(),
                utxo.getOutputIndex()
            ));
        }
        // Note: The actual UTXOs will need to be passed during execution
        // For now we just store the references for serialization
        return this;
    }

    /**
     * Mint a single asset using a native script.


     * @param script the native script policy
     * @param asset the asset to mint
     * @return this TxDsl for method chaining
     */
    public TxDsl mintAssets(NativeScript script, Asset asset) {
        intentions.add(new MintingIntention(script, asset));
        return this;
    }

    /**
     * Mint a single asset using a native script to a specific receiver.


     * @param script the native script policy
     * @param asset the asset to mint
     * @param receiver the receiver address
     * @return this TxDsl for method chaining
     */
    public TxDsl mintAssets(NativeScript script, Asset asset, String receiver) {
        intentions.add(new MintingIntention(script, asset, receiver));
        return this;
    }

    /**
     * Mint multiple assets using a native script.


     * @param script the native script policy
     * @param assets the assets to mint
     * @return this TxDsl for method chaining
     */
    public TxDsl mintAssets(NativeScript script, List<Asset> assets) {
        intentions.add(new MintingIntention(script, assets));
        return this;
    }

    /**
     * Mint multiple assets using a native script to a specific receiver.


     * @param script the native script policy
     * @param assets the assets to mint
     * @param receiver the receiver address
     * @return this TxDsl for method chaining
     */
    public TxDsl mintAssets(NativeScript script, List<Asset> assets, String receiver) {
        intentions.add(new MintingIntention(script, assets, receiver));
        return this;
    }

    // ===== STAKING METHODS =====

    /**
     * Register stake address.


     * @param address address to register. Address should have delegation credential. So it should be a base address or stake address.
     * @return this TxDsl for method chaining
     */
    public TxDsl registerStakeAddress(String address) {
        intentions.add(new StakeRegistrationIntention(address));
        return this;
    }

    /**
     * De-register stake address. The key deposit will be refunded to the change address or fee payer if change address is not specified.


     * @param address address to de-register. Address should have delegation credential. So it should be a base address or stake address.
     * @return this TxDsl for method chaining
     */
    public TxDsl deregisterStakeAddress(String address) {
        intentions.add(new StakeDeregistrationIntention(address));
        return this;
    }

    /**
     * De-register stake address with custom refund address.


     * @param address address to de-register. Address should have delegation credential. So it should be a base address or stake address.
     * @param refundAddr address to refund the deposit to
     * @return this TxDsl for method chaining
     */
    public TxDsl deregisterStakeAddress(String address, String refundAddr) {
        intentions.add(new StakeDeregistrationIntention(address, refundAddr));
        return this;
    }

    /**
     * Delegate stake address to a stake pool.


     * @param address stake address to delegate
     * @param poolId stake pool ID (bech32 or hex)
     * @return this TxDsl for method chaining
     */
    public TxDsl delegateTo(String address, String poolId) {
        intentions.add(new StakeDelegationIntention(address, poolId));
        return this;
    }

    /**
     * Withdraw rewards from stake address.


     * @param rewardAddress reward address to withdraw from
     * @param amount amount to withdraw in lovelace
     * @return this TxDsl for method chaining
     */
    public TxDsl withdraw(String rewardAddress, BigInteger amount) {
        intentions.add(new StakeWithdrawalIntention(rewardAddress, amount));
        return this;
    }

    /**
     * Withdraw rewards from stake address with custom receiver.


     * @param rewardAddress reward address to withdraw from
     * @param amount amount to withdraw in lovelace
     * @param receiver address to receive the withdrawn rewards
     * @return this TxDsl for method chaining
     */
    public TxDsl withdraw(String rewardAddress, BigInteger amount, String receiver) {
        intentions.add(new StakeWithdrawalIntention(rewardAddress, amount, receiver));
        return this;
    }

    /**
     * Register stake address using Address.


     * @param address address to register
     * @return this TxDsl for method chaining
     */
    public TxDsl registerStakeAddress(@NonNull Address address) {
        intentions.add(new StakeRegistrationIntention(address.toBech32()));
        return this;
    }

    /**
     * Deregister stake address using Address.


     * @param address address to deregister
     * @return this TxDsl for method chaining
     */
    public TxDsl deregisterStakeAddress(@NonNull Address address) {
        intentions.add(new StakeDeregistrationIntention(address.toBech32()));
        return this;
    }

    /**
     * Deregister stake address using Address with refund address.


     * @param address address to deregister
     * @param refundAddr refund address
     * @return this TxDsl for method chaining
     */
    public TxDsl deregisterStakeAddress(@NonNull Address address, @NonNull String refundAddr) {
        intentions.add(new StakeDeregistrationIntention(address.toBech32(), refundAddr));
        return this;
    }

    /**
     * Delegate stake using Address.


     * @param address address to delegate
     * @param poolId pool ID
     * @return this TxDsl for method chaining
     */
    public TxDsl delegateTo(@NonNull Address address, @NonNull String poolId) {
        intentions.add(new StakeDelegationIntention(address.toBech32(), poolId));
        return this;
    }

    /**
     * Withdraw rewards using Address.


     * @param rewardAddress reward address to withdraw from
     * @param amount amount to withdraw
     * @return this TxDsl for method chaining
     */
    public TxDsl withdraw(@NonNull Address rewardAddress, @NonNull BigInteger amount) {
        intentions.add(new StakeWithdrawalIntention(rewardAddress.toBech32(), amount));
        return this;
    }

    /**
     * Withdraw rewards using Address with custom receiver.


     * @param rewardAddress reward address to withdraw from
     * @param amount amount to withdraw
     * @param receiver receiver address
     * @return this TxDsl for method chaining
     */
    public TxDsl withdraw(@NonNull Address rewardAddress, @NonNull BigInteger amount, String receiver) {
        intentions.add(new StakeWithdrawalIntention(rewardAddress.toBech32(), amount, receiver));
        return this;
    }

    /**
     * Register a stake pool.


     * @param poolRegistration stake pool registration certificate
     * @return this TxDsl for method chaining
     */
    public TxDsl registerPool(@NonNull PoolRegistration poolRegistration) {
        return this;
    }

    /**
     * Update a stake pool.


     * @param poolRegistration pool registration
     * @return this TxDsl for method chaining
     */
    public TxDsl updatePool(@NonNull PoolRegistration poolRegistration) {
        return this;
    }

    /**
     * Retire a stake pool.


     * @param poolId stake pool ID (bech32 or hex)
     * @param epoch epoch to retire the pool
     * @return this TxDsl for method chaining
     */
    public TxDsl retirePool(@NonNull String poolId, int epoch) {
        return this;
    }

    // ===== GOVERNANCE METHODS =====

    /**
     * Register a DRep using Account.


     * @param account Account
     * @param anchor Anchor (optional)
     * @return this TxDsl for method chaining
     */
    public TxDsl registerDRep(@NonNull Account account, Anchor anchor) {
        intentions.add(new DRepRegistrationIntention(account.drepCredential(), anchor));
        return this;
    }

    /**
     * Register a DRep using Account.


     * @param account Account
     * @return this TxDsl for method chaining
     */
    public TxDsl registerDRep(@NonNull Account account) {
        intentions.add(new DRepRegistrationIntention(account.drepCredential()));
        return this;
    }

    /**
     * Register a DRep using Credential.


     * @param drepCredential Credential
     * @param anchor Anchor (optional)
     * @return this TxDsl for method chaining
     */
    public TxDsl registerDRep(@NonNull Credential drepCredential, Anchor anchor) {
        intentions.add(new DRepRegistrationIntention(drepCredential, anchor));
        return this;
    }

    /**
     * Register a DRep using Credential.


     * @param drepCredential Credential
     * @return this TxDsl for method chaining
     */
    public TxDsl registerDRep(@NonNull Credential drepCredential) {
        intentions.add(new DRepRegistrationIntention(drepCredential));
        return this;
    }

    /**
     * Unregister a DRep.


     * @param drepCredential Credential
     * @param refundAddress refund address (optional)
     * @param refundAmount refund amount (optional)
     * @return this TxDsl for method chaining
     */
    public TxDsl unregisterDRep(@NonNull Credential drepCredential, String refundAddress, BigInteger refundAmount) {
        intentions.add(new DRepDeregistrationIntention(drepCredential, refundAddress, refundAmount));
        return this;
    }

    /**
     * Unregister a DRep.


     * @param drepCredential Credential
     * @return this TxDsl for method chaining
     */
    public TxDsl unregisterDRep(@NonNull Credential drepCredential) {
        intentions.add(new DRepDeregistrationIntention(drepCredential));
        return this;
    }

    /**
     * Unregister a DRep with refund address.


     * @param drepCredential Credential
     * @param refundAddress refund address
     * @return this TxDsl for method chaining
     */
    public TxDsl unregisterDRep(@NonNull Credential drepCredential, @NonNull String refundAddress) {
        intentions.add(new DRepDeregistrationIntention(drepCredential, refundAddress));
        return this;
    }

    /**
     * Update a DRep.


     * @param drepCredential Credential
     * @param anchor Anchor (optional)
     * @return this TxDsl for method chaining
     */
    public TxDsl updateDRep(@NonNull Credential drepCredential, Anchor anchor) {
        intentions.add(new DRepUpdateIntention(drepCredential, anchor));
        return this;
    }

    /**
     * Update a DRep.


     * @param drepCredential Credential
     * @return this TxDsl for method chaining
     */
    public TxDsl updateDRep(@NonNull Credential drepCredential) {
        intentions.add(new DRepUpdateIntention(drepCredential));
        return this;
    }

    /**
     * Create a new governance proposal.


     * @param govAction GovAction
     * @param rewardAccount return address for the deposit refund
     * @param anchor Anchor
     * @return this TxDsl for method chaining
     */
    public TxDsl createProposal(@NonNull GovAction govAction, @NonNull String rewardAccount, Anchor anchor) {
        intentions.add(new GovernanceProposalIntention(govAction, rewardAccount, anchor));
        return this;
    }

    /**
     * Create a voting procedure.


     * @param voter Voter
     * @param govActionId GovActionId
     * @param vote Vote
     * @param anchor Anchor
     * @return this TxDsl for method chaining
     */
    public TxDsl createVote(@NonNull Voter voter, @NonNull GovActionId govActionId, @NonNull Vote vote, Anchor anchor) {
        intentions.add(new GovernanceVoteIntention(voter, govActionId, vote, anchor));
        return this;
    }

    /**
     * Create a voting procedure.


     * @param voter Voter
     * @param govActionId GovActionId
     * @param vote Vote
     * @return this TxDsl for method chaining
     */
    public TxDsl createVote(@NonNull Voter voter, @NonNull GovActionId govActionId, @NonNull Vote vote) {
        intentions.add(new GovernanceVoteIntention(voter, govActionId, vote));
        return this;
    }

    /**
     * Delegate voting power to a DRep using String address.


     * @param address Address
     * @param drep DRep
     * @return this TxDsl for method chaining
     */
    public TxDsl delegateVotingPowerTo(@NonNull String address, @NonNull DRep drep) {
        intentions.add(new VotingDelegationIntention(address, drep));
        return this;
    }

    /**
     * Delegate voting power to a DRep using Address.


     * @param address Address
     * @param drep DRep
     * @return this TxDsl for method chaining
     */
    public TxDsl delegateVotingPowerTo(@NonNull Address address, @NonNull DRep drep) {
        intentions.add(new VotingDelegationIntention(address.toBech32(), drep));
        return this;
    }

    /**
     * Get the captured intentions for serialization.

     * @return unmodifiable list of intentions
     */
    public List<TxIntention> getIntentions() {
        return Collections.unmodifiableList(intentions);
    }

    /**
     * Add an intention to the internal list.
     * This is used by composition classes like StakeTxDsl.

     * @param intention the intention to add
     */
    public void addIntention(TxIntention intention) {
        intentions.add(intention);
    }

    /**
     * Serialize this TxDsl to YAML format.

     * @return YAML string representation
     */
    public String toYaml() {
        TransactionDocument doc = new TransactionDocument();
        doc.setVariables(variables);

        // Create transaction entry with attributes and intentions
        TransactionDocument.TxEntry txEntry = new TransactionDocument.TxEntry();
        TransactionDocument.TxContent content = new TransactionDocument.TxContent();

        // Set attributes
        content.setFrom(fromAddress);
        content.setFromWallet(fromWalletKey);
        content.setChangeAddress(changeAddress);
        content.setCollectFrom(collectFromInputs);

        // Set intentions
        content.setIntentions(intentions);

        txEntry.setTx(content);
        doc.getTransaction().add(txEntry);

        return YamlSerializer.serialize(doc);
    }

    /**
     * Create a TxDsl from YAML string.

     * @param yaml the YAML string
     * @return reconstructed TxDsl
     */
    public static TxDsl fromYaml(String yaml) {
        TransactionDocument doc = YamlSerializer.deserialize(yaml);

        // Restore from the first transaction entry (MVP: support single tx)
        if (doc.getTransaction() != null && !doc.getTransaction().isEmpty()) {
            TransactionDocument.TxEntry firstTx = doc.getTransaction().get(0);
            return fromTransactionEntry(firstTx, doc.getVariables());
        }

        // Return empty TxDsl if no transactions
        TxDsl txDsl = new TxDsl();
        if (doc.getVariables() != null) {
            txDsl.variables.putAll(doc.getVariables());
        }
        return txDsl;
    }

    /**
     * Create a TxDsl from a TransactionDocument.TxEntry.
     * This method centralizes the logic for converting transaction entries to TxDsl instances.
     *
     * @param txEntry the transaction entry from the document
     * @param variables optional variables to include in the TxDsl
     * @return TxDsl instance with restored state
     */
    public static TxDsl fromTransactionEntry(TransactionDocument.TxEntry txEntry, Map<String, Object> variables) {
        TxDsl txDsl = new TxDsl();

        // Set variables if provided
        if (variables != null) {
            txDsl.variables.putAll(variables);
        }

        // Process the transaction content
        TransactionDocument.TxContent content = txEntry.getTx();
        if (content != null) {
            // Restore attributes
            if (content.getFrom() != null) {
                txDsl.from(content.getFrom());
            }
            if (content.getFromWallet() != null) {
                txDsl.fromWalletKey = content.getFromWallet();
            }
            if (content.getChangeAddress() != null) {
                txDsl.changeAddress = content.getChangeAddress();
            }
            if (content.getCollectFrom() != null) {
                txDsl.collectFromInputs = content.getCollectFrom();
            }
            // Add intentions to the TxDsl (instead of applying them immediately)
            if (content.getIntentions() != null) {
                txDsl.intentions.addAll(content.getIntentions());
            }
        }

        return txDsl;
    }


    /**
     * Get the variables map for external access.

     * @return unmodifiable view of variables map
     */
    public Map<String, Object> getVariables() {
        return Collections.unmodifiableMap(variables);
    }
}
