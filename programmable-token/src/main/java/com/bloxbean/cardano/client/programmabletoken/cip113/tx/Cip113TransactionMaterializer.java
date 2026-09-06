package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.address.CredentialType;
import com.bloxbean.cardano.client.api.MinAdaCalculator;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployment;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Exception;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProtocolService;
import com.bloxbean.cardano.client.programmabletoken.cip113.LedgerOrdering;
import com.bloxbean.cardano.client.programmabletoken.cip113.PolicyIdDerivation;
import com.bloxbean.cardano.client.programmabletoken.cip113.PolicyOrdering;
import com.bloxbean.cardano.client.programmabletoken.cip113.SmartWalletAddress;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.Cip113Redeemers;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.IssuanceCborHex;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.RegistryNode;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.intent.CollectFromIntent;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.spec.Withdrawal;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Build-local CIP-113 materializer: turns the typed programmable-token operations of one
 * transaction into ordinary QuickTx intents, then resolves every positional redeemer index once
 * the transaction is final.
 *
 * <p>Package-private on purpose. {@link Cip113BuildExtension} is the only caller: it constructs
 * one materializer per authored transaction, records the aggregated operations through the
 * {@code record*} verbs, and hands the generated intents to QuickTx as a build-local overlay.
 * Nothing here is public API.</p>
 *
 * <p>Two behaviours are worth knowing:</p>
 * <ul>
 *   <li>Programmable outputs always sit at a party's <i>smart wallet</i> — the base-script payment
 *       credential plus the party's own credential — and hold one programmable policy each.</li>
 *   <li>Scripts the deployment published as reference scripts are referenced rather than carried;
 *       anything else is resolved by hash through {@link DeploymentScripts}.</li>
 * </ul>
 */
@Slf4j
final class Cip113TransactionMaterializer extends Tx {

    /** Which core delegate authorises the base-script spends of this transaction. */
    private enum Dispatch { TRANSFER, THIRD_PARTY, UNFRACKING }

    private final Cip113Deployment deployment;
    private final RegistryLookup registry;
    private final UtxoSupplier utxoSupplier;

    private Address owner;
    private Utxo coordinationUtxo;

    /** Substandard redeemers, keyed by policy id. */
    private final Map<String, PlutusData> substandardRedeemers = new LinkedHashMap<>();

    /** Issuance-specific burn redeemers, kept distinct from transfer authorization. */
    private final Map<String, PlutusData> burnIssuanceRedeemers = new LinkedHashMap<>();

    /** Published scripts added as reference inputs, keyed by hash so none is referenced twice. */
    private final Map<String, PlutusScript> referencedScripts = new LinkedHashMap<>();

    /** Logic-script hashes already in the witness set, so none is attached twice. */
    private final Set<String> attachedLogicScripts = new LinkedHashSet<>();

    /** Reward address -> redeemer, so one withdrawal is emitted per reward account. */
    private final Map<String, PlutusData> logicWithdrawals = new LinkedHashMap<>();

    /** Global-state policies already referenced, so each is added once. */
    private final Set<String> globalStateReferenced = new LinkedHashSet<>();

    /** Resolves a global-state UTxO by NFT policy; null when wired by hand. */
    private Function<String, Utxo> globalStateResolver;

    /** Programmable payments declared but not yet turned into inputs/withdrawals. */
    private final Map<String, List<PendingPayment>> pending = new LinkedHashMap<>();

    /** Policies already materialised, so the work is not repeated. */
    private final Set<String> materialised = new LinkedHashSet<>();

    /** Base-script UTxOs being spent, in declaration order. */
    private final List<Utxo> plbInputs = new ArrayList<>();

    /** Registry nodes referenced, keyed by policy id. */
    private final Map<String, RegistryLookup.RegistryNodeUtxo> referencedNodes = new LinkedHashMap<>();

    /** Covering nodes for unregistered policies co-resident in selected PLB inputs. */
    private final Map<String, RegistryLookup.RegistryNodeUtxo> coveringProofNodes = new LinkedHashMap<>();

    /** Quantities being destroyed, keyed by policy id. Emitted as a negative mint. */
    private final Map<String, List<Asset>> burns = new LinkedHashMap<>();

    /**
     * Policies whose mint proof is an OUTPUT rather than a reference input, keyed to the registry
     * NFT policy the node output carries. Populated when a token is minted in the same transaction
     * that registers it — its node does not exist to be referenced yet.
     */
    private final Map<String, String> mintProofFromOutput = new LinkedHashMap<>();

    private Dispatch dispatch = Dispatch.TRANSFER;

    /** Whose smart wallet a third-party action acts on. Null unless {@link #thirdPartyFrom} ran. */
    private Address thirdPartyHolder;

    /** The policy being unfracked. Null unless {@link #recordUnfrackForExtension} ran. */
    private String unfrackPolicy;

    /** Where the paired continuing outputs sit, and how many — resolved into outputs_start_idx. */
    private String pairedOutputAddress;
    private int pairedOutputCount;

    /** Resolved in preTxEvaluation: where the paired continuing outputs start. */
    private int outputsStartIdx;

    /** The spec this transaction is registering, when it registers one. */
    private RegistryNodeSpec registeringSpec;

    /** Registry nodes backing a mint proof, keyed by policy id. */
    private final Map<String, RegistryLookup.RegistryNodeUtxo> mintProofNodes = new LinkedHashMap<>();

    /** "txHash#index" of every base-script input, so only their Spend redeemers are rewritten. */
    private final Set<String> plbInputRefs = new LinkedHashSet<>();

    /** The core transfer withdrawal is one per transaction, not one per policy. */
    private boolean transferWithdrawalAdded;

    /** Whether index resolution has run, so the post-balance pass knows to verify or to resolve. */
    private boolean indicesResolved;

    /**
     * Fallback floor, used only when no {@link ProtocolParamsSupplier} is configured.
     *
     * <p>A guess in both directions: generous for a bare token output, and short for one carrying
     * a large inline datum. The protocol-params supplier replaces it with the real calculation.</p>
     */
    static final BigInteger MIN_ADA_PER_PROGRAMMABLE_OUTPUT = BigInteger.valueOf(1_500_000L);

    /**
     * Fallback headroom, used only when protocol parameters are unavailable and the real fee
     * ceiling cannot be computed.
     */
    private static final BigInteger FALLBACK_FEE_HEADROOM = BigInteger.valueOf(3_000_000L);

    /**
     * ADA set aside for a registry-node output.
     *
     * <p>Deliberately generous: the node's inline datum carries four credentials and two policy
     * ids, which {@link MinAdaCalculator} does not see when it is handed amounts alone. The
     * surplus comes back as change, so over-estimating costs nothing, while under-estimating
     * leaves a negative output and makes the builder skip the pass that resolves redeemer
     * indices.</p>
     */
    private static final BigInteger REGISTRY_NODE_OUTPUT_ALLOWANCE = BigInteger.valueOf(5_000_000L);

    /** Lovelace this builder has explicitly put into outputs. */
    private BigInteger declaredOutputLovelace = BigInteger.ZERO;

    /** Ordinary fee-payer inputs pinned so balancing has nothing to add. */
    private final Set<String> pinnedInputRefs = new LinkedHashSet<>();
    private final Set<String> externallyReservedInputRefs = new LinkedHashSet<>();
    private BigInteger pinnedLovelace = BigInteger.ZERO;

    /** Resolves the deployment's applied scripts by hash. */
    private DeploymentScripts scripts;
    private ProtocolParamsSupplier protocolParamsSupplier;

    /** Whether the base script and transfer delegate have been attached yet. */
    private boolean coreScriptsAttached;

    /** The IssuanceCborHex UTxO — required to derive a policy id and to register. */
    private Utxo issuanceTemplateUtxo;

    /** Policy id of the token registered by this transaction, when it registers one. */
    private String registeredPolicyId;

    private Snapshot resolvedSnapshot;
    private String resolvedIndexFingerprint;

    private static final String LOVELACE = "lovelace";

    /**
     * Build against the extension's protocol service and a per-build registry snapshot.
     *
     * <p>Takes the UTxO supplier separately because the service exposes chain reads, not the
     * supplier it happens to read them through.</p>
     */
    Cip113TransactionMaterializer(Cip113ProtocolService service, UtxoSupplier utxoSupplier,
                                  RegistryLookup buildRegistry) {
        this(service.deployment(), buildRegistry, utxoSupplier);
        this.scripts = service.scripts();
        this.protocolParamsSupplier = service.protocolParamsSupplier();
        this.globalStateResolver = service::globalStateUtxo;
        // Optional here: a service that could not resolve the coordination UTxO yields null, and
        // the verb that needs one says so by name instead of failing inside Tx.readFrom.
        Utxo coordination = service.coordinationUtxo();
        if (coordination != null) {
            this.coordinationUtxo = coordination;
            readFrom(coordination);
        }
        this.issuanceTemplateUtxo = service.issuanceTemplateUtxo();
    }

    /**
     * Manual wiring, for tests and callers assembling the pieces themselves — an indexer-backed
     * {@link RegistryLookup}, or a deployment not read from a backend at all.
     */
    Cip113TransactionMaterializer(Cip113Deployment deployment, RegistryLookup registry,
                                  UtxoSupplier utxoSupplier) {
        this.deployment = deployment;
        this.registry = registry;
        this.utxoSupplier = utxoSupplier;
    }

    // -------------------------------------------------------------- setup

    @Override
    public Cip113TransactionMaterializer from(String sender) {
        super.from(sender);
        this.owner = new Address(sender);
        return this;
    }

    Cip113TransactionMaterializer excludeInputs(Set<String> inputRefs) {
        if (inputRefs != null) externallyReservedInputRefs.addAll(inputRefs);
        return this;
    }

    /**
     * Min-ADA for an output holding exactly these amounts (and datum) at this address.
     *
     * <p>Falls back to the fixed floor when no supplier is configured, and on any failure to read
     * the parameters — a transaction that would otherwise build should not fail because a
     * protocol-parameter lookup was unavailable.</p>
     */
    private BigInteger minAdaFor(Address destination, List<Amount> amounts, PlutusData inlineDatum) {
        if (protocolParamsSupplier == null) return MIN_ADA_PER_PROGRAMMABLE_OUTPUT;
        try {
            List<MultiAsset> multiAssets = new ArrayList<>();
            BigInteger coin = BigInteger.ZERO;
            for (Amount amount : amounts) {
                if (LOVELACE.equals(amount.getUnit())) {
                    coin = coin.add(amount.getQuantity());
                } else {
                    multiAssets.add(AssetUtil.getMultiAssetFromUnitAndAmount(
                            amount.getUnit(), amount.getQuantity()));
                }
            }
            TransactionOutput output = TransactionOutput.builder()
                    .address(destination.toBech32())
                    .value(Value.builder().coin(coin).multiAssets(multiAssets).build())
                    .inlineDatum(inlineDatum)
                    .build();
            BigInteger minAda = new MinAdaCalculator(protocolParamsSupplier.getProtocolParams())
                    .calculateMinAda(output);
            return minAda.signum() > 0 ? minAda : MIN_ADA_PER_PROGRAMMABLE_OUTPUT;
        } catch (Exception e) {
            log.debug("Could not compute min-ADA from protocol params; using the fixed floor", e);
            return MIN_ADA_PER_PROGRAMMABLE_OUTPUT;
        }
    }

