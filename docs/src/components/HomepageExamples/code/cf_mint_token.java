// Token minting using Composable Functions approach
String senderAddress = "addr_test1qp...";
String receiverAddress = "addr_test1qr...";

// Create minting policy
Policy mintingPolicy = PolicyUtil.createMultiSigScriptAllPolicy("my-token-policy", 1);
Asset tokenAsset = new Asset("TestCoin", BigInteger.valueOf(50000));

// Create multi-asset for minting
MultiAsset multiAsset = MultiAsset.builder()
    .policyId(mintingPolicy.getPolicyId())
    .assets(List.of(tokenAsset))
    .build();

// Define output with native token
Output tokenOutput = Output.builder()
    .address(receiverAddress)
    .policyId(mintingPolicy.getPolicyId())
    .assetName(tokenAsset.getName())
    .qty(BigInteger.valueOf(50000))
    .build();

// Create metadata for the token
Metadata tokenMetadata = MessageMetadata.create()
    .add("Token Name", "Test Coin")
    .add("Description", "A test token using Composable Functions");

// Build minting transaction using CF
TxBuilder mintTxBuilder = tokenOutput.mintOutputBuilder()
    .buildInputs(InputBuilders.createFromSender(senderAddress, senderAddress))
    .andThen(MintCreators.mintCreator(mintingPolicy.getPolicyScript(), multiAsset))
    .andThen(AuxDataProviders.metadataProvider(tokenMetadata))
    .andThen(BalanceTxBuilders.balanceTx(senderAddress, 2));

try {
    // Build and sign transaction
    Transaction signedTransaction = TxBuilderContext.init(utxoSupplier, protocolParamsSupplier)
        .buildAndSign(mintTxBuilder, signerFrom(senderAccount).andThen(signerFrom(mintingPolicy)));
    
    // Submit transaction
    Result<String> result = transactionService.submitTransaction(signedTransaction.serialize());
    
    if (result.isSuccessful()) {
        System.out.println("CF token minting successful!");
        System.out.println("Minted: " + tokenAsset.getValue() + " " + tokenAsset.getName());
        System.out.println("Policy ID: " + mintingPolicy.getPolicyId());
        System.out.println("Transaction: " + result.getValue());
    } else {
        System.err.println("CF minting failed: " + result.getResponse());
    }
} catch (Exception e) {
    System.err.println("Minting error: " + e.getMessage());
}
