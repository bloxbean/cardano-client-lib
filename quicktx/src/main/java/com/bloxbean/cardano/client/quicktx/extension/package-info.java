/**
 * QuickTx extension SPI: typed extension intents, build participants and TxPlan metadata.
 *
 * <p><b>Experimental.</b> This SPI has one implementation so far (the programmable-token module)
 * and may change incompatibly in a minor release until it is promoted. The same applies to the
 * extension entry points outside this package: {@code QuickTxBuilder.withExtension},
 * {@code TxPlan.withExtension}/{@code getExtensions}, {@code TxPlanCodec} and
 * {@code PlutusDataValue}.</p>
 */
package com.bloxbean.cardano.client.quicktx.extension;
