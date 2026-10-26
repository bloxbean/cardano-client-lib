package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.ScriptSupplier;
import com.bloxbean.cardano.client.api.TransactionEvaluator;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.impl.StaticTransactionEvaluator;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.CostModelUtil;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenExtension;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployment;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Protocol;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProtocolService;
import com.bloxbean.cardano.client.programmabletoken.cip113.LedgerOrdering;
import com.bloxbean.cardano.client.programmabletoken.cip113.PolicyIdDerivation;
import com.bloxbean.cardano.client.programmabletoken.cip113.PolicyOrdering;
import com.bloxbean.cardano.client.programmabletoken.cip113.SmartWalletAddress;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.IssuanceCborHex;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.RegistryNode;
import com.bloxbean.cardano.client.quicktx.AbstractTx;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Offline QuickTx fixture for third-party actions, in the style of
 * {@link Cip113BuiltTransactionTest}: stand-in scripts registered by hash, nothing evaluated,
 * every redeemer read back against the ledger orderings the validators index.
 *
 * <p>Two registered policies: A (minting logic {@link #LOGIC_A}; its third-party logic is chosen
 * per test) and B (minting, transfer and third-party logic {@link #LOGIC_M}). The registry is a
 * real linked list (head → lower policy → higher policy → tail), so a registration in the same
 * transaction finds a genuine covering node.</p>
 */
final class ThirdPartyFixture {

    static final PlutusScript BASE = script("01");
    static final PlutusScript TRANSFER = script("02");
    static final PlutusScript LOGIC_A = script("03");
    static final PlutusScript THIRD_PARTY = script("04");
    static final PlutusScript DISPATCHER = script("05");
    static final PlutusScript ISSUANCE_LOGIC = script("06");
    /** A third-party logic distinct from policy A's minting logic. */
    static final PlutusScript LOGIC_B = script("07");
    static final PlutusScript REGISTRY = script("08");
    /** Policy B's single logic script. */
    static final PlutusScript LOGIC_M = script("09");

    static final IssuanceCborHex TEMPLATE = new IssuanceCborHex(
            HexUtil.decodeHexString("58aa"), HexUtil.decodeHexString("bbcc"));
    static final String POLICY_A = policyFor(LOGIC_A);
    static final String POLICY_B = policyFor(LOGIC_M);
    static final String TOK = HexUtil.encodeHexString("Tok".getBytes(StandardCharsets.UTF_8));
    static final String TOK2 = HexUtil.encodeHexString("Tok2".getBytes(StandardCharsets.UTF_8));
    static final String UNIT_A = POLICY_A + TOK;
    static final String UNIT_A2 = POLICY_A + TOK2;
    static final String UNIT_B = POLICY_B + TOK;

    static final Cip113Deployment DEPLOYMENT = Cip113Deployment.builder()
            .network(Networks.testnet())
            .paramsPolicy("99".repeat(28))
            .programmableLogicBaseHash(hash(BASE))
            .programmableLogicGlobalHash(hash(DISPATCHER))
            .issuanceLogicHash(hash(ISSUANCE_LOGIC))
            .transferScriptHash(hash(TRANSFER))
            .thirdPartyScriptHash(hash(THIRD_PARTY))
            .unfrackingScriptHash("66".repeat(28))
            .registryNodeCs(hash(REGISTRY))
            .issuanceCborHexCs("44".repeat(28))
            .maxInlineDatumBytes(1024)
            .unfrackingEnabled(true)
            .build();

    static final Address ADMIN = AddressProvider.getEntAddress(
            Credential.fromKey("44".repeat(28)), Networks.testnet());
    static final Address HOLDER_A = AddressProvider.getEntAddress(
            Credential.fromKey("33".repeat(28)), Networks.testnet());
    static final Address HOLDER_B = AddressProvider.getEntAddress(
            Credential.fromKey("55".repeat(28)), Networks.testnet());
    static final Address RECEIVER = AddressProvider.getEntAddress(
            Credential.fromKey("77".repeat(28)), Networks.testnet());
    static final Address REF_SCRIPTS = AddressProvider.getEntAddress(
            Credential.fromKey("88".repeat(28)), Networks.testnet());

    static final Utxo PARAMS = Utxo.builder().txHash("cc".repeat(32)).outputIndex(0)
            .address(DEPLOYMENT.protocolParamsAddress().toBech32())
            .amount(List.of(Amount.lovelace(BigInteger.valueOf(5_000_000L)))).build();
    static final Utxo FEE = Utxo.builder().txHash("ee".repeat(32)).outputIndex(0)
            .address(ADMIN.toBech32())
            .amount(List.of(Amount.lovelace(BigInteger.valueOf(500_000_000L)))).build();

    /** Policy A's third-party logic; {@link #LOGIC_A} shares one credential with minting. */
    PlutusScript thirdPartyLogicA = LOGIC_A;
    final List<Utxo> chain = new ArrayList<>();
    /** Outputs {@code getTxOutput} still resolves but that are no longer unspent. */
    final List<Utxo> spent = new ArrayList<>();
    final List<Transaction> evaluated = new ArrayList<>();
    /** Stand-in scripts beyond the fixed set, e.g. a new registration's logic. */
    final List<PlutusScript> extraScripts = new ArrayList<>();

    ThirdPartyFixture withScripts(PlutusScript... scripts) {
        extraScripts.addAll(List.of(scripts));
        return this;
    }

    ThirdPartyFixture withThirdPartyLogicA(PlutusScript logic) {
        this.thirdPartyLogicA = logic;
        return this;
    }

    ThirdPartyFixture holding(Utxo... utxos) {
        chain.addAll(List.of(utxos));
        return this;
    }

    ThirdPartyFixture spentAlready(Utxo... utxos) {
        spent.addAll(List.of(utxos));
        return this;
    }

    /** UTxOs publishing deployment scripts as reference scripts, by script hash. */
    final Map<String, Utxo> published = new LinkedHashMap<>();
    /** Scripts the caller declares with {@code withReferenceScripts}; none by default. */
    final List<PlutusScript> callerReferenceScripts = new ArrayList<>();

    ThirdPartyFixture publishing(PlutusScript... scripts) {
        for (PlutusScript script : scripts) {
            published.put(hash(script), Utxo.builder().txHash("ab".repeat(32)).outputIndex(published.size())
                    .address(REF_SCRIPTS.toBech32())
                    .amount(List.of(Amount.lovelace(BigInteger.valueOf(20_000_000L))))
                    .referenceScriptHash(hash(script))
                    .build());
        }
        return this;
    }

    ThirdPartyFixture declaringReferenceScripts(PlutusScript... scripts) {
        callerReferenceScripts.addAll(List.of(scripts));
        return this;
    }

    // ------------------------------------------------------------------ chain

    static String wallet(Address owner) {
        return SmartWalletAddress.ofPaymentCredential(DEPLOYMENT, owner).toBech32();
    }

    static Utxo utxo(String txHashByte, int index, String address, Amount... amounts) {
        return Utxo.builder().txHash(txHashByte.repeat(32)).outputIndex(index).address(address)
                .amount(List.of(amounts)).build();
    }

    static Utxo held(String txHashByte, int index, Address owner, Amount... tokens) {
        List<Amount> amounts = new ArrayList<>();
        amounts.add(Amount.lovelace(BigInteger.valueOf(2_000_000L)));
        amounts.addAll(List.of(tokens));
        return Utxo.builder().txHash(txHashByte.repeat(32)).outputIndex(index).address(wallet(owner))
                .amount(amounts).build();
    }

    static String ref(Utxo utxo) {
        return utxo.getTxHash() + "#" + utxo.getOutputIndex();
    }

    static Asset asset(String nameHex, long quantity) {
        return new Asset("0x" + nameHex, BigInteger.valueOf(quantity));
    }

    List<RegistryLookup.RegistryNodeUtxo> nodes() {
        List<String> keys = new ArrayList<>(List.of(POLICY_A, POLICY_B));
        keys.sort(PolicyOrdering.COMPARATOR);
        List<RegistryLookup.RegistryNodeUtxo> nodes = new ArrayList<>();
        nodes.add(node("", keys.get(0), "0f", LOGIC_M, LOGIC_M, LOGIC_M));
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            String next = i + 1 < keys.size() ? keys.get(i + 1) : "ff".repeat(28);
            nodes.add(POLICY_A.equals(key)
                    ? node(key, next, "0a", LOGIC_A, LOGIC_A, thirdPartyLogicA)
                    : node(key, next, "0b", LOGIC_M, LOGIC_M, LOGIC_M));
        }
        return nodes;
    }

    RegistryLookup.RegistryNodeUtxo nodeOf(String policy) {
        return nodes().stream().filter(n -> n.getDatum().getKey().equalsIgnoreCase(policy))
                .findFirst().orElseThrow();
    }

    private static RegistryLookup.RegistryNodeUtxo node(String key, String next, String txByte,
                                                        PlutusScript minting, PlutusScript transfer,
                                                        PlutusScript thirdParty) {
        RegistryNode datum = RegistryNode.builder().key(key).next(next)
                .mintingLogicScript(Credential.fromScript(hash(minting)))
                .transferLogicScript(Credential.fromScript(hash(transfer)))
                .thirdPartyTransferLogicScript(Credential.fromScript(hash(thirdParty)))
                .unfrackingLogicScript(Credential.fromKey(""))
                .globalStateCs("").build();
        Utxo utxo = Utxo.builder().txHash(txByte.repeat(32)).outputIndex(3)
                .address(DEPLOYMENT.registryAddress().toBech32())
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(5_000_000L)),
                        Amount.asset(hash(REGISTRY) + key, 1)))
                .inlineDatum(datum.toPlutusData().serializeToHex())
                .build();
        return new RegistryLookup.RegistryNodeUtxo(utxo, datum);
    }

    // ------------------------------------------------------------------ build

    Transaction build(AbstractTx<?>... txs) {
        ProtocolParams protocolParams = protocolParams();
        // A reference script's language has no default cost model in the script-data hash.
        if (!published.isEmpty())
            protocolParams.setCostModelsRaw(new LinkedHashMap<>(Map.of("PlutusV3", java.util.Arrays.stream(
                    CostModelUtil.PlutusV3CostModel.getCosts()).boxed().toList())));
        ProtocolParamsSupplier protocolParamsSupplier = () -> protocolParams;

        Map<String, List<Utxo>> unspent = new LinkedHashMap<>();
        unspent.computeIfAbsent(ADMIN.toBech32(), a -> new ArrayList<>()).add(FEE);
        unspent.computeIfAbsent(PARAMS.getAddress(), a -> new ArrayList<>()).add(PARAMS);
        for (RegistryLookup.RegistryNodeUtxo node : nodes())
            unspent.computeIfAbsent(node.getUtxo().getAddress(), a -> new ArrayList<>()).add(node.getUtxo());
        for (Utxo utxo : chain)
            unspent.computeIfAbsent(utxo.getAddress(), a -> new ArrayList<>()).add(utxo);
        for (Utxo utxo : published.values())
            unspent.computeIfAbsent(utxo.getAddress(), a -> new ArrayList<>()).add(utxo);
        UtxoSupplier utxoSupplier = new InMemoryUtxoSupplier(unspent, spent);

        Cip113ProtocolService service = service(protocolParamsSupplier);
        ProgrammableTokenExtension extension = ProgrammableTokenExtension.builder()
                .protocol(new Cip113Protocol(service)).deployment(Map.of()).build();

        TransactionEvaluator evaluator = (cbor, inputUtxos) -> {
            Transaction candidate;
            try {
                candidate = Transaction.deserialize(cbor);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            evaluated.add(candidate);
            return new StaticTransactionEvaluator(Collections.nCopies(
                    candidate.getWitnessSet().getRedeemers().size(), ExUnits.builder()
                            .mem(BigInteger.valueOf(100_000)).steps(BigInteger.valueOf(10_000_000)).build()))
                    .evaluateTx(cbor, inputUtxos);
        };
        QuickTxBuilder.TxContext context = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, null)
                .withExtension(extension)
                .compose(txs)
                .feePayer(ADMIN.toBech32())
                .collateralPayer(ADMIN.toBech32())
                .withScriptSupplier(service.scripts())
                .withTxEvaluator(evaluator);
        if (!callerReferenceScripts.isEmpty())
            context = context.withReferenceScripts(callerReferenceScripts.toArray(new PlutusScript[0]));
        return context.build();
    }

    private Cip113ProtocolService service(ProtocolParamsSupplier protocolParamsSupplier) {
        List<RegistryLookup.RegistryNodeUtxo> nodes = nodes();
        RegistryLookup registry = new RegistryLookup() {
            @Override public Optional<RegistryNodeUtxo> byPolicy(String policyId) {
                return nodes.stream().filter(n -> n.getDatum().getKey().equalsIgnoreCase(policyId)).findFirst();
            }

            @Override public RegistryNodeUtxo coveringNode(String policyId) {
                return nodes.stream().filter(n -> PolicyOrdering.covers(
                        n.getDatum().getKey(), n.getDatum().getNext(), policyId)).findFirst().orElseThrow();
            }

            @Override public List<RegistryNodeUtxo> all() {
                return nodes;
            }
        };
        List<PlutusScript> known = new ArrayList<>(List.of(BASE, TRANSFER, LOGIC_A, THIRD_PARTY,
                DISPATCHER, ISSUANCE_LOGIC, LOGIC_B, REGISTRY, LOGIC_M));
        known.addAll(extraScripts);
        DeploymentScripts scripts = new DeploymentScripts((ScriptSupplier) null, DEPLOYMENT)
                .registerAll(known);
        published.forEach(scripts::publishedAt);
        Utxo template = Utxo.builder().txHash("dd".repeat(32)).outputIndex(0)
                .address("addr_test1template")
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(5_000_000L))))
                .inlineDatum(ConstrPlutusData.of(0,
                        BytesPlutusData.of(TEMPLATE.getPrefixCborHex()),
                        BytesPlutusData.of(TEMPLATE.getPostfixCborHex())).serializeToHex())
                .build();

        Cip113ProtocolService service = mock(Cip113ProtocolService.class);
        when(service.deployment()).thenReturn(DEPLOYMENT);
        when(service.registryLookup(any())).thenReturn(registry);
        when(service.scripts()).thenReturn(scripts);
        when(service.coordinationUtxo(any())).thenReturn(PARAMS);
        when(service.issuanceTemplateUtxo()).thenReturn(template);
        when(service.protocolParamsSupplier()).thenReturn(protocolParamsSupplier);
        when(service.globalStateUtxo(anyString(), any())).thenReturn(null);
        return service;
    }

    // ---------------------------------------------------------------- readers

    static PlutusData spendRedeemer(Transaction txn, Utxo input) {
        int idx = LedgerOrdering.indexOfInput(txn, input.getTxHash(), input.getOutputIndex());
        return redeemer(txn, RedeemerTag.Spend, idx);
    }

    static PlutusData rewardRedeemer(Transaction txn, Credential credential) {
        return redeemer(txn, RedeemerTag.Reward, LedgerOrdering.indexOfWithdrawal(txn, credential));
    }

    static PlutusData mintRedeemer(Transaction txn, String policyId) {
        List<String> policies = new ArrayList<>();
        txn.getBody().getMint().forEach(ma -> policies.add(ma.getPolicyId().toLowerCase()));
        policies.sort(PolicyOrdering.COMPARATOR);
        return redeemer(txn, RedeemerTag.Mint, policies.indexOf(policyId.toLowerCase()));
    }

    static PlutusData redeemer(Transaction txn, RedeemerTag tag, int index) {
        List<Redeemer> matching = txn.getWitnessSet().getRedeemers().stream()
                .filter(r -> r.getTag() == tag && r.getIndex().intValue() == index)
                .toList();
        assertThat(matching).as("one %s redeemer at index %d", tag, index).hasSize(1);
        return matching.get(0).getData();
    }

    /** {@code [registry_node_idx, outputs_start_idx]} of the third_party delegate's redeemer. */
    static int[] thirdPartyIndices(Transaction txn) {
        PlutusData data = rewardRedeemer(txn, DEPLOYMENT.thirdPartyCredential());
        assertThat(data).isInstanceOf(ConstrPlutusData.class);
        List<PlutusData> fields = ((ConstrPlutusData) data).getData().getPlutusDataList();
        assertThat(fields).as("registry_node_idx, outputs_start_idx").hasSize(2);
        return new int[]{((BigIntPlutusData) fields.get(0)).getValue().intValue(),
                ((BigIntPlutusData) fields.get(1)).getValue().intValue()};
    }

    /** The reward-account credential hashes withdrawn, in body order. */
    static Set<String> withdrawnCredentials(Transaction txn) {
        Set<String> hashes = new LinkedHashSet<>();
        txn.getBody().getWithdrawals().forEach(w -> hashes.add(HexUtil.encodeHexString(
                AddressProvider.getDelegationCredential(new Address(w.getRewardAddress()))
                        .orElseThrow().getBytes())));
        return hashes;
    }

    /** The base-script inputs of {@code txn}, in ledger order, as {@code txHash#index}. */
    static List<String> sortedInputRefs(Transaction txn, Set<String> among) {
        List<String> refs = new ArrayList<>();
        for (TransactionInput input : LedgerOrdering.sortedInputs(txn)) {
            String ref = input.getTransactionId() + "#" + input.getIndex();
            if (among.contains(ref)) refs.add(ref);
        }
        return refs;
    }

    /** Quantity of {@code unit} in an output. */
    static BigInteger quantity(TransactionOutput output, String unit) {
        if (output.getValue().getMultiAssets() == null) return BigInteger.ZERO;
        BigInteger total = BigInteger.ZERO;
        for (MultiAsset ma : output.getValue().getMultiAssets()) {
            for (Asset asset : ma.getAssets()) {
                if ((ma.getPolicyId() + HexUtil.encodeHexString(asset.getNameAsBytes())).equalsIgnoreCase(unit))
                    total = total.add(asset.getValue());
            }
        }
        return total;
    }

    /** The mint field's quantity for {@code unit}, or null when absent. */
    static BigInteger minted(Transaction txn, String unit) {
        if (txn.getBody().getMint() == null) return null;
        for (MultiAsset ma : txn.getBody().getMint()) {
            for (Asset asset : ma.getAssets()) {
                if ((ma.getPolicyId() + HexUtil.encodeHexString(asset.getNameAsBytes())).equalsIgnoreCase(unit))
                    return asset.getValue();
            }
        }
        return null;
    }

    static boolean isBaseScript(TransactionOutput output) {
        try {
            return AddressProvider.getPaymentCredential(new Address(output.getAddress()))
                    .map(c -> HexUtil.encodeHexString(c.getBytes()).equalsIgnoreCase(hash(BASE)))
                    .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- helpers

    static ProtocolParams protocolParams() {
        try (InputStream in = ThirdPartyFixture.class.getResourceAsStream("/cip113/protocol-params.json")) {
            return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readValue(in, ProtocolParams.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static PlutusScript script(String lastByte) {
        return PlutusBlueprintUtil.getPlutusScriptFromCompiledCode("4501010024" + lastByte, PlutusVersion.v3);
    }

    static String hash(PlutusScript script) {
        try {
            return script.getPolicyId();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String policyFor(PlutusScript mintingLogic) {
        return hash(PolicyIdDerivation.issuanceScript(TEMPLATE, Credential.fromScript(hash(mintingLogic))));
    }

    /** UTxOs by address, all on the first page; spent outputs still resolve by reference. */
    static final class InMemoryUtxoSupplier implements UtxoSupplier {
        private final Map<String, List<Utxo>> byAddress;
        private final List<Utxo> spent;

        InMemoryUtxoSupplier(Map<String, List<Utxo>> byAddress, List<Utxo> spent) {
            this.byAddress = byAddress;
            this.spent = spent;
        }

        @Override
        public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
            return page != null && page == 0 ? byAddress.getOrDefault(address, List.of()) : List.of();
        }

        @Override
        public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {
            return java.util.stream.Stream.concat(byAddress.values().stream().flatMap(List::stream), spent.stream())
                    .filter(u -> u.getTxHash().equals(txHash) && u.getOutputIndex() == outputIndex)
                    .findFirst();
        }
    }
}
