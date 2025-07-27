// Submit a pre-built transaction using Composable Functions
try {
    // Serialize and submit the signed transaction
    Result<String> result = transactionService.submitTransaction(signedTransaction.serialize());
    
    // Handle submission result
    if (result.isSuccessful()) {
        System.out.println("Transaction submitted successfully!");
        System.out.println("Transaction hash: " + result.getValue());
        
        // Optional: Wait for confirmation
        System.out.println("Waiting for confirmation...");
    } else {
        System.err.println("Transaction submission failed: " + result.getResponse());
        
        // Check if it's a temporary network issue
        if (result.getResponse().contains("network")) {
            System.out.println("Consider retrying after a few seconds");
        }
    }
} catch (Exception e) {
    System.err.println("Submission error: " + e.getMessage());
    throw e; // Re-throw for higher-level handling
}
