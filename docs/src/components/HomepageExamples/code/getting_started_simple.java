/**
 * Getting Started - Simple Example
 * 
 * This is the simplest possible example to get you started with
 * Cardano Client Library. It demonstrates basic setup and a simple payment.
 */

public class GettingStartedExample {
    
    public static void main(String[] args) {
        try {
            // Step 1: Setup your API key (get from Blockfrost.io)
            String apiKey = System.getenv("CARDANO_API_KEY");
            if (apiKey == null) {
                System.err.println("Please set CARDANO_API_KEY environment variable");
                System.err.println("Get your API key from: https://blockfrost.io");
                return;
            }
            
            // Step 2: Initialize backend service (Blockfrost testnet)
            BackendService backendService = new BFBackendService(
                "https://cardano-preview.blockfrost.io/api/v0/", 
                apiKey
            );
            
            // Step 3: Create QuickTx builder
            QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
            
            // Step 4: Create an account from mnemonic
            String mnemonic = "your twelve word mnemonic phrase goes here for testing purposes";
            Account senderAccount = new Account(Networks.testnet(), mnemonic);
            
            System.out.println("Your address: " + senderAccount.baseAddress());
            System.out.println("Make sure this address has some test ADA!");
            
            // Step 5: Create a simple payment transaction
            String receiverAddress = "addr_test1qr..."; // Replace with real address
            Amount paymentAmount = Amount.ada(1.5); // Send 1.5 ADA
            
            Tx paymentTx = new Tx()
                .payToAddress(receiverAddress, paymentAmount)
                .from(senderAccount.baseAddress());
            
            // Step 6: Submit the transaction
            System.out.println("Sending " + paymentAmount + " to " + receiverAddress);
            
            Result<String> result = quickTxBuilder.compose(paymentTx)
                .withSigner(SignerProviders.signerFrom(senderAccount))
                .completeAndWait(System.out::println); // This shows transaction progress
            
            // Step 7: Check the result
            if (result.isSuccessful()) {
                System.out.println("🎉 Payment successful!");
                System.out.println("Transaction hash: " + result.getValue());
                System.out.println("View on explorer: https://preview.cardanoscan.io/transaction/" + result.getValue());
            } else {
                System.err.println("😞 Payment failed: " + result.getResponse());
                
                // Common issues and solutions
                System.err.println("\nCommon solutions:");
                System.err.println("- Make sure your address has enough ADA (including fees)");
                System.err.println("- Check that receiver address is valid");
                System.err.println("- Verify your API key is correct");
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            
            // Help with common setup issues
            if (e.getMessage().contains("Unauthorized")) {
                System.err.println("❌ API key issue. Check your CARDANO_API_KEY");
            } else if (e.getMessage().contains("Insufficient")) {
                System.err.println("❌ Not enough ADA. Get test ADA from faucet:");
                System.err.println("   https://docs.cardano.org/cardano-testnet/tools/faucet");
            } else {
                System.err.println("❌ Check the full error above for details");
            }
        }
    }
}

/*
 * QUICK SETUP CHECKLIST:
 * 
 * 1. Get API key from https://blockfrost.io (free)
 * 2. Set environment variable: export CARDANO_API_KEY="your_key_here"
 * 3. Generate a test wallet mnemonic (or use existing)
 * 4. Get test ADA from the faucet
 * 5. Update the receiver address in the code
 * 6. Run this example!
 * 
 * WHAT THIS EXAMPLE DOES:
 * - Connects to Cardano testnet via Blockfrost
 * - Creates an account from your mnemonic
 * - Builds a simple ADA payment transaction
 * - Submits it to the network
 * - Shows you the transaction hash
 * 
 * NEXT STEPS:
 * - Try the foundation_complete_example.java for advanced features
 * - Explore token minting, multi-sig, and smart contracts
 * - Read the documentation at https://cardano-client.io
 */