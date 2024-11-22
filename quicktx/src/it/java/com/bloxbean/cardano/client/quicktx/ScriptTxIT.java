package com.bloxbean.cardano.client.quicktx;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.aiken.AikenTransactionEvaluator;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.model.TxContentRedeemers;
import com.bloxbean.cardano.client.cip.cip68.CIP68FT;
import com.bloxbean.cardano.client.cip.cip68.CIP68NFT;
import com.bloxbean.cardano.client.cip.cip68.CIP68RFT;
import com.bloxbean.cardano.client.cip.cip68.CIP68ReferenceToken;
import com.bloxbean.cardano.client.cip.cip68.common.CIP68File;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.helper.ScriptUtxoFinders;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.util.JsonUtil;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**Topup following Addresses
 addr_test1qz5fcpvkg7pekqvv9ld03t5sx2w2c2fac67fzlaxw5844s83l4p6tr389lhgcpe4797kt7xkcxqvcc4a6qjshzsmta8sh3ncs4
 addr_test1qp73ljurtknpm5fgey5r2y9aympd33ksgw0f8rc5khheg83y35rncur9mjvs665cg4052985ry9rzzmqend9sqw0cdksxvefah
 addr_test1wqag3rt979nep9g2wtdwu8mr4gz6m4kjdpp5zp705km8wys6t2kla
 **/
public class ScriptTxIT extends TestDataBaseIT {

    private boolean aikenEvaluation = false;

    @Test
    void alwaysTrueScript_plutusV1() throws ApiException {
        PlutusV1Script plutusScript = PlutusV1Script.builder()
                .type("PlutusScriptV1")
                .cborHex("4e4d01000033222220051200120011")
                .build();

        String scriptAddress = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).toBech32();
        BigInteger scriptAmt = new BigInteger("2479280");

        Random rand = new Random();
        int randInt = rand.nextInt();
        BigIntPlutusData plutusData =  new BigIntPlutusData(BigInteger.valueOf(randInt)); //any random number

