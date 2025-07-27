---
description: Your first transaction using Cardano Client Lib - complete tutorial from setup to submission
sidebar_label: First Transaction
sidebar_position: 2
---

# Your First Transaction

Welcome to Cardano Client Lib! In this comprehensive tutorial, you'll learn how to send your first Ada transaction from start to finish. We'll use the **QuickTx API** - our recommended approach for transaction building.

## What You'll Learn

By the end of this tutorial, you'll know how to:
- ✅ Set up your development environment
- ✅ Create accounts and generate addresses  
- ✅ Configure a backend service
- ✅ Build a transaction using QuickTx API
- ✅ Submit your transaction to the network
- ✅ Handle errors and verify results

## Prerequisites

- Java 11 or higher
- Basic Java programming knowledge
- Access to a Cardano testnet faucet

:::tip
This tutorial uses **QuickTx API** - the recommended approach for most developers. It's intuitive, handles complexity automatically, and is perfect for learning Cardano development.
:::

## Step 1: Project Setup

First, ensure you have the required dependencies. Check the [Installation Guide](./installation.md) for detailed setup instructions.

**Quick setup for Maven:**

```xml
<dependency>
    <groupId>com.bloxbean.cardano</groupId>
    <artifactId>cardano-client-lib</artifactId>
    <version>0.6.6</version>
</dependency>
<dependency>
    <groupId>com.bloxbean.cardano</groupId>
    <artifactId>cardano-client-backend-blockfrost</artifactId>
    <version>0.6.6</version>
</dependency>
```

## Step 2: Choose Your Network and Provider

For this tutorial, we'll use:
- **Network**: Preprod testnet (safe for testing)
- **Backend**: Blockfrost (reliable and well-documented)

### Get a Blockfrost Project ID

