Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("policy-1", 1);
Asset asset = new Asset("TestCoin", BigInteger.valueOf(50000));

MultiAsset multiAsset = MultiAsset.builder()
        .policyId(policy.getPolicyId())
        .assets(List.of(asset))
        .build();

Output output = Output.builder()
        .address(receiverAddress)
        .policyId(policy.getPolicyId())
        .assetName(asset.getName())
        .qty(BigInteger.valueOf(50000))
        .build();

Metadata metadata = MessageMetadata.create()
        .add("Mint Test Coin");

TxBuilder txBuilder = output.mintOutputBuilder()
        .buildInputs(InputBuilders.createFromSender(senderAddress, senderAddress))
        .andThen(MintCreators.mintCreator(policy.getPolicyScript(), multiAsset))
        .andThen(AuxDataProviders.metadataProvider(metadata))
        .andThen(BalanceTxBuilders.balanceTx(senderAddress, 2));

Transaction signedTransaction = TxBuilderContext.init(utxoSupplier, protocolParamsSupplier)
        .buildAndSign(txBuilder, signerFrom(sender).andThen(signerFrom(policy)));
