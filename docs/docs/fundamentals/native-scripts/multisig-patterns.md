---
description: Complete guide to multi-signature patterns, treasury management, escrow scenarios, and real-world implementations using native scripts
sidebar_label: Multi-signature Patterns
sidebar_position: 2
---

# Multi-signature Patterns and Examples

Multi-signature (multi-sig) wallets are one of the most practical applications of native scripts, enabling shared control over funds while maintaining security and flexibility. This guide covers common patterns, real-world implementations, and best practices for multi-signature solutions.

## Why Multi-signature?

Multi-signature wallets provide enhanced security and shared control by requiring multiple signatures for transactions:

- **Enhanced Security** - No single point of failure
- **Shared Control** - Multiple parties must agree on transactions
- **Flexible Governance** - Implement various approval mechanisms
- **Risk Mitigation** - Protection against key loss or compromise
- **Compliance** - Meet regulatory requirements for fund management

## Basic Multi-signature Patterns

### 2-of-2 Multi-signature (Joint Account)

Requires both parties to sign every transaction.

```java
import com.bloxbean.cardano.client.transaction.spec.script.*;
import com.bloxbean.cardano.client.address.AddressProvider;

public class JointAccount {
    
    public static class JointAccountResult {
        private final ScriptAll script;
        private final Keys aliceKeys;
        private final Keys bobKeys;
        private final Address accountAddress;
        
        // Constructor and getters...
    }
    
    public JointAccountResult createJointAccount(Network network) {
        // Generate key pairs for both parties
        ScriptPubkey aliceScript = ScriptPubkey.createWithNewKey();
        ScriptPubkey bobScript = ScriptPubkey.createWithNewKey();
        
        // Require both signatures
        ScriptAll jointScript = new ScriptAll()
            .addScript(aliceScript)
            .addScript(bobScript);
        
        // Generate address for the joint account
        Address accountAddress = AddressProvider.getEntAddress(jointScript, network);
        
        return new JointAccountResult(
            jointScript,
            aliceScript.getKeys(),
            bobScript.getKeys(),
            accountAddress
        );
    }
    
    public Result<String> spendFromJointAccount(
            JointAccountResult account,
            String recipient,
            Amount amount) {
        
        // Both parties must sign
        List<TransactionSigner> signers = Arrays.asList(
            SignerProviders.signerFrom(account.getAliceKeys()),
            SignerProviders.signerFrom(account.getBobKeys())
        );
        
        Tx tx = new Tx()
            .payToAddress(recipient, amount)
            .from(account.getAccountAddress().getAddress())
            .attachSpendingScript(account.getScript());
        
        return new QuickTxBuilder(backendService)
            .compose(tx)
            .withSigners(signers)
            .completeAndWait();
    }
}
```

### 2-of-3 Multi-signature (Majority Control)

Requires any 2 signatures from 3 possible signers.

```java
public class MajorityWallet {
    
    public static class MultiSigWallet {
        private final ScriptAtLeast script;
        private final List<Keys> allKeys;
        private final Address walletAddress;
        private final String policyId;
        
        // Constructor and getters...
    }
    
    public MultiSigWallet create2of3Wallet(Network network) {
        // Generate three key pairs
        ScriptPubkey key1 = ScriptPubkey.createWithNewKey();
        ScriptPubkey key2 = ScriptPubkey.createWithNewKey();
        ScriptPubkey key3 = ScriptPubkey.createWithNewKey();
        
        // Require any 2 of the 3 signatures
        ScriptAtLeast multiSigScript = new ScriptAtLeast(2)
            .addScript(key1)
            .addScript(key2)
            .addScript(key3);
        
        // Generate wallet address
        Address walletAddress = AddressProvider.getEntAddress(multiSigScript, network);
        
        // Collect all keys
        List<Keys> allKeys = Arrays.asList(
            key1.getKeys(),
            key2.getKeys(),
            key3.getKeys()
        );
        
        return new MultiSigWallet(multiSigScript, allKeys, walletAddress, multiSigScript.getPolicyId());
    }
    
    public Result<String> spendWithMajority(
            MultiSigWallet wallet,
            List<Keys> signingKeys,
            String recipient,
            Amount amount) {
        
        if (signingKeys.size() < 2) {
            return Result.error("Need at least 2 signatures for 2-of-3 wallet");
        }
        
        // Create signers from provided keys
        List<TransactionSigner> signers = signingKeys.stream()
            .limit(2) // Only need 2 signatures
            .map(SignerProviders::signerFrom)
            .collect(Collectors.toList());
        
        Tx tx = new Tx()
            .payToAddress(recipient, amount)
            .from(wallet.getWalletAddress().getAddress())
            .attachSpendingScript(wallet.getScript());
        
        return new QuickTxBuilder(backendService)
            .compose(tx)
            .withSigners(signers)
            .completeAndWait();
    }
    
    public void demonstrateKeyRotation(MultiSigWallet wallet) {
        // In a 2-of-3 setup, if one key is compromised:
        // 1. Use the other 2 keys to move funds to a new wallet
        // 2. Generate a new 2-of-3 wallet with fresh keys
        
        MultiSigWallet newWallet = create2of3Wallet(Networks.mainnet());
        
        // Move all funds to new wallet (using 2 good keys)
        List<Keys> goodKeys = wallet.getAllKeys().subList(0, 2); // First 2 keys
        
        Result<String> migration = spendWithMajority(
            wallet,
            goodKeys,
            newWallet.getWalletAddress().getAddress(),
            getAllFunds(wallet.getWalletAddress())
        );
        
        System.out.println("Migration result: " + migration.isSuccessful());
    }
}
```