1. Visit [blockfrost.io](https://blockfrost.io) and create a free account
2. Create a new project for **Cardano Preprod**
3. Copy your Project ID - you'll need it soon

:::info
**Why Blockfrost?** It provides reliable blockchain data with excellent uptime and comprehensive APIs. Perfect for development and production use.
:::

## Step 3: Create Your First Account

Let's start by creating a Cardano account. This will generate a mnemonic phrase and derive addresses for you.

```java
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;

public class FirstTransaction {
    public static void main(String[] args) {
        // Create a new testnet account
        Account senderAccount = new Account(Networks.testnet());
        
        // Get the base address where you can receive funds
        String senderAddress = senderAccount.baseAddress();
        
        // Get the mnemonic phrase (keep this secure!)
        String mnemonic = senderAccount.mnemonic();
        
        System.out.println("Sender Address: " + senderAddress);
        System.out.println("Mnemonic (keep secure): " + mnemonic);
        
        // We'll also need a receiver address for our transaction
        Account receiverAccount = new Account(Networks.testnet());
        String receiverAddress = receiverAccount.baseAddress();
        
        System.out.println("Receiver Address: " + receiverAddress);
    }
}
```

### Understanding the Output

When you run this code, you'll see something like:

```
Sender Address: addr_test1qr4z8k2ge3p8f7wqnpvk5t3jx2h8m9x...
Mnemonic (keep secure): abandon abandon abandon ... (24 words)
Receiver Address: addr_test1qp8h5t2mp7qjx4k9dv3n2m5x8p7r4s...
```

:::warning Security Note
**Never share your mnemonic phrase!** It controls access to your funds. In production, store it securely and never log it.
:::

## Step 4: Fund Your Account

Before you can send a transaction, you need some testnet Ada:

1. Copy your **Sender Address** from Step 3
2. Visit the [Cardano Preprod Faucet](https://docs.cardano.org/cardano-testnet/tools/faucet)
3. Request test Ada using your sender address
4. Wait a few minutes for the transaction to confirm

You can verify the funds arrived by checking your address on a Cardano explorer like [Preprod CardanoScan](https://preprod.cardanoscan.io/).

## Step 5: Set Up Backend Service

Now let's configure Blockfrost to communicate with the Cardano network:

```java
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;

public class FirstTransaction {
    public static void main(String[] args) {
        // ... previous account creation code ...
        
        // Configure Blockfrost backend
        String blockfrostProjectId = "preprod_YOUR_PROJECT_ID_HERE";
        BackendService backendService = new BFBackendService(
            Constants.BLOCKFROST_PREPROD_URL, 
            blockfrostProjectId
        );
        
        System.out.println("Backend service configured successfully!");
    }
}
```

:::tip
Replace `"preprod_YOUR_PROJECT_ID_HERE"` with your actual Blockfrost Project ID from Step 2.
:::

## Step 6: Build Your Transaction with QuickTx

Now for the exciting part - building your first transaction! We'll use the QuickTx API to send 5 Ada to the receiver address:

```java
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.cip.cip20.MessageMetadata;
import com.bloxbean.cardano.client.function.helper.SignerProviders;

public class FirstTransaction {
    public static void main(String[] args) {
        try {
            // ... previous setup code ...
            
            // Step 1: Create transaction with QuickTx
            Tx transaction = new Tx()
                .payToAddress(receiverAddress, Amount.ada(5.0))  // Send 5 Ada
                .attachMetadata(MessageMetadata.create()         // Add a message
                    .add("My first Cardano transaction!"))
                .from(senderAddress);                            // From our sender
            
            // Step 2: Build and submit transaction
            QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
            Result<String> result = quickTxBuilder
                .compose(transaction)                            // Compose the transaction
                .withSigner(SignerProviders.signerFrom(senderAccount))  // Sign it
                .completeAndWait(System.out::println);          // Submit and wait
            
            // Step 3: Check the result
            if (result.isSuccessful()) {
                System.out.println("🎉 Transaction successful!");
                System.out.println("Transaction ID: " + result.getValue());
                System.out.println("Check it on explorer: https://preprod.cardanoscan.io/transaction/" + result.getValue());
            } else {
                System.out.println("❌ Transaction failed: " + result.getResponse());
            }
            
        } catch (Exception e) {
            System.out.println("❌ Error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

### Understanding the QuickTx API

Let's break down what happened:

1. **`new Tx()`** - Creates a new transaction builder
2. **`.payToAddress(address, amount)`** - Specifies where to send funds  
3. **`.attachMetadata(metadata)`** - Adds optional message data
4. **`.from(address)`** - Specifies the sender address
5. **`QuickTxBuilder`** - Handles the complex transaction building
6. **`.withSigner(signer)`** - Provides signing capability
7. **`.completeAndWait()`** - Submits and waits for result

## Step 7: Complete Working Example

Here's the complete, runnable example:

```java
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.cip.cip20.MessageMetadata;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;

public class FirstTransaction {
    public static void main(String[] args) {
        try {
            System.out.println("🚀 Starting your first Cardano transaction...\n");
            
            // 1. Create accounts
            Account senderAccount = new Account(Networks.testnet());
            String senderAddress = senderAccount.baseAddress();
            
            Account receiverAccount = new Account(Networks.testnet());  
            String receiverAddress = receiverAccount.baseAddress();
            
            System.out.println("📋 Accounts created:");
            System.out.println("Sender: " + senderAddress);
            System.out.println("Receiver: " + receiverAddress);
            System.out.println("💡 Fund the sender address with testnet Ada before continuing!\n");
            
            // 2. Configure backend service
            String blockfrostProjectId = "preprod_YOUR_PROJECT_ID_HERE";
            BackendService backendService = new BFBackendService(
                Constants.BLOCKFROST_PREPROD_URL, 
                blockfrostProjectId
            );
            System.out.println("🔗 Backend service configured\n");
            
            // 3. Build transaction using QuickTx
            System.out.println("🔨 Building transaction...");
            Tx transaction = new Tx()
                .payToAddress(receiverAddress, Amount.ada(5.0))
                .attachMetadata(MessageMetadata.create()
                    .add("My first Cardano transaction!"))
                .from(senderAddress);
            
            // 4. Submit transaction
            System.out.println("📡 Submitting transaction...");
            QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
            Result<String> result = quickTxBuilder
                .compose(transaction)
                .withSigner(SignerProviders.signerFrom(senderAccount))
                .completeAndWait(status -> System.out.println("Status: " + status));
            
            // 5. Handle result
            if (result.isSuccessful()) {
                System.out.println("\n🎉 Transaction successful!");
                System.out.println("Transaction ID: " + result.getValue());
                System.out.println("Explorer: https://preprod.cardanoscan.io/transaction/" + result.getValue());
            } else {
                System.out.println("\n❌ Transaction failed: " + result.getResponse());
            }
            
        } catch (Exception e) {
            System.out.println("\n❌ Error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

## Step 8: Run and Verify

1. **Update the Project ID**: Replace `"preprod_YOUR_PROJECT_ID_HERE"` with your actual Blockfrost project ID
2. **Make sure your sender address has funds** from the testnet faucet  
3. **Run the program**
4. **Check the transaction** on the Cardano explorer using the provided link

### Expected Output

```
🚀 Starting your first Cardano transaction...

📋 Accounts created:
Sender: addr_test1qr4z8k2ge3p8f7wqnpvk5t3jx2h8m9x...
Receiver: addr_test1qp8h5t2mp7qjx4k9dv3n2m5x8p7r4s...
💡 Fund the sender address with testnet Ada before continuing!

🔗 Backend service configured

🔨 Building transaction...
📡 Submitting transaction...
Status: Transaction built successfully
Status: Transaction submitted

🎉 Transaction successful!
Transaction ID: 8f2e9d4c5b7a3e1f9c8d6e2a5b4f8c1d3e7a9b2c4f5d8e1c3a6b9d2e5f7c4a1b8e
Explorer: https://preprod.cardanoscan.io/transaction/8f2e9d4c5b7a3e1f9c8d6e2a5b4f8c1d3e7a9b2c4f5d8e1c3a6b9d2e5f7c4a1b8e
```

## Error Handling and Troubleshooting

### Common Issues and Solutions

#### 1. Insufficient Funds
```
❌ Transaction failed: Insufficient funds
```
**Solution**: Make sure your sender address has enough Ada (at least 6 Ada for this example - 5 to send + ~1 for fees).

#### 2. Invalid Project ID
```
❌ Error occurred: 403 Forbidden
```
**Solution**: Check your Blockfrost Project ID and make sure it's for the Preprod network.

#### 3. Network Connection Issues
```
❌ Error occurred: java.net.ConnectException
```
**Solution**: Check your internet connection and Blockfrost service status.

### Adding Better Error Handling

```java
try {
    // Transaction code here...
    Result<String> result = quickTxBuilder
        .compose(transaction)
        .withSigner(SignerProviders.signerFrom(senderAccount))
        .completeAndWait(status -> System.out.println("Status: " + status));
    
    if (result.isSuccessful()) {
        System.out.println("🎉 Success! TX ID: " + result.getValue());
    } else {
        // Handle different error types
        String errorMessage = result.getResponse();
        if (errorMessage.contains("insufficient")) {
            System.out.println("❌ Not enough funds. Please add more testnet Ada.");
        } else if (errorMessage.contains("forbidden")) {
            System.out.println("❌ API access denied. Check your Blockfrost project ID.");
        } else {
            System.out.println("❌ Transaction failed: " + errorMessage);
        }
    }
} catch (Exception e) {
    System.out.println("❌ Unexpected error: " + e.getMessage());
    // Log the full stack trace for debugging
    e.printStackTrace();
}
```

## What's Next?

Congratulations! 🎉 You've successfully sent your first Cardano transaction. Here are some next steps:

### Immediate Next Steps
- **Try different amounts**: Modify the `Amount.ada(5.0)` to send different amounts
- **Add multiple recipients**: Use `.payToAddress()` multiple times
- **Experiment with metadata**: Try different message metadata

### Explore More Features  
- **Native Tokens**: Learn to mint and transfer tokens (coming soon)
- **Smart Contracts**: Interact with Plutus contracts (coming soon)
- **Multi-signature**: Create multi-sig wallets (coming soon)
- **Staking**: Delegate to stake pools (coming soon)

### Advanced Topics
- **Composable Functions**: For more complex transaction patterns (coming soon)
- **Custom Backend**: Integrate with your own infrastructure (coming soon)
- **Production Best Practices**: Security and optimization (coming soon)

## Key Takeaways

✅ **QuickTx API is beginner-friendly** - Handles complexity automatically
✅ **Always use testnets for learning** - Safe and free to experiment  
✅ **Transaction building is declarative** - Describe what you want, not how to do it
✅ **Error handling is crucial** - Always check results and handle failures gracefully
✅ **Cardano supports rich metadata** - Add messages and structured data to transactions

## Need Help?

- **Documentation**: Check our API Reference for detailed information (coming soon)
- **Community**: Join our [Discord](https://discord.gg/JtQ54MSw6p) for support
- **Issues**: Report bugs on [GitHub](https://github.com/bloxbean/cardano-client-lib/issues)
- **Discussions**: Ask questions on [GitHub Discussions](https://github.com/bloxbean/cardano-client-lib/discussions)

---

**Ready for more?** Continue with [Installation Guide](./installation.md) for advanced setup options or explore [Choosing Your Path](./choosing-your-path.md) to learn about different transaction building approaches.