    /**
     * The largest fee any valid transaction could be asked to pay, from protocol parameters.
     *
     * <p>Funding to this bound is what makes index resolution safe. Balancing appends an input
     * when the selected inputs cannot cover outputs plus fee, and appending one shifts the
     * canonical input ordering that every Spend redeemer already points at. Covering the maximum
     * possible fee up front removes the reason to append.</p>
     *
     * <pre>
     *   maxTxSize x minFeeA + minFeeB          largest a transaction can be
     * + priceMem  x maxTxExMem                 largest script memory budget
     * + priceStep x maxTxExSteps               largest script step budget
     * + refScriptCostPerByte x refScriptBytes  reference scripts actually referenced
     * </pre>
     *
     * <p>Returns {@link #FALLBACK_FEE_HEADROOM} when no {@link ProtocolParamsSupplier} is
     * configured or the lookup fails.</p>
     */
    private BigInteger maxFeeCeiling() {
        if (protocolParamsSupplier == null) return FALLBACK_FEE_HEADROOM;
        try {
            ProtocolParams pp = protocolParamsSupplier.getProtocolParams();
            BigInteger ceiling = BigInteger.valueOf(pp.getMaxTxSize())
                    .multiply(BigInteger.valueOf(pp.getMinFeeA()))
                    .add(BigInteger.valueOf(pp.getMinFeeB()));

            ceiling = ceiling.add(priced(pp.getPriceMem(), pp.getMaxTxExMem()));
            ceiling = ceiling.add(priced(pp.getPriceStep(), pp.getMaxTxExSteps()));

            // Reference scripts are charged per byte. Their sizes are not on the UTxO, so each
            // referenced script is costed at the largest a script could be. Over-stating is the
            // safe direction: the surplus comes back as change.
            if (pp.getMinFeeRefScriptCostPerByte() != null) {
                long referenced = referencedScriptCount();
                if (referenced > 0) {
                    ceiling = ceiling.add(pp.getMinFeeRefScriptCostPerByte()
                            .multiply(java.math.BigDecimal.valueOf(referenced))
                            .multiply(java.math.BigDecimal.valueOf(pp.getMaxTxSize()))
                            .toBigInteger());
                }
            }
            return ceiling;
        } catch (Exception e) {
            log.debug("Could not compute the fee ceiling from protocol params; using the fallback", e);
            return FALLBACK_FEE_HEADROOM;
        }
    }

    private static BigInteger priced(java.math.BigDecimal price, String maxUnits) {
        if (price == null || maxUnits == null || maxUnits.isBlank()) return BigInteger.ZERO;
        return price.multiply(new java.math.BigDecimal(maxUnits)).toBigInteger();
    }

    /** How many reference inputs declare a reference script, which the fee is charged for. */
    private long referencedScriptCount() {
        long count = referencedNodes.values().stream()
                .filter(n -> n.getUtxo().getReferenceScriptHash() != null
                        && !n.getUtxo().getReferenceScriptHash().isEmpty())
                .count();
        if (coordinationUtxo != null && coordinationUtxo.getReferenceScriptHash() != null
                && !coordinationUtxo.getReferenceScriptHash().isEmpty()) {
            count++;
        }
        return count;
    }

    /**
     * Bring in enough ordinary ADA up front that balancing never has to add an input.
     *
     * <p>This is what makes the whole index-resolution scheme work. For a script transaction CCL
     * leaves fee-payer selection to balancing, so until that runs the change output carries a
     * negative coin — and {@code QuickTxBuilder._build()} skips {@code preTxEvaluation} and the
     * first script evaluation whenever any output does. Funding the transaction here keeps the
     * change positive, so the redeemers are resolved before any evaluation and balancing has
     * nothing left to append.</p>
     */
    private void fundFromFeePayer() {
        String feePayer = getFromAddress();
        if (feePayer == null) return;

        BigInteger fromPlb = BigInteger.ZERO;
        for (Utxo utxo : plbInputs) {
            for (Amount amount : utxo.getAmount()) {
                if (LOVELACE.equals(amount.getUnit())) fromPlb = fromPlb.add(amount.getQuantity());
            }
        }

        BigInteger ceiling = maxFeeCeiling();
        BigInteger needed = declaredOutputLovelace.add(ceiling).subtract(fromPlb);
        if (needed.signum() <= 0) return;

        BigInteger picked = pinnedLovelace;
        for (Utxo utxo : utxoSupplier.getAll(feePayer)) {
            if (picked.compareTo(needed) >= 0) break;
            String key = ref(utxo.getTxHash(), utxo.getOutputIndex());
            if (plbInputRefs.contains(key) || pinnedInputRefs.contains(key)
                    || externallyReservedInputRefs.contains(key)) continue;
            // Plain-ADA UTxOs only: pulling in another asset would drag it through the
            // programmable output rules for no reason.
            boolean adaOnly = utxo.getAmount().stream().allMatch(a -> LOVELACE.equals(a.getUnit()));
            if (!adaOnly) continue;

            // A UTxO publishing a reference script looks exactly like plain ADA to the filter
            // above, and spending one destroys the published script for everybody — including the
            // transactions this builder is about to reference it from. Skip them.
            if (utxo.getReferenceScriptHash() != null && !utxo.getReferenceScriptHash().isEmpty())
                continue;

            pinnedInputRefs.add(key);
            collectFrom(List.of(utxo));
            for (Amount amount : utxo.getAmount()) {
                if (LOVELACE.equals(amount.getUnit())) picked = picked.add(amount.getQuantity());
            }
        }
        pinnedLovelace = picked;

        if (picked.compareTo(needed) < 0) {
            // Failing here beats failing after evaluation: at this point the shortfall can be
            // named exactly, whereas the downstream symptom is an appended input, shifted
            // redeemer indices and an evaluation failure that points nowhere.
            throw new Cip113Exception("Not enough plain ADA to fund this transaction safely."
                    + " Needed " + needed + " lovelace of ADA-only UTxOs at " + feePayer
                    + " but could pin only " + picked + "."
                    + " The target covers the declared outputs (" + declaredOutputLovelace
                    + ") plus the largest fee protocol parameters allow (" + ceiling + "),"
                    + " which is what stops balancing from appending an input and invalidating"
                    + " every resolved redeemer index. The surplus returns as change, so this is a"
                    + " coin-selection requirement rather than a cost — but the ADA has to be"
                    + " there, in UTxOs holding nothing but ADA.");
        }
    }

    // -------------------------------------------------------------- verbs

    /** Record an owner transfer of a registered policy. Never falls back to an ordinary payment. */
    Cip113TransactionMaterializer recordTransferForExtension(String policyId, String address,
                                                             Amount amount, PlutusData inlineDatum) {
        String amountPolicy = policyOf(amount);
        if (amountPolicy == null || !policyId.equalsIgnoreCase(amountPolicy))
            throw new Cip113Exception("Transfer amount does not belong to policy " + policyId);
        if (registry.byPolicy(policyId).isEmpty())
            throw new Cip113Exception("Policy " + policyId + " is not registered");
        requireInlineDatumWithinBound(inlineDatum, "transfer");
        addProgrammablePayment(policyId.toLowerCase(), address, amount, inlineDatum);
        return this;
    }

    private void addProgrammablePayment(String policyId, String address, Amount amount,
                                        PlutusData inlineDatum) {
        Address target = new Address(address);
        Address destination = SmartWalletAddress.isSmartWallet(deployment, target)
                ? target
                : SmartWalletAddress.ofPaymentCredential(deployment, target);

        pending.computeIfAbsent(policyId, k -> new ArrayList<>())
                .add(new PendingPayment(destination, amount));

        // One programmable policy per output, plus its own min-ADA.
        BigInteger minAda = minAdaFor(destination, List.of(amount), inlineDatum);
        declaredOutputLovelace = declaredOutputLovelace.add(minAda);
        super.payToAddress(destination.toBech32(), List.of(Amount.lovelace(minAda), amount),
                null, inlineDatum, null, null);
        materialiseIfReady(policyId);
    }

    /**
     * Record a burn of one asset: spend the holder's supply, destroy part of it, return the rest.
     *
     * <p>Recorded as a payment with no destination, which is exactly what a burn is — it makes
     * {@code selectInputs} pull enough supply in and makes {@code returnProgrammableChange} treat
     * the burned amount as spoken for, without emitting an output for it.</p>
     *
     * <p>There is one transfer-logic and one issuance-logic invocation per policy, so every burn of
     * a policy must agree on both redeemers; a conflicting value fails here rather than silently
     * winning by being recorded last.</p>
     */
    void recordBurnForExtension(String policyId, Asset asset, PlutusData transferRedeemer,
                                PlutusData issuanceRedeemer) {
        String key = policyId.toLowerCase();
        rejectConflictingRedeemer(substandardRedeemers.get(key), transferRedeemer, key, "transfer");
        rejectConflictingRedeemer(burnIssuanceRedeemers.get(key), issuanceRedeemer, key, "issuance");

        BigInteger burned = asset.getValue().negate();          // arrives negative, as Tx expects
        String unit = key + HexUtil.encodeHexString(asset.getNameAsBytes());

        pending.computeIfAbsent(key, k -> new ArrayList<>())
                .add(new PendingPayment(null, Amount.builder().unit(unit).quantity(burned).build()));

        burns.computeIfAbsent(key, k -> new ArrayList<>()).add(asset);

        substandardRedeemers.put(key, transferRedeemer);
        burnIssuanceRedeemers.put(key, issuanceRedeemer);
        log.debug("Burning {} of {}", burned, unit);
    }

    private static void rejectConflictingRedeemer(PlutusData existing, PlutusData candidate,
                                                  String policy, String role) {
        if (existing == null || PlutusDataEquality.equals(existing, candidate)) return;
        throw new Cip113Exception("Policy " + policy + " declares different " + role
                + " redeemers in one transaction. There is one " + role + " logic invocation per"
                + " policy, so every operation on it must agree — split them into separate"
                + " transactions or use the same redeemer.");
    }

    /**
     * Mint a registered (or being-registered) programmable policy. Fails closed for anything else:
     * an unregistered policy cannot be a programmable token, and silently minting it as an ordinary
     * native asset would move it past the rules that exist to constrain it.
     */
    void recordMintForExtension(String policyId, List<Asset> assets, PlutusData issuanceRedeemer,
                                String receiver, PlutusData inlineDatum) {
        String key = policyId.toLowerCase();
        boolean programmable = registry.byPolicy(key).isPresent()
                || key.equalsIgnoreCase(registeredPolicyId);
        if (!programmable) {
            throw new Cip113Exception("Policy " + key + " is not registered, so it cannot be minted"
                    + " as a programmable token. Register it first — in an earlier transaction, or"
                    + " in this one with a register intent whose name the mint references.");
        }
        requireInlineDatumWithinBound(inlineDatum, "mint");
        for (Asset asset : assets) {
            if (asset.getValue().signum() <= 0)
                throw new Cip113Exception("A programmable mint needs positive quantities; use a"
                        + " burn intent to destroy supply.");
            recordMint(key, asset, receiver, issuanceRedeemer, inlineDatum);
        }
    }

    /**
     * The substandard's redeemer for one policy — mandatory for every programmable payment.
     *
     * <p>Attached per <i>policy</i>, not per payment: pay the same token to three recipients
     * and there is still one set of rules to satisfy.</p>
     */
    Cip113TransactionMaterializer withRedeemer(String policyId, PlutusData substandardRedeemer) {
        String key = policyId.toLowerCase();
        rejectConflictingRedeemer(substandardRedeemers.get(key), substandardRedeemer, key, "transfer");
        substandardRedeemers.put(key, substandardRedeemer);
        materialiseIfReady(key);
        return this;
    }

    List<Utxo> selectedInputs() {
        Map<String, Utxo> selected = new LinkedHashMap<>();
        getIntentions().stream()
                .filter(CollectFromIntent.class::isInstance)
                .map(CollectFromIntent.class::cast)
                .map(CollectFromIntent::getUtxos)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .forEach(utxo -> selected.putIfAbsent(
                        ref(utxo.getTxHash(), utxo.getOutputIndex()), utxo));
        return new ArrayList<>(selected.values());
    }

    // ------------------------------------------------------------- assembly

