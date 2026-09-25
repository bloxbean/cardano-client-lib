package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.ScriptSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.programmabletoken.BurnAuthorization;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenCapability;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenTx;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployment;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Exception;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProtocolService;
import com.bloxbean.cardano.client.programmabletoken.cip113.PolicyIdDerivation;
import com.bloxbean.cardano.client.programmabletoken.cip113.SmartWalletAddress;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.IssuanceCborHex;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.RegistryNode;
import com.bloxbean.cardano.client.quicktx.AbstractTx;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionBuildContext;
import com.bloxbean.cardano.client.quicktx.intent.PaymentIntent;
import com.bloxbean.cardano.client.quicktx.intent.ScriptCollectFromIntent;
import com.bloxbean.cardano.client.quicktx.intent.ScriptMintingIntent;
import com.bloxbean.cardano.client.quicktx.intent.TxIntent;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Owner transfers and burns materialised offline, through input selection, programmable change
 * and the negative mint.
 *
 * <p>The deployment's scripts are registered by hash and never evaluated, and burns derive their
 * policy from a stand-in issuance template, so what is asserted is the shape of the transaction:
 * which smart-wallet UTxOs are spent, what every output carries and what is minted. Each test
 * checks per-unit conservation — spent = paid + change + burned — because a shape that breaks it
 * is exactly what {@code validate_transfer} rejects on chain with no usable detail.</p>
 */
class Cip113OwnerOperationsTest {

    // Stand-in scripts: only their hashes matter, nothing is evaluated.
    private static final PlutusScript BASE = script("01");
    private static final PlutusScript TRANSFER = script("02");
    private static final PlutusScript LOGIC_A = script("03");
    private static final PlutusScript LOGIC_B = script("04");

    private static final IssuanceCborHex TEMPLATE = new IssuanceCborHex(
            HexUtil.decodeHexString("58aa"), HexUtil.decodeHexString("bbcc"));
    private static final String POLICY_A = issuancePolicy(LOGIC_A);
    private static final String POLICY_B = issuancePolicy(LOGIC_B);

    private static final String TOK = HexUtil.encodeHexString("Tok".getBytes(StandardCharsets.UTF_8));
    private static final String UNIT_A = POLICY_A + TOK;
    private static final String UNIT_B = POLICY_B + TOK;

    private static final Cip113Deployment DEPLOYMENT = Cip113Deployment.builder()
            .network(Networks.testnet())
            .programmableLogicBaseHash(hash(BASE))
            .transferScriptHash(hash(TRANSFER))
            .thirdPartyScriptHash("55".repeat(28))
            .unfrackingScriptHash("66".repeat(28))
            .registryNodeCs("59fd9f91c09ab82bbc40b58e62c455eab8d3105ca3d2f46948af7b0c")
            .maxInlineDatumBytes(64)
            .build();

    private static final Address OWNER = AddressProvider.getEntAddress(
            Credential.fromKey("22".repeat(28)), Networks.testnet());
    private static final Address HOLDER = AddressProvider.getEntAddress(
            Credential.fromKey("33".repeat(28)), Networks.testnet());
    private static final String OWNER_WALLET = SmartWalletAddress.ofPaymentCredential(DEPLOYMENT, OWNER).toBech32();
    private static final String HOLDER_WALLET = SmartWalletAddress.ofPaymentCredential(DEPLOYMENT, HOLDER).toBech32();

    private static final BurnAuthorization BURN = BurnAuthorization.of(BigIntPlutusData.of(0), BigIntPlutusData.of(0));

    // ------------------------------------------------------------------ burns

