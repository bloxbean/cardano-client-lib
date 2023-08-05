Output output = Output.builder()
        .address(receiverAddress)
        .assetName(LOVELACE)
        .qty(adaToLovelace(2.1))
        .build();

Output output2 = Output.builder()
        .address(receiverAddress2)
        .assetName(LOVELACE)
        .qty(adaToLovelace(5))
        .build();

TxBuilder txBuilder = output.outputBuilder()
        .and(output2.outputBuilder())
        .buildInputs(InputBuilders.createFromSender(senderAddress, senderAddress))
        .andThen(BalanceTxBuilders.balanceTx(senderAddress, 1));

Transaction signedTransaction = TxBuilderContext.init(utxoSupplier, protocolParamsSupplier)
        .buildAndSign(txBuilder, SignerProviders.signerFrom(sender));