### 3-of-5 Corporate Multi-signature

Higher threshold for larger organizations.

```java
public class CorporateMultiSig {
    
    public static class CorporateWallet {
        private final ScriptAtLeast script;
        private final Map<String, Keys> namedKeys;
        private final Address walletAddress;
        
        // Constructor and getters...
    }
    
    public CorporateWallet createCorporateWallet(Network network) {
        // Named key pairs for organization members
        Map<String, ScriptPubkey> namedScripts = new HashMap<>();
        namedScripts.put("CEO", ScriptPubkey.createWithNewKey());
        namedScripts.put("CFO", ScriptPubkey.createWithNewKey());
        namedScripts.put("CTO", ScriptPubkey.createWithNewKey());
        namedScripts.put("COO", ScriptPubkey.createWithNewKey());
        namedScripts.put("Board_Chair", ScriptPubkey.createWithNewKey());
        
        // Require 3 of 5 signatures
        ScriptAtLeast corporateScript = new ScriptAtLeast(3);
        Map<String, Keys> namedKeys = new HashMap<>();
        
        for (Map.Entry<String, ScriptPubkey> entry : namedScripts.entrySet()) {
            corporateScript.addScript(entry.getValue());
            namedKeys.put(entry.getKey(), entry.getValue().getKeys());
        }
        
        Address walletAddress = AddressProvider.getEntAddress(corporateScript, network);
        
        return new CorporateWallet(corporateScript, namedKeys, walletAddress);
    }
    
    public Result<String> executeCorporateTransaction(
            CorporateWallet wallet,
            List<String> signerRoles,
            String recipient,
            Amount amount,
            String purpose) {
        
        if (signerRoles.size() < 3) {
            return Result.error("Need at least 3 corporate signatures");
        }
        
        // Validate all signers exist
        List<Keys> signingKeys = new ArrayList<>();
        for (String role : signerRoles) {
            Keys keys = wallet.getNamedKeys().get(role);
            if (keys == null) {
                return Result.error("Unknown signer role: " + role);
            }
            signingKeys.add(keys);
        }
        
        // Create transaction with metadata for audit trail
        Tx tx = new Tx()
            .payToAddress(recipient, amount)
            .from(wallet.getWalletAddress().getAddress())
            .attachMetadata(MessageMetadata.create()
                .add("purpose", purpose)
                .add("signers", String.join(",", signerRoles))
                .add("timestamp", Instant.now().toString()))
            .attachSpendingScript(wallet.getScript());
        
        List<TransactionSigner> signers = signingKeys.stream()
            .map(SignerProviders::signerFrom)
            .collect(Collectors.toList());
        
        return new QuickTxBuilder(backendService)
            .compose(tx)
            .withSigners(signers)
            .completeAndWait();
    }
}
```

## Time-Locked Multi-signature Patterns

### Emergency Recovery with Time Delay

Allows emergency access after a time delay.

