package com.bloxbean.cardano.client.quicktx.annotation;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.cardano.client.function.helper.ScriptUtxoFinders;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.annotation.helloworld.HelloWorldValidator;
import com.bloxbean.cardano.client.quicktx.annotation.helloworld.model.Owner;
import com.bloxbean.cardano.client.quicktx.annotation.helloworld.model.impl.OwnerData;
import com.bloxbean.cardano.client.quicktx.annotation.helloworld.model.impl.RedeemerData;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.ChangeReceiver;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.PubKeyReceiver;
import com.bloxbean.cardano.client.util.JsonUtil;
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

    @Test
    public void fullUnlock_toRegularAddress() {
        var datum = deployAndLock(true);
        fullUnlockToRegularAddress(datum);
    }

    @Test
    public void fullUnlock_toScriptAddress() {
        var datum = deployAndLock(true);
        fullUnlockToScriptAddress(datum);
    }

    @Test
    public void fullUnlock_toScriptAddress_withCustomDatum() {
        var datum = deployAndLock(true);
        fullUnlockToScriptAddressWithCustomDatum(datum);
    }

    @Test
    public void fullUnlock_toScriptAddress_withUtxo() throws ApiException {
        var datum = deployAndLock(true);
        fullUnlockToScriptAddressWithUtxo(datum);
    }

    @Test
    public void fullUnlock_toScriptAddress_withUtxo_withPlutusData() throws ApiException {
        var datum = deployAndLock(true);
        fullUnlockToScriptAddressWithUtxo_withPlutusData(datum);
    }

    @Test
    public void fullUnlock_toRegularAddress_withUtxo() throws ApiException {
        var datum = deployAndLock(true);
        fullUnlockToRegularAddressWithUtxo(datum);
    }

    @Test
    public void fullUnlockTxWithUtxo_toRegularAddress() throws ApiException {
        var datum = deployAndLock(true);
        fullUnlockTxWithUtxo_toRegularAddress(datum);
    }

    @Test
    public void fullUnlockTxWithUtxo_toContractAddress() throws ApiException {
        var datum = deployAndLock(true);
        fullUnlockTxWithUtxo_toContractAddress(datum);
    }

    @Test
    public void fullUnlockTx_toRegularAddress() throws ApiException {
        var datum = deployAndLock(true);
        fullUnlockTx_toRegularAddress(datum);
    }

    @Test
    public void fullUnlockTx_toContractAddress() throws ApiException {
        var datum = deployAndLock(true);
        fullUnlockTx_toContractAddress(datum);
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
                .withReferenceScripts(validator.getPlutusScript())
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
                .withReferenceScripts(validator.getPlutusScript())
                .completeAndWait(System.out::println);

        System.out.println(txResult2.getValue());
        System.out.println(txResult2.getResponse());

        assertTrue(txResult2.isSuccessful());
    }

    private void fullUnlockToRegularAddress(Owner datum) {
        var redeemer = new RedeemerData();
        redeemer.setMsg("Hello, World!".getBytes(StandardCharsets.UTF_8));

        var txResult2 = validator
                .unlockToAddress(datum, redeemer, receiver1)
                .feePayer(account.baseAddress())
                .withRequiredSigners(account.getBaseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        System.out.println(txResult2.getValue());
        System.out.println(txResult2.getResponse());

        assertTrue(txResult2.isSuccessful());
    }

    private void fullUnlockToScriptAddress(Owner datum) {
        var redeemer = new RedeemerData();
        redeemer.setMsg("Hello, World!".getBytes(StandardCharsets.UTF_8));

        var newDatum = new OwnerData();
        newDatum.setOwner(new Address(receiver1).getPaymentCredentialHash().get());

        var txResult2 = validator
                .unlockToContract(datum, redeemer, validator.getScriptAddress(), newDatum)
                .feePayer(account.baseAddress())
                .withRequiredSigners(account.getBaseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        System.out.println(txResult2.getValue());
        System.out.println(txResult2.getResponse());

        assertTrue(txResult2.isSuccessful());
    }

    private void fullUnlockToScriptAddressWithCustomDatum(Owner datum) {
        var redeemer = new RedeemerData();
        redeemer.setMsg("Hello, World!".getBytes(StandardCharsets.UTF_8));

        var newDatum = BigIntPlutusData.of(System.currentTimeMillis());

        var txResult2 = validator
                .unlockToContract(datum, redeemer, validator.getScriptAddress(), newDatum)
                .feePayer(account.baseAddress())
                .withRequiredSigners(account.getBaseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        System.out.println(txResult2.getValue());
        System.out.println(txResult2.getResponse());

        assertTrue(txResult2.isSuccessful());
    }

    private void fullUnlockToScriptAddressWithUtxo(Owner datum) throws ApiException {
        var utxo = ScriptUtxoFinders.findFirstByInlineDatum(new DefaultUtxoSupplier(backendService.getUtxoService()),
                validator.getScriptAddress(), datum.toPlutusData());
        assertTrue(utxo.isPresent());

        var redeemer = new RedeemerData();
        redeemer.setMsg("Hello, World!".getBytes(StandardCharsets.UTF_8));

        var newDatum = new OwnerData();
        newDatum.setOwner(new Address(receiver1).getPaymentCredentialHash().get());

        var txResult2 = validator
                .unlockToContract(utxo.get(), redeemer, validator.getScriptAddress(), newDatum)
                .feePayer(account.baseAddress())
                .withRequiredSigners(account.getBaseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        System.out.println(txResult2.getValue());
        System.out.println(txResult2.getResponse());

        assertTrue(txResult2.isSuccessful());
    }

    private void fullUnlockToScriptAddressWithUtxo_withPlutusData(Owner datum) throws ApiException {
        var utxo = ScriptUtxoFinders.findFirstByInlineDatum(new DefaultUtxoSupplier(backendService.getUtxoService()),
                validator.getScriptAddress(), datum.toPlutusData());
        assertTrue(utxo.isPresent());

        var redeemer = new RedeemerData();
        redeemer.setMsg("Hello, World!".getBytes(StandardCharsets.UTF_8));

        var newDatum = new OwnerData();
        newDatum.setOwner(new Address(receiver1).getPaymentCredentialHash().get());

        var txResult2 = validator
                .unlockToContract(utxo.get(), redeemer, validator.getScriptAddress(), newDatum.toPlutusData())
                .feePayer(account.baseAddress())
                .withRequiredSigners(account.getBaseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        System.out.println(txResult2.getValue());
        System.out.println(txResult2.getResponse());

        assertTrue(txResult2.isSuccessful());
    }


    private void fullUnlockToRegularAddressWithUtxo(Owner datum) throws ApiException {
        var utxo = ScriptUtxoFinders.findFirstByInlineDatum(new DefaultUtxoSupplier(backendService.getUtxoService()),
                validator.getScriptAddress(), datum.toPlutusData());
        assertTrue(utxo.isPresent());

        var redeemer = new RedeemerData();
        redeemer.setMsg("Hello, World!".getBytes(StandardCharsets.UTF_8));

        var newDatum = new OwnerData();
        newDatum.setOwner(new Address(receiver1).getPaymentCredentialHash().get());

        var txResult2 = validator
                .unlockToAddress(utxo.get(), redeemer, receiver1)
                .feePayer(account.baseAddress())
                .withRequiredSigners(account.getBaseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        System.out.println(txResult2.getValue());
        System.out.println(txResult2.getResponse());

        assertTrue(txResult2.isSuccessful());
    }

    private void fullUnlockTxWithUtxo_toRegularAddress(Owner datum) throws ApiException {
        var utxo = ScriptUtxoFinders.findFirstByInlineDatum(new DefaultUtxoSupplier(backendService.getUtxoService()),
                validator.getScriptAddress(), datum.toPlutusData());
        assertTrue(utxo.isPresent());

        //Unlock the contract
        var redeemer = new RedeemerData();
        redeemer.setMsg("Hello, World!".getBytes(StandardCharsets.UTF_8));

        var tx = validator
                .unlockToAddressTx(utxo.get(), redeemer, receiver1);

        var txResult2 = new QuickTxBuilder(backendService)
                .compose(tx)
                .feePayer(account.baseAddress())
                .withRequiredSigners(account.getBaseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .withReferenceScripts(validator.getPlutusScript())
                .completeAndWait(System.out::println);

        System.out.println(txResult2.getValue());
        System.out.println(txResult2.getResponse());

        assertTrue(txResult2.isSuccessful());
    }

    private void fullUnlockTxWithUtxo_toContractAddress(Owner datum) throws ApiException {
        var utxo = ScriptUtxoFinders.findFirstByInlineDatum(new DefaultUtxoSupplier(backendService.getUtxoService()),
                validator.getScriptAddress(), datum.toPlutusData());
        assertTrue(utxo.isPresent());

        //Unlock the contract
        var redeemer = new RedeemerData();
        redeemer.setMsg("Hello, World!".getBytes(StandardCharsets.UTF_8));

        var newOwner = new OwnerData();
        newOwner.setOwner(new Address(receiver1).getPaymentCredentialHash().get());

        var tx = validator
                .unlockToContractTx(utxo.get(), redeemer, validator.getScriptAddress(), newOwner.toPlutusData());

        var txResult2 = new QuickTxBuilder(backendService)
                .compose(tx)
                .feePayer(account.baseAddress())
                .withRequiredSigners(account.getBaseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .withReferenceScripts(validator.getPlutusScript())
                .completeAndWait(System.out::println);

        System.out.println(txResult2.getValue());
        System.out.println(txResult2.getResponse());

        assertTrue(txResult2.isSuccessful());
    }

    private void fullUnlockTx_toRegularAddress(Owner datum) throws ApiException {
        //Unlock the contract
        var redeemer = new RedeemerData();
        redeemer.setMsg("Hello, World!".getBytes(StandardCharsets.UTF_8));

        var tx = validator
                .unlockToAddressTx(datum, redeemer, receiver1);

        var tx2 = new Tx()
                .payToAddress(receiver2, Amount.ada(3.4))
                .from(account.baseAddress());

        //Create another Lock tx with account2, but same validator
        Account account2 = new Account(Networks.testnet(), mnemonic, DerivationPath.createExternalAddressDerivationPathForAccount(2));
        Owner owner2 = new OwnerData();
        owner2.setOwner(account2.getBaseAddress().getPaymentCredentialHash().get());

        var amount = Amount.ada(20);

        //Lock the contract
        var txResult1 = validator.lock(account2.baseAddress(), amount, owner2)
                .feePayer(account2.baseAddress())
                .withSigner(SignerProviders.signerFrom(account2))
                .completeAndWait(System.out::println);
        System.out.println(txResult1);

        var redeemer2 = new RedeemerData();
        redeemer2.setMsg("Hello, World!".getBytes(StandardCharsets.UTF_8));

        var tx3 = validator
                .unlockToAddressTx(owner2, redeemer2, receiver2);

        var txResult2 = new QuickTxBuilder(backendService)
                .compose(tx2, tx, tx3)
                .feePayer(account.baseAddress())
                .withRequiredSigners(account.getBaseAddress(), account2.getBaseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .withSigner(SignerProviders.signerFrom(account2))
                .withTxInspector((txn) -> {
                    System.out.println(JsonUtil.getPrettyJson(txn));
                })
                .completeAndWait(System.out::println);

        System.out.println(JsonUtil.getPrettyJson(txResult2));

        System.out.println(txResult2.getValue());
        System.out.println(txResult2.getResponse());

        assertTrue(txResult2.isSuccessful());
    }

    private void fullUnlockTx_toContractAddress(Owner datum) {

        //Unlock the contract
        var redeemer = new RedeemerData();
        redeemer.setMsg("Hello, World!".getBytes(StandardCharsets.UTF_8));

        var newOwner = new OwnerData();
        newOwner.setOwner(new Address(receiver1).getPaymentCredentialHash().get());

        var tx = validator
                .unlockToContractTx(datum, redeemer, validator.getScriptAddress(), newOwner);

        var txResult2 = new QuickTxBuilder(backendService)
                .compose(tx)
                .feePayer(account.baseAddress())
                .withRequiredSigners(account.getBaseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .withReferenceScripts(validator.getPlutusScript())
                .completeAndWait(System.out::println);

        System.out.println(txResult2.getValue());
        System.out.println(txResult2.getResponse());

        assertTrue(txResult2.isSuccessful());
    }
}

