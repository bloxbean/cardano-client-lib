package com.bloxbean.cardano.client.transaction.spec.conformance;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.cert.AuthCommitteeHotCert;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.cert.GenesisKeyDelegation;
import com.bloxbean.cardano.client.transaction.spec.cert.MoveInstataneous;
import com.bloxbean.cardano.client.transaction.spec.cert.PoolRegistration;
import com.bloxbean.cardano.client.transaction.spec.cert.PoolRetirement;
import com.bloxbean.cardano.client.transaction.spec.cert.RegCert;
import com.bloxbean.cardano.client.transaction.spec.cert.RegDRepCert;
import com.bloxbean.cardano.client.transaction.spec.cert.ResignCommitteeColdCert;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeCredential;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeDelegation;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeDeregistration;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeRegDelegCert;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeRegistration;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeVoteDelegCert;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeVoteRegDelegCert;
import com.bloxbean.cardano.client.transaction.spec.cert.UnregCert;
import com.bloxbean.cardano.client.transaction.spec.cert.UnregDRepCert;
import com.bloxbean.cardano.client.transaction.spec.cert.UpdateDRepCert;
import com.bloxbean.cardano.client.transaction.spec.cert.VoteDelegCert;
import com.bloxbean.cardano.client.transaction.spec.cert.VoteRegDelegCert;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.transaction.util.CredentialSerializer;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what every public certificate decoder of this module does over cardano-cbor-dataset's Conway samples of the
 * rules a certificate is made of, valid and invalid alike (ADR 0003 I5, I6): whether it accepts a sample and, when it
 * does, the model it returns. {@code cbor-conformance/certificate-outcomes.txt} holds, for each rule, category and
 * entry point, the number of samples, how many were accepted and a digest of the outcomes. It was generated from the
 * decoders as they were before they moved onto the reader. The dataset has no licence, so nothing of a sample is kept
 * here.
 * <p>
 * Runs only with {@code -PcborDataset=<dataset dir>}. It writes the lines it computed to
 * {@code build/cbor-conformance/certificate-outcomes.txt}, and those are the file to compare or, for an approved
 * difference, to commit. Deleted with the other differential tests once the milestone has shipped (ADR 0003 G7).
 */
@EnabledIfSystemProperty(named = "cbor.dataset", matches = ".+")
class CertificateOutcomesTest {
    private static final String OUTCOMES = "/cbor-conformance/certificate-outcomes.txt";
    private static final String INPUT = ".input.cbor";
    private static final String HASH28 = "581c" + "00".repeat(28);
    private static final String HASH32 = "5820" + "00".repeat(32);
    private static final String REWARD_ACCOUNT = "581de0" + "00".repeat(28);
    private static final String MARGIN = "d81e820001";
    private static final String MIN_BODY_KEYS = "0080" + "0180" + "0200";

    @FunctionalInterface
    private interface Entry {
        Object decode(byte[] cbor) throws Exception;
    }

    private static final java.util.Map<String, java.util.Map<String, Entry>> RULES = rules();

