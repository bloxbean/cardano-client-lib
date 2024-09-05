package com.bloxbean.cardano.client.backend.ogmios;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.ScriptUtxoFinders;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusV3Script;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.spec.EraSerializationConfig;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OgmiosTransactionServiceIT extends OgmiosBaseTest {
    TransactionService transactionService;
    QuickTxBuilder quickTxBuilder;

    String sender1Addr;
    Account sender1;
    String receiver2 = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

    @BeforeEach
    public void setup() {
        transactionService = ogmiosBackendService.getTransactionService();
        quickTxBuilder = new QuickTxBuilder(kupmiosBackendService);
        String senderMnemonic = "drive useless envelope shine range ability time copper alarm museum near flee wrist live type device meadow allow churn purity wisdom praise drop code";
        sender1 = new Account(Networks.testnet(), senderMnemonic);
        sender1Addr = sender1.baseAddress();
    }

    @Test
    void evaluateTx_cbor_error() throws ApiException {
        String txHex = "8484a70081825820d477e2e4de888adb9a35ecfe89b3401588280ed40015c34931e60cc006de991500018182583900a307e23e7f0e2102717aa610f0df3d5e8de80ddc9c099b6036f3aa78592eabbe8d5b88e92932a01c13a511bc04e0f5c1cfbb04ee8dfe07601a0025d4b002000d81825820d477e2e4de888adb9a35ecfe89b3401588280ed40015c34931e60cc006de9915010e81581c7d1fcb835da61dd128c9283510bd26c2d8c6d0439e938f14b5ef941e10825839007d1fcb835da61dd128c9283510bd26c2d8c6d0439e938f14b5ef941e248d073c7065dc990d6a98455f4514f4190a310b60ccda5801cfc36d1a3aaf642e111a000f4240a205818400001a5b39662882192710192710068149480100002221200101f5f6";
        byte[] cbor = HexUtil.decodeHexString(txHex);

        Result<List<EvaluationResult>> evaluationResult = transactionService.evaluateTx(cbor);
        System.out.println(evaluationResult);
        assertThat(evaluationResult.isSuccessful()).isEqualTo(false);
        assertThat(evaluationResult.getResponse()).isNotNull();
    }

    @Test
    void submitTx() {
        Tx tx = new Tx()
                .payToAddress(receiver2, Amount.ada(1.5))
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .complete();

        System.out.println(result);
        assertTrue(result.isSuccessful());
    }

    @Test
    void referenceInputUtxo_guessSumScript() throws ApiException, InterruptedException {
        EraSerializationConfig.INSTANCE.useConwayEraFormat();
        //Sum Script
        PlutusV3Script sumScript =
                PlutusV3Script.builder()
                        //.cborHex("5907a65907a3010000323322323232323232323232323232323322323232323222232325335323232333573466e1ccc07000d200000201e01d3333573466e1cd55cea80224000466442466002006004646464646464646464646464646666ae68cdc39aab9d500c480008cccccccccccc88888888888848cccccccccccc00403403002c02802402001c01801401000c008cd405c060d5d0a80619a80b80c1aba1500b33501701935742a014666aa036eb94068d5d0a804999aa80dbae501a35742a01066a02e0446ae85401cccd5406c08dd69aba150063232323333573466e1cd55cea801240004664424660020060046464646666ae68cdc39aab9d5002480008cc8848cc00400c008cd40b5d69aba15002302e357426ae8940088c98c80c0cd5ce01881801709aab9e5001137540026ae854008c8c8c8cccd5cd19b8735573aa004900011991091980080180119a816bad35742a004605c6ae84d5d1280111931901819ab9c03103002e135573ca00226ea8004d5d09aba2500223263202c33573805a05805426aae7940044dd50009aba1500533501775c6ae854010ccd5406c07c8004d5d0a801999aa80dbae200135742a00460426ae84d5d1280111931901419ab9c029028026135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d55cf280089baa00135742a00860226ae84d5d1280211931900d19ab9c01b01a018375a00a6eb4014405c4c98c805ccd5ce24810350543500017135573ca00226ea800448c88c008dd6000990009aa80b911999aab9f0012500a233500930043574200460066ae880080508c8c8cccd5cd19b8735573aa004900011991091980080180118061aba150023005357426ae8940088c98c8050cd5ce00a80a00909aab9e5001137540024646464646666ae68cdc39aab9d5004480008cccc888848cccc00401401000c008c8c8c8cccd5cd19b8735573aa0049000119910919800801801180a9aba1500233500f014357426ae8940088c98c8064cd5ce00d00c80b89aab9e5001137540026ae854010ccd54021d728039aba150033232323333573466e1d4005200423212223002004357426aae79400c8cccd5cd19b875002480088c84888c004010dd71aba135573ca00846666ae68cdc3a801a400042444006464c6403666ae7007006c06406005c4d55cea80089baa00135742a00466a016eb8d5d09aba2500223263201533573802c02a02626ae8940044d5d1280089aab9e500113754002266aa002eb9d6889119118011bab00132001355014223233335573e0044a010466a00e66442466002006004600c6aae754008c014d55cf280118021aba200301213574200222440042442446600200800624464646666ae68cdc3a800a40004642446004006600a6ae84d55cf280191999ab9a3370ea0049001109100091931900819ab9c01101000e00d135573aa00226ea80048c8c8cccd5cd19b875001480188c848888c010014c01cd5d09aab9e500323333573466e1d400920042321222230020053009357426aae7940108cccd5cd19b875003480088c848888c004014c01cd5d09aab9e500523333573466e1d40112000232122223003005375c6ae84d55cf280311931900819ab9c01101000e00d00c00b135573aa00226ea80048c8c8cccd5cd19b8735573aa004900011991091980080180118029aba15002375a6ae84d5d1280111931900619ab9c00d00c00a135573ca00226ea80048c8cccd5cd19b8735573aa002900011bae357426aae7940088c98c8028cd5ce00580500409baa001232323232323333573466e1d4005200c21222222200323333573466e1d4009200a21222222200423333573466e1d400d2008233221222222233001009008375c6ae854014dd69aba135744a00a46666ae68cdc3a8022400c4664424444444660040120106eb8d5d0a8039bae357426ae89401c8cccd5cd19b875005480108cc8848888888cc018024020c030d5d0a8049bae357426ae8940248cccd5cd19b875006480088c848888888c01c020c034d5d09aab9e500b23333573466e1d401d2000232122222223005008300e357426aae7940308c98c804ccd5ce00a00980880800780700680600589aab9d5004135573ca00626aae7940084d55cf280089baa0012323232323333573466e1d400520022333222122333001005004003375a6ae854010dd69aba15003375a6ae84d5d1280191999ab9a3370ea0049000119091180100198041aba135573ca00c464c6401866ae700340300280244d55cea80189aba25001135573ca00226ea80048c8c8cccd5cd19b875001480088c8488c00400cdd71aba135573ca00646666ae68cdc3a8012400046424460040066eb8d5d09aab9e500423263200933573801401200e00c26aae7540044dd500089119191999ab9a3370ea00290021091100091999ab9a3370ea00490011190911180180218031aba135573ca00846666ae68cdc3a801a400042444004464c6401466ae7002c02802001c0184d55cea80089baa0012323333573466e1d40052002200923333573466e1d40092000200923263200633573800e00c00800626aae74dd5000a4c240029210350543100320013550032225335333573466e1c0092000005004100113300333702004900119b80002001122002122001112323001001223300330020020011")
                        .cborHex("46450101002499")
                        .build();
        String sumScriptAddr = AddressProvider.getEntAddress(sumScript, Networks.testnet()).toBech32();
        Amount sumScriptAmt = Amount.ada(4.0);
        PlutusData sumScriptDatum =  new BigIntPlutusData(BigInteger.valueOf(8)); //redeemer should be 36
        PlutusData sumScriptRedeemer = new BigIntPlutusData(BigInteger.valueOf(36));

        //Create a reference input and send lock amount at script address
        Tx createRefInputTx = new Tx();
        createRefInputTx.payToAddress(receiver2, List.of(Amount.ada(1.0)), sumScript)
                .payToContract(sumScriptAddr, sumScriptAmt, sumScriptDatum)
                .from(sender1Addr);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(kupmiosBackendService);
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
        Optional<Utxo> sumUtxo  = ScriptUtxoFinders.findFirstByInlineDatum(new DefaultUtxoSupplier(kupmiosBackendService.getUtxoService()), sumScriptAddr, sumScriptDatum);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(sumUtxo.get(), sumScriptRedeemer)
                .readFrom(refUtxo)
                .payToAddress(receiver2, List.of(sumScriptAmt))
                .withChangeAddress(sumScriptAddr, sumScriptDatum);

        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withReferenceScripts(sumScript)
//                .withTxEvaluator(!backendType.equals(BLOCKFROST)?
//                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier, scriptHash -> sumScript): null)
                .completeAndWait(System.out::println);

        System.out.println(result1.getResponse());
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), sender1Addr);
    }

    protected void checkIfUtxoAvailable(String txHash, String address) {
        Optional<Utxo> utxo = Optional.empty();
        int count = 0;
        while (utxo.isEmpty()) {
            if (count++ >= 20)
                break;
            List<Utxo> utxos = new DefaultUtxoSupplier(kupmiosBackendService.getUtxoService()).getAll(address);
            utxo = utxos.stream().filter(u -> u.getTxHash().equals(txHash))
                    .findFirst();
            System.out.println("Try to get new output... txhash: " + txHash);
            try {
                Thread.sleep(1000);
            } catch (Exception e) {}
        }
    }

}
