package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.aiken.AikenTransactionEvaluator;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.programmabletoken.BurnAuthorization;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenTx;
import com.bloxbean.cardano.client.programmabletoken.ThirdPartyBurnAuthorization;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployment;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProgrammableTokenService;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProtocolService;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Registration;
import com.bloxbean.cardano.client.programmabletoken.cip113.SmartWalletKey;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QuickTx regression for third-party actions where the sender also owns the holder's wallet.
 * With STAKE wallets, accidentally declaring the holder as a required signer demands a second
 * witness: a payment signature alone must suffice for the always-true third-party logic.
 * Owner-authorized transfers and burns must still declare and witness the stake key.
 *
 * <p>Run against Yaci DevKit with:
 * {@code ./gradlew :programmable-token:integrationTest --tests '*Cip113ThirdPartySignerIT'}.
 * Funds a fresh account and deploys its own protocol without resetting the devnet. Only a missing
 * devnet skips the tests; setup, evaluation, submission and balance failures fail them.</p>
 */
@ResourceLock("yaci-devkit")
class Cip113ThirdPartySignerIT {
    private static final Logger log = LoggerFactory.getLogger(Cip113ThirdPartySignerIT.class);
    private static final Network NETWORK = Networks.testnet();
    private static final BigInteger SPENT = BigInteger.valueOf(4);

    private static BackendService backend;
    private static Account account;
    private static Address owner;
    private static Cip113ProgrammableTokenService programmableTokens;
    private static Cip113ProtocolService protocol;
    private static String policyId;

    enum Operation { BURN, TRANSFER }