    private static java.util.Map<String, java.util.Map<String, Entry>> rules() {
        java.util.Map<String, java.util.Map<String, Entry>> rules = new TreeMap<>();
        rules.put("certificate", certificateEntries());
        rules.put("certificates", java.util.Map.of("TransactionBody",
                c -> body(cat(hex("a4" + MIN_BODY_KEYS + "04"), c))));
        rules.put("transaction_body", java.util.Map.of("TransactionBody", CertificateOutcomesTest::body));
        for (String rule : List.of("credential", "stake_credential", "drep_credential", "committee_cold_credential",
                "committee_hot_credential")) {
            rules.put(rule, java.util.Map.of(
                    "StakeCredential", c -> StakeCredential.deserialize(array(c)),
                    "CredentialSerializer", c -> CredentialSerializer.deserialize(array(c))));
        }
        rules.put("drep", java.util.Map.of("DRep", c -> DRep.deserialize(item(c))));
        rules.put("anchor", java.util.Map.of("Anchor", c -> Anchor.deserialize(array(c))));
        rules.put("url", java.util.Map.of("Anchor", c -> Anchor.deserialize(array(cat(hex("82"), c, hex(HASH32))))));
        rules.put("relay", java.util.Map.of("PoolRegistration", c -> pool(hex(MARGIN), hex(REWARD_ACCOUNT),
                cat(hex("81"), c), hex("f6"))));
        rules.put("dns_name", java.util.Map.of("PoolRegistration", c -> pool(hex(MARGIN), hex(REWARD_ACCOUNT),
                cat(hex("818202"), c), hex("f6"))));
        rules.put("pool_metadata", java.util.Map.of("PoolRegistration", c -> pool(hex(MARGIN), hex(REWARD_ACCOUNT),
                hex("80"), c)));
        rules.put("unit_interval", java.util.Map.of("PoolRegistration", c -> pool(c, hex(REWARD_ACCOUNT), hex("80"),
                hex("f6"))));
        rules.put("reward_account", java.util.Map.of("PoolRegistration",
                c -> pool(hex(MARGIN), c, hex("80"), hex("f6"))));
        return rules;
    }

    private static java.util.Map<String, Entry> certificateEntries() {
        java.util.Map<String, Entry> entries = new TreeMap<>();
        entries.put("Certificate", c -> Certificate.deserialize(array(c)));
        entries.put("StakeRegistration", c -> StakeRegistration.deserialize(array(c)));
        entries.put("StakeDeregistration", c -> StakeDeregistration.deserialize(array(c)));
        entries.put("StakeDelegation", c -> StakeDelegation.deserialize(array(c)));
        entries.put("PoolRegistration", c -> PoolRegistration.deserialize(item(c)));
        entries.put("PoolRetirement", c -> PoolRetirement.deserialize(array(c)));
        entries.put("GenesisKeyDelegation", c -> GenesisKeyDelegation.deserialize(array(c)));
        entries.put("MoveInstataneous", c -> MoveInstataneous.deserialize(array(c)));
        entries.put("RegCert", c -> RegCert.deserialize(array(c)));
        entries.put("UnregCert", c -> UnregCert.deserialize(array(c)));
        entries.put("VoteDelegCert", c -> VoteDelegCert.deserialize(array(c)));
        entries.put("StakeVoteDelegCert", c -> StakeVoteDelegCert.deserialize(item(c)));
        entries.put("StakeRegDelegCert", c -> StakeRegDelegCert.deserialize(array(c)));
        entries.put("VoteRegDelegCert", c -> VoteRegDelegCert.deserialize(array(c)));
        entries.put("StakeVoteRegDelegCert", c -> StakeVoteRegDelegCert.deserialize(array(c)));
        entries.put("AuthCommitteeHotCert", c -> AuthCommitteeHotCert.deserialize(array(c)));
        entries.put("ResignCommitteeColdCert", c -> ResignCommitteeColdCert.deserialize(item(c)));
        entries.put("RegDRepCert", c -> RegDRepCert.deserialize(item(c)));
        entries.put("UnregDRepCert", c -> UnregDRepCert.deserialize(item(c)));
        entries.put("UpdateDRepCert", c -> UpdateDRepCert.deserialize(item(c)));
        return entries;
    }

    private static TransactionBody body(byte[] cbor) throws Exception {
        return TransactionBody.deserialize((Map) item(cbor));
    }

    private static PoolRegistration pool(byte[] margin, byte[] rewardAccount, byte[] relays, byte[] metadata)
            throws Exception {
        return PoolRegistration.deserialize(item(cat(hex("8a03" + HASH28 + HASH32 + "0000"), margin, rewardAccount,
                hex("80"), relays, metadata)));
    }

