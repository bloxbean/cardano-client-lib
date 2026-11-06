package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR 0003 I15: every Dijkstra test vector reads and walks with no rejection, and every hash and id matches the
 * vector's, compared on the reader's original bytes. The vectors are {@code cbor/dijkstra-vectors.json}: from Haskell
 * code or from networks of Haskell nodes, each with its source and the values recorded there.
 */
class DijkstraVectorsTest {

    /** Unknown, out-of-era, extra, repeated and missing items, and values of the wrong shape (D6). */
    static final FormatPolicy STRUCTURE = StrictLedgerPolicy.INSTANCE;

    private record Vector(String name, String kind, JsonNode expected, byte[] cbor) {
    }

    private static List<Vector> vectors() throws IOException {
        try (InputStream in = DijkstraVectorsTest.class.getResourceAsStream("/cbor/dijkstra-vectors.json")) {
            List<Vector> vectors = new ArrayList<>();
            for (JsonNode item : new ObjectMapper().readTree(in).get("items")) {
                vectors.add(new Vector(item.get("name").asText(), item.get("kind").asText(), item.get("expected"),
                        decodeHexString(item.get("cbor").asText())));
            }
            return vectors;
        }
    }

    private static List<Vector> vectors(String... kinds) throws IOException {
        List<String> wanted = List.of(kinds);
        return vectors().stream().filter(vector -> wanted.contains(vector.kind())).toList();
    }

    @Test
    void everyKindHasItsVectors() throws IOException {
        Map<String, Integer> kinds = new HashMap<>();
        for (Vector vector : vectors())
            kinds.merge(vector.kind(), 1, Integer::sum);
        assertThat(kinds).containsEntry("block", 18).containsEntry("header", 2).containsEntry("mempool_tx", 13)
                .containsEntry("block_tx", 4).containsEntry("leios_certificate", 3).containsEntry("eb_references", 3);
    }

    // ---- blocks and headers ----

    @Test
    void everyBlockReadsAndWalksWithItsRecordedValues() throws IOException {
        Map<String, String> hashes = new HashMap<>();
        int withCertificate = 0;
        for (Vector vector : vectors("block")) {
            RawBlock block = block(vector.cbor());
            RawHeader header = block.rawHeader();
            JsonNode expected = vector.expected();
            String name = vector.name();
            hashes.put(name, encodeHexString(header.hash()));

            assertThat(block.era()).as(name).isEqualTo(Era.Dijkstra);
            assertThat(header.bodyHash()).as(name).isEqualTo(block.bodyHash());
            assertThat(header.bodySize()).as(name).isEqualTo(blockBody(vector.cbor()).length());
            assertThat(header.hash()).as(name).isEqualTo(Blake2bUtil.blake2bHash256(block.header().bytes()));
            if (expected.has("blockHash"))
                assertThat(encodeHexString(header.hash())).as(name).isEqualTo(expected.get("blockHash").asText());
            if (expected.has("blockNumber"))
                assertThat(header.blockNumber()).as(name).isEqualTo(expected.get("blockNumber").asLong());
            if (expected.has("slot"))
                assertThat(header.slot()).as(name).isEqualTo(expected.get("slot").asLong());
            if (expected.has("prevHash")) {
                assertThat(header.prevHash().map(hash -> encodeHexString(hash))).as(name)
                        .contains(expected.get("prevHash").asText());
            }
            if (expected.has("txCount"))
                assertThat(block.txCount()).as(name).isEqualTo(expected.get("txCount").asInt());
            assertHeaderLayout(header, expected.get("headerItems").asInt(), name);

            boolean certified = expected.path("leiosCertificate").asBoolean(false);
            assertThat(block.leiosCertificate().isPresent()).as(name).isEqualTo(certified);
            if (header.leiosCertInBody().isPresent())
                assertThat(header.leiosCertInBody()).as(name).contains(certified);
            if (certified) {
                withCertificate++;
                assertThat(block.leiosCertificate().orElseThrow().signature()).as(name).hasSize(48);
            }
            if (expected.has("leiosSigners")) {
                assertThat(encodeHexString(block.leiosCertificate().orElseThrow().signers())).as(name)
                        .isEqualTo(expected.get("leiosSigners").asText());
            }
            // a valid block at the pin has no Peras certificate (BlockBody/Internal.hs)
            assertThat(block.perasCertificate()).as(name).isEmpty();
            assertThat(block.invalidTransactions()).as(name).isEmpty();
            assertThatThrownBy(block::transactionBodies).isInstanceOf(UnsupportedOperationException.class);

            assertTransactions(block, vector.cbor(), name);
            RawHeader.walk(block.header(), Era.Dijkstra, STRUCTURE);
        }
        assertThat(withCertificate).isEqualTo(4);
        // the rc-2026w41 certifying block's previous block is the announcing one
        Vector certifying = vectors("block").stream().filter(v -> v.name().equals("rc-2026w41-certifying-block"))
                .findFirst().orElseThrow();
        assertThat(block(certifying.cbor()).rawHeader().prevHash().map(hash -> encodeHexString(hash)))
                .contains(hashes.get(certifying.expected().get("prevIs").asText()));
    }

