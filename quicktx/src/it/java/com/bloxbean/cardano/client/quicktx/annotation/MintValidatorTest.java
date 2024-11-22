package com.bloxbean.cardano.client.quicktx.annotation;

import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.annotation.mintvalidator.MintValidator;
import com.bloxbean.cardano.client.quicktx.annotation.mintvalidator.model.Action;
import com.bloxbean.cardano.client.quicktx.annotation.mintvalidator.model.impl.ActionData;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.MintAsset;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

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

    @Test
    void testMintToAddress() {
        var validator = deploy(false);
        mintToAddress(validator, false);
    }

    @Test
    void testMintToContract() {
        var validator = deploy(true);
        mintToContract(validator, true);
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

    private void mintToAddress(MintValidator mintValidator, boolean withReferenceInput) {

        Asset asset1 = new Asset("MintToAddressToken1", BigInteger.valueOf(100));
        Asset asset2 = new Asset("MintToAddressToken2", BigInteger.valueOf(100));
        var txResult = mintValidator
                .mintToAddress(ActionData.of(Action.Mint), List.of(asset1, asset2), receiver1)
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        System.out.println(txResult);
        assertTrue(txResult.isSuccessful());

        Asset asset3 = new Asset("MintToAddressToken3", BigInteger.valueOf(3));
        txResult = mintValidator
                .mintToAddress(ActionData.of(Action.Mint), asset3, receiver1)
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        System.out.println(txResult);
        assertTrue(txResult.isSuccessful());

    }

    private void mintToContract(MintValidator mintValidator, boolean withReferenceInput) {

        Asset asset1 = new Asset("MintToAddressToken1", BigInteger.valueOf(100));
        Asset asset2 = new Asset("MintToAddressToken2", BigInteger.valueOf(200));

        var txResult = mintValidator
                .mintToContract(ActionData.of(Action.Mint), List.of(asset1, asset2), mintValidator.getScriptAddress(), () -> ConstrPlutusData.of(3))
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        System.out.println(txResult);
        assertTrue(txResult.isSuccessful());

        Asset asset3 = new Asset("MintToAddressToken3", BigInteger.valueOf(5));

        txResult = mintValidator
                .mintToContract(ActionData.of(Action.Mint), asset3, mintValidator.getScriptAddress(), () -> ConstrPlutusData.of(5))
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        System.out.println(txResult);
        assertTrue(txResult.isSuccessful());

        Asset asset4 = new Asset("MintToAddressToken4", BigInteger.valueOf(4));

        txResult = mintValidator
                .mintToContract(ActionData.of(Action.Mint), asset4, mintValidator.getScriptAddress(), ConstrPlutusData.of(4))
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        System.out.println(txResult);
        assertTrue(txResult.isSuccessful());
    }

    @Test
    void testMintAddressTx() {
        var validator = deploy(false);
        mintAddressTx(validator, false);
    }

    public void mintAddressTx(MintValidator mintValidator, boolean withReferenceInput) {
        Asset asset1 = new Asset("MintToAddressToken1", BigInteger.valueOf(100));
        Asset asset2 = new Asset("MintToAddressToken2", BigInteger.valueOf(200));

        var tx = mintValidator
                .mintToAddressTx(ActionData.of(Action.Mint), List.of(asset1, asset2), receiver1);

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

        //--

        Asset asset3 = new Asset("MintToAddressToken3", BigInteger.valueOf(900));
        tx = mintValidator
                .mintToAddressTx(ActionData.of(Action.Mint), asset3, receiver1);

        txResult = new QuickTxBuilder(backendService)
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

    @Test
    void testMintContractTx() {
        var validator = deploy(false);
        mintContractTx(validator, false);
    }

    public void mintContractTx(MintValidator mintValidator, boolean withReferenceInput) {
        Asset asset1 = new Asset("MintToAddressToken1", BigInteger.valueOf(100));
        Asset asset2 = new Asset("MintToAddressToken2", BigInteger.valueOf(200));

        var tx = mintValidator
                .mintToContractTx(ActionData.of(Action.Mint), List.of(asset1, asset2),
                        mintValidator.getScriptAddress(), () -> ConstrPlutusData.of(444));

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

        //--

        Asset asset3 = new Asset("MintToAddressToken3", BigInteger.valueOf(900));
        tx = mintValidator
                .mintToContractTx(ActionData.of(Action.Mint), asset3, mintValidator.getScriptAddress(), ConstrPlutusData.of(555));

        txResult = new QuickTxBuilder(backendService)
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

        Asset asset4 = new Asset("MintToAddressToken4", BigInteger.valueOf(4400));
        tx = mintValidator
                .mintToContractTx(ActionData.of(Action.Mint), asset4, mintValidator.getScriptAddress(), () -> ConstrPlutusData.of(666));

        txResult = new QuickTxBuilder(backendService)
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
