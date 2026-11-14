package com.bloxbean.cardano.client.transaction.spec.conformance;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.raw.CertKind;
import com.bloxbean.cardano.client.transaction.raw.FieldType;
import com.bloxbean.cardano.client.transaction.raw.RawAnchor;
import com.bloxbean.cardano.client.transaction.raw.RawBootstrapWitness;
import com.bloxbean.cardano.client.transaction.raw.RawConstitution;
import com.bloxbean.cardano.client.transaction.raw.RawCostModels;
import com.bloxbean.cardano.client.transaction.raw.RawCredential;
import com.bloxbean.cardano.client.transaction.raw.RawDRep;
import com.bloxbean.cardano.client.transaction.raw.RawExUnitPrices;
import com.bloxbean.cardano.client.transaction.raw.RawExUnits;
import com.bloxbean.cardano.client.transaction.raw.RawGovAction;
import com.bloxbean.cardano.client.transaction.raw.RawGovActionId;
import com.bloxbean.cardano.client.transaction.raw.RawInput;
import com.bloxbean.cardano.client.transaction.raw.RawOutput;
import com.bloxbean.cardano.client.transaction.raw.RawProposal;
import com.bloxbean.cardano.client.transaction.raw.RawProtocolParamUpdate;
import com.bloxbean.cardano.client.transaction.raw.RawProtocolVersion;
import com.bloxbean.cardano.client.transaction.raw.RawRelay;
import com.bloxbean.cardano.client.transaction.raw.RawValue;
import com.bloxbean.cardano.client.transaction.raw.RawVkeyWitness;
import com.bloxbean.cardano.client.transaction.raw.RawVoter;
import com.bloxbean.cardano.client.transaction.raw.RawVotingProcedure;
import com.bloxbean.cardano.client.transaction.raw.RawVotingProcedures;
import com.bloxbean.cardano.client.transaction.raw.StrictLedgerPolicy;
import com.bloxbean.cardano.client.transaction.raw.FieldSpec;
import com.bloxbean.cardano.client.transaction.raw.FormatPolicy;
import com.bloxbean.cardano.client.transaction.raw.GovActionKind;
import com.bloxbean.cardano.client.transaction.raw.ProtocolParamField;
import com.bloxbean.cardano.client.transaction.raw.RawCertificate;
import com.bloxbean.cardano.client.transaction.raw.RawTx;
import com.bloxbean.cardano.client.transaction.raw.RecordSpec;
import com.bloxbean.cardano.client.transaction.raw.TxBodyField;
import com.bloxbean.cardano.client.transaction.raw.WitnessSetField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR 0003 D5, I13: over cardano-cbor-dataset's valid Conway samples, the reader accepts each one, a walk reports no
 * unknown, repeated, out-of-era or missing field, and every accessor of a certificate reads. ADR 0003 D4a, I15: the
 * same over its valid Dijkstra samples that are still valid at the pin, read with {@link Era#Dijkstra}. Runs only
 * with {@code -PcborDataset=<dataset dir>}.
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

    /** Read without an era, every valid Conway transaction stays in Conway (ADR 0004 W6). */
    @Test
    void everyValidTransactionIsReadInConway() throws IOException {
        assertThat(rejected("transaction", span -> {
            if (RawTx.eraOf(span.bytes(), Era.Conway) != Era.Conway)
                throw new IllegalStateException("read in a later era");
        })).isEmpty();
    }

    /**
     * The Dijkstra samples generated with cardano-ledger 6176e413 whose rule is unchanged at the pin, or provably
     * reaches no changed part, of every rule with a table or a typed reader: {@code
     * cbor-conformance/dijkstra-usable-valid.txt} names them (ADR 0003 D4a, I15). They are those of
     * cardano-cbor-dataset {@code 4fd977b2}, the gate's pin, whose Dijkstra samples equal those of {@code d4709b12}, the
     * r6 pin; an earlier revision lacks some. Each is walked in Dijkstra with a policy that also checks every value's
     * shape, and read with its typed reader if it has one.
     */
    @Test
    void everyUsableDijkstraSampleReadsAndWalks() throws IOException {
        Map<String, Consumer<CborSpan>> reads = Map.ofEntries(
                Map.entry("certificate", span -> {
                    RawCertificate cert = RawCertificate.of(span, Era.Dijkstra);
                    read(cert);
                    cert.blsKey();
                    walk(CertKind.RECORD).accept(span);
                }),
                Map.entry("gov_action", span -> {
                    RawGovAction.of(span, Era.Dijkstra);
                    walk(GovActionKind.RECORD).accept(span);
                }),
                Map.entry("protocol_param_update", span -> {
                    RawProtocolParamUpdate.of(span, Era.Dijkstra);
                    walk(ProtocolParamField.RECORD).accept(span);
                }),
                Map.entry("transaction_body", walk(TxBodyField.RECORD)),
                Map.entry("sub_transaction_body", walk(TxBodyField.SUB_RECORD)),
                Map.entry("transaction_witness_set", walk(WitnessSetField.RECORD)),
                Map.entry("mempool_transaction", span -> {
                    RawTx.of(span.bytes()).body(Era.Dijkstra).subTransactions();
                    RawTx.walk(span.bytes(), Era.Dijkstra, StrictLedgerPolicy.INSTANCE);
                }),
                Map.entry("transaction_output", output()),
                Map.entry("alonzo_transaction_output", output()),
                Map.entry("babbage_transaction_output", output()),
                Map.entry("value", span -> {
                    RawValue.of(span).multiAssets();
                    walk(FieldType.VALUE.record()).accept(span);
                }),
                Map.entry("%multiasset<positive_coin>", walk(FieldType.VALUE.record().field(4, Era.Dijkstra).record()
                        .field(1, Era.Dijkstra).record())),
                Map.entry("mint", walk(TxBodyField.MINT.spec().record())),
                Map.entry("withdrawals", walk(FieldType.WITHDRAWALS.record())),
                Map.entry("transaction_input", span -> {
                    RawInput.of(span);
                    walk(TxBodyField.INPUTS.spec().record()).accept(span);
                }),
                Map.entry("datum_option", walk(FieldType.DATUM_OPTION.record())),
                Map.entry("redeemers", walk(WitnessSetField.REDEEMERS_FIELD.spec().record())),
                Map.entry("auxiliary_data", walk(FieldType.OPTIONAL_AUXILIARY_DATA.record())),
                Map.entry("auxiliary_data_array", walk(FieldType.OPTIONAL_AUXILIARY_DATA.record())),
                Map.entry("auxiliary_data_map", walk(FieldType.OPTIONAL_AUXILIARY_DATA.record())),
                Map.entry("auxiliary_scripts", span -> span.items()
                        .forEach(walk(WitnessSetField.NATIVE_SCRIPTS_SET.spec().record()))),
                Map.entry("native_script", walk(WitnessSetField.NATIVE_SCRIPTS_SET.spec().record())),
                Map.entry("plutus_data", walk(WitnessSetField.PLUTUS_DATA_SET.spec().record())),
                Map.entry("%constr<plutus_data>", walk(WitnessSetField.PLUTUS_DATA_SET.spec().record())),
                Map.entry("vkeywitness", span -> {
                    RawVkeyWitness.of(span);
                    walk(WitnessSetField.VKEY_WITNESSES_SET.spec().record()).accept(span);
                }),
                Map.entry("bootstrap_witness", span -> {
                    RawBootstrapWitness.of(span);
                    walk(WitnessSetField.BOOTSTRAP_WITNESSES_SET.spec().record()).accept(span);
                }),
                Map.entry("credential", credential()),
                Map.entry("stake_credential", credential()),
                Map.entry("drep_credential", credential()),
                Map.entry("committee_cold_credential", credential()),
                Map.entry("committee_hot_credential", credential()),
                Map.entry("drep", span -> {
                    RawDRep.of(span);
                    walk(FieldType.DREP.record()).accept(span);
                }),
                Map.entry("anchor", span -> {
                    RawAnchor.of(span);
                    walk(FieldType.ANCHOR.record()).accept(span);
                }),
                Map.entry("constitution", span -> {
                    RawConstitution.of(span);
                    walk(FieldType.CONSTITUTION.record()).accept(span);
                }),
                Map.entry("relay", span -> {
                    RawRelay.of(span);
                    walk(FieldType.RELAY_LIST.record()).accept(span);
                }),
                Map.entry("pool_metadata", walk(FieldType.OPTIONAL_POOL_METADATA.record())),
                Map.entry("gov_action_id", span -> {
                    RawGovActionId.of(span);
                    walk(FieldType.GOV_ACTION_ID.record()).accept(span);
                }),
                Map.entry("voter", span -> {
                    RawVoter.of(span);
                    walk(FieldType.VOTER.record()).accept(span);
                }),
                Map.entry("voting_procedure", span -> {
                    RawVotingProcedure.of(span);
                    walk(FieldType.VOTING_PROCEDURE.record()).accept(span);
                }),
                Map.entry("voting_procedures", span -> {
                    RawVotingProcedures.of(span).voters();
                    walk(TxBodyField.VOTING_PROCEDURES.spec().record()).accept(span);
                }),
                Map.entry("proposal_procedure", span -> {
                    RawProposal.of(span, Era.Dijkstra);
                    walk(TxBodyField.PROPOSAL_PROCEDURES.spec().record()).accept(span);
                }),
                Map.entry("cost_models", span -> {
                    RawCostModels.of(span);
                    walk(FieldType.COST_MODELS.record()).accept(span);
                }),
                Map.entry("ex_units", span -> {
                    RawExUnits.of(span);
                    walk(FieldType.EX_UNITS.record()).accept(span);
                }),
                Map.entry("ex_unit_prices", span -> {
                    RawExUnitPrices.of(span);
                    walk(FieldType.EX_UNIT_PRICES.record()).accept(span);
                }),
                Map.entry("pool_voting_thresholds", walk(FieldType.POOL_VOTING_THRESHOLDS.record())),
                Map.entry("drep_voting_thresholds", walk(FieldType.DREP_VOTING_THRESHOLDS.record())),
                Map.entry("operational_cert", walk(FieldType.OPERATIONAL_CERT.record())),
                Map.entry("protocol_version", span -> {
                    RawProtocolVersion.of(span);
                    walk(FieldType.PROTOCOL_VERSION.record()).accept(span);
                }),
                Map.entry("eb_references_announcement", walk(FieldType.OPTIONAL_EB_REFERENCES_ANNOUNCEMENT.record())),
                Map.entry("account_balance_interval", walk(FieldType.ACCOUNT_BALANCE_INTERVAL.record())));
        Map<String, Integer> listed = new TreeMap<>();
        Map<String, Integer> read = new TreeMap<>();
        List<String> rejected = new ArrayList<>();
        for (String[] sample : dijkstraSamples()) {
            listed.merge(sample[0], 1, Integer::sum);
            Path file = Path.of(System.getProperty("cbor.dataset"), "dijkstra", sample[1]);
            assertThat(file).as("a sample of cardano-cbor-dataset 4fd977b2").exists();
            try {
                reads.get(sample[0]).accept(CborSpan.of(Files.readAllBytes(file)));
                read.merge(sample[0], 1, Integer::sum);
            } catch (RuntimeException e) {
                rejected.add(sample[1] + ": " + e.getMessage());
            }
        }
        assertThat(rejected).containsExactlyElementsOf(DISPUTED_DIJKSTRA);
        assertThat(listed.keySet()).isEqualTo(reads.keySet());
        listed.merge("transaction_witness_set", -DISPUTED_DIJKSTRA.size(), Integer::sum);
        assertThat(read).isEqualTo(listed);
        assertThat(read.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(7575);
    }

    /**
     * Disputed: the dataset marks these valid, and the ledger at the pin rejects them, so the strict policy does
     * (Haskell is the source of truth). An empty set of bootstrap witnesses: from version 12, key 2 is decoded with
     * {@code decodeNonEmptySetLikeEnforceNoDuplicates} (alonzo {@code TxWits.hs}), which fails "Expected a non-empty
     * set" ({@code Decoder.hs}, cardano-ledger 95b36905).
     */
    private static final List<String> DISPUTED_DIJKSTRA = List.of(
            "transaction_witness_set/manual-valid/bootstrap-witnesses-empty.input.cbor: "
                    + "not a NONEMPTY_SET at offset 2");

    private static Consumer<CborSpan> walk(RecordSpec record) {
        return span -> record.walk(span, Era.Dijkstra, StrictLedgerPolicy.INSTANCE);
    }

    private static Consumer<CborSpan> output() {
        return span -> {
            RawOutput.of(span);
            walk(FieldType.TRANSACTION_OUTPUT.record()).accept(span);
        };
    }

    private static Consumer<CborSpan> credential() {
        return span -> {
            RawCredential.of(span).hash();
            walk(FieldType.CREDENTIAL.record()).accept(span);
        };
    }

    // Each listed sample as {rule, path under dataset/dijkstra}.
    private static List<String[]> dijkstraSamples() throws IOException {
        List<String[]> samples = new ArrayList<>();
        try (InputStream in = LedgerReaderConformanceTest.class
                .getResourceAsStream("/cbor-conformance/dijkstra-usable-valid.txt");
             BufferedReader lines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String line = lines.readLine(); line != null; line = lines.readLine()) {
                if (!line.isBlank() && !line.startsWith("#"))
                    samples.add(line.split(" ", 2));
            }
        }
        return samples;
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
