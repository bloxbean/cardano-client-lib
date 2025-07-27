---
description: Complete guide to transaction signing, verification workflows, multi-signature scenarios, and CIP-8 message signing
sidebar_label: Signing & Verification
sidebar_position: 2
---

# Signing and Verification Guide

Digital signatures are fundamental to Cardano's security model, ensuring transaction authenticity and authorization. This guide covers all aspects of signing and verification, from basic transaction signing to advanced multi-signature scenarios and CIP-8 message signing.

## Overview: Digital Signatures in Cardano

Cardano uses Ed25519 digital signatures to:

- **Authenticate transactions** - Prove ownership of UTXOs
- **Authorize spending** - Validate spending permissions
- **Enable multi-signature** - Support shared control mechanisms
- **Sign messages** - Provide off-chain authentication
- **Verify integrity** - Ensure data hasn't been tampered with

### Key Concepts

- **Private Key** - Secret key used for signing (never shared)
- **Public Key** - Corresponding public key used for verification
- **Signature** - Cryptographic proof created with private key
- **Transaction Hash** - Unique identifier for transaction content
- **Witness** - Container for signatures and other proof elements

## Transaction Signing Patterns

### Basic Transaction Signing

```java
import com.bloxbean.cardano.client.crypto.EdDSASigningProvider;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;

public class BasicTransactionSigning {
    
    public String signAndSubmitTransaction() {
        // Create account and transaction
        Account senderAccount = Account.createFromMnemonic(Networks.mainnet(), mnemonic);
        
        Tx tx = new Tx()
            .payToAddress(receiverAddress, Amount.ada(10))
            .from(senderAccount.baseAddress());
        
        // Sign and submit using QuickTx
        Result<String> result = new QuickTxBuilder(backendService)
            .compose(tx)
            .withSigner(SignerProviders.signerFrom(senderAccount))
            .completeAndWait();
        
        if (result.isSuccessful()) {
            return result.getValue(); // Transaction hash
        } else {
            throw new RuntimeException("Transaction failed: " + result.getResponse());
        }
    }
    
    public void demonstrateManualSigning() {
        Account account = Account.createFromMnemonic(Networks.mainnet(), mnemonic);
        
        // Create transaction manually
        TransactionBody txBody = TransactionBody.builder()
            .inputs(getInputs())
            .outputs(getOutputs())
            .fee(BigInteger.valueOf(170000))
            .build();
        
        // Sign transaction body
        byte[] txBodyBytes = txBody.serialize();
        byte[] signature = account.sign(txBodyBytes);
        
        // Create witness
        VkeyWitness witness = VkeyWitness.builder()
            .vkey(account.publicKeyHex())
            .signature(Hex.encodeHexString(signature))
            .build();
        
        TransactionWitnessSet witnessSet = TransactionWitnessSet.builder()
            .vkeyWitnesses(Arrays.asList(witness))
            .build();
        
        // Build complete transaction
        Transaction signedTx = Transaction.builder()
            .body(txBody)
            .witnessSet(witnessSet)
            .build();
        
        System.out.println("Signed transaction: " + Hex.encodeHexString(signedTx.serialize()));
    }
}
```

### Multi-Input Signing

When spending from multiple UTXOs, you need signatures for each input.

```java
public class MultiInputSigning {
    
    public Result<String> spendFromMultipleAccounts(
            List<Account> accounts,
            List<Utxo> utxos,
            String recipient,
            Amount totalAmount) {
        
        // Validate that accounts correspond to UTXOs
        validateAccountsForUtxos(accounts, utxos);
        
        // Build transaction with multiple inputs
        Tx tx = new Tx()
            .payToAddress(recipient, totalAmount);
        
        // Add inputs from different accounts
        for (int i = 0; i < utxos.size(); i++) {
            Utxo utxo = utxos.get(i);
            Account account = accounts.get(i);
            
            tx.collectFrom(utxo, account.baseAddress());
        }
        
        // Create signers for all accounts
        List<TransactionSigner> signers = accounts.stream()
            .map(SignerProviders::signerFrom)
            .collect(Collectors.toList());
        
        return new QuickTxBuilder(backendService)
            .compose(tx)
            .withSigners(signers)
            .completeAndWait();
    }
    
    private void validateAccountsForUtxos(List<Account> accounts, List<Utxo> utxos) {
        if (accounts.size() != utxos.size()) {
            throw new IllegalArgumentException("Account count must match UTXO count");
        }
        
        for (int i = 0; i < accounts.size(); i++) {
            Account account = accounts.get(i);
            Utxo utxo = utxos.get(i);
            
            String accountAddress = account.baseAddress();
            String utxoAddress = utxo.getAddress();
            
            if (!accountAddress.equals(utxoAddress)) {
                throw new IllegalArgumentException(
                    "Account " + i + " address doesn't match UTXO address");
            }
        }
    }
}
```

### Script Signing

Signing transactions that spend from script addresses.

