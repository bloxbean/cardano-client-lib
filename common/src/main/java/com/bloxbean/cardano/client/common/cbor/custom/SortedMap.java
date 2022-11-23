package com.bloxbean.cardano.client.common.cbor.custom;

import co.nstant.in.cbor.model.Map;

/**
 * If the key sorting is done in the caller, this class can be used to preserve the sorting order during cbor serialization.
 * If this class is used instead of {@link Map}, {@link CustomMapEncoder} doesn't try to sort key again during serialization even if canonical = true in the encoder.
 */
public class SortedMap extends Map {

}
