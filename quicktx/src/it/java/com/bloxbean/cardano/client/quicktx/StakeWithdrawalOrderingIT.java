package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.Withdrawal;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Submits withdraw-zero transactions to Yaci DevKit (API: localhost:8080, admin: localhost:10000).
 * Run with {@code ./gradlew :quicktx:integrationTest --tests '*StakeWithdrawalOrderingIT' -PskipSigning --rerun-tasks}.
 * Uses a fresh funded account without resetting the running devnet.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StakeWithdrawalOrderingIT extends QuickTxBaseIT {
    private static final Logger log = LoggerFactory.getLogger(StakeWithdrawalOrderingIT.class);

    private BackendService backendService;
    private Account payer;
    private final List<Account> stakeAccounts = new ArrayList<>();
    private List<ScriptWithdrawal> scripts;
    private List<String> expectedRewardAddresses;

    @BeforeAll
    void setup() throws InterruptedException {
        assumeTrue(DEVKIT.equals(backendType), "This test funds accounts using the DevKit faucet");
        backendService = getBackendService();
        payer = new Account(Networks.testnet());

        // The same Plutus V2 fixtures as ScriptTxIT: each accepts only its own integer redeemer.
        // Distinct redeemers also detect an incorrect ordering among the three scripts.
        scripts = Stream.of(
                        script("581801000032223253330043370e00290010a4c2c6eb40095cd1", 1),
                        script("581801000032223253330043370e00290020a4c2c6eb40095cd1", 2),
                        script("581801000032223253330043370e00290030a4c2c6eb40095cd1", 3))
                .sorted(Comparator.comparing(s -> credentialHash(s.rewardAddress())))
                .toList();

        // Both key hashes must precede every script hash. Hash-only sorting would then give
        // the scripts indices 2, 3, 4, whereas the ledger requires 0, 1, 2.
        String lowestScriptHash = credentialHash(scripts.get(0).rewardAddress());
        for (int attempt = 0; attempt < 1000 && stakeAccounts.size() < 2; attempt++) {
            Account candidate = new Account(Networks.testnet());
            if (credentialHash(candidate.stakeAddress()).compareTo(lowestScriptHash) < 0) {
                stakeAccounts.add(candidate);
            }
        }
        assertThat(stakeAccounts).as("two key hashes lower than %s", lowestScriptHash).hasSize(2);
        stakeAccounts.sort(Comparator.comparing(a -> credentialHash(a.stakeAddress())));

        // Compute the expectation independently of WithdrawalUtil, the code under test.
        expectedRewardAddresses = new ArrayList<>();
        scripts.forEach(script -> expectedRewardAddresses.add(script.rewardAddress()));
        stakeAccounts.forEach(account -> expectedRewardAddresses.add(account.stakeAddress()));
        assertThat(expectedRewardAddresses).doesNotHaveDuplicates();
        for (int i = 0; i < expectedRewardAddresses.size(); i++) {
            log.info("Ledger withdrawal index {}: {} ({})", i, expectedRewardAddresses.get(i),
                    credentialHash(expectedRewardAddresses.get(i)));
        }

        topUpFund(payer.baseAddress(), 1000);
        DefaultUtxoSupplier utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
        for (int attempt = 0; attempt < 60 && utxoSupplier.getAll(payer.baseAddress()).isEmpty(); attempt++) {
            Thread.sleep(1000);
        }
        assertThat(utxoSupplier.getAll(payer.baseAddress())).as("DevKit faucet funding").isNotEmpty();

        // Script stake registration needs no script witness. These fixed scripts may already
        // be registered by another run; only the ledger's explicit rejection is accepted.
        for (ScriptWithdrawal script : scripts) {
            TxResult result = new QuickTxBuilder(backendService)
                    .compose(new Tx().registerStakeAddress(script.rewardAddress()).from(payer.baseAddress()))
                    .withSigner(SignerProviders.signerFrom(payer))
                    .completeAndWait(Duration.ofMinutes(2), log::info);
            if (result.isSuccessful()) {
                assertConfirmed(result);
            } else {
                assertThat(result.getResponse()).as("script stake registration").contains("StakeKeyRegistered");
            }
        }

        // Conway key withdrawals require vote delegation, including for a zero amount.
        Tx registration = new Tx().from(payer.baseAddress())
                .payToAddress(payer.baseAddress(), Amount.ada(10));
        for (Account account : stakeAccounts) {
            registration.registerStakeAddress(account.stakeAddress())
                    .delegateVotingPowerTo(new Address(account.stakeAddress()), DRep.abstain());
        }
        TxResult result = new QuickTxBuilder(backendService).compose(registration)
                .withSigner(SignerProviders.signerFrom(payer))
                .withSigner(SignerProviders.stakeKeySignerFrom(stakeAccounts.get(0)))
                .withSigner(SignerProviders.stakeKeySignerFrom(stakeAccounts.get(1)))
                // Keep a separate pure-ADA output available for collateral.
                .mergeOutputs(false)
                .completeAndWait(Duration.ofMinutes(2), log::info);
        assertConfirmed(result);
    }

    static Stream<Arguments> withdrawalOrders() {
        // Indices refer to the independent ledger order: S0, S1, S2, K0, K1.
        return Stream.of(
                Arguments.of("keys first, scripts descending", List.of(4, 3, 2, 1, 0)),
                Arguments.of("interleaved credentials", List.of(3, 1, 4, 0, 2)),
                Arguments.of("scripts descending, keys last", List.of(2, 1, 0, 4, 3)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("withdrawalOrders")
    void zeroWithdrawalsWithMixedCredentials(String description, List<Integer> insertionOrder) throws ApiException {
        ScriptTx withdrawalTx = new ScriptTx();
        for (int index : insertionOrder) {
            if (index < scripts.size()) {
                ScriptWithdrawal script = scripts.get(index);
                withdrawalTx.withdraw(script.rewardAddress(), BigInteger.ZERO, BigIntPlutusData.of(script.redeemer()))
                        .attachRewardValidator(script.validator());
            } else {
                withdrawalTx.withdraw(expectedRewardAddresses.get(index), BigInteger.ZERO, null);
            }
        }

        // Use the DevKit backend's real evaluator, then submit and wait for block inclusion.
        TxResult result = new QuickTxBuilder(backendService).compose(withdrawalTx)
                .feePayer(payer.baseAddress())
                .withSigner(SignerProviders.signerFrom(payer))
                .withSigner(SignerProviders.stakeKeySignerFrom(stakeAccounts.get(0)))
                .withSigner(SignerProviders.stakeKeySignerFrom(stakeAccounts.get(1)))
                .withTxInspector(this::assertWithdrawalRedeemers)
                .completeAndWait(Duration.ofMinutes(2), log::info);
        assertConfirmed(result);

        Result<TransactionContent> confirmed = backendService.getTransactionService().getTransaction(result.getTxHash());
        assertThat(confirmed.isSuccessful()).as("transaction lookup: %s", confirmed.getResponse()).isTrue();
        // DevKit's lightweight API exposes block_height, but not Blockfrost's block hash,
        // valid_contract or withdrawal/redeemer counts. CONFIRMED above requires normal output 0.
        assertThat(confirmed.getValue().getHash()).isEqualTo(result.getTxHash());
        assertThat(confirmed.getValue().getBlockHeight()).isPositive();
        log.info("Confirmed {}: {} at block height {}", description, result.getTxHash(),
                confirmed.getValue().getBlockHeight());
    }

    private void assertWithdrawalRedeemers(Transaction transaction) {
        // Signing can round-trip canonical CBOR, whose withdrawal map puts keys first.
        // Redeemer indices must still follow ledger order, independently of that wire order.
        assertThat(transaction.getBody().getWithdrawals())
                .extracting(Withdrawal::getRewardAddress).containsExactlyInAnyOrderElementsOf(expectedRewardAddresses);
        assertThat(transaction.getBody().getWithdrawals())
                .allSatisfy(withdrawal -> assertThat(withdrawal.getCoin()).isZero());
        assertThat(transaction.getWitnessSet().getPlutusV2Scripts()).hasSize(3);
        List<Redeemer> redeemers = transaction.getWitnessSet().getRedeemers();
        assertThat(redeemers).hasSize(3);
        for (int index = 0; index < scripts.size(); index++) {
            BigInteger expectedIndex = BigInteger.valueOf(index);
            BigIntPlutusData expectedData = BigIntPlutusData.of(scripts.get(index).redeemer());
            assertThat(redeemers).filteredOn(redeemer -> expectedIndex.equals(redeemer.getIndex()))
                    .singleElement().satisfies(redeemer -> {
                        assertThat(redeemer.getTag()).isEqualTo(RedeemerTag.Reward);
                        assertThat(redeemer.getData()).isEqualTo(expectedData);
                    });
        }
    }

    private static void assertConfirmed(TxResult result) {
        assertThat(result.isSuccessful()).as("transaction submission: %s", result.getResponse()).isTrue();
        assertThat(result.getTxStatus()).as("block inclusion of %s", result.getTxHash()).isEqualTo(TxStatus.CONFIRMED);
    }

    private static String credentialHash(String rewardAddress) {
        return HexUtil.encodeHexString(AddressProvider.getDelegationCredentialHash(new Address(rewardAddress))
                .orElseThrow());
    }

    private static ScriptWithdrawal script(String compiledCode, int redeemer) {
        PlutusScript validator = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(compiledCode, PlutusVersion.v2);
        return new ScriptWithdrawal(validator,
                AddressProvider.getRewardAddress(validator, Networks.testnet()).toBech32(), redeemer);
    }

    private record ScriptWithdrawal(PlutusScript validator, String rewardAddress, int redeemer) {
    }
}
