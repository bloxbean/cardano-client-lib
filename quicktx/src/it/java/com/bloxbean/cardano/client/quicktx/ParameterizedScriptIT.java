package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.aiken.AikenScriptUtil;
import com.bloxbean.cardano.aiken.AikenTransactionEvaluator;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.coinselection.UtxoSelector;
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelector;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Optional;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mint Script - Gift Card V2 (Parameterized Contract)
 use aiken/dict
 use aiken/list
 use aiken/transaction.{OutputReference, ScriptContext, Transaction} as tx
 use aiken/transaction/value

 type Action {
 Mint
 Burn
 }

 validator(token_name: ByteArray, utxo_ref: OutputReference) {
 fn gift_card(rdmr: Action, ctx: ScriptContext) -> Bool {
 let ScriptContext { transaction, purpose } = ctx

 expect tx.Mint(policy_id) = purpose

 let Transaction { inputs, mint, .. } = transaction

 expect [(asset_name, amount)] =
 mint
 |> value.from_minted_value
 |> value.tokens(policy_id)
 |> dict.to_list()

 when rdmr is {
 Mint -> {
 expect Some(_input) =
 list.find(inputs, fn(input) { input.output_reference == utxo_ref })
 amount == 1 && asset_name == token_name
 }
 Burn ->
 amount == -1 && asset_name == token_name
 }
 }
 }

 **/

/** Hello World contract
 use aiken/hash.{Blake2b_224, Hash}
 use aiken/list
 use aiken/transaction.{ScriptContext}
 use aiken/transaction/credential.{VerificationKey}

 type Datum {
 owner: Hash<Blake2b_224, VerificationKey>,
 }

 type Redeemer {
 msg: ByteArray,
 }

 validator {
 fn hello_world(datum: Datum, redeemer: Redeemer, context: ScriptContext) -> Bool {
 let must_say_hello =
 redeemer.msg == "Hello, World!"

 let must_be_signed =
 list.has(context.transaction.extra_signatories, datum.owner)

 must_say_hello && must_be_signed
 }
 }

 ==================================================================================
 https://github.com/aiken-lang/aiken/blob/main/examples/gift_card/validators/oneshot.ak
 Mint Script - Gift Card V2 & V3 - Aiken Compiler v1.1.2 - StdLib 2.0.0 (Parameterized Contract)
 use aiken/collection/dict
 use aiken/collection/list
 use cardano/assets.{PolicyId}
 use cardano/credential.{Script}
 use cardano/transaction.{OutputReference, Transaction} as tx

 pub type Action {
 Mint
 Burn
 }

 validator gift_card(token_name: ByteArray, utxo_ref: OutputReference) {
 spend(_d, _r, own_ref: OutputReference, transaction: Transaction) {
 let Transaction { mint, inputs, .. } = transaction

 expect Some(own_input) =
 list.find(inputs, fn(input) { input.output_reference == own_ref })

 expect Script(policy_id) = own_input.output.address.payment_credential

 expect [Pair(asset_name, amount)] =
 mint
 |> assets.tokens(policy_id)
 |> dict.to_pairs()

 amount == -1 && asset_name == token_name
 }

 mint(rdmr: Action, policy_id: PolicyId, transaction: Transaction) {
 let Transaction { inputs, mint, .. } = transaction

 expect [Pair(asset_name, amount)] =
 mint
 |> assets.tokens(policy_id)
 |> dict.to_pairs()

 when rdmr is {
 Mint -> {
 expect Some(_input) =
 list.find(inputs, fn(input) { input.output_reference == utxo_ref })

 amount == 1 && asset_name == token_name
 }
 Burn -> amount == -1 && asset_name == token_name
 }
 }

 else(_) {
 fail
 }
 }
 **/
public class ParameterizedScriptIT extends TestDataBaseIT {

