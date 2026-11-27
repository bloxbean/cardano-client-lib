package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenTx;
import com.bloxbean.cardano.client.programmabletoken.ThirdPartyBurnAuthorization;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.programmabletoken.cip113.tx.ThirdPartyFixture.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A script the deployment publishes is referenced, never also witnessed: the ledger rejects a
 * witness the reference inputs already supply ({@code ExtraneousScriptWitnessesUTXOW}).
 *
 * <p>CCL attaches every script it invokes — attaching is what makes QuickTx emit the redeemer —
 * and adds the publishing UTxO as a reference input. The witness copy must then be dropped
 * whether or not the caller declares reference scripts of its own: a caller's
 * {@code withReferenceScripts} switches QuickTx's reference-script resolver off.</p>
 */
class PublishedScriptWitnessTest {

    /** The core scripts a third-party burn invokes: base spend, dispatcher, issuance logic, third_party. */
    private static final PlutusScript[] CORE = {BASE, DISPATCHER, ISSUANCE_LOGIC, THIRD_PARTY};
    /** A script of the caller's own, published elsewhere, that the caller declares itself. */
    private static final PlutusScript CALLERS_OWN = script("0b");

    @Test
    void publishedCoreScriptsAreNotWitnessedWhenQuickTxResolvesReferences() {
        Transaction txn = burn(new ThirdPartyFixture().publishing(CORE));

        assertReferencedNotWitnessed(txn);
    }

    @Test
    void publishedCoreScriptsAreNotWitnessedWhenTheCallerDeclaresReferenceScripts() {
        Transaction txn = burn(new ThirdPartyFixture().publishing(CORE).declaringReferenceScripts(CALLERS_OWN));

        assertReferencedNotWitnessed(txn);
    }

    private static Transaction burn(ThirdPartyFixture fixture) {
        Utxo pinned = held("1a", 2, HOLDER_A, Amount.asset(UNIT_A, 9));
        fixture.holding(pinned);
        return fixture.build(new ProgrammableTokenTx().from(ADMIN.toBech32())
                .thirdPartyBurn(ref(pinned), POLICY_A, List.of(asset(TOK, 9)),
                        ThirdPartyBurnAuthorization.of(BigIntPlutusData.of(7), BigIntPlutusData.of(7))));
    }

    private static void assertReferencedNotWitnessed(Transaction txn) {
        Set<String> referenceInputs = txn.getBody().getReferenceInputs().stream()
                .map(in -> in.getTransactionId() + "#" + in.getIndex())
                .collect(Collectors.toSet());
        Set<String> witnessed = txn.getWitnessSet().getPlutusV3Scripts() == null ? Set.of()
                : txn.getWitnessSet().getPlutusV3Scripts().stream()
                        .map(ThirdPartyFixture::hash)
                        .collect(Collectors.toSet());

        for (PlutusScript core : CORE) {
            Utxo publishing = new ThirdPartyFixture().publishing(CORE).published.get(hash(core));
            assertThat(referenceInputs).as("the UTxO publishing %s is a reference input", hash(core))
                    .contains(ref(publishing));
            assertThat(witnessed).as("published script %s is not also witnessed", hash(core))
                    .doesNotContain(hash(core));
        }
        assertThat(witnessed).as("the token's own logic is not published, so it stays witnessed")
                .contains(hash(LOGIC_A));
    }
}