```java
public class EmergencyRecoveryWallet {
    
    public NativeScript createEmergencyWallet() {
        // Regular signers (3 of 5)
        ScriptAtLeast regularApproval = new ScriptAtLeast(3)
            .addScript(ScriptPubkey.createWithNewKey()) // CEO
            .addScript(ScriptPubkey.createWithNewKey()) // CFO
            .addScript(ScriptPubkey.createWithNewKey()) // CTO
            .addScript(ScriptPubkey.createWithNewKey()) // COO
            .addScript(ScriptPubkey.createWithNewKey()); // Legal
        
        // Emergency keys (2 of 2) with time delay
        ScriptPubkey emergencyKey1 = ScriptPubkey.createWithNewKey();
        ScriptPubkey emergencyKey2 = ScriptPubkey.createWithNewKey();
        
        long emergencyDelay = getCurrentSlot() + (7 * 24 * 60 * 60); // 7 days
        
        ScriptAll emergencyAccess = new ScriptAll()
            .addScript(new RequireTimeAfter(emergencyDelay))
            .addScript(emergencyKey1)
            .addScript(emergencyKey2);
        
        // Either regular approval OR emergency access
        return new ScriptAny()
            .addScript(regularApproval)
            .addScript(emergencyAccess);
    }
    
    public void demonstrateEmergencyActivation() {
        // Emergency scenario: Need to access funds but can't get 3 signatures
        // Solution: Wait for time delay, then use emergency keys
        
        long currentSlot = getCurrentSlot();
        long emergencySlot = currentSlot + (7 * 24 * 60 * 60);
        
        System.out.println("Emergency access available after slot: " + emergencySlot);
        System.out.println("Current slot: " + currentSlot);
        System.out.println("Time remaining: " + (emergencySlot - currentSlot) + " slots");
    }
}
```

### Vesting with Multi-signature Release

Multi-signature control over vesting schedule.

```java
public class VestingMultiSig {
    
    public List<NativeScript> createVestingWallets(
            ScriptPubkey beneficiary,
            List<ScriptPubkey> trustees,
            long startSlot,
            int quarters) {
        
        List<NativeScript> vestingWallets = new ArrayList<>();
        long quarterlySlots = 90 * 24 * 60 * 60; // 90 days
        
        // Require majority of trustees (2 of 3) for each release
        ScriptAtLeast trusteeApproval = new ScriptAtLeast(2);
        trustees.forEach(trusteeApproval::addScript);
        
        for (int i = 0; i < quarters; i++) {
            long vestingSlot = startSlot + (i * quarterlySlots);
            
            // Require: beneficiary + trustees + time
            ScriptAll quarterlyVesting = new ScriptAll()
                .addScript(beneficiary)
                .addScript(trusteeApproval)
                .addScript(new RequireTimeAfter(vestingSlot));
            
            vestingWallets.add(quarterlyVesting);
        }
        
        return vestingWallets;
    }
    
    public Result<String> releaseVestedFunds(
            NativeScript vestingScript,
            Keys beneficiaryKeys,
            List<Keys> trusteeKeys,
            String recipient,
            Amount amount) {
        
        if (trusteeKeys.size() < 2) {
            return Result.error("Need at least 2 trustee signatures");
        }
        
        // Create signers
        List<TransactionSigner> signers = new ArrayList<>();
        signers.add(SignerProviders.signerFrom(beneficiaryKeys));
        trusteeKeys.stream()
            .limit(2) // Only need 2 of 3 trustees
            .map(SignerProviders::signerFrom)
            .forEach(signers::add);
        
        Tx tx = new Tx()
            .payToAddress(recipient, amount)
            .from(getScriptAddress(vestingScript))
            .attachSpendingScript(vestingScript)
            .attachMetadata(MessageMetadata.create()
                .add("type", "vesting_release")
                .add("quarter", getCurrentQuarter()));
        
        return new QuickTxBuilder(backendService)
            .compose(tx)
            .withSigners(signers)
            .completeAndWait();
    }
}
```

## Escrow Scenarios

### Simple Escrow (Buyer + Seller)

Basic escrow requiring both parties to agree.