    private boolean aikenEvaluation = false;
    private final String giftCardAikenContractV2 = "590221010000323232323232323232323223222232533300b32323232533300f3370e9000180700089919191919191919191919299980e98100010991919299980e99b87480000044c94ccc078cdc3a4000603a002264a66603e66e1c011200213371e00a0322940c07000458c8cc004004030894ccc088004530103d87a80001323253330213375e6603a603e004900000d099ba548000cc0940092f5c0266008008002604c00460480022a66603a66e1c009200113371e00602e2940c06c050dd6980e8011bae301b00116301e001323232533301b3370e90010008a5eb7bdb1804c8dd59810800980c801180c800991980080080111299980f0008a6103d87a8000132323232533301f3371e01e004266e95200033023374c00297ae0133006006003375660400066eb8c078008c088008c080004c8cc004004008894ccc07400452f5bded8c0264646464a66603c66e3d221000021003133022337606ea4008dd3000998030030019bab301f003375c603a0046042004603e0026eacc070004c070004c06c004c068004c064008dd6180b80098078029bae3015001300d001163013001301300230110013009002149858c94ccc02ccdc3a40000022a66601c60120062930b0a99980599b874800800454ccc038c02400c52616163009002375c0026600200290001111199980399b8700100300c233330050053370000890011807000801001118029baa001230033754002ae6955ceaab9e5573eae815d0aba201";
    private final String giftCardAikenContractV2NewAiken = "5902750100003232323232323223222533300532323232323232323232532333010300500613232323232325333016300730173754002264a66602e601860306ea80044c8c94ccc070c07c0084c94ccc068cdc39bad301c002480044cdc780080c0a50375c60340022c603a002660160066eb8c070c064dd50008b1804980c1baa3009301837546036603860306ea8c06cc060dd50008b198039bac301a00223375e601260306ea8004014dd5980c980d180d180d180d000980a9baa00d3017301800230160013012375400e2a666020600200c2646464a66602660080022a66602c602a6ea802c540085854ccc04cc02000454ccc058c054dd50058a8010b0b18099baa00a1323232325333018301b00213232533301730083018375401e2a66602e601060306ea8cc0240148cdd79805980d1baa00101515333017300c00113371e00402a29405854ccc05ccdc3800a4002266e3c0080545281bad3018002375c602c0022c60320026600e6eacc060c064c064c064c06400800cdd6180b80098099baa00b375c602a60246ea801c58dc3a400044646600200200644a66602a0022980103d87a8000132325333014300500213374a90001980c00125eb804cc010010004c064008c05c0048c04c00488c94ccc03cc010c040dd50008a5eb7bdb1804dd5980a18089baa001323300100100322533301300114c103d87a800013232323253330143372200e0042a66602866e3c01c0084cdd2a4000660306e980052f5c02980103d87a80001330060060033756602a0066eb8c04c008c05c008c054004dc3a400460166ea8004c038c03c008c034004c034008c02c004c01cdd50008a4c26cac6eb80055cd2ab9d5573caae7d5d02ba15745";
    private final String giftCardAikenContractV3NewAiken = "5902750101003232323232323223222533300532323232323232323232532333010300500613232323232325333016300730173754002264a66602e601860306ea80044c8c94ccc070c07c0084c94ccc068cdc39bad301c002480044cdc780080c0a50375c60340022c603a002660160066eb8c070c064dd50008b1804980c1baa3009301837546036603860306ea8c06cc060dd50008b198039bac301a00223375e601260306ea8004014dd5980c980d180d180d180d000980a9baa00d3017301800230160013012375400e2a666020600200c2646464a66602660080022a66602c602a6ea802c540085854ccc04cc02000454ccc058c054dd50058a8010b0b18099baa00a1323232325333018301b00213232533301730083018375401e2a66602e601060306ea8cc0240148cdd79805980d1baa00101515333017300c00113371e00402a29405854ccc05ccdc3800a4002266e3c0080545281bad3018002375c602c0022c60320026600e6eacc060c064c064c064c06400800cdd6180b80098099baa00b375c602a60246ea801c58dc3a400044646600200200644a66602a0022980103d87a8000132325333014300500213374a90001980c00125eb804cc010010004c064008c05c0048c04c00488c94ccc03cc010c040dd50008a5eb7bdb1804dd5980a18089baa001323300100100322533301300114c103d87a800013232323253330143372200e0042a66602866e3c01c0084cdd2a4000660306e980052f5c02980103d87a80001330060060033756602a0066eb8c04c008c05c008c054004dc3a400460166ea8004c038c03c008c034004c034008c02c004c01cdd50008a4c26cac6eb80055cd2ab9d5573caae7d5d02ba15745";

