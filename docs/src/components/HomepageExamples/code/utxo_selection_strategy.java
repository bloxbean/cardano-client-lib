// Create transaction requiring custom UTXO selection
String senderAddress = "addr_test1qp...";
String receiverAddress = "addr_test1qr...";
Amount paymentAmount = Amount.ada(5.0);

Tx paymentTx = new Tx()
    .payToAddress(receiverAddress, paymentAmount)
    .from(senderAddress);

// Use custom UTXO selection strategy
UtxoSelectionStrategy randomSelection = new RandomImproveUtxoSelectionStrategy(
    new DefaultUtxoSupplier(backendService.getUtxoService())
);

// Build and submit with custom strategy
Result<String> result = quickTxBuilder.compose(paymentTx)
    .withSigner(SignerProviders.signerFrom(senderAccount))
    .withUtxoSelectionStrategy(randomSelection)
    .completeAndWait(System.out::println);

// Handle result with strategy info
if (result.isSuccessful()) {
    System.out.println("Payment with custom UTXO selection successful!");
    System.out.println("Strategy: RandomImprove");
    System.out.println("Transaction: " + result.getValue());
} else {
    System.err.println("Payment failed: " + result.getResponse());
}
