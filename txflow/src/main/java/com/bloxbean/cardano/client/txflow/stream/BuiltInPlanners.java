package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.intent.PaymentIntent;
import com.bloxbean.cardano.client.quicktx.intent.TxIntent;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.ChainingMode;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Built-in {@link TxStreamPlanner} implementations.
 *
 * <p>Both planners are deterministic by construction: every identity is a
 * pure function of the member items' claim keys (via {@link StreamIdentities}
 * / the context's {@link StableIdFactory}), member ordering inside a
 * multi-item flow is sorted by claim key, and lane groups are ordered by
 * canonical identity — never by submission order, window position, or
 * time.</p>
 */
final class BuiltInPlanners {
    /**
     * One single-step flow per item. Flow id, step id, and claim key are
     * identical to the pre-planner (1A/1B) derivations, so execution
     * identities are stable across stream versions.
     */
    static final TxStreamPlanner PER_ITEM = context -> {
        List<PlannedExecution> executions = new ArrayList<>(context.items().size());
        for (TxWorkItem item : context.items()) {
            TxStreamPlanningContext.PlanningSeed seed = context.seed(item.getItemId());
            FlowStep step = seed.enforcedStep;
            TxFlow flow = TxFlow.builder(context.ids().flowId(List.of(seed.claimKey)))
                    .addStep(step)
                    .build();
            executions.add(new PlannedExecution(flow, seed.lane.laneName(), seed.claimKey,
                    List.of(new TxStreamPlannedItem(item.getItemId(), step.getId()))));
        }
        return TxStreamPlan.of(executions);
    };

    /**
     * One flow per lane group within the window (ADR 0004 Decision 2: exactly
     * one lane per execution — a multi-lane window partitions into one flow
     * per lane). Members ride their own steps, ordered by claim key; the flow
     * claim key is derived from the sorted member keys, so the same items in
     * any submission order produce a byte-identical plan. Flow-level dedup
     * only (Decision 3).
     */
    static final TxStreamPlanner PER_WINDOW = context -> planPerWindow(context, null);

    /** Per-window planning with explicitly requested engine pipelining. */
    private static final TxStreamPlanner PER_WINDOW_PIPELINED =
            context -> planPerWindow(context, ChainingMode.PIPELINED);

    /**
     * Returns a per-window planner for the supported planner-local chaining
     * modes. The legacy/default sequential planner deliberately leaves the
     * execution setting absent so existing flow fingerprints and idempotency
     * claims remain byte-for-byte compatible.
     */
    static TxStreamPlanner perWindow(ChainingMode chainingMode) {
        if (chainingMode == null) {
            throw new IllegalArgumentException(
                    "TxStream perWindow chainingMode must be SEQUENTIAL or PIPELINED");
        }
        switch (chainingMode) {
            case SEQUENTIAL:
                return PER_WINDOW;
            case PIPELINED:
                return PER_WINDOW_PIPELINED;
            default:
                throw new IllegalArgumentException(
                        "TxStream perWindow supports only SEQUENTIAL and PIPELINED; "
                                + chainingMode + " is not supported");
        }
    }

    private static TxStreamPlan planPerWindow(TxStreamPlanningContext context,
                                               ChainingMode chainingMode) {
        // Lane groups keyed and ordered by canonical spending identity.
        Map<String, List<TxWorkItem>> groups = new TreeMap<>();
        for (TxWorkItem item : context.items()) {
            TxStreamPlanningContext.PlanningSeed seed = context.seed(item.getItemId());
            groups.computeIfAbsent(seed.lane.canonicalSpendingIdentity(),
                    ignored -> new ArrayList<>()).add(item);
        }
        List<PlannedExecution> executions = new ArrayList<>(groups.size());
        for (List<TxWorkItem> group : groups.values()) {
            // Deterministic member order: sorted by claim key.
            Map<String, TxWorkItem> byClaimKey = new TreeMap<>();
            for (TxWorkItem item : group) {
                byClaimKey.put(context.seed(item.getItemId()).claimKey, item);
            }
            List<String> sortedKeys = new ArrayList<>(byClaimKey.keySet());
            String flowClaimKey = StreamIdentities.windowClaimKey(sortedKeys);
            TxFlow.Builder flowBuilder = TxFlow.builder(context.ids().flowId(sortedKeys));
            if (chainingMode != null) {
                flowBuilder.withChainingMode(chainingMode);
            }
            List<TxStreamPlannedItem> mappings = new ArrayList<>(byClaimKey.size());
            String laneName = null;
            List<String> earlierGeneratedStepIds = new ArrayList<>();
            for (Map.Entry<String, TxWorkItem> member : byClaimKey.entrySet()) {
                TxStreamPlanningContext.PlanningSeed seed =
                        context.seed(member.getValue().getItemId());
                if (laneName == null) {
                    laneName = seed.lane.laneName();
                }
                String stepId = context.ids().stepId(member.getKey());
                // Pipelining one funding lane requires every later build to see
                // all earlier unspent same-lane change, not merely its immediate
                // predecessor. A predecessor may have selected another base UTxO
                // and left older pending change unconsumed.
                List<String> pipelineFunding = chainingMode == ChainingMode.PIPELINED
                        ? earlierGeneratedStepIds : List.of();
                flowBuilder.addStep(copyStepWithId(
                        seed.enforcedStep, stepId, pipelineFunding));
                mappings.add(new TxStreamPlannedItem(member.getValue().getItemId(), stepId));
                earlierGeneratedStepIds.add(stepId);
            }
            executions.add(new PlannedExecution(flowBuilder.build(), laneName,
                    flowClaimKey, mappings));
        }
        return TxStreamPlan.of(executions);
    }