    @Test
    void giftCardContractV2() throws ApiException {
        gitCardContract(giftCardAikenContractV2, PlutusVersion.v2);
    }
    @Test
    void giftCardContractV2NewAiken() throws ApiException {
        gitCardContract(giftCardAikenContractV2NewAiken, PlutusVersion.v2);
    }
    @Test
    void giftCardContractV3NewAiken() throws ApiException {
        gitCardContract(giftCardAikenContractV3NewAiken, PlutusVersion.v3);
    }

    void gitCardContract(String compiledContract, PlutusVersion version) throws ApiException {

        System.out.println("Sender Address: " + sender1Addr);
        UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoSupplier);
        Optional<Utxo> utxoOptional = utxoSelector.findFirst(sender1Addr, utxo -> utxo.getAmount().stream()
                .anyMatch(a -> LOVELACE.equals(a.getUnit()) && a.getQuantity().compareTo(adaToLovelace(2)) >= 0)); //Find an utxo with at least 2 ADA

        Utxo utxo = utxoOptional.orElseThrow();

        //Create Contract parameter
        String tokenName = "AikenJava";
        PlutusData outputRef =  ConstrPlutusData.of(0,
                ConstrPlutusData.of(0,
                        BytesPlutusData.of(HexUtil.decodeHexString(utxo.getTxHash()))),
                BigIntPlutusData.of(utxo.getOutputIndex()));
        ListPlutusData params = ListPlutusData.of(BytesPlutusData.of(tokenName), outputRef);

        //Apply param to script and get compiled code
        String compiledCode = AikenScriptUtil.applyParamToScript(params, compiledContract);
        //convert Aiken compiled code to PlutusScript
        PlutusScript giftPlutusScript = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(compiledCode, version);

        PlutusData mintAction = ConstrPlutusData.of(0);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(utxo)
                .mintAsset(giftPlutusScript, new Asset(tokenName, BigInteger.valueOf(1)), mintAction, sender1Addr);

        Result<String> result = new QuickTxBuilder(backendService)
                .compose(scriptTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxEvaluator(aikenEvaluation? new AikenTransactionEvaluator(backendService): null)
                .withTxInspector(transaction -> {
                    System.out.println(transaction);
                }).completeAndWait(System.out::println);

        System.out.println(result);
        assertTrue(result.isSuccessful());
    }

    @Test
    void gitCardContract_withHelloWorldContract() throws ApiException {
        String giftCardAikenContract = "590221010000323232323232323232323223222232533300b32323232533300f3370e9000180700089919191919191919191919299980e98100010991919299980e99b87480000044c94ccc078cdc3a4000603a002264a66603e66e1c011200213371e00a0322940c07000458c8cc004004030894ccc088004530103d87a80001323253330213375e6603a603e004900000d099ba548000cc0940092f5c0266008008002604c00460480022a66603a66e1c009200113371e00602e2940c06c050dd6980e8011bae301b00116301e001323232533301b3370e90010008a5eb7bdb1804c8dd59810800980c801180c800991980080080111299980f0008a6103d87a8000132323232533301f3371e01e004266e95200033023374c00297ae0133006006003375660400066eb8c078008c088008c080004c8cc004004008894ccc07400452f5bded8c0264646464a66603c66e3d221000021003133022337606ea4008dd3000998030030019bab301f003375c603a0046042004603e0026eacc070004c070004c06c004c068004c064008dd6180b80098078029bae3015001300d001163013001301300230110013009002149858c94ccc02ccdc3a40000022a66601c60120062930b0a99980599b874800800454ccc038c02400c52616163009002375c0026600200290001111199980399b8700100300c233330050053370000890011807000801001118029baa001230033754002ae6955ceaab9e5573eae815d0aba201";

        System.out.println("Sender Address: " + sender1Addr);
        UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoSupplier);
        Optional<Utxo> utxoOptional = utxoSelector.findFirst(sender1Addr, utxo -> utxo.getAmount().stream()
                .anyMatch(a -> LOVELACE.equals(a.getUnit()) && a.getQuantity().compareTo(adaToLovelace(2)) >= 0)); //Find an utxo with at least 2 ADA

