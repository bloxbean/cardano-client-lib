// Unlock funds from a Plutus script address
Utxo scriptUtxo = findUtxoAtScript(scriptAddress);
PlutusData unlockRedeemer = PlutusData.newBytes("unlock".getBytes());
Amount unlockAmount = Amount.ada(5.0);

// Create script transaction to unlock funds
ScriptTx unlockTx = new ScriptTx()
    .collectFrom(scriptUtxo, unlockRedeemer)
    .payToAddress(beneficiaryAddress, unlockAmount)
    .attachSpendingValidator(contractValidator);

// Submit with fee payer and required signers
Result<String> result = quickTxBuilder.compose(unlockTx)
    .feePayer(feePayerAddress)
    .withSigner(SignerProviders.signerFrom(feePayerAccount))
    .completeAndWait(System.out::println);

// Process result
if (result.isSuccessful()) {
    System.out.println("Script unlock successful!");
    System.out.println("Unlocked: " + unlockAmount + " to " + beneficiaryAddress);
} else {
    System.err.println("Script unlock failed: " + result.getResponse());
}