```java
public class ScriptSigning {
    
    public Result<String> spendFromNativeScript(
            NativeScript script,
            List<Keys> requiredKeys,
            Utxo scriptUtxo,
            String recipient,
            Amount amount) {
        
        // Determine which signatures are needed
        List<String> requiredKeyHashes = extractRequiredKeyHashes(script);
        
        // Validate we have the required keys
        validateRequiredKeys(requiredKeys, requiredKeyHashes);
        
        // Build transaction
        Tx tx = new Tx()
            .collectFrom(scriptUtxo, script)
            .payToAddress(recipient, amount)
            .attachSpendingScript(script);
        
        // Create signers
        List<TransactionSigner> signers = requiredKeys.stream()
            .map(SignerProviders::signerFrom)
            .collect(Collectors.toList());
        
        return new QuickTxBuilder(backendService)
            .compose(tx)
            .withSigners(signers)
            .completeAndWait();
    }
    
    public Result<String> spendFromPlutusScript(
            Script plutusScript,
            PlutusData redeemer,
            PlutusData datum,
            List<Keys> requiredKeys,
            Utxo scriptUtxo,
            String recipient,
            Amount amount) {
        
        // Build script transaction
        ScriptTx scriptTx = new ScriptTx()
            .collectFrom(scriptUtxo, redeemer)
            .payToAddress(recipient, amount)
            .attachSpendingScript(plutusScript);
        
        // For Plutus scripts, we also need the datum if it's not inline
        if (datum != null && scriptUtxo.getInlineDatum() == null) {
            scriptTx.withTxInputDatum(scriptUtxo.getTxHash(), scriptUtxo.getOutputIndex(), datum);
        }
        
        List<TransactionSigner> signers = requiredKeys.stream()
            .map(SignerProviders::signerFrom)
            .collect(Collectors.toList());
        
        return new QuickTxBuilder(backendService)
            .compose(scriptTx)
            .withSigners(signers)
            .completeAndWait();
    }
    
    private List<String> extractRequiredKeyHashes(NativeScript script) {
        List<String> keyHashes = new ArrayList<>();
        extractKeyHashesRecursive(script, keyHashes);
        return keyHashes;
    }
    
    private void extractKeyHashesRecursive(NativeScript script, List<String> keyHashes) {
        if (script instanceof ScriptPubkey) {
            ScriptPubkey pubkeyScript = (ScriptPubkey) script;
            keyHashes.add(pubkeyScript.getKeyHash());
            
        } else if (script instanceof ScriptAll) {
            ScriptAll scriptAll = (ScriptAll) script;
            for (NativeScript subScript : scriptAll.getNativeScripts()) {
                extractKeyHashesRecursive(subScript, keyHashes);
            }
            
        } else if (script instanceof ScriptAny) {
            ScriptAny scriptAny = (ScriptAny) script;
            for (NativeScript subScript : scriptAny.getNativeScripts()) {
                extractKeyHashesRecursive(subScript, keyHashes);
            }
            
        } else if (script instanceof ScriptAtLeast) {
            ScriptAtLeast scriptAtLeast = (ScriptAtLeast) script;
            for (NativeScript subScript : scriptAtLeast.getNativeScripts()) {
                extractKeyHashesRecursive(subScript, keyHashes);
            }
        }
        // Time-based scripts don't require additional signatures
    }
    
    private void validateRequiredKeys(List<Keys> providedKeys, List<String> requiredKeyHashes) {
        Set<String> providedKeyHashes = providedKeys.stream()
            .map(keys -> KeyGenUtil.getKeyHash(keys.getVkey()))
            .collect(Collectors.toSet());
        
        for (String requiredHash : requiredKeyHashes) {
            if (!providedKeyHashes.contains(requiredHash)) {
                throw new IllegalArgumentException("Missing required key for hash: " + requiredHash);
            }
        }
    }
}
```

## Multi-signature Scenarios

### Coordinated Multi-signature

Multiple parties signing the same transaction.

