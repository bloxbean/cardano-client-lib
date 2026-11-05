package com.bloxbean.cardano.client.transaction.raw;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.util.RealCborFixturesAccess;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.bloxbean.cardano.client.crypto.Blake2bUtil.blake2bHash256;
import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link BlockEnvelope} and {@link RawHeader} on every real block (Shelley to Conway; mainnet, preprod, preview): the
 * header fields against the same items decoded by cbor-java, by the CDDL's positions written out here; the header hash
 * against the on-chain block hash; the block number against the chain; the declared body against the body. And what a
 * damaged real block does.
 */
class RawHeaderRealCorpusTest {

    @Test
    void headersMatchTheChainAndAnIndependentDecoder() throws CborException {
        Set<Era> eras = new HashSet<>();
        for (RealCborFixturesAccess.Block block : RealCborFixturesAccess.blocks()) {
            RawBlock raw = RawBlock.of(block.cbor());
            Era era = raw.era();
            eras.add(era);
            RawHeader header = raw.rawHeader();

            assertThat(encodeHexString(header.hash())).as(block.name()).isEqualTo(block.blockHash());
            assertThat(header.blockNumber()).as(block.name()).isEqualTo(block.blockHeight());
            assertThat(header.bodyHash()).as(block.name()).isEqualTo(raw.bodyHash());
            assertThat(header.bodySize()).as(block.name()).isEqualTo(bodySize(raw));
            assertFields(header, decode(raw.header().bytes()), era);
            assertThat(header.span().bytes()).isEqualTo(raw.header().bytes());

            // without an era, the shape gives the same layout
            assertSameHeader(RawHeader.of(raw.header()), header);
            assertSameHeader(RawHeader.of(raw.header(), era), header);
        }
        assertThat(eras).containsExactlyInAnyOrder(Era.values());
    }

    private static long bodySize(RawBlock raw) {
        return raw.transactionBodies().length() + raw.transactionWitnessSets().length()
                + raw.auxiliaryDataSet().length() + raw.invalidTransactions().map(CborSpan::length).orElse(0);
    }

    private static void assertFields(RawHeader header, Array decoded, Era era) {
        List<DataItem> top = decoded.getDataItems();
        List<DataItem> body = ((Array) top.get(0)).getDataItems();
        boolean flat = era.value <= Era.Alonzo.value;
        assertThat(body).hasSize(flat ? 15 : 10);
        assertThat(header.blockNumber()).isEqualTo(number(body.get(0)));
        assertThat(header.slot()).isEqualTo(number(body.get(1)));
        if (body.get(2) == SimpleValue.NULL)
            assertThat(header.prevHash()).isEmpty();
        else
            assertThat(header.prevHash().orElseThrow()).isEqualTo(bytes(body.get(2)));
        assertThat(header.issuerVkey()).isEqualTo(bytes(body.get(3)));
        assertThat(header.vrfVkey()).isEqualTo(bytes(body.get(4)));
        assertThat(header.bodySignature()).isEqualTo(bytes(top.get(1)));
        if (flat) {
            assertVrf(header.nonceVrf().orElseThrow(), body.get(5));
            assertVrf(header.leaderVrf().orElseThrow(), body.get(6));
            assertThat(header.vrfResult()).isEmpty();
            assertThat(header.bodySize()).isEqualTo(number(body.get(7)));
            assertThat(header.bodyHash()).isEqualTo(bytes(body.get(8)));
            assertOperationalCert(header, body.subList(9, 13));
            assertProtocolVersion(header, body.subList(13, 15));
        } else {
            assertVrf(header.vrfResult().orElseThrow(), body.get(5));
            assertThat(header.nonceVrf()).isEmpty();
            assertThat(header.leaderVrf()).isEmpty();
            assertThat(header.bodySize()).isEqualTo(number(body.get(6)));
            assertThat(header.bodyHash()).isEqualTo(bytes(body.get(7)));
            assertOperationalCert(header, ((Array) body.get(8)).getDataItems());
            assertProtocolVersion(header, ((Array) body.get(9)).getDataItems());
        }
    }

    private static void assertVrf(RawVrfCert vrf, DataItem decoded) {
        List<DataItem> items = ((Array) decoded).getDataItems();
        assertThat(items).hasSize(2);
        assertThat(vrf.output()).isEqualTo(bytes(items.get(0)));
        assertThat(vrf.proof()).isEqualTo(bytes(items.get(1)));
    }

    private static void assertOperationalCert(RawHeader header, List<DataItem> items) {
        assertThat(items).hasSize(4);
        assertThat(header.hotVkey()).isEqualTo(bytes(items.get(0)));
        assertThat(header.sequenceNumber()).isEqualTo(number(items.get(1)));
        assertThat(header.kesPeriod()).isEqualTo(number(items.get(2)));
        assertThat(header.sigma()).isEqualTo(bytes(items.get(3)));
    }