    /**
     * Burns of one asset name add up to a single mint entry, whichever order the burns and a
     * transfer of the same asset are authored in. Two entries for one name would serialise as the
     * last one only, burning 10 where selection and change had accounted for 11.
     */
    @Test
    void burnsOfOneAssetMintTheirSumAsOneEntryInAnyOrder() {
        Utxo supply = walletUtxo(1, Map.of(UNIT_A, 20));
        List<Function<ProgrammableTokenTx, ProgrammableTokenTx>> burnOne = List.of(
                tx -> tx.burn(POLICY_A, List.of(tok(1)), BURN),
                tx -> transfer(tx, UNIT_A, 1),
                tx -> tx.burn(POLICY_A, List.of(tok(10)), BURN));

        for (List<Integer> order : List.of(List.of(0, 1, 2), List.of(1, 0, 2), List.of(0, 2, 1))) {
            ProgrammableTokenTx tx = owner();
            order.forEach(i -> burnOne.get(i).apply(tx));

            Built built = build(tx, supply);

            assertThat(built.minted(POLICY_A)).as("order %s", order)
                    .singleElement()
                    .satisfies(asset -> {
                        assertThat(HexUtil.encodeHexString(asset.getNameAsBytes())).isEqualTo(TOK);
                        assertThat(asset.getValue()).isEqualTo(BigInteger.valueOf(-11));
                    });
            assertThat(built.spent()).as("order %s", order).containsExactly(supply);
            assertThat(built.paidTo(HOLDER_WALLET)).containsExactly(Map.entry(UNIT_A, BigInteger.ONE));
            assertThat(built.paidTo(OWNER_WALLET)).containsExactly(Map.entry(UNIT_A, BigInteger.valueOf(8)));
            built.assertConserved();
        }
    }

    // ------------------------------------------------------- shared UTxOs

    /**
     * A UTxO holding two acted policies is spent once and returns each policy's remainder once,
     * in either fluent order. Selecting per policy either refused it (the second policy's
     * registry node was not referenced yet) or subtracted the first policy's payment twice.
     */
    @Test
    void aSharedUtxoFundsTransfersOfBothItsPoliciesInEitherOrder() {
        Utxo shared = walletUtxo(1, Map.of(UNIT_A, 10, UNIT_B, 5));
        Utxo onlyB = walletUtxo(2, Map.of(UNIT_B, 5));

        Built aFirst = build(transfer(transfer(owner(), UNIT_A, 3), UNIT_B, 8), shared, onlyB);
        Built bFirst = build(transfer(transfer(owner(), UNIT_B, 8), UNIT_A, 3), shared, onlyB);

        for (Built built : List.of(aFirst, bFirst)) {
            assertThat(built.spent()).containsExactlyInAnyOrder(shared, onlyB);
            assertThat(built.paidTo(HOLDER_WALLET)).containsOnly(
                    Map.entry(UNIT_A, BigInteger.valueOf(3)), Map.entry(UNIT_B, BigInteger.valueOf(8)));
            assertThat(built.paidTo(OWNER_WALLET)).containsOnly(
                    Map.entry(UNIT_A, BigInteger.valueOf(7)), Map.entry(UNIT_B, BigInteger.TWO));
            assertThat(built.minted(POLICY_A)).isEmpty();
            built.assertOnePolicyPerOutput();
            built.assertConserved();
        }
        assertThat(aFirst.spent()).containsExactlyElementsOf(bFirst.spent());
    }

    /** Burning one policy and transferring another out of the same shared UTxO. */
    @Test
    void aSharedUtxoFundsABurnOfOnePolicyAndATransferOfTheOther() {
        Utxo shared = walletUtxo(1, Map.of(UNIT_A, 10, UNIT_B, 5));

        Built built = build(transfer(owner().burn(POLICY_B, List.of(tok(2)), BURN), UNIT_A, 3), shared);

        assertThat(built.spent()).containsExactly(shared);
        assertThat(built.minted(POLICY_B)).singleElement()
                .satisfies(asset -> assertThat(asset.getValue()).isEqualTo(BigInteger.valueOf(-2)));
        assertThat(built.minted(POLICY_A)).isEmpty();
        assertThat(built.paidTo(HOLDER_WALLET)).containsOnly(Map.entry(UNIT_A, BigInteger.valueOf(3)));
        assertThat(built.paidTo(OWNER_WALLET)).containsOnly(
                Map.entry(UNIT_A, BigInteger.valueOf(7)), Map.entry(UNIT_B, BigInteger.valueOf(3)));
        built.assertOnePolicyPerOutput();
        built.assertConserved();
    }