```java
public class CoordinatedMultiSig {
    
    public static class PartiallySignedTransaction {
        private final Transaction transaction;
        private final Set<String> signedKeyHashes;
        private final Set<String> requiredKeyHashes;
        
        // Constructor and getters...
        
        public boolean isFullySigned() {
            return signedKeyHashes.containsAll(requiredKeyHashes);
        }
        
        public Set<String> getMissingSignatures() {
            Set<String> missing = new HashSet<>(requiredKeyHashes);
            missing.removeAll(signedKeyHashes);
            return missing;
        }
    }
    
    public PartiallySignedTransaction createPartialTransaction(
            NativeScript multiSigScript,
            Utxo scriptUtxo,
            String recipient,
            Amount amount) {
        
        // Build unsigned transaction
        TransactionBody txBody = TransactionBody.builder()
            .inputs(Arrays.asList(TransactionInput.of(scriptUtxo)))
            .outputs(Arrays.asList(createOutput(recipient, amount)))
            .fee(calculateFee())
            .build();
        
        // Create empty witness set
        TransactionWitnessSet witnessSet = TransactionWitnessSet.builder()
            .vkeyWitnesses(new ArrayList<>())
            .nativeScripts(Arrays.asList(multiSigScript))
            .build();
        
        Transaction unsignedTx = Transaction.builder()
            .body(txBody)
            .witnessSet(witnessSet)
            .build();
        
        Set<String> requiredKeyHashes = extractRequiredKeyHashes(multiSigScript);
        
        return new PartiallySignedTransaction(unsignedTx, new HashSet<>(), requiredKeyHashes);
    }
    
    public PartiallySignedTransaction addSignature(
            PartiallySignedTransaction partialTx,
            Keys signerKeys) {
        
        String keyHash = KeyGenUtil.getKeyHash(signerKeys.getVkey());
        
        if (!partialTx.getRequiredKeyHashes().contains(keyHash)) {
            throw new IllegalArgumentException("This key is not required for this transaction");
        }
        
        if (partialTx.getSignedKeyHashes().contains(keyHash)) {
            throw new IllegalArgumentException("This key has already signed the transaction");
        }
        
        // Sign transaction body
        byte[] txBodyBytes = partialTx.getTransaction().getBody().serialize();
        byte[] signature = signerKeys.sign(txBodyBytes);
        
        // Create new witness
        VkeyWitness newWitness = VkeyWitness.builder()
            .vkey(signerKeys.getVkey())
            .signature(Hex.encodeHexString(signature))
            .build();
        
        // Add to existing witnesses
        List<VkeyWitness> witnesses = new ArrayList<>(
            partialTx.getTransaction().getWitnessSet().getVkeyWitnesses()
        );
        witnesses.add(newWitness);
        
        // Update witness set
        TransactionWitnessSet updatedWitnessSet = partialTx.getTransaction().getWitnessSet()
            .toBuilder()
            .vkeyWitnesses(witnesses)
            .build();
        
        Transaction updatedTx = partialTx.getTransaction()
            .toBuilder()
            .witnessSet(updatedWitnessSet)
            .build();
        
        Set<String> updatedSignedHashes = new HashSet<>(partialTx.getSignedKeyHashes());
        updatedSignedHashes.add(keyHash);
        
        return new PartiallySignedTransaction(
            updatedTx,
            updatedSignedHashes,
            partialTx.getRequiredKeyHashes()
        );
    }
    
    public String submitWhenComplete(PartiallySignedTransaction partialTx) {
        if (!partialTx.isFullySigned()) {
            throw new IllegalStateException(
                "Transaction not fully signed. Missing: " + partialTx.getMissingSignatures()
            );
        }
        
        try {
            return backendService.getTransactionService()
                .submitTransaction(partialTx.getTransaction().serialize())
                .getValue();
        } catch (Exception e) {
            throw new RuntimeException("Failed to submit transaction", e);
        }
    }
}
```

### Distributed Multi-signature Workflow

```java
public class DistributedMultiSig {
    
    public void demonstrateDistributedFlow() {
        // Step 1: Coordinator creates unsigned transaction
        CoordinatedMultiSig coordinator = new CoordinatedMultiSig();
        
        NativeScript twoOfThreeScript = new ScriptAtLeast(2)
            .addScript(ScriptPubkey.createWithNewKey()) // Alice
            .addScript(ScriptPubkey.createWithNewKey()) // Bob
            .addScript(ScriptPubkey.createWithNewKey()); // Charlie
        
        Utxo scriptUtxo = // ... get UTXO from script address
        
        PartiallySignedTransaction partialTx = coordinator.createPartialTransaction(
            twoOfThreeScript,
            scriptUtxo,
            "addr1...", // recipient
            Amount.ada(100)
        );
        
        // Step 2: Serialize and send to signers
        String serializedTx = serializePartialTransaction(partialTx);
        
        // Step 3: Alice signs
        Keys aliceKeys = // ... Alice's keys
        PartiallySignedTransaction aliceSigned = coordinator.addSignature(partialTx, aliceKeys);
        
        // Step 4: Bob signs (Charlie doesn't need to for 2-of-3)
        Keys bobKeys = // ... Bob's keys
        PartiallySignedTransaction fullySigned = coordinator.addSignature(aliceSigned, bobKeys);
        
        // Step 5: Submit completed transaction
        String txHash = coordinator.submitWhenComplete(fullySigned);
        System.out.println("Multi-sig transaction submitted: " + txHash);
    }
    
    public String serializePartialTransaction(PartiallySignedTransaction partialTx) {
        // Serialize transaction and metadata for transmission
        Map<String, Object> data = new HashMap<>();
        data.put("transaction", Hex.encodeHexString(partialTx.getTransaction().serialize()));
        data.put("signed_key_hashes", partialTx.getSignedKeyHashes());
        data.put("required_key_hashes", partialTx.getRequiredKeyHashes());
        
        // Convert to JSON or other format for transmission
        return new ObjectMapper().writeValueAsString(data);
    }
    
    public PartiallySignedTransaction deserializePartialTransaction(String serialized) {
        // Deserialize from transmitted format
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> data = mapper.readValue(serialized, Map.class);
        
        byte[] txBytes = Hex.decodeHex((String) data.get("transaction"));
        Transaction transaction = Transaction.deserialize(txBytes);
        
        Set<String> signedHashes = new HashSet<>((List<String>) data.get("signed_key_hashes"));
        Set<String> requiredHashes = new HashSet<>((List<String>) data.get("required_key_hashes"));
        
        return new PartiallySignedTransaction(transaction, signedHashes, requiredHashes);
    }
}
```