        Utxo utxo = utxoOptional.orElseThrow();

        //Create Contract parameter
        String tokenName = "AikenJava";
        PlutusData outputRef =  ConstrPlutusData.of(0,
                ConstrPlutusData.of(0,
                        BytesPlutusData.of(HexUtil.decodeHexString(utxo.getTxHash()))),
                BigIntPlutusData.of(utxo.getOutputIndex()));
        ListPlutusData params = ListPlutusData.of(BytesPlutusData.of(tokenName), outputRef);

        //Apply param to script and get compiled code
        String compiledCode = AikenScriptUtil.applyParamToScript(params, giftCardAikenContract);
        //convert Aiken compiled code to PlutusScript
        PlutusScript giftPlutusScript = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(compiledCode, PlutusVersion.v2);

        //Hello World contract
        PlutusScript helloWorldContract =
                PlutusBlueprintUtil.getPlutusScriptFromCompiledCode("59010d010000323232323232323232322223232533300a3232533300c002100114a06644646600200200644a66602400229404c8c94ccc044cdc78010028a511330040040013015002375c60260026eb0cc01cc024cc01cc024011200048040dd71980398048032400066e3cdd71980318040022400091010d48656c6c6f2c20576f726c642100149858c94ccc028cdc3a400000226464a66601e60220042930b1bae300f00130080041630080033253330093370e900000089919299980718080010a4c2c6eb8c038004c01c01058c01c00ccc0040052000222233330073370e0020060164666600a00a66e000112002300d001002002230053754002460066ea80055cd2ab9d5573caae7d5d0aba21",
                        PlutusVersion.v2);

        String helloContractAddr = AddressProvider.getEntAddress(helloWorldContract, Networks.testnet()).toBech32();
        BigInteger scriptAmt = new BigInteger("2479280");

        ConstrPlutusData datum = ConstrPlutusData.of(0,
                BytesPlutusData.of(new Address(sender1Addr).getPaymentCredentialHash().get())
        );

        //Topup Hello World contract
        Tx tx = new Tx();
        tx.payToContract(helloContractAddr, Amount.lovelace(scriptAmt), datum)
                .from(sender2Addr);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);

        System.out.println(result.getResponse());
        checkIfUtxoAvailable(result.getValue(), helloContractAddr);
        String lockFundTxHash = result.getValue();

        Utxo lockedUtxo = utxoSupplier.getTxOutput(lockFundTxHash, 0).orElseThrow();

        //Combine both minting and Hello World unlock together
        PlutusData mintAction = ConstrPlutusData.of(0);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(utxo)
                .mintAsset(giftPlutusScript, new Asset(tokenName, BigInteger.valueOf(1)), mintAction, sender1Addr)
                .collectFrom(lockedUtxo, ConstrPlutusData.of(0, BytesPlutusData.of("Hello, World!")))
                .payToAddress(sender1Addr, Amount.lovelace(scriptAmt))
                .attachSpendingValidator(helloWorldContract)
                .withChangeAddress(helloContractAddr, datum);

        result = new QuickTxBuilder(backendService)
                .compose(scriptTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withRequiredSigners(sender1.getBaseAddress())
                .withTxEvaluator(aikenEvaluation? new AikenTransactionEvaluator(backendService): null)
//                .ignoreScriptCostEvaluationError(true)
                .withTxInspector(transaction -> {
                    System.out.println(transaction);
                }).completeAndWait(System.out::println);

        System.out.println(result);
        assertTrue(result.isSuccessful());
    }
}
