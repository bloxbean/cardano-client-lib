package com.bloxbean.cardano.client.tools.chainparse;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.plutus.spec.Language;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.Script;
import com.bloxbean.cardano.client.transaction.raw.RawBlock;
import com.bloxbean.cardano.client.transaction.raw.RawDatum;
import com.bloxbean.cardano.client.transaction.raw.RawOutput;
import com.bloxbean.cardano.client.transaction.raw.RawRedeemer;
import com.bloxbean.cardano.client.transaction.raw.RawScript;
import com.bloxbean.cardano.client.transaction.raw.RawTx;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScriptEvaluator;
import com.bloxbean.cardano.client.transaction.util.TransactionUtil;
import com.bloxbean.cardano.client.util.HexUtil;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongToIntFunction;

/** All per-block and per-transaction checks. One instance per worker thread; State and Issues are shared. */
final class Checker {
    private static final Language[] LANGS = {Language.PLUTUS_V1, Language.PLUTUS_V2, Language.PLUTUS_V3};

    final State state;
    private final Issues issues;
    private final CostModels costModels;      // null: script-data-hash check skipped
    private final LongToIntFunction epochOfSlot;

    // current block context
    long slot;
    long blockNo;
    String blockHash;
    String era;

    Checker(State state, Issues issues, CostModels costModels, LongToIntFunction epochOfSlot) {
        this.state = state;
        this.issues = issues;
        this.costModels = costModels;
        this.epochOfSlot = epochOfSlot;
    }

    /** Checks one block; Byron blocks are counted and skipped. */
    void block(byte[] bytes) {
        CborSpan top = CborSpan.of(bytes);
        if (top.tag() == 24) top = top.embedded();
        long eraIndex = top.get(0).asLong();
        if (eraIndex <= 1) {
            state.inc("Byron", "skipped_blocks", 1);
            return;
        }
        CborSpan blk = top.get(1);
        CborSpan header = blk.get(0);
        CborSpan hb = header.get(0);
        blockNo = hb.get(0).asLong();
        slot = hb.get(1).asLong();
        blockHash = HexUtil.encodeHexString(Blake2bUtil.blake2bHash256(header.bytes()));
        int hbSize = hb.size();
        byte[] headerBodyHash = hb.get(hbSize == 15 ? 8 : 7).byteString(); // TPraos 15 items, Praos 10
        Era expected;
        try {
            expected = Era.fromValue((int) eraIndex);
        } catch (Exception e) {
            era = "era" + eraIndex;
            issue("unknown_era", null, "era index " + eraIndex, null, bytes);
            state.countBlock();
            return;
        }
        era = expected.name();
        state.inc(era, "blocks", 1);

        RawBlock rb;
        try {
            rb = RawBlock.of(bytes);
        } catch (Throwable t) {
            issue("rawblock_of", null, trace(t), null, bytes);
            state.countBlock();
            return;
        }
        if (rb.era() != expected)
            issue("era_detection", null, "envelope " + expected + " RawBlock " + rb.era(), null, null);
        if ((hbSize == 15) != (expected.getValue() <= 5))
            issue("header_shape", null, "header body has " + hbSize + " items in " + expected, null, null);
        try {
            if (!Arrays.equals(rb.bodyHash(), headerBodyHash))
                issue("body_hash", null, "header " + hex(headerBodyHash) + " computed " + hex(rb.bodyHash()), null, bytes);
        } catch (Throwable t) {
            issue("body_hash_error", null, trace(t), null, bytes);
        }

        // independent tx count alignment
        List<CborSpan> bodies = blk.get(1).items();
        int wits = blk.get(2).size();
        int n = bodies.size();
        if (rb.txCount() != n || wits != n)
            issue("tx_count", null, "bodies " + n + " witnesses " + wits + " RawBlock " + rb.txCount(), null, null);
        Set<Long> auxKeys = new HashSet<>();
        for (Map.Entry<CborSpan, CborSpan> e : blk.get(3).entries()) {
            long k = e.getKey().asLong();
            auxKeys.add(k);
            if (k < 0 || k >= n)
                issue("aux_index", null, "aux key " + k + " of " + n, null, null);
        }
        int[] invalid = rb.invalidTxIndexes();
        if (blk.size() == 5) {
            List<CborSpan> inv = blk.get(4).items();
            long[] own = inv.stream().mapToLong(CborSpan::asLong).toArray();
            if (own.length != invalid.length || !Arrays.equals(own, Arrays.stream(invalid).asLongStream().toArray()))
                issue("invalid_tx_indexes", null, "raw " + Arrays.toString(own) + " RawBlock " + Arrays.toString(invalid), null, null);
        }
        state.inc(era, "txs", n);
        state.inc(era, "invalid_txs", invalid.length);

        for (int i = 0; i < n; i++) {
            try {
                tx(rb, i, bodies.get(i), auxKeys.contains((long) i));
            } catch (Throwable t) {
                issue("tx_harness_error", null, "tx index " + i + ": " + trace(t), null, null);
            }
        }
        state.countBlock();
    }

