package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.aiken.AikenScriptUtil;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.it.BaseIT;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintLoader;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.transaction.spec.governance.Vote;
import com.bloxbean.cardano.client.transaction.spec.governance.Voter;
import com.bloxbean.cardano.client.transaction.spec.governance.VoterType;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovActionId;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.InfoAction;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Property-style check of Voting redeemer indices on Yaci DevKit (API: localhost:8080, admin: localhost:10000).
 * <p>
 * Setup registers three script DReps and two key DReps, then creates two InfoAction proposals. One key DRep's
 * hash sorts before every script hash, so hash-only ordering would shift the script indices. Each trial then
 * votes from a random subset of DReps, on one or both proposals, in a random insertion order, and the
 * transaction must be confirmed.
 * <p>
 * The script DReps use the {@code vote_redeemer} Aiken validator (src/it/resources/blueprint/vote_redeemer),
 * applied with a different integer per DRep. Each script accepts only its own integer as redeemer, so a swapped
 * redeemer between two script voters fails phase-2 validation.
 * <p>
 * Run with {@code ./gradlew :quicktx:integrationTest --tests '*VotingOrderingIT' --rerun-tasks}.
 * Reproduce a run with {@code VOTING_IT_SEED=<seed>}; change the number of random trials with
 * {@code VOTING_IT_TRIALS=<n>} (environment variables, as Gradle does not forward {@code -D} to the test JVM;
 * the system properties {@code voting.it.seed} / {@code voting.it.trials} also work).
 * Uses a fresh funded account without resetting the running devnet.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VotingOrderingIT extends BaseIT {
    private static final Logger log = LoggerFactory.getLogger(VotingOrderingIT.class);

    private static final long SEED = Long.parseLong(setting("voting.it.seed", "VOTING_IT_SEED", String.valueOf(System.nanoTime())));
    private static final int RANDOM_TRIALS = Integer.parseInt(setting("voting.it.trials", "VOTING_IT_TRIALS", "6"));
    private static final Anchor ANCHOR = new Anchor("https://xyz.com",
            HexUtil.decodeHexString("cafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

    private BackendService backendService;
    private Account payer;
    // Sorted by script hash; index i in this list is the script voter's ledger position among script DReps
    private List<ScriptDRep> scriptDReps;
    // Sorted by DRep key hash
    private final List<Account> keyDReps = new ArrayList<>();
    private final List<GovActionId> proposals = new ArrayList<>();

    @BeforeAll
    void setup() throws Exception {
        assumeTrue(DEVKIT.equals(backendType), "This test funds accounts using the DevKit faucet");
        log.info("VotingOrderingIT seed: {}, random trials: {} (rerun with VOTING_IT_SEED={})", SEED, RANDOM_TRIALS, SEED);
        backendService = getBackendService();
        payer = new Account(Networks.testnet());
        Random random = new Random(SEED);

        // Fresh parameters per run give fresh script hashes, so reruns never hit an already registered DRep
        String compiledCode = loadCompiledCode();
        Set<Integer> params = new LinkedHashSet<>();
        while (params.size() < 3)
            params.add(1 + random.nextInt(1_000_000));
        scriptDReps = params.stream()
                .map(param -> scriptDRep(compiledCode, param))
                .sorted(Comparator.comparing(ScriptDRep::hash))
                .collect(Collectors.toList());
        assertThat(scriptDReps).extracting(ScriptDRep::hash).doesNotHaveDuplicates();

        // One key hash must precede every script hash; the second key DRep is random
        String lowestScriptHash = scriptDReps.get(0).hash();
        for (int attempt = 0; attempt < 1000 && keyDReps.isEmpty(); attempt++) {
            Account candidate = new Account(Networks.testnet());
            if (keyHash(candidate).compareTo(lowestScriptHash) < 0)
                keyDReps.add(candidate);
        }
        assertThat(keyDReps).as("a DRep key hash lower than %s", lowestScriptHash).hasSize(1);
        keyDReps.add(new Account(Networks.testnet()));
        keyDReps.sort(Comparator.comparing(VotingOrderingIT::keyHash));

        scriptDReps.forEach(s -> log.info("Script DRep {} (param {}): {}", s.name(), s.param(), s.hash()));
        for (int i = 0; i < keyDReps.size(); i++)
            log.info("Key DRep K{}: {}", i, keyHash(keyDReps.get(i)));

        fundPayer();

        // Script DRep registration runs the publish handler with the DRep's own redeemer. A replayed seed gives
        // the same scripts, which may already be registered; only the ledger's explicit rejection is accepted.
        for (ScriptDRep script : scriptDReps) {
            Tx registration = new Tx()
                    .registerDRep(script.credential(), ANCHOR, BigIntPlutusData.of(script.param()))
                    .attachCertificateValidator(script.validator())
                    .from(payer.baseAddress());
            TxResult result = new QuickTxBuilder(backendService).compose(registration)
                    .withSigner(SignerProviders.signerFrom(payer))
                    .mergeOutputs(false)
                    .completeAndWait(Duration.ofMinutes(2), log::info);
            if (result.isSuccessful())
                assertConfirmed(result, "register " + script.name());
            else {
                assertThat(result.getResponse()).as("register %s", script.name()).contains("DRepAlreadyRegistered");
                log.info("Script DRep {} already registered (replayed seed)", script.name());
            }
        }

        Tx keyRegistration = new Tx().from(payer.baseAddress());
        keyDReps.forEach(account -> keyRegistration.registerDRep(account, ANCHOR));
        assertConfirmed(new QuickTxBuilder(backendService).compose(keyRegistration)
                .withSigner(SignerProviders.signerFrom(payer))
                .withSigner(SignerProviders.drepKeySignerFrom(keyDReps.get(0)))
                .withSigner(SignerProviders.drepKeySignerFrom(keyDReps.get(1)))
                .mergeOutputs(false)
                .completeAndWait(Duration.ofMinutes(2), log::info), "register key DReps");

        // The proposal deposit return account must be registered before the proposals
        assertConfirmed(new QuickTxBuilder(backendService)
                .compose(new Tx().registerStakeAddress(payer.stakeAddress()).from(payer.baseAddress()))
                .withSigner(SignerProviders.signerFrom(payer))
                .mergeOutputs(false)
                .completeAndWait(Duration.ofMinutes(2), log::info), "register return account");

        // InfoAction has no prev-action or constitution guardrail dependencies
        for (int i = 0; i < 2; i++) {
            TxResult result = new QuickTxBuilder(backendService)
                    .compose(new Tx().createProposal(new InfoAction(), payer.stakeAddress(), ANCHOR)
                            .from(payer.baseAddress()))
                    .withSigner(SignerProviders.signerFrom(payer))
                    .mergeOutputs(false)
                    .completeAndWait(Duration.ofMinutes(2), log::info);
            assertConfirmed(result, "create proposal " + i);
            proposals.add(new GovActionId(result.getTxHash(), 0));
        }
    }

    Stream<Arguments> trials() {
        List<Arguments> trials = new ArrayList<>();
        // Fixed worst case: every voter, keys first, scripts in descending hash order, every vote on both proposals
        List<Ballot> fixed = new ArrayList<>();
        for (int k = keyDReps.size() - 1; k >= 0; k--)
            for (int p = 0; p < proposals.size(); p++)
                fixed.add(new Ballot(VoterRef.key(k), p, Vote.YES));
        for (int s = scriptDReps.size() - 1; s >= 0; s--)
            for (int p = 0; p < proposals.size(); p++)
                fixed.add(new Ballot(VoterRef.script(s), p, Vote.NO));
        trials.add(Arguments.of("fixed: keys first, scripts descending, both proposals", fixed));

        Random random = new Random(SEED ^ 0x5DEECE66DL);
        for (int t = 0; t < RANDOM_TRIALS; t++) {
            List<Ballot> ballots = randomBallots(random);
            trials.add(Arguments.of("random #" + t + ": " + describe(ballots), ballots));
        }
        return trials.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("trials")
    void votesFromMixedDReps_confirmedWithLedgerOrderIndexes(String description, List<Ballot> ballots) throws ApiException {
        Tx voteTx = new Tx().from(payer.baseAddress());
        Set<Integer> scriptsUsed = new LinkedHashSet<>();
        Set<Integer> keysUsed = new LinkedHashSet<>();
        for (Ballot ballot : ballots) {
            if (ballot.voter().script()) {
                ScriptDRep script = scriptDReps.get(ballot.voter().index());
                voteTx.createVote(script.voter(), proposals.get(ballot.proposal()), ballot.vote(), null,
                        BigIntPlutusData.of(script.param()));
                scriptsUsed.add(ballot.voter().index());
            } else {
                voteTx.createVote(keyVoter(keyDReps.get(ballot.voter().index())), proposals.get(ballot.proposal()),
                        ballot.vote());
                keysUsed.add(ballot.voter().index());
            }
        }
        scriptsUsed.forEach(s -> voteTx.attachVotingValidator(scriptDReps.get(s).validator()));

        // One withSigner call per key: QuickTxBuilder sizes the fee from the number of signers
        QuickTxBuilder.TxContext context = new QuickTxBuilder(backendService).compose(voteTx)
                .withSigner(SignerProviders.signerFrom(payer));
        for (int k : keysUsed)
            context = context.withSigner(SignerProviders.drepKeySignerFrom(keyDReps.get(k)));
        TxResult result = context
                .withTxInspector(transaction -> assertVotingRedeemers(transaction, scriptsUsed, keysUsed))
                .mergeOutputs(false)
                .completeAndWait(Duration.ofMinutes(2), log::info);
        assertConfirmed(result, description);
        log.info("Confirmed {}: {}", description, result.getTxHash());
    }

    @Test
    void swappedRedeemer_rejectedByScript() {
        // Guards the fixture: if scripts accepted any redeemer, the trials above could not detect a swap
        ScriptDRep s0 = scriptDReps.get(0);
        ScriptDRep s1 = scriptDReps.get(1);
        Tx voteTx = new Tx()
                .createVote(s0.voter(), proposals.get(0), Vote.YES, null, BigIntPlutusData.of(s1.param()))
                .attachVotingValidator(s0.validator())
                .from(payer.baseAddress());

        boolean rejected;
        try {
            TxResult result = new QuickTxBuilder(backendService).compose(voteTx)
                    .withSigner(SignerProviders.signerFrom(payer))
                    .mergeOutputs(false)
                    .completeAndWait(Duration.ofMinutes(1), log::info);
            rejected = !result.isSuccessful();
            log.info("Swapped redeemer result: {}", result.getResponse());
        } catch (Exception e) {
            log.info("Swapped redeemer rejected: {}", e.getMessage());
            rejected = true;
        }
        assertThat(rejected).as("script %s must reject the redeemer of %s", s0.name(), s1.name()).isTrue();
    }

    private void assertVotingRedeemers(Transaction transaction, Set<Integer> scriptsUsed, Set<Integer> keysUsed) {
        // Expected ledger order, computed independently of VoterUtil: all voters are DReps, so script
        // credentials first, then key credentials, each by hash. scriptDReps and keyDReps are hash-sorted.
        List<Voter> expectedOrder = new ArrayList<>();
        scriptsUsed.stream().sorted().forEach(s -> expectedOrder.add(scriptDReps.get(s).voter()));
        keysUsed.stream().sorted().forEach(k -> expectedOrder.add(keyVoter(keyDReps.get(k))));
        assertThat(transaction.getBody().getVotingProcedures().getVoting().keySet())
                .containsExactlyInAnyOrderElementsOf(expectedOrder);

        List<Redeemer> redeemers = transaction.getWitnessSet().getRedeemers();
        assertThat(redeemers).hasSize(scriptsUsed.size());
        assertThat(redeemers).allSatisfy(redeemer -> assertThat(redeemer.getTag()).isEqualTo(RedeemerTag.Voting));
        for (int s : scriptsUsed) {
            ScriptDRep script = scriptDReps.get(s);
            BigInteger expectedIndex = BigInteger.valueOf(expectedOrder.indexOf(script.voter()));
            assertThat(redeemers).filteredOn(redeemer -> expectedIndex.equals(redeemer.getIndex()))
                    .as("Voting redeemer of %s at index %s", script.name(), expectedIndex)
                    .singleElement()
                    .satisfies(redeemer -> assertThat(redeemer.getData()).isEqualTo(BigIntPlutusData.of(script.param())));
        }
    }

    private List<Ballot> randomBallots(Random random) {
        List<VoterRef> voters = new ArrayList<>();
        // At least two script voters most of the time, so their relative order matters
        List<Integer> scripts = IntStream.range(0, scriptDReps.size()).boxed().collect(Collectors.toList());
        Collections.shuffle(scripts, random);
        int scriptCount = random.nextInt(4) == 0 ? 1 : 2 + random.nextInt(scriptDReps.size() - 1);
        scripts.subList(0, scriptCount).forEach(s -> voters.add(VoterRef.script(s)));
        for (int k = 0; k < keyDReps.size(); k++)
            if (random.nextBoolean())
                voters.add(VoterRef.key(k));

        Vote[] votes = Vote.values();
        List<Ballot> ballots = new ArrayList<>();
        for (VoterRef voter : voters) {
            // Voting on both proposals exercises one redeemer per voter
            List<Integer> chosen = random.nextBoolean() ? List.of(0, 1) : List.of(random.nextInt(proposals.size()));
            for (int p : chosen)
                ballots.add(new Ballot(voter, p, votes[random.nextInt(votes.length)]));
        }
        Collections.shuffle(ballots, random);
        return ballots;
    }

    private void fundPayer() throws Exception {
        ProtocolParams params = backendService.getEpochService().getProtocolParameters().getValue();
        BigInteger required = params.getDrepDeposit().multiply(BigInteger.valueOf(5))
                .add(params.getGovActionDeposit().multiply(BigInteger.valueOf(2)))
                .add(new BigInteger(params.getKeyDeposit()))
                .add(BigInteger.valueOf(200_000_000L)); // fees and collateral headroom
        log.info("Required for deposits and fees: {} lovelace", required);

        topupIfNeeded(payer.baseAddress());
        DefaultUtxoSupplier utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
        for (int attempt = 0; attempt < 60 && utxoSupplier.getAll(payer.baseAddress()).isEmpty(); attempt++)
            Thread.sleep(1000);
        BigInteger balance = utxoSupplier.getAll(payer.baseAddress()).stream()
                .flatMap(utxo -> utxo.getAmount().stream())
                .filter(amount -> "lovelace".equals(amount.getUnit()))
                .map(Amount::getQuantity)
                .reduce(BigInteger.ZERO, BigInteger::add);
        assertThat(balance).as("DevKit faucet funding vs deposits (DRep %s, gov action %s)",
                params.getDrepDeposit(), params.getGovActionDeposit()).isGreaterThanOrEqualTo(required);
    }

    private String loadCompiledCode() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/blueprint/vote_redeemer/plutus.json")) {
            assertThat(is).as("vote_redeemer blueprint").isNotNull();
            return PlutusBlueprintLoader.loadBlueprint(is).getValidators().stream()
                    .filter(v -> v.getTitle().equals("vote_redeemer.vote_redeemer.vote"))
                    .findFirst().orElseThrow()
                    .getCompiledCode();
        }
    }

    private static ScriptDRep scriptDRep(String compiledCode, int param) {
        PlutusScript validator = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(
                AikenScriptUtil.applyParamToScript(ListPlutusData.of(BigIntPlutusData.of(param)), compiledCode),
                PlutusVersion.v3);
        try {
            return new ScriptDRep(validator, param, HexUtil.encodeHexString(validator.getScriptHash()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String setting(String property, String env, String defaultValue) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank())
            value = System.getenv(env);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static Voter keyVoter(Account account) {
        return new Voter(VoterType.DREP_KEY_HASH, account.drepCredential());
    }

    private static String keyHash(Account account) {
        return HexUtil.encodeHexString(account.drepCredential().getBytes());
    }

    private String describe(List<Ballot> ballots) {
        return ballots.stream()
                .map(b -> (b.voter().script() ? "S" : "K") + b.voter().index() + "/P" + b.proposal())
                .collect(Collectors.joining(","));
    }

    private static void assertConfirmed(TxResult result, String what) {
        assertThat(result.isSuccessful()).as("%s submission: %s", what, result.getResponse()).isTrue();
        assertThat(result.getTxStatus()).as("%s block inclusion of %s", what, result.getTxHash()).isEqualTo(TxStatus.CONFIRMED);
    }

    private record ScriptDRep(PlutusScript validator, int param, String hash) {
        String name() {
            return "S(" + hash.substring(0, 8) + ")";
        }

        Credential credential() {
            return Credential.fromScript(HexUtil.decodeHexString(hash));
        }

        Voter voter() {
            return new Voter(VoterType.DREP_SCRIPT_HASH, credential());
        }
    }

    // index refers to the hash-sorted scriptDReps or keyDReps list
    private record VoterRef(boolean script, int index) {
        static VoterRef script(int index) {
            return new VoterRef(true, index);
        }

        static VoterRef key(int index) {
            return new VoterRef(false, index);
        }
    }

    private record Ballot(VoterRef voter, int proposal, Vote vote) {
    }
}
