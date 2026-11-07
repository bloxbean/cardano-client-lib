package com.bloxbean.cardano.client.tools.readerdiff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The commands on real sides: {@code base} is this build's own jars, and {@code synthetic} is the same with a patched
 * {@code Blake2bUtil} first, whose only effect is a wrong {@code blockHash} ({@code src/synthetic}).
 */
class ReaderDiffEndToEndTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ALLOW_BLOCK_HASH = "header.headerBody.blockHash expected=block-header-hash";

    @TempDir
    Path dir;
    private Path base;
    private Path synthetic;
    private Path blocks;
    private Path out;

    @BeforeEach
    void sides() throws Exception {
        base = Files.createDirectory(dir.resolve("base"));
        synthetic = Files.createDirectory(dir.resolve("synthetic"));
        for (String jar : System.getProperty("reader.diff.jars").split(File.pathSeparator)) {
            Path source = Path.of(jar);
            Files.createSymbolicLink(base.resolve(source.getFileName()), source);
            Files.createSymbolicLink(synthetic.resolve(source.getFileName()), source);
        }
        Files.createSymbolicLink(synthetic.resolve("000-synthetic.jar"),
                Path.of(System.getProperty("reader.diff.synthetic")));
        blocks = dir.resolve("blocks.bin");
        write(blocks, Fixtures.block());
        out = dir.resolve("out");
    }

    private static void write(Path file, byte[]... blocks) throws Exception {
        try (DataOutputStream stream = Blocks.writer(file)) {
            for (byte[] block : blocks)
                Blocks.write(stream, block);
        }
    }

    private int diff(Path oldSide, Path newSide, String... more) throws Exception {
        List<String> args = new ArrayList<>(List.of("diff", "--old=" + oldSide, "--new=" + newSide, "--blocks=" + blocks,
                "--out=" + out, "--workers=2"));
        args.addAll(List.of(more));
        return ReaderDiff.run(args.toArray(String[]::new));
    }

    private String summary() throws Exception {
        return Files.readString(out.resolve("summary.txt"));
    }

    private List<JsonNode> differences() throws Exception {
        List<JsonNode> nodes = new ArrayList<>();
        for (String line : Files.readAllLines(out.resolve("differences.jsonl")))
            nodes.add(JSON.readTree(line));
        return nodes;
    }

    private String allowFile() throws Exception {
        return "--allow=" + Files.writeString(dir.resolve("allow.txt"), "# D8\n" + ALLOW_BLOCK_HASH + "\n");
    }

    @Test
    void sidesAreIsolatedFromTheToolAndEachOther() throws Exception {
        try (Side one = Side.open(base); Side two = Side.open(base)) {
            ClassLoader first = one.probes().get("yaci-block").getClass().getClassLoader();
            ClassLoader second = two.probes().get("yaci-block").getClass().getClassLoader();
            assertNotSame(first, second);
            assertNotSame(getClass().getClassLoader(), first);
            assertEquals(ClassLoader.getPlatformClassLoader(), first.getParent());
            assertTrue(one.probes().get("yaci-block").apply(Fixtures.block()).startsWith("A:{\"@class\":"));
            assertTrue(one.probes().get("yaci-block").apply(new byte[]{1}).startsWith("R:"));
        }
    }

    @Test
    void aSideAgainstItselfHasNoDifferences() throws Exception {
        assertEquals(0, diff(base, base));
        assertTrue(summary().contains("yaci-block  {A/A equal=1}"), summary());
        assertTrue(summary().contains("ccl-tx  {A/A equal=3}"), summary());
        assertTrue(summary().contains("differences: 0 "), summary());
        assertTrue(differences().isEmpty());
    }

    @Test
    void findsTheFieldASideGetsWrong() throws Exception {
        assertEquals(1, diff(base, synthetic));
        assertTrue(summary().contains("yaci-block  {A/A different=1}"), summary());
        assertTrue(summary().contains("ccl-tx  {A/A equal=3}"), summary());
        JsonNode difference = differences().getFirst();
        assertEquals(1, differences().size());
        assertEquals("A/A different", difference.path("kind").asText());
        assertEquals("yaci-block", difference.path("probe").asText());
        assertEquals("babbage", difference.path("group").asText());
        assertEquals("header.headerBody.blockHash", difference.path("path").asText());
        assertEquals(1, difference.path("differingFields").asInt());
        assertEquals(Fixtures.blockHex(), difference.path("inputHex").asText());
    }

    @Test
    void anAllowedFieldIsReportedAndCheckedNotDropped() throws Exception {
        // the new side corrects the field: allowed, and it matches the value computed from the original bytes
        assertEquals(0, diff(synthetic, base, allowFile()));
        assertTrue(summary().contains("yaci-block  {A/A allowed=1}"), summary());
        assertTrue(summary().contains(
                "header.headerBody.blockHash  {differ=1, new matches expected=1, old differs from expected=1}"), summary());

        // the new side breaks the field: being on the list does not hide it
        assertEquals(1, diff(base, synthetic, allowFile()));
        assertTrue(summary().contains("yaci-block  {A/A expected-mismatch=1}"), summary());
        assertEquals("header.headerBody.blockHash", differences().getFirst().path("path").asText());
    }

    /** ADR 0003 D8's example: the block number's encoding changed, which is where Yaci's hash is wrong today. */
    @Test
    void theExpectedHashComesFromTheOriginalBytes() throws Exception {
        String changed = Fixtures.blockHex().replace("1a0004774b", "1b000000000004774b");
        write(blocks, HexFormat.of().parseHex(changed));

        assertEquals(0, diff(base, base));
        assertEquals(1, diff(base, base, allowFile()));
        JsonNode difference = differences().getFirst();
        assertEquals("A/A expected-mismatch", difference.path("kind").asText());
        assertTrue(difference.path("old").asText().startsWith("1cf993e9"), difference.toString());
        assertTrue(difference.path("new").asText().contains("(expected 73ee9b5e"), difference.toString());
    }

    /** ADR 0003 D8: an appended top-level value, which only some readers tolerate, must not stop a run. */
    @Test
    void anInputTheToolCannotReadStillReachesTheDecoders() throws Exception {
        write(blocks, HexFormat.of().parseHex(Fixtures.blockHex() + "00"));
        assertEquals(1, diff(base, base));
        assertTrue(summary().contains("yaci-block  unreadable  {A/A equal=1}"), summary());
        assertTrue(summary().contains("ccl-tx  unreadable  {split-failed=1}"), summary());
    }

    @Test
    void comparesDatasetSamplesPerDecoder() throws Exception {
        Path dataset = dir.resolve("conway");
        sample(dataset.resolve("credential/valid"), "8200581c" + "00".repeat(28));
        sample(dataset.resolve("credential/invalid-zap-0"), "8200");
        sample(dataset.resolve("vote/manual-valid"), "00");
        sample(dataset.resolve("not_a_rule/valid"), "00");

        assertEquals(0, ReaderDiff.run(new String[]{"diff-dataset", "--old=" + base, "--new=" + base,
                "--dataset=" + dataset, "--out=" + out, "--workers=2"}));
        String summary = summary();
        assertTrue(summary.contains("ccl:credential  valid  {A/A equal=1}"), summary);
        assertTrue(summary.contains("yaci:credential  valid  {A/A equal=1}"), summary);
        assertTrue(summary.contains("ccl:vote  valid  {A/A equal=1}"), summary);
        assertTrue(summary.contains("{not_a_rule=1}"), summary);
        assertFalse(summary.contains("A/R"), summary);
    }

    private static void sample(Path directory, String hex) throws Exception {
        Files.createDirectories(directory);
        Files.write(directory.resolve("00001.input.cbor"), HexFormat.of().parseHex(hex));
    }

    @Test
    void benchWritesEveryRound() throws Exception {
        Path result = dir.resolve("bench/base.json");
        assertEquals(0, ReaderDiff.run(new String[]{"bench", "--side=" + base, "--blocks=" + blocks, "--out=" + result,
                "--rounds=3"}));
        JsonNode json = JSON.readTree(result.toFile());
        assertEquals(1, json.path("blocks").asInt());
        JsonNode block = json.path("probes").path("yaci-block");
        assertEquals(0, block.path("rejected").asInt());
        assertEquals(3, block.path("wallNanosPerBlock").path("rounds").size());
        assertTrue(block.path("allocatedBytesPerBlock").path("mean").asDouble() > 0);
        assertEquals(3, json.path("probes").path("ccl-tx").path("inputs").asInt());
    }
}