    private void tx(RawBlock rb, int i, CborSpan ownBody, boolean hasAux) {
        RawTx tx;
        byte[] txBytes;
        try {
            tx = rb.tx(i);
            txBytes = rb.txBytes(i);
        } catch (Throwable t) {
            issue("rawtx_in_block", hex(Blake2bUtil.blake2bHash256(ownBody.bytes())), "index " + i + ": " + trace(t), null, null);
            return;
        }
        String txHash = hex(tx.txId());
        Ctx c = new Ctx(txHash, txBytes);

        // tx id cross-checks
        String own = hex(Blake2bUtil.blake2bHash256(ownBody.bytes()));
        if (!own.equals(txHash)) c.issue("txid_body_slice", "own " + own + " RawTx " + txHash);
        try {
            String tu = TransactionUtil.getTxHash(txBytes);
            if (!tu.equals(txHash)) c.issue("txid_transaction_util", "TransactionUtil " + tu);
            RawTx re = RawTx.of(txBytes);
            if (!Arrays.equals(re.txId(), tx.txId()) || re.isValid() != tx.isValid())
                c.issue("rawtx_reparse", "reparsed id " + hex(re.txId()) + " valid " + re.isValid() + " vs " + tx.isValid());
        } catch (Throwable t) {
            c.issue("rawtx_reparse_error", trace(t));
        }
        if (hasAux != tx.auxData().isPresent())
            c.issue("aux_alignment", "aux map has index " + hasAux + ", RawTx aux " + tx.auxData().isPresent());

        // the model
        Transaction t = null;
        try {
            t = Transaction.deserialize(txBytes);
            state.inc(era, "deserialize_ok", 1);
        } catch (Throwable e) {
            c.issue("tx_deserialize", trace(e));
        }

        // aux data hash
        Optional<byte[]> auxHash = tx.auxDataHash();
        Optional<CborSpan> k7 = tx.bodyField(7);
        if (auxHash.isPresent()) {
            state.inc(era, "aux", 1);
            if (k7.isEmpty()) c.issue("aux_hash_missing_in_body", "aux data present, no body key 7");
            else if (!Arrays.equals(k7.get().byteString(), auxHash.get()))
                c.issue("aux_hash", "body " + hex(k7.get().byteString()) + " computed " + hex(auxHash.get()));
        } else if (k7.isPresent()) {
            c.issue("aux_hash_without_aux", "body key 7 " + hex(k7.get().byteString()) + " but no aux data");
        }

        // native script context
        Set<String> vkeys = new HashSet<>();
        for (CborSpan w : tx.vkeyWitnesses())
            vkeys.add(hex(Blake2bUtil.blake2bHash224(w.get(0).byteString())));
        // slots are unsigned 64-bit: keep the low 64 bits (the evaluator reads them unsigned)
        Long invalidBefore = tx.bodyField(8).map(s -> s.asBigInteger().longValue()).orElse(null);
        Long invalidHereafter = tx.bodyField(3).map(s -> s.asBigInteger().longValue()).orElse(null);

        Set<Language> witnessLangs = new HashSet<>();
        for (RawScript s : tx.scripts()) {
            script(c, s, "witness", true, vkeys, invalidBefore, invalidHereafter);
            if (s.type() > 0) witnessLangs.add(LANGS[s.type() - 1]);
        }
        for (RawScript s : tx.auxScripts())
            script(c, s, "aux", false, vkeys, invalidBefore, invalidHereafter);
        List<RawOutput> outputs = new ArrayList<>(tx.outputs());
        tx.collateralReturn().ifPresent(outputs::add);
        for (RawOutput o : outputs) {
            o.scriptRef().ifPresent(s -> script(c, s, "ref", false, vkeys, invalidBefore, invalidHereafter));
            o.inlineDatum().ifPresent(d -> datum(c, d, "inline"));
            o.datumHash().ifPresent(h -> state.inc(era, "output_datum_hashes", 1));
        }
        for (RawDatum d : tx.witnessDatums())
            datum(c, d, "witness");
        List<RawRedeemer> redeemers = tx.redeemers();
        for (RawRedeemer r : redeemers) {
            state.inc(era, "redeemers", 1);
            try {
                PlutusData.deserialize(r.data().bytes());
            } catch (Throwable e) {
                c.issue("redeemer_data_decode", trace(e));
            }
        }

        if (rb.era().getValue() >= Era.Alonzo.getValue())
            scriptDataHash(c, tx, rb.era(), witnessLangs);
        if (t != null)
            model(c, t, tx, redeemers);
    }