    // ------------------------------------------------------------------ units

    /**
     * A unit written in upper case is the same asset: it is selected from the wallet's lowercase
     * UTxO, counted together with a burn of that asset, and emitted in canonical form.
     */
    @Test
    void anUppercaseUnitIsTheSameAssetAsItsCanonicalForm() {
        Utxo supply = walletUtxo(1, Map.of(UNIT_A, 5));

        Built built = build(transfer(owner(), UNIT_A.toUpperCase(), 2)
                .burn(POLICY_A, List.of(tok(1)), BURN), supply);

        assertThat(built.spent()).containsExactly(supply);
        assertThat(built.paidTo(HOLDER_WALLET)).containsExactly(Map.entry(UNIT_A, BigInteger.TWO));
        assertThat(built.paidTo(OWNER_WALLET)).containsExactly(Map.entry(UNIT_A, BigInteger.TWO));
        built.assertConserved();
    }

    /** A 56-character unit is the policy's empty asset name: transferable and burnable. */
    @Test
    void anEmptyAssetNameIsTransferredAndBurnedLikeAnyOther() {
        Utxo supply = walletUtxo(1, Map.of(POLICY_A, 5));

        Built built = build(transfer(owner(), POLICY_A, 2)
                .burn(POLICY_A, List.of(new Asset("0x", BigInteger.ONE)), BURN), supply);

        assertThat(built.spent()).containsExactly(supply);
        assertThat(built.paidTo(HOLDER_WALLET)).containsExactly(Map.entry(POLICY_A, BigInteger.TWO));
        assertThat(built.paidTo(OWNER_WALLET)).containsExactly(Map.entry(POLICY_A, BigInteger.TWO));
        assertThat(built.minted(POLICY_A)).singleElement().satisfies(asset -> {
            assertThat(asset.getNameAsBytes()).isEmpty();
            assertThat(asset.getValue()).isEqualTo(BigInteger.ONE.negate());
        });
        built.assertConserved();
    }

    // ---------------------------------------------------- materialise once

    /** Operations recorded after the inputs were selected would be unfunded, so they are refused. */
    @Test
    void recordingAfterMaterialiseIsRefused() {
        Cip113TransactionMaterializer materializer = new Cip113TransactionMaterializer(
                service(), supplier(walletUtxo(1, Map.of(UNIT_A, 5))), registry())
                .from(OWNER.toBech32())
                .recordTransferForExtension(POLICY_A, HOLDER.toBech32(), amount(UNIT_A, 1), null)
                .withRedeemer(POLICY_A, BigIntPlutusData.of(0));
        materializer.materialise();

        assertThatThrownBy(() -> materializer.recordTransferForExtension(POLICY_A, HOLDER.toBech32(),
                amount(UNIT_A, 1), null))
                .isInstanceOf(Cip113Exception.class).hasMessageContaining("recorded after");
        assertThatThrownBy(() -> materializer.recordBurnForExtension(POLICY_A,
                tok(1).negate(), BigIntPlutusData.of(0), BigIntPlutusData.of(0)))
                .isInstanceOf(Cip113Exception.class).hasMessageContaining("recorded after");
        assertThatThrownBy(() -> materializer.withRedeemer(POLICY_A, BigIntPlutusData.of(0)))
                .isInstanceOf(Cip113Exception.class).hasMessageContaining("recorded after");
        assertThatThrownBy(materializer::materialise)
                .isInstanceOf(Cip113Exception.class).hasMessageContaining("runs once");
    }

    // ---------------------------------------------------------------- fixtures

    private static ProgrammableTokenTx owner() {
        return new ProgrammableTokenTx().from(OWNER.toBech32());
    }

    private static ProgrammableTokenTx transfer(ProgrammableTokenTx tx, String unit, long quantity) {
        return tx.transfer(HOLDER.toBech32(), amount(unit, quantity), BigIntPlutusData.of(0));
    }