```java
public class SimpleEscrow {
    
    public static class EscrowContract {
        private final ScriptAny script;
        private final Keys buyerKeys;
        private final Keys sellerKeys;
        private final Keys arbitratorKeys;
        private final Address escrowAddress;
        private final long timeoutSlot;
        
        // Constructor and getters...
    }
    
    public EscrowContract createEscrow(Network network, long timeoutHours) {
        ScriptPubkey buyer = ScriptPubkey.createWithNewKey();
        ScriptPubkey seller = ScriptPubkey.createWithNewKey();
        ScriptPubkey arbitrator = ScriptPubkey.createWithNewKey();
        
        long timeoutSlot = getCurrentSlot() + (timeoutHours * 60 * 60);
        
        // Escrow conditions:
        // 1. Both buyer and seller agree, OR
        // 2. Arbitrator decides after timeout
        ScriptAny escrowScript = new ScriptAny()
            .addScript(new ScriptAll()
                .addScript(buyer)
                .addScript(seller))
            .addScript(new ScriptAll()
                .addScript(arbitrator)
                .addScript(new RequireTimeAfter(timeoutSlot)));
        
        Address escrowAddress = AddressProvider.getEntAddress(escrowScript, network);
        
        return new EscrowContract(
            escrowScript,
            buyer.getKeys(),
            seller.getKeys(),
            arbitrator.getKeys(),
            escrowAddress,
            timeoutSlot
        );
    }
    
    public Result<String> mutualRelease(
            EscrowContract escrow,
            String recipient,
            Amount amount) {
        
        // Both buyer and seller agree
        List<TransactionSigner> signers = Arrays.asList(
            SignerProviders.signerFrom(escrow.getBuyerKeys()),
            SignerProviders.signerFrom(escrow.getSellerKeys())
        );
        
        Tx tx = new Tx()
            .payToAddress(recipient, amount)
            .from(escrow.getEscrowAddress().getAddress())
            .attachSpendingScript(escrow.getScript())
            .attachMetadata(MessageMetadata.create()
                .add("resolution", "mutual_agreement"));
        
        return new QuickTxBuilder(backendService)
            .compose(tx)
            .withSigners(signers)
            .completeAndWait();
    }
    
    public Result<String> arbitratorDecision(
            EscrowContract escrow,
            String recipient,
            Amount amount,
            String decision) {
        
        long currentSlot = getCurrentSlot();
        if (currentSlot < escrow.getTimeoutSlot()) {
            return Result.error("Arbitrator can only act after timeout at slot " + escrow.getTimeoutSlot());
        }
        
        List<TransactionSigner> signers = Arrays.asList(
            SignerProviders.signerFrom(escrow.getArbitratorKeys())
        );
        
        Tx tx = new Tx()
            .payToAddress(recipient, amount)
            .from(escrow.getEscrowAddress().getAddress())
            .attachSpendingScript(escrow.getScript())
            .attachMetadata(MessageMetadata.create()
                .add("resolution", "arbitrator_decision")
                .add("decision", decision));
        
        return new QuickTxBuilder(backendService)
            .compose(tx)
            .withSigners(signers)
            .completeAndWait();
    }
}
```

### Multi-party Escrow with Milestones

Complex escrow with milestone-based releases.

```java
public class MilestoneEscrow {
    
    public static class MilestoneContract {
        private final Map<String, NativeScript> milestones;
        private final Map<String, Address> milestoneAddresses;
        private final Keys clientKeys;
        private final Keys vendorKeys;
        private final Keys pmKeys; // Project Manager
        
        // Constructor and getters...
    }
    
    public MilestoneContract createMilestoneEscrow(Network network) {
        ScriptPubkey client = ScriptPubkey.createWithNewKey();
        ScriptPubkey vendor = ScriptPubkey.createWithNewKey();
        ScriptPubkey pm = ScriptPubkey.createWithNewKey(); // Project Manager
        
        Map<String, NativeScript> milestones = new HashMap<>();
        Map<String, Address> milestoneAddresses = new HashMap<>();
        
        // Milestone 1: Planning (Client + PM approval)
        ScriptAll milestone1 = new ScriptAll()
            .addScript(client)
            .addScript(pm);
        
        // Milestone 2: Development (Any 2 of 3)
        ScriptAtLeast milestone2 = new ScriptAtLeast(2)
            .addScript(client)
            .addScript(vendor)
            .addScript(pm);
        
        // Milestone 3: Delivery (Client + Vendor)
        ScriptAll milestone3 = new ScriptAll()
            .addScript(client)
            .addScript(vendor);
        
        milestones.put("planning", milestone1);
        milestones.put("development", milestone2);
        milestones.put("delivery", milestone3);
        
        // Generate addresses for each milestone
        for (Map.Entry<String, NativeScript> entry : milestones.entrySet()) {
            Address address = AddressProvider.getEntAddress(entry.getValue(), network);
            milestoneAddresses.put(entry.getKey(), address);
        }
        
        return new MilestoneContract(
            milestones,
            milestoneAddresses,
            client.getKeys(),
            vendor.getKeys(),
            pm.getKeys()
        );
    }
    
    public Result<String> releaseMilestone(
            MilestoneContract contract,
            String milestone,
            List<String> approvers,
            String recipient,
            Amount amount) {
        
        NativeScript milestoneScript = contract.getMilestones().get(milestone);
        if (milestoneScript == null) {
            return Result.error("Unknown milestone: " + milestone);
        }
        
        // Map approvers to keys
        Map<String, Keys> roleKeys = Map.of(
            "client", contract.getClientKeys(),
            "vendor", contract.getVendorKeys(),
            "pm", contract.getPmKeys()
        );
        
        List<TransactionSigner> signers = approvers.stream()
            .map(roleKeys::get)
            .filter(Objects::nonNull)
            .map(SignerProviders::signerFrom)
            .collect(Collectors.toList());
        
        if (signers.isEmpty()) {
            return Result.error("No valid approvers provided");
        }
        
        Address milestoneAddress = contract.getMilestoneAddresses().get(milestone);
        
        Tx tx = new Tx()
            .payToAddress(recipient, amount)
            .from(milestoneAddress.getAddress())
            .attachSpendingScript(milestoneScript)
            .attachMetadata(MessageMetadata.create()
                .add("milestone", milestone)
                .add("approvers", String.join(",", approvers)));
        
        return new QuickTxBuilder(backendService)
            .compose(tx)
            .withSigners(signers)
            .completeAndWait();
    }
}
```