    private void script(Ctx c, RawScript s, String where, boolean mustHold, Set<String> vkeys, Long ib, Long ih) {
        boolean nativeScript = s.type() == 0;
        state.inc(era, where + (nativeScript ? "_native_scripts" : "_plutus_scripts"), 1);
        byte[] raw = s.hash();
        Script m;
        try {
            m = s.toScript();
        } catch (Throwable e) {
            c.issue("script_decode_" + where, "type " + s.type() + " hash " + hex(raw) + ": " + trace(e));
            return;
        }
        byte[] modelHash;
        try {
            modelHash = m.getScriptHash();
        } catch (Throwable e) {
            c.issue("script_model_hash_error_" + where, "hash " + hex(raw) + ": " + trace(e));
            return;
        }
        if (nativeScript) {
            CborScan scan = CborScan.scan(s.span().bytes(), false);
            state.max("native_script_depth", scan.maxDepth, c.txHash, slot);
            if (!Arrays.equals(raw, modelHash)) {
                if (scan.canonical)
                    c.issue("native_script_hash_canonical", where + " raw " + hex(raw) + " model " + hex(modelHash)
                            + " script " + hex(s.span().bytes()));
                else
                    state.inc(era, "native_script_noncanonical", 1);
            }
            try {
                boolean holds = NativeScriptEvaluator.evaluate((NativeScript) m, vkeys, ib, ih);
                state.inc(era, "native_eval_" + where + "_" + holds, 1);
                if (mustHold && !holds)
                    c.issue("native_script_eval_false", "witness native script " + hex(raw) + " does not hold; vkeys "
                            + vkeys + " invalidBefore " + ib + " invalidHereafter " + ih);
            } catch (Throwable e) {
                c.issue("native_script_eval_error", where + " " + hex(raw) + ": " + trace(e));
            }
        } else if (!Arrays.equals(raw, modelHash)) {
            c.issue("plutus_script_hash_" + where, "raw " + hex(raw) + " model " + hex(modelHash));
        }
    }

