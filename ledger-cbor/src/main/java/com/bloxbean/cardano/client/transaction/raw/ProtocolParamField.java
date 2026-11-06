package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.spec.Era;

import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.transaction.raw.FieldType.COIN;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.EX_UNITS;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.NONCE;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.NONNEGATIVE_INTERVAL;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.OPTIONAL_NONNEGATIVE_INTERVAL;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.OPTIONAL_UINT32;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.POSITIVE_INTERVAL;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.POSITIVE_UINT32;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.UINT;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.UINT16;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.UINT32;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.UNIT_INTERVAL;

/**
 * The fields of the {@code protocol_param_update} rule, read from the pinned CDDL
 * ({@code eras/*}{@code /impl/cddl/data}). Every field is optional. A key has a row for each era range with its own
 * type, and key 17 changes meaning at Babbage: coins per UTxO word (8 bytes) in Alonzo, coins per UTxO byte from
 * Babbage. The CDDL types both {@code coin} and calls both "ada per utxo byte"; the ledger at the pin decodes key 17 as
 * {@code CoinPerWord} in Alonzo ({@code Alonzo/PParams.hs}, {@code PParamUpdate 17 ppuCoinsPerUTxOWordL}) and
 * {@code CoinPerByte} from Babbage ({@code Babbage/PParams.hs}, {@code PParamUpdate 17 ppuCoinsPerUTxOByteL}). Keys 34
 * to 56 are new in Dijkstra ({@code Dijkstra/PParams.hs}); key 53 is {@code nil / uint .size 4}
 * ({@code encodeNullStrictMaybe}). Their rows are experimental: Dijkstra is not final; this may change in any release.
 */
