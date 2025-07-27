// Pay to a Plutus script contract with datum
String senderAddress = "addr_test1qp...";
String scriptAddress = "addr_test1wp..."; // Script address
Amount lockAmount = Amount.ada(10.0);

// Create datum for the contract
PlutusData lockDatum = PlutusData.newConstr(0, Arrays.asList(
    PlutusData.newBytes("lock_key".getBytes()),
    PlutusData.newInteger(BigInteger.valueOf(System.currentTimeMillis()))
));

// Create transaction to lock funds in script
Tx contractPayment = new Tx()
    .payToContract(scriptAddress, lockAmount, lockDatum)
    .from(senderAddress);

// Submit transaction with error handling
Result<String> result = quickTxBuilder.compose(contractPayment)
    .withSigner(SignerProviders.signerFrom(senderAccount))
    .completeAndWait(System.out::println);

// Process result
if (result.isSuccessful()) {
    System.out.println("Contract payment successful!");
    System.out.println("Locked: " + lockAmount + " with datum");
    System.out.println("Transaction: " + result.getValue());
} else {
    System.err.println("Contract payment failed: " + result.getResponse());
}
