package com.bloxbean.cardano.client.transaction.spec.script;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.transaction.util.RealCborFixturesAccess;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;

/**
 * The native scripts of the real transactions and blocks, read from the original bytes: witness scripts, aux-data scripts
 * and reference scripts. Witness scripts come with what the ledger evaluated them against: the key hashes of the vkey
 * witnesses and the validity interval.
 */
final class NativeScriptCorpus {
    record Script(String source, byte[] cbor) {
        @Override
        public String toString() {
            return source;
        }
    }

    record WitnessScripts(String tx, List<byte[]> scripts, Set<String> vkeyHashes, Long invalidBefore, Long invalidHereafter) {
        @Override
        public String toString() {
            return tx;
        }
    }

    private NativeScriptCorpus() {
    }

    static List<Script> all() {
        List<Script> scripts = new ArrayList<>();
        for (RealCborFixturesAccess.Tx tx : RealCborFixturesAccess.allTxsAndBlockTxs()) {
            List<CborSpan> parts = CborSpan.of(tx.cbor()).items();
            witnessScripts(parts.get(1)).forEach(script -> scripts.add(new Script(tx.name() + " witness", script.bytes())));
            auxScripts(parts.get(parts.size() - 1)).forEach(script -> scripts.add(new Script(tx.name() + " aux", script.bytes())));
            for (CborSpan output : parts.get(0).field(1).orElseThrow().items()) {
                Optional<CborSpan> scriptRef = output.majorType() == 5 ? output.field(3) : Optional.empty();
                if (scriptRef.isPresent()) {
                    CborSpan typed = scriptRef.get().embedded();
                    if (typed.get(0).asLong() == 0)
                        scripts.add(new Script(tx.name() + " reference", typed.get(1).bytes()));
                }
            }
        }
        return scripts;
    }

    static List<WitnessScripts> witnessScripts() {
        List<WitnessScripts> txs = new ArrayList<>();
        for (RealCborFixturesAccess.Tx tx : RealCborFixturesAccess.allTxsAndBlockTxs()) {
            List<CborSpan> parts = CborSpan.of(tx.cbor()).items();
            List<CborSpan> scripts = witnessScripts(parts.get(1));
            if (scripts.isEmpty())
                continue;
            Set<String> vkeyHashes = new HashSet<>();
            parts.get(1).field(0).ifPresent(witnesses -> witnesses.untagIf(258).items().forEach(witness ->
                    vkeyHashes.add(encodeHexString(Blake2bUtil.blake2bHash224(witness.get(0).byteString())))));
            CborSpan body = parts.get(0);
            List<byte[]> bytes = new ArrayList<>();
            scripts.forEach(script -> bytes.add(script.bytes()));
            txs.add(new WitnessScripts(tx.name(), bytes, vkeyHashes,
                    body.field(8).map(CborSpan::asLong).orElse(null), body.field(3).map(CborSpan::asLong).orElse(null)));
        }
        return txs;
    }

    private static List<CborSpan> witnessScripts(CborSpan witnessSet) {
        return witnessSet.field(1).map(scripts -> scripts.untagIf(258).items()).orElse(List.of());
    }

    // Aux data: a metadata map (Shelley), [metadata, scripts] (Allegra, Mary), or tag 259 {0: metadata, 1: scripts, ...}
    private static List<CborSpan> auxScripts(CborSpan aux) {
        if (aux.majorType() == 4)
            return aux.get(1).items();
        if (aux.majorType() == 5 && aux.tag() == 259)
            return aux.untag().field(1).map(CborSpan::items).orElse(List.of());
        return List.of();
    }
}