## CIP-8 Message Signing

CIP-8 defines a standard for signing arbitrary messages with Cardano keys.

### Basic Message Signing

```java
import com.bloxbean.cardano.client.crypto.cip8.COSESign1;
import com.bloxbean.cardano.client.crypto.cip8.COSESignature;

public class CIP8MessageSigning {
    
    public String signMessage(String message, Keys signerKeys, String address) {
        try {
            // Create CIP-8 signature
            COSESignature coseSignature = COSESignature.builder()
                .message(message)
                .address(address)
                .signerKey(signerKeys)
                .build();
            
            // Generate signature
            String signature = coseSignature.sign();
            
            System.out.println("Message: " + message);
            System.out.println("Address: " + address);
            System.out.println("Signature: " + signature);
            
            return signature;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign message", e);
        }
    }
    
    public boolean verifyMessage(String message, String signature, String address) {
        try {
            // Verify CIP-8 signature
            return COSESignature.verify(message, signature, address);
            
        } catch (Exception e) {
            System.err.println("Verification failed: " + e.getMessage());
            return false;
        }
    }
    
    public void demonstrateMessageSigning() {
        // Create account
        Account account = Account.createFromMnemonic(Networks.mainnet(), mnemonic);
        String address = account.baseAddress();
        
        // Sign authentication message
        String authMessage = "Login to MyApp at " + Instant.now();
        String signature = signMessage(authMessage, account.keys(), address);
        
        // Verify signature
        boolean isValid = verifyMessage(authMessage, signature, address);
        System.out.println("Signature valid: " + isValid);
        
        // Use signature for authentication
        if (isValid) {
            System.out.println("User authenticated successfully");
        }
    }
}
```

### Advanced Message Signing Patterns

```java
public class AdvancedMessageSigning {
    
    public static class SignedMessage {
        private final String message;
        private final String signature;
        private final String address;
        private final long timestamp;
        private final Map<String, String> metadata;
        
        // Constructor and getters...
    }
    
    public SignedMessage createAuthenticationToken(Account account, String applicationId, long validDuration) {
        long timestamp = Instant.now().getEpochSecond();
        long expiryTime = timestamp + validDuration;
        
        // Create structured message
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("application", applicationId);
        messageData.put("address", account.baseAddress());
        messageData.put("timestamp", timestamp);
        messageData.put("expires", expiryTime);
        messageData.put("nonce", generateNonce());
        
        String message = new ObjectMapper().writeValueAsString(messageData);
        
        // Sign message
        CIP8MessageSigning signer = new CIP8MessageSigning();
        String signature = signer.signMessage(message, account.keys(), account.baseAddress());
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("application", applicationId);
        metadata.put("expires", String.valueOf(expiryTime));
        
        return new SignedMessage(message, signature, account.baseAddress(), timestamp, metadata);
    }
    
    public boolean validateAuthenticationToken(SignedMessage signedMessage, String expectedApplication) {
        try {
            // Parse message
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> messageData = mapper.readValue(signedMessage.getMessage(), Map.class);
            
            // Validate application
            if (!expectedApplication.equals(messageData.get("application"))) {
                System.err.println("Invalid application ID");
                return false;
            }
            
            // Validate expiry
            long expiryTime = ((Number) messageData.get("expires")).longValue();
            if (Instant.now().getEpochSecond() > expiryTime) {
                System.err.println("Token expired");
                return false;
            }
            
            // Validate signature
            CIP8MessageSigning verifier = new CIP8MessageSigning();
            if (!verifier.verifyMessage(signedMessage.getMessage(), signedMessage.getSignature(), signedMessage.getAddress())) {
                System.err.println("Invalid signature");
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            System.err.println("Token validation failed: " + e.getMessage());
            return false;
        }
    }
    
    public SignedMessage signContractTerms(Account account, String contractHash, List<String> terms) {
        // Create contract agreement message
        Map<String, Object> contractData = new HashMap<>();
        contractData.put("type", "contract_agreement");
        contractData.put("contract_hash", contractHash);
        contractData.put("terms", terms);
        contractData.put("signer_address", account.baseAddress());
        contractData.put("timestamp", Instant.now().toString());
        
        String message = new ObjectMapper().writeValueAsString(contractData);
        
        CIP8MessageSigning signer = new CIP8MessageSigning();
        String signature = signer.signMessage(message, account.keys(), account.baseAddress());
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("contract_hash", contractHash);
        metadata.put("type", "contract_agreement");
        
        return new SignedMessage(message, signature, account.baseAddress(), 
                                Instant.now().getEpochSecond(), metadata);
    }
    
    private String generateNonce() {
        return UUID.randomUUID().toString();
    }
}
```

### CIP-8 in dApp Integration

