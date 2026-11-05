package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;

import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A move_instantaneous_reward as received, {@code [0 / 1, {* stake_credential => delta_coin} / coin]}: the funds move
 * to stake credentials, or to the other accounting pot.
 */
public final class RawMir {
    static final RecordSpec RECORD = RecordSpec.positional("move_instantaneous_reward", FieldType.UINT,
            FieldType.MIR_TARGET);

    private final PositionalRecord record;
    private final boolean lenient;

    private RawMir(PositionalRecord record, boolean lenient) {
        this.record = record;
        this.lenient = lenient;
    }

    static RawMir read(CborSpan span, boolean lenient) {
        return new RawMir(PositionalRecord.of(RECORD, span, !lenient), lenient);
    }

    /**
     * @return the pot the funds are drawn from, as encoded: 0 the reserves, 1 the treasury
     */
    public int pot() {
        return RawItems.smallUnsigned(record.value(FieldType.UINT));
    }

    /**
     * @return whether the funds move to stake credentials ({@link #targets()}) and not to the other pot
     * ({@link #coin()})
     */
    public boolean movesToCredentials() {
        return target().majorType() == 5;
    }

    /**
     * @return the stake credentials and their delta coins, in encoded order with any repeated key kept
     */
    public List<Map.Entry<RawCredential, BigInteger>> targets() {
        List<Map.Entry<RawCredential, BigInteger>> targets = new ArrayList<>();
        for (Map.Entry<CborSpan, CborSpan> entry : RawItems.map(target(), "stake credential map")) {
            targets.add(new AbstractMap.SimpleImmutableEntry<>(RawCredential.of(entry.getKey()),
                    RawItems.integer(entry.getValue(), lenient)));
        }
        return targets;
    }

    /**
     * @return the coin that moves to the other pot
     */
    public BigInteger coin() {
        return RawItems.integer(target(), lenient);
    }

    private CborSpan target() {
        return record.value(FieldType.MIR_TARGET);
    }
}