    /**
     * Builds the {@code batching(...)} planner (ADR 0004 Decision 6): within
     * each lane group it merges compatible payment-shaped items into ONE
     * transaction — one flow, one merged step, N item→step mappings all
     * pointing at that step — so item status becomes transaction-granular. Any
     * item that is not payment-shaped (a script, mint, staking, governance,
     * metadata, {@code collectFrom}, reference input, or a contract/datum output
     * that {@code payToAddress} cannot reproduce) is emitted as its own
     * single-item flow (or, when the options reject them, fails the window
     * typed) — never silently merged.
     *
     * <p>Everything is deterministic: lane groups are keyed by canonical
     * spending identity, batchable members are ordered by claim key, the merged
     * transaction appends each member's payment outputs in that order, and every
     * identity derives from the sorted member keys — so the same window produces
     * a byte-identical, byte-stable plan regardless of submission order. The
     * merged flow's claim key is flow-level ({@link StreamIdentities#windowClaimKey}):
     * batching is flow-level dedup only (see {@link BatchingOptions}).</p>
     *
     * @param options batching configuration (item cap, non-payment handling)
     * @return the batching planner
     */
    static TxStreamPlanner batching(BatchingOptions options) {
        Objects.requireNonNull(options, "options");
        return context -> {
            // Lane groups keyed and ordered by canonical spending identity —
            // items on different lanes never share a transaction.
            Map<String, List<TxWorkItem>> laneGroups = new TreeMap<>();
            for (TxWorkItem item : context.items()) {
                String identity = context.seed(item.getItemId()).lane.canonicalSpendingIdentity();
                laneGroups.computeIfAbsent(identity, ignored -> new ArrayList<>()).add(item);
            }
            List<PlannedExecution> executions = new ArrayList<>();
            for (List<TxWorkItem> laneGroup : laneGroups.values()) {
                // Split each lane group into merge-eligible payment items
                // (sorted by claim key) and everything else.
                Map<String, TxWorkItem> batchable = new TreeMap<>();
                List<TxWorkItem> singletons = new ArrayList<>();
                for (TxWorkItem item : laneGroup) {
                    TxStreamPlanningContext.PlanningSeed seed = context.seed(item.getItemId());
                    if (isBatchablePayment(seed)) {
                        batchable.put(seed.claimKey, item);
                    } else if (options.allowNonPaymentSingletons()) {
                        singletons.add(item);
                    } else {
                        // Reject-mode: a non-payment item in the window fails it
                        // typed (the planner SPI has no per-item failure channel,
                        // so the whole window is rejected). Wrapped by the
                        // stream's planner isolation as TXSTREAM_PLANNER_FAILED
                        // with this cause.
                        throw new TxStreamException("TXSTREAM_BATCH_INELIGIBLE_ITEM",
                                "Item '" + item.getItemId() + "' is not a payment-shaped"
                                        + " transaction and BatchingOptions rejects non-payment"
                                        + " items; it cannot be batched");
                    }
                }
                // Non-payment / ineligible items keep per-item identity and true
                // per-item dedup (a perItem-style single-step flow each).
                for (TxWorkItem item : singletons) {
                    TxStreamPlanningContext.PlanningSeed seed = context.seed(item.getItemId());
                    FlowStep step = seed.enforcedStep;
                    TxFlow flow = TxFlow.builder(context.ids().flowId(List.of(seed.claimKey)))
                            .addStep(step)
                            .build();
                    executions.add(new PlannedExecution(flow, seed.lane.laneName(), seed.claimKey,
                            List.of(new TxStreamPlannedItem(item.getItemId(), step.getId()))));
                }
                // Batchable payments merge into chunks of at most
                // maxItemsPerTransaction (overflow -> multiple merged flows).
                List<Map.Entry<String, TxWorkItem>> sorted = new ArrayList<>(batchable.entrySet());
                int max = options.maxItemsPerTransaction();
                for (int start = 0; start < sorted.size(); start += max) {
                    executions.add(mergeChunk(context,
                            sorted.subList(start, Math.min(start + max, sorted.size()))));
                }
            }
            return TxStreamPlan.of(executions);
        };
    }