## Treasury Management Patterns

### Hierarchical Treasury (Different Spending Limits)

Different approval requirements based on amount.

```java
public class HierarchicalTreasury {
    
    public static class TreasurySystem {
        private final Map<String, NativeScript> tiers;
        private final Map<String, Address> tierAddresses;
        private final Map<String, BigInteger> spendingLimits;
        
        // Constructor and getters...
    }
    
    public TreasurySystem createHierarchicalTreasury(Network network) {
        // Key holders by role
        ScriptPubkey ceo = ScriptPubkey.createWithNewKey();
        ScriptPubkey cfo = ScriptPubkey.createWithNewKey();
        ScriptPubkey treasurer = ScriptPubkey.createWithNewKey();
        ScriptPubkey manager1 = ScriptPubkey.createWithNewKey();
        ScriptPubkey manager2 = ScriptPubkey.createWithNewKey();
        
        Map<String, NativeScript> tiers = new HashMap<>();
        Map<String, Address> tierAddresses = new HashMap<>();
        Map<String, BigInteger> spendingLimits = new HashMap<>();
        
        // Tier 1: Small amounts (any manager)
        ScriptAny tier1 = new ScriptAny()
            .addScript(manager1)
            .addScript(manager2);
        
        // Tier 2: Medium amounts (treasurer + any manager)
        ScriptAll tier2 = new ScriptAll()
            .addScript(treasurer)
            .addScript(new ScriptAny()
                .addScript(manager1)
                .addScript(manager2));
        
        // Tier 3: Large amounts (CFO + CEO)
        ScriptAll tier3 = new ScriptAll()
            .addScript(ceo)
            .addScript(cfo);
        
        // Tier 4: Massive amounts (all executives)
        ScriptAll tier4 = new ScriptAll()
            .addScript(ceo)
            .addScript(cfo)
            .addScript(treasurer);
        
        tiers.put("small", tier1);
        tiers.put("medium", tier2);
        tiers.put("large", tier3);
        tiers.put("massive", tier4);
        
        // Set spending limits (in lovelace)
        spendingLimits.put("small", adaToLovelace(1000));     // 1,000 ADA
        spendingLimits.put("medium", adaToLovelace(10000));   // 10,000 ADA
        spendingLimits.put("large", adaToLovelace(100000));   // 100,000 ADA
        spendingLimits.put("massive", adaToLovelace(1000000)); // 1,000,000 ADA
        
        // Generate addresses
        for (Map.Entry<String, NativeScript> entry : tiers.entrySet()) {
            Address address = AddressProvider.getEntAddress(entry.getValue(), network);
            tierAddresses.put(entry.getKey(), address);
        }
        
        return new TreasurySystem(tiers, tierAddresses, spendingLimits);
    }
    
    public String determineTier(BigInteger amount, TreasurySystem treasury) {
        for (Map.Entry<String, BigInteger> entry : treasury.getSpendingLimits().entrySet()) {
            if (amount.compareTo(entry.getValue()) <= 0) {
                return entry.getKey();
            }
        }
        return "massive"; // Default to highest tier
    }
    
    public Result<String> executePayment(
            TreasurySystem treasury,
            BigInteger amount,
            String recipient,
            List<String> approvers) {
        
        String tier = determineTier(amount, treasury);
        NativeScript tierScript = treasury.getTiers().get(tier);
        Address tierAddress = treasury.getTierAddresses().get(tier);
        
        // Map approvers to signers (simplified)
        List<TransactionSigner> signers = getSignersForApprovers(approvers);
        
        Tx tx = new Tx()
            .payToAddress(recipient, Amount.ada(amount))
            .from(tierAddress.getAddress())
            .attachSpendingScript(tierScript)
            .attachMetadata(MessageMetadata.create()
                .add("tier", tier)
                .add("amount", amount.toString())
                .add("approvers", String.join(",", approvers)));
        
        return new QuickTxBuilder(backendService)
            .compose(tx)
            .withSigners(signers)
            .completeAndWait();
    }
}
```