    // Each transaction as the block gives it, and as it was submitted.
    private static void assertTransactions(RawBlock block, byte[] cbor, String name) {
        List<CborSpan> transactions = blockBody(cbor).get(0).items();
        assertThat(block.txCount()).as(name).isEqualTo(transactions.size());
        List<Integer> invalid = new ArrayList<>();
        for (int i = 0; i < block.txCount(); i++) {
            CborSpan original = transactions.get(i);
            RawTx tx = block.tx(i);
            assertThat(tx.txId()).isEqualTo(Blake2bUtil.blake2bHash256(original.get(0).bytes()));
            assertThat(tx.isValid()).isEqualTo(original.get(3).asBoolean());
            if (!tx.isValid())
                invalid.add(i);
            byte[] submitted = block.txBytes(i);
            assertThat(submitted[0]).as(name).isEqualTo((byte) 0x83);
            RawTx resubmitted = RawTx.of(submitted);
            assertThat(resubmitted.txId()).isEqualTo(tx.txId());
            assertThat(resubmitted.auxData().map(CborSpan::bytes).orElse(null))
                    .isEqualTo(original.get(2).isNull() ? null : original.get(2).bytes());
            // the block form read on its own: the flag is last
            RawTx alone = RawTx.of(original.bytes());
            assertThat(alone.txId()).isEqualTo(tx.txId());
            assertThat(alone.isValid()).isEqualTo(tx.isValid());

            RawTx.walk(original, Era.Dijkstra, STRUCTURE);
            RawTx.walk(submitted, Era.Dijkstra, STRUCTURE);
            readEverything(tx);
        }
        assertThat(block.invalidTxIndexes()).as(name).containsExactly(invalid.stream().mapToInt(i -> i).toArray());
    }

    @Test
    void everyHeaderReadsAndWalksWithItsRecordedValues() throws IOException {
        for (Vector vector : vectors("header")) {
            CborSpan span = CborSpan.of(vector.cbor());
            RawHeader header = RawHeader.of(span, Era.Dijkstra);
            JsonNode expected = vector.expected();
            String name = vector.name();
            assertHeaderLayout(header, expected.get("headerItems").asInt(), name);
            if (expected.has("blockHash"))
                assertThat(encodeHexString(header.hash())).as(name).isEqualTo(expected.get("blockHash").asText());
            if (expected.has("blockNumber"))
                assertThat(header.blockNumber()).as(name).isEqualTo(expected.get("blockNumber").asLong());
            if (expected.has("slot"))
                assertThat(header.slot()).as(name).isEqualTo(expected.get("slot").asLong());
            if (expected.has("prevHash")) {
                assertThat(header.prevHash().map(hash -> encodeHexString(hash))).as(name)
                        .contains(expected.get("prevHash").asText());
            }
            if (expected.has("protocolMajor"))
                assertThat(header.protocolMajor()).as(name).isEqualTo(expected.get("protocolMajor").asLong());
            assertThat(RawHeader.of(span).hash()).isEqualTo(header.hash());
            RawHeader.walk(span, Era.Dijkstra, STRUCTURE);
        }
    }