```java
public class DAppMessageSigning {
    
    public static class WalletConnectRequest {
        private final String challenge;
        private final String dappName;
        private final String dappUrl;
        private final long expiryTime;
        
        // Constructor and getters...
    }
    
    public WalletConnectRequest createConnectChallenge(String dappName, String dappUrl) {
        String challenge = generateSecureChallenge();
        long expiryTime = Instant.now().getEpochSecond() + 300; // 5 minutes
        
        return new WalletConnectRequest(challenge, dappName, dappUrl, expiryTime);
    }
    
    public SignedMessage signConnectChallenge(WalletConnectRequest request, Account account) {
        // Create connection message
        Map<String, Object> connectData = new HashMap<>();
        connectData.put("action", "wallet_connect");
        connectData.put("challenge", request.getChallenge());
        connectData.put("dapp_name", request.getDappName());
        connectData.put("dapp_url", request.getDappUrl());
        connectData.put("wallet_address", account.baseAddress());
        connectData.put("timestamp", Instant.now().getEpochSecond());
        
        String message = new ObjectMapper().writeValueAsString(connectData);
        
        CIP8MessageSigning signer = new CIP8MessageSigning();
        String signature = signer.signMessage(message, account.keys(), account.baseAddress());
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("dapp_name", request.getDappName());
        metadata.put("action", "wallet_connect");
        
        return new SignedMessage(message, signature, account.baseAddress(),
                                Instant.now().getEpochSecond(), metadata);
    }
    
    public boolean verifyConnectSignature(WalletConnectRequest request, SignedMessage signedMessage) {
        try {
            // Parse signed message
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> connectData = mapper.readValue(signedMessage.getMessage(), Map.class);
            
            // Validate challenge
            if (!request.getChallenge().equals(connectData.get("challenge"))) {
                return false;
            }
            
            // Validate expiry
            if (Instant.now().getEpochSecond() > request.getExpiryTime()) {
                return false;
            }
            
            // Verify signature
            CIP8MessageSigning verifier = new CIP8MessageSigning();
            return verifier.verifyMessage(signedMessage.getMessage(), 
                                        signedMessage.getSignature(), 
                                        signedMessage.getAddress());
            
        } catch (Exception e) {
            return false;
        }
    }
    
    private String generateSecureChallenge() {
        SecureRandom random = new SecureRandom();
        byte[] challengeBytes = new byte[32];
        random.nextBytes(challengeBytes);
        return Hex.encodeHexString(challengeBytes);
    }
}
```

## Verification Workflows

### Transaction Verification

```java
public class TransactionVerification {
    
    public boolean verifyTransaction(Transaction transaction) {
        try {
            // Extract transaction body and witnesses
            TransactionBody txBody = transaction.getBody();
            TransactionWitnessSet witnessSet = transaction.getWitnessSet();
            
            if (witnessSet == null || witnessSet.getVkeyWitnesses() == null) {
                System.err.println("Transaction has no witnesses");
                return false;
            }
            
            // Verify each signature
            byte[] txBodyBytes = txBody.serialize();
            
            for (VkeyWitness witness : witnessSet.getVkeyWitnesses()) {
                if (!verifyWitness(witness, txBodyBytes)) {
                    System.err.println("Invalid witness: " + witness.getVkey());
                    return false;
                }
            }
            
            // Verify script witnesses if present
            if (witnessSet.getNativeScripts() != null) {
                if (!verifyNativeScripts(txBody, witnessSet)) {
                    System.err.println("Native script validation failed");
                    return false;
                }
            }
            
            return true;
            
        } catch (Exception e) {
            System.err.println("Transaction verification error: " + e.getMessage());
            return false;
        }
    }
    
    private boolean verifyWitness(VkeyWitness witness, byte[] txBodyBytes) {
        try {
            EdDSASigningProvider verifier = new EdDSASigningProvider();
            
            byte[] publicKey = Hex.decodeHex(witness.getVkey());
            byte[] signature = Hex.decodeHex(witness.getSignature());
            
            return verifier.verify(signature, txBodyBytes, publicKey);
            
        } catch (Exception e) {
            System.err.println("Witness verification failed: " + e.getMessage());
            return false;
        }
    }
    
    private boolean verifyNativeScripts(TransactionBody txBody, TransactionWitnessSet witnessSet) {
        // Simplified native script verification
        // In practice, this would need to evaluate script logic against inputs
        
        Set<String> providedKeyHashes = witnessSet.getVkeyWitnesses().stream()
            .map(w -> KeyGenUtil.getKeyHash(Hex.decodeHex(w.getVkey())))
            .collect(Collectors.toSet());
        
        for (NativeScript script : witnessSet.getNativeScripts()) {
            if (!evaluateNativeScript(script, providedKeyHashes)) {
                return false;
            }
        }
        
        return true;
    }
    
    private boolean evaluateNativeScript(NativeScript script, Set<String> providedKeyHashes) {
        if (script instanceof ScriptPubkey) {
            ScriptPubkey pubkeyScript = (ScriptPubkey) script;
            return providedKeyHashes.contains(pubkeyScript.getKeyHash());
            
        } else if (script instanceof ScriptAll) {
            ScriptAll scriptAll = (ScriptAll) script;
            return scriptAll.getNativeScripts().stream()
                .allMatch(s -> evaluateNativeScript(s, providedKeyHashes));
                
        } else if (script instanceof ScriptAny) {
            ScriptAny scriptAny = (ScriptAny) script;
            return scriptAny.getNativeScripts().stream()
                .anyMatch(s -> evaluateNativeScript(s, providedKeyHashes));
                
        } else if (script instanceof ScriptAtLeast) {
            ScriptAtLeast scriptAtLeast = (ScriptAtLeast) script;
            long satisfiedCount = scriptAtLeast.getNativeScripts().stream()
                .mapToLong(s -> evaluateNativeScript(s, providedKeyHashes) ? 1 : 0)
                .sum();
            return satisfiedCount >= scriptAtLeast.getRequired().longValue();
        }
        
        // Time-based scripts would need current slot for evaluation
        return true; // Simplified
    }
}
```