### DAO Treasury with Voting

Decentralized treasury management with token-weighted voting.

```java
public class DAOTreasury {
    
    public static class DAOProposal {
        private final String proposalId;
        private final String recipient;
        private final Amount amount;
        private final String description;
        private final long votingDeadline;
        private final Map<String, BigInteger> votes; // voter -> vote weight
        
        // Constructor and getters...
    }
    
    public NativeScript createDAOTreasuryScript() {
        // DAO Council (5 members, need 3)
        ScriptAtLeast council = new ScriptAtLeast(3)
            .addScript(ScriptPubkey.createWithNewKey()) // Council member 1
            .addScript(ScriptPubkey.createWithNewKey()) // Council member 2
            .addScript(ScriptPubkey.createWithNewKey()) // Council member 3
            .addScript(ScriptPubkey.createWithNewKey()) // Council member 4
            .addScript(ScriptPubkey.createWithNewKey()); // Council member 5
        
        // Emergency multisig (all founders)
        ScriptAll emergency = new ScriptAll()
            .addScript(ScriptPubkey.createWithNewKey()) // Founder 1
            .addScript(ScriptPubkey.createWithNewKey()) // Founder 2
            .addScript(ScriptPubkey.createWithNewKey()); // Founder 3
        
        long emergencyDelay = getCurrentSlot() + (30 * 24 * 60 * 60); // 30 days
        
        ScriptAll emergencyWithDelay = new ScriptAll()
            .addScript(emergency)
            .addScript(new RequireTimeAfter(emergencyDelay));
        
        // Either council decision OR emergency protocol
        return new ScriptAny()
            .addScript(council)
            .addScript(emergencyWithDelay);
    }
    
    public DAOProposal createProposal(
            String recipient,
            Amount amount,
            String description,
            long votingDurationSlots) {
        
        String proposalId = generateProposalId();
        long votingDeadline = getCurrentSlot() + votingDurationSlots;
        
        return new DAOProposal(
            proposalId,
            recipient,
            amount,
            description,
            votingDeadline,
            new HashMap<>()
        );
    }
    
    public Result<String> executeApprovedProposal(
            DAOProposal proposal,
            NativeScript treasuryScript,
            List<Keys> councilKeys) {
        
        if (councilKeys.size() < 3) {
            return Result.error("Need at least 3 council signatures");
        }
        
        List<TransactionSigner> signers = councilKeys.stream()
            .limit(3)
            .map(SignerProviders::signerFrom)
            .collect(Collectors.toList());
        
        Address treasuryAddress = AddressProvider.getEntAddress(treasuryScript, Networks.mainnet());
        
        Tx tx = new Tx()
            .payToAddress(proposal.getRecipient(), proposal.getAmount())
            .from(treasuryAddress.getAddress())
            .attachSpendingScript(treasuryScript)
            .attachMetadata(MessageMetadata.create()
                .add("proposal_id", proposal.getProposalId())
                .add("description", proposal.getDescription())
                .add("execution_time", Instant.now().toString()));
        
        return new QuickTxBuilder(backendService)
            .compose(tx)
            .withSigners(signers)
            .completeAndWait();
    }
}
```

## Real-World Use Cases

### Family Trust

Multi-generational wealth management.

