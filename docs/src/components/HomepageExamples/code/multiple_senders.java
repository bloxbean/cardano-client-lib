// Multiple senders contributing to a shared payment
String sender1Address = "addr_test1qp...";
String sender2Address = "addr_test1qr...";
String beneficiaryAddress = "addr_test1qs...";

// Transaction 1: Sender 1 contributes 1.5 ADA
Tx contribution1 = new Tx()
    .payToAddress(beneficiaryAddress, Amount.ada(1.5))
    .from(sender1Address);

// Transaction 2: Sender 2 contributes 2.5 ADA  
Tx contribution2 = new Tx()
    .payToAddress(beneficiaryAddress, Amount.ada(2.5))
    .from(sender2Address);

// Compose both transactions atomically
Result<String> result = quickTxBuilder.compose(contribution1, contribution2)
    .feePayer(sender1Address)  // Sender 1 pays fees
    .withSigner(SignerProviders.signerFrom(sender1Account))
    .withSigner(SignerProviders.signerFrom(sender2Account))
    .completeAndWait(System.out::println);

// Handle result
if (result.isSuccessful()) {
    System.out.println("Multi-sender payment completed!");
    System.out.println("Total sent: 4.0 ADA from 2 senders");
    System.out.println("Transaction: " + result.getValue());
} else {
    System.err.println("Payment failed: " + result.getResponse());
}