    /** DijkstraNet's first header from chain-sync is the header of its first block from block-fetch. */
    @Test
    void theDijkstraNetHeaderIsItsBlocksHeader() throws IOException {
        Vector header = vectors("header").stream().filter(v -> v.name().equals("dijkstranet-first-header"))
                .findFirst().orElseThrow();
        Vector block = vectors("block").stream().filter(v -> v.name().equals("dijkstranet-first-block"))
                .findFirst().orElseThrow();
        assertThat(block(block.cbor()).header().bytes()).isEqualTo(header.cbor());
    }

    /** ADR 0003 I14: a 12-item header is refused where Conway is meant, as before D4a. */
    @Test
    void aTwelveItemHeaderIsStillRefusedAsConway() throws IOException {
        int refused = 0;
        for (Vector vector : vectors("header", "block")) {
            CborSpan header = vector.kind().equals("header") ? CborSpan.of(vector.cbor())
                    : block(vector.cbor()).header();
            if (vector.expected().get("headerItems").asInt() != 12)
                continue;
            assertThatThrownBy(() -> RawHeader.walk(header, FormatPolicy.ACCEPT_ALL))
                    .isInstanceOf(CborRuntimeException.class).hasMessageContaining("15 or 10 items, found 12");
            assertThatThrownBy(() -> RawHeader.of(header, Era.Conway)).isInstanceOf(CborRuntimeException.class);
            refused++;
        }
        assertThat(refused).isEqualTo(18);
    }

    private static void assertHeaderLayout(RawHeader header, int items, String name) {
        assertThat(header.bodySpan().size()).as(name).isEqualTo(items);
        assertThat(header.vrfResult()).as(name).isPresent();
        assertThat(header.leiosCertInBody().isPresent()).as(name).isEqualTo(items == 12);
        if (items == 10)
            assertThat(header.ebReferencesAnnouncement()).as(name).isEmpty();
        header.ebReferencesAnnouncement().ifPresent(announcement -> {
            assertThat(announcement.hash()).as(name).hasSize(32);
            assertThat(announcement.size()).as(name).isPositive();
        });
        header.protocolMinor();
        header.hotVkey();
        header.kesPeriod();
    }

    // ---- transactions ----

    /** KleioScan serves these two sub-transactions on their own (the vectors' README, section 6): they have no fee. */
    private static final List<String> STANDALONE_SUB_TRANSACTIONS = List.of("kleioscan-musashi-5d76e2e9",
            "kleioscan-musashi-deb6542d");

    @Test
    void everyTransactionReadsAndWalksWithItsRecordedId() throws IOException {
        for (Vector vector : vectors("mempool_tx", "block_tx")) {
            RawTx tx = RawTx.of(vector.cbor());
            JsonNode expected = vector.expected();
            String name = vector.name();
            assertThat(tx.txId()).as(name).isEqualTo(Blake2bUtil.blake2bHash256(tx.body().bytes()));
            if (expected.has("txId"))
                assertThat(encodeHexString(tx.txId())).as(name).isEqualTo(expected.get("txId").asText());
            RawTxBody body = tx.body(Era.Dijkstra);
            if (expected.has("subTxCount"))
                assertThat(body.subTransactions()).as(name).hasSize(expected.get("subTxCount").asInt());
            if (expected.has("plutusV4Scripts")) {
                assertThat(tx.witnessSet(Era.Dijkstra).plutusV4Scripts().orElse(List.of())).as(name)
                        .hasSize(expected.get("plutusV4Scripts").asInt());
            }
            // the block form has its flag last, the other forms none or third
            assertThat(vector.cbor()[0]).as(name).isEqualTo(vector.kind().equals("block_tx") ? (byte) 0x84
                    : (byte) 0x83);
            if (STANDALONE_SUB_TRANSACTIONS.contains(name)) {
                assertThat(body.fee()).as(name).isEmpty();
                TxBodyField.SUB_RECORD.walk(tx.body(), Era.Dijkstra, STRUCTURE);
                WitnessSetField.RECORD.walk(tx.witnessSet(), Era.Dijkstra, STRUCTURE);
            } else {
                RawTx.walk(vector.cbor(), Era.Dijkstra, STRUCTURE);
            }
            readEverything(tx);
            RawTx lenient = RawTx.lenient(vector.cbor());
            assertThat(lenient.txId()).isEqualTo(tx.txId());
            assertThat(lenient.isValid()).isEqualTo(tx.isValid());
        }
    }