```java
public class FamilyTrust {
    
    public NativeScript createFamilyTrust() {
        // Trustees (professional managers)
        ScriptAtLeast trustees = new ScriptAtLeast(2)
            .addScript(ScriptPubkey.createWithNewKey()) // Trustee 1
            .addScript(ScriptPubkey.createWithNewKey()) // Trustee 2
            .addScript(ScriptPubkey.createWithNewKey()); // Trustee 3
        
        // Family council (family members)
        ScriptAtLeast familyCouncil = new ScriptAtLeast(3)
            .addScript(ScriptPubkey.createWithNewKey()) // Parent 1
            .addScript(ScriptPubkey.createWithNewKey()) // Parent 2
            .addScript(ScriptPubkey.createWithNewKey()) // Child 1
            .addScript(ScriptPubkey.createWithNewKey()) // Child 2
            .addScript(ScriptPubkey.createWithNewKey()); // Child 3
        
        // Major decisions: trustees + family approval
        ScriptAll majorDecisions = new ScriptAll()
            .addScript(trustees)
            .addScript(familyCouncil);
        
        // Emergency: all trustees (with time delay for security)
        long emergencyDelay = getCurrentSlot() + (14 * 24 * 60 * 60); // 14 days
        
        ScriptAll emergencyAccess = new ScriptAll()
            .addScript(new RequireTimeAfter(emergencyDelay))
            .addScript(new ScriptAtLeast(3) // All trustees
                .addScript(ScriptPubkey.createWithNewKey())
                .addScript(ScriptPubkey.createWithNewKey())
                .addScript(ScriptPubkey.createWithNewKey()));
        
        return new ScriptAny()
            .addScript(majorDecisions)
            .addScript(emergencyAccess);
    }
}
```

### Cryptocurrency Exchange Hot Wallet

High-security exchange wallet with operational flexibility.

```java
public class ExchangeHotWallet {
    
    public NativeScript createExchangeWallet() {
        // Operational keys (for daily transactions)
        ScriptAtLeast operations = new ScriptAtLeast(2)
            .addScript(ScriptPubkey.createWithNewKey()) // Ops manager 1
            .addScript(ScriptPubkey.createWithNewKey()) // Ops manager 2
            .addScript(ScriptPubkey.createWithNewKey()); // Ops manager 3
        
        // Security team (for large withdrawals)
        ScriptAtLeast security = new ScriptAtLeast(2)
            .addScript(ScriptPubkey.createWithNewKey()) // Security lead
            .addScript(ScriptPubkey.createWithNewKey()) // CISO
            .addScript(ScriptPubkey.createWithNewKey()); // Security auditor
        
        // Executive approval (for emergency actions)
        ScriptAll executives = new ScriptAll()
            .addScript(ScriptPubkey.createWithNewKey()) // CEO
            .addScript(ScriptPubkey.createWithNewKey()); // CTO
        
        // Time-based restrictions for security
        long businessHoursStart = getCurrentSlot();
        long businessHoursEnd = businessHoursStart + (8 * 60 * 60); // 8 hours
        
        ScriptAll businessHours = new ScriptAll()
            .addScript(new RequireTimeAfter(businessHoursStart))
            .addScript(new RequireTimeBefore(businessHoursEnd));
        
        // Operational transactions during business hours
        ScriptAll operationalWindow = new ScriptAll()
            .addScript(operations)
            .addScript(businessHours);
        
        // Large transactions need security approval
        ScriptAll secureTransactions = new ScriptAll()
            .addScript(operations)
            .addScript(security);
        
        // Emergency access
        ScriptAny emergencyAccess = new ScriptAny()
            .addScript(executives)
            .addScript(new ScriptAll() // All security team
                .addScript(ScriptPubkey.createWithNewKey())
                .addScript(ScriptPubkey.createWithNewKey())
                .addScript(ScriptPubkey.createWithNewKey()));
        
        return new ScriptAny()
            .addScript(operationalWindow)
            .addScript(secureTransactions)
            .addScript(emergencyAccess);
    }
}
```

## Testing Multi-signature Patterns

### Unit Tests for Multi-signature Scripts

