package com.bloxbean.cardano.client.quicktx.annotation;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.ScriptUtxoFinders;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.annotation.helloworld.HelloWorldValidator;
import com.bloxbean.cardano.client.quicktx.annotation.helloworld.model.Owner;
import com.bloxbean.cardano.client.quicktx.annotation.helloworld.model.impl.OwnerData;
import com.bloxbean.cardano.client.quicktx.annotation.helloworld.model.impl.RedeemerData;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.ChangeReceiver;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.PubKeyReceiver;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class HelloWorldValidatorTest extends AnnotationTestBase {

    HelloWorldValidator validator;
    public HelloWorldValidatorTest() {
        this.validator = new HelloWorldValidator(Networks.testnet())
                .withBackendService(backendService);
    }

    @Test
    public void deployAndLockAndUnlock_withoutRefInput() {
        var datum = deployAndLock(false);
        unlock(datum);
    }

    @Test
    public void deployAndLockAndUnlock() {
        var datum = deployAndLock(true);
        unlock(datum);
    }

    @Test
    public void deployAndLockAndUnlockWithUtxo() throws Exception {
        var datum = deployAndLock(true);
        unlockWithUtxo(datum);
    }

    @Test
    public void deployTxAndLockTxAndUnlockTx() {
        var datum = deployTxAndLockTx(true);
        unlockTx(datum);
    }

    @Test
    public void deployTxAndLockTxAndUnlockTxWithUtxo() throws Exception {
        var datum = deployTxAndLockTx(true);
        unlockTxWithUtxo(datum);
    }

    private Owner deployAndLock(boolean withReferenceInput) {
        String address = account.baseAddress();

        //Deploy the contract to create reference input
        var txResult = validator
                .deploy(address)
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        if (withReferenceInput) {
            validator.withReferenceTxInput(txResult.getValue(), 0);
            System.out.println(txResult);
        }

        //Datum to lock
        Owner owner = new OwnerData();
        owner.setOwner(account.getBaseAddress().getPaymentCredentialHash().get());

        var amount = Amount.ada(20);

        //Lock the contract
        var txResult1 = validator.lock(address, amount, owner)
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);
        System.out.println(txResult1);

        assertTrue(txResult1.isSuccessful());

        return owner;
    }

    private Owner deployTxAndLockTx(boolean withReferenceInput) {
        String address = account.baseAddress();

        //Deploy the contract to create reference input
        var tx = validator
                .deployTx(address);

        var txResult = new QuickTxBuilder(backendService)
                .compose(tx)
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        if (withReferenceInput) {
            validator.withReferenceTxInput(txResult.getValue(), 0);
            System.out.println(txResult);
        }

        //Datum to lock
        Owner owner = new OwnerData();
        owner.setOwner(account.getBaseAddress().getPaymentCredentialHash().get());

        var amount = Amount.ada(20);

        //Lock the contract
        var tx1 = validator.lockTx(address, amount, owner);
        var txResult1 = new QuickTxBuilder(backendService)
                .compose(tx1)
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);
        System.out.println(txResult1);

        assertTrue(txResult1.isSuccessful());

        return owner;
    }

    private void unlock(Owner datum) {
        //Unlock the contract
        var receiver = new PubKeyReceiver(account.baseAddress(), Amount.ada(20));
        var redeemer = new RedeemerData();
        redeemer.setMsg("Hello, World!".getBytes(StandardCharsets.UTF_8));

        var txResult2 = validator
                .unlock(datum, redeemer, List.of(receiver), new ChangeReceiver(account.baseAddress()))
                .feePayer(account.baseAddress())
                .withRequiredSigners(account.getBaseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        System.out.println(txResult2.getValue());
        System.out.println(txResult2.getResponse());

        assertTrue(txResult2.isSuccessful());
    }

    private void unlockWithUtxo(Owner datum) throws ApiException {
        var utxo = ScriptUtxoFinders.findFirstByInlineDatum(new DefaultUtxoSupplier(backendService.getUtxoService()),
                validator.getScriptAddress(), datum.toPlutusData());
        assertTrue(utxo.isPresent());

        //Unlock the contract
        var receiver = new PubKeyReceiver(account.baseAddress(), Amount.ada(20));
        var redeemer = new RedeemerData();
        redeemer.setMsg("Hello, World!".getBytes(StandardCharsets.UTF_8));

        var txResult2 = validator
                .unlock(utxo.get(), redeemer, List.of(receiver), new ChangeReceiver(account.baseAddress()))
                .feePayer(account.baseAddress())
                .withRequiredSigners(account.getBaseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        System.out.println(txResult2.getValue());
        System.out.println(txResult2.getResponse());

        assertTrue(txResult2.isSuccessful());
    }

    private void unlockTx(Owner datum) {
        //Unlock the contract
        var receiver = new PubKeyReceiver(account.baseAddress(), Amount.ada(20));
        var redeemer = new RedeemerData();
        redeemer.setMsg("Hello, World!".getBytes(StandardCharsets.UTF_8));

        var tx = validator
                .unlockTx(datum, redeemer, List.of(receiver), new ChangeReceiver(account.baseAddress()));

        var txResult2 = new QuickTxBuilder(backendService)
                .compose(tx)
                .feePayer(account.baseAddress())
                .withRequiredSigners(account.getBaseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        System.out.println(txResult2.getValue());
        System.out.println(txResult2.getResponse());

        assertTrue(txResult2.isSuccessful());
    }

    private void unlockTxWithUtxo(Owner datum) throws ApiException {
        var utxo = ScriptUtxoFinders.findFirstByInlineDatum(new DefaultUtxoSupplier(backendService.getUtxoService()),
                validator.getScriptAddress(), datum.toPlutusData());
        assertTrue(utxo.isPresent());

        //Unlock the contract
        var receiver = new PubKeyReceiver(account.baseAddress(), Amount.ada(20));
        var redeemer = new RedeemerData();
        redeemer.setMsg("Hello, World!".getBytes(StandardCharsets.UTF_8));

        var tx = validator
                .unlockTx(utxo.get(), redeemer, List.of(receiver), new ChangeReceiver(account.baseAddress()));

        var txResult2 = new QuickTxBuilder(backendService)
                .compose(tx)
                .feePayer(account.baseAddress())
                .withRequiredSigners(account.getBaseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        System.out.println(txResult2.getValue());
        System.out.println(txResult2.getResponse());

        assertTrue(txResult2.isSuccessful());
    }

}

