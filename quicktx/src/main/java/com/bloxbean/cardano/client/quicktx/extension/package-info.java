/**
 * QuickTx extension SPI: typed extension intents, build participants and TxPlan metadata.
 *
 * <p><b>Experimental.</b> This SPI has one implementation so far (the programmable-token module)
 * and may change incompatibly in a minor release until it is promoted. The same applies to the
 * extension entry points outside this package: {@code QuickTxBuilder.withExtension},
 * {@code TxPlan.withExtension}/{@code getExtensions}, {@code TxPlanCodec} and
 * {@code PlutusDataValue}.</p>
 *
 * <h2>Running under TxFlow</h2>
 * <p>TxFlow runs plans that use a registered extension without extension-specific code. An
 * extension works there when it meets these conditions:</p>
 * <ol>
 *   <li>Every chain read made while building ({@code prepare}, {@code afterBalance},
 *       {@code verify}) goes through {@link ExtensionBuildContext#getUtxoSupplier()}. Under TxFlow
 *       that supplier's address scans hide outputs earlier flow steps spent and show pending
 *       outputs of steps the current step is funded from; a private backend supplier does
 *       neither. Its exact lookups ({@code getTxOutput}) are not spent-filtered, so read
 *       spendable protocol state by address scan.</li>
 *   <li>Intents are pure data, and decoding then encoding them through {@code TxPlanCodec}
 *       reproduces the authored values.</li>
 *   <li>{@link QuickTxExtension#validateMetadata} and {@link ExtensionIntent#validate()} do no
 *       chain or network I/O; they run during validation and compilation.</li>
 *   <li>{@link QuickTxExtension#newBuildExtension} returns a fresh participant for each build.</li>
 * </ol>
 */
package com.bloxbean.cardano.client.quicktx.extension;
