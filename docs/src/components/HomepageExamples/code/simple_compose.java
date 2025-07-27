// Initialize QuickTx builder with backend service
QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

// Compose, sign and submit transaction
Result<String> result = quickTxBuilder.compose(paymentTx)
    .withSigner(SignerProviders.signerFrom(senderAccount))
    .completeAndWait(System.out::println);

// Handle the result
if (result.isSuccessful()) {
    System.out.println("Transaction successful: " + result.getValue());
} else {
    System.err.println("Transaction failed: " + result.getResponse());
}