    /**
     * Whether an item's accept-time step is a plain, mergeable payment: exactly
     * one {@link Tx} whose intents are ALL pure {@link PaymentIntent}s
     * (losslessly reproducible by {@code payToAddress} — see
     * {@link #isPurePayment}), drawing from the lane's declared funding scope.
     * The funding-scope check is defence in depth — {@code enforceLaneFundingScope}
     * already pins built-in-lane items, so a member drawing from a foreign
     * source (only reachable through a custom lane setup) is treated as
     * ineligible and never silently re-homed onto the lane's source.
     */
    private static boolean isBatchablePayment(TxStreamPlanningContext.PlanningSeed seed) {
        Tx tx = singleTx(seed.enforcedStep.getTxPlan());
        if (tx == null) {
            return false;
        }
        List<TxIntent> intents = tx.getIntentions();
        if (intents.isEmpty()) {
            return false;
        }
        for (TxIntent intent : intents) {
            if (!(intent instanceof PaymentIntent) || !isPurePayment((PaymentIntent) intent)) {
                return false;
            }
        }
        return drawsFromLaneScope(tx, seed.lane.fundingScope());
    }

    /**
     * Whether a payment is losslessly reproducible by the merge — a POSITIVE,
     * future-proof equivalence guard, not a hand-maintained field denylist. The
     * merge re-emits every member payment as exactly
     * {@code payToAddress(address, amounts)} (see {@link #mergeChunk}); this
     * returns {@code true} only when that re-emission reproduces a
     * {@link PaymentIntent} EQUAL to the original, field for field. Any
     * attachment the re-emit does not carry — today a datum, datum hash, script,
     * or reference script; tomorrow any newly-added output-affecting
     * {@code PaymentIntent} field — makes the round-trip differ, so the payment
     * is treated as NOT batchable and runs unmerged rather than being silently
     * merged with that data dropped. (A payment with no address or no amounts
     * cannot be re-emitted at all and is likewise non-batchable.)
     *
     * <p>This is safe-by-construction: a future output-affecting field the merge
     * does not thread through can never be silently lost — an item carrying it
     * fails the round-trip and gets its own transaction. The old field-denylist
     * (FINDING-2) could drop an unknown new field; the round-trip cannot.</p>
     */
    private static boolean isPurePayment(PaymentIntent payment) {
        if (payment.getAddress() == null || payment.getAddress().isEmpty()
                || payment.getAmounts() == null || payment.getAmounts().isEmpty()) {
            return false;
        }
        // Positive round-trip: build exactly what the merge would emit and
        // require it to equal the original. Equality is over EVERY PaymentIntent
        // field (Lombok @Data), so any output-affecting field the merge omits —
        // known or future — makes this false and the item non-batchable.
        return reEmittedPayment(payment).equals(payment);
    }

    /**
     * The {@link PaymentIntent} the merge would produce for this member — a bare
     * {@code payToAddress(address, amounts)} carrying no other attachment — so
     * batchability is tested against the real merge operation
     * ({@link #mergeChunk} re-emits identically), not a static list of fields to
     * exclude.
     */
    private static PaymentIntent reEmittedPayment(PaymentIntent payment) {
        Tx probe = new Tx();
        probe.payToAddress(payment.getAddress(), new ArrayList<>(payment.getAmounts()));
        return (PaymentIntent) probe.getIntentions().get(0);
    }

    private static boolean drawsFromLaneScope(Tx tx, LaneFundingScope scope) {
        String from = tx.getSender();
        String fromRef = tx.getFromRef();
        boolean hasFrom = from != null && !from.isEmpty();
        boolean hasFromRef = fromRef != null && !fromRef.isEmpty();
        if (scope.kind() == LaneFundingScope.Kind.ADDRESS) {
            return hasFrom && !hasFromRef && scope.source().equals(from);
        }
        return hasFromRef && !hasFrom && scope.source().equals(fromRef);
    }

