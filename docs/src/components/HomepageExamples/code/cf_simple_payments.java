// Composable Functions approach for multi-output payment
String senderAddress = "addr_test1qp...";
String receiverAddress = "addr_test1qr...";
String receiverAddress2 = "addr_test1qs...";

// Create multiple outputs using CF
Output output1 = Output.builder()
    .address(receiverAddress)
    .assetName(LOVELACE)
    .qty(adaToLovelace(2.1))
    .build();

Output output2 = Output.builder()
    .address(receiverAddress2)
    .assetName(LOVELACE)
    .qty(adaToLovelace(5.0))
    .build();

// Build transaction using Composable Functions
TxBuilder txBuilder = output1.outputBuilder()
    .and(output2.outputBuilder())
    .buildInputs(InputBuilders.createFromSender(senderAddress, senderAddress))
    .andThen(BalanceTxBuilders.balanceTx(senderAddress, 1));

try {
    // Initialize context and build transaction
    Transaction signedTransaction = TxBuilderContext.init(utxoSupplier, protocolParamsSupplier)
        .buildAndSign(txBuilder, SignerProviders.signerFrom(senderAccount));
        
    // Submit transaction
    Result<String> result = transactionService.submitTransaction(signedTransaction.serialize());
    
    if (result.isSuccessful()) {
        System.out.println("CF multi-payment successful!");
        System.out.println("Sent: 2.1 + 5.0 = 7.1 ADA");
        System.out.println("Transaction: " + result.getValue());
    } else {
        System.err.println("CF payment failed: " + result.getResponse());
    }
} catch (Exception e) {
    System.err.println("Transaction building failed: " + e.getMessage());
}
