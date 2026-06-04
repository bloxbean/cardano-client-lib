package com.bloxbean.cardano.client.annotation.devnet.plutus;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.LockUnlockValidatorExtender;

/**
 * Drives {@link MapDatumDevnetTest}. The datum carries a
 * {@code Pairs<ByteArray, Int>} field which Aiken emits as CIP-57
 * {@code dataType: "map"} — exercises Map CBOR encoding end-to-end (key
 * sort order, definite-length encoding, and the bytes-keyed lookup the
 * validator performs on chain).
 */
@Blueprint(fileInResources = "blueprint/map_datum/plutus.json",
        packageName = "com.bloxbean.cardano.client.annotation.devnet.plutus")
@ExtendWith(LockUnlockValidatorExtender.class)
public interface MapDatumDevnetBlueprint {
}