    private static void assertProtocolVersion(RawHeader header, List<DataItem> items) {
        assertThat(items).hasSize(2);
        assertThat(header.protocolMajor()).isEqualTo(number(items.get(0)));
        assertThat(header.protocolMinor()).isEqualTo(number(items.get(1)));
    }

    private static void assertSameHeader(RawHeader actual, RawHeader expected) {
        assertThat(actual.hash()).isEqualTo(expected.hash());
        assertThat(actual.slot()).isEqualTo(expected.slot());
        assertThat(actual.protocolMinor()).isEqualTo(expected.protocolMinor());
        assertThat(actual.sequenceNumber()).isEqualTo(expected.sequenceNumber());
        assertThat(actual.nonceVrf().isPresent()).isEqualTo(expected.nonceVrf().isPresent());
    }

    private static Array decode(byte[] bytes) throws CborException {
        return (Array) new CborDecoder(new ByteArrayInputStream(bytes)).decodeNext();
    }

    private static long number(DataItem item) {
        return ((UnsignedInteger) item).getValue().longValueExact();
    }

    private static byte[] bytes(DataItem item) {
        return ((ByteString) item).getBytes();
    }

    @Test
    void theEnvelopeReadsEveryBlockAsRawBlockDoes() {
        for (RealCborFixturesAccess.Block block : RealCborFixturesAccess.blocks()) {
            RawBlock expected = RawBlock.of(block.cbor());
            BlockEnvelope envelope = BlockEnvelope.of(block.cbor());
            assertThat(envelope.eraIndex()).as(block.name()).isEqualTo(expected.era().value);
            assertThat(BlockEnvelope.eraIndexOf(block.cbor())).isEqualTo(envelope.eraIndex());
            assertThat(envelope.block().bytes()).isEqualTo(CborSpan.of(block.cbor()).get(1).bytes());

            for (RawBlock actual : List.of(RawBlock.of(envelope), RawBlock.readAs(envelope, expected.era()))) {
                assertThat(actual.era()).isEqualTo(expected.era());
                assertThat(actual.bodyHash()).isEqualTo(expected.bodyHash());
                assertThat(actual.txCount()).isEqualTo(expected.txCount());
                assertThat(actual.invalidTxIndexes()).isEqualTo(expected.invalidTxIndexes());
                assertThat(actual.header().bytes()).isEqualTo(expected.header().bytes());
            }
            assertThat(BlockEnvelope.headerOf(block.cbor()).bytes()).isEqualTo(expected.header().bytes());
            assertThat(BlockEnvelope.of(RawBlockRealCorpusTest.embedded(block.cbor())).eraIndex())
                    .isEqualTo(expected.era().value);
        }
    }

    /** A block read as an era with other layouts fails, unless the two eras share them (ADR 0003 D4, D8). */
    @Test
    void readAsFollowsTheLayoutsOfTheNamedEra() {
        for (RealCborFixturesAccess.Block block : RealCborFixturesAccess.blocks()) {
            RawBlock raw = RawBlock.of(block.cbor());
            BlockEnvelope envelope = BlockEnvelope.of(block.cbor());
            for (Era named : Era.values()) {
                EraSpec own = EraSpec.of(raw.era());
                EraSpec other = EraSpec.of(named);
                if (other.blockLayout != own.blockLayout) {
                    assertThatThrownBy(() -> RawBlock.readAs(envelope, named)).as(block.name())
                            .isInstanceOf(CborRuntimeException.class);
                } else if (other.headerLayout != own.headerLayout) {
                    RawBlock read = RawBlock.readAs(envelope, named);
                    assertThat(read.era()).isEqualTo(named);
                    assertThatThrownBy(read::rawHeader).as(block.name()).isInstanceOf(CborRuntimeException.class);
                } else {
                    RawBlock read = RawBlock.readAs(envelope, named);
                    assertThat(read.era()).isEqualTo(named);
                    assertThat(read.rawHeader().hash()).isEqualTo(raw.rawHeader().hash());
                    assertThat(read.bodyHash()).isEqualTo(raw.bodyHash());
                }
            }
        }
    }

    /** An era index this reader does not know, on a real block of the newest layouts (ADR 0003 I12). */
    @Test
    void anUnknownIndexOnARealBlockIsReadOnlyWhenAsked() {
        int read = 0;
        for (RealCborFixturesAccess.Block block : RealCborFixturesAccess.blocks()) {
            RawBlock raw = RawBlock.of(block.cbor());
            if (raw.era() != EraSpec.newest())
                continue;
            byte[] renamed = block.cbor().clone();
            renamed[1] = 9; // 82 07 ...
            assertThat(BlockEnvelope.of(renamed).eraIndex()).isEqualTo(9);
            assertThatThrownBy(() -> RawBlock.of(renamed)).isInstanceOf(UnsupportedEraException.class);
            assertThatThrownBy(() -> RawBlock.of(BlockEnvelope.of(renamed)))
                    .isInstanceOf(UnsupportedEraException.class);
            RawBlock asNewest = RawBlock.readAs(BlockEnvelope.of(renamed), EraSpec.newest());
            assertThat(asNewest.txCount()).isEqualTo(raw.txCount());
            assertThat(asNewest.rawHeader().blockNumber()).isEqualTo(block.blockHeight());
            read++;
        }
        assertThat(read).isPositive();
    }

