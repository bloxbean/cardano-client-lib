//Create a policy
Policy policy
        = PolicyUtil.createMultiSigScriptAtLeastPolicy("test_policy", 1, 1);
String assetName = "MyAsset";
BigInteger qty = BigInteger.valueOf(1000);

//Define mint Tx
Tx tx = new Tx()
        .mintAssets(policy.getPolicyScript(),
                new Asset(assetName, qty), receiver)
        .attachMetadata(MessageMetadata.create().add("Sample Metadata"))
        .from(sender1Addr);

//Compose and sign Tx
Result<String> result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.signerFrom(sender1))
        .withSigner(SignerProviders.signerFrom(policy))
        .complete();
