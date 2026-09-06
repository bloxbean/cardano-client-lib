import com.bloxbean.cardano.julc.core.Builtins;
import com.bloxbean.cardano.julc.core.PlutusData;
import com.bloxbean.cardano.julc.ledger.OutputDatum;
import com.bloxbean.cardano.julc.ledger.ScriptContext;
import com.bloxbean.cardano.julc.stdlib.annotation.Entrypoint;
import com.bloxbean.cardano.julc.stdlib.annotation.WithdrawValidator;

import java.math.BigInteger;

/**
 * Integration-only programmable-token substandard compiled to Plutus V3 by JuLC.
 *
 * <p>It accepts only the typed authorization {@code Authorization(1, 42)} and requires the
 * programmable-token state participating in the transaction to carry the inline datum
 * {@code HolderDatum(1, 113)}. Minting supplies it on an output; transfer and burn consume it from
 * an input. This proves that TxPlan structured redeemers and inline datums reach an actual on-chain
 * substandard validator; an always-true script cannot provide that guarantee.</p>
 */
@WithdrawValidator
public class TxPlanSubstandard {
    public record Authorization(BigInteger action, BigInteger nonce) { }
    public record HolderDatum(BigInteger schema, BigInteger tag) { }

    @Entrypoint
    public static boolean validate(Authorization authorization, ScriptContext context) {
        boolean authorized = authorization.action().equals(BigInteger.ONE)
                && authorization.nonce().equals(BigInteger.valueOf(42));
        if (!authorized) return false;

        PlutusData expectedDatum = (PlutusData) (Object)
                new HolderDatum(BigInteger.ONE, BigInteger.valueOf(113));
        boolean datumOnOutput = context.txInfo().outputs().any(output ->
                matches(output.datum(), expectedDatum));
        boolean datumOnInput = context.txInfo().inputs().any(input ->
                matches(input.resolved().datum(), expectedDatum));
        return datumOnOutput || datumOnInput;
    }

    private static boolean matches(OutputDatum outputDatum, PlutusData expectedDatum) {
        // A CIP-113 transaction also contains unrelated datum schemas, notably its registry datum.
        // Structural comparison is safe across heterogeneous datum values; eagerly casting every
        // inline datum to HolderDatum is not.
        return switch (outputDatum) {
            case OutputDatum.OutputDatumInline inline ->
                    Builtins.equalsData(inline.datum(), expectedDatum);
            case OutputDatum.NoOutputDatum ignored -> false;
            case OutputDatum.OutputDatumHash ignored -> false;
        };
    }
}