        Tx tx = new Tx();
        tx.payToContract(scriptAddress, Amount.lovelace(scriptAmt), plutusData.getDatumHash())
                .from(sender2Addr);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);

        System.out.println(result.getResponse());
        checkIfUtxoAvailable(result.getValue(), scriptAddress);

        Optional<Utxo> optionalUtxo  = ScriptUtxoFinders.findFirstByDatumHash(utxoSupplier, scriptAddress, plutusData.getDatumHash());
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(optionalUtxo.get(), plutusData, plutusData)
                .payToAddress(receiver1, Amount.lovelace(scriptAmt))
                .attachSpendingValidator(plutusScript)
                .withChangeAddress(scriptAddress, plutusData);

        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withRequiredSigners(sender1.getBaseAddress())
                .completeAndWait(System.out::println);

        System.out.println(result1.getResponse());
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), receiver1);

        // Example of getting the redeemer datum hash and then getting the datum values.
        List<TxContentRedeemers> redeemers = getBackendService().getTransactionService()
                .getTransactionRedeemers(result1.getValue()).getValue();

        int gottenValue = getBackendService().getScriptService()
                .getScriptDatum(redeemers.get(0).getRedeemerDataHash())
                .getValue().getJsonValue().get("int").asInt();
        assertThat(randInt).isEqualTo(gottenValue);
    }

    @Test
    void alwaysTrueScript_plutusV1_referenceInput() throws ApiException {
        PlutusV1Script plutusScript = PlutusV1Script.builder()
                .type("PlutusScriptV1")
                .cborHex("4e4d01000033222220051200120011")
                .build();

        String scriptAddress = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).toBech32();
        BigInteger scriptAmt = new BigInteger("2479280");

        Random rand = new Random();
        int randInt = rand.nextInt();
        BigIntPlutusData plutusData =  new BigIntPlutusData(BigInteger.valueOf(randInt)); //any random number

        //Create reference script and pay to contract
        Tx tx = new Tx();
        tx.payToAddress(receiver1, Amount.ada(1), plutusScript);
        tx.payToContract(scriptAddress, Amount.lovelace(scriptAmt), plutusData.getDatumHash())
                .from(sender2Addr);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);

        System.out.println(result.getResponse());
        checkIfUtxoAvailable(result.getValue(), scriptAddress);

        var refUtxo = utxoSupplier.getTxOutput(result.getValue(), 0);

        Optional<Utxo> optionalUtxo  = ScriptUtxoFinders.findFirstByDatumHash(utxoSupplier, scriptAddress, plutusData.getDatumHash());
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(optionalUtxo.get(), plutusData, plutusData)
                .readFrom(refUtxo.get())
                .payToAddress(receiver1, Amount.lovelace(scriptAmt))
                .withChangeAddress(scriptAddress, plutusData);

        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withRequiredSigners(sender1.getBaseAddress())
                .withSerializationEra(Era.Babbage)
                .completeAndWait(System.out::println);

        System.out.println(result1.getResponse());
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), receiver1);

        // Example of getting the redeemer datum hash and then getting the datum values.
        List<TxContentRedeemers> redeemers = getBackendService().getTransactionService()
                .getTransactionRedeemers(result1.getValue()).getValue();

        int gottenValue = getBackendService().getScriptService()
                .getScriptDatum(redeemers.get(0).getRedeemerDataHash())
                .getValue().getJsonValue().get("int").asInt();
        assertThat(randInt).isEqualTo(gottenValue);
    }

    @Test
    void alwaysTrueScript() throws ApiException {
        PlutusV2Script plutusScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        String scriptAddress = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).toBech32();
        BigInteger scriptAmt = new BigInteger("2479280");

        Random rand = new Random();
        int randInt = rand.nextInt();
        BigIntPlutusData plutusData =  new BigIntPlutusData(BigInteger.valueOf(randInt)); //any random number

        Tx tx = new Tx();
        tx.payToContract(scriptAddress, Amount.lovelace(scriptAmt), plutusData)
                .from(sender2Addr);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);

        System.out.println(result.getResponse());
        checkIfUtxoAvailable(result.getValue(), scriptAddress);

        Optional<Utxo> optionalUtxo  = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddress, plutusData);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(optionalUtxo.get(), plutusData)
                .payToAddress(receiver1, Amount.lovelace(scriptAmt))
                .attachSpendingValidator(plutusScript)
                .withChangeAddress(scriptAddress, plutusData);

        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withRequiredSigners(sender1.getBaseAddress())
                .withTxEvaluator(!backendType.equals(BLOCKFROST) && aikenEvaluation?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier): null)
                .withVerifier(txn -> {
                    assertThat(txn.getBody().getRequiredSigners()).hasSize(1);
                    assertThat(txn.getBody().getRequiredSigners().get(0)) //Verify sender's payment cred hash in required signer
                            .isEqualTo(sender1.getBaseAddress().getPaymentCredentialHash().get());
                })
                .completeAndWait(System.out::println);

        System.out.println(result1.getResponse());
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), sender2Addr);

    }

    @SneakyThrows
    @Test
    void alwaysTrueScript_cip68Minting() throws Exception {
        PlutusV2Script mintingScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        CIP68NFT nft = CIP68NFT.create()
                .name("CIP68-NFT-" + System.currentTimeMillis())
                .image("https://xyz.com/image1.png")
                .description("This is my first CIP-68 NFT")
                .addFile(CIP68File.create()
                        .mediaType("image/png")
                        .name("image1.png")
                        .src("https://xyz.com/image1.png")
                )
                .property("key1", "key1Value")
                .property("key2", List.of("key2Value", "key2Value2"))
                .property("key5", "key5Value");

        nft.getDatum()
                .extra(BytesPlutusData.of("Extra text"));

        System.out.println(nft.toJson());

        CIP68ReferenceToken referenceToken = nft.getReferenceToken();
        Asset userToken = nft.getAsset(BigInteger.valueOf(1));
        Asset referenzToken = referenceToken.getAsset(BigInteger.valueOf(1));

        PlutusData asPlutusData = referenceToken.getDatumAsPlutusData();

        System.out.println(asPlutusData.serializeToHex());

        String userTokenReceiver = sender2Addr;
        String referenceTokenReceiver = AddressProvider.getEntAddress(mintingScript, Networks.preprod()).toBech32();

        ScriptTx scriptTx = new ScriptTx()
                .mintAsset(mintingScript, List.of(referenzToken), PlutusData.unit(), referenceTokenReceiver, asPlutusData)
                .mintAsset(mintingScript, List.of(userToken),PlutusData.unit(), userTokenReceiver);
        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(sender2Addr)
                .withSigner(SignerProviders.signerFrom(sender2))
                .withTxEvaluator(!backendType.equals(BLOCKFROST) && aikenEvaluation?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier): null)
                .completeAndWait(System.out::println);

        System.out.println(result1.getResponse());
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), sender2Addr);
    }

    @SneakyThrows
    @Test
    void alwaysTrueScript_cip68FTMinting() throws Exception {
        PlutusV2Script mintingScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        CIP68FT ft = CIP68FT.create()
                .name("CIP68-FT-" + System.currentTimeMillis())
                .ticker("GYU")
                .url("https://xyz.com")
                .logo("https://xyz.com/logo.png")
                .decimals(6)
                .description("This is my first CIP-68 FT")
                .property("key1", "key1Value");

        ft.getDatum()
                .extra(BytesPlutusData.of("Extra text"));

        System.out.println(ft.toJson());

        CIP68ReferenceToken referenceToken = ft.getReferenceToken();
        Asset userToken = ft.getAsset(BigInteger.valueOf(1000000000));
        Asset referenzToken = referenceToken.getAsset(BigInteger.valueOf(1));

        PlutusData asPlutusData = referenceToken.getDatumAsPlutusData();

        System.out.println(asPlutusData.serializeToHex());

        String userTokenReceiver = sender2Addr;
        String referenceTokenReceiver = AddressProvider.getEntAddress(mintingScript, Networks.preprod()).toBech32();

        ScriptTx scriptTx = new ScriptTx()
                .mintAsset(mintingScript, List.of(referenzToken), PlutusData.unit(), referenceTokenReceiver, asPlutusData)
                .mintAsset(mintingScript, List.of(userToken),PlutusData.unit(), userTokenReceiver);
        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(sender2Addr)
                .withSigner(SignerProviders.signerFrom(sender2))
                .withTxEvaluator(!backendType.equals(BLOCKFROST) && aikenEvaluation?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier): null)
                .completeAndWait(System.out::println);

        System.out.println(result1.getResponse());
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), sender2Addr);
    }

    @SneakyThrows
    @Test
    void alwaysTrueScript_cip68RFT() {
        PlutusV2Script mintingScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        CIP68RFT rft = CIP68RFT.create()
                .name("CIP68-RFT-" + System.currentTimeMillis())
                .image("https://xyz.com/image1.png")
                .description("This is my first CIP-68 NFT")
                .addFile(CIP68File.create()
                        .mediaType("image/png")
                        .name("image1.png")
                        .src("https://xyz.com/image1.png")
                )
                .property("key1", "key1Value")
                .property("key2", List.of("key2Value", "key2Value2"))
                .property("key5", "key5Value");

        rft.getDatum()
                .extra(BytesPlutusData.of("Extra text"));

        System.out.println(rft.toJson());

        CIP68ReferenceToken referenceToken = rft.getReferenceToken();
        Asset userToken = rft.getAsset(BigInteger.valueOf(1));
        Asset referenzToken = referenceToken.getAsset(BigInteger.valueOf(1));

        PlutusData asPlutusData = referenceToken.getDatumAsPlutusData();

        System.out.println(asPlutusData.serializeToHex());

        String userTokenReceiver = sender2Addr;
        String referenceTokenReceiver = AddressProvider.getEntAddress(mintingScript, Networks.preprod()).toBech32();

        ScriptTx scriptTx = new ScriptTx()
                .mintAsset(mintingScript, List.of(referenzToken), PlutusData.unit(), referenceTokenReceiver, asPlutusData)
                .mintAsset(mintingScript, List.of(userToken),PlutusData.unit(), userTokenReceiver);
        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(sender2Addr)
                .withSigner(SignerProviders.signerFrom(sender2))
                .withTxEvaluator(!backendType.equals(BLOCKFROST) && aikenEvaluation?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier): null)
                .completeAndWait(System.out::println);

        System.out.println(result1.getResponse());
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), sender2Addr);
    }


    @Test
    void alwaysTrueScript_withRegularPayment() throws ApiException, InterruptedException {
        PlutusV2Script plutusScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        String scriptAddress = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).toBech32();
        BigInteger scriptAmt = new BigInteger("2479280");

        Random rand = new Random();
        int randInt = rand.nextInt();
        BigIntPlutusData plutusData =  new BigIntPlutusData(BigInteger.valueOf(randInt)); //any random number

        Tx tx = new Tx();
        tx.payToContract(scriptAddress, Amount.lovelace(scriptAmt), plutusData)
            .payToContract(scriptAddress, Amount.ada(2), plutusData)
            .payToContract(scriptAddress, Amount.ada(3), plutusData)
            .payToContract(scriptAddress, Amount.ada(4), plutusData)
            .from(sender2Addr);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder.compose(tx)
                .mergeOutputs(false)
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);

        System.out.println(result.getResponse());
        checkIfUtxoAvailable(result.getValue(), scriptAddress);

        Tx tx1 = new Tx()
                .payToAddress(receiver3, Amount.ada(1))
                .from(sender1Addr);

        List<Utxo> utxos  = ScriptUtxoFinders.findAllByInlineDatum(utxoSupplier, scriptAddress, plutusData);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(utxos, plutusData)
                .payToAddress(receiver1, Amount.lovelace(scriptAmt))
                .payToAddress(receiver1, Amount.ada(2))
                .payToAddress(receiver1, Amount.ada(3))
                .payToAddress(receiver1, Amount.ada(4))
                .attachSpendingValidator(plutusScript)
                .withChangeAddress(scriptAddress, plutusData);

        Result<String> result1 = quickTxBuilder.compose(tx1, scriptTx)
                .feePayer(sender1Addr)
                .mergeOutputs(false)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxEvaluator(!backendType.equals(BLOCKFROST) && aikenEvaluation?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier): null)
                .completeAndWait(System.out::println);

        System.out.println(result1.getResponse());
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), sender1Addr);
    }


    @Test
    void alwaysTrueScript_guessSumScript() throws ApiException, InterruptedException {
        PlutusV2Script alwaysTrueScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        String alwaysTrueScriptAddr = AddressProvider.getEntAddress(alwaysTrueScript, Networks.testnet()).toBech32();
        Amount alwaysTrueScriptAmt = Amount.lovelace(BigInteger.valueOf(2479280));

        Random rand = new Random();
        int randInt = rand.nextInt();
        BigIntPlutusData alwaysTruePlutusData =  new BigIntPlutusData(BigInteger.valueOf(randInt)); //any random number

        //Sum Script
        PlutusV2Script sumScript =
                PlutusV2Script.builder()
                        .cborHex("5907a65907a3010000323322323232323232323232323232323322323232323222232325335323232333573466e1ccc07000d200000201e01d3333573466e1cd55cea80224000466442466002006004646464646464646464646464646666ae68cdc39aab9d500c480008cccccccccccc88888888888848cccccccccccc00403403002c02802402001c01801401000c008cd405c060d5d0a80619a80b80c1aba1500b33501701935742a014666aa036eb94068d5d0a804999aa80dbae501a35742a01066a02e0446ae85401cccd5406c08dd69aba150063232323333573466e1cd55cea801240004664424660020060046464646666ae68cdc39aab9d5002480008cc8848cc00400c008cd40b5d69aba15002302e357426ae8940088c98c80c0cd5ce01881801709aab9e5001137540026ae854008c8c8c8cccd5cd19b8735573aa004900011991091980080180119a816bad35742a004605c6ae84d5d1280111931901819ab9c03103002e135573ca00226ea8004d5d09aba2500223263202c33573805a05805426aae7940044dd50009aba1500533501775c6ae854010ccd5406c07c8004d5d0a801999aa80dbae200135742a00460426ae84d5d1280111931901419ab9c029028026135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d55cf280089baa00135742a00860226ae84d5d1280211931900d19ab9c01b01a018375a00a6eb4014405c4c98c805ccd5ce24810350543500017135573ca00226ea800448c88c008dd6000990009aa80b911999aab9f0012500a233500930043574200460066ae880080508c8c8cccd5cd19b8735573aa004900011991091980080180118061aba150023005357426ae8940088c98c8050cd5ce00a80a00909aab9e5001137540024646464646666ae68cdc39aab9d5004480008cccc888848cccc00401401000c008c8c8c8cccd5cd19b8735573aa0049000119910919800801801180a9aba1500233500f014357426ae8940088c98c8064cd5ce00d00c80b89aab9e5001137540026ae854010ccd54021d728039aba150033232323333573466e1d4005200423212223002004357426aae79400c8cccd5cd19b875002480088c84888c004010dd71aba135573ca00846666ae68cdc3a801a400042444006464c6403666ae7007006c06406005c4d55cea80089baa00135742a00466a016eb8d5d09aba2500223263201533573802c02a02626ae8940044d5d1280089aab9e500113754002266aa002eb9d6889119118011bab00132001355014223233335573e0044a010466a00e66442466002006004600c6aae754008c014d55cf280118021aba200301213574200222440042442446600200800624464646666ae68cdc3a800a40004642446004006600a6ae84d55cf280191999ab9a3370ea0049001109100091931900819ab9c01101000e00d135573aa00226ea80048c8c8cccd5cd19b875001480188c848888c010014c01cd5d09aab9e500323333573466e1d400920042321222230020053009357426aae7940108cccd5cd19b875003480088c848888c004014c01cd5d09aab9e500523333573466e1d40112000232122223003005375c6ae84d55cf280311931900819ab9c01101000e00d00c00b135573aa00226ea80048c8c8cccd5cd19b8735573aa004900011991091980080180118029aba15002375a6ae84d5d1280111931900619ab9c00d00c00a135573ca00226ea80048c8cccd5cd19b8735573aa002900011bae357426aae7940088c98c8028cd5ce00580500409baa001232323232323333573466e1d4005200c21222222200323333573466e1d4009200a21222222200423333573466e1d400d2008233221222222233001009008375c6ae854014dd69aba135744a00a46666ae68cdc3a8022400c4664424444444660040120106eb8d5d0a8039bae357426ae89401c8cccd5cd19b875005480108cc8848888888cc018024020c030d5d0a8049bae357426ae8940248cccd5cd19b875006480088c848888888c01c020c034d5d09aab9e500b23333573466e1d401d2000232122222223005008300e357426aae7940308c98c804ccd5ce00a00980880800780700680600589aab9d5004135573ca00626aae7940084d55cf280089baa0012323232323333573466e1d400520022333222122333001005004003375a6ae854010dd69aba15003375a6ae84d5d1280191999ab9a3370ea0049000119091180100198041aba135573ca00c464c6401866ae700340300280244d55cea80189aba25001135573ca00226ea80048c8c8cccd5cd19b875001480088c8488c00400cdd71aba135573ca00646666ae68cdc3a8012400046424460040066eb8d5d09aab9e500423263200933573801401200e00c26aae7540044dd500089119191999ab9a3370ea00290021091100091999ab9a3370ea00490011190911180180218031aba135573ca00846666ae68cdc3a801a400042444004464c6401466ae7002c02802001c0184d55cea80089baa0012323333573466e1d40052002200923333573466e1d40092000200923263200633573800e00c00800626aae74dd5000a4c240029210350543100320013550032225335333573466e1c0092000005004100113300333702004900119b80002001122002122001112323001001223300330020020011")
                        .build();
        String sumScriptAddr = AddressProvider.getEntAddress(sumScript, Networks.testnet()).toBech32();
        Amount sumScriptAmt = Amount.ada(4.0);
        PlutusData sumScriptDatum =  new BigIntPlutusData(BigInteger.valueOf(8)); //redeemer should be 36
        PlutusData sumScriptRedeemer = new BigIntPlutusData(BigInteger.valueOf(36));

        Tx tx = new Tx();
        tx.payToContract(alwaysTrueScriptAddr, alwaysTrueScriptAmt, alwaysTruePlutusData)
                .payToContract(sumScriptAddr, sumScriptAmt, sumScriptDatum)
                .from(sender2Addr);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);

        System.out.println(result.getResponse());
        if (result.isSuccessful())
            checkIfUtxoAvailable(result.getValue(), alwaysTrueScriptAddr);

        Optional<Utxo> alwaysTrueUtxo  = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, alwaysTrueScriptAddr, alwaysTruePlutusData);
        Optional<Utxo> sumUtxo  = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, sumScriptAddr, sumScriptDatum);