### Signature Audit Trail

```java
public class SignatureAuditTrail {
    
    public static class SignatureRecord {
        private final String txHash;
        private final String signerAddress;
        private final String signerKeyHash;
        private final Instant signatureTime;
        private final boolean isValid;
        private final String purpose;
        
        // Constructor and getters...
    }
    
    public List<SignatureRecord> auditTransactionSignatures(Transaction transaction) {
        List<SignatureRecord> records = new ArrayList<>();
        
        TransactionBody txBody = transaction.getBody();
        TransactionWitnessSet witnessSet = transaction.getWitnessSet();
        String txHash = calculateTransactionHash(txBody);
        
        if (witnessSet != null && witnessSet.getVkeyWitnesses() != null) {
            for (VkeyWitness witness : witnessSet.getVkeyWitnesses()) {
                SignatureRecord record = auditWitness(txHash, witness, txBody);
                records.add(record);
            }
        }
        
        return records;
    }
    
    private SignatureRecord auditWitness(String txHash, VkeyWitness witness, TransactionBody txBody) {
        try {
            byte[] publicKey = Hex.decodeHex(witness.getVkey());
            String keyHash = KeyGenUtil.getKeyHash(publicKey);
            
            // Determine signer address
            String signerAddress = deriveAddressFromPublicKey(publicKey);
            
            // Verify signature
            TransactionVerification verifier = new TransactionVerification();
            boolean isValid = verifier.verifyWitness(witness, txBody.serialize());
            
            // Determine purpose
            String purpose = determinePurpose(txBody, keyHash);
            
            return new SignatureRecord(
                txHash,
                signerAddress,
                keyHash,
                Instant.now(), // In practice, would be from block timestamp
                isValid,
                purpose
            );
            
        } catch (Exception e) {
            return new SignatureRecord(
                txHash,
                "unknown",
                "unknown",
                Instant.now(),
                false,
                "verification_failed: " + e.getMessage()
            );
        }
    }
    
    private String determinePurpose(TransactionBody txBody, String keyHash) {
        // Analyze transaction to determine why this key was needed
        
        // Check if it's for spending inputs
        for (TransactionInput input : txBody.getInputs()) {
            if (isKeyRequiredForInput(input, keyHash)) {
                return "spending_authorization";
            }
        }
        
        // Check if it's for certificates
        if (txBody.getCerts() != null) {
            for (Certificate cert : txBody.getCerts()) {
                if (isKeyRequiredForCertificate(cert, keyHash)) {
                    return "certificate_authorization";
                }
            }
        }
        
        // Check if it's for withdrawals
        if (txBody.getWithdrawals() != null) {
            for (Map.Entry<String, BigInteger> withdrawal : txBody.getWithdrawals().entrySet()) {
                if (isKeyRequiredForWithdrawal(withdrawal.getKey(), keyHash)) {
                    return "withdrawal_authorization";
                }
            }
        }
        
        return "unknown_purpose";
    }
    
    private boolean isKeyRequiredForInput(TransactionInput input, String keyHash) {
        // In practice, would need to resolve input UTXO and check if key hash matches
        return false; // Simplified
    }
    
    private boolean isKeyRequiredForCertificate(Certificate cert, String keyHash) {
        // Check if key is required for certificate
        return false; // Simplified
    }
    
    private boolean isKeyRequiredForWithdrawal(String rewardAddress, String keyHash) {
        // Check if key corresponds to reward address
        return false; // Simplified
    }
    
    private String deriveAddressFromPublicKey(byte[] publicKey) {
        // Derive potential addresses from public key
        try {
            Address baseAddress = AddressProvider.getBaseAddress(
                HdPublicKey.fromBytes(publicKey),
                HdPublicKey.fromBytes(publicKey), // Simplified - same key for payment and stake
                Networks.mainnet()
            );
            return baseAddress.getAddress();
        } catch (Exception e) {
            return "unknown_address";
        }
    }
    
    private String calculateTransactionHash(TransactionBody txBody) {
        try {
            byte[] txBodyBytes = txBody.serialize();
            byte[] hash = Blake2bUtil.blake2bHash256(txBodyBytes);
            return Hex.encodeHexString(hash);
        } catch (Exception e) {
            return "unknown_hash";
        }
    }
}
```