    /**
     * cardano-ledger's golden Dijkstra transaction: a sub-transaction, Plutus V4 witnesses, a script credential guard,
     * reward-account keyed intervals, and pool registrations with and without a BLS key.
     */
    @Test
    void theLedgersGoldenTransactionHasEveryNewField() throws IOException {
        Vector golden = vectors("mempool_tx").stream().filter(v -> v.name().equals("ledger-golden-tx")).findFirst()
                .orElseThrow();
        RawTx tx = RawTx.of(golden.cbor());
        RawTxBody body = tx.body(Era.Dijkstra);
        assertThat(body.subTransactions()).hasSize(1);
        assertThat(body.requiredSigners()).isEmpty();
        assertThat(body.guards().orElseThrow()).anyMatch(RawCredential::isScript);
        assertThat(body.directDeposits()).isNotEmpty();
        assertThat(body.accountBalanceIntervals()).isNotEmpty();
        assertThat(tx.witnessSet(Era.Dijkstra).plutusV4Scripts().orElseThrow()).isNotEmpty();
        assertThat(tx.witnessSet(Era.Dijkstra).scripts()).hasSizeGreaterThan(tx.scripts().size());
        List<RawCertificate> pools = body.certificates().stream()
                .filter(cert -> cert.kind() == CertKind.POOL_REGISTRATION).toList();
        assertThat(pools).anyMatch(cert -> cert.blsKey().isPresent());
        assertThat(pools).anyMatch(cert -> cert.blsKey().isEmpty());
        for (RawCertificate pool : pools) {
            pool.blsKey().ifPresent(key -> {
                assertThat(key.publicKey()).hasSize(96);
                assertThat(key.proofOfPossession()).hasSize(48);
            });
            assertThat(pool.pledge()).isNotNull();
            assertThat(pool.margin()).isNotNull();
        }
        RawTx sub = body.subTransactions().get(0);
        assertThat(sub.isValid()).isTrue();
        assertThat(sub.txId()).isEqualTo(Blake2bUtil.blake2bHash256(sub.body().bytes()));
        assertThat(sub.body(Era.Dijkstra).fee()).isEmpty();
        assertThat(sub.witnessSet(Era.Dijkstra).plutusV4Scripts().orElseThrow()).isNotEmpty();
        // a Plutus V4 script has no model yet (ADR 0004), so the model helper refuses it rather than call it V3
        RawScript v4 = tx.witnessSet(Era.Dijkstra).plutusV4Scripts().orElseThrow().get(0);
        assertThatThrownBy(() -> RawModels.toScript(v4)).isInstanceOf(CborDeserializationException.class)
                .hasMessageContaining("Plutus V4");
    }

