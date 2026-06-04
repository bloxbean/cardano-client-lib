package com.bloxbean.cardano.client.annotation.devnet.plutus;

import com.bloxbean.cardano.client.annotation.devnet.plutus.mintpolicy.MintPolicyMintValidator;
import com.bloxbean.cardano.client.annotation.devnet.plutus.mintpolicy.model.MintAction;
import com.bloxbean.cardano.client.annotation.devnet.plutus.mintpolicy.model.converter.MintActionConverter;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.it.BaseIT;
import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.Data;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import scalus.bloxbean.MapScriptSupplier;
import scalus.bloxbean.ScalusScriptUtils;
import scalus.bloxbean.ScalusTransactionEvaluator;

import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Unique trait:</b> the only {@code mint}-purpose validator — uses
 * {@link com.bloxbean.cardano.client.quicktx.blueprint.extender.MintValidatorExtender}
 * (not the lock/unlock extender), has no datum, and its 2-variant redeemer
 * is generated as a Java {@code enum} (auto-emitted for zero-field ADT
 * variants — a different codegen pathway from {@link MultiActionDevnetTest}'s
 * interface-based ADT).
 *
 * <b>Asserts on Cardano:</b> minting 1000 units of {@code CclDemoToken}
 * under the bound authority's signature succeeds, proving the mint
 * codepath wires up end-to-end on a real network — the right extender, the
 * enum-redeemer-to-{@code Constr} conversion, the parameter-applied policy
 * script, and the mint witness all the way to a token appearing in the
 * receiver's UTxO.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Mint policy validator — exercises the mint purpose, the MintValidatorExtender, and the enum-redeemer codegen path")
public class MintPolicyDevnetTest extends BaseIT {

    private MintPolicyMintValidator validator;
    private BackendService backendService;

    @SneakyThrows
    @BeforeAll
    void setup() {
        initializeAccounts();
        backendService = getBackendService();
        topupAllTestAccounts();

        var protocolParams = backendService.getEpochService().getProtocolParameters().getValue();
        var utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());

        // Bind authority to account1's payment key hash.
        String appliedCode = applyParamsAndUnwrap(MintPolicyMintValidator.COMPILED_CODE,
                BytesPlutusData.of(account1.getBaseAddress().getPaymentCredentialHash().get()));

        validator = new MintPolicyMintValidator(Networks.testnet(), appliedCode)
                .withBackendService(backendService);

        var scriptSupplier = new MapScriptSupplier(Map.of(
                validator.getApplyParamHash(), validator.getPlutusScript()));
        validator.withTransactionEvaluator(
                new ScalusTransactionEvaluator(protocolParams, utxoSupplier, scriptSupplier));
    }

    @Test
    @DisplayName("Mint redeemer (Mint enum variant) authorises minting a token to the bound authority")
    void mintRedeemerMintsTokenOnChain() {
        MintActionConverter converter = new MintActionConverter();
        Data<?> redeemer = () -> converter.toPlutusData(MintAction.Mint);

        Asset asset = new Asset("CclDemoToken", BigInteger.valueOf(1_000));

        var result = validator
                .mintToAddress(redeemer, asset, address1)
                .feePayer(address1)
                .withRequiredSigners(account1.getBaseAddress())
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);

        System.out.println("Mint result: " + result);
        assertTrue(result.isSuccessful(), "Mint should succeed under the bound authority signer");
    }

    /**
     * Scalus's {@code applyParamsToScript} expects double-CBOR-wrapped script hex and returns
     * double-CBOR-wrapped output. The blueprint's {@code COMPILED_CODE} constant is the
     * single-wrapped form, and the generated validator constructor expects single-wrapped too
     * (it re-wraps internally via {@code getPlutusScriptFromCompiledCode}). Bridge the two:
     * wrap once before Scalus, strip one layer after.
     */
    private static String applyParamsAndUnwrap(String singleWrappedHex, PlutusData... params) {
        String doubleWrapped = PlutusBlueprintUtil
                .getPlutusScriptFromCompiledCode(singleWrappedHex, PlutusVersion.v3)
                .getCborHex();
        String appliedDouble = ScalusScriptUtils.applyParamsToScript(doubleWrapped, params);
        ByteString outerByteString = (ByteString) CborSerializationUtil
                .deserialize(HexUtil.decodeHexString(appliedDouble));
        return HexUtil.encodeHexString(outerByteString.getBytes());
    }
}
