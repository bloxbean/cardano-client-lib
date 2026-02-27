ScriptTx scriptTx = new ScriptTx()
        .collectFrom(utxo, redeemer)
        .payToAddress(receiver1, amount)
        .attachSpendingValidator(plutusScript);

Result<String> result = quickTxBuilder.compose(scriptTx)
        .feePayer(sender1Addr)
        .withSigner(SignerProviders.signerFrom(sender1))
        .completeAndWait(System.out::println);
