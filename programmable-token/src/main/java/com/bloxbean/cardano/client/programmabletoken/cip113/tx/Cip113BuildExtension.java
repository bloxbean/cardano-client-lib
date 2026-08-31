package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.address.CredentialType;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenCapability;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenTx;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Exception;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProtocolService;
import com.bloxbean.cardano.client.programmabletoken.cip113.PolicyOrdering;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.RegistryNode;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.AbstractTx;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.extension.BalanceFinalization;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionBuildContext;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionIntent;
import com.bloxbean.cardano.client.quicktx.extension.TxBuildExtension;
import com.bloxbean.cardano.client.quicktx.serialization.PlutusDataYamlUtil;
import com.bloxbean.cardano.client.quicktx.serialization.YamlSerializer;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Build-local CIP-113 materializer used by {@link com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Protocol}. */
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
        Map<AbstractTx<?>, List<ExtensionIntent>> work = new LinkedHashMap<>();

        for (AbstractTx<?> abstractTx : context.getTransactions()) {
            List<ExtensionIntent> intents = abstractTx.getIntentions().stream()
                    .filter(ExtensionIntent.class::isInstance)
                    .map(ExtensionIntent.class::cast)
                    .filter(i -> ProgrammableTokenTx.EXTENSION_ID.equals(i.getExtensionId()))
                    .toList();
            if (intents.isEmpty()) continue;
            validateCapabilities(intents);
            work.put(abstractTx, intents);
        }

        // Installing the extension must remain free for ordinary transactions. Capability errors
        // also fail before the first backend lookup.
        if (work.isEmpty()) return;
        active = true;

        List<RegistryLookup.RegistryNodeUtxo> nodes = freshRegistrySnapshot();
        RegistryLookup registry = new SnapshotRegistryLookup(nodes);

        for (Map.Entry<AbstractTx<?>, List<ExtensionIntent>> entry : work.entrySet()) {
            AbstractTx<?> abstractTx = entry.getKey();
            if (!(abstractTx instanceof Tx))
                throw new Cip113Exception("Programmable-token intents require a Tx fragment");

            Tx source = (Tx) abstractTx;
            String owner = source.getSender();
            if (owner == null || owner.isBlank())
                throw new Cip113Exception("Programmable-token operations require from(...) before build");

            materializeSource(context, source, owner, entry.getValue(), registry);
        }
    }

    private void materializeSource(ExtensionBuildContext context, Tx source, String owner,
                                   List<ExtensionIntent> intents, RegistryLookup registry) {
        Map<String, String> namedPolicies = new LinkedHashMap<>();
        List<ExtensionIntent> registrations = intents.stream()
                .filter(intent -> "register".equals(intent.getOperation())).toList();
        List<ExtensionIntent> updates = intents.stream()
                .filter(intent -> "update_registry".equals(intent.getOperation())).toList();
        boolean hasThirdParty = intents.stream()
                .anyMatch(intent -> "third_party_transfer".equals(intent.getOperation()));

        if (registrations.size() > 1)
            throw new Cip113Exception("CIP-113 supports one token registration per transaction");
        if (updates.size() > 1)
            throw new Cip113Exception("CIP-113 supports one registry update per transaction");
        if (!updates.isEmpty() && intents.size() > updates.size())
            throw new Cip113Exception("A CIP-113 registry update must be its own transaction");
        if (hasThirdParty && intents.stream().anyMatch(intent ->
                !"third_party_transfer".equals(intent.getOperation())))
            throw new Cip113Exception("A CIP-113 third-party transfer cannot be mixed with owner operations");

        if (!updates.isEmpty()) {
            Map<String, Object> payload = updates.get(0).getPayload();
            Cip113TransactionMaterializer materializer = newMaterializer(context, owner, registry);
            materializer.updateRegistryNode(registryNode(payload),
                    plutus(payload.get("authorization")));
            finish(context, source, materializer);
            return;
        }

        if (hasThirdParty) {
            materializeThirdParty(context, source, owner, intents, registry);
            return;
        }

        Cip113TransactionMaterializer primary = newMaterializer(context, owner, registry);

        // Registration publishes named policies before any dependent mint, independent of fluent order.
        for (ExtensionIntent intent : registrations) {
            Map<String, Object> payload = intent.getPayload();
            primary.registerToken(registration(payload), plutus(payload.get("registration_redeemer")));
            String policyId = primary.registeredPolicyId();
            String name = string(payload, "name");
            namedPolicies.put(name, policyId);
        }

        // Accumulate all owner operations in one materializer. This is important for CIP-113:
        // the base/coordination scripts and transfer withdrawal are transaction-wide, and using
        // one materializer also gives fee-input selection one coherent reservation set.
        Map<String, List<ExtensionIntent>> ownerActions = new LinkedHashMap<>();
        for (ExtensionIntent intent : intents) {
            if ("transfer".equals(intent.getOperation()) || "burn".equals(intent.getOperation())) {
                ownerActions.computeIfAbsent(policy(intent.getPayload(), namedPolicies), k -> new ArrayList<>())
                        .add(intent);
            }
        }
        for (Map.Entry<String, List<ExtensionIntent>> entry : ownerActions.entrySet()) {
            PlutusData transferRedeemer = null;
            for (ExtensionIntent intent : entry.getValue()) {
                Map<String, Object> payload = intent.getPayload();
                if ("transfer".equals(intent.getOperation())) {
                    primary.recordTransferForExtension(entry.getKey(),
                            string(payload, "receiver"), amount(payload));
                    transferRedeemer = sameRedeemer(transferRedeemer,
                            plutus(payload.get("transfer_redeemer")), entry.getKey(), "transfer");
                } else {
                    PlutusData transfer = plutus(payload.get("transfer_redeemer"));
                    transferRedeemer = sameRedeemer(transferRedeemer, transfer, entry.getKey(), "transfer");
                    PlutusData issuance = plutus(payload.get("issuance_redeemer"));
                    for (Asset asset : assets(payload)) {
                        Asset negative = asset.getValue().signum() > 0
                                ? new Asset("0x" + HexUtil.encodeHexString(asset.getNameAsBytes()),
                                asset.getValue().negate()) : asset;
                        primary.recordBurnForExtension(entry.getKey(), negative, transfer, issuance);
                    }
                }
            }
            primary.withRedeemer(entry.getKey(), transferRedeemer);
        }

        for (ExtensionIntent intent : intents) {
            Map<String, Object> payload = intent.getPayload();
            switch (intent.getOperation()) {
                case "mint": {
                    String policyRef = optionalString(payload.get("policy_ref"));
                    if (!policyRef.isEmpty() && !namedPolicies.containsKey(policyRef))
                        throw new Cip113Exception("Unknown programmable-token policy_ref " + policyRef);
                    String policy = policy(payload, namedPolicies);
                    primary.mintAsset(policy, assets(payload), plutus(payload.get("issuance_redeemer")),
                            string(payload, "receiver"), plutus(payload.get("inline_datum")));
                    break;
                }
                case "unfrack":
                    throw new Cip113Exception("CIP-113 unfrack is not implemented by this adapter");
                default:
                    break;
            }
        }
        finish(context, source, primary);
    }

    private void materializeThirdParty(ExtensionBuildContext context, Tx source, String owner,
                                       List<ExtensionIntent> intents, RegistryLookup registry) {
        Set<String> holders = intents.stream()
                .map(ExtensionIntent::getPayload)
                .map(payload -> string(payload, "holder"))
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (holders.size() != 1)
            throw new Cip113Exception("One CIP-113 transaction can act for only one third-party holder");

        Cip113TransactionMaterializer materializer = newMaterializer(context, owner, registry)
                .thirdPartyFrom(new Address(holders.iterator().next()));
        Map<String, PlutusData> redeemers = new LinkedHashMap<>();
        for (ExtensionIntent intent : intents) {
            Map<String, Object> payload = intent.getPayload();
            Amount amount = amount(payload);
            if (amount.getUnit().length() <= 56)
                throw new Cip113Exception("third_party_transfer requires a native-asset unit");
            String policy = amount.getUnit().substring(0, 56).toLowerCase();
            materializer.recordTransferForExtension(policy, string(payload, "receiver"), amount);
            redeemers.put(policy, sameRedeemer(redeemers.get(policy),
                    plutus(payload.get("third_party_redeemer")), policy, "third-party"));
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

    private void validateCapabilities(List<ExtensionIntent> intents) {
        for (ExtensionIntent intent : intents) {
            ProgrammableTokenCapability capability = switch (intent.getOperation()) {
                case "transfer" -> ProgrammableTokenCapability.TRANSFER;
                case "mint" -> ProgrammableTokenCapability.MINT;
                case "burn" -> ProgrammableTokenCapability.BURN;
                case "third_party_transfer" -> ProgrammableTokenCapability.THIRD_PARTY_TRANSFER;
                case "register" -> ProgrammableTokenCapability.REGISTER;
                case "update_registry" -> ProgrammableTokenCapability.UPDATE_REGISTRY;
                case "unfrack" -> ProgrammableTokenCapability.UNFRACK;
                default -> throw new Cip113Exception("Unknown programmable-token operation "
                        + intent.getOperation());
            };
            if (!capabilities.contains(capability))
                throw new Cip113Exception("Protocol cip-113 does not support operation "
                        + intent.getOperation() + " in this CCL adapter");
        }
    }

    private static String policy(Map<String, Object> payload, Map<String, String> named) {
        Object literal = payload.get("policy_id");
        if (literal != null) return literal.toString().toLowerCase();
        Object unit = payload.get("unit");
        if (unit != null && unit.toString().length() > 56)
            return unit.toString().substring(0, 56).toLowerCase();
        String name = string(payload, "policy_ref");
        String resolved = named.get(name);
        if (resolved == null) throw new Cip113Exception("Unknown programmable-token policy_ref " + name);
        return resolved;
    }

    private static Amount amount(Map<String, Object> payload) {
        return Amount.builder().unit(string(payload, "unit"))
                .quantity(bigInteger(payload.get("quantity"))).build();
    }

    @SuppressWarnings("unchecked")
    private static List<Asset> assets(Map<String, Object> payload) {
        Object value = payload.get("assets");
        if (!(value instanceof List)) throw new Cip113Exception("assets must be a list");
        List<Asset> result = new ArrayList<>();
        for (Object item : (List<Object>) value) {
            Map<String, Object> asset = (Map<String, Object>) item;
            result.add(new Asset("0x" + string(asset, "name"), bigInteger(asset.get("quantity"))));
        }
        return result;
    }

    private static PlutusData plutus(Object value) {
        if (value == null) return null;
        if (value instanceof PlutusData) return (PlutusData) value;
        try {
            JsonNode node = value instanceof JsonNode ? (JsonNode) value
                    : YamlSerializer.getYamlMapper().valueToTree(value);
            return PlutusDataYamlUtil.fromYamlNode(node, Collections.emptyMap());
        } catch (Exception e) {
            throw new Cip113Exception("Invalid structured Plutus data", e);
        }
    }

    private static PlutusData sameRedeemer(PlutusData existing, PlutusData candidate,
                                           String policy, String role) {
        if (existing == null) return candidate;
        if (!existing.equals(candidate)) throw new Cip113Exception("Policy " + policy
                + " declares different " + role + " redeemers in one transaction");
        return existing;
    }

    @SuppressWarnings("unchecked")
    private static RegistryNodeSpec registration(Map<String, Object> payload) {
        Map<String, Object> data = (Map<String, Object>) payload.get("registration");
        if (data == null) throw new Cip113Exception("register requires registration data");
        return RegistryNodeSpec.builder()
                .mintingLogicScript(credential(data.get("minting_logic_script")))
                .transferLogicScript(credential(data.get("transfer_logic_script")))
                .thirdPartyTransferLogicScript(credential(data.get("third_party_transfer_logic_script")))
                .unfrackingLogicScript(credential(data.get("unfracking_logic_script")))
                .globalStateCs(optionalString(data.get("global_state_cs")))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static RegistryNode registryNode(Map<String, Object> payload) {
        Map<String, Object> data = (Map<String, Object>) payload.get("update");
        if (data == null) throw new Cip113Exception("update_registry requires update data");
        return RegistryNode.builder()
                .key(string(payload, "policy_id"))
                .next(string(data, "next"))
                .mintingLogicScript(credential(data.get("minting_logic_script")))
                .transferLogicScript(credential(data.get("transfer_logic_script")))
                .thirdPartyTransferLogicScript(credential(data.get("third_party_transfer_logic_script")))
                .unfrackingLogicScript(credential(data.get("unfracking_logic_script")))
                .globalStateCs(optionalString(data.get("global_state_cs")))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Credential credential(Object value) {
        if (!(value instanceof Map)) throw new Cip113Exception("credential must be a mapping");
        Map<String, Object> data = (Map<String, Object>) value;
        Object hash = data.get("hash");
        if (hash == null) throw new Cip113Exception("credential hash is required");
        byte[] bytes = HexUtil.decodeHexString(hash.toString());
        String type = string(data, "type");
        return "script".equalsIgnoreCase(type) ? Credential.fromScript(bytes) : Credential.fromKey(bytes);
    }

    private static String string(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null || value.toString().isBlank())
            throw new Cip113Exception(key + " is required");
        return value.toString();
    }

    private static String optionalString(Object value) {
        return value == null ? "" : value.toString();
    }

    private static BigInteger bigInteger(Object value) {
        if (value instanceof BigInteger) return (BigInteger) value;
        if (value instanceof Number) return BigInteger.valueOf(((Number) value).longValue());
        return new BigInteger(String.valueOf(value));
    }

    private static final class SnapshotRegistryLookup implements RegistryLookup {
        private final List<RegistryNodeUtxo> nodes;

        private SnapshotRegistryLookup(List<RegistryNodeUtxo> nodes) {
            this.nodes = nodes;
        }

        @Override public Optional<RegistryNodeUtxo> byPolicy(String policyId) {
            return nodes.stream().filter(n -> n.getDatum().getKey().equalsIgnoreCase(policyId)).findFirst();
        }

        @Override public RegistryNodeUtxo coveringNode(String policyId) {
            return nodes.stream().filter(n -> PolicyOrdering.covers(
                            n.getDatum().getKey(), n.getDatum().getNext(), policyId))
                    .findFirst().orElseThrow(() -> new Cip113Exception(
                            "No covering registry node for policy " + policyId));
        }

        @Override public List<RegistryNodeUtxo> all() { return nodes; }
    }
}