    @BeforeAll
    static void setup() throws Exception {
        Assumptions.assumeTrue(DevNet.isRunning(), "Start a local devnet with `yaci-devkit up`");
        backend = new BFBackendService(DevNet.BACKEND_URL, "Dummy");
        account = new Account(NETWORK);
        owner = new Address(account.baseAddress());
        // Separate seeds for the bootstrap's one-shot minting policies.
        DevNet.topUp(account.baseAddress(), 1_000L);
        DevNet.topUp(account.baseAddress(), 1_000L);
        awaitSeedUtxos();

        Cip113Bootstrap.Deployed deployed = new Cip113Bootstrap().deploy(backend, account, NETWORK);
        programmableTokens = Cip113ProgrammableTokenService.create(backend,
                deployed.deployment().toBuilder().smartWalletKey(SmartWalletKey.STAKE).build());
        protocol = programmableTokens.advanced();
        protocol.scripts().registerAll(deployed.appliedScripts());
        protocol.scripts().register(AlwaysTrueScripts.ALWAYS_TRUE);
        Result<Cip113Deployment> resolved = protocol.resolveDeployment();
        assertThat(resolved.isSuccessful()).as("resolve deployment: %s", resolved.getResponse()).isTrue();
        assertThat(resolved.getValue().getSmartWalletKey()).isEqualTo(SmartWalletKey.STAKE);

        ensureLogicRewardAccountRegistered();
        Result<String> derived = protocol.derivePolicyId(AlwaysTrueScripts.credential());
        assertThat(derived.isSuccessful()).as("derive policy: %s", derived.getResponse()).isTrue();
        policyId = derived.getValue();
        Result<String> registered = build(new ProgrammableTokenTx().from(account.baseAddress())
                .register("signer-regression", Cip113Registration.from(RegistryNodeSpec.builder()
                        .mintingLogicScript(AlwaysTrueScripts.credential())
                        .transferLogicScript(AlwaysTrueScripts.credential())
                        .thirdPartyTransferLogicScript(AlwaysTrueScripts.credential())
                        .build()), PlutusData.unit()))
                .completeAndWait(log::info);
        assertThat(registered.isSuccessful()).as("register token: %s", registered.getResponse()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void thirdPartyActionFromSendersStakeWalletNeedsOnlyPaymentSignature(Operation operation) {
        String assetName = "third-party-" + operation.name();
        Utxo held = mintToOwner(assetName);
        Address receiver = new Address(new Account(NETWORK).baseAddress());
        ProgrammableTokenTx tx = new ProgrammableTokenTx().from(account.baseAddress());
        if (operation == Operation.BURN) {
            tx.thirdPartyBurn(held.getTxHash() + "#" + held.getOutputIndex(), policyId,
                    List.of(new Asset(assetName, SPENT)),
                    ThirdPartyBurnAuthorization.of(PlutusData.unit(), PlutusData.unit()));
        } else {
            tx.thirdPartyTransfer(account.baseAddress(), receiver.toBech32(),
                    Amount.asset(policyId, assetName, SPENT), PlutusData.unit());
        }

        AtomicReference<Transaction> submitted = new AtomicReference<>();
        // Deliberately sign only with the payment key, even though the sender is also the holder.
        Result<String> result = build(tx).withTxInspector(submitted::set).completeAndWait(log::info);
        assertThat(result.isSuccessful())
                .as("third-party %s must be accepted with only the payment signature: %s",
                        operation, result.getResponse()).isTrue();
        Transaction transaction = submitted.get();
        assertThat(transaction).isNotNull();
        assertThat(transaction.getBody().getInputs())
                .contains(new TransactionInput(held.getTxHash(), held.getOutputIndex()));
        assertThat(transaction.getBody().getRequiredSigners()).isNullOrEmpty();
        assertThat(transaction.getWitnessSet().getVkeyWitnesses())
                .extracting(w -> HexUtil.encodeHexString(w.getVkey()))
                .containsExactly(HexUtil.encodeHexString(account.publicKeyBytes()));
        assertBalances(assetName, receiver, operation);
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void ownerActionStillDeclaresAndWitnessesTheStakeKey(Operation operation) {
        String assetName = "owner-" + operation.name();
        mintToOwner(assetName);
        Address receiver = new Address(new Account(NETWORK).baseAddress());
        ProgrammableTokenTx tx = new ProgrammableTokenTx().from(account.baseAddress());
        if (operation == Operation.BURN) {
            tx.burn(policyId, List.of(new Asset(assetName, SPENT)),
                    BurnAuthorization.of(PlutusData.unit(), PlutusData.unit()));
        } else {
            tx.transfer(receiver.toBech32(), Amount.asset(policyId, assetName, SPENT), PlutusData.unit());
        }

        AtomicReference<Transaction> submitted = new AtomicReference<>();
        Result<String> result = build(tx)
                .withSigner(SignerProviders.stakeKeySignerFrom(account))
                .withTxInspector(submitted::set).completeAndWait(log::info);
        assertThat(result.isSuccessful()).as("owner %s: %s", operation, result.getResponse()).isTrue();
        Transaction transaction = submitted.get();
        assertThat(transaction).isNotNull();
        assertThat(transaction.getBody().getRequiredSigners()).extracting(HexUtil::encodeHexString)
                .containsExactly(HexUtil.encodeHexString(owner.getDelegationCredentialHash().orElseThrow()));
        assertThat(transaction.getWitnessSet().getVkeyWitnesses())
                .extracting(w -> HexUtil.encodeHexString(w.getVkey()))
                .containsExactlyInAnyOrder(HexUtil.encodeHexString(account.publicKeyBytes()),
                        HexUtil.encodeHexString(account.stakeHdKeyPair().getPublicKey().getKeyData()));
        assertBalances(assetName, receiver, operation);
    }

    private static Utxo mintToOwner(String assetName) {
        Result<String> minted = build(new ProgrammableTokenTx().from(account.baseAddress())
                .mint(policyId, account.baseAddress(), List.of(new Asset(assetName, BigInteger.TEN)),
                        PlutusData.unit(), null))
                .completeAndWait(log::info);
        assertThat(minted.isSuccessful()).as("mint %s: %s", assetName, minted.getResponse()).isTrue();
        Result<List<Utxo>> utxos = protocol.getUtxos(owner);
        assertThat(utxos.isSuccessful()).as("holder UTxOs: %s", utxos.getResponse()).isTrue();
        Utxo held = utxos.getValue().stream()
                .filter(u -> quantity(u.getAmount(), unit(assetName)).signum() > 0)
                .findFirst().orElseThrow();
        assertThat(held.getAddress()).isEqualTo(protocol.smartWalletAddress(owner).toBech32());
        assertThat(new Address(held.getAddress()).getDelegationCredentialHash().orElseThrow())
                .isEqualTo(owner.getDelegationCredentialHash().orElseThrow());
        assertThat(quantity(held.getAmount(), unit(assetName))).isEqualTo(BigInteger.TEN);
        return held;
    }

    private static void assertBalances(String assetName, Address receiver, Operation operation) {
        assertThat(balance(owner, unit(assetName))).isEqualTo(BigInteger.TEN.subtract(SPENT));
        if (operation == Operation.TRANSFER) {
            assertThat(balance(receiver, unit(assetName))).isEqualTo(SPENT);
        }
    }

    private static BigInteger balance(Address holder, String unit) {
        Result<List<Amount>> balance = protocol.getProgrammableBalance(holder);
        assertThat(balance.isSuccessful()).as("balance: %s", balance.getResponse()).isTrue();
        return quantity(balance.getValue(), unit);
    }

    private static BigInteger quantity(List<Amount> amounts, String unit) {
        return amounts.stream().filter(a -> unit.equals(a.getUnit()))
                .map(Amount::getQuantity).reduce(BigInteger.ZERO, BigInteger::add);
    }

    private static String unit(String assetName) {
        return policyId + HexUtil.encodeHexString(assetName.getBytes(StandardCharsets.UTF_8));
    }

    private static QuickTxBuilder.TxContext build(Tx tx) {
        return new QuickTxBuilder(backend).withExtension(programmableTokens.extension())
                .compose(tx).feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .withTxEvaluator(new AikenTransactionEvaluator(backend));
    }

    private static void ensureLogicRewardAccountRegistered() {
        Result<String> result = new QuickTxBuilder(backend)
                .compose(new Tx().registerStakeAddress(AlwaysTrueScripts.rewardAddress(NETWORK).toBech32())
                        .from(account.baseAddress()))
                .withSigner(SignerProviders.signerFrom(account)).completeAndWait(log::info);
        if (!result.isSuccessful()) {
            // This fixed test script may already be registered by an earlier suite or invocation.
            assertThat(result.getResponse()).as("register logic reward account")
                    .contains("StakeKeyRegistered");
        }
    }

    private static void awaitSeedUtxos() throws InterruptedException {
        DefaultUtxoSupplier supplier = new DefaultUtxoSupplier(backend.getUtxoService());
        long deadline = System.nanoTime() + 60_000_000_000L;
        while (System.nanoTime() < deadline) {
            long count = supplier.getAll(account.baseAddress()).stream()
                    .filter(u -> quantity(u.getAmount(), "lovelace")
                            .compareTo(BigInteger.valueOf(100_000_000L)) > 0)
                    .count();
            if (count >= 2) return;
            Thread.sleep(1_000L);
        }
        throw new IllegalStateException("Two bootstrap seed UTxOs did not appear within 60 seconds");
    }
}