## Security Best Practices

### Secure Signing Environment

```java
public class SecureSigningEnvironment {
    
    public static class SigningSession {
        private final String sessionId;
        private final long createdAt;
        private final long expiresAt;
        private final Set<String> authorizedOperations;
        
        // Constructor and getters...
        
        public boolean isValid() {
            return Instant.now().getEpochSecond() < expiresAt;
        }
    }
    
    public SigningSession createSecureSession(Account account, Set<String> operations, long durationSeconds) {
        // Verify account access
        if (!verifyAccountAccess(account)) {
            throw new SecurityException("Account access verification failed");
        }
        
        String sessionId = generateSecureSessionId();
        long now = Instant.now().getEpochSecond();
        
        return new SigningSession(
            sessionId,
            now,
            now + durationSeconds,
            new HashSet<>(operations)
        );
    }
    
    public Result<String> secureSign(
            SigningSession session,
            String operation,
            Supplier<Result<String>> signingOperation) {
        
        // Validate session
        if (!session.isValid()) {
            return Result.error("Signing session expired");
        }
        
        if (!session.getAuthorizedOperations().contains(operation)) {
            return Result.error("Operation not authorized for this session");
        }
        
        try {
            // Perform signing with additional security measures
            return performSecureSigning(signingOperation);
            
        } catch (Exception e) {
            // Log security event
            logSecurityEvent("signing_failure", session.getSessionId(), operation, e.getMessage());
            return Result.error("Signing failed: " + e.getMessage());
        }
    }
    
    private Result<String> performSecureSigning(Supplier<Result<String>> signingOperation) {
        // Clear sensitive data after use
        try {
            return signingOperation.get();
        } finally {
            // Implement secure cleanup
            System.gc(); // Simplified - in practice, would implement secure memory clearing
        }
    }
    
    private boolean verifyAccountAccess(Account account) {
        try {
            // Test signature to verify key access
            byte[] testMessage = "access_verification".getBytes();
            byte[] signature = account.sign(testMessage);
            
            EdDSASigningProvider verifier = new EdDSASigningProvider();
            return verifier.verify(signature, testMessage, account.publicKey());
            
        } catch (Exception e) {
            return false;
        }
    }
    
    private String generateSecureSessionId() {
        SecureRandom random = new SecureRandom();
        byte[] sessionBytes = new byte[32];
        random.nextBytes(sessionBytes);
        return Hex.encodeHexString(sessionBytes);
    }
    
    private void logSecurityEvent(String eventType, String sessionId, String operation, String details) {
        // Implement security logging
        System.err.println(String.format("SECURITY_EVENT: %s, session=%s, operation=%s, details=%s",
                                        eventType, sessionId, operation, details));
    }
}
```

### Key Validation and Security

```java
public class SigningSecurityValidation {
    
    public boolean validateSigningRequest(Account account, TransactionBody txBody) {
        // Check for suspicious transaction patterns
        if (hasSuspiciousPatterns(txBody)) {
            System.err.println("Suspicious transaction pattern detected");
            return false;
        }
        
        // Validate transaction size
        if (isTransactionTooLarge(txBody)) {
            System.err.println("Transaction size exceeds safety limits");
            return false;
        }
        
        // Check output addresses
        if (hasBlacklistedAddresses(txBody)) {
            System.err.println("Transaction contains blacklisted addresses");
            return false;
        }
        
        // Validate fee amount
        if (hasExcessiveFees(txBody)) {
            System.err.println("Transaction fees are suspiciously high");
            return false;
        }
        
        return true;
    }
    
    private boolean hasSuspiciousPatterns(TransactionBody txBody) {
        // Check for known attack patterns
        
        // Too many outputs (potential dust attack)
        if (txBody.getOutputs().size() > 100) {
            return true;
        }
        
        // Excessive metadata size
        if (txBody.getAuxiliaryData() != null) {
            try {
                byte[] metadataBytes = txBody.getAuxiliaryData().serialize();
                if (metadataBytes.length > 16384) { // 16KB limit
                    return true;
                }
            } catch (Exception e) {
                return true; // Serialization failure is suspicious
            }
        }
        
        return false;
    }
    
    private boolean isTransactionTooLarge(TransactionBody txBody) {
        try {
            byte[] txBytes = txBody.serialize();
            return txBytes.length > 16384; // 16KB Cardano limit
        } catch (Exception e) {
            return true; // Serialization failure
        }
    }
    
    private boolean hasBlacklistedAddresses(TransactionBody txBody) {
        Set<String> blacklistedAddresses = getBlacklistedAddresses();
        
        for (TransactionOutput output : txBody.getOutputs()) {
            if (blacklistedAddresses.contains(output.getAddress())) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean hasExcessiveFees(TransactionBody txBody) {
        BigInteger fee = txBody.getFee();
        BigInteger maxReasonableFee = BigInteger.valueOf(1000000); // 1 ADA
        
        return fee.compareTo(maxReasonableFee) > 0;
    }
    
    private Set<String> getBlacklistedAddresses() {
        // In practice, would load from external service or configuration
        return new HashSet<>();
    }
}
```