    private void materialiseIfReady(String policyId) {
        String key = policyId.toLowerCase();
        if (materialised.contains(key)) return;
        if (!pending.containsKey(key) || !substandardRedeemers.containsKey(key)) return;
        if (owner == null) return;               // need the owner before selecting inputs
        requireCoordinationUtxo("a programmable payment");

        RegistryLookup.RegistryNodeUtxo node = registry.byPolicy(key)
                .orElseThrow(() -> new Cip113Exception("Policy " + key + " is not registered"));

        Map<String, BigInteger> requiredByUnit = new LinkedHashMap<>();
        for (PendingPayment payment : pending.get(key)) {
            requiredByUnit.merge(payment.amount.getUnit(), payment.amount.getQuantity(), BigInteger::add);
        }

        if (dispatch == Dispatch.THIRD_PARTY) {
            materialiseThirdParty(key, node, requiredByUnit);
            materialised.add(key);
            return;
        }

        List<Utxo> selected = selectInputs(key, requiredByUnit);
        if (selected.isEmpty()) {
            throw new Cip113Exception("No base-script UTxOs holding policy " + key
                    + " found at " + smartWallet().toBech32());
        }

        attachCoreScripts();

        // Placeholder indices — the real ones only exist once the transaction is final,
        // so they are rewritten in preTxEvaluation.
        PlutusData spendPlaceholder = Cip113Redeemers.spendViaTransfer(0, 0);
        collectFrom(selected, spendPlaceholder);
        plbInputs.addAll(selected);
        selected.forEach(u -> plbInputRefs.add(ref(u.getTxHash(), u.getOutputIndex())));

        readFrom(node.getUtxo());
        referencedNodes.put(key, node);
        prepareIncidentalPolicyProofs(selected);

        addGlobalStateReference(key, node.getDatum().getGlobalStateCs());

        // Core delegate: exactly one per transaction, whatever the policy count. A second
        // withdrawal against the same credential would collide in the ledger's withdrawal map
        // and shift every later redeemer index.
        if (!transferWithdrawalAdded) {
            withdraw(deployment.transferRewardAddress(), BigInteger.ZERO,
                    Cip113Redeemers.transfer(0, List.of()));
            transferWithdrawalAdded = true;
        }

        // The token's own transfer logic, with the caller's redeemer. Which script that is comes
        // from the registry node, not from the caller.
        invokeLogicScript(node.getDatum().getTransferLogicScript(),
                substandardRedeemers.get(key), "transfer logic");

        materialiseBurn(key, node);

        returnProgrammableChange(selected);
        fundFromFeePayer();

        materialised.add(key);
    }

    /**
     * Emit the negative mint for a policy being burned, if this transaction burns one.
     *
     * <p>A burn is a transfer whose destination is nowhere: the spend is dispatched through
     * {@code SpendViaTransfer} like any other, and {@code validate_transfer} folds the negative
     * mint into the input side for any policy present in the base-script inputs, so the remainder
     * simply has to come back — which {@link #returnProgrammableChange} already does.</p>
     *
     * <p>{@code issuance_mint} additionally requires the substandard's <i>minting</i> logic to
     * withdraw-zero, on top of the transfer logic the spend already invokes. When a token uses one
     * credential for both, that is a single withdrawal and {@link #invokeLogicScript} dedupes it.</p>
     */
    private void materialiseBurn(String policyId, RegistryLookup.RegistryNodeUtxo node) {
        List<Asset> burning = burns.get(policyId);
        if (burning == null || burning.isEmpty()) return;

        if (issuanceTemplateUtxo == null) {
            throw new Cip113Exception("Burning needs the issuance template to reassemble the"
                    + " token's issuance script, but the deployment has none resolved.");
        }

        PlutusScript issuanceScript = PolicyIdDerivation.issuanceScript(
                readIssuanceTemplate(), node.getDatum().getMintingLogicScript());
        String derived = scriptHashOf(issuanceScript);
        if (!derived.equalsIgnoreCase(policyId)) {
            throw new Cip113Exception("Assembled issuance script hashes to " + derived
                    + " but the policy being burned is " + policyId
                    + ". The registry node's minting_logic_script does not match this template.");
        }

        attachMintValidator(issuanceScript);
        // Index is a placeholder; resolveMintRedeemers rewrites it once the node's position in
        // the sorted reference inputs is known.
        mintAsset(issuanceScript, burning, Cip113Redeemers.mintRefInput(0));
        mintProofNodes.put(policyId, node);

        // issuance_mint's own authorisation, distinct from the transfer logic above.
        invokeLogicScript(node.getDatum().getMintingLogicScript(),
                burnIssuanceRedeemers.get(policyId), "minting logic");
    }

    /**
     * Spending a base-script UTxO needs the base script itself, and the withdraw-zero needs the
     * transfer delegate. Fetch them once, by hash.
     */
    private void attachCoreScripts() {
        if (coreScriptsAttached) return;
        requireScripts("spending a programmable-token UTxO");
        attachSpending(scripts.programmableLogicBase());
        attachReward(scripts.transferDelegate());
        coreScriptsAttached = true;
    }

    /**
     * Attach a spending validator, preferring the chain's copy over our own.
     *
     * <p>When the deployment published the script as a reference script, pointing at that UTxO
     * costs a few dozen bytes instead of carrying several kilobytes in the witness set. CCL's
     * reference-script resolver picks it up from {@code readFrom}, and it runs before index
     * resolution, so the extra reference input is already in place when the positional indices
     * are computed.</p>
     */
    private void attachSpending(PlutusScript script) {
        referenceIfPublished(script);
        attachSpendingValidator(script);
    }

    /** {@link #attachSpending} for a withdraw-zero delegate. */
    private void attachReward(PlutusScript script) {
        referenceIfPublished(script);
        attachRewardValidator(script);
    }

    /**
     * Add the reference input publishing a script, if the deployment published one.
     *
     * <p>The validator is still attached alongside: attaching is what makes CCL emit the Spend or
     * Reward redeemer for it. CCL's own {@code ReferenceScriptResolver} then recognises that a
     * reference input already carries the script and drops the witness copy.</p>
     */
    private void referenceIfPublished(PlutusScript script) {
        if (scripts == null || script == null) return;
        String hash;
        try {
            hash = script.getPolicyId();
        } catch (Exception e) {
            return;                       // unhashable: the witness path will report it
        }
        Optional<Utxo> published = scripts.publishedAt(hash);
        if (published.isEmpty()) return;
        if (referencedScripts.putIfAbsent(hash.toLowerCase(), script) == null) {
            readFrom(published.get());
            log.debug("Referencing published script {} at {}#{}", hash,
                    published.get().getTxHash(), published.get().getOutputIndex());
        }
    }

    /**
     * The scripts this transaction reads from reference inputs instead of witnessing.
     *
     * <p>A reference input alone is not enough: without telling the builder, it still expects the
     * script in the witness set and the evaluator reports the redeemers as missing.</p>
     */
    java.util.Collection<PlutusScript> referencedScripts() {
        return referencedScripts.values();
    }

    /**
     * Send whatever the spent UTxOs held beyond what is being paid out back to the owner's own
     * smart wallet.
     *
     * <p>Without this the leftover is swept into the ordinary change output, which sits at the
     * fee payer's normal address — and {@code validate_transfer} requires outputs at the base
     * script to be a per-policy superset of the inputs. Programmable tokens can only ever move
     * between smart wallets.</p>
     *
     * <p>Computed per <i>unit</i>, not per policy: one UTxO can hold several asset names under
     * the same policy and each needs its own remainder.</p>
     */
    private void returnProgrammableChange(List<Utxo> spent) {
        Map<String, BigInteger> held = new LinkedHashMap<>();
        for (Utxo utxo : spent) {
            for (Amount amount : utxo.getAmount()) {
                if (policyOf(amount) == null) continue;               // lovelace rides along
                held.merge(amount.getUnit(), amount.getQuantity(), BigInteger::add);
            }
        }

        List<Amount> change = new ArrayList<>();
        for (Map.Entry<String, BigInteger> entry : held.entrySet()) {
            BigInteger remainder = entry.getValue().subtract(paidOut(entry.getKey()));
            if (remainder.signum() > 0) {
                change.add(Amount.builder().unit(entry.getKey()).quantity(remainder).build());
            }
        }
        if (change.isEmpty()) return;

        // One programmable policy per output, so a UTxO holding several policies is split apart.
        Map<String, List<Amount>> byPolicy = new LinkedHashMap<>();
        for (Amount amount : change) {
            byPolicy.computeIfAbsent(policyOf(amount), k -> new ArrayList<>()).add(amount);
        }

        Address ownWalletAddress = smartWallet();
        String ownWallet = ownWalletAddress.toBech32();
        for (List<Amount> group : byPolicy.values()) {
            List<Amount> output = new ArrayList<>();
            BigInteger minAda = minAdaFor(ownWalletAddress, group, null);
            output.add(Amount.lovelace(minAda));
            output.addAll(group);
            declaredOutputLovelace = declaredOutputLovelace.add(minAda);
            super.payToAddress(ownWallet, output);
            log.debug("Returning programmable change to {}: {}", ownWallet, group);
        }
    }

    /** How much of a unit this transaction already pays out. */
    private BigInteger paidOut(String unit) {
        BigInteger total = BigInteger.ZERO;
        for (List<PendingPayment> payments : pending.values()) {
            for (PendingPayment payment : payments) {
                if (unit.equals(payment.amount.getUnit())) {
                    total = total.add(payment.amount.getQuantity());
                }
            }
        }
        return total;
    }

    private List<Utxo> selectInputs(String policyId, Map<String, BigInteger> requiredByUnit) {
        return selectInputs(policyId, requiredByUnit, smartWallet());
    }

    private List<Utxo> selectInputs(String policyId, Map<String, BigInteger> requiredByUnit,
                                    Address wallet) {
        List<Utxo> candidates = new ArrayList<>(utxoSupplier.getAll(wallet.toBech32()));
        candidates.removeIf(utxo -> externallyReservedInputRefs.contains(
                ref(utxo.getTxHash(), utxo.getOutputIndex())));

        // Prefer single-policy UTxOs, then the candidates covering most of what is needed. The
        // deterministic tie-breakers keep positional third-party pairing reproducible and avoid
        // turning a fragmented wallet into needless script inputs and continuing outputs.
        candidates.sort(Comparator.comparingInt(Cip113TransactionMaterializer::policyCount)
                .thenComparing((Utxo utxo) -> usefulQuantity(utxo, requiredByUnit),
                        Comparator.reverseOrder())
                .thenComparing(Utxo::getTxHash)
                .thenComparingInt(Utxo::getOutputIndex));

        // Track each unit separately: an input holding "PolicyA.TokenB" does nothing for a
        // payment of "PolicyA.TokenC", even though both share a policy.
        Map<String, BigInteger> accumulated = new LinkedHashMap<>();
        List<Utxo> selected = new ArrayList<>();

        for (Utxo utxo : candidates) {
            boolean useful = false;
            for (Amount amount : utxo.getAmount()) {
                String unit = amount.getUnit();
                if (!requiredByUnit.containsKey(unit)) continue;
                if (accumulated.getOrDefault(unit, BigInteger.ZERO)
                        .compareTo(requiredByUnit.get(unit)) >= 0) continue;
                useful = true;
            }
            if (!useful) continue;

            selected.add(utxo);
            for (Amount amount : utxo.getAmount()) {
                if (requiredByUnit.containsKey(amount.getUnit())) {
                    accumulated.merge(amount.getUnit(), amount.getQuantity(), BigInteger::add);
                }
            }
            if (satisfied(accumulated, requiredByUnit)) break;
        }

        if (!satisfied(accumulated, requiredByUnit)) {
            throw new Cip113Exception("Smart wallet " + wallet.toBech32()
                    + " does not hold enough. Required " + requiredByUnit
                    + ", found " + accumulated + ".");
        }
        return selected;
    }

