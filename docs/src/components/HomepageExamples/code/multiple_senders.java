Tx tx1 = new Tx()
        .payToAddress(receiver1, Amount.ada(1.5))
        .from(sender1Addr);

Tx tx2 = new Tx()
        .payToAddress(receiver1, Amount.ada(1.5))
        .from(sender2Addr);

Result<String> result = quickTxBuilder.compose(tx1, tx2)
        .withSigner(SignerProviders.signerFrom(sender1))
        .withSigner(SignerProviders.signerFrom(sender2))
        .completeAndWait(System.out::println);