    private static Amount amount(String unit, long quantity) {
        return Amount.builder().unit(unit).quantity(BigInteger.valueOf(quantity)).build();
    }

    private static Asset tok(long quantity) {
        return new Asset("0x" + TOK, BigInteger.valueOf(quantity));
    }

    /** Runs the extension over {@code tx} against a smart wallet holding {@code wallet}. */
    private static Built build(ProgrammableTokenTx tx, Utxo... wallet) {
        ExtensionBuildContext context = new ExtensionBuildContext(new AbstractTx<?>[]{tx},
                supplier(wallet), mock(ProtocolParamsSupplier.class));
        new Cip113BuildExtension(service(), EnumSet.allOf(ProgrammableTokenCapability.class)).prepare(context);
        return new Built(context.preparedIntents(tx));
    }

    private static UtxoSupplier supplier(Utxo... wallet) {
        List<Utxo> walletUtxos = List.of(wallet);
        List<Utxo> ada = List.of(Utxo.builder().txHash("ee".repeat(32)).outputIndex(0)
                .address(OWNER.toBech32())
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(100_000_000L)))).build());
        UtxoSupplier supplier = mock(UtxoSupplier.class);
        when(supplier.getAll(OWNER_WALLET)).thenReturn(walletUtxos);
        when(supplier.getAll(OWNER.toBech32())).thenReturn(ada);
        return supplier;
    }

    private static Utxo walletUtxo(int index, Map<String, Integer> tokens) {
        List<Amount> amounts = new ArrayList<>();
        amounts.add(Amount.lovelace(BigInteger.valueOf(2_000_000L)));
        tokens.forEach((unit, quantity) -> amounts.add(amount(unit, quantity)));
        return Utxo.builder().txHash(String.format("%064x", index)).outputIndex(0)
                .address(OWNER_WALLET).amount(amounts).build();
    }

    private static Cip113ProtocolService service() {
        // Built before any stubbing: Mockito rejects stubbing inside another stub's thenReturn.
        RegistryLookup registry = registry();
        DeploymentScripts scripts = new DeploymentScripts((ScriptSupplier) null, DEPLOYMENT)
                .registerAll(List.of(BASE, TRANSFER, LOGIC_A, LOGIC_B));
        Utxo coordination = Utxo.builder().txHash("cc".repeat(32)).outputIndex(0)
                .address("addr_test1coordination")
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(5_000_000L)))).build();
        Utxo template = Utxo.builder().txHash("dd".repeat(32)).outputIndex(0)
                .address("addr_test1template")
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(5_000_000L))))
                .inlineDatum(ConstrPlutusData.of(0,
                        BytesPlutusData.of(TEMPLATE.getPrefixCborHex()),
                        BytesPlutusData.of(TEMPLATE.getPostfixCborHex())).serializeToHex())
                .build();

        Cip113ProtocolService service = mock(Cip113ProtocolService.class);
        when(service.deployment()).thenReturn(DEPLOYMENT);
        when(service.registryLookup()).thenReturn(registry);
        when(service.scripts()).thenReturn(scripts);
        when(service.coordinationUtxo()).thenReturn(coordination);
        when(service.issuanceTemplateUtxo()).thenReturn(template);
        return service;
    }

    private static RegistryLookup registry() {
        List<RegistryLookup.RegistryNodeUtxo> nodes = List.of(node(POLICY_A, LOGIC_A, 1), node(POLICY_B, LOGIC_B, 2));
        return new RegistryLookup() {
            @Override public Optional<RegistryNodeUtxo> byPolicy(String policyId) {
                return nodes.stream().filter(n -> n.getDatum().getKey().equalsIgnoreCase(policyId)).findFirst();
            }

            @Override public RegistryNodeUtxo coveringNode(String policyId) {
                throw new AssertionError("no unregistered co-resident policy in these wallets: " + policyId);
            }

            @Override public List<RegistryNodeUtxo> all() { return nodes; }
        };
    }

    private static RegistryLookup.RegistryNodeUtxo node(String policy, PlutusScript logic, int index) {
        Credential credential = Credential.fromScript(hash(logic));
        RegistryNode datum = RegistryNode.builder().key(policy).next("ff".repeat(28))
                .mintingLogicScript(credential).transferLogicScript(credential)
                .thirdPartyTransferLogicScript(credential).unfrackingLogicScript(credential)
                .globalStateCs("").build();
        Utxo utxo = Utxo.builder().txHash(String.format("%064x", 100 + index)).outputIndex(0)
                .address("addr_test1registry")
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(2_000_000L)))).build();
        return new RegistryLookup.RegistryNodeUtxo(utxo, datum);
    }

    private static PlutusScript script(String lastByte) {
        return PlutusBlueprintUtil.getPlutusScriptFromCompiledCode("4501010024" + lastByte, PlutusVersion.v3);
    }

    private static String hash(PlutusScript script) {
        try {
            return script.getPolicyId();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String issuancePolicy(PlutusScript mintingLogic) {
        return hash(PolicyIdDerivation.issuanceScript(TEMPLATE, Credential.fromScript(hash(mintingLogic))));
    }

    /** The prepared intents of one build, read back as spends, outputs and mint. */
    private static final class Built {
        private final List<TxIntent> intents;

        Built(List<TxIntent> intents) {
            this.intents = intents;
        }

        /** Smart-wallet UTxOs spent through the base script. */
        List<Utxo> spent() {
            return intents.stream().filter(ScriptCollectFromIntent.class::isInstance)
                    .map(ScriptCollectFromIntent.class::cast)
                    .flatMap(intent -> intent.getUtxos().stream())
                    .toList();
        }

        List<List<Amount>> outputsAt(String address) {
            return intents.stream().filter(PaymentIntent.class::isInstance)
                    .map(PaymentIntent.class::cast)
                    .filter(payment -> address.equals(payment.getAddress()))
                    .map(payment -> payment.getAmounts().stream()
                            .filter(amount -> !"lovelace".equals(amount.getUnit())).toList())
                    .toList();
        }

        Map<String, BigInteger> paidTo(String address) {
            return sum(outputsAt(address).stream().flatMap(List::stream).toList());
        }

        List<Asset> minted(String policy) {
            return intents.stream().filter(ScriptMintingIntent.class::isInstance)
                    .map(ScriptMintingIntent.class::cast)
                    .filter(mint -> policy.equalsIgnoreCase(mint.getPolicyId()))
                    .flatMap(mint -> mint.getAssets().stream())
                    .toList();
        }

        void assertOnePolicyPerOutput() {
            for (String address : List.of(OWNER_WALLET, HOLDER_WALLET)) {
                assertThat(outputsAt(address)).allSatisfy(output -> assertThat(output.stream()
                        .map(amount -> AssetUtil.getPolicyId(amount.getUnit())).distinct())
                        .hasSize(1));
            }
        }

        /** Per unit: everything spent is paid out, returned as change, or burned. */
        void assertConserved() {
            Map<String, BigInteger> spent = sum(spent().stream()
                    .flatMap(utxo -> utxo.getAmount().stream())
                    .filter(amount -> !"lovelace".equals(amount.getUnit())).toList());
            Map<String, BigInteger> accounted = new LinkedHashMap<>(paidTo(HOLDER_WALLET));
            paidTo(OWNER_WALLET).forEach((unit, quantity) -> accounted.merge(unit, quantity, BigInteger::add));
            for (String policy : List.of(POLICY_A, POLICY_B)) {
                for (Asset burned : minted(policy)) {
                    accounted.merge(policy + HexUtil.encodeHexString(burned.getNameAsBytes()),
                            burned.getValue().negate(), BigInteger::add);
                }
            }
            assertThat(accounted).as("spent = paid + change + burned").isEqualTo(spent);
        }

        private static Map<String, BigInteger> sum(List<Amount> amounts) {
            Map<String, BigInteger> total = new LinkedHashMap<>();
            amounts.forEach(amount -> total.merge(amount.getUnit(), amount.getQuantity(), BigInteger::add));
            return total;
        }
    }
}
