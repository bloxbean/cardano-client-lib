/**
 * Complete Foundation Example - Cardano Client Library
 * 
 * This example demonstrates the complete workflow from account creation
 * to transaction submission using both QuickTx and Composable Functions.
 */

// 1. BACKEND SERVICE SETUP
// Initialize backend service with proper configuration
public class CardanoFoundationExample {
    
    public static void main(String[] args) {
        try {
            // Setup backend service
            BackendService backendService = createBackendService();
            QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
            
            // Create accounts
            AccountSetup accounts = createAccounts();
            
            // Example 1: Simple ADA Payment
            executeSimplePayment(quickTxBuilder, accounts);
            
            // Example 2: Token Minting
            executeTokenMinting(quickTxBuilder, accounts);
            
            // Example 3: Multi-signature Transaction
            executeMultiSigTransaction(quickTxBuilder, accounts);
            
            // Example 4: Script Interaction
            executeScriptInteraction(quickTxBuilder, accounts);
            
        } catch (Exception e) {
            System.err.println("Foundation example failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // BACKEND SERVICE CONFIGURATION
    private static BackendService createBackendService() {
        String apiKey = System.getenv("CARDANO_API_KEY");
        String backendUrl = System.getenv("CARDANO_BACKEND_URL");
        
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("CARDANO_API_KEY environment variable required");
        }
        
        if (backendUrl == null || backendUrl.isEmpty()) {
            backendUrl = "https://cardano-preview.blockfrost.io/api/v0/";
        }
        
        System.out.println("Connecting to: " + backendUrl);
        return new BFBackendService(backendUrl, apiKey);
    }
    
    // ACCOUNT SETUP
    private static AccountSetup createAccounts() {
        // Create accounts for different roles
        String mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
        
        Account sender = new Account(Networks.testnet(), mnemonic);
        Account receiver = new Account(Networks.testnet(), mnemonic, 1); // Derived account
        Account feePayer = new Account(Networks.testnet(), mnemonic, 2);
        
        System.out.println("=== ACCOUNT SETUP ===");
        System.out.println("Sender: " + sender.baseAddress());
        System.out.println("Receiver: " + receiver.baseAddress());
        System.out.println("Fee Payer: " + feePayer.baseAddress());
        
        return new AccountSetup(sender, receiver, feePayer);
    }
    
    // EXAMPLE 1: SIMPLE ADA PAYMENT
    private static void executeSimplePayment(QuickTxBuilder quickTxBuilder, AccountSetup accounts) {
        System.out.println("\n=== SIMPLE ADA PAYMENT ===");
        
        try {
            Amount paymentAmount = Amount.ada(2.5);
            
            Tx paymentTx = new Tx()
                .payToAddress(accounts.receiver.baseAddress(), paymentAmount)
                .from(accounts.sender.baseAddress());
            
            Result<String> result = quickTxBuilder.compose(paymentTx)
                .withSigner(SignerProviders.signerFrom(accounts.sender))
                .completeAndWait(System.out::println);
            
            if (result.isSuccessful()) {
                System.out.println("✅ Payment successful!");
                System.out.println("Amount: " + paymentAmount);
                System.out.println("TxHash: " + result.getValue());
            } else {
                System.err.println("❌ Payment failed: " + result.getResponse());
            }
            
        } catch (Exception e) {
            System.err.println("❌ Payment error: " + e.getMessage());
        }
    }
    
    // EXAMPLE 2: TOKEN MINTING
    private static void executeTokenMinting(QuickTxBuilder quickTxBuilder, AccountSetup accounts) {
        System.out.println("\n=== TOKEN MINTING ===");
        
        try {
            // Create minting policy
            Policy mintingPolicy = PolicyUtil.createMultiSigScriptAtLeastPolicy(
                "foundation-token-policy", 1, 1
            );
            
            Asset tokenAsset = new Asset("FoundationToken", BigInteger.valueOf(1000000));
            
            Tx mintTx = new Tx()
                .mintAssets(mintingPolicy.getPolicyScript(), tokenAsset, accounts.receiver.baseAddress())
                .attachMetadata(MessageMetadata.create()
                    .add("name", "Foundation Token")
                    .add("description", "Example token from foundation tutorial")
                    .add("ticker", "FOUND")
                    .add("decimals", 6))
                .from(accounts.sender.baseAddress());
            
            Result<String> result = quickTxBuilder.compose(mintTx)
                .withSigner(SignerProviders.signerFrom(accounts.sender))
                .withSigner(SignerProviders.signerFrom(mintingPolicy))
                .completeAndWait(System.out::println);
            
            if (result.isSuccessful()) {
                System.out.println("✅ Token minting successful!");
                System.out.println("Token: " + tokenAsset.getValue() + " " + tokenAsset.getName());
                System.out.println("Policy ID: " + mintingPolicy.getPolicyId());
                System.out.println("TxHash: " + result.getValue());
            } else {
                System.err.println("❌ Minting failed: " + result.getResponse());
            }
            
        } catch (Exception e) {
            System.err.println("❌ Minting error: " + e.getMessage());
        }
    }
    
    // EXAMPLE 3: MULTI-SIGNATURE TRANSACTION
    private static void executeMultiSigTransaction(QuickTxBuilder quickTxBuilder, AccountSetup accounts) {
        System.out.println("\n=== MULTI-SIGNATURE TRANSACTION ===");
        
        try {
            // Create 2-of-3 multisig script
            NativeScript multiSigScript = new ScriptAtLeast(2)
                .addNativeScript(new ScriptPubkey(accounts.sender.hdKeyPair().getPublicKey()))
                .addNativeScript(new ScriptPubkey(accounts.receiver.hdKeyPair().getPublicKey()))
                .addNativeScript(new ScriptPubkey(accounts.feePayer.hdKeyPair().getPublicKey()));
            
            String scriptAddress = AddressProvider.getEntAddress(multiSigScript, Networks.testnet()).toBech32();
            Amount fundingAmount = Amount.ada(10.0);
            
            // First transaction: Fund the multisig address
            Tx fundingTx = new Tx()
                .payToAddress(scriptAddress, fundingAmount)
                .from(accounts.sender.baseAddress());
            
            Result<String> fundingResult = quickTxBuilder.compose(fundingTx)
                .withSigner(SignerProviders.signerFrom(accounts.sender))
                .completeAndWait(System.out::println);
            
            if (fundingResult.isSuccessful()) {
                System.out.println("✅ Multisig address funded!");
                System.out.println("Script Address: " + scriptAddress);
                System.out.println("Funding TxHash: " + fundingResult.getValue());
                
                // Second transaction: Spend from multisig (requires 2 signatures)
                // Note: In practice, you'd wait for confirmation before spending
                System.out.println("📝 Multisig spending would require 2 of 3 signatures");
                System.out.println("📝 Signers needed: sender + receiver OR sender + feePayer OR receiver + feePayer");
                
            } else {
                System.err.println("❌ Multisig funding failed: " + fundingResult.getResponse());
            }
            
        } catch (Exception e) {
            System.err.println("❌ Multisig error: " + e.getMessage());
        }
    }
    
    // EXAMPLE 4: SCRIPT INTERACTION
    private static void executeScriptInteraction(QuickTxBuilder quickTxBuilder, AccountSetup accounts) {
        System.out.println("\n=== SCRIPT INTERACTION ===");
        
        try {
            // Simulate script address (in practice, load from compiled Plutus script)
            String scriptAddress = "addr_test1wpag6x4vv7x7lzm9pq3lam25yhsf9x7y6k5jhhce";
            Amount lockAmount = Amount.ada(5.0);
            
            // Create datum for locking funds
            PlutusData lockDatum = PlutusData.newConstr(0, Arrays.asList(
                PlutusData.newBytes("unlock_key".getBytes()),
                PlutusData.newInteger(BigInteger.valueOf(System.currentTimeMillis()))
            ));
            
            // Lock funds in script
            Tx lockTx = new Tx()
                .payToContract(scriptAddress, lockAmount, lockDatum)
                .from(accounts.sender.baseAddress());
            
            Result<String> lockResult = quickTxBuilder.compose(lockTx)
                .withSigner(SignerProviders.signerFrom(accounts.sender))
                .completeAndWait(System.out::println);
            
            if (lockResult.isSuccessful()) {
                System.out.println("✅ Funds locked in script!");
                System.out.println("Script Address: " + scriptAddress);
                System.out.println("Locked Amount: " + lockAmount);
                System.out.println("Lock TxHash: " + lockResult.getValue());
                
                System.out.println("📝 To unlock, you would need:");
                System.out.println("📝 - The script CBOR");
                System.out.println("📝 - Correct redeemer data");
                System.out.println("📝 - Script execution within budget");
                
            } else {
                System.err.println("❌ Script locking failed: " + lockResult.getResponse());
            }
            
        } catch (Exception e) {
            System.err.println("❌ Script interaction error: " + e.getMessage());
        }
    }
    
    // Helper class for account management
    private static class AccountSetup {
        final Account sender;
        final Account receiver;
        final Account feePayer;
        
        AccountSetup(Account sender, Account receiver, Account feePayer) {
            this.sender = sender;
            this.receiver = receiver;
            this.feePayer = feePayer;
        }
    }
}