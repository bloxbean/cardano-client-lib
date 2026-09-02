package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenCapability;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenPolicyRef;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenTx;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Exception;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProtocolService;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Registration;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113RegistryUpdate;
import com.bloxbean.cardano.client.programmabletoken.cip113.PolicyOrdering;
import com.bloxbean.cardano.client.programmabletoken.intent.ProgrammableBurnIntent;
import com.bloxbean.cardano.client.programmabletoken.intent.ProgrammableMintIntent;
import com.bloxbean.cardano.client.programmabletoken.intent.ProgrammableRegisterIntent;
import com.bloxbean.cardano.client.programmabletoken.intent.ProgrammableRegistryUpdateIntent;
import com.bloxbean.cardano.client.programmabletoken.intent.ProgrammableThirdPartyTransferIntent;
import com.bloxbean.cardano.client.programmabletoken.intent.ProgrammableTokenAsset;
import com.bloxbean.cardano.client.programmabletoken.intent.ProgrammableTokenIntent;
import com.bloxbean.cardano.client.programmabletoken.intent.ProgrammableTransferIntent;
import com.bloxbean.cardano.client.programmabletoken.intent.ProgrammableUnfrackIntent;
import com.bloxbean.cardano.client.quicktx.AbstractTx;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.extension.BalanceFinalization;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionBuildContext;
import com.bloxbean.cardano.client.quicktx.extension.TxBuildExtension;
import com.bloxbean.cardano.client.quicktx.intent.PlutusDataValue;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Build-local CIP-113 materializer used by the CIP-113 Programmable Token protocol. */
public final class Cip113BuildExtension implements TxBuildExtension {
    private final Cip113ProtocolService service;
    private final Set<ProgrammableTokenCapability> capabilities;
    private final List<Cip113TransactionMaterializer> materializers = new ArrayList<>();
    private boolean active;

    public Cip113BuildExtension(Cip113ProtocolService service,
                               Set<ProgrammableTokenCapability> capabilities) {
        this.service = service;
        this.capabilities = EnumSet.copyOf(capabilities);
    }

    @Override
    public boolean removeDuplicateScriptWitnesses() {
        return active;
    }

    @Override
    public void prepare(ExtensionBuildContext context) {
        Map<AbstractTx<?>, List<ProgrammableTokenIntent>> work = new LinkedHashMap<>();

        for (AbstractTx<?> transaction : context.getTransactions()) {
            List<ProgrammableTokenIntent> intents = context
                    .extensionIntents(transaction, ProgrammableTokenTx.EXTENSION_ID).stream()
                    .map(intent -> {
                        if (!(intent instanceof ProgrammableTokenIntent))
                            throw new Cip113Exception("Intent " + intent.getClass().getName()
                                    + " claims programmable-token ownership but is not a typed "
                                    + "Programmable Token intent");
                        return (ProgrammableTokenIntent) intent;
                    })
                    .toList();
            if (intents.isEmpty()) continue;
            validateCapabilities(intents);
            work.put(transaction, intents);
        }

        // Installing the extension remains free for ordinary transactions. Capability errors also
        // fail before the first registry lookup.
        if (work.isEmpty()) return;
        active = true;

        RegistryLookup registry = new SnapshotRegistryLookup(freshRegistrySnapshot());
        for (Map.Entry<AbstractTx<?>, List<ProgrammableTokenIntent>> entry : work.entrySet()) {
            if (!(entry.getKey() instanceof Tx))
                throw new Cip113Exception("Programmable-token intents require a Tx fragment");
            Tx source = (Tx) entry.getKey();
            String owner = source.getSender();
            if (owner == null || owner.isBlank())
                throw new Cip113Exception("Programmable-token operations require from(...) before build");
            materializeSource(context, source, owner, entry.getValue(), registry);
        }
    }

