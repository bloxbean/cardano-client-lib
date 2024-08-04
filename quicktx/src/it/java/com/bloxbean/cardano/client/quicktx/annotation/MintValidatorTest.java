package com.bloxbean.cardano.client.quicktx.annotation;

import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.annotation.mint_validator.MintValidator;
import com.bloxbean.cardano.client.quicktx.annotation.mint_validator.model.Action;
import com.bloxbean.cardano.client.quicktx.annotation.mint_validator.model.impl.ActionData;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.MintAsset;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class MintValidatorTest extends AnnotationTestBase {

    public MintValidatorTest() {
    }

    @Test
    void testMint() {
        var validator = deploy(false);
        mint(validator, false);
    }

    @Test
    void testMintWithReferenceInput() {
        var validator = deploy(true);
        mint(validator, true);
    }

    @Test
    void testMintTx() {
        var validator = deploy(false);
        mintTx(validator, false);
    }

    @Test
    void testMintTxWithReferenceInput() {
        var validator = deploy(true);
        mintTx(validator, true);
    }

    private MintValidator deploy(boolean withReferenceInput) {
        var mintValidator = new MintValidator(Networks.testnet())
                .withBackendService(backendService);

        var result = mintValidator.deploy(account.baseAddress())
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        if (withReferenceInput) {
            mintValidator.withReferenceTxInput(result.getValue(), 0);
        }

        assertTrue(result.isSuccessful());
        return mintValidator;
    }

    private void mint(MintValidator mintValidator, boolean withReferenceInput) {
        var mintAsset1 = new MintAsset("TestA", BigInteger.valueOf(2), receiver1);
        var mintAsset2 = new MintAsset("TestA", BigInteger.valueOf(3), receiver2);
        var mintAsset3 = new MintAsset("TestA", BigInteger.valueOf(4), receiver3);
        var mintAsset4 = new MintAsset("TestB", BigInteger.valueOf(10), receiver1);
        var mintAsset5 = new MintAsset("TestB", BigInteger.valueOf(20), receiver3);
        var mintAsset6 = new MintAsset("TestB", BigInteger.valueOf(9), receiver1);

        var txResult = mintValidator
                .mint(ActionData.of(Action.Mint), mintAsset1, mintAsset2, mintAsset3, mintAsset4, mintAsset5, mintAsset6)
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        System.out.println(txResult);
        assertTrue(txResult.isSuccessful());

    }

    public void mintTx(MintValidator mintValidator, boolean withReferenceInput) {
        var mintAsset1 = new MintAsset("TestAA", BigInteger.valueOf(2), receiver1);
        var mintAsset2 = new MintAsset("TestAA", BigInteger.valueOf(3), receiver2);
        var mintAsset3 = new MintAsset("TestAA", BigInteger.valueOf(4), receiver3);
        var mintAsset4 = new MintAsset("TestBB", BigInteger.valueOf(10), receiver1);
        var mintAsset5 = new MintAsset("TestBB", BigInteger.valueOf(20), receiver3);
        var mintAsset6 = new MintAsset("TestBB", BigInteger.valueOf(9), receiver1);

        var tx = mintValidator
                .mintTx(ActionData.of(Action.Mint), mintAsset1, mintAsset2, mintAsset3, mintAsset4, mintAsset5, mintAsset6);

        var txResult = new QuickTxBuilder(backendService)
                .compose(tx)
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .preBalanceTx((context, txn) -> {
                    if (withReferenceInput)
                        txn.getWitnessSet().getPlutusV2Scripts().remove(mintValidator.getPlutusScript());
                })
                .completeAndWait(System.out::println);

        System.out.println(txResult);
        assertTrue(txResult.isSuccessful());

    }

}