## Performance and Optimization

### Batch Signing

```java
public class BatchSigning {
    
    public List<String> signMultipleTransactions(List<TransactionBody> transactions, Account signer) {
        List<String> signatures = new ArrayList<>();
        
        // Pre-compute signer information
        byte[] publicKey = signer.publicKey();
        String publicKeyHex = signer.publicKeyHex();
        
        for (TransactionBody txBody : transactions) {
            try {
                // Sign transaction
                byte[] txBodyBytes = txBody.serialize();
                byte[] signature = signer.sign(txBodyBytes);
                
                // Create witness
                VkeyWitness witness = VkeyWitness.builder()
                    .vkey(publicKeyHex)
                    .signature(Hex.encodeHexString(signature))
                    .build();
                
                // Create witness set
                TransactionWitnessSet witnessSet = TransactionWitnessSet.builder()
                    .vkeyWitnesses(Arrays.asList(witness))
                    .build();
                
                // Build complete transaction
                Transaction signedTx = Transaction.builder()
                    .body(txBody)
                    .witnessSet(witnessSet)
                    .build();
                
                signatures.add(Hex.encodeHexString(signedTx.serialize()));
                
            } catch (Exception e) {
                System.err.println("Failed to sign transaction: " + e.getMessage());
                signatures.add(null); // Maintain order
            }
        }
        
        return signatures;
    }
    
    public Map<String, String> signTransactionsParallel(List<TransactionBody> transactions, Account signer) {
        return transactions.parallelStream()
            .collect(Collectors.toMap(
                txBody -> calculateTxHash(txBody),
                txBody -> {
                    try {
                        return signSingleTransaction(txBody, signer);
                    } catch (Exception e) {
                        return "ERROR: " + e.getMessage();
                    }
                }
            ));
    }
    
    private String signSingleTransaction(TransactionBody txBody, Account signer) throws Exception {
        byte[] txBodyBytes = txBody.serialize();
        byte[] signature = signer.sign(txBodyBytes);
        
        VkeyWitness witness = VkeyWitness.builder()
            .vkey(signer.publicKeyHex())
            .signature(Hex.encodeHexString(signature))
            .build();
        
        TransactionWitnessSet witnessSet = TransactionWitnessSet.builder()
            .vkeyWitnesses(Arrays.asList(witness))
            .build();
        
        Transaction signedTx = Transaction.builder()
            .body(txBody)
            .witnessSet(witnessSet)
            .build();
        
        return Hex.encodeHexString(signedTx.serialize());
    }
    
    private String calculateTxHash(TransactionBody txBody) {
        try {
            byte[] txBodyBytes = txBody.serialize();
            byte[] hash = Blake2bUtil.blake2bHash256(txBodyBytes);
            return Hex.encodeHexString(hash);
        } catch (Exception e) {
            return "unknown";
        }
    }
}
```

## Summary and Best Practices

### Key Takeaways

✅ **Secure Key Management** - Protect private keys at all times  
✅ **Validate Before Signing** - Check transaction content and patterns  
✅ **Use Proper Witnesses** - Include all required signatures  
✅ **Implement Verification** - Always verify signatures after creation  
✅ **Handle Errors Gracefully** - Proper error handling for security  

### Security Checklist

- [ ] Private keys are stored securely and never logged
- [ ] Transaction content is validated before signing
- [ ] Signatures are verified after creation
- [ ] Multi-signature workflows are properly coordinated
- [ ] CIP-8 message signing follows standard format
- [ ] Rate limiting is implemented for signing operations
- [ ] Audit trails are maintained for security events

### Common Pitfalls to Avoid

❌ **Signing without validation** - Always validate transaction content  
❌ **Exposing private keys** - Never log or transmit private keys  
❌ **Incomplete multi-sig** - Ensure all required signatures are present  
❌ **Ignoring verification** - Always verify signatures after creation  

### Next Steps

Now that you understand signing and verification, explore:

- **[HD Wallets & Accounts](../accounts-and-addresses/hd-wallets.md)** - Account management for signing
- **[Native Scripts](../native-scripts/native-scripts-overview.md)** - Multi-signature script patterns
- **[Cryptographic Operations](./cryptographic-operations.md)** - Underlying cryptographic primitives

## Resources

- **[CIP-8 Specification](https://cips.cardano.org/cips/cip8/)** - Message signing standard
- **[Ed25519 Specification](https://tools.ietf.org/html/rfc8032)** - Digital signature algorithm
- **[Cardano Transaction Format](https://github.com/cardano-foundation/CIPs)** - Transaction specifications
- **[Examples Repository](https://github.com/bloxbean/cardano-client-examples)** - Complete working examples

---

**Remember**: Digital signatures are the foundation of blockchain security. Always validate inputs, protect private keys, and implement proper verification workflows to ensure the integrity and authenticity of your transactions.