    private void datum(Ctx c, RawDatum d, String where) {
        state.inc(era, where + "_datums", 1);
        byte[] raw = d.hash();
        PlutusData pd;
        try {
            pd = d.toPlutusData();
        } catch (Throwable e) {
            c.issue("datum_decode_" + where, "hash " + hex(raw) + ": " + trace(e));
            return;
        }
        byte[] model;
        try {
            model = pd.getDatumHashAsBytes();
        } catch (Throwable e) {
            c.issue("datum_model_hash_error_" + where, "hash " + hex(raw) + ": " + trace(e));
            return;
        }
        if (!Arrays.equals(raw, model)) {
            byte[] bytes = d.span().bytes();
            CborScan scan = CborScan.scan(bytes, true);
            if (scan.canonical)
                c.issue("datum_hash_canonical_" + (scan.bignum ? "bignum_" : "") + where, "raw " + hex(raw) + " model " + hex(model) + " datum "
                        + (bytes.length <= 4096 ? hex(bytes) : bytes.length + " bytes"));
            else
                state.inc(era, "datum_noncanonical_" + where, 1);
        }
    }

    private void scriptDataHash(Ctx c, RawTx tx, Era txEra, Set<Language> witnessLangs) {
        Optional<byte[]> onChain = tx.bodyField(11).map(CborSpan::byteString);
        boolean hasRedeemersOrDatums = !tx.redeemers().isEmpty() || !tx.witnessDatums().isEmpty();
        if (onChain.isEmpty() && !hasRedeemersOrDatums && witnessLangs.isEmpty())
            return;
        if (costModels == null) {
            state.inc(era, "sdh_skipped_no_costmodels", 1);
            return;
        }
        int epoch = epochOfSlot.applyAsInt(slot);
        Map<Language, long[]> models = costModels.epoch(epoch);
        if (models == null) {
            state.inc(era, "sdh_skipped_no_costmodels", 1);
            return;
        }
        state.inc(era, "sdh_checked", 1);
        // reference scripts in spent / referenced outputs may add languages we cannot see without a UTxO set:
        // accept any superset of the witness languages for which the epoch has a cost model
        List<Language> optional = new ArrayList<>();
        for (Language l : LANGS)
            if (!witnessLangs.contains(l) && models.containsKey(l)) optional.add(l);
        for (Language l : witnessLangs)
            if (!models.containsKey(l)) {
                c.issue("sdh_language_without_costmodel", l + " in epoch " + epoch);
                return;
            }
        List<String> tried = new ArrayList<>();
        for (int mask = 0; mask < (1 << optional.size()); mask++) {
            List<Language> langs = new ArrayList<>();
            for (Language l : LANGS)
                if (witnessLangs.contains(l) || (optional.contains(l) && (mask & (1 << optional.indexOf(l))) != 0))
                    langs.add(l);
            byte[] views = costModels.languageViews(epoch, models, langs);
            Optional<byte[]> computed = tx.scriptDataHash(txEra, views);
            if (computed.map(h -> onChain.isPresent() && Arrays.equals(h, onChain.get())).orElse(onChain.isEmpty())) {
                state.inc(era, "sdh_matched", 1);
                if (mask != 0) state.inc(era, "sdh_matched_with_ref_script_languages", 1);
                return;
            }
            tried.add(langs + "=" + computed.map(Checker::hex).orElse("none"));
        }
        c.issue("script_data_hash", "epoch " + epoch + " on-chain " + onChain.map(Checker::hex).orElse("none")
                + " tried " + tried);
    }

