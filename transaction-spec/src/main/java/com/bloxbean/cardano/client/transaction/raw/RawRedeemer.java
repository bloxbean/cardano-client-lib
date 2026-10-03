package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;

/**
 * A redeemer as received, from either form of witness field 5: the array {@code [tag, index, data, ex_units]} or the
 * Conway map entry {@code [tag, index] => [data, ex_units]}.
 *
 * @param tag     the purpose: 0 spend, 1 mint, 2 cert, 3 reward, 4 voting, 5 proposing
 * @param index   the index of the item the redeemer is for
 * @param data    the redeemer data, as encoded (see {@link RawDatum} to hash or decode it)
 * @param exUnits the execution units {@code [mem, steps]}, as encoded
 */
public record RawRedeemer(int tag, long index, CborSpan data, CborSpan exUnits) {
}