```java
@Test
public class MultiSigPatternTests {
    
    @Test
    public void test2of3MultiSig() {
        MajorityWallet.MultiSigWallet wallet = new MajorityWallet().create2of3Wallet(Networks.testnet());
        
        // Test that we can spend with any 2 keys
        List<Keys> firstTwo = wallet.getAllKeys().subList(0, 2);
        List<Keys> lastTwo = wallet.getAllKeys().subList(1, 3);
        
        assertNotNull(wallet.getScript());
        assertEquals(28, wallet.getScript().getScriptHash().length); // Valid script hash
        
        // Both combinations should work (in real implementation)
        assertTrue(canSpendWith(wallet, firstTwo));
        assertTrue(canSpendWith(wallet, lastTwo));
    }
    
    @Test
    public void testEmergencyRecovery() {
        EmergencyRecoveryWallet emergency = new EmergencyRecoveryWallet();
        NativeScript script = emergency.createEmergencyWallet();
        
        assertNotNull(script);
        assertTrue(script instanceof ScriptAny);
        
        ScriptAny scriptAny = (ScriptAny) script;
        assertEquals(2, scriptAny.getNativeScripts().size());
    }
    
    @Test
    public void testHierarchicalTreasury() {
        HierarchicalTreasury.TreasurySystem treasury = 
            new HierarchicalTreasury().createHierarchicalTreasury(Networks.testnet());
        
        // Test tier determination
        HierarchicalTreasury hierarchical = new HierarchicalTreasury();
        
        assertEquals("small", hierarchical.determineTier(adaToLovelace(500), treasury));
        assertEquals("medium", hierarchical.determineTier(adaToLovelace(5000), treasury));
        assertEquals("large", hierarchical.determineTier(adaToLovelace(50000), treasury));
        assertEquals("massive", hierarchical.determineTier(adaToLovelace(500000), treasury));
    }
    
    @Test
    public void testEscrowContract() {
        SimpleEscrow escrow = new SimpleEscrow();
        SimpleEscrow.EscrowContract contract = escrow.createEscrow(Networks.testnet(), 72);
        
        assertNotNull(contract.getScript());
        assertTrue(contract.getTimeoutSlot() > getCurrentSlot());
        
        // Test mutual release scenario
        assertNotNull(contract.getBuyerKeys());
        assertNotNull(contract.getSellerKeys());
        assertNotNull(contract.getArbitratorKeys());
    }
}
```

## Best Practices and Security

### Security Guidelines

✅ **Key Management**
- Generate keys securely with proper entropy
- Store keys separately and securely
- Implement key rotation procedures
- Use hardware security modules when possible

✅ **Script Design**
- Keep scripts as simple as possible
- Test thoroughly before deployment
- Document approval procedures clearly
- Implement proper time delays for security

✅ **Operational Security**
- Verify all signers before transactions
- Use metadata for audit trails
- Implement spending limits and approvals
- Monitor all multi-sig transactions

### Common Pitfalls to Avoid

❌ **Insufficient Testing**
- Always test scripts with real key combinations
- Verify time constraints work as expected
- Test edge cases and error conditions

❌ **Poor Key Distribution**
- Don't store multiple keys in same location
- Ensure key holders understand their responsibilities
- Have clear procedures for key recovery

❌ **Inadequate Documentation**
- Document who has which keys
- Maintain clear approval procedures
- Keep records of all transactions

## Summary

Multi-signature patterns provide powerful tools for shared fund management:

### Key Patterns Covered

✅ **Basic Multi-sig** - 2-of-2, 2-of-3, 3-of-5 patterns  
✅ **Time-locked Multi-sig** - Emergency recovery, vesting  
✅ **Escrow Patterns** - Simple and milestone-based  
✅ **Treasury Management** - Hierarchical and DAO governance  
✅ **Real-world Applications** - Family trusts, exchange wallets  

### When to Use Each Pattern

- **2-of-2**: Joint accounts, partnerships
- **2-of-3**: Personal security, small businesses
- **3-of-5**: Corporate governance, larger organizations
- **Time-locked**: Emergency recovery, vesting schedules
- **Escrow**: Business transactions, milestone payments
- **Hierarchical**: Treasury management, spending controls

### Next Steps

Now that you understand multi-signature patterns, explore:

- **[Native Scripts Overview](./native-scripts-overview.md)** - Core script functionality
- **[HD Wallets & Accounts](../accounts-and-addresses/hd-wallets.md)** - Key management for multi-sig
- **[Address Types](../accounts-and-addresses/address-types.md)** - Script addresses and validation

## Resources

- **[Multi-signature Best Practices](https://docs.cardano.org/native-tokens/multi-signature-scripts)** - Official guidelines
- **[Examples Repository](https://github.com/bloxbean/cardano-client-examples)** - Complete working examples
- **[Security Guidelines](https://github.com/cardano-foundation/CIPs)** - Cardano security standards

---

**Remember**: Multi-signature wallets are powerful security tools, but they require careful planning, proper key management, and thorough testing before deployment to production environments.