    private static BigInteger usefulQuantity(Utxo utxo, Map<String, BigInteger> requiredByUnit) {
        BigInteger total = BigInteger.ZERO;
        for (Amount amount : utxo.getAmount()) {
            BigInteger required = requiredByUnit.get(amount.getUnit());
            if (required != null)
                total = total.add(amount.getQuantity().min(required));
        }
        return total;
    }

    private static boolean satisfied(Map<String, BigInteger> have, Map<String, BigInteger> need) {
        for (Map.Entry<String, BigInteger> entry : need.entrySet()) {
            if (have.getOrDefault(entry.getKey(), BigInteger.ZERO)
                    .compareTo(entry.getValue()) < 0) return false;
        }
        return true;
    }

    private Address smartWallet() {
        return SmartWalletAddress.ofPaymentCredential(deployment, owner);
    }

    // ---------------------------------------------------- index finalisation

    /**
     * Resolve every positional index against the canonical orderings, before ex-unit evaluation
     * and fee calculation. All intents have applied by this point, so inputs, reference inputs,
     * withdrawals and outputs are present.
     */
    @Override
    protected void preTxEvaluation(Transaction txn) {
        super.preTxEvaluation(txn);
        resolveIndices(txn);
    }

    void finalizeForEvaluation(Transaction txn) {
        preTxEvaluation(txn);
    }

    boolean refinalizeIfChanged(Transaction txn) {
        if (!hasCip113Work()) return false;
        Snapshot current = snapshot(txn);
        if (current.equals(resolvedSnapshot)) return false;
        String currentIndexFingerprint = indexFingerprint(txn);
        if (Objects.equals(currentIndexFingerprint, resolvedIndexFingerprint)) {
            // Balancing changed only non-index-bearing content (normally fee/change quantities).
            // Accept that as the new final snapshot without another non-idempotent balance pass.
            resolvedSnapshot = current;
            return false;
        }
        resolveIndices(txn);
        return true;
    }

    /**
     * Verify the final transaction still matches the orderings the redeemers were resolved
     * against. Rewriting them here would be too late: the builder has already evaluated the
     * scripts, priced the ex-units and computed the script-data hash.
     */
    void verifyStable(Transaction txn) {
        if (!hasCip113Work()) return;
        if (!indicesResolved) {
            throw new Cip113Exception("CIP-113 indices were never resolved: the builder skipped"
                    + " pre-evaluation, which happens when an output still carries a negative coin"
                    + " when _build() runs. The builder funds itself from ADA-only fee-payer UTxOs"
                    + " precisely to keep the change positive, so reaching here means the fee"
                    + " payer had no plain-ADA UTxO to pin.");
        }
        Snapshot current = snapshot(txn);
        if (!current.equals(resolvedSnapshot))
            throw new Cip113Exception("Transaction changed after final CIP-113 evaluation ("
                    + resolvedSnapshot + " -> " + current + "). When balancing appends an input"
                    + " the canonical input order shifts, so every Spend redeemer points at the"
                    + " wrong input. The builder pins inputs covering its declared outputs plus the"
                    + " largest fee protocol parameters permit, so balancing should never have"
                    + " needed to append one — report it.");
    }

    /** The orderings the redeemers depend on, for detecting post-balance drift. */
    private static final class Snapshot {
        final String inputs, refInputs, withdrawals, outputs, mintPolicies;

