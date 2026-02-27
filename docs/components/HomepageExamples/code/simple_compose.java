QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
Result<String> result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.signerFrom(sender1))
        .completeAndWait(System.out::println);