    /** ADR 0003 D8's correction, on real headers of every era: the hash follows the bytes, the values do not change. */
    @Test
    void aReEncodedRealHeaderReadsTheSameAndHashesItsOwnBytes() {
        for (RealCborFixturesAccess.Block block : RealCborFixturesAccess.blocks()) {
            RawBlock raw = RawBlock.of(block.cbor());
            CborSpan original = raw.header();
            CborSpan number = original.get(0).get(0);
            byte[] wide = decodeHexString(String.format("1b%016x", number.asLong()));
            byte[] encoded = original.replacing(List.of(number), List.of(wide));
            assertThat(encoded).isNotEqualTo(original.bytes());

            RawHeader header = RawHeader.of(CborSpan.of(encoded), raw.era());
            assertThat(header.blockNumber()).isEqualTo(block.blockHeight());
            assertThat(header.slot()).isEqualTo(raw.rawHeader().slot());
            assertThat(header.hash()).isEqualTo(blake2bHash256(encoded));
            assertThat(encodeHexString(header.hash())).isNotEqualTo(block.blockHash());
        }
    }

    /** The tables hold on real headers: a walk finds every item, so no position in them is off for a real shape. */
    @Test
    void theLayoutTablesWalkEveryRealHeader() {
        for (RealCborFixturesAccess.Block block : RealCborFixturesAccess.blocks()) {
            RawBlock raw = RawBlock.of(block.cbor());
            int[] unknown = {0};
            EraSpec.of(raw.era()).headerLayout.body.walk(raw.header().get(0), raw.era(), new FormatPolicy() {
                @Override
                public void entry(RecordSpec record, FieldSpec field, CborSpan key, CborSpan value, int occurrence,
                                  Era era) {
                    if (field == null)
                        unknown[0]++;
                }

                @Override
                public void missing(RecordSpec record, FieldSpec field, CborSpan container, Era era) {
                    unknown[0]++;
                }
            });
            assertThat(unknown[0]).as(block.name()).isZero();
        }
    }

    // ---- what a damaged block does ----

    @Test
    void aRealBlockCutAfterItsHeaderStillHasItsHeader() {
        int cuts = 0;
        for (RealCborFixturesAccess.Block block : RealCborFixturesAccess.blocks()) {
            byte[] cbor = block.cbor();
            CborSpan header = RawBlock.of(cbor).header();
            int headerEnd = header.offset() + header.length();
            int step = Math.max(1, (cbor.length - headerEnd) / 16);
            for (int length = headerEnd; length < cbor.length; length += step) {
                byte[] cut = Arrays.copyOf(cbor, length);
                assertThatThrownBy(() -> RawBlock.of(cut)).isInstanceOf(CborRuntimeException.class);
                assertThatThrownBy(() -> BlockEnvelope.of(cut)).isInstanceOf(CborRuntimeException.class);
                RawHeader read = RawHeader.of(BlockEnvelope.headerOf(cut));
                assertThat(read.blockNumber()).as(block.name()).isEqualTo(block.blockHeight());
                assertThat(encodeHexString(read.hash())).as(block.name()).isEqualTo(block.blockHash());
                cuts++;
            }
        }
        assertThat(cuts).isGreaterThan(100);
    }

    @Test
    void aRealBlockCutInsideItsHeaderHasNoHeader() {
        for (RealCborFixturesAccess.Block block : RealCborFixturesAccess.blocks()) {
            byte[] cbor = block.cbor();
            CborSpan header = RawBlock.of(cbor).header();
            int headerEnd = header.offset() + header.length();
            for (int length = 0; length < headerEnd; length += 37) {
                byte[] cut = Arrays.copyOf(cbor, length);
                assertThatThrownBy(() -> BlockEnvelope.headerOf(cut)).as(block.name())
                        .isInstanceOf(CborRuntimeException.class);
            }
        }
    }

    /** Each part after the header in turn replaced with a reserved head, so the block is malformed from there. */
    @Test
    void aRealBlockMalformedAfterItsHeaderStillHasItsHeader() {
        for (RealCborFixturesAccess.Block block : RealCborFixturesAccess.blocks()) {
            RawBlock raw = RawBlock.of(block.cbor());
            for (CborSpan part : List.of(raw.transactionBodies(), raw.transactionWitnessSets(),
                    raw.auxiliaryDataSet())) {
                byte[] damaged = block.cbor().clone();
                damaged[part.offset()] = (byte) 0xfc;
                assertThatThrownBy(() -> RawBlock.of(damaged)).isInstanceOf(CborRuntimeException.class);
                assertThat(RawHeader.of(BlockEnvelope.headerOf(damaged)).blockNumber()).as(block.name())
                        .isEqualTo(block.blockHeight());
            }
        }
    }
}