        Snapshot(String inputs, String refInputs, String withdrawals,
                 String outputs, String mintPolicies) {
            this.inputs = inputs;
            this.refInputs = refInputs;
            this.withdrawals = withdrawals;
            this.outputs = outputs;
            this.mintPolicies = mintPolicies;
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Snapshot)) return false;
            Snapshot s = (Snapshot) o;
            return Objects.equals(inputs, s.inputs)
                    && Objects.equals(refInputs, s.refInputs)
                    && Objects.equals(withdrawals, s.withdrawals)
                    && Objects.equals(outputs, s.outputs)
                    && Objects.equals(mintPolicies, s.mintPolicies);
        }

        @Override public int hashCode() {
            return Objects.hash(inputs, refInputs, withdrawals, outputs, mintPolicies);
        }

        @Override public String toString() {
            return "inputs=" + inputs + " refInputs=" + refInputs
                    + " withdrawals=" + withdrawals + " outputs=" + outputs
                    + " mintPolicies=" + mintPolicies;
        }
    }

    private static Snapshot snapshot(Transaction txn) {
        return new Snapshot(
                orderedContent(txn.getBody().getInputs()),
                orderedContent(txn.getBody().getReferenceInputs()),
                orderedContent(txn.getBody().getWithdrawals()),
                orderedContent(txn.getBody().getOutputs()),
                orderedContent(txn.getBody().getMint()));
    }

    static String indexSensitiveFingerprint(Transaction txn) {
        return indexFingerprint(txn);
    }

    private static String indexFingerprint(Transaction txn) {
        String inputs = orderedContent(txn.getBody().getInputs());
        String refs = orderedContent(txn.getBody().getReferenceInputs());
        String withdrawals = txn.getBody().getWithdrawals() == null ? "[]"
                : txn.getBody().getWithdrawals().stream()
                .map(Withdrawal::getRewardAddress).collect(Collectors.joining(","));
        String outputs = txn.getBody().getOutputs() == null ? "[]"
                : txn.getBody().getOutputs().stream().map(Cip113TransactionMaterializer::outputIdentity)
                .collect(Collectors.joining(","));
        String mint = txn.getBody().getMint() == null ? "[]"
                : txn.getBody().getMint().stream().map(MultiAsset::getPolicyId)
                .collect(Collectors.joining(","));
        return "inputs=" + inputs + " refs=" + refs + " withdrawals=" + withdrawals
                + " outputs=" + outputs + " mint=" + mint;
    }

    private static String outputIdentity(TransactionOutput output) {
        String assets = output.getValue() == null || output.getValue().getMultiAssets() == null ? ""
                : output.getValue().getMultiAssets().stream()
                .flatMap(multiAsset -> multiAsset.getAssets().stream()
                        .map(asset -> multiAsset.getPolicyId() + "."
                                + HexUtil.encodeHexString(asset.getNameAsBytes())))
                .collect(Collectors.joining("+"));
        String datumHash = output.getDatumHash() == null ? ""
                : HexUtil.encodeHexString(output.getDatumHash());
        String inlineDatum = output.getInlineDatum() == null ? ""
                : output.getInlineDatum().serializeToHex();
        String script = output.getScriptRef() == null ? "" : output.getScriptRef().toString();
        return output.getAddress() + "|" + assets + "|" + datumHash + "|" + inlineDatum + "|" + script;
    }

    private static String orderedContent(Object value) {
        return value == null ? "[]" : value.toString();
    }

    private static int size(List<?> list) {
        return list == null ? 0 : list.size();
    }

    /**
     * Whether this transaction has any CIP-113 index to resolve.
     *
     * <p>All three kinds count: a transaction that only registers and mints has no base-script
     * inputs and no reference-input mint proof, so guarding on those two alone would leave
     * {@code issuance_mint} reading the placeholder {@code RefInput(0)}.</p>
     */
    private boolean hasCip113Work() {
        return !plbInputRefs.isEmpty() || !mintProofNodes.isEmpty() || !mintProofFromOutput.isEmpty();
    }

    private void resolveIndices(Transaction txn) {
        if (!hasCip113Work()) return;

        int paramsIdx = LedgerOrdering.indexOfReferenceInput(
                txn, coordinationUtxo.getTxHash(), coordinationUtxo.getOutputIndex());

        resolveRewardRedeemers(txn, paramsIdx);
        resolveSpendRedeemers(txn, paramsIdx);
        resolveMintRedeemers(txn);
        declareOwnerAsRequiredSigner(txn);

        indicesResolved = true;
        resolvedSnapshot = snapshot(txn);
        resolvedIndexFingerprint = indexFingerprint(txn);

        log.info("CIP-113 indices resolved against {} | params_idx={} plbInputs={} refInputs={} withdrawals={}",
                resolvedSnapshot, paramsIdx, plbInputRefs.size(),
                size(txn.getBody().getReferenceInputs()), size(txn.getBody().getWithdrawals()));
    }

    /**
     * Fix up the Reward redeemers: both their <i>index</i> and, for the core delegates, their data.
     *
     * <p>CCL assigns a Reward redeemer's index by position in {@code body.withdrawals}, which it
     * sorted by credential hash alone. The ledger orders withdrawals with every script credential
     * before every key credential, then bytewise — so whenever a transaction mixes the two kinds,
     * CCL's pointer names the wrong withdrawal. Re-indexing against {@link LedgerOrdering}
     * corrects it.</p>
     */
    private void resolveRewardRedeemers(Transaction txn, int paramsIdx) {
        List<Withdrawal> asBuilt = txn.getBody().getWithdrawals();
        if (asBuilt == null || asBuilt.isEmpty()) return;
        List<Withdrawal> ledgerOrder = LedgerOrdering.sortedWithdrawals(txn);
        String transferReward = deployment.transferRewardAddress();
        String thirdPartyReward = deployment.thirdPartyRewardAddress();
        String unfrackingReward = deployment.unfrackingRewardAddress();

        for (Redeemer redeemer : txn.getWitnessSet().getRedeemers()) {
            if (redeemer.getTag() != RedeemerTag.Reward) continue;

            int asBuiltIdx = redeemer.getIndex().intValue();
            if (asBuiltIdx < 0 || asBuiltIdx >= asBuilt.size()) continue;
            Withdrawal withdrawal = asBuilt.get(asBuiltIdx);

            redeemer.setIndex(ledgerOrder.indexOf(withdrawal));

            if (transferReward.equals(withdrawal.getRewardAddress())) {
                redeemer.setData(Cip113Redeemers.transfer(paramsIdx, buildProofs(txn)));
            } else if (thirdPartyReward.equals(withdrawal.getRewardAddress())) {
                redeemer.setData(Cip113Redeemers.thirdParty(
                        paramsIdx, actedNodeIndex(txn), resolveOutputsStartIdx(txn)));
            } else if (unfrackingReward.equals(withdrawal.getRewardAddress())) {
                redeemer.setData(Cip113Redeemers.unfracking(
                        paramsIdx, actedNodeIndex(txn), resolveOutputsStartIdx(txn)));
            }
        }
    }

    /** The single acted-on registry node of a third-party or unfracking transaction. */
    private int actedNodeIndex(Transaction txn) {
        RegistryLookup.RegistryNodeUtxo node = referencedNodes.values().iterator().next();
        return LedgerOrdering.indexOfReferenceInput(txn,
                node.getUtxo().getTxHash(), node.getUtxo().getOutputIndex());
    }

    /**
     * Where the paired continuing outputs begin.
     *
     * <p>Found from the finished transaction rather than tracked as outputs were declared: the
     * builder fixes output order later than declaration, so a recorded position would be a guess.
     * A seizure locates them by address, because the holder's wallet appears only as continuing
     * outputs. An unfracking cannot: the regrouped tokens land at the same wallet, so its
     * continuing outputs are the ones that carry no acted-policy asset.</p>
     */
    private int resolveOutputsStartIdx(Transaction txn) {
        List<TransactionOutput> outputs = txn.getBody().getOutputs();
        outputsStartIdx = dispatch == Dispatch.UNFRACKING
                ? firstContiguousRunWithoutPolicy(outputs, pairedOutputAddress, pairedOutputCount, unfrackPolicy)
                : firstContiguousRun(outputs, pairedOutputAddress, pairedOutputCount);
        return outputsStartIdx;
    }

    /** A seizure's continuing outputs: the run of outputs at the holder's wallet. */
    static int firstContiguousRun(List<TransactionOutput> outputs, String address, int count) {
        return firstContiguousRun(outputs, address, count,
                output -> address.equals(output.getAddress()));
    }

    /**
     * An unfracking's continuing outputs: the run of outputs at the owner's wallet that carry no
     * acted-policy asset — the regrouped output sits at the same wallet and must be skipped.
     */
    static int firstContiguousRunWithoutPolicy(List<TransactionOutput> outputs, String address,
                                               int count, String policyId) {
        return firstContiguousRun(outputs, address, count,
                output -> address.equals(output.getAddress()) && !carriesPolicy(output, policyId));
    }

    /**
     * Where a run of {@code count} consecutive continuing outputs begins.
     *
     * <p>Contiguity is the whole point: the delegate walks the outputs from
     * {@code outputs_start_idx} and pairs each acted-on input against the next one, so anything
     * interleaved makes it pair an input against an output that is not its continuation. Failing
     * here names that; letting it through produces a validator rejection with no detail.</p>
     */
    static int firstContiguousRun(List<TransactionOutput> outputs, String address, int count,
                                  Predicate<TransactionOutput> continuing) {
        for (int i = 0; i < outputs.size(); i++) {
            if (!continuing.test(outputs.get(i))) continue;

            for (int j = 0; j < count; j++) {
                int at = i + j;
                if (at >= outputs.size() || !continuing.test(outputs.get(at))) {
                    throw new Cip113Exception("The continuing outputs of this action are not"
                            + " contiguous: expected " + count + " outputs at " + address
                            + " starting at index " + i + ", but index " + at + " is not one."
                            + " The delegate pairs inputs to outputs by position, so it would pair"
                            + " against the wrong one. Outputs at one address must not be merged"
                            + " (mergeOutputs must stay off for programmable-token builds).");
                }
            }
            return i;
        }
        throw new Cip113Exception("No continuing output at " + address + ", so this action has no"
                + " outputs to pair its inputs against.");
    }

    private static boolean carriesPolicy(TransactionOutput output, String policyId) {
        if (output.getValue() == null || output.getValue().getMultiAssets() == null) return false;
        return output.getValue().getMultiAssets().stream()
                .anyMatch(ma -> ma.getPolicyId().equalsIgnoreCase(policyId));
    }

    /** Only the base-script inputs get a CIP-113 dispatch redeemer; other script inputs keep theirs. */
    private void resolveSpendRedeemers(Transaction txn, int paramsIdx) {
        if (plbInputRefs.isEmpty()) return;

        // Which delegate authorises these spends decides both the arm and which withdrawal the
        // index has to point at. programmable_logic_base compares the witnessed withdrawal against
        // the credential the arm names, so naming one and pointing at the other fails the equality.
        PlutusData dispatchData;
        switch (dispatch) {
            case THIRD_PARTY:
                dispatchData = Cip113Redeemers.spendViaThirdParty(paramsIdx,
                        LedgerOrdering.indexOfWithdrawal(txn, deployment.thirdPartyCredential()));
                break;
            case UNFRACKING:
                dispatchData = Cip113Redeemers.spendViaUnfracking(paramsIdx,
                        LedgerOrdering.indexOfWithdrawal(txn, deployment.unfrackingCredential()));
                break;
            default:
                dispatchData = Cip113Redeemers.spendViaTransfer(paramsIdx,
                        LedgerOrdering.indexOfWithdrawal(txn, deployment.transferCredential()));
        }
        List<TransactionInput> sortedInputs = LedgerOrdering.sortedInputs(txn);

        for (Redeemer redeemer : txn.getWitnessSet().getRedeemers()) {
            if (redeemer.getTag() != RedeemerTag.Spend) continue;
            int idx = redeemer.getIndex().intValue();
            if (idx < 0 || idx >= sortedInputs.size()) continue;
            TransactionInput input = sortedInputs.get(idx);
            if (plbInputRefs.contains(ref(input.getTransactionId(), input.getIndex()))) {
                redeemer.setData(dispatchData);
            }
        }
    }

    /**
     * Point each mint proof at its own registry node.
     *
     * <p>Scoped by policy: a Mint redeemer's index is its policy's position in the sorted mint
     * map, so a transaction minting both a registry NFT and a programmable token keeps each
     * redeemer intact.</p>
     */
    private void resolveMintRedeemers(Transaction txn) {
        if (mintProofNodes.isEmpty() && mintProofFromOutput.isEmpty()) return;

        List<String> mintedPolicies = new ArrayList<>();
        if (txn.getBody().getMint() != null) {
            txn.getBody().getMint().forEach(ma -> mintedPolicies.add(ma.getPolicyId().toLowerCase()));
        }
        mintedPolicies.sort(PolicyOrdering.COMPARATOR);

        for (Map.Entry<String, RegistryLookup.RegistryNodeUtxo> entry : mintProofNodes.entrySet()) {
            int policyIdx = mintedPolicies.indexOf(entry.getKey());
            if (policyIdx < 0) continue;

            int nodeIdx = LedgerOrdering.indexOfReferenceInput(txn,
                    entry.getValue().getUtxo().getTxHash(), entry.getValue().getUtxo().getOutputIndex());

            setMintRedeemer(txn, policyIdx, Cip113Redeemers.mintRefInput(nodeIdx));
        }

        // Policies whose node is being CREATED by this transaction: the node is an output, not a
        // reference input, so issuance_mint reads it through OutputIndex instead.
        for (Map.Entry<String, String> entry : mintProofFromOutput.entrySet()) {
            int policyIdx = mintedPolicies.indexOf(entry.getKey());
            if (policyIdx < 0) continue;

            int outputIdx = indexOfNodeOutput(txn, entry.getValue(), entry.getKey());
            setMintRedeemer(txn, policyIdx, Cip113Redeemers.mintOutputIndex(outputIdx));
        }
    }

    private static void setMintRedeemer(Transaction txn, int policyIdx, PlutusData data) {
        for (Redeemer redeemer : txn.getWitnessSet().getRedeemers()) {
            if (redeemer.getTag() == RedeemerTag.Mint
                    && redeemer.getIndex().intValue() == policyIdx) {
                redeemer.setData(data);
            }
        }
    }

    /**
     * Where the registry node for {@code policyId} sits in the outputs.
     *
     * <p>Found rather than tracked: the node's NFT is named after the policy id, so the output is
     * identifiable from the finished transaction — the same discipline every other index in this
     * class follows.</p>
     */
    static int indexOfNodeOutput(Transaction txn, String registryNodeCs, String policyId) {
        List<TransactionOutput> outputs = txn.getBody().getOutputs();
        for (int i = 0; i < outputs.size(); i++) {
            for (MultiAsset ma : outputs.get(i).getValue().getMultiAssets()) {
                if (!ma.getPolicyId().equalsIgnoreCase(registryNodeCs)) continue;
                for (Asset asset : ma.getAssets()) {
                    if (HexUtil.encodeHexString(asset.getNameAsBytes()).equalsIgnoreCase(policyId)) {
                        return i;
                    }
                }
            }
        }
        throw new Cip113Exception("No output carries the registry-node NFT " + registryNodeCs
                + " named " + policyId + ", so issuance_mint's OutputIndex proof cannot be"
                + " resolved. A mint that rides along with its own registration must emit that"
                + " node in the same transaction.");
    }

    /**
     * One proof per distinct non-lovelace policy across the base-script inputs, ascending
     * unsigned-bytewise — the order the validator walks them.
     */
    private List<PlutusData> buildProofs(Transaction txn) {
        List<String> policies = new ArrayList<>();
        for (Utxo utxo : plbInputs) {
            for (Amount amount : utxo.getAmount()) {
                String policy = policyOf(amount);
                if (policy != null && !policies.contains(policy)) policies.add(policy);
            }
        }
        policies.sort(PolicyOrdering.COMPARATOR);

        List<PlutusData> proofs = new ArrayList<>();
        for (String policy : policies) {
            RegistryLookup.RegistryNodeUtxo node = referencedNodes.get(policy);
            if (node == null) {
                node = coveringProofNodes.get(policy);
                if (node == null) throw new Cip113Exception("No prepared registry proof for"
                        + " co-resident policy " + policy + ". Proof inputs must be resolved before"
                        + " transaction intentions are applied.");
                proofs.add(Cip113Redeemers.tokenDoesNotExist(LedgerOrdering.indexOfReferenceInput(
                        txn, node.getUtxo().getTxHash(), node.getUtxo().getOutputIndex())));
            } else {
                proofs.add(Cip113Redeemers.tokenExists(LedgerOrdering.indexOfReferenceInput(
                        txn, node.getUtxo().getTxHash(), node.getUtxo().getOutputIndex())));
            }
        }
        return proofs;
    }

    /** Resolve every co-resident policy proof before intention application fixes input indexes. */
    private void prepareIncidentalPolicyProofs(List<Utxo> selected) {
        Set<String> policies = new LinkedHashSet<>();
        for (Utxo utxo : selected) {
            for (Amount amount : utxo.getAmount()) {
                String policy = policyOf(amount);
                if (policy != null) policies.add(policy);
            }
        }
        for (String policy : policies) {
            if (referencedNodes.containsKey(policy) || coveringProofNodes.containsKey(policy)) continue;
            if (registry.byPolicy(policy).isPresent()) {
                throw new Cip113Exception("A selected base-script UTxO also holds registered policy "
                        + policy + ". Its transfer authorization must be included in the same"
                        + " aggregated programmable-token operation.");
            }
            RegistryLookup.RegistryNodeUtxo covering = registry.coveringNode(policy);
            coveringProofNodes.put(policy, covering);
            readFrom(covering.getUtxo());
        }
    }

    /**
     * The base script's owner check reads {@code extra_signatories}, so the owner's key hash has
     * to be declared as a required signer or {@code authorised_stake_cred} fails.
     *
     * <p>{@code Tx} exposes no required-signers API — it lives on {@code QuickTxBuilder.TxContext}
     * — so this sets it on the body. Doing it before balancing means fee calculation accounts
     * for it.</p>
     */
    private void declareOwnerAsRequiredSigner(Transaction txn) {
        if (owner == null) return;
        AddressProvider.getPaymentCredentialHash(owner).ifPresent(hash -> {
            List<byte[]> signers = txn.getBody().getRequiredSigners();
            if (signers == null) {
                signers = new ArrayList<>();
                txn.getBody().setRequiredSigners(signers);
            }
            for (byte[] existing : signers) {
                if (java.util.Arrays.equals(existing, hash)) return;
            }
            signers.add(hash);
        });
    }

    private static String ref(String txHash, int index) {
        return txHash.toLowerCase() + "#" + index;
    }

    // ------------------------------------------------------------------ mint

    /**
     * Build the minting half for one asset of a programmable policy.
     *
     * <p>Where the registry node lives is the only thing that varies: already registered, it is a
     * reference input and the proof is {@code RefInput}; registered by this same transaction, it is
     * an output and the proof is {@code OutputIndex}.</p>
     */
    private void recordMint(String policyId, Asset asset, String receiver,
                            PlutusData issuanceRedeemer, PlutusData inlineDatum) {
        requireCoordinationUtxo("a mint");
        if (issuanceTemplateUtxo == null) {
            throw new Cip113Exception("The issuance template is required to assemble the token's"
                    + " issuance script, but the deployment has none resolved.");
        }

        RegistryLookup.RegistryNodeUtxo node = registry.byPolicy(policyId).orElse(null);
        boolean registeringNow = node == null && policyId.equalsIgnoreCase(registeredPolicyId);
        if (node == null && !registeringNow) {
            throw new Cip113Exception("Policy " + policyId + " is not registered, so it cannot"
                    + " be minted. Register it first — either in an earlier transaction, or in"
                    + " this one before the mint.");
        }

        Credential mintingLogic = registeringNow
                ? registeringSpec.getMintingLogicScript()
                : node.getDatum().getMintingLogicScript();
        String globalStateCs = registeringNow
                ? registeringSpec.getGlobalStateCs()
                : node.getDatum().getGlobalStateCs();

        PlutusScript issuanceScript = PolicyIdDerivation.issuanceScript(
                readIssuanceTemplate(), mintingLogic);

        String derived = scriptHashOf(issuanceScript);
        if (!derived.equalsIgnoreCase(policyId)) {
            throw new Cip113Exception("Assembled issuance script hashes to " + derived
                    + " but the policy is " + policyId
                    + ". The registry node's minting_logic_script does not match this template.");
        }

        // The node proves the policy is registered; its index is resolved in preTxEvaluation.
        // A node created by this transaction is already an output — referencing it too would be
        // rejected as a non-disjoint reference input.
        if (registeringNow) {
            mintProofFromOutput.put(policyId.toLowerCase(), deployment.getRegistryNodeCs());
        } else {
            readFrom(node.getUtxo());
            mintProofNodes.put(policyId.toLowerCase(), node);
        }

        // Minted tokens must land at a smart wallet in seizable shape: base-script payment
        // credential, inline stake credential, no datum hash, no reference script. A null receiver
        // means the sender.
        Address target = receiver == null ? owner : new Address(receiver);
        if (target == null) {
            throw new Cip113Exception("Minting needs a receiver, or a sender to default to.");
        }
        Address destination = SmartWalletAddress.isSmartWallet(deployment, target)
                ? target
                : SmartWalletAddress.ofPaymentCredential(deployment, target);

        attachMintValidator(issuanceScript);
        mintAsset(issuanceScript, List.of(asset), Cip113Redeemers.mintRefInput(0),
                destination.toBech32(), inlineDatum);

        addGlobalStateReference(policyId, globalStateCs);

        // The substandard's issuance logic must run.
        invokeLogicScript(mintingLogic, issuanceRedeemer, "minting logic");

        // Funding is what makes the redeemer indices resolvable: QuickTxBuilder._build() skips
        // preTxEvaluation while any output still has a negative coin, and preTxEvaluation is where
        // the mint proof's index is written.
        declaredOutputLovelace = declaredOutputLovelace.add(
                minAdaFor(destination, List.of(Amount.builder()
                        .unit(policyId + HexUtil.encodeHexString(asset.getNameAsBytes()))
                        .quantity(asset.getValue())
                        .build()), inlineDatum));
        fundFromFeePayer();
    }

    private static String scriptHashOf(PlutusScript script) {
        try {
            return script.getPolicyId();
        } catch (Exception e) {
            throw new Cip113Exception("Could not hash the assembled issuance script", e);
        }
    }

    // ------------------------------------------------------------ third party

    /**
     * Act on someone else's holdings — seize, claw back, enforce a freeze.
     *
     * <p>Marks every programmable payment that follows as a third-party action against
     * {@code holder}'s smart wallet rather than a transfer from the sender. Which policies may be
     * acted on, and by whom, is the substandard's business: the token's
     * {@code third_party_transfer_logic_script} is invoked and can refuse.</p>
     *
     * <p>The seized tokens land at the recipient's smart wallet; whatever the holder's spent
     * UTxOs carried beyond the seized amount goes back to them, one continuing output per UTxO
     * with the same address, datum and reference script. That per-pair preservation is the
     * contract's rule, not a convenience: it stops a seizure from also moving ownership, altering
     * metadata, or attaching a reference script.</p>
     */
    Cip113TransactionMaterializer thirdPartyFrom(Address holder) {
        if (holder == null) {
            throw new Cip113Exception("thirdPartyFrom(null): a third-party action needs the holder"
                    + " whose smart wallet is being acted on.");
        }
        this.thirdPartyHolder = holder;
        this.dispatch = Dispatch.THIRD_PARTY;
        return this;
    }

    /**
     * Build the seizure half of a third-party action.
     *
     * <p>The layout the {@code third_party} validator demands:</p>
     * <ul>
     *   <li>The destination outputs — already emitted by the payment, which ran before this — sit
     *       ahead of {@code outputs_start_idx} and are where the seized tokens go.</li>
     *   <li>From {@code outputs_start_idx} on, one continuing output per acted-on input, walked in
     *       the ledger's input order rather than declaration order, because the validator pairs
     *       them positionally against {@code self.inputs}.</li>
     *   <li>Each pair keeps address, datum and reference script byte-identical and carries at
     *       least the input's lovelace; every policy other than the acted-on one is passed through
     *       untouched.</li>
     * </ul>
     */
    private void materialiseThirdParty(String policyId, RegistryLookup.RegistryNodeUtxo node,
                                       Map<String, BigInteger> seizedByUnit) {
        if (!materialised.isEmpty()) {
            throw new Cip113Exception("A CIP-113 third-party transaction acts on exactly one"
                    + " policy; " + materialised + " is already acted on. The third_party"
                    + " redeemer names a single registry node, so split " + policyId
                    + " into a separate transaction.");
        }
        Address holderWallet = SmartWalletAddress.isSmartWallet(deployment, thirdPartyHolder)
                ? thirdPartyHolder
                : SmartWalletAddress.ofPaymentCredential(deployment, thirdPartyHolder);

        rejectSeizingToSelf(holderWallet);
        requireScripts("a third-party action");

        List<Utxo> selected = selectInputs(policyId, seizedByUnit, holderWallet);
        if (selected.isEmpty()) {
            throw new Cip113Exception("No base-script UTxOs holding policy " + policyId
                    + " found at " + holderWallet.toBech32() + ", so there is nothing to seize."
                    + " A third-party action acts on the holder's smart wallet, not the sender's.");
        }

        // The validator walks self.inputs, which the ledger sorts by (txId, index) — so the paired
        // outputs have to be emitted in that order, not the order coin selection happened to pick.
        selected.sort(Comparator.comparing(Utxo::getTxHash)
                .thenComparingInt(Utxo::getOutputIndex));

        attachSpending(scripts.programmableLogicBase());
        attachReward(scripts.thirdPartyDelegate());

        PlutusData spendPlaceholder = Cip113Redeemers.spendViaThirdParty(0, 0);
        collectFrom(selected, spendPlaceholder);
        plbInputs.addAll(selected);
        selected.forEach(u -> plbInputRefs.add(ref(u.getTxHash(), u.getOutputIndex())));

        readFrom(node.getUtxo());
        referencedNodes.put(policyId, node);
        prepareIncidentalPolicyProofs(selected);
        addGlobalStateReference(policyId, node.getDatum().getGlobalStateCs());

        withdraw(deployment.thirdPartyRewardAddress(), BigInteger.ZERO,
                Cip113Redeemers.thirdParty(0, 0, 0));

        invokeLogicScript(node.getDatum().getThirdPartyTransferLogicScript(),
                substandardRedeemers.get(policyId), "third-party logic");

        emitPairedOutputs(policyId, selected, holderWallet, seizedByUnit);

        fundFromFeePayer();
    }

    /**
     * Refuse a seizure that pays back into the wallet it seized from.
     *
     * <p>The paired continuing outputs are located by address, so a destination at the same smart
     * wallet is indistinguishable from them — and the seizure would be a no-op regardless.</p>
     */
    private void rejectSeizingToSelf(Address holderWallet) {
        String wallet = holderWallet.toBech32();
        for (List<PendingPayment> payments : pending.values()) {
            for (PendingPayment payment : payments) {
                if (payment.destination != null && wallet.equals(payment.destination.toBech32())) {
                    throw new Cip113Exception("A third-party action cannot send the seized tokens"
                            + " back to the same smart wallet it seized them from — there would be"
                            + " nothing to distinguish the destination output from the holder's"
                            + " own continuing output, and the seizure would be a no-op.");
                }
            }
        }
    }

    /**
     * One continuing output per acted-on input, each the input minus its share of the seizure.
     *
     * <p>The seizure is drawn from the inputs in order until it is satisfied, so a partial seizure
     * empties the first UTxOs rather than shaving every one of them.</p>
     */
    private void emitPairedOutputs(String policyId, List<Utxo> selected, Address holderWallet,
                                   Map<String, BigInteger> seizedByUnit) {
        Map<String, BigInteger> remaining = new LinkedHashMap<>(seizedByUnit);
        pairedOutputAddress = holderWallet.toBech32();

        for (Utxo input : selected) {
            List<Amount> kept = new ArrayList<>();
            for (Amount held : input.getAmount()) {
                BigInteger owed = remaining.getOrDefault(held.getUnit(), BigInteger.ZERO);
                if (LOVELACE.equals(held.getUnit()) || owed.signum() == 0) {
                    kept.add(held);            // lovelace rides along; nothing seized from this unit
                    continue;
                }
                BigInteger take = owed.min(held.getQuantity());
                remaining.put(held.getUnit(), owed.subtract(take));
                BigInteger left = held.getQuantity().subtract(take);
                if (left.signum() > 0) {
                    kept.add(withQuantity(held, left));
                }
            }
            continuingOutput(input, holderWallet, kept);
        }

        BigInteger short_ = remaining.values().stream().reduce(BigInteger.ZERO, BigInteger::add);
        if (short_.signum() > 0) {
            throw new Cip113Exception("The selected UTxOs at " + holderWallet.toBech32()
                    + " are short by " + short_ + " of what this seizure moves. Coin selection"
                    + " picked inputs that do not cover it, which should not happen — report it.");
        }
    }

    /**
     * The continuing output paired with one base-script input: same address, datum and reference
     * script, carrying {@code kept}.
     */
    private void continuingOutput(Utxo input, Address address, List<Amount> kept) {
        boolean hasInlineDatum = input.getInlineDatum() != null && !input.getInlineDatum().isBlank();
        // Backends report the hash of an inline datum as well, so a hash alone is what marks a
        // datum-hash output — the one shape a continuing output cannot reproduce.
        if (!hasInlineDatum && input.getDataHash() != null && !input.getDataHash().isBlank()) {
            throw new Cip113Exception("Base-script input " + input.getTxHash() + "#"
                    + input.getOutputIndex() + " carries a datum hash. CIP-113 continuing"
                    + " outputs support no datum or an inline datum, so it cannot be preserved.");
        }
        PlutusData inlineDatum = null;
        if (hasInlineDatum) {
            try {
                inlineDatum = PlutusData.deserialize(HexUtil.decodeHexString(input.getInlineDatum()));
            } catch (Exception e) {
                throw new Cip113Exception("Cannot decode inline datum on base-script input "
                        + input.getTxHash() + "#" + input.getOutputIndex(), e);
            }
        }
        PlutusScript referenceScript = null;
        if (input.getReferenceScriptHash() != null && !input.getReferenceScriptHash().isBlank()) {
            if (scripts == null) throw new Cip113Exception("Cannot preserve reference script "
                    + input.getReferenceScriptHash() + " without a script resolver");
            referenceScript = scripts.getScript(input.getReferenceScriptHash())
                    .orElseThrow(() -> new Cip113Exception("Cannot resolve reference script "
                            + input.getReferenceScriptHash() + " for continuing output"));
        }
        super.payToAddress(address.toBech32(), kept, null, inlineDatum, referenceScript, null);
        pairedOutputCount++;
    }

    static Amount withQuantity(Amount amount, BigInteger quantity) {
        return Amount.builder().unit(amount.getUnit()).quantity(quantity).build();
    }

    // ------------------------------------------------------------- unfracking

    /**
     * Regroup one policy out of the owner's shared ("fracked") smart-wallet UTxOs.
     *
     * <p>The layout the {@code unfracking} validator demands, and why each part is where it is:</p>
     * <ul>
     *   <li>Every base-script input carries the owner's full smart-wallet address <b>and</b> the
     *       acted policy, so only UTxOs holding that policy together with another asset are
     *       spent. A UTxO holding the policy alone needs no restructuring and would only add a
     *       base-script spend.</li>
     *   <li>From {@code outputs_start_idx} on, one continuing output per input in the ledger's
     *       input order, byte-identical in address, datum, reference script and every other
     *       asset, with the acted policy stripped entirely and its lovelace kept.</li>
     *   <li>One destination output at the same smart wallet carrying every acted-policy token, so
     *       the conservation equality holds against the inputs.</li>
     *   <li>The acted policy's {@code unfracking_logic_script} hook withdraws zero with the
     *       caller's authorization; the empty-verification-key sentinel means the issuer forbade
     *       unfracking, and the ledger could not carry that withdrawal anyway.</li>
     *   <li>Nothing is minted or burned, so an unfracking is its own transaction.</li>
     * </ul>
     */
    void recordUnfrackForExtension(String policyId, PlutusData authorization) {
        String key = policyId.toLowerCase();
        if (!plbInputs.isEmpty() || !pending.isEmpty() || !materialised.isEmpty()) {
            throw new Cip113Exception("A CIP-113 unfracking must be its own transaction: the"
                    + " validator requires an empty mint and pairs every base-script input with a"
                    + " continuing output, so it cannot be combined with other programmable"
                    + " operations.");
        }
        if (owner == null) throw new Cip113Exception("Unfracking needs the owner — call from(...)");
        requireCoordinationUtxo("an unfracking");
        requireScripts("an unfracking");

        RegistryLookup.RegistryNodeUtxo node = registry.byPolicy(key)
                .orElseThrow(() -> new Cip113Exception("Policy " + key + " is not registered"));
        Credential hook = node.getDatum().getUnfrackingLogicScript();
        if (RegistryNodeSpec.isUnfrackingForbidden(hook)) {
            throw new Cip113Exception("Unfracking is forbidden for policy " + key + ": its registry"
                    + " node's unfracking_logic_script is the empty-verification-key sentinel, so"
                    + " no hook can authorise a restructuring. The issuer has to update the registry"
                    + " node with a script hook before holders can unfrack this token.");
        }

        dispatch = Dispatch.UNFRACKING;
        unfrackPolicy = key;

        Address wallet = smartWallet();
        List<Utxo> selected = frackedUtxos(wallet, key);
        if (selected.isEmpty()) {
            throw new Cip113Exception("Nothing to unfrack: no UTxO at " + wallet.toBech32()
                    + " holds policy " + key + " together with another asset. Unfracking moves one"
                    + " policy out of shared UTxOs; a UTxO already holding only that policy needs no"
                    + " restructuring.");
        }
        // The validator walks self.inputs in the ledger's (txId, index) order and pairs each
        // base-script input with the next continuing output.
        selected.sort(Comparator.comparing(Utxo::getTxHash)
                .thenComparingInt(Utxo::getOutputIndex));

        attachSpending(scripts.programmableLogicBase());
        attachReward(scripts.unfrackingDelegate());

        collectFrom(selected, Cip113Redeemers.spendViaUnfracking(0, 0));
        plbInputs.addAll(selected);
        selected.forEach(u -> plbInputRefs.add(ref(u.getTxHash(), u.getOutputIndex())));

        readFrom(node.getUtxo());
        referencedNodes.put(key, node);
        // No registry proofs for co-resident policies: the unfracking validator preserves them
        // byte-for-byte in the paired outputs and never reads their nodes.
        addGlobalStateReference(key, node.getDatum().getGlobalStateCs());

        withdraw(deployment.unfrackingRewardAddress(), BigInteger.ZERO,
                Cip113Redeemers.unfracking(0, 0, 0));

        invokeLogicScript(hook, authorization, "unfracking logic");

        emitUnfrackOutputs(key, selected, wallet);
        fundFromFeePayer();
        materialised.add(key);
    }

    /** The owner's smart-wallet UTxOs that hold {@code policyId} alongside another asset. */
    private List<Utxo> frackedUtxos(Address wallet, String policyId) {
        List<Utxo> fracked = new ArrayList<>();
        for (Utxo utxo : utxoSupplier.getAll(wallet.toBech32())) {
            if (externallyReservedInputRefs.contains(ref(utxo.getTxHash(), utxo.getOutputIndex())))
                continue;
            boolean holdsPolicy = false;
            boolean holdsOther = false;
            for (Amount amount : utxo.getAmount()) {
                String policy = policyOf(amount);
                if (policy == null) continue;
                if (policyId.equals(policy)) holdsPolicy = true;
                else holdsOther = true;
            }
            if (holdsPolicy && holdsOther) fracked.add(utxo);
        }
        return fracked;
    }

    /**
     * The continuing outputs (declared first, in input order) and then the single destination
     * output carrying every acted-policy token. Declaration order is what puts the paired run
     * ahead of the destination; either order satisfies the validator, but the run must stay
     * contiguous.
     */
    private void emitUnfrackOutputs(String policyId, List<Utxo> selected, Address wallet) {
        pairedOutputAddress = wallet.toBech32();
        Map<String, BigInteger> regrouped = new LinkedHashMap<>();

        for (Utxo input : selected) {
            List<Amount> kept = new ArrayList<>();
            for (Amount held : input.getAmount()) {
                if (policyId.equals(policyOf(held))) {
                    regrouped.merge(held.getUnit(), held.getQuantity(), BigInteger::add);
                } else {
                    kept.add(held);            // lovelace and every other policy stay put
                }
            }
            continuingOutput(input, wallet, kept);
        }

        List<Amount> acted = new ArrayList<>();
        regrouped.forEach((unit, quantity) ->
                acted.add(Amount.builder().unit(unit).quantity(quantity).build()));
        BigInteger minAda = minAdaFor(wallet, acted, null);
        declaredOutputLovelace = declaredOutputLovelace.add(minAda);
        List<Amount> destination = new ArrayList<>();
        destination.add(Amount.lovelace(minAda));
        destination.addAll(acted);
        super.payToAddress(wallet.toBech32(), destination);
        log.debug("Regrouping {} into one output at {}", acted, wallet.toBech32());
    }

    // ------------------------------------------------------------- registry

    /**
     * Register a new programmable token: insert its node into the registry linked list.
     *
     * <p>The transaction shape is dictated by {@code registry_mint} (RegistryInsert) and
     * {@code registry_spend}, and every part of it is derived:</p>
     * <ul>
     *   <li>the policy id, by hashing the issuance template against the issuance credential;</li>
     *   <li>the covering node, by walking the registry;</li>
     *   <li>two node outputs — the new node, and the covering node re-emitted with its
     *       {@code next} re-pointed — both at the registry address, inline datums, no reference
     *       script, each carrying exactly one node NFT named after its own key;</li>
     *   <li>exactly one minted node NFT whose asset name is the new policy id;</li>
     *   <li>the coordination UTxO and the issuance template as reference inputs;</li>
     *   <li>the issuance credential's withdraw-zero, required even when no first mint occurs.</li>
     * </ul>
     *
     * @return the derived policy id is available afterwards from {@link #registeredPolicyId()}
     */
    Cip113TransactionMaterializer registerToken(RegistryNodeSpec spec, PlutusData issuanceRedeemer) {
        spec.validate();
        rejectUnsupportedKeyCredentials("Registering a token", spec.getTransferLogicScript(),
                spec.getThirdPartyTransferLogicScript(), spec.getUnfrackingLogicScript());
        requireCoordinationUtxo("a registration");
        if (issuanceTemplateUtxo == null) {
            throw new Cip113Exception("The issuance-template UTxO is required — the policy id is"
                    + " derived from its datum and registry_mint re-checks it — but the deployment"
                    + " has none resolved.");
        }
        requireScripts("a registration");

        IssuanceCborHex template = readIssuanceTemplate();
        String policyId = PolicyIdDerivation.derive(template, spec.getMintingLogicScript());
        this.registeredPolicyId = policyId;
        this.registeringSpec = spec;

        RegistryLookup.RegistryNodeUtxo covering = registry.coveringNode(policyId);
        RegistryNode coveringNode = covering.getDatum();

        RegistryNode newNode = RegistryNode.builder()
                .key(policyId)
                .next(coveringNode.getNext())
                .mintingLogicScript(spec.getMintingLogicScript())
                .transferLogicScript(spec.getTransferLogicScript())
                .thirdPartyTransferLogicScript(spec.getThirdPartyTransferLogicScript())
                .unfrackingLogicScript(spec.getUnfrackingLogicScript())
                .globalStateCs(spec.getGlobalStateCs() == null ? "" : spec.getGlobalStateCs())
                .build();

        RegistryNode repointedCovering = coveringNode.toBuilder().next(policyId).build();

        String registryAddress = deployment.registryAddress().toBech32();

        // Spend the covering node. registry_spend ignores its redeemer.
        attachSpendingValidator(scripts.registrySpend());
        collectFrom(List.of(covering.getUtxo()), BigIntPlutusData.of(0));

        // Both validators scan the reference inputs for their marker NFT.
        readFrom(issuanceTemplateUtxo);

        // Mint exactly one node NFT, asset name = the new policy id, straight into the new node.
        attachMintValidator(scripts.registryMint());
        mintAsset(scripts.registryMint(),
                List.of(new Asset("0x" + policyId, BigInteger.ONE)),
                Cip113Redeemers.registryInsert(policyId, spec.getMintingLogicScript()),
                registryAddress,
                newNode.toPlutusData());

        // Re-emit the covering node unchanged except for `next`, keeping its own NFT and lovelace.
        payToContract(registryAddress, covering.getUtxo().getAmount(), repointedCovering.toPlutusData());

        // Proof of instance: the substandard authorises its own registration. There is no registry
        // node to read yet, so the credential comes from the spec that is about to become one.
        invokeLogicScript(spec.getMintingLogicScript(), issuanceRedeemer, "minting logic");

        // The new node output needs fresh ADA (the covering node's re-emit reuses the ADA of the
        // input it spends). Its inline datum is large, so this is a deliberate over-estimate —
        // the surplus returns as change.
        declaredOutputLovelace = declaredOutputLovelace.add(REGISTRY_NODE_OUTPUT_ALLOWANCE);
        fundFromFeePayer();

        log.debug("Registering policy {} between {} and {}",
                policyId, coveringNode.getKey(), coveringNode.getNext());
        return this;
    }

    /**
     * The policy id derived during {@link #registerToken}, or null if this is not a registration.
     */
    String registeredPolicyId() {
        return registeredPolicyId;
    }

    private IssuanceCborHex readIssuanceTemplate() {
        try {
            return IssuanceCborHex.fromPlutusData(PlutusData.deserialize(
                    HexUtil.decodeHexString(issuanceTemplateUtxo.getInlineDatum())));
        } catch (Exception e) {
            throw new Cip113Exception("Could not decode the issuance-template datum from "
                    + issuanceTemplateUtxo.getTxHash() + "#" + issuanceTemplateUtxo.getOutputIndex(), e);
        }
    }

    /**
     * Change a registered token's mutable rules, in place.
     *
     * <p>Four of a node's seven fields may change: {@code transfer_logic_script},
     * {@code third_party_transfer_logic_script}, {@code unfracking_logic_script} and
     * {@code global_state_cs}. {@code key}, {@code next} and {@code minting_logic_script} are
     * frozen — the first and last because the policy id is derived from them, {@code next}
     * because it is the registry's linked-list structure, which only an insert may re-point.</p>
     *
     * <p>{@code registry_spend} recognises an update by the <i>absence</i> of a registry-node
     * mint: it requires exactly one continuing output carrying the node NFT at the same address,
     * the frozen fields unchanged, and the node's own minting logic to withdraw-zero. It also
     * forbids minting or burning the node's own token in the same transaction.</p>
     *
     * @param updated the node's desired end state; its {@code key} names which node to update
     * @param issuanceRedeemer the redeemer the token's minting logic expects, since that script
     *                         authorises the change
     */
    Cip113TransactionMaterializer updateRegistryNode(RegistryNode updated, PlutusData issuanceRedeemer) {
        if (updated == null || updated.getKey() == null || updated.getKey().isEmpty()) {
            throw new Cip113Exception("The updated node must carry the key of the node being"
                    + " updated — that is what says which registry node to spend.");
        }
        rejectUnsupportedKeyCredentials("Updating registry node " + updated.getKey(),
                updated.getTransferLogicScript(), updated.getThirdPartyTransferLogicScript(),
                updated.getUnfrackingLogicScript());
        requireCoordinationUtxo("a registry update");
        requireScripts("a registry update");

        String policyId = updated.getKey().toLowerCase();
        RegistryLookup.RegistryNodeUtxo existing = registry.byPolicy(policyId)
                .orElseThrow(() -> new Cip113Exception("Policy " + policyId + " is not"
                        + " registered, so there is no registry node to update."));
        RegistryNode current = existing.getDatum();

        rejectFrozenChange("key", current.getKey(), updated.getKey());
        rejectFrozenChange("next", current.getNext(), updated.getNext());
        rejectFrozenChange("minting_logic_script",
                current.getMintingLogicScript(), updated.getMintingLogicScript());

        // registry_spend requires the node's own token to be absent from the mint field, so an
        // update can never ride along with a mint or a burn of that token.
        if (mintProofNodes.containsKey(policyId) || policyId.equals(registeredPolicyId)) {
            throw new Cip113Exception("Policy " + policyId + " is both minted and node-updated"
                    + " in this transaction. registry_spend forbids that — a node update is a"
                    + " lifecycle action, never an issuance. Split them into two transactions.");
        }

        attachSpendingValidator(scripts.registrySpend());
        // registry_spend ignores its redeemer; the authorisation is the withdraw-zero below.
        collectFrom(List.of(existing.getUtxo()), BigIntPlutusData.of(0));

        // Exactly one continuing output carrying the node NFT, at the same address, keeping
        // its own lovelace and NFT.
        payToContract(existing.getUtxo().getAddress(),
                existing.getUtxo().getAmount(),
                updated.toPlutusData());

        // The node's minting logic authorises the change. registry_spend rejects a key
        // credential outright, so this must resolve to a script.
        invokeLogicScript(current.getMintingLogicScript(), issuanceRedeemer, "minting logic");

        log.debug("Updating registry node {}", policyId);
        return this;
    }

    /** {@code is_field_updated_registry_node} freezes three fields; say which one moved. */
    private static void rejectFrozenChange(String field, Object current, Object proposed) {
        if (current == null ? proposed == null : current.equals(proposed)) return;
        throw new Cip113Exception("A registry node's " + field + " is frozen and this update"
                + " changes it (" + describeFrozen(current) + " -> " + describeFrozen(proposed)
                + "). Only transfer_logic_script, third_party_transfer_logic_script,"
                + " unfracking_logic_script and global_state_cs may change; key and"
                + " minting_logic_script together derive the policy id, and next is the registry's"
                + " linked-list structure that only an insert may re-point.");
    }

    private static String describeFrozen(Object value) {
        if (value == null) return "null";
        if (value instanceof Credential) {
            byte[] bytes = ((Credential) value).getBytes();
            return bytes == null ? "an empty credential" : HexUtil.encodeHexString(bytes);
        }
        return String.valueOf(value);
    }

    /**
     * Refuse to create or update a token whose operational credentials this adapter cannot invoke.
     *
     * <p>CIP-113 permits a verification-key transfer, third-party or unfracking credential, but
     * this adapter only invokes script credentials via withdraw-zero. Registering such a token
     * would succeed and every later operation on it would fail, so it is refused before the
     * registry changes. The empty-key sentinel on {@code unfracking_logic_script} is data — it
     * forbids unfracking — and is not an operation this adapter attempts.</p>
     */
    private static void rejectUnsupportedKeyCredentials(String action, Credential transfer,
                                                        Credential thirdParty, Credential unfracking) {
        rejectKeyCredential(action, "transfer_logic_script", transfer);
        rejectKeyCredential(action, "third_party_transfer_logic_script", thirdParty);
        if (!RegistryNodeSpec.isUnfrackingForbidden(unfracking))
            rejectKeyCredential(action, "unfracking_logic_script", unfracking);
    }

    private static void rejectKeyCredential(String action, String field, Credential credential) {
        if (credential == null || credential.getType() != CredentialType.Key) return;
        throw new Cip113Exception(action + " names a verification-key " + field + " ("
                + HexUtil.encodeHexString(credential.getBytes()) + "). CIP-113 permits key"
                + " credentials, but this adapter can only invoke script credentials, so the token"
                + " could be registered and then never transferred, seized or unfracked through"
                + " this library. Use a script credential.");
    }

    // -------------------------------------------------------------- helpers

    private void requireCoordinationUtxo(String what) {
        if (coordinationUtxo != null) return;
        throw new Cip113Exception("The coordination UTxO is required for " + what + ": every"
                + " CIP-113 redeemer indexes into the reference inputs to find it. The deployment"
                + " has not been resolved.");
    }

    private void requireScripts(String what) {
        if (scripts != null) return;
        throw new Cip113Exception("No script resolver is available for " + what + ", so the"
                + " deployment's applied scripts cannot be fetched by hash.");
    }

    /**
     * {@code assets.is_seizable_output_shape_bounded} caps a holder-created inline datum by the
     * deployment's {@code max_inline_datum_bytes}; fail here rather than in evaluation.
     */
    private void requireInlineDatumWithinBound(PlutusData inlineDatum, String operation) {
        if (inlineDatum == null || deployment.getMaxInlineDatumBytes() <= 0) return;
        int size;
        try {
            size = inlineDatum.serializeToBytes().length;
        } catch (Exception e) {
            throw new Cip113Exception("Cannot serialize the inline datum of this " + operation, e);
        }
        if (size > deployment.getMaxInlineDatumBytes()) {
            throw new Cip113Exception("The inline datum of this " + operation + " is " + size
                    + " bytes, above the deployment's max_inline_datum_bytes of "
                    + deployment.getMaxInlineDatumBytes() + ". Programmable-token outputs must stay"
                    + " seizable, so a third party can reproduce the datum in a continuing output.");
        }
    }

    /**
     * Add a token's global-state UTxO as a reference input, when it declares one.
     *
     * <p>A registry node may name a {@code global_state_cs}; that token's logic scripts then
     * expect the UTxO holding its NFT among the reference inputs. Fails rather than warning: a
     * missing global-state reference surfaces as a script evaluation failure with no indication
     * of what was absent.</p>
     */
    private void addGlobalStateReference(String policyId, String globalStateCs) {
        if (globalStateCs == null || globalStateCs.isEmpty()) return;
        if (!globalStateReferenced.add(globalStateCs.toLowerCase())) return;

        Utxo utxo = globalStateResolver == null ? null : globalStateResolver.apply(globalStateCs);
        if (utxo == null) {
            throw new Cip113Exception("Policy " + policyId + " declares global state under policy "
                    + globalStateCs + ", whose UTxO must be a reference input, but it could not be"
                    + " found.");
        }
        readFrom(utxo);
        log.debug("Added global-state reference input {}#{} for policy {}",
                utxo.getTxHash(), utxo.getOutputIndex(), policyId);
    }

    private String rewardAddressOf(Credential credential) {
        return AddressProvider.getRewardAddress(credential, deployment.getNetwork()).toBech32();
    }

    /**
     * Run a token's own logic script, via the withdraw-zero its credential names.
     *
     * <p>The credential comes from the registry node (or, during registration, from the spec that
     * is about to become one), so which script must run is never the caller's to state. The
     * script <i>body</i> is resolved by hash through {@link DeploymentScripts}.</p>
     *
     * @param role what this credential governs, for the error message when it cannot be resolved
     */
    private void invokeLogicScript(Credential logic, PlutusData redeemer, String role) {
        // A withdraw-zero against a *key* credential is authorised by a signature, and a Reward
        // redeemer against one is not a thing the ledger accepts. Nothing here can express that
        // shape, so say so instead of failing in evaluation with no usable detail.
        if (logic.getType() != CredentialType.Script) {
            throw new Cip113Exception("This token's " + role + " is a verification-key credential ("
                    + HexUtil.encodeHexString(logic.getBytes()) + "). CIP-113 permits that, but"
                    + " this builder can only invoke script credentials: a key credential is"
                    + " authorised by a signature rather than a Plutus redeemer, which needs a"
                    + " redeemer-less withdrawal and an explicit required signer. Not implemented"
                    + " yet — use a script-credential substandard.");
        }

        // The ledger keys withdrawals by reward account, so a second withdrawal against the same
        // credential is not a second entry — it collapses into the first when serialised, while
        // LedgerOrdering still indexes a Java list that contains both. Two policies sharing one
        // logic script is normal, so this is a real case.
        String rewardAddress = rewardAddressOf(logic);
        PlutusData existing = logicWithdrawals.get(rewardAddress);
        if (existing != null) {
            if (!PlutusDataEquality.equals(existing, redeemer)) {
                throw new Cip113Exception("Two policies in this transaction share the " + role
                        + " credential " + HexUtil.encodeHexString(logic.getBytes())
                        + " but were given different redeemers. The ledger has one withdrawal per"
                        + " reward account, so only one redeemer can reach that script — split"
                        + " these into separate transactions, or give both policies the same"
                        + " redeemer.");
            }
            return;                       // same script, same redeemer: already invoked
        }
        logicWithdrawals.put(rewardAddress, redeemer);
        withdraw(rewardAddress, BigInteger.ZERO, redeemer);
        attachLogicScript(logic, role);
    }

    /** Put a logic script in the witness set, once. */
    private void attachLogicScript(Credential logic, String role) {
        String hash = logicScriptHash(logic, role);
        if (!attachedLogicScripts.add(hash)) return;

        PlutusScript script = scripts == null ? null : scripts.getScript(hash).orElse(null);
        if (script == null) {
            throw new Cip113Exception("Could not resolve the " + role + " script " + hash
                    + ", which this token's registry entry requires. A backend only serves a script"
                    + " the chain has already revealed, so a substandard that has never been used"
                    + " has to be supplied: register it once with"
                    + " Cip113ProtocolService.scripts().register(script).");
        }
        attachReward(script);
    }

    /**
     * A script credential's hash, as the lowercase hex the resolvers are keyed by.
     *
     * <p>The bytes can legitimately be absent: a credential decoded from a malformed datum leaves
     * them null, and {@link HexUtil#encodeHexString(byte[])} answers null rather than throwing —
     * so the naive form fails with an NPE that names neither the token nor the role.</p>
     */
    static String logicScriptHash(Credential logic, String role) {
        byte[] bytes = logic.getBytes();
        if (bytes == null || bytes.length == 0) {
            throw new Cip113Exception("The " + role + " credential carries no script hash, so the"
                    + " script it names cannot be resolved. That credential is read from the"
                    + " token's registry node, so either the node's datum is malformed or the"
                    + " RegistryNodeSpec it was registered with named an empty credential.");
        }
        return HexUtil.encodeHexString(bytes).toLowerCase();
    }

    /**
     * The policy of an amount, or null for ADA.
     *
     * <p>A 56-character unit is a policy with an <b>empty asset name</b> — a perfectly valid
     * native asset that still needs a registry proof. Only the literal {@code lovelace} unit is
     * ADA.</p>
     */
    private static String policyOf(Amount amount) {
        String unit = amount.getUnit();
        if (unit == null || LOVELACE.equals(unit) || unit.length() < 56) return null;
        return unit.substring(0, 56).toLowerCase();
    }

    private static int policyCount(Utxo utxo) {
        Set<String> policies = new LinkedHashSet<>();
        for (Amount amount : utxo.getAmount()) {
            String policy = policyOf(amount);
            if (policy != null) policies.add(policy);
        }
        return policies.size();
    }

    private static final class PendingPayment {
        final Address destination;
        final Amount amount;

        PendingPayment(Address destination, Amount amount) {
            this.destination = destination;
            this.amount = amount;
        }
    }
}