    /**
     * Merges one chunk of sorted-by-claim-key payment items into a single
     * {@link PlannedExecution}: one flow with one merged step carrying every
     * member's payment outputs (appended in claim-key order for byte
     * stability), funded from the lane's declared source, with every member
     * mapped to that one step. The flow claim key is flow-level over the sorted
     * member keys — flow-level dedup only.
     */
    private static PlannedExecution mergeChunk(TxStreamPlanningContext context,
                                               List<Map.Entry<String, TxWorkItem>> chunk) {
        // The chunk is already in ascending claim-key order (TreeMap entrySet).
        List<String> sortedKeys = new ArrayList<>(chunk.size());
        for (Map.Entry<String, TxWorkItem> member : chunk) {
            sortedKeys.add(member.getKey());
        }
        String flowClaimKey = StreamIdentities.windowClaimKey(sortedKeys);
        String flowId = context.ids().flowId(sortedKeys);
        String mergedStepId = StreamIdentities.mergedStepId(sortedKeys);
        ResolvedLane lane = context.seed(chunk.get(0).getValue().getItemId()).lane;
        LaneFundingScope scope = lane.fundingScope();

        Tx merged = new Tx();
        for (Map.Entry<String, TxWorkItem> member : chunk) {
            TxStreamPlanningContext.PlanningSeed seed =
                    context.seed(member.getValue().getItemId());
            Tx memberTx = singleTx(seed.enforcedStep.getTxPlan());
            for (TxIntent intent : memberTx.getIntentions()) {
                PaymentIntent payment = (PaymentIntent) intent;
                // Every member passed isBatchablePayment's positive round-trip
                // guard, so this exact re-emit is guaranteed lossless (no datum,
                // script, or future field is being dropped). Defensive copy of
                // the amounts so the merged plan never aliases the frozen member
                // payload's list.
                merged.payToAddress(payment.getAddress(), new ArrayList<>(payment.getAmounts()));
            }
        }
        // from(...) after the outputs are defined; lane-scoped coin selection.
        TxPlan mergedPlan;
        if (scope.kind() == LaneFundingScope.Kind.ADDRESS) {
            merged.from(scope.source());
            mergedPlan = TxPlan.from(merged);
        } else {
            merged.fromRef(scope.source());
            // A funding-ref lane resolves its sender AND its payment signer from
            // the same reference (mirroring the fromRef(ref).withSigner(ref)
            // pattern), so the merged plan carries the lane's funding ref as the
            // payment signer — otherwise the reconstructed transaction would have
            // no signer on a real engine. Deterministic (the ref is the lane
            // scope source).
            mergedPlan = TxPlan.from(merged).withSigner(scope.source());
        }

        FlowStep mergedStep = FlowStep.builder(mergedStepId)
                .withTxPlan(mergedPlan)
                .build();
        TxFlow flow = TxFlow.builder(flowId).addStep(mergedStep).build();
        List<TxStreamPlannedItem> mappings = new ArrayList<>(chunk.size());
        for (Map.Entry<String, TxWorkItem> member : chunk) {
            mappings.add(new TxStreamPlannedItem(member.getValue().getItemId(), mergedStepId));
        }
        return new PlannedExecution(flow, lane.laneName(), flowClaimKey, mappings);
    }

    /** The single {@link Tx} of a plan, or {@code null} when it is not exactly one Tx. */
    private static Tx singleTx(TxPlan plan) {
        if (plan == null || plan.getTxs().size() != 1 || !(plan.getTxs().get(0) instanceof Tx)) {
            return null;
        }
        return (Tx) plan.getTxs().get(0);
    }

    private BuiltInPlanners() {
    }

    /**
     * Copies a portable step under a new id. Multi-item flows need unique
     * step ids, so each member's accept-time step is re-identified with its
     * deterministic {@link StableIdFactory#stepId(String)} identity; every
     * other field is preserved. Java-factory steps cannot reach this point —
     * they are rejected at submit-time portability validation.
     */
    private static FlowStep copyStepWithId(FlowStep step, String newId) {
        return copyStepWithId(step, newId, List.of());
    }

    private static FlowStep copyStepWithId(FlowStep step, String newId,
                                           List<String> additionalFundingSteps) {
        FlowStep.Builder builder = FlowStep.builder(newId);
        if (step.getTxPlan() != null) {
            builder.withTxPlan(step.getTxPlan());
        }
        if (step.getTransactionTemplate() != null) {
            builder.withTransactionTemplate(step.getTransactionTemplate());
        }
        if (step.getDescription() != null) {
            builder.withDescription(step.getDescription());
        }
        if (step.getRetryPolicy() != null) {
            builder.withRetryPolicy(step.getRetryPolicy());
        }
        step.getDependencies().forEach(builder::dependsOn);
        Set<String> fundingSteps = new LinkedHashSet<>(step.getFundingFrom());
        fundingSteps.addAll(additionalFundingSteps);
        fundingSteps.forEach(builder::fundsFrom);
        step.getNeeds().forEach(builder::needs);
        step.getOutputBindings().forEach(builder::bindOutput);
        return builder.build();
    }
}
