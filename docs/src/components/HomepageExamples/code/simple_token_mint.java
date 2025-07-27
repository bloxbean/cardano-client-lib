// Create a minting policy (1-of-1 multi-sig)
Policy mintingPolicy = PolicyUtil.createMultiSigScriptAtLeastPolicy(
    "my_token_policy", 1, 1
);

// Define the token to mint
String assetName = "MyToken";
BigInteger quantity = BigInteger.valueOf(1000);
Asset tokenAsset = new Asset(assetName, quantity);

// Create minting transaction with metadata
Tx mintTx = new Tx()
    .mintAssets(mintingPolicy.getPolicyScript(), tokenAsset, receiverAddress)
    .attachMetadata(MessageMetadata.create()
        .add("Token Name", "My Custom Token")
        .add("Description", "A sample token for demonstration"))
    .from(minterAddress);

// Compose, sign and submit transaction
Result<String> result = quickTxBuilder.compose(mintTx)
    .withSigner(SignerProviders.signerFrom(minterAccount))
    .withSigner(SignerProviders.signerFrom(mintingPolicy))
    .completeAndWait();

// Check result
if (result.isSuccessful()) {
    System.out.println("Token minted! TxHash: " + result.getValue());
    System.out.println("Policy ID: " + mintingPolicy.getPolicyId());
} else {
    System.err.println("Minting failed: " + result.getResponse());
}