    private void materializeSource(ExtensionBuildContext context, Tx source, String owner,
                                   List<ProgrammableTokenIntent> intents, RegistryLookup registry) {
        Map<String, String> namedPolicies = new LinkedHashMap<>();
        List<ProgrammableRegisterIntent> registrations = intents.stream()
                .filter(ProgrammableRegisterIntent.class::isInstance)
                .map(ProgrammableRegisterIntent.class::cast).toList();
        List<ProgrammableRegistryUpdateIntent> updates = intents.stream()
                .filter(ProgrammableRegistryUpdateIntent.class::isInstance)
                .map(ProgrammableRegistryUpdateIntent.class::cast).toList();
        List<ProgrammableThirdPartyTransferIntent> thirdParty = intents.stream()
                .filter(ProgrammableThirdPartyTransferIntent.class::isInstance)
                .map(ProgrammableThirdPartyTransferIntent.class::cast).toList();

        if (registrations.size() > 1)
            throw new Cip113Exception("CIP-113 supports one token registration per transaction");
        if (updates.size() > 1)
            throw new Cip113Exception("CIP-113 supports one registry update per transaction");
        if (!updates.isEmpty() && intents.size() > updates.size())
            throw new Cip113Exception("A CIP-113 registry update must be its own transaction");
        if (!thirdParty.isEmpty() && intents.size() > thirdParty.size())
            throw new Cip113Exception(
                    "A CIP-113 third-party transfer cannot be mixed with owner operations");

        if (!updates.isEmpty()) {
            ProgrammableRegistryUpdateIntent update = updates.get(0);
            Cip113TransactionMaterializer materializer = newMaterializer(context, owner, registry);
            materializer.updateRegistryNode(
                    Cip113RegistryUpdate.toNode(update.getPolicyId(), update.getUpdate()),
                    resolved(update.getAuthorization(), "authorization"));
            finish(context, source, materializer);
            return;
        }

        if (!thirdParty.isEmpty()) {
            materializeThirdParty(context, source, owner, thirdParty, registry);
            return;
        }

        Cip113TransactionMaterializer primary = newMaterializer(context, owner, registry);

        // Registration publishes named policies before dependent mints, independent of fluent order.
        for (ProgrammableRegisterIntent registration : registrations) {
            primary.registerToken(Cip113Registration.toSpec(registration.getRegistration()),
                    resolved(registration.getRegistrationRedeemer(), "registration_redeemer"));
            namedPolicies.put(registration.getName(), primary.registeredPolicyId());
        }

        // Aggregate all owner operations by policy into one transaction-wide materializer.
        Map<String, List<ProgrammableTokenIntent>> ownerActions = new LinkedHashMap<>();
        for (ProgrammableTokenIntent intent : intents) {
            if (intent instanceof ProgrammableTransferIntent) {
                ProgrammableTransferIntent transfer = (ProgrammableTransferIntent) intent;
                ownerActions.computeIfAbsent(policyFromAmount(transfer.getAmount()),
                        ignored -> new ArrayList<>()).add(intent);
            } else if (intent instanceof ProgrammableBurnIntent) {
                ProgrammableBurnIntent burn = (ProgrammableBurnIntent) intent;
                ownerActions.computeIfAbsent(resolvePolicy(burn.getPolicy(), namedPolicies),
                        ignored -> new ArrayList<>()).add(intent);
            }
        }

        for (Map.Entry<String, List<ProgrammableTokenIntent>> entry : ownerActions.entrySet()) {
            PlutusData transferRedeemer = null;
            for (ProgrammableTokenIntent intent : entry.getValue()) {
                if (intent instanceof ProgrammableTransferIntent) {
                    ProgrammableTransferIntent transfer = (ProgrammableTransferIntent) intent;
                    primary.recordTransferForExtension(entry.getKey(), transfer.getReceiver(),
                            transfer.getAmount());
                    transferRedeemer = sameRedeemer(transferRedeemer,
                            resolved(transfer.getTransferRedeemer(), "transfer_redeemer"),
                            entry.getKey(), "transfer");
                } else {
                    ProgrammableBurnIntent burn = (ProgrammableBurnIntent) intent;
                    PlutusData burnTransfer = resolved(
                            burn.getTransferRedeemer(), "transfer_redeemer");
                    PlutusData burnIssuance = resolved(
                            burn.getIssuanceRedeemer(), "issuance_redeemer");
                    transferRedeemer = sameRedeemer(transferRedeemer,
                            burnTransfer, entry.getKey(), "transfer");
                    for (ProgrammableTokenAsset declaredAsset : burn.getAssets()) {
                        Asset asset = declaredAsset.toLedgerAsset();
                        Asset negative = asset.getValue().signum() > 0
                                ? new Asset("0x" + HexUtil.encodeHexString(asset.getNameAsBytes()),
                                asset.getValue().negate()) : asset;
                        primary.recordBurnForExtension(entry.getKey(), negative,
                                burnTransfer, burnIssuance);
                    }
                }
            }
            primary.withRedeemer(entry.getKey(), transferRedeemer);
        }

        for (ProgrammableTokenIntent intent : intents) {
            if (intent instanceof ProgrammableMintIntent) {
                ProgrammableMintIntent mint = (ProgrammableMintIntent) intent;
                String policy = resolvePolicy(mint.getPolicy(), namedPolicies);
                List<Asset> assets = mint.getAssets().stream()
                        .map(ProgrammableTokenAsset::toLedgerAsset)
                        .toList();
                primary.mintAsset(policy, assets,
                        resolved(mint.getIssuanceRedeemer(), "issuance_redeemer"),
                        mint.getReceiver(), resolvedOptional(mint.getInlineDatum(), "inline_datum"));
            } else if (intent instanceof ProgrammableUnfrackIntent) {
                throw new Cip113Exception("CIP-113 unfrack is not implemented by this adapter");
            }
        }
        finish(context, source, primary);
    }