    @Test
    void outcomesAreUnchanged() throws Exception {
        List<String> actual = outcomes();
        Path written = Path.of("build", "cbor-conformance", "certificate-outcomes.txt");
        Files.createDirectories(written.getParent());
        Files.write(written, actual);

        assertThat(actual).isEqualTo(expected());
    }

    private static List<String> outcomes() throws IOException, NoSuchAlgorithmException {
        List<String> lines = new ArrayList<>();
        Path conway = Path.of(System.getProperty("cbor.dataset")).resolve("conway");
        for (var rule : RULES.entrySet()) {
            List<Path> categories;
            try (Stream<Path> directories = Files.list(conway.resolve(rule.getKey()))) {
                categories = directories.filter(Files::isDirectory).sorted().collect(Collectors.toList());
            }
            for (Path category : categories) {
                List<Path> samples = samples(category);
                for (var entry : new TreeMap<>(rule.getValue()).entrySet())
                    lines.add(rule.getKey() + " " + category.getFileName() + " " + entry.getKey() + " "
                            + digest(samples, entry.getValue()));
            }
        }
        return lines;
    }

    private static List<Path> samples(Path category) throws IOException {
        try (Stream<Path> files = Files.list(category)) {
            return files.filter(file -> file.getFileName().toString().endsWith(INPUT)).sorted()
                    .collect(Collectors.toList());
        }
    }

    /** @return the number of samples, how many were accepted, and a digest of each sample's outcome in order */
    private static String digest(List<Path> samples, Entry entry) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        int accepted = 0;
        for (Path sample : samples) {
            String outcome;
            try {
                outcome = describe(entry.decode(Files.readAllBytes(sample)));
                accepted++;
            } catch (Exception e) {
                outcome = "rejected";
            }
            digest.update((sample.getFileName() + " " + outcome + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return samples.size() + " " + accepted + " " + HexFormat.of().formatHex(digest.digest(), 0, 12);
    }

    /** The whole model by reflection, as most certificates define no {@code equals}. Sets are sorted. */
    static String describe(Object value) {
        if (value == null || value instanceof String || value instanceof BigInteger || value instanceof Number
                || value instanceof Boolean || value instanceof Enum)
            return String.valueOf(value);
        if (value instanceof byte[])
            return HexUtil.encodeHexString((byte[]) value);
        if (value instanceof InetAddress)
            return ((InetAddress) value).getHostAddress();
        if (value instanceof Set)
            return ((Set<?>) value).stream().map(CertificateOutcomesTest::describe)
                    .collect(Collectors.toCollection(TreeSet::new)).toString();
        if (value instanceof Collection)
            return ((Collection<?>) value).stream().map(CertificateOutcomesTest::describe).collect(Collectors.toList())
                    .toString();
        if (value instanceof java.util.Map) {
            java.util.Map<String, String> entries = new LinkedHashMap<>();
            ((java.util.Map<?, ?>) value).forEach((k, v) -> entries.put(describe(k), describe(v)));
            return entries.toString();
        }
        if (!value.getClass().getName().startsWith("com.bloxbean."))
            return value.toString();
        StringBuilder out = new StringBuilder(value.getClass().getSimpleName()).append('{');
        for (Class<?> type = value.getClass(); type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()))
                    continue;
                field.setAccessible(true);
                try {
                    out.append(field.getName()).append('=').append(describe(field.get(value))).append(',');
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return out.append('}').toString();
    }

    private static List<String> expected() throws IOException {
        try (InputStream in = CertificateOutcomesTest.class.getResourceAsStream(OUTCOMES)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.isEmpty() && !line.startsWith("#")).collect(Collectors.toList());
        }
    }

    private static DataItem item(byte[] cbor) {
        return CborSerializationUtil.deserialize(cbor);
    }

    private static Array array(byte[] cbor) {
        return (Array) item(cbor);
    }

    private static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Stream.of(parts).forEach(out::writeBytes);
        return out.toByteArray();
    }

    private static byte[] hex(String hex) {
        return HexFormat.of().parseHex(hex);
    }
}