public enum ProtocolParamField {
    // key, type, from, until
    MIN_FEE_A_UINT(0, UINT, Era.Shelley, Era.Babbage),
    MIN_FEE_A(0, COIN, Era.Conway, null),
    MIN_FEE_B_UINT(1, UINT, Era.Shelley, Era.Babbage),
    MIN_FEE_B(1, COIN, Era.Conway, null),
    MAX_BLOCK_BODY_SIZE(2, UINT32, Era.Shelley, null),
    MAX_TX_SIZE(3, UINT32, Era.Shelley, null),
    MAX_BLOCK_HEADER_SIZE(4, UINT16, Era.Shelley, null),
    KEY_DEPOSIT(5, COIN, Era.Shelley, null),
    POOL_DEPOSIT(6, COIN, Era.Shelley, null),
    MAX_EPOCH(7, UINT32, Era.Shelley, null),
    N_OPT(8, UINT16, Era.Shelley, null),
    POOL_PLEDGE_INFLUENCE(9, NONNEGATIVE_INTERVAL, Era.Shelley, null),
    EXPANSION_RATE(10, UNIT_INTERVAL, Era.Shelley, null),
    TREASURY_GROWTH_RATE(11, UNIT_INTERVAL, Era.Shelley, null),
    DECENTRALIZATION(12, UNIT_INTERVAL, Era.Shelley, Era.Alonzo),
    EXTRA_ENTROPY(13, NONCE, Era.Shelley, Era.Alonzo),
    PROTOCOL_VERSION(14, FieldType.PROTOCOL_VERSION, Era.Shelley, Era.Babbage),
    MIN_UTXO_VALUE(15, COIN, Era.Shelley, Era.Mary),
    MIN_POOL_COST(16, COIN, Era.Shelley, null),
    COINS_PER_UTXO_WORD(17, COIN, Era.Alonzo, Era.Alonzo),
    COINS_PER_UTXO_BYTE(17, COIN, Era.Babbage, null),
    COST_MODELS(18, FieldType.COST_MODELS, Era.Alonzo, null),
    EXECUTION_COSTS(19, FieldType.EX_UNIT_PRICES, Era.Alonzo, null),
    MAX_TX_EX_UNITS(20, EX_UNITS, Era.Alonzo, null),
    MAX_BLOCK_EX_UNITS(21, EX_UNITS, Era.Alonzo, null),
    MAX_VALUE_SIZE(22, UINT32, Era.Alonzo, null),
    COLLATERAL_PERCENTAGE(23, UINT16, Era.Alonzo, null),
    MAX_COLLATERAL_INPUTS(24, UINT16, Era.Alonzo, null),
    POOL_VOTING_THRESHOLDS(25, FieldType.POOL_VOTING_THRESHOLDS, Era.Conway, null),
    DREP_VOTING_THRESHOLDS(26, FieldType.DREP_VOTING_THRESHOLDS, Era.Conway, null),
    MIN_COMMITTEE_SIZE(27, UINT16, Era.Conway, null),
    COMMITTEE_TERM_LIMIT(28, UINT32, Era.Conway, null),
    GOV_ACTION_LIFETIME(29, UINT32, Era.Conway, null),
    GOV_ACTION_DEPOSIT(30, COIN, Era.Conway, null),
    DREP_DEPOSIT(31, COIN, Era.Conway, null),
    DREP_INACTIVITY(32, UINT32, Era.Conway, null),
    MIN_FEE_REF_SCRIPT_COST_PER_BYTE(33, NONNEGATIVE_INTERVAL, Era.Conway, null),
    MAX_REF_SCRIPT_SIZE_PER_BLOCK(34, UINT32, Era.Dijkstra, null),
    MAX_REF_SCRIPT_SIZE_PER_TX(35, UINT32, Era.Dijkstra, null),
    REF_SCRIPT_COST_STRIDE(36, POSITIVE_UINT32, Era.Dijkstra, null),
    REF_SCRIPT_COST_MULTIPLIER(37, POSITIVE_INTERVAL, Era.Dijkstra, null),
    MAX_PLEDGE_LEVERAGE(38, OPTIONAL_NONNEGATIVE_INTERVAL, Era.Dijkstra, null),
    MIN_POOL_MARGIN(39, UNIT_INTERVAL, Era.Dijkstra, null),
    LEIOS_ANNOUNCEMENT_PERIOD_LENGTH(40, UINT32, Era.Dijkstra, null),
    LEIOS_VOTE_PERIOD_LENGTH(41, UINT32, Era.Dijkstra, null),
    LEIOS_DIFFUSION_PERIOD_LENGTH(42, UINT32, Era.Dijkstra, null),
    LEIOS_COMMITTEE_SIZE(43, UINT16, Era.Dijkstra, null),
    LEIOS_QUORUM_STAKE_THRESHOLD(44, UNIT_INTERVAL, Era.Dijkstra, null),
    MAX_ENDORSER_BLOCK_REFERENCES_SIZE(45, UINT32, Era.Dijkstra, null),
    MAX_ENDORSER_BLOCK_TXS_SIZE(46, UINT32, Era.Dijkstra, null),
    MAX_ENDORSER_BLOCK_EX_UNITS(47, EX_UNITS, Era.Dijkstra, null),
    MAX_REF_SCRIPT_SIZE_PER_ENDORSER_BLOCK(48, UINT32, Era.Dijkstra, null),
    PERAS_MIN_CANDIDATE_BLOCK_AGE(49, UINT32, Era.Dijkstra, null),
    PERAS_HEALING_FACTOR(50, POSITIVE_INTERVAL, Era.Dijkstra, null),
    PERAS_CERT_BOOST(51, UINT16, Era.Dijkstra, null),
    PERAS_TARGET_COMMITTEE_SIZE(52, UINT16, Era.Dijkstra, null),
    PERAS_BOOTSTRAP_ROUND(53, OPTIONAL_UINT32, Era.Dijkstra, null),
    PERAS_QUORUM_THRESHOLD_SAFETY_MARGIN(54, UNIT_INTERVAL, Era.Dijkstra, null),
    REF_INPUTS_COST_PER_MULTI_ASSET_POLICY(55, COIN, Era.Dijkstra, null),
    REF_INPUTS_COST_PER_DATUM_BYTE(56, COIN, Era.Dijkstra, null);

    /** The {@code protocol_param_update} rule, a keyed record. */
    public static final RecordSpec RECORD;

    static {
        List<FieldSpec> rows = new ArrayList<>();
        for (ProtocolParamField field : values())
            rows.add(field.spec);
        RECORD = RecordSpec.keyed("protocol_param_update", rows);
    }

    private final FieldSpec spec;

    ProtocolParamField(int key, FieldType type, Era from, Era until) {
        this.spec = FieldSpec.of(key, type, false, from, until);
    }

    /** @return the field's key */
    public long key() {
        return spec.key();
    }

    /** @return the field's row */
    public FieldSpec spec() {
        return spec;
    }
}
