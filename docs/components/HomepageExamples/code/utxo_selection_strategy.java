UtxoSelectionStrategy randomSelectionStg =
        new RandomImproveUtxoSelectionStrategy(
                new DefaultUtxoSupplier(backendService.getUtxoService()))

Result<String> result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.signerFrom(sender1))
        .withUtxoSelectionStrategy(randomSelectionStg)
        .complete();