    // Reads every field of the body, the witness set and the aux data in Dijkstra, sub-transactions included.
    private static void readEverything(RawTx tx) {
        RawTxBody body = tx.body(Era.Dijkstra);
        body.inputs();
        body.outputs();
        body.fee();
        body.ttl();
        body.certificates().forEach(DijkstraVectorsTest::readCertificate);
        body.withdrawals();
        body.auxDataHash();
        body.validityStart();
        body.mint();
        body.scriptDataHash();
        body.collateral();
        assertThat(body.requiredSigners()).isEmpty();
        body.guards().ifPresent(guards -> guards.forEach(RawCredential::hash));
        body.networkId();
        body.collateralReturn();
        body.totalCollateral();
        body.referenceInputs();
        body.votingProcedures().ifPresent(RawVotingProcedures::voters);
        body.proposalProcedures();
        body.currentTreasury();
        body.donation();
        body.requiredTopLevelGuards().forEach(guard -> guard.credential().hash());
        body.directDeposits();
        body.accountBalanceIntervals();
        body.startingAccountBalanceIntervals();
        RawWitnessSet witnesses = tx.witnessSet(Era.Dijkstra);
        witnesses.vkeyWitnesses();
        witnesses.bootstrapWitnesses();
        witnesses.datums();
        witnesses.redeemers().ifPresent(redeemers -> redeemers.forEach(RawRedeemer::units));
        witnesses.scripts().forEach(RawScript::hash);
        tx.auxData(Era.Dijkstra).ifPresent(aux -> aux.scripts().forEach(RawScript::hash));
        tx.outputs().forEach(output -> output.scriptRef().ifPresent(RawScript::hash));
        for (RawTx sub : body.subTransactions()) {
            assertThat(sub.isValid()).isTrue();
            readEverything(sub);
        }
    }

    private static void readCertificate(RawCertificate cert) {
        if (cert.kind() == CertKind.POOL_REGISTRATION) {
            cert.blsKey();
            cert.relays().forEach(RawRelay::kind);
            cert.poolMetadata();
            cert.poolOwners();
        }
    }

    // ---- Leios ----

    @Test
    void everyLeiosCertificateReads() throws IOException {
        Map<String, Integer> signers = Map.of("ledger-leios-cert-pv12", 1, "ledger-leios-cert-pv13", 1,
                "ledger-leios-cert-1000", 125);
        for (Vector vector : vectors("leios_certificate")) {
            RawLeiosCertificate certificate = RawLeiosCertificate.of(CborSpan.of(vector.cbor()));
            assertThat(certificate.signers()).as(vector.name()).hasSize(signers.get(vector.name()));
            assertThat(certificate.signature()).as(vector.name()).hasSize(48);
        }
    }

    @Test
    void everyEndorserBlockReferenceHasItsAnnouncedHashAndSize() throws IOException {
        for (Vector vector : vectors("eb_references")) {
            RawEbReferences references = RawEbReferences.of(vector.cbor());
            JsonNode expected = vector.expected();
            String name = vector.name();
            assertThat(encodeHexString(references.hash())).as(name).isEqualTo(expected.get("hash").asText());
            assertThat(references.span().length()).as(name).isEqualTo(expected.get("size").asInt());
            List<RawEbReferences.Entry> entries = references.entries();
            assertThat(entries).as(name).hasSize(expected.get("count").asInt());
            assertThat(encodeHexString(entries.get(0).txHash())).as(name)
                    .isEqualTo(expected.get("firstKey").asText());
            assertThat(entries.get(0).size()).as(name).isEqualTo(expected.get("firstSize").asLong());
        }
    }

    // ---- helpers ----

    // A Dijkstra block, enveloped [8, block] or bare.
    private static RawBlock block(byte[] cbor) {
        CborSpan top = CborSpan.of(cbor);
        boolean enveloped = top.size() == 2 && top.get(0).majorType() == 0;
        if (enveloped) {
            assertThat(BlockEnvelope.of(cbor).eraIndex()).isEqualTo(Era.Dijkstra.value);
            return RawBlock.of(cbor);
        }
        return RawBlock.of(cbor, Era.Dijkstra);
    }

    private static CborSpan blockBody(byte[] cbor) {
        CborSpan top = CborSpan.of(cbor);
        CborSpan block = top.size() == 2 && top.get(0).majorType() == 0 ? top.get(1) : top;
        return block.get(1);
    }
}