    private void materializeThirdParty(ExtensionBuildContext context, Tx source, String owner,
                                       List<ProgrammableThirdPartyTransferIntent> intents,
                                       RegistryLookup registry) {
        Set<String> holders = intents.stream()
                .map(ProgrammableThirdPartyTransferIntent::getHolder)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (holders.size() != 1)
            throw new Cip113Exception(
                    "One CIP-113 transaction can act for only one third-party holder");

        Cip113TransactionMaterializer materializer = newMaterializer(context, owner, registry)
                .thirdPartyFrom(new Address(holders.iterator().next()));
        Map<String, PlutusData> redeemers = new LinkedHashMap<>();
        for (ProgrammableThirdPartyTransferIntent intent : intents) {
            String policy = policyFromAmount(intent.getAmount());
            materializer.recordTransferForExtension(policy, intent.getReceiver(), intent.getAmount());
            redeemers.put(policy, sameRedeemer(redeemers.get(policy),
                    resolved(intent.getThirdPartyRedeemer(), "third_party_redeemer"),
                    policy, "third-party"));
        }
        redeemers.forEach(materializer::withRedeemer);
        finish(context, source, materializer);
    }

    private Cip113TransactionMaterializer newMaterializer(ExtensionBuildContext context,
                                                          String owner, RegistryLookup registry) {
        return new Cip113TransactionMaterializer(service, context.getUtxoSupplier(), registry)
                .excludeInputs(Set.copyOf(context.getReservedInputs()))
                .from(owner);
    }

    private void finish(ExtensionBuildContext context, Tx source,
                        Cip113TransactionMaterializer materializer) {
        for (Utxo input : materializer.selectedInputs()) {
            if (!context.reserveInput(input.getTxHash(), input.getOutputIndex()))
                throw new Cip113Exception("Programmable-token input selected twice: "
                        + input.getTxHash() + "#" + input.getOutputIndex());
        }
        materializer.getIntentions().forEach(source::addIntention);
        materializers.add(materializer);
    }

    @Override
    public void beforeScriptEvaluation(ExtensionBuildContext context, Transaction transaction) {
        materializers.forEach(materializer -> materializer.finalizeForEvaluation(transaction));
    }

    @Override
    public BalanceFinalization afterBalance(ExtensionBuildContext context, Transaction transaction) {
        boolean changed = false;
        for (Cip113TransactionMaterializer materializer : materializers)
            changed |= materializer.refinalizeIfChanged(transaction);
        return changed ? BalanceFinalization.REFINALIZED : BalanceFinalization.STABLE;
    }

