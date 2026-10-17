package com.bloxbean.cardano.client.txflow.testsupport;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.quicktx.AbstractTx;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionBuildContext;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionIntent;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionMetadata;
import com.bloxbean.cardano.client.quicktx.extension.QuickTxExtension;
import com.bloxbean.cardano.client.quicktx.extension.TxBuildExtension;
import com.bloxbean.cardano.client.quicktx.intent.PaymentIntent;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-only QuickTx extension used to prove TxFlow supports extensions generically (ADR 0008 M1):
 * TxFlow has no knowledge of it. Each {@code fake:pay} intent becomes an ordinary payment, may scan
 * a "protocol" address or look up one exact output through the build's supplier, and records what
 * it saw so tests can assert visibility and literal values.
 */
public final class FakeExtension implements QuickTxExtension {
    public static final String ID = "fake";
    public static final String NAMESPACE = "fk";
    /** Lookup value that exact-reads every output the same intent just found by address scan. */
    public static final String SCANNED = "scanned";

    private final String network;
    public final List<String> notes = new CopyOnWriteArrayList<>();
    public final List<List<Utxo>> scans = new CopyOnWriteArrayList<>();
    public final List<Optional<Utxo>> lookups = new CopyOnWriteArrayList<>();

    public FakeExtension() {
        this("0");
    }

    /** @param network the deployment network this instance is configured for */
    public FakeExtension(String network) {
        this.network = network;
    }

    @Override public String id() { return ID; }
    @Override public String schemaVersion() { return "1"; }
    @Override public Set<String> operations() { return Set.of(PayIntent.OPERATION); }

    @Override
    public Map<String, Class<? extends ExtensionIntent>> intentTypes() {
        return Map.of(PayIntent.OPERATION, PayIntent.class);
    }

    @Override
    public ExtensionMetadata metadata() {
        return ExtensionMetadata.builder().extension(ID).schemaVersion("1")
                .deployment(Map.of("network", network)).build();
    }

    /** Like a real protocol, the binding must match the operator-configured deployment. */
    @Override
    public void validateMetadata(ExtensionMetadata metadata) {
        QuickTxExtension.super.validateMetadata(metadata);
        Object declared = metadata.getDeployment().get("network");
        if (!network.equals(String.valueOf(declared)))
            throw new IllegalArgumentException("deployment network " + declared
                    + " does not match configured network " + network);
    }

    @Override
    public TxBuildExtension newBuildExtension() {
        return new TxBuildExtension() {
            @Override
            public void prepare(ExtensionBuildContext context) {
                for (AbstractTx<?> transaction : context.getTransactions()) {
                    for (ExtensionIntent declared : context.extensionIntents(transaction, ID)) {
                        PayIntent intent = (PayIntent) declared;
                        if (intent.getNote() != null) notes.add(intent.getNote());
                        List<Utxo> seen = List.of();
                        if (intent.getScanAddress() != null) {
                            seen = context.getUtxoSupplier().getAll(intent.getScanAddress());
                            scans.add(List.copyOf(seen));
                            if (Boolean.TRUE.equals(intent.getRequireScanHit()) && seen.isEmpty())
                                throw new IllegalStateException("fake protocol state not found at "
                                        + intent.getScanAddress());
                        }
                        if (SCANNED.equals(intent.getLookup())) {
                            for (Utxo utxo : seen)
                                lookups.add(context.getUtxoSupplier().getTxOutput(utxo.getTxHash(), utxo.getOutputIndex()));
                        } else if (intent.getLookup() != null) {
                            String[] ref = intent.getLookup().split("#");
                            lookups.add(context.getUtxoSupplier().getTxOutput(ref[0], Integer.parseInt(ref[1])));
                        }
                        context.addPreparedIntent(transaction, PaymentIntent.builder()
                                .address(intent.getReceiver())
                                .amounts(List.of(Amount.lovelace(BigInteger.valueOf(intent.getLovelace()))))
                                .build());
                    }
                }
            }
        };
    }

    /** A plan with one {@code fake:pay} intent, bound under {@link #NAMESPACE}. */
    public TxPlan plan(String sender, PayIntent intent) {
        Tx tx = new Tx().from(sender);
        tx.addIntention(intent);
        return TxPlan.from(tx).withExtension(NAMESPACE, metadata()).withSigner(FakeChain.SIGNER);
    }

    public static PayIntent pay(String receiver, long lovelace) {
        PayIntent intent = new PayIntent();
        intent.setReceiver(receiver);
        intent.setLovelace(lovelace);
        return intent;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class PayIntent implements ExtensionIntent {
        public static final String OPERATION = "pay";
        private String receiver;
        private long lovelace;
        private String note;
        private String scanAddress;
        private Boolean requireScanHit;
        private String lookup;

        @Override public String getExtensionId() { return ID; }
        @Override public String getOperation() { return OPERATION; }
        @Override public boolean hasRedeemer() { return false; }

        @Override
        public void validate() {
            if (receiver == null || receiver.isBlank()) throw new IllegalStateException("receiver is required");
            if (lovelace <= 0) throw new IllegalStateException("lovelace must be positive");
        }

        public String getReceiver() { return receiver; }
        public void setReceiver(String receiver) { this.receiver = receiver; }
        public long getLovelace() { return lovelace; }
        public void setLovelace(long lovelace) { this.lovelace = lovelace; }
        public String getNote() { return note; }
        public PayIntent note(String value) { this.note = value; return this; }
        public void setNote(String note) { this.note = note; }
        @JsonProperty("scan_address") public String getScanAddress() { return scanAddress; }
        @JsonProperty("scan_address") public void setScanAddress(String scanAddress) { this.scanAddress = scanAddress; }
        public PayIntent scan(String address, boolean requireHit) {
            this.scanAddress = address;
            this.requireScanHit = address != null && requireHit ? Boolean.TRUE : null;
            return this;
        }
        @JsonProperty("require_scan_hit") public Boolean getRequireScanHit() { return requireScanHit; }
        @JsonProperty("require_scan_hit") public void setRequireScanHit(Boolean value) { this.requireScanHit = value; }
        public String getLookup() { return lookup; }
        public void setLookup(String lookup) { this.lookup = lookup; }
        public PayIntent lookup(String txHash, int index) { this.lookup = txHash + "#" + index; return this; }
    }
}
