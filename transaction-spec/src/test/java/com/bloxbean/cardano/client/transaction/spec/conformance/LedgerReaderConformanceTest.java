package com.bloxbean.cardano.client.transaction.spec.conformance;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.raw.CertKind;
import com.bloxbean.cardano.client.transaction.raw.FieldSpec;
import com.bloxbean.cardano.client.transaction.raw.FormatPolicy;
import com.bloxbean.cardano.client.transaction.raw.RawCertificate;
import com.bloxbean.cardano.client.transaction.raw.RawTx;
import com.bloxbean.cardano.client.transaction.raw.RecordSpec;
import com.bloxbean.cardano.client.transaction.raw.TxBodyField;
import com.bloxbean.cardano.client.transaction.raw.WitnessSetField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR 0003 D5, I13: over cardano-cbor-dataset's valid Conway samples, the reader accepts each one, a walk reports no
 * unknown, repeated, out-of-era or missing field, and every accessor of a certificate reads. Runs only with
 * {@code -PcborDataset=<dataset dir>}.
 */
@EnabledIfSystemProperty(named = "cbor.dataset", matches = ".+")
class LedgerReaderConformanceTest {

    private static final FormatPolicy STRUCTURE = new FormatPolicy() {
        @Override
        public void entry(RecordSpec record, FieldSpec field, CborSpan key, CborSpan value, int occurrence, Era era) {
            if (record.shape() == RecordSpec.Shape.POSITIONAL)
                return;
            if (field == null || !field.validIn(era))
                throw new IllegalStateException("unknown key in " + record + ": " + key);
            if (occurrence > 0)
                throw new IllegalStateException("repeated key in " + record + ": " + key);
        }

        @Override
        public void missing(RecordSpec record, FieldSpec field, CborSpan container, Era era) {
            throw new IllegalStateException("missing " + field.type() + " in " + record);
        }
    };

    @Test
    void everyValidCertificateReads() throws IOException {
        assertThat(rejected("certificate", span -> read(RawCertificate.of(span)))).isEmpty();
    }

    @Test
    void everyValidCertificateWalks() throws IOException {
        assertThat(rejected("certificate", span -> CertKind.RECORD.walk(span, Era.Conway, STRUCTURE))).isEmpty();
    }

    @Test
    void everyValidBodyAndWitnessSetWalks() throws IOException {
        assertThat(rejected("transaction_body", span -> TxBodyField.RECORD.walk(span, Era.Conway, STRUCTURE)))
                .isEmpty();
        assertThat(rejected("transaction_witness_set",
                span -> WitnessSetField.RECORD.walk(span, Era.Conway, STRUCTURE))).isEmpty();
    }

    @Test
    void everyValidTransactionWalks() throws IOException {
        assertThat(rejected("transaction", span -> RawTx.walk(span.bytes(), Era.Conway, STRUCTURE))).isEmpty();
    }

    private static void read(RawCertificate cert) {
        switch (cert.kind()) {
            case STAKE_REGISTRATION:
            case STAKE_DEREGISTRATION:
                cert.credential().hash();
                break;
            case STAKE_DELEGATION:
                cert.credential().hash();
                cert.poolKeyHash();
                break;
            case POOL_REGISTRATION:
                cert.poolKeyHash();
                cert.vrfKeyHash();
                cert.pledge();
                cert.cost();
                cert.margin();
                cert.rewardAccount();
                cert.poolOwners();
                cert.relays().forEach(relay -> {
                    switch (relay.kind()) {
                        case SINGLE_HOST_ADDR:
                            relay.port();
                            relay.ipv4();
                            relay.ipv6();
                            break;
                        case SINGLE_HOST_NAME:
                            relay.port();
                            relay.dnsName();
                            break;
                        default:
                            relay.dnsName();
                    }
                });
                cert.poolMetadata().ifPresent(metadata -> {
                    metadata.url();
                    metadata.hash();
                });
                break;
            case POOL_RETIREMENT:
                cert.poolKeyHash();
                cert.epoch();
                break;
            case REG_CERT:
            case UNREG_CERT:
            case UNREG_DREP_CERT:
                cert.credential().hash();
                cert.coin();
                break;
            case VOTE_DELEG_CERT:
                cert.credential().hash();
                cert.drep();
                break;
            case STAKE_VOTE_DELEG_CERT:
                cert.credential().hash();
                cert.poolKeyHash();
                cert.drep();
                break;
            case STAKE_REG_DELEG_CERT:
                cert.credential().hash();
                cert.poolKeyHash();
                cert.coin();
                break;
            case VOTE_REG_DELEG_CERT:
                cert.credential().hash();
                cert.drep();
                cert.coin();
                break;
            case STAKE_VOTE_REG_DELEG_CERT:
                cert.credential().hash();
                cert.poolKeyHash();
                cert.drep();
                cert.coin();
                break;
            case AUTH_COMMITTEE_HOT_CERT:
                cert.credential().hash();
                cert.hotCredential().hash();
                break;
            case RESIGN_COMMITTEE_COLD_CERT:
            case UPDATE_DREP_CERT:
                cert.credential().hash();
                cert.anchor().ifPresent(anchor -> anchor.dataHash());
                break;
            case REG_DREP_CERT:
                cert.credential().hash();
                cert.coin();
                cert.anchor().ifPresent(anchor -> anchor.dataHash());
                break;
            default:
                throw new IllegalStateException("Conway has no " + cert.kind());
        }
    }

    /** @return the valid samples of the rule that the reader rejects, with why */
    private static List<String> rejected(String rule, Consumer<CborSpan> read) throws IOException {
        List<String> rejected = new ArrayList<>();
        for (String category : List.of("valid", "manual-valid")) {
            Path directory = Path.of(System.getProperty("cbor.dataset"), "conway", rule, category);
            if (!Files.isDirectory(directory))
                continue;
            List<Path> samples;
            try (Stream<Path> files = Files.list(directory)) {
                samples = files.filter(file -> file.getFileName().toString().endsWith(".input.cbor")).sorted()
                        .collect(Collectors.toList());
            }
            for (Path sample : samples) {
                try {
                    read.accept(CborSpan.of(Files.readAllBytes(sample)));
                } catch (RuntimeException e) {
                    rejected.add(category + "/" + sample.getFileName() + ": " + e.getMessage());
                }
            }
        }
        return rejected;
    }
}