    @Override
    public void verify(ExtensionBuildContext context, Transaction transaction) {
        materializers.forEach(materializer -> materializer.verifyStable(transaction));
    }

    private List<RegistryLookup.RegistryNodeUtxo> freshRegistrySnapshot() {
        RegistryLookup lookup = service.registryLookup();
        lookup.invalidate();
        return List.copyOf(lookup.all());
    }

    private void validateCapabilities(List<ProgrammableTokenIntent> intents) {
        for (ProgrammableTokenIntent intent : intents) {
            ProgrammableTokenCapability capability = capability(intent);
            if (!capabilities.contains(capability))
                throw new Cip113Exception("Protocol cip-113 does not support operation "
                        + intent.getOperation() + " in this CCL adapter");
        }
    }

    private static ProgrammableTokenCapability capability(ProgrammableTokenIntent intent) {
        if (intent instanceof ProgrammableTransferIntent) return ProgrammableTokenCapability.TRANSFER;
        if (intent instanceof ProgrammableMintIntent) return ProgrammableTokenCapability.MINT;
        if (intent instanceof ProgrammableBurnIntent) return ProgrammableTokenCapability.BURN;
        if (intent instanceof ProgrammableThirdPartyTransferIntent)
            return ProgrammableTokenCapability.THIRD_PARTY_TRANSFER;
        if (intent instanceof ProgrammableRegisterIntent) return ProgrammableTokenCapability.REGISTER;
        if (intent instanceof ProgrammableRegistryUpdateIntent)
            return ProgrammableTokenCapability.UPDATE_REGISTRY;
        if (intent instanceof ProgrammableUnfrackIntent) return ProgrammableTokenCapability.UNFRACK;
        throw new Cip113Exception("Unknown programmable-token intent " + intent.getClass().getName());
    }

    private static String resolvePolicy(ProgrammableTokenPolicyRef reference,
                                        Map<String, String> namedPolicies) {
        if (reference.getPolicyId() != null) return reference.getPolicyId().toLowerCase();
        String resolved = namedPolicies.get(reference.getName());
        if (resolved == null)
            throw new Cip113Exception(
                    "Unknown programmable-token policy_ref " + reference.getName());
        return resolved;
    }

    private static String policyFromAmount(Amount amount) {
        if (amount == null || amount.getUnit() == null || amount.getUnit().length() <= 56)
            throw new Cip113Exception("programmable transfer requires a native-asset unit");
        return amount.getUnit().substring(0, 56).toLowerCase();
    }

    private static PlutusData sameRedeemer(PlutusData existing, PlutusData candidate,
                                           String policy, String role) {
        if (existing == null) return candidate;
        if (!PlutusDataEquality.equals(existing, candidate))
            throw new Cip113Exception("Policy " + policy + " declares different " + role
                    + " redeemers in one transaction");
        return existing;
    }

    private static PlutusData resolved(PlutusDataValue value, String fieldName) {
        if (value == null) throw new Cip113Exception(fieldName + " is required");
        return value.requireResolved(fieldName);
    }

    private static PlutusData resolvedOptional(PlutusDataValue value, String fieldName) {
        return value == null ? null : value.requireResolved(fieldName);
    }

    private static final class SnapshotRegistryLookup implements RegistryLookup {
        private final List<RegistryNodeUtxo> nodes;

        private SnapshotRegistryLookup(List<RegistryNodeUtxo> nodes) {
            this.nodes = nodes;
        }

        @Override public Optional<RegistryNodeUtxo> byPolicy(String policyId) {
            return nodes.stream().filter(node ->
                    node.getDatum().getKey().equalsIgnoreCase(policyId)).findFirst();
        }

        @Override public RegistryNodeUtxo coveringNode(String policyId) {
            return nodes.stream().filter(node -> PolicyOrdering.covers(
                            node.getDatum().getKey(), node.getDatum().getNext(), policyId))
                    .findFirst().orElseThrow(() -> new Cip113Exception(
                            "No covering registry node for policy " + policyId));
        }

        @Override public List<RegistryNodeUtxo> all() { return nodes; }
    }
}