    private void model(Ctx c, Transaction t, RawTx tx, List<RawRedeemer> rawRedeemers) {
        TransactionBody b = t.getBody();
        TransactionWitnessSet w = t.getWitnessSet();
        if (b == null || w == null) {
            c.issue("model_missing_part", "body " + (b != null) + " witnesses " + (w != null));
            return;
        }
        setEq(c, "inputs", tx.bodyField(0), b.getInputs().size());
        eq(c, "outputs", tx.outputs().size(), b.getOutputs().size());
        eq(c, "fee", tx.bodyField(2).map(CborSpan::asBigInteger).orElse(BigInteger.ZERO), b.getFee());
        eq(c, "ttl", tx.bodyField(3).map(s -> s.asBigInteger().longValue()).orElse(0L), b.getTtl());
        eq(c, "validity_start", tx.bodyField(8).map(s -> s.asBigInteger().longValue()).orElse(0L),
                b.getValidityStartInterval());
        eq(c, "aux_data_hash", tx.bodyField(7).map(s -> hex(s.byteString())).orElse(null),
                b.getAuxiliaryDataHash() == null ? null : hex(b.getAuxiliaryDataHash()));
        eq(c, "script_data_hash", tx.bodyField(11).map(s -> hex(s.byteString())).orElse(null),
                b.getScriptDataHash() == null ? null : hex(b.getScriptDataHash()));
        eq(c, "is_valid", tx.isValid(), t.isValid());
        eq(c, "aux_present", tx.auxData().isPresent(), t.getAuxiliaryData() != null);
        setEq(c, "vkey_witnesses", tx.witnessField(0), size(w.getVkeyWitnesses()));
        setEq(c, "native_scripts", tx.witnessField(1), size(w.getNativeScripts()));
        setEq(c, "bootstrap_witnesses", tx.witnessField(2), size(w.getBootstrapWitnesses()));
        setEq(c, "plutus_v1_scripts", tx.witnessField(3), size(w.getPlutusV1Scripts()));
        setEq(c, "plutus_v2_scripts", tx.witnessField(6), size(w.getPlutusV2Scripts()));
        setEq(c, "plutus_v3_scripts", tx.witnessField(7), size(w.getPlutusV3Scripts()));
        // a repeated redeemer key (Conway map form) is kept once, as the ledger keeps it
        if (size(w.getRedeemers()) != rawRedeemers.size() && size(w.getRedeemers()) != rawRedeemers.stream()
                .map(r -> r.tag() + "/" + r.index()).distinct().count())
            c.issue("model_redeemers", "on-chain " + rawRedeemers.size() + " model " + size(w.getRedeemers()));
    }

    private static int size(List<?> l) {
        return l == null ? 0 : l.size();
    }

    // A set field: the model may keep repeated entries or drop them (UniqueList), so either count is accepted.
    private void setEq(Ctx c, String field, Optional<CborSpan> set, int model) {
        List<CborSpan> items = set.map(s -> s.untagIf(258).items()).orElse(List.of());
        Set<String> seen = new HashSet<>();
        for (CborSpan s : items) seen.add(hex(s.bytes()));
        if (model != items.size() && model != seen.size())
            c.issue("model_" + field, "on-chain " + items.size() + " (distinct " + seen.size() + ") model " + model);
    }

    private void eq(Ctx c, String field, Object raw, Object model) {
        boolean same = raw == null ? model == null : (raw instanceof BigInteger bi && model instanceof BigInteger mi
                ? bi.compareTo(mi) == 0 : raw.equals(model));
        if (!same) c.issue("model_" + field, "on-chain " + raw + " model " + model);
    }

    // ---- issue plumbing

    private final class Ctx {
        final String txHash;
        final byte[] txBytes;

        Ctx(String txHash, byte[] txBytes) {
            this.txHash = txHash;
            this.txBytes = txBytes;
        }

        void issue(String check, String detail) {
            Checker.this.issue(check, txHash, detail, txBytes, null);
        }
    }

    private void issue(String check, String txHash, String detail, byte[] txCbor, byte[] blockCbor) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("network", state.network);
        m.put("era", era);
        m.put("slot", slot);
        m.put("blockNo", blockNo);
        m.put("block", blockHash);
        m.put("tx", txHash);
        m.put("check", check);
        m.put("detail", detail);
        issues.report(check, m, txCbor, blockCbor);
    }

    static String trace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String[] lines = sw.toString().split("\n");
        return String.join(" | ", Arrays.copyOf(lines, Math.min(lines.length, 12))).replace("\t", "");
    }

    static String hex(byte[] b) {
        return HexUtil.encodeHexString(b);
    }
}