//        Optional<Utxo> alwaysTrueUtxo = utxoSupplier.getTxOutput(result.getValue(), 0);
//        Optional<Utxo> sumUtxo = utxoSupplier.getTxOutput(result.getValue(), 1);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(alwaysTrueUtxo.get(), PlutusData.unit())
                .collectFrom(sumUtxo.get(), sumScriptRedeemer)
                .payToAddress(receiver1, List.of(alwaysTrueScriptAmt, sumScriptAmt))
                .attachSpendingValidator(alwaysTrueScript)
                .attachSpendingValidator(sumScript)
                .withChangeAddress(sumScriptAddr, sumScriptDatum);

        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxEvaluator(!backendType.equals(BLOCKFROST) && aikenEvaluation?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier): null)
                .completeAndWait(System.out::println);

        System.out.println(result1.getResponse());
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), sender1Addr);
    }

    @Test
    void alwaysTrueScript_withFeeFromChange_differentCollateral() throws ApiException {
        PlutusV2Script plutusScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        String scriptAddress = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).toBech32();
        BigInteger scriptAmt = new BigInteger("3479280");

        Random rand = new Random();
        int randInt = rand.nextInt();
        BigIntPlutusData plutusData =  new BigIntPlutusData(BigInteger.valueOf(randInt)); //any random number

        Tx tx = new Tx();
        tx.payToContract(scriptAddress, Amount.lovelace(scriptAmt), plutusData)
                .from(sender2Addr);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);

        System.out.println(result.getResponse());

        Optional<Utxo> optionalUtxo  = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddress, plutusData);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(optionalUtxo.get(), plutusData)
                .payToAddress(receiver1, Amount.ada(1.1))
                .attachSpendingValidator(plutusScript)
                .withChangeAddress(scriptAddress, plutusData);

        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(scriptAddress)
                .collateralPayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxEvaluator(!backendType.equals(BLOCKFROST) && aikenEvaluation?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier): null)
                .completeAndWait(System.out::println);

        System.out.println(result1.getResponse());
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), sender1Addr);
    }

    @Test
    void alwaysTrueScript_minting() throws ApiException {
        PlutusV2Script mintingScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        PlutusV2Script spendingScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        String scriptAddress = AddressProvider.getEntAddress(spendingScript, Networks.testnet()).toBech32();
        BigInteger scriptAmt = new BigInteger("2479280");

        Random rand = new Random();
        int randInt = rand.nextInt();
        BigIntPlutusData plutusData =  new BigIntPlutusData(BigInteger.valueOf(randInt)); //any random number

        Tx tx = new Tx();
        tx.payToContract(scriptAddress, Amount.lovelace(scriptAmt), plutusData)
                .from(sender2Addr);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);

        System.out.println(result.getResponse());

        //Required as backend service returns outdated utxo
        if (result.isSuccessful()) {
            checkIfUtxoAvailable(result.getValue(), sender2Addr);
        }

        System.out.println("Script Addr: " + scriptAddress);
        System.out.println("Datum: " + randInt);
        Optional<Utxo> spendingUtxoOptional  = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddress, plutusData);

        Asset asset = new Asset("PlutusMintToken", BigInteger.valueOf(4000));

        String aikenCompiledCode1 = "581801000032223253330043370e00290010a4c2c6eb40095cd1"; //redeemer = 1
        PlutusScript plutusScript1 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompiledCode1, PlutusVersion.v2);

        String aikenCompileCode2 = "581801000032223253330043370e00290020a4c2c6eb40095cd1"; //redeemer = 2
        PlutusScript plutusScript2 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompileCode2, PlutusVersion.v2);

        String aikenCompileCode3 = "581801000032223253330043370e00290030a4c2c6eb40095cd1";
        PlutusScript plutusScript3 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompileCode3, PlutusVersion.v2);

        Asset asset1 = new Asset("PlutusMintToken-1", BigInteger.valueOf(8000));
        Asset asset2 = new Asset("PlutusMintToken-2", BigInteger.valueOf(5000));
        Asset asset3 = new Asset("PlutusMintToken-3", BigInteger.valueOf(2000));

        ScriptTx scriptTx = new ScriptTx()
              .payToAddress(receiver2, Amount.lovelace(scriptAmt))
                .collectFrom(spendingUtxoOptional.get(), plutusData)
                .mintAsset(mintingScript, asset, PlutusData.unit(), sender2Addr)
                .mintAsset(plutusScript1, asset1, BigIntPlutusData.of(1), receiver1)
                .mintAsset(plutusScript2, asset2, BigIntPlutusData.of(2), sender1Addr)
                .mintAsset(plutusScript3, asset3, BigIntPlutusData.of(3), receiver1)
                .attachSpendingValidator(spendingScript)
                .withChangeAddress(sender2Addr);

        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(sender2Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(sender2))
                .mergeOutputs(false)
                .withTxInspector(txn -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .withTxEvaluator(!backendType.equals(BLOCKFROST) && aikenEvaluation?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier): null)
                .completeAndWait(System.out::println);

        System.out.println(result1.getResponse());
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), sender2Addr);
    }

    @Test
    void alwaysTrueScript_minting_multipleReceiverNewTokens() throws ApiException, CborSerializationException, InterruptedException {
        PlutusV2Script mintingScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        PlutusV2Script spendingScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        String aikenCompiledCode2 = "581801000032223253330043370e00290010a4c2c6eb40095cd1"; //redeemer = 1
        PlutusScript mintingScript2 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompiledCode2, PlutusVersion.v2);

        String aikenCompileCode3 = "581801000032223253330043370e00290020a4c2c6eb40095cd1"; //redeemer = 2
        PlutusScript mintingScript3 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompileCode3, PlutusVersion.v2);

        String scriptAddress = AddressProvider.getEntAddress(spendingScript, Networks.testnet()).toBech32();
        BigInteger scriptAmt = new BigInteger("2479280");

        Random rand = new Random();
        int randInt = rand.nextInt();
        BigIntPlutusData plutusData =  new BigIntPlutusData(BigInteger.valueOf(randInt)); //any random number

        Tx tx = new Tx();
        tx.payToContract(scriptAddress, Amount.lovelace(scriptAmt), plutusData)
                .from(sender2Addr);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);

        System.out.println(result.getResponse());

        //Required as backend service returns outdated utxo
        if (result.isSuccessful()) {
            checkIfUtxoAvailable(result.getValue(), sender2Addr);
        }

        System.out.println("Script Addr: " + scriptAddress);
        System.out.println("Datum: " + randInt);
        Optional<Utxo> spendingUtxoOptional  = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddress, plutusData);

        Asset asset = new Asset(getRandomTokenName(), BigInteger.valueOf(4000));
        String policyId = mintingScript.getPolicyId();

        Asset asset2 = new Asset("PlutusMintToken-2", BigInteger.valueOf(5000));
        Asset asset3 = new Asset("PlutusMintToken-3", BigInteger.valueOf(6000));

        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(spendingUtxoOptional.get(), plutusData)
                .attachSpendingValidator(spendingScript)
                .payToAddress(receiver2, Amount.lovelace(scriptAmt))
                .mintAsset(mintingScript, asset, PlutusData.unit())
                .mintAsset(mintingScript2, asset2, BigIntPlutusData.of(1), receiver1)
                .mintAsset(mintingScript3, asset3, BigIntPlutusData.of(2), sender1Addr)
                .payToAddress(receiver1, Amount.asset(policyId, asset.getName(), BigInteger.valueOf(1000)))
                .payToAddress(receiver3, Amount.asset(policyId, asset.getName(), BigInteger.valueOf(2500)))
                .payToContract(scriptAddress, List.of(Amount.asset(policyId, asset.getName(), BigInteger.valueOf(200))), plutusData)
                .payToContract(scriptAddress, List.of(Amount.asset(policyId, asset.getName(), BigInteger.valueOf(300))), plutusData);

        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(sender2Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(sender2))
                .withTxEvaluator(!backendType.equals(BLOCKFROST) && aikenEvaluation?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier): null)
                .mergeOutputs(false)
                .completeAndWait(System.out::println);

        System.out.println(result1.getResponse());
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), sender2Addr);
    }

    @Test
    void alwaysTrueScript_minting_multipleReceiverNewTokens_with_payMintTokenToContract()
            throws ApiException, CborSerializationException, CborException {
        PlutusV2Script mintingScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        PlutusV2Script spendingScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        String aikenCompiledCode2 = "581801000032223253330043370e00290010a4c2c6eb40095cd1"; //redeemer = 1
        PlutusScript mintingScript2 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompiledCode2, PlutusVersion.v2);

        String aikenCompileCode3 = "581801000032223253330043370e00290020a4c2c6eb40095cd1"; //redeemer = 2
        PlutusScript mintingScript3 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompileCode3, PlutusVersion.v2);

        String scriptAddress = AddressProvider.getEntAddress(spendingScript, Networks.testnet()).toBech32();
        BigInteger scriptAmt = new BigInteger("2479280");

        Random rand = new Random();
        int randInt = rand.nextInt();
        BigIntPlutusData plutusData =  new BigIntPlutusData(BigInteger.valueOf(randInt)); //any random number

        Tx tx = new Tx();
        tx.payToContract(scriptAddress, Amount.lovelace(scriptAmt), plutusData)
                .from(sender2Addr);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);

        System.out.println(result.getResponse());

        //Required as backend service returns outdated utxo
        if (result.isSuccessful()) {
            checkIfUtxoAvailable(result.getValue(), sender2Addr);
        }

        System.out.println("Script Addr: " + scriptAddress);
        System.out.println("Datum: " + randInt);
        Optional<Utxo> spendingUtxoOptional  = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddress, plutusData);

        Asset asset = new Asset(getRandomTokenName(), BigInteger.valueOf(4000));
        String policyId = mintingScript.getPolicyId();

        Asset asset2 = new Asset("PlutusMintToken-2", BigInteger.valueOf(5000));
        Asset asset3 = new Asset("PlutusMintToken-3", BigInteger.valueOf(6000));

        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(spendingUtxoOptional.get(), plutusData)
                .attachSpendingValidator(spendingScript)
                .payToAddress(receiver2, Amount.lovelace(scriptAmt))
                .mintAsset(mintingScript, asset, PlutusData.unit())
                .mintAsset(mintingScript2, asset2, BigIntPlutusData.of(1), receiver1)
                .mintAsset(mintingScript3, asset3, BigIntPlutusData.of(2), sender1Addr)
                .payToAddress(receiver1, Amount.asset(policyId, asset.getName(), BigInteger.valueOf(1000)))
                .payToAddress(receiver3, Amount.asset(policyId, asset.getName(), BigInteger.valueOf(2500)))
                .payToContract(scriptAddress, Amount.asset(policyId, asset.getName(), BigInteger.valueOf(100)), plutusData)
                .payToContract("addr_test1wr297svp7eth4y2qd356a042gwn3th93j93843sa3hgm5lcgc3gkc",
                        Amount.asset(policyId, asset.getName(), BigInteger.valueOf(100)), plutusData.getDatumHash())
                .payToContract(scriptAddress, List.of(Amount.asset(policyId, asset.getName(), BigInteger.valueOf(300))), plutusData);

        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(sender2Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(sender2))
                .withTxEvaluator(!backendType.equals(BLOCKFROST) && aikenEvaluation?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier): null)
                .mergeOutputs(false)
                .completeAndWait(System.out::println);

        System.out.println(result1.getResponse());
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), sender2Addr);
    }

    @Test
    void multi_minting() throws ApiException, CborSerializationException, InterruptedException {
        String aikenCompiledCode1 = "581801000032223253330043370e00290010a4c2c6eb40095cd1"; //redeemer = 1
        PlutusScript plutusScript1 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompiledCode1, PlutusVersion.v2);

        String aikenCompileCode2 = "581801000032223253330043370e00290020a4c2c6eb40095cd1"; //redeemer = 2
        PlutusScript plutusScript2 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompileCode2, PlutusVersion.v2);

        String aikenCompileCode3 = "581801000032223253330043370e00290030a4c2c6eb40095cd1";
        PlutusScript plutusScript3 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompileCode3, PlutusVersion.v2);

        Asset asset1 = new Asset("PlutusMintToken-1", BigInteger.valueOf(8000));
        Asset asset2 = new Asset("PlutusMintToken-2", BigInteger.valueOf(5000));
        Asset asset3 = new Asset("PlutusMintToken-3", BigInteger.valueOf(2000));

        System.out.println("policy 1: " + plutusScript1.getPolicyId());
        System.out.println("policy 2: " + plutusScript2.getPolicyId());

        ScriptTx scriptTx = new ScriptTx()
                .mintAsset(plutusScript1, asset1, BigIntPlutusData.of(1), receiver1)
                .mintAsset(plutusScript2, asset2, BigIntPlutusData.of(2), sender1Addr)
                .mintAsset(plutusScript3, asset3, BigIntPlutusData.of(3), receiver1)
                .withChangeAddress(sender2Addr);

        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(sender2Addr)
                .withSigner(SignerProviders.signerFrom(sender2))
                .mergeOutputs(false)
                .withTxInspector(tx -> System.out.println(JsonUtil.getPrettyJson(tx)))
                .withTxEvaluator(!backendType.equals(BLOCKFROST) && aikenEvaluation?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier): null)
                .completeAndWait(System.out::println);

        System.out.println(result1.getResponse());
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), sender2Addr);
    }

    @Test
    void referenceInputUtxo_guessSumScript() throws ApiException, InterruptedException {
        //Sum Script
        PlutusV2Script sumScript =
                PlutusV2Script.builder()
                        .cborHex("5907a65907a3010000323322323232323232323232323232323322323232323222232325335323232333573466e1ccc07000d200000201e01d3333573466e1cd55cea80224000466442466002006004646464646464646464646464646666ae68cdc39aab9d500c480008cccccccccccc88888888888848cccccccccccc00403403002c02802402001c01801401000c008cd405c060d5d0a80619a80b80c1aba1500b33501701935742a014666aa036eb94068d5d0a804999aa80dbae501a35742a01066a02e0446ae85401cccd5406c08dd69aba150063232323333573466e1cd55cea801240004664424660020060046464646666ae68cdc39aab9d5002480008cc8848cc00400c008cd40b5d69aba15002302e357426ae8940088c98c80c0cd5ce01881801709aab9e5001137540026ae854008c8c8c8cccd5cd19b8735573aa004900011991091980080180119a816bad35742a004605c6ae84d5d1280111931901819ab9c03103002e135573ca00226ea8004d5d09aba2500223263202c33573805a05805426aae7940044dd50009aba1500533501775c6ae854010ccd5406c07c8004d5d0a801999aa80dbae200135742a00460426ae84d5d1280111931901419ab9c029028026135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d55cf280089baa00135742a00860226ae84d5d1280211931900d19ab9c01b01a018375a00a6eb4014405c4c98c805ccd5ce24810350543500017135573ca00226ea800448c88c008dd6000990009aa80b911999aab9f0012500a233500930043574200460066ae880080508c8c8cccd5cd19b8735573aa004900011991091980080180118061aba150023005357426ae8940088c98c8050cd5ce00a80a00909aab9e5001137540024646464646666ae68cdc39aab9d5004480008cccc888848cccc00401401000c008c8c8c8cccd5cd19b8735573aa0049000119910919800801801180a9aba1500233500f014357426ae8940088c98c8064cd5ce00d00c80b89aab9e5001137540026ae854010ccd54021d728039aba150033232323333573466e1d4005200423212223002004357426aae79400c8cccd5cd19b875002480088c84888c004010dd71aba135573ca00846666ae68cdc3a801a400042444006464c6403666ae7007006c06406005c4d55cea80089baa00135742a00466a016eb8d5d09aba2500223263201533573802c02a02626ae8940044d5d1280089aab9e500113754002266aa002eb9d6889119118011bab00132001355014223233335573e0044a010466a00e66442466002006004600c6aae754008c014d55cf280118021aba200301213574200222440042442446600200800624464646666ae68cdc3a800a40004642446004006600a6ae84d55cf280191999ab9a3370ea0049001109100091931900819ab9c01101000e00d135573aa00226ea80048c8c8cccd5cd19b875001480188c848888c010014c01cd5d09aab9e500323333573466e1d400920042321222230020053009357426aae7940108cccd5cd19b875003480088c848888c004014c01cd5d09aab9e500523333573466e1d40112000232122223003005375c6ae84d55cf280311931900819ab9c01101000e00d00c00b135573aa00226ea80048c8c8cccd5cd19b8735573aa004900011991091980080180118029aba15002375a6ae84d5d1280111931900619ab9c00d00c00a135573ca00226ea80048c8cccd5cd19b8735573aa002900011bae357426aae7940088c98c8028cd5ce00580500409baa001232323232323333573466e1d4005200c21222222200323333573466e1d4009200a21222222200423333573466e1d400d2008233221222222233001009008375c6ae854014dd69aba135744a00a46666ae68cdc3a8022400c4664424444444660040120106eb8d5d0a8039bae357426ae89401c8cccd5cd19b875005480108cc8848888888cc018024020c030d5d0a8049bae357426ae8940248cccd5cd19b875006480088c848888888c01c020c034d5d09aab9e500b23333573466e1d401d2000232122222223005008300e357426aae7940308c98c804ccd5ce00a00980880800780700680600589aab9d5004135573ca00626aae7940084d55cf280089baa0012323232323333573466e1d400520022333222122333001005004003375a6ae854010dd69aba15003375a6ae84d5d1280191999ab9a3370ea0049000119091180100198041aba135573ca00c464c6401866ae700340300280244d55cea80189aba25001135573ca00226ea80048c8c8cccd5cd19b875001480088c8488c00400cdd71aba135573ca00646666ae68cdc3a8012400046424460040066eb8d5d09aab9e500423263200933573801401200e00c26aae7540044dd500089119191999ab9a3370ea00290021091100091999ab9a3370ea00490011190911180180218031aba135573ca00846666ae68cdc3a801a400042444004464c6401466ae7002c02802001c0184d55cea80089baa0012323333573466e1d40052002200923333573466e1d40092000200923263200633573800e00c00800626aae74dd5000a4c240029210350543100320013550032225335333573466e1c0092000005004100113300333702004900119b80002001122002122001112323001001223300330020020011")
                        .build();
        String sumScriptAddr = AddressProvider.getEntAddress(sumScript, Networks.testnet()).toBech32();
        Amount sumScriptAmt = Amount.ada(4.0);
        PlutusData sumScriptDatum =  new BigIntPlutusData(BigInteger.valueOf(8)); //redeemer should be 36
        PlutusData sumScriptRedeemer = new BigIntPlutusData(BigInteger.valueOf(36));

        //Create a reference input and send lock amount at script address
        Tx createRefInputTx = new Tx();
        createRefInputTx.payToAddress(receiver1, List.of(Amount.ada(1.0)), sumScript)
                .payToContract(sumScriptAddr, sumScriptAmt, sumScriptDatum)
                .from(sender1Addr);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder.compose(createRefInputTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);
        System.out.println("Tx Response: " + result.getResponse());
        assertTrue(result.isSuccessful());

        //Required as backend service returns outdated utxo
        if (result.isSuccessful()) {
            checkIfUtxoAvailable(result.getValue(), sender1Addr);
        }

        Utxo refUtxo = Utxo.builder()
                .txHash(result.getValue())
                .outputIndex(0)
                .build();

        //Pay to script
        Tx scriptPayTx = new Tx();
        scriptPayTx.payToContract(sumScriptAddr, sumScriptAmt, sumScriptDatum)
                .from(sender1Addr);
        result = quickTxBuilder.compose(scriptPayTx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);
        //Required as backend service returns outdated utxo
        if (result.isSuccessful()) {
            checkIfUtxoAvailable(result.getValue(), sender1Addr);
        }

        //Find the utxo for the script address
        Optional<Utxo> sumUtxo  = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, sumScriptAddr, sumScriptDatum);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(sumUtxo.get(), sumScriptRedeemer)
                .readFrom(refUtxo)
                .payToAddress(receiver1, List.of(sumScriptAmt))
                .withChangeAddress(sumScriptAddr, sumScriptDatum);

        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxEvaluator(!backendType.equals(BLOCKFROST) && aikenEvaluation?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier, scriptHash -> sumScript): null)
                .completeAndWait(System.out::println);

        System.out.println(result1.getResponse());
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), sender1Addr);
    }

    @Test
    void referenceInputUtxo_guessSumScript_withRefScriptCall() throws ApiException, InterruptedException {
        //Sum Script
        PlutusV2Script sumScript =
                PlutusV2Script.builder()
                        .cborHex("5907a65907a3010000323322323232323232323232323232323322323232323222232325335323232333573466e1ccc07000d200000201e01d3333573466e1cd55cea80224000466442466002006004646464646464646464646464646666ae68cdc39aab9d500c480008cccccccccccc88888888888848cccccccccccc00403403002c02802402001c01801401000c008cd405c060d5d0a80619a80b80c1aba1500b33501701935742a014666aa036eb94068d5d0a804999aa80dbae501a35742a01066a02e0446ae85401cccd5406c08dd69aba150063232323333573466e1cd55cea801240004664424660020060046464646666ae68cdc39aab9d5002480008cc8848cc00400c008cd40b5d69aba15002302e357426ae8940088c98c80c0cd5ce01881801709aab9e5001137540026ae854008c8c8c8cccd5cd19b8735573aa004900011991091980080180119a816bad35742a004605c6ae84d5d1280111931901819ab9c03103002e135573ca00226ea8004d5d09aba2500223263202c33573805a05805426aae7940044dd50009aba1500533501775c6ae854010ccd5406c07c8004d5d0a801999aa80dbae200135742a00460426ae84d5d1280111931901419ab9c029028026135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d55cf280089baa00135742a00860226ae84d5d1280211931900d19ab9c01b01a018375a00a6eb4014405c4c98c805ccd5ce24810350543500017135573ca00226ea800448c88c008dd6000990009aa80b911999aab9f0012500a233500930043574200460066ae880080508c8c8cccd5cd19b8735573aa004900011991091980080180118061aba150023005357426ae8940088c98c8050cd5ce00a80a00909aab9e5001137540024646464646666ae68cdc39aab9d5004480008cccc888848cccc00401401000c008c8c8c8cccd5cd19b8735573aa0049000119910919800801801180a9aba1500233500f014357426ae8940088c98c8064cd5ce00d00c80b89aab9e5001137540026ae854010ccd54021d728039aba150033232323333573466e1d4005200423212223002004357426aae79400c8cccd5cd19b875002480088c84888c004010dd71aba135573ca00846666ae68cdc3a801a400042444006464c6403666ae7007006c06406005c4d55cea80089baa00135742a00466a016eb8d5d09aba2500223263201533573802c02a02626ae8940044d5d1280089aab9e500113754002266aa002eb9d6889119118011bab00132001355014223233335573e0044a010466a00e66442466002006004600c6aae754008c014d55cf280118021aba200301213574200222440042442446600200800624464646666ae68cdc3a800a40004642446004006600a6ae84d55cf280191999ab9a3370ea0049001109100091931900819ab9c01101000e00d135573aa00226ea80048c8c8cccd5cd19b875001480188c848888c010014c01cd5d09aab9e500323333573466e1d400920042321222230020053009357426aae7940108cccd5cd19b875003480088c848888c004014c01cd5d09aab9e500523333573466e1d40112000232122223003005375c6ae84d55cf280311931900819ab9c01101000e00d00c00b135573aa00226ea80048c8c8cccd5cd19b8735573aa004900011991091980080180118029aba15002375a6ae84d5d1280111931900619ab9c00d00c00a135573ca00226ea80048c8cccd5cd19b8735573aa002900011bae357426aae7940088c98c8028cd5ce00580500409baa001232323232323333573466e1d4005200c21222222200323333573466e1d4009200a21222222200423333573466e1d400d2008233221222222233001009008375c6ae854014dd69aba135744a00a46666ae68cdc3a8022400c4664424444444660040120106eb8d5d0a8039bae357426ae89401c8cccd5cd19b875005480108cc8848888888cc018024020c030d5d0a8049bae357426ae8940248cccd5cd19b875006480088c848888888c01c020c034d5d09aab9e500b23333573466e1d401d2000232122222223005008300e357426aae7940308c98c804ccd5ce00a00980880800780700680600589aab9d5004135573ca00626aae7940084d55cf280089baa0012323232323333573466e1d400520022333222122333001005004003375a6ae854010dd69aba15003375a6ae84d5d1280191999ab9a3370ea0049000119091180100198041aba135573ca00c464c6401866ae700340300280244d55cea80189aba25001135573ca00226ea80048c8c8cccd5cd19b875001480088c8488c00400cdd71aba135573ca00646666ae68cdc3a8012400046424460040066eb8d5d09aab9e500423263200933573801401200e00c26aae7540044dd500089119191999ab9a3370ea00290021091100091999ab9a3370ea00490011190911180180218031aba135573ca00846666ae68cdc3a801a400042444004464c6401466ae7002c02802001c0184d55cea80089baa0012323333573466e1d40052002200923333573466e1d40092000200923263200633573800e00c00800626aae74dd5000a4c240029210350543100320013550032225335333573466e1c0092000005004100113300333702004900119b80002001122002122001112323001001223300330020020011")
                        .build();
        String sumScriptAddr = AddressProvider.getEntAddress(sumScript, Networks.testnet()).toBech32();
        Amount sumScriptAmt = Amount.ada(4.0);
        PlutusData sumScriptDatum =  new BigIntPlutusData(BigInteger.valueOf(8)); //redeemer should be 36
        PlutusData sumScriptRedeemer = new BigIntPlutusData(BigInteger.valueOf(36));

        //Create a reference input and send lock amount at script address
        Tx createRefInputTx = new Tx();
        createRefInputTx.payToAddress(receiver1, List.of(Amount.ada(1.0)), sumScript)
                .payToContract(sumScriptAddr, sumScriptAmt, sumScriptDatum)
                .from(sender1Addr);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder.compose(createRefInputTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);
        System.out.println("Tx Response: " + result.getResponse());
        assertTrue(result.isSuccessful());

        //Required as backend service returns outdated utxo
        if (result.isSuccessful()) {
            checkIfUtxoAvailable(result.getValue(), sender1Addr);
        }

        Utxo refUtxo = Utxo.builder()
                .txHash(result.getValue())
                .outputIndex(0)
                .build();

        //Pay to script
        Tx scriptPayTx = new Tx();
        scriptPayTx.payToContract(sumScriptAddr, sumScriptAmt, sumScriptDatum)
                .from(sender1Addr);
        result = quickTxBuilder.compose(scriptPayTx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);
        //Required as backend service returns outdated utxo
        if (result.isSuccessful()) {
            checkIfUtxoAvailable(result.getValue(), sender1Addr);
        }

        //Find the utxo for the script address
        Optional<Utxo> sumUtxo  = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, sumScriptAddr, sumScriptDatum);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(sumUtxo.get(), sumScriptRedeemer)
                .readFrom(refUtxo)
                .payToAddress(receiver1, List.of(sumScriptAmt))
                .withChangeAddress(sumScriptAddr, sumScriptDatum);

        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxEvaluator(!backendType.equals(BLOCKFROST) && aikenEvaluation?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier, scriptHash -> sumScript): null)
                .withReferenceScripts(sumScript)
                .completeAndWait(System.out::println);

        System.out.println(result1.getResponse());
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), sender1Addr);
    }

    @Test
    void multipleUtxosCollectFrom_guessSumScript() throws ApiException, InterruptedException {
        //Sum Script
        PlutusV2Script sumScript =
                PlutusV2Script.builder()
                        .cborHex("5907a65907a3010000323322323232323232323232323232323322323232323222232325335323232333573466e1ccc07000d200000201e01d3333573466e1cd55cea80224000466442466002006004646464646464646464646464646666ae68cdc39aab9d500c480008cccccccccccc88888888888848cccccccccccc00403403002c02802402001c01801401000c008cd405c060d5d0a80619a80b80c1aba1500b33501701935742a014666aa036eb94068d5d0a804999aa80dbae501a35742a01066a02e0446ae85401cccd5406c08dd69aba150063232323333573466e1cd55cea801240004664424660020060046464646666ae68cdc39aab9d5002480008cc8848cc00400c008cd40b5d69aba15002302e357426ae8940088c98c80c0cd5ce01881801709aab9e5001137540026ae854008c8c8c8cccd5cd19b8735573aa004900011991091980080180119a816bad35742a004605c6ae84d5d1280111931901819ab9c03103002e135573ca00226ea8004d5d09aba2500223263202c33573805a05805426aae7940044dd50009aba1500533501775c6ae854010ccd5406c07c8004d5d0a801999aa80dbae200135742a00460426ae84d5d1280111931901419ab9c029028026135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d55cf280089baa00135742a00860226ae84d5d1280211931900d19ab9c01b01a018375a00a6eb4014405c4c98c805ccd5ce24810350543500017135573ca00226ea800448c88c008dd6000990009aa80b911999aab9f0012500a233500930043574200460066ae880080508c8c8cccd5cd19b8735573aa004900011991091980080180118061aba150023005357426ae8940088c98c8050cd5ce00a80a00909aab9e5001137540024646464646666ae68cdc39aab9d5004480008cccc888848cccc00401401000c008c8c8c8cccd5cd19b8735573aa0049000119910919800801801180a9aba1500233500f014357426ae8940088c98c8064cd5ce00d00c80b89aab9e5001137540026ae854010ccd54021d728039aba150033232323333573466e1d4005200423212223002004357426aae79400c8cccd5cd19b875002480088c84888c004010dd71aba135573ca00846666ae68cdc3a801a400042444006464c6403666ae7007006c06406005c4d55cea80089baa00135742a00466a016eb8d5d09aba2500223263201533573802c02a02626ae8940044d5d1280089aab9e500113754002266aa002eb9d6889119118011bab00132001355014223233335573e0044a010466a00e66442466002006004600c6aae754008c014d55cf280118021aba200301213574200222440042442446600200800624464646666ae68cdc3a800a40004642446004006600a6ae84d55cf280191999ab9a3370ea0049001109100091931900819ab9c01101000e00d135573aa00226ea80048c8c8cccd5cd19b875001480188c848888c010014c01cd5d09aab9e500323333573466e1d400920042321222230020053009357426aae7940108cccd5cd19b875003480088c848888c004014c01cd5d09aab9e500523333573466e1d40112000232122223003005375c6ae84d55cf280311931900819ab9c01101000e00d00c00b135573aa00226ea80048c8c8cccd5cd19b8735573aa004900011991091980080180118029aba15002375a6ae84d5d1280111931900619ab9c00d00c00a135573ca00226ea80048c8cccd5cd19b8735573aa002900011bae357426aae7940088c98c8028cd5ce00580500409baa001232323232323333573466e1d4005200c21222222200323333573466e1d4009200a21222222200423333573466e1d400d2008233221222222233001009008375c6ae854014dd69aba135744a00a46666ae68cdc3a8022400c4664424444444660040120106eb8d5d0a8039bae357426ae89401c8cccd5cd19b875005480108cc8848888888cc018024020c030d5d0a8049bae357426ae8940248cccd5cd19b875006480088c848888888c01c020c034d5d09aab9e500b23333573466e1d401d2000232122222223005008300e357426aae7940308c98c804ccd5ce00a00980880800780700680600589aab9d5004135573ca00626aae7940084d55cf280089baa0012323232323333573466e1d400520022333222122333001005004003375a6ae854010dd69aba15003375a6ae84d5d1280191999ab9a3370ea0049000119091180100198041aba135573ca00c464c6401866ae700340300280244d55cea80189aba25001135573ca00226ea80048c8c8cccd5cd19b875001480088c8488c00400cdd71aba135573ca00646666ae68cdc3a8012400046424460040066eb8d5d09aab9e500423263200933573801401200e00c26aae7540044dd500089119191999ab9a3370ea00290021091100091999ab9a3370ea00490011190911180180218031aba135573ca00846666ae68cdc3a801a400042444004464c6401466ae7002c02802001c0184d55cea80089baa0012323333573466e1d40052002200923333573466e1d40092000200923263200633573800e00c00800626aae74dd5000a4c240029210350543100320013550032225335333573466e1c0092000005004100113300333702004900119b80002001122002122001112323001001223300330020020011")
                        .build();
        String sumScriptAddr = AddressProvider.getEntAddress(sumScript, Networks.testnet()).toBech32();
        Amount sumScriptAmt = Amount.ada(4.0);
        PlutusData sumScriptDatum =  new BigIntPlutusData(BigInteger.valueOf(8)); //redeemer should be 36
        PlutusData sumScriptRedeemer = new BigIntPlutusData(BigInteger.valueOf(36));

        for (int i=0; i<2; i++) {
            //Create contract pay tx
            Tx scriptPayTx = new Tx();
            scriptPayTx.payToContract(sumScriptAddr, sumScriptAmt, sumScriptDatum)
                    .from(sender1Addr);

            QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
            Result<String> result = quickTxBuilder.compose(scriptPayTx)
                    .withSigner(SignerProviders.signerFrom(sender1))
                    .completeAndWait(System.out::println);
            System.out.println("Tx Response: " + result.getResponse());
            assertTrue(result.isSuccessful());

            //Required as backend service returns outdated utxo
            if (result.isSuccessful()) {
                checkIfUtxoAvailable(result.getValue(), sender1Addr);
            }
        }

        //Find the utxo for the script address
        List<Utxo> scriptUtxos  = ScriptUtxoFinders.findAllByInlineDatum(utxoSupplier, sumScriptAddr, sumScriptDatum);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(List.of(scriptUtxos.get(0), scriptUtxos.get(1)), sumScriptRedeemer)
                .payToAddress(receiver1,
                        List.of(Amount.ada(8.0)))
                .attachSpendingValidator(sumScript)
                .withChangeAddress(sumScriptAddr, sumScriptDatum);

        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxEvaluator(!backendType.equals(BLOCKFROST) && aikenEvaluation?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier): null)
                .completeAndWait(System.out::println);

        System.out.println(result1.getResponse());
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), sender1Addr);
    }

    @Test
    void referenceInputTransactionInput_guessSumScript() throws ApiException, InterruptedException {
        //Sum Script
        PlutusV2Script sumScript =
                PlutusV2Script.builder()
                        .cborHex("5907a65907a3010000323322323232323232323232323232323322323232323222232325335323232333573466e1ccc07000d200000201e01d3333573466e1cd55cea80224000466442466002006004646464646464646464646464646666ae68cdc39aab9d500c480008cccccccccccc88888888888848cccccccccccc00403403002c02802402001c01801401000c008cd405c060d5d0a80619a80b80c1aba1500b33501701935742a014666aa036eb94068d5d0a804999aa80dbae501a35742a01066a02e0446ae85401cccd5406c08dd69aba150063232323333573466e1cd55cea801240004664424660020060046464646666ae68cdc39aab9d5002480008cc8848cc00400c008cd40b5d69aba15002302e357426ae8940088c98c80c0cd5ce01881801709aab9e5001137540026ae854008c8c8c8cccd5cd19b8735573aa004900011991091980080180119a816bad35742a004605c6ae84d5d1280111931901819ab9c03103002e135573ca00226ea8004d5d09aba2500223263202c33573805a05805426aae7940044dd50009aba1500533501775c6ae854010ccd5406c07c8004d5d0a801999aa80dbae200135742a00460426ae84d5d1280111931901419ab9c029028026135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d55cf280089baa00135742a00860226ae84d5d1280211931900d19ab9c01b01a018375a00a6eb4014405c4c98c805ccd5ce24810350543500017135573ca00226ea800448c88c008dd6000990009aa80b911999aab9f0012500a233500930043574200460066ae880080508c8c8cccd5cd19b8735573aa004900011991091980080180118061aba150023005357426ae8940088c98c8050cd5ce00a80a00909aab9e5001137540024646464646666ae68cdc39aab9d5004480008cccc888848cccc00401401000c008c8c8c8cccd5cd19b8735573aa0049000119910919800801801180a9aba1500233500f014357426ae8940088c98c8064cd5ce00d00c80b89aab9e5001137540026ae854010ccd54021d728039aba150033232323333573466e1d4005200423212223002004357426aae79400c8cccd5cd19b875002480088c84888c004010dd71aba135573ca00846666ae68cdc3a801a400042444006464c6403666ae7007006c06406005c4d55cea80089baa00135742a00466a016eb8d5d09aba2500223263201533573802c02a02626ae8940044d5d1280089aab9e500113754002266aa002eb9d6889119118011bab00132001355014223233335573e0044a010466a00e66442466002006004600c6aae754008c014d55cf280118021aba200301213574200222440042442446600200800624464646666ae68cdc3a800a40004642446004006600a6ae84d55cf280191999ab9a3370ea0049001109100091931900819ab9c01101000e00d135573aa00226ea80048c8c8cccd5cd19b875001480188c848888c010014c01cd5d09aab9e500323333573466e1d400920042321222230020053009357426aae7940108cccd5cd19b875003480088c848888c004014c01cd5d09aab9e500523333573466e1d40112000232122223003005375c6ae84d55cf280311931900819ab9c01101000e00d00c00b135573aa00226ea80048c8c8cccd5cd19b8735573aa004900011991091980080180118029aba15002375a6ae84d5d1280111931900619ab9c00d00c00a135573ca00226ea80048c8cccd5cd19b8735573aa002900011bae357426aae7940088c98c8028cd5ce00580500409baa001232323232323333573466e1d4005200c21222222200323333573466e1d4009200a21222222200423333573466e1d400d2008233221222222233001009008375c6ae854014dd69aba135744a00a46666ae68cdc3a8022400c4664424444444660040120106eb8d5d0a8039bae357426ae89401c8cccd5cd19b875005480108cc8848888888cc018024020c030d5d0a8049bae357426ae8940248cccd5cd19b875006480088c848888888c01c020c034d5d09aab9e500b23333573466e1d401d2000232122222223005008300e357426aae7940308c98c804ccd5ce00a00980880800780700680600589aab9d5004135573ca00626aae7940084d55cf280089baa0012323232323333573466e1d400520022333222122333001005004003375a6ae854010dd69aba15003375a6ae84d5d1280191999ab9a3370ea0049000119091180100198041aba135573ca00c464c6401866ae700340300280244d55cea80189aba25001135573ca00226ea80048c8c8cccd5cd19b875001480088c8488c00400cdd71aba135573ca00646666ae68cdc3a8012400046424460040066eb8d5d09aab9e500423263200933573801401200e00c26aae7540044dd500089119191999ab9a3370ea00290021091100091999ab9a3370ea00490011190911180180218031aba135573ca00846666ae68cdc3a801a400042444004464c6401466ae7002c02802001c0184d55cea80089baa0012323333573466e1d40052002200923333573466e1d40092000200923263200633573800e00c00800626aae74dd5000a4c240029210350543100320013550032225335333573466e1c0092000005004100113300333702004900119b80002001122002122001112323001001223300330020020011")
                        .build();
        String sumScriptAddr = AddressProvider.getEntAddress(sumScript, Networks.testnet()).toBech32();
        Amount sumScriptAmt = Amount.ada(4.0);
        PlutusData sumScriptDatum =  new BigIntPlutusData(BigInteger.valueOf(8)); //redeemer should be 36
        PlutusData sumScriptRedeemer = new BigIntPlutusData(BigInteger.valueOf(36));

        //Create a reference input and send lock amount at script address
        Tx createRefInputTx = new Tx();
        createRefInputTx.payToAddress(receiver1, List.of(Amount.ada(1.0)), sumScript)
                .from(sender1Addr);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder.compose(createRefInputTx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);
        System.out.println("Tx Response: " + result.getResponse());
        assertTrue(result.isSuccessful());

        //Required as backend service returns outdated utxo
        if (result.isSuccessful()) {
            checkIfUtxoAvailable(result.getValue(), sender1Addr);
        }

        TransactionInput refInput = new TransactionInput(result.getValue(), 0);

        Tx scriptPayTx = new Tx();
        scriptPayTx.payToContract(sumScriptAddr, sumScriptAmt, sumScriptDatum)
                .from(sender1Addr);

        result = quickTxBuilder.compose(scriptPayTx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);
        System.out.println("Tx Response: " + result.getResponse());
        assertTrue(result.isSuccessful());

        if (result.isSuccessful()) {
            checkIfUtxoAvailable(result.getValue(), sender1Addr);
        }

        //Find the utxo for the script address
        Optional<Utxo> sumUtxo  = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, sumScriptAddr, sumScriptDatum);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(sumUtxo.get(), sumScriptRedeemer)
                .readFrom(refInput)
                .payToAddress(receiver1, List.of(sumScriptAmt))
                .attachSpendingValidator(sumScript)
                .withChangeAddress(sumScriptAddr, sumScriptDatum);

        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxEvaluator(!backendType.equals(BLOCKFROST) && aikenEvaluation?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier): null)
                .postBalanceTx((context, txn) -> {
                    txn.getWitnessSet().getPlutusV2Scripts().clear();
                }).completeAndWait(System.out::println);

        System.out.println(result1.getResponse());
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), sender1Addr);
    }

    @Nested
    class DatumInWitnessTests {

        @Test
        void testDatumInWitnessWhenDatumHash() throws ApiException {
            PlutusV2Script plutusScript = PlutusV2Script.builder()
                    .type("PlutusScriptV2")
                    .cborHex("49480100002221200101")
                    .build();

            String scriptAddress = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).toBech32();
            BigInteger scriptAmt = new BigInteger("2479280");

            Random rand = new Random();
            int randInt = rand.nextInt();
            BigIntPlutusData plutusData =  new BigIntPlutusData(BigInteger.valueOf(randInt)); //any random number

            Tx tx = new Tx();
            tx.payToContract(scriptAddress, Amount.lovelace(scriptAmt), plutusData.getDatumHash())
                    .from(sender2Addr);

            QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
            Result<String> result = quickTxBuilder.compose(tx)
                    .withSigner(SignerProviders.signerFrom(sender2))
                    .completeAndWait(System.out::println);

            System.out.println(result.getResponse());
            checkIfUtxoAvailable(result.getValue(), scriptAddress);

            Optional<Utxo> optionalUtxo  = ScriptUtxoFinders.findFirstByDatumHash(utxoSupplier, scriptAddress, plutusData.getDatumHash());
            ScriptTx scriptTx = new ScriptTx()
                    .collectFrom(optionalUtxo.get(), PlutusData.unit(), plutusData)
                    .payToAddress(receiver1, Amount.lovelace(scriptAmt))
                    .attachSpendingValidator(plutusScript)
                    .withChangeAddress(scriptAddress, plutusData);

            Result<String> result1 = quickTxBuilder.compose(scriptTx)
                    .feePayer(sender1Addr)
                    .withSigner(SignerProviders.signerFrom(sender1))
                    .withTxEvaluator(!backendType.equals(BLOCKFROST) && aikenEvaluation?
                            new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier): null)
                    .withVerifier(txn -> {
                        assertThat(txn.getWitnessSet().getPlutusDataList()).contains(plutusData);
                        assertThat(txn.getWitnessSet().getPlutusDataList()).hasSize(1);
                    })
                    .withSerializationEra(Era.Babbage)
                    .completeAndWait(System.out::println);

            System.out.println(result1.getResponse());
            assertTrue(result1.isSuccessful());

            checkIfUtxoAvailable(result1.getValue(), sender1Addr);
        }

        @Test
        void testDatumInWitnessWhenDatumHash_multipleUtxos() throws ApiException {
            PlutusV2Script plutusScript = PlutusV2Script.builder()
                    .type("PlutusScriptV2")
                    .cborHex("49480100002221200101")
                    .build();

            String scriptAddress = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).toBech32();
            Random rand = new Random();
            int randInt = rand.nextInt();
            BigIntPlutusData plutusData =  new BigIntPlutusData(BigInteger.valueOf(randInt)); //any random number

            //second datum
            BigIntPlutusData plutusData2 =  new BigIntPlutusData(BigInteger.valueOf(rand.nextInt())); //any random number

            //three datum
            BigIntPlutusData plutusData3 =  new BigIntPlutusData(BigInteger.valueOf(rand.nextInt())); //any random number

            Tx tx = new Tx();
            tx.payToContract(scriptAddress, Amount.ada(1), plutusData.getDatumHash())
                    .payToContract(scriptAddress, Amount.ada(2), plutusData.getDatumHash())
                    .payToContract(scriptAddress, Amount.ada(2.5), plutusData.getDatumHash())
                    .payToContract(scriptAddress, Amount.ada(1.5), plutusData.getDatumHash())
                    .payToContract(scriptAddress, Amount.ada(1), plutusData2.getDatumHash())
                    .payToContract(scriptAddress, Amount.ada(1.1), plutusData2.getDatumHash())
                    .payToContract(scriptAddress, Amount.ada(1.2), plutusData3)
                    .from(sender2Addr);

            QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
            Result<String> result = quickTxBuilder.compose(tx)
                    .withSigner(SignerProviders.signerFrom(sender2))
                    .mergeOutputs(false)
                    .completeAndWait(System.out::println);

            System.out.println(result.getResponse());
            checkIfUtxoAvailable(result.getValue(), scriptAddress);

            List<Utxo> utxos  = ScriptUtxoFinders.findAllByDatumHash(utxoSupplier, scriptAddress, plutusData.getDatumHash());
            List<Utxo> utxos2  = ScriptUtxoFinders.findAllByDatumHash(utxoSupplier, scriptAddress, plutusData2.getDatumHash());
            Optional<Utxo> utxos3  = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddress, plutusData3);

            ScriptTx scriptTx = new ScriptTx()
                    .collectFrom(utxos, PlutusData.unit(), plutusData)
                    .collectFrom(utxos2, PlutusData.unit(), plutusData2)
                    .collectFrom(utxos3.get(), PlutusData.unit())
                    .payToAddress(receiver1, Amount.ada(10.3))
                    .attachSpendingValidator(plutusScript)
                    .withChangeAddress(scriptAddress, plutusData);

            Result<String> result1 = quickTxBuilder.compose(scriptTx)
                    .feePayer(sender1Addr)
                    .withSigner(SignerProviders.signerFrom(sender1))
                    .withTxEvaluator(!backendType.equals(BLOCKFROST) && aikenEvaluation?
                            new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier): null)
                    .withVerifier(txn -> {
                        assertThat(txn.getWitnessSet().getPlutusDataList()).contains(plutusData);
                        assertThat(txn.getWitnessSet().getPlutusDataList()).contains(plutusData2);
                        assertThat(txn.getWitnessSet().getPlutusDataList()).hasSize(2);
                    })
                    .withSerializationEra(Era.Babbage)
                    .completeAndWait(System.out::println);

            System.out.println(result1.getResponse());
            assertTrue(result1.isSuccessful());

            checkIfUtxoAvailable(result1.getValue(), sender1Addr);
        }
    }

    @Nested
    class CollateralInputTest {

        @Test
        void whenCustomCollateralInputs() throws ApiException {
            PlutusV2Script plutusScript = PlutusV2Script.builder()
                    .type("PlutusScriptV2")
                    .cborHex("49480100002221200101")
                    .build();

            String scriptAddress = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).toBech32();
            BigInteger scriptAmt = new BigInteger("2479280");

            Random rand = new Random();
            int randInt = rand.nextInt();
            BigIntPlutusData plutusData =  new BigIntPlutusData(BigInteger.valueOf(randInt)); //any random number

            Tx tx = new Tx();
            tx.payToContract(scriptAddress, Amount.lovelace(scriptAmt), plutusData)
                    .payToAddress(sender1Addr, Amount.ada(5))
                    .payToAddress(sender1Addr, Amount.ada(20))
                    .from(sender2Addr);

            QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
            Result<String> result = quickTxBuilder.compose(tx)
                    .withSigner(SignerProviders.signerFrom(sender2))
                    .mergeOutputs(false)
                    .completeAndWait(System.out::println);

            String payTxHash = result.getValue();

            System.out.println(result.getResponse());
            checkIfUtxoAvailable(result.getValue(), scriptAddress);

            Optional<Utxo> optionalUtxo  = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddress, plutusData);
            ScriptTx scriptTx = new ScriptTx()
                    .collectFrom(optionalUtxo.get(), plutusData)
                    .payToAddress(receiver1, Amount.lovelace(scriptAmt))
                    .attachSpendingValidator(plutusScript)
                    .withChangeAddress(scriptAddress, plutusData);

            Result<String> result1 = quickTxBuilder.compose(scriptTx)
                    .feePayer(sender1Addr)
                    .withSigner(SignerProviders.signerFrom(sender1))
                    .withCollateralInputs(new TransactionInput(payTxHash, 1), new TransactionInput(payTxHash, 2))
                    .withTxEvaluator(!backendType.equals(BLOCKFROST) && aikenEvaluation?
                            new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier): null)
                    .withVerifier(txn -> {
                        assertThat(txn.getBody().getCollateral()).hasSize(2);
                        assertThat(txn.getBody().getCollateral()).contains(
                                new TransactionInput(payTxHash, 1), new TransactionInput(payTxHash, 2));
                    })
                    .completeAndWait(System.out::println);

            System.out.println(result1.getResponse());
            assertTrue(result1.isSuccessful());

            checkIfUtxoAvailable(result1.getValue(), sender1Addr);
        }
    }

    //TODO -- Write an integration test to verify required signer is present in script transaction

    private String getRandomTokenName() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        int length = 6;
        for(int i = 0; i < length; i++) {
            int index = random.nextInt(alphabet.length());
            char randomChar = alphabet.charAt(index);
            sb.append(randomChar);
        }

        return sb.toString();
    }
}
