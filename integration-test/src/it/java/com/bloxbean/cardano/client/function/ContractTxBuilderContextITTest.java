package com.bloxbean.cardano.client.function;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.api.helper.FeeCalculationService;
import com.bloxbean.cardano.client.api.helper.TransactionHelperService;
import com.bloxbean.cardano.client.api.helper.model.TransactionResult;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.*;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.cip.cip20.MessageMetadata;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.coinselection.UtxoSelector;
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelectionStrategyImpl;
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelector;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.config.Configuration;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.helper.*;
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.PlutusField;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.bloxbean.cardano.client.util.Tuple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.*;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static com.bloxbean.cardano.client.common.CardanoConstants.ONE_ADA;
import static com.bloxbean.cardano.client.function.helper.BalanceTxBuilders.balanceTx;
import static com.bloxbean.cardano.client.function.helper.CollateralBuilders.collateralFrom;
import static com.bloxbean.cardano.client.function.helper.InputBuilders.createFromSender;
import static com.bloxbean.cardano.client.function.helper.InputBuilders.createFromUtxos;
import static com.bloxbean.cardano.client.function.helper.ScriptCallContextProviders.scriptCallContext;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContractTxBuilderContextITTest extends BaseITTest {
    BackendService backendService;
    UtxoService utxoService;
    TransactionService transactionService;
    TransactionHelperService transactionHelperService;
    BlockService blockService;
    FeeCalculationService feeCalculationService;
    EpochService epochService;
    MetadataService metadataService;
    ProtocolParams protocolParams;
    UtxoSupplier utxoSupplier;

    static Account sender;
    static String senderAddress;

    @BeforeAll
    public static void setupAll() {
        //addr_test1qpkcp26l47j2fp4crdl9n83zmnw84qrp64sd5w6fwesqt6g8sd9mcktl67rn2t0cth25ryflz59yfxlx636csng7hawstfp400
        String senderMnemonic = "company coast prison denial unknown design paper engage sadness employ phone cherry thunder chimney vapor cake lock afraid frequent myself engage lumber between tip";
        sender = new Account(Networks.testnet(), senderMnemonic);
        senderAddress = sender.baseAddress();

        if (backendType.equals(DEVKIT)) {
            topUpFund(senderAddress, 50000);
        }
    }

    @BeforeEach
    public void setup() throws ApiException {
        backendService = getBackendService();
        utxoService = backendService.getUtxoService();
        transactionService = backendService.getTransactionService();
        transactionHelperService = backendService.getTransactionHelperService();
        blockService = backendService.getBlockService();
        feeCalculationService = backendService.getFeeCalculationService(transactionHelperService);
        epochService = backendService.getEpochService();
        metadataService = backendService.getMetadataService();

        protocolParams = backendService.getEpochService().getProtocolParameters().getValue();
        utxoSupplier = new DefaultUtxoSupplier(utxoService);
    }

    @AfterEach
    public void cleanup() {
    }

    @Test
    void customDatumCustomRedeemerGuessContract() throws CborSerializationException, ApiException, AddressExcepion {
        PlutusV1Script plutusScript = PlutusV1Script.builder()
                .type("PlutusScriptV1")
                .cborHex("590a15590a120100003323322332232323332223233322232333333332222222232333222323333222232323322323332223233322232323322332232323333322222332233223322332233223322223223223232533530333330083333573466e1cd55cea8032400046eb4d5d09aab9e500723504935304a335738921035054310004b499263333573466e1cd55cea8022400046eb4d5d09aab9e500523504935304a3357389201035054310004b499263333573466e1cd55cea8012400046601664646464646464646464646666ae68cdc39aab9d500a480008cccccccccc064cd409c8c8c8cccd5cd19b8735573aa004900011980f981d1aba15002302c357426ae8940088d4164d4c168cd5ce249035054310005b49926135573ca00226ea8004d5d0a80519a8138141aba150093335502e75ca05a6ae854020ccd540b9d728169aba1500733502704335742a00c66a04e66aa0a8098eb4d5d0a8029919191999ab9a3370e6aae754009200023350213232323333573466e1cd55cea80124000466a05266a084eb4d5d0a80118239aba135744a00446a0ba6a60bc66ae712401035054310005f49926135573ca00226ea8004d5d0a8011919191999ab9a3370e6aae7540092000233502733504275a6ae854008c11cd5d09aba2500223505d35305e3357389201035054310005f49926135573ca00226ea8004d5d09aba2500223505935305a3357389201035054310005b49926135573ca00226ea8004d5d0a80219a813bae35742a00666a04e66aa0a8eb88004d5d0a801181c9aba135744a00446a0aa6a60ac66ae71241035054310005749926135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d5d1280089aba25001135573ca00226ea8004d5d0a8011919191999ab9a3370ea00290031180f181d9aba135573ca00646666ae68cdc3a801240084603a608a6ae84d55cf280211999ab9a3370ea00690011180e98181aba135573ca00a46666ae68cdc3a80224000460406eb8d5d09aab9e50062350503530513357389201035054310005249926499264984d55cea80089baa001357426ae8940088d4124d4c128cd5ce249035054310004b49926104a1350483530493357389201035054350004a4984d55cf280089baa0011375400226ea80048848cc00400c0088004888888888848cccccccccc00402c02802402001c01801401000c00880048848cc00400c008800448848cc00400c0084800448848cc00400c0084800448848cc00400c00848004848888c010014848888c00c014848888c008014848888c004014800448c88c008dd6000990009aa81a111999aab9f0012500e233500d30043574200460066ae880080cc8c8c8c8cccd5cd19b8735573aa006900011998039919191999ab9a3370e6aae754009200023300d303135742a00466a02605a6ae84d5d1280111a81b1a981b99ab9c491035054310003849926135573ca00226ea8004d5d0a801999aa805bae500a35742a00466a01eeb8d5d09aba25002235032353033335738921035054310003449926135744a00226aae7940044dd50009110919980080200180110009109198008018011000899aa800bae75a224464460046eac004c8004d540b888c8cccd55cf80112804919a80419aa81898031aab9d5002300535573ca00460086ae8800c0b84d5d08008891001091091198008020018900089119191999ab9a3370ea002900011a80418029aba135573ca00646666ae68cdc3a801240044a01046a0526a605466ae712401035054310002b499264984d55cea80089baa001121223002003112200112001232323333573466e1cd55cea8012400046600c600e6ae854008dd69aba135744a00446a0466a604866ae71241035054310002549926135573ca00226ea80048848cc00400c00880048c8cccd5cd19b8735573aa002900011bae357426aae7940088d407cd4c080cd5ce24810350543100021499261375400224464646666ae68cdc3a800a40084a00e46666ae68cdc3a8012400446a014600c6ae84d55cf280211999ab9a3370ea00690001280511a8111a981199ab9c490103505431000244992649926135573aa00226ea8004484888c00c0104488800844888004480048c8cccd5cd19b8750014800880188cccd5cd19b8750024800080188d4068d4c06ccd5ce249035054310001c499264984d55ce9baa0011220021220012001232323232323333573466e1d4005200c200b23333573466e1d4009200a200d23333573466e1d400d200823300b375c6ae854014dd69aba135744a00a46666ae68cdc3a8022400c46601a6eb8d5d0a8039bae357426ae89401c8cccd5cd19b875005480108cc048c050d5d0a8049bae357426ae8940248cccd5cd19b875006480088c050c054d5d09aab9e500b23333573466e1d401d2000230133016357426aae7940308d407cd4c080cd5ce2481035054310002149926499264992649926135573aa00826aae79400c4d55cf280109aab9e500113754002424444444600e01044244444446600c012010424444444600a010244444440082444444400644244444446600401201044244444446600201201040024646464646666ae68cdc3a800a400446660106eb4d5d0a8021bad35742a0066eb4d5d09aba2500323333573466e1d400920002300a300b357426aae7940188d4040d4c044cd5ce2490350543100012499264984d55cea80189aba25001135573ca00226ea80048488c00800c888488ccc00401401000c80048c8c8cccd5cd19b875001480088c018dd71aba135573ca00646666ae68cdc3a80124000460106eb8d5d09aab9e500423500a35300b3357389201035054310000c499264984d55cea80089baa001212230020032122300100320011122232323333573466e1cd55cea80124000466aa016600c6ae854008c014d5d09aba25002235007353008335738921035054310000949926135573ca00226ea8004498480048004448848cc00400c008448004448c8c00400488cc00cc008008004ccc888c8c8ccc888ccc888cccccccc88888888cc88ccccc88888cccc8888cc88cc88cc88ccc888cc88cc88ccc888cc88cc88cc88cc88888cc894cd4c0e4008400440e8ccd40d540d800d205433350355036002481508848cc00400c0088004888888888848cccccccccc00402c02802402001c01801401000c00880048848cc00400c008800488848ccc00401000c00880044488008488488cc00401000c48004448848cc00400c0084480048848cc00400c008800448488c00800c44880044800448848cc00400c0084800448848cc00400c0084800448848cc00400c00848004484888c00c010448880084488800448004848888c010014848888c00c014848888c008014848888c00401480048848cc00400c0088004848888888c01c0208848888888cc018024020848888888c014020488888880104888888800c8848888888cc0080240208848888888cc00402402080048488c00800c888488ccc00401401000c80048488c00800c8488c00400c800448004488ccd5cd19b87002001005004122002122001200101")
                .build();

        System.out.println("SENDER >> " + sender.baseAddress());
        String collateral = "ab44e0f5faf56154cc33e757c9d98a60666346179d5a7a0b9d77734c23c42082";
        int collateralIndex = 0;

        Tuple<String, Integer> collateralTuple = checkCollateral(sender, collateral, collateralIndex);
        if (collateralTuple == null) {
            System.out.println("Collateral cannot be found or created. " + collateral);
            return;
        }
        collateral = collateralTuple._1;
        collateralIndex = collateralTuple._2;

        String scriptAddress = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).getAddress();
        System.out.println("Script Address: " + scriptAddress);

        Guess guess = new Guess(Integer.valueOf(42));
        Optional<Utxo> utxoOptional = ScriptUtxoFinders.findFirstByDatumHashUsingDatum(utxoSupplier, scriptAddress, guess);
        //Start contract transaction to claim fund
        if (!utxoOptional.isPresent()) {
            System.out.println("No utxo found...Let's transfer some Ada to script address");
            boolean paymentSuccessful = transferToContractAddress(sender, scriptAddress, adaToLovelace(5),
                    Configuration.INSTANCE.getPlutusObjectConverter().toPlutusData(guess).getDatumHash(), collateral, collateralIndex);
            utxoOptional = ScriptUtxoFinders.findFirstByDatumHashUsingDatum(utxoSupplier, scriptAddress, guess);
            assertTrue(paymentSuccessful);
        }

        Utxo scriptUtxo = utxoOptional.get();
        BigInteger claimableAmt = utxoOptional.get().getAmount().stream().filter(amount -> LOVELACE.equals(amount.getUnit()))
                .findFirst()
                .map(amount -> amount.getQuantity())
                .orElseGet(() -> BigInteger.ZERO);

        System.out.println("Script utxo >>>>>>>> " + scriptUtxo.getTxHash());

        Output scriptOutput = Output.builder()
                .address(sender.baseAddress())
                .assetName(LOVELACE)
                .qty(claimableAmt)
                .build();

        //Mem and steps are set to 0, as we are going to evaluate those in this test
        ExUnits exUnits = ExUnits.builder()
                .mem(BigInteger.valueOf(0))
                .steps(BigInteger.valueOf(0)).build();

        Output sendOutput = Output.builder()
                .address("addr_test1qpg4faaydel7n6cq8e4p5kscg6zahmrhlgeke8c6hn6utespky66rz9quy288xqfwc4k2z3v5h4g7gqxpkr8hn9rngvq00hz02")
                .assetName(LOVELACE)
                .qty(ONE_ADA.multiply(BigInteger.valueOf(2)))
                .build();

        Output secondSendOutput = Output.builder()
                .address("addr_test1qq9f6hzuwmqpe3p9h90z2rtgs0v0lsq9ln5f79fjyec7eclg7v88q9als70uzkdh5k6hw20uuwqfz477znfp5v4rga2s3ysgxu")
                .assetName(LOVELACE)
                .qty(ONE_ADA.multiply(BigInteger.valueOf(4)))
                .build();

        System.out.println("SENDER ADDRESS >> " + senderAddress);
        TxBuilder builder = sendOutput.outputBuilder()
                .and(secondSendOutput.outputBuilder())
                .buildInputs(createFromSender(senderAddress, senderAddress))
                .andThen(
                        scriptOutput.outputBuilder()
                                .buildInputs(createFromUtxos(List.of(scriptUtxo), senderAddress, null))
                )
                .andThen(collateralFrom(collateral, collateralIndex))
                .andThen(scriptCallContext(plutusScript, scriptUtxo, guess, guess, RedeemerTag.Spend, exUnits))
                .andThen((context, txn) -> { //Evaluate ExUnits
                    //update estimate ExUnits
                    ExUnits estimatedExUnits;
                    try {
                        estimatedExUnits = evaluateExUnits(txn);
                        txn.getWitnessSet().getRedeemers().get(0).setExUnits(estimatedExUnits);
                    } catch (Exception e) {
                        throw new ApiRuntimeException("Script cost evaluation failed", e);
                    }
                })
                .andThen(balanceTx(senderAddress, 1));

        TxSigner signer = SignerProviders.signerFrom(sender);

        Transaction signedTxn = TxBuilderContext.init(utxoSupplier, protocolParams)
                .withSerializationEra(Era.Babbage)
                .buildAndSign(builder, signer);

        Result<String> result = transactionService.submitTransaction(signedTxn.serialize());
        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);
    }

    private ExUnits evaluateExUnits(Transaction transaction) throws ApiException, CborSerializationException {
        if (backendType.equals(BLOCKFROST) || backendType.equals(DEVKIT)) {
            Result<List<EvaluationResult>> evalResults = transactionService.evaluateTx(transaction.serialize());
            if (evalResults.isSuccessful()) {
                return evalResults.getValue().get(0).getExUnits();
            } else {
                return null;
            }
        } else {
            //Hard coded value for other backend types where evaluateTx is not yet supported
            return ExUnits.builder()
                    .mem(BigInteger.valueOf(4676948))
                    .steps(BigInteger.valueOf(630892334)).build();
        }
    }

    @Test
    void customDatumCustomRedeemerGuessContractAndSumContract() throws CborSerializationException, ApiException, AddressExcepion, CborException {
        PlutusV1Script customGuessScript = PlutusV1Script.builder()
                .type("PlutusScriptV1")
                .cborHex("590a15590a120100003323322332232323332223233322232333333332222222232333222323333222232323322323332223233322232323322332232323333322222332233223322332233223322223223223232533530333330083333573466e1cd55cea8032400046eb4d5d09aab9e500723504935304a335738921035054310004b499263333573466e1cd55cea8022400046eb4d5d09aab9e500523504935304a3357389201035054310004b499263333573466e1cd55cea8012400046601664646464646464646464646666ae68cdc39aab9d500a480008cccccccccc064cd409c8c8c8cccd5cd19b8735573aa004900011980f981d1aba15002302c357426ae8940088d4164d4c168cd5ce249035054310005b49926135573ca00226ea8004d5d0a80519a8138141aba150093335502e75ca05a6ae854020ccd540b9d728169aba1500733502704335742a00c66a04e66aa0a8098eb4d5d0a8029919191999ab9a3370e6aae754009200023350213232323333573466e1cd55cea80124000466a05266a084eb4d5d0a80118239aba135744a00446a0ba6a60bc66ae712401035054310005f49926135573ca00226ea8004d5d0a8011919191999ab9a3370e6aae7540092000233502733504275a6ae854008c11cd5d09aba2500223505d35305e3357389201035054310005f49926135573ca00226ea8004d5d09aba2500223505935305a3357389201035054310005b49926135573ca00226ea8004d5d0a80219a813bae35742a00666a04e66aa0a8eb88004d5d0a801181c9aba135744a00446a0aa6a60ac66ae71241035054310005749926135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d5d1280089aba25001135573ca00226ea8004d5d0a8011919191999ab9a3370ea00290031180f181d9aba135573ca00646666ae68cdc3a801240084603a608a6ae84d55cf280211999ab9a3370ea00690011180e98181aba135573ca00a46666ae68cdc3a80224000460406eb8d5d09aab9e50062350503530513357389201035054310005249926499264984d55cea80089baa001357426ae8940088d4124d4c128cd5ce249035054310004b49926104a1350483530493357389201035054350004a4984d55cf280089baa0011375400226ea80048848cc00400c0088004888888888848cccccccccc00402c02802402001c01801401000c00880048848cc00400c008800448848cc00400c0084800448848cc00400c0084800448848cc00400c00848004848888c010014848888c00c014848888c008014848888c004014800448c88c008dd6000990009aa81a111999aab9f0012500e233500d30043574200460066ae880080cc8c8c8c8cccd5cd19b8735573aa006900011998039919191999ab9a3370e6aae754009200023300d303135742a00466a02605a6ae84d5d1280111a81b1a981b99ab9c491035054310003849926135573ca00226ea8004d5d0a801999aa805bae500a35742a00466a01eeb8d5d09aba25002235032353033335738921035054310003449926135744a00226aae7940044dd50009110919980080200180110009109198008018011000899aa800bae75a224464460046eac004c8004d540b888c8cccd55cf80112804919a80419aa81898031aab9d5002300535573ca00460086ae8800c0b84d5d08008891001091091198008020018900089119191999ab9a3370ea002900011a80418029aba135573ca00646666ae68cdc3a801240044a01046a0526a605466ae712401035054310002b499264984d55cea80089baa001121223002003112200112001232323333573466e1cd55cea8012400046600c600e6ae854008dd69aba135744a00446a0466a604866ae71241035054310002549926135573ca00226ea80048848cc00400c00880048c8cccd5cd19b8735573aa002900011bae357426aae7940088d407cd4c080cd5ce24810350543100021499261375400224464646666ae68cdc3a800a40084a00e46666ae68cdc3a8012400446a014600c6ae84d55cf280211999ab9a3370ea00690001280511a8111a981199ab9c490103505431000244992649926135573aa00226ea8004484888c00c0104488800844888004480048c8cccd5cd19b8750014800880188cccd5cd19b8750024800080188d4068d4c06ccd5ce249035054310001c499264984d55ce9baa0011220021220012001232323232323333573466e1d4005200c200b23333573466e1d4009200a200d23333573466e1d400d200823300b375c6ae854014dd69aba135744a00a46666ae68cdc3a8022400c46601a6eb8d5d0a8039bae357426ae89401c8cccd5cd19b875005480108cc048c050d5d0a8049bae357426ae8940248cccd5cd19b875006480088c050c054d5d09aab9e500b23333573466e1d401d2000230133016357426aae7940308d407cd4c080cd5ce2481035054310002149926499264992649926135573aa00826aae79400c4d55cf280109aab9e500113754002424444444600e01044244444446600c012010424444444600a010244444440082444444400644244444446600401201044244444446600201201040024646464646666ae68cdc3a800a400446660106eb4d5d0a8021bad35742a0066eb4d5d09aba2500323333573466e1d400920002300a300b357426aae7940188d4040d4c044cd5ce2490350543100012499264984d55cea80189aba25001135573ca00226ea80048488c00800c888488ccc00401401000c80048c8c8cccd5cd19b875001480088c018dd71aba135573ca00646666ae68cdc3a80124000460106eb8d5d09aab9e500423500a35300b3357389201035054310000c499264984d55cea80089baa001212230020032122300100320011122232323333573466e1cd55cea80124000466aa016600c6ae854008c014d5d09aba25002235007353008335738921035054310000949926135573ca00226ea8004498480048004448848cc00400c008448004448c8c00400488cc00cc008008004ccc888c8c8ccc888ccc888cccccccc88888888cc88ccccc88888cccc8888cc88cc88cc88ccc888cc88cc88ccc888cc88cc88cc88cc88888cc894cd4c0e4008400440e8ccd40d540d800d205433350355036002481508848cc00400c0088004888888888848cccccccccc00402c02802402001c01801401000c00880048848cc00400c008800488848ccc00401000c00880044488008488488cc00401000c48004448848cc00400c0084480048848cc00400c008800448488c00800c44880044800448848cc00400c0084800448848cc00400c0084800448848cc00400c00848004484888c00c010448880084488800448004848888c010014848888c00c014848888c008014848888c00401480048848cc00400c0088004848888888c01c0208848888888cc018024020848888888c014020488888880104888888800c8848888888cc0080240208848888888cc00402402080048488c00800c888488ccc00401401000c80048488c00800c8488c00400c800448004488ccd5cd19b87002001005004122002122001200101")
                .build();

        String collateral = "ab44e0f5faf56154cc33e757c9d98a60666346179d5a7a0b9d77734c23c42082";
        int collateralIndex = 0;

        Tuple<String, Integer> collateralTuple = checkCollateral(sender, collateral, collateralIndex);
        if (collateralTuple == null) {
            System.out.println("Collateral cannot be found or created. " + collateral);
            return;
        }
        collateral = collateralTuple._1;
        collateralIndex = collateralTuple._2;

        //Custom guess contract setup
        String customGuessScriptAddress = AddressProvider.getEntAddress(customGuessScript, Networks.testnet()).getAddress();
        System.out.println("Script Address: " + customGuessScriptAddress);

        Guess guess = new Guess(Integer.valueOf(42));

        Optional<Utxo> customGuessUtxoOptional = ScriptUtxoFinders.findFirstByDatumHashUsingDatum(utxoSupplier, customGuessScriptAddress, guess);
        //Start contract transaction to claim fund
        if (!customGuessUtxoOptional.isPresent()) {
            System.out.println("No utxo found...Let's transfer some Ada to script address");
            boolean paymentSuccessful = transferToContractAddress(sender, customGuessScriptAddress, adaToLovelace(5),
                    Configuration.INSTANCE.getPlutusObjectConverter().toPlutusData(guess).getDatumHash(), collateral, collateralIndex);
            customGuessUtxoOptional = ScriptUtxoFinders.findFirstByDatumHashUsingDatum(utxoSupplier, customGuessScriptAddress, guess);
            assertTrue(paymentSuccessful);
        }

        //Sum contract
        PlutusV1Script sumScript = PlutusV1Script.builder()
                .type("PlutusScriptV1")
                .cborHex("5909de5909db01000033233223322323233322232333222323333333322222222323332223233332222323233223233322232333222323233223322323233333222223322332233223322332233222222323253353031333006375a00a6eb4010cccd5cd19b8735573aa004900011980499191919191919191919191999ab9a3370e6aae754029200023333333333017335025232323333573466e1cd55cea8012400046603a60706ae854008c0a8d5d09aba250022350573530583357389201035054310005949926135573ca00226ea8004d5d0a80519a8128131aba150093335502c75ca0566ae854020ccd540b1d728159aba1500733502504135742a00c66a04a66aa0a4094eb4d5d0a8029919191999ab9a3370e6aae7540092000233501f3232323333573466e1cd55cea80124000466a04e66a080eb4d5d0a80118229aba135744a00446a0b66a60b866ae712401035054310005d49926135573ca00226ea8004d5d0a8011919191999ab9a3370e6aae7540092000233502533504075a6ae854008c114d5d09aba2500223505b35305c3357389201035054310005d49926135573ca00226ea8004d5d09aba250022350573530583357389201035054310005949926135573ca00226ea8004d5d0a80219a812bae35742a00666a04a66aa0a4eb88004d5d0a801181b9aba135744a00446a0a66a60a866ae71241035054310005549926135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d5d1280089aba25001135573ca00226ea8004d5d0a8011919191999ab9a3370ea00290031180e181c9aba135573ca00646666ae68cdc3a801240084603660866ae84d55cf280211999ab9a3370ea00690011180d98171aba135573ca00a46666ae68cdc3a802240004603c6eb8d5d09aab9e500623504e35304f3357389201035054310005049926499264984d55cea80089baa001357426ae8940088d411cd4c120cd5ce2490350543100049499261048135046353047335738920103505435000484984d55cf280089baa0012212330010030022001222222222212333333333300100b00a00900800700600500400300220012212330010030022001122123300100300212001122123300100300212001122123300100300212001212222300400521222230030052122223002005212222300100520011232230023758002640026aa068446666aae7c004940388cd4034c010d5d080118019aba200203323232323333573466e1cd55cea801a4000466600e6464646666ae68cdc39aab9d5002480008cc034c0c4d5d0a80119a8098169aba135744a00446a06c6a606e66ae712401035054310003849926135573ca00226ea8004d5d0a801999aa805bae500a35742a00466a01eeb8d5d09aba25002235032353033335738921035054310003449926135744a00226aae7940044dd50009110919980080200180110009109198008018011000899aa800bae75a224464460046eac004c8004d540b888c8cccd55cf80112804919a80419aa81898031aab9d5002300535573ca00460086ae8800c0b84d5d08008891001091091198008020018900089119191999ab9a3370ea002900011a80418029aba135573ca00646666ae68cdc3a801240044a01046a0526a605466ae712401035054310002b499264984d55cea80089baa001121223002003112200112001232323333573466e1cd55cea8012400046600c600e6ae854008dd69aba135744a00446a0466a604866ae71241035054310002549926135573ca00226ea80048848cc00400c00880048c8cccd5cd19b8735573aa002900011bae357426aae7940088d407cd4c080cd5ce24810350543100021499261375400224464646666ae68cdc3a800a40084a00e46666ae68cdc3a8012400446a014600c6ae84d55cf280211999ab9a3370ea00690001280511a8111a981199ab9c490103505431000244992649926135573aa00226ea8004484888c00c0104488800844888004480048c8cccd5cd19b8750014800880188cccd5cd19b8750024800080188d4068d4c06ccd5ce249035054310001c499264984d55ce9baa0011220021220012001232323232323333573466e1d4005200c200b23333573466e1d4009200a200d23333573466e1d400d200823300b375c6ae854014dd69aba135744a00a46666ae68cdc3a8022400c46601a6eb8d5d0a8039bae357426ae89401c8cccd5cd19b875005480108cc048c050d5d0a8049bae357426ae8940248cccd5cd19b875006480088c050c054d5d09aab9e500b23333573466e1d401d2000230133016357426aae7940308d407cd4c080cd5ce2481035054310002149926499264992649926135573aa00826aae79400c4d55cf280109aab9e500113754002424444444600e01044244444446600c012010424444444600a010244444440082444444400644244444446600401201044244444446600201201040024646464646666ae68cdc3a800a400446660106eb4d5d0a8021bad35742a0066eb4d5d09aba2500323333573466e1d400920002300a300b357426aae7940188d4040d4c044cd5ce2490350543100012499264984d55cea80189aba25001135573ca00226ea80048488c00800c888488ccc00401401000c80048c8c8cccd5cd19b875001480088c018dd71aba135573ca00646666ae68cdc3a80124000460106eb8d5d09aab9e500423500a35300b3357389201035054310000c499264984d55cea80089baa001212230020032122300100320011122232323333573466e1cd55cea80124000466aa016600c6ae854008c014d5d09aba25002235007353008335738921035054310000949926135573ca00226ea8004498480048004448848cc00400c008448004448c8c00400488cc00cc008008004c8ccc888c8ccc888ccc888cccccccc88888888cc88ccccc88888cccc8888cc88cc88cc88ccc888cc88cc88ccc888cc88cc88cc88cc88888ccd5cd19b8733035003480000080e00dc8848cc00400c0088004888888888848cccccccccc00402c02802402001c01801401000c00880048848cc00400c008800488848ccc00401000c00880044488008488488cc00401000c48004448848cc00400c0084480048848cc00400c008800448488c00800c44880044800448848cc00400c0084800448848cc00400c0084800448848cc00400c00848004484888c00c010448880084488800448004848888c010014848888c00c014848888c008014848888c00401480048848cc00400c0088004848888888c01c0208848888888cc018024020848888888c014020488888880104888888800c8848888888cc0080240208848888888cc00402402080048488c00800c888488ccc00401401000c80048488c00800c8488c00400c8004c8004c8c8c00400488cc00cc0080080048894cd4c010c8ccd5cd19b8700300100700630074800040044cc00cc8cdc08018009803a400466e0000800448800848800480048005")
                .build();

        Integer sumDatum = 3;
        Integer sumRedeemer = 6;

        //Custom guess contract setup
        String sumScriptAddress = AddressProvider.getEntAddress(sumScript, Networks.testnet()).getAddress();
        System.out.println("Sum Script Address: " + sumScriptAddress);

        Optional<Utxo> sumUtxoOptional = ScriptUtxoFinders.findFirstByDatumHashUsingDatum(utxoSupplier, sumScriptAddress, sumDatum);
        //Start contract transaction to claim fund
        if (!sumUtxoOptional.isPresent()) {
            System.out.println("No utxo found for sum contract...Let's transfer some Ada to script address");
            System.out.println("Sum Datum hash >>> " + Configuration.INSTANCE.getPlutusObjectConverter().toPlutusData(sumDatum).getDatumHash());
            boolean paymentSuccessful = transferToContractAddress(sender, sumScriptAddress, adaToLovelace(6),
                    Configuration.INSTANCE.getPlutusObjectConverter().toPlutusData(sumDatum).getDatumHash(), collateral, collateralIndex);
            sumUtxoOptional = ScriptUtxoFinders.findFirstByDatumHashUsingDatum(utxoSupplier, sumScriptAddress, sumDatum);
            assertTrue(paymentSuccessful);
        }

        Utxo customGuessUtxo = customGuessUtxoOptional.get();
        BigInteger customGuessClaimableAmt = customGuessUtxo.getAmount().stream().filter(amount -> LOVELACE.equals(amount.getUnit()))
                .findFirst()
                .map(amount -> amount.getQuantity())
                .orElseGet(() -> BigInteger.ZERO);

        System.out.println("Guess utxo... " + customGuessUtxoOptional.get().getTxHash());

        Utxo sumScriptUtxo = sumUtxoOptional.get();
        BigInteger sumClaimableAmt = sumUtxoOptional.get().getAmount().stream().filter(amount -> LOVELACE.equals(amount.getUnit()))
                .findFirst()
                .map(amount -> amount.getQuantity())
                .orElseGet(() -> BigInteger.ZERO);

        System.out.println("Sum utxo.... " + sumScriptUtxo.getTxHash());

        Output customGuessContractOutput = Output.builder()
                .address(sender.baseAddress())
                .assetName(LOVELACE)
                .qty(customGuessClaimableAmt)
                .build();

        //sum contract
        Output sumContractOutput = Output.builder()
                .address(sender.baseAddress())
                .assetName(LOVELACE)
                .qty(sumClaimableAmt)
                .build();

        ExUnits exUnits = ExUnits.builder()
                .mem(BigInteger.valueOf(1676948))
                .steps(BigInteger.valueOf(430892334)).build();

        ExUnits sumExUnits = ExUnits.builder()
                .mem(BigInteger.valueOf(989624))
                .steps(BigInteger.valueOf(314842019)).build();

        TxBuilder builder = customGuessContractOutput.outputBuilder()
                .and(sumContractOutput.outputBuilder())
                .buildInputs(createFromUtxos(List.of(customGuessUtxo, sumScriptUtxo), senderAddress))
                .andThen(collateralFrom(collateral, collateralIndex))
                .andThen(scriptCallContext(customGuessScript, customGuessUtxo, guess, guess, RedeemerTag.Spend, exUnits))
                .andThen(scriptCallContext(sumScript, sumScriptUtxo, sumDatum, sumRedeemer, RedeemerTag.Spend, sumExUnits))
                .andThen(balanceTx(senderAddress, 1));

        TxSigner signer = SignerProviders.signerFrom(sender);

        var txContext = TxBuilderContext.init(utxoSupplier, protocolParams);
        Transaction transaction = TxBuilderContext.init(utxoSupplier, protocolParams)
                .withSerializationEra(Era.Babbage)
                .build(builder);
        Transaction signedTxn = signer.sign(txContext, transaction);

        //Or build and sign in one call
        //  Transaction signedTxn = TxBuilderContext.init(backendService)
        //          .buildAndSign(builder, signer);

        System.out.println("Signer: " + senderAddress);
        System.out.println(transaction);

        Result<String> result = transactionService.submitTransaction(signedTxn.serialize());
        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);
    }

    @Test
    void mint() throws Exception {
        String receiverAddress = "addr_test1qrs2a2hjfs2wt8r3smzwmptezmave3yjgws068hp0qsflmcypglx0rl69tp49396282ns02caz4cx7a2n290h2df0j3qjku4dy";
        PlutusV1Script mintScript = PlutusV1Script.builder()
                .type("PlutusScriptV1")
                .cborHex("59083159082e010000323322332232323233322232333222323333333322222222323332223233332222323233223233322232333222323233223322323233333222223322332233223322332233222232325335302f332203330430043333573466e1cd55cea8012400046600e64646464646464646464646666ae68cdc39aab9d500a480008cccccccccc054cd408c8c8c8cccd5cd19b8735573aa004900011980d981b1aba150023028357426ae8940088d4158d4c15ccd5ce249035054310005849926135573ca00226ea8004d5d0a80519a8118121aba150093335502a75ca0526ae854020ccd540a9d728149aba1500733502303f35742a00c66a04666aa0a2090eb4d5d0a8029919191999ab9a3370e6aae7540092000233501d3232323333573466e1cd55cea80124000466a04a66a07ceb4d5d0a80118219aba135744a00446a0b46a60b666ae712401035054310005c49926135573ca00226ea8004d5d0a8011919191999ab9a3370e6aae7540092000233502333503e75a6ae854008c10cd5d09aba2500223505a35305b3357389201035054310005c49926135573ca00226ea8004d5d09aba250022350563530573357389201035054310005849926135573ca00226ea8004d5d0a80219a811bae35742a00666a04666aa0a2eb8140d5d0a801181a9aba135744a00446a0a46a60a666ae712401035054310005449926135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d5d1280089aba25001135573ca00226ea8004d5d0a8011919191999ab9a3370ea00290031180d181b9aba135573ca00646666ae68cdc3a801240084603260826ae84d55cf280211999ab9a3370ea00690011180c98161aba135573ca00a46666ae68cdc3a80224000460386eb8d5d09aab9e500623504d35304e3357389201035054310004f49926499264984d55cea80089baa001357426ae8940088d4118d4c11ccd5ce2490350543100048499261047135045353046335738920103505435000474984d55cf280089baa0012212330010030022001222222222212333333333300100b00a00900800700600500400300220012212330010030022001122123300100300212001122123300100300212001122123300100300212001212222300400521222230030052122223002005212222300100520011232230023758002640026aa06a446666aae7c004940388cd4034c010d5d080118019aba200203423232323333573466e1cd55cea801a4000466600e6464646666ae68cdc39aab9d5002480008cc034c0c4d5d0a80119a8098169aba135744a00446a06e6a607066ae712401035054310003949926135573ca00226ea8004d5d0a801999aa805bae500a35742a00466a01eeb8d5d09aba25002235033353034335738921035054310003549926135744a00226aae7940044dd50009110919980080200180110009109198008018011000899aa800bae75a224464460046eac004c8004d540bc88c8cccd55cf80112804919a80419aa81918031aab9d5002300535573ca00460086ae8800c0bc4d5d08008891001091091198008020018900089119191999ab9a3370ea002900011a80418029aba135573ca00646666ae68cdc3a801240044a01046a0546a605666ae712401035054310002c499264984d55cea80089baa001121223002003112200112001232323333573466e1cd55cea8012400046600c600e6ae854008dd69aba135744a00446a0486a604a66ae71241035054310002649926135573ca00226ea80048848cc00400c00880048c8cccd5cd19b8735573aa002900011bae357426aae7940088d4080d4c084cd5ce24810350543100022499261375400224464646666ae68cdc3a800a40084a00e46666ae68cdc3a8012400446a014600c6ae84d55cf280211999ab9a3370ea00690001280511a8119a981219ab9c490103505431000254992649926135573aa00226ea8004484888c00c0104488800844888004480048c8cccd5cd19b8750014800880188cccd5cd19b8750024800080188d406cd4c070cd5ce249035054310001d499264984d55ce9baa0011220021220012001232323232323333573466e1d4005200c200b23333573466e1d4009200a200d23333573466e1d400d200823300b375c6ae854014dd69aba135744a00a46666ae68cdc3a8022400c46601a6eb8d5d0a8039bae357426ae89401c8cccd5cd19b875005480108cc048c050d5d0a8049bae357426ae8940248cccd5cd19b875006480088c050c054d5d09aab9e500b23333573466e1d401d2000230133016357426aae7940308d4080d4c084cd5ce2481035054310002249926499264992649926135573aa00826aae79400c4d55cf280109aab9e500113754002424444444600e01044244444446600c012010424444444600a010244444440082444444400644244444446600401201044244444446600201201040024646464646666ae68cdc3a800a400446660106eb4d5d0a8021bad35742a0066eb4d5d09aba2500323333573466e1d400920002300a300b357426aae7940188d4044d4c048cd5ce2490350543100013499264984d55cea80189aba25001135573ca00226ea80048488c00800c888488ccc00401401000c80048c8c8cccd5cd19b875001480088c018dd71aba135573ca00646666ae68cdc3a80124000460106eb8d5d09aab9e500423500b35300c3357389201035054310000d499264984d55cea80089baa0012122300200321223001003200120011122232323333573466e1cd55cea80124000466aa016600c6ae854008c014d5d09aba25002235007353008335738921035054310000949926135573ca00226ea8004498480048004448848cc00400c008448004448c8c00400488cc00cc0080080041")
                .build();

        //-- Custom guess contract setup
        String mintScriptAddress = AddressProvider.getEntAddress(mintScript, Networks.testnet()).getAddress();
        System.out.println("Mint Script Address: " + mintScriptAddress);

        //-- Setup collateral
        String collateral = "ab44e0f5faf56154cc33e757c9d98a60666346179d5a7a0b9d77734c23c42082";
        int collateralIndex = 0;

        Tuple<String, Integer> collateralTuple = checkCollateral(sender, collateral, collateralIndex);
        if (collateralTuple == null) {
            System.out.println("Collateral cannot be found or created. " + collateral);
            return;
        }
        collateral = collateralTuple._1;
        collateralIndex = collateralTuple._2;

        PlutusData redeemerData = new BigIntPlutusData(BigInteger.valueOf(4444)); //any redeemer .. doesn't matter

        MultiAsset multiAsset = MultiAsset.builder()
                .policyId(mintScript.getPolicyId())
                .assets(Arrays.asList(
                        Asset.builder()
                                .name("ScriptToken")
                                .value(BigInteger.valueOf(2))
                                .build()))
                .build();


        TransactionOutput mintOutput = TransactionOutput
                .builder()
                .address(receiverAddress)
                .value(new Value(BigInteger.ZERO, Arrays.asList(multiAsset)))
                .build();

        MessageMetadata metadata = MessageMetadata.create()
                .add("NFT minted by Plutus script");

        ExUnits exUnits = ExUnits.builder()
                .mem(BigInteger.valueOf(989624))
                .steps(BigInteger.valueOf(514842019)).build();

        TxBuilder txBuilder = OutputBuilders.createFromMintOutput(mintOutput)
                .buildInputs(InputBuilders.createFromSender(senderAddress, senderAddress))
                .andThen(CollateralBuilders.collateralFrom(collateral, collateralIndex))
                .andThen(MintCreators.mintCreator(mintScript, multiAsset))
                .andThen(ScriptCallContextProviders.scriptCallContext(mintScript, null, null, redeemerData, RedeemerTag.Mint, exUnits))
                .andThen(AuxDataProviders.metadataProvider(metadata))
                .andThen(BalanceTxBuilders.balanceTx(senderAddress, 1));

        TxSigner signer = SignerProviders.signerFrom(sender);

        Transaction signTxn = TxBuilderContext.init(utxoSupplier, protocolParams)
                .withSerializationEra(Era.Babbage)
                .buildAndSign(txBuilder, signer);

        System.out.println(signTxn);
        Result<String> result = transactionService.submitTransaction(signTxn.serialize());
        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);
    }

    @Test
    void mint_whenReceiverIsSender() throws Exception {
        PlutusV1Script mintScript = PlutusV1Script.builder()
                .type("PlutusScriptV1")
                .cborHex("59083159082e010000323322332232323233322232333222323333333322222222323332223233332222323233223233322232333222323233223322323233333222223322332233223322332233222232325335302f332203330430043333573466e1cd55cea8012400046600e64646464646464646464646666ae68cdc39aab9d500a480008cccccccccc054cd408c8c8c8cccd5cd19b8735573aa004900011980d981b1aba150023028357426ae8940088d4158d4c15ccd5ce249035054310005849926135573ca00226ea8004d5d0a80519a8118121aba150093335502a75ca0526ae854020ccd540a9d728149aba1500733502303f35742a00c66a04666aa0a2090eb4d5d0a8029919191999ab9a3370e6aae7540092000233501d3232323333573466e1cd55cea80124000466a04a66a07ceb4d5d0a80118219aba135744a00446a0b46a60b666ae712401035054310005c49926135573ca00226ea8004d5d0a8011919191999ab9a3370e6aae7540092000233502333503e75a6ae854008c10cd5d09aba2500223505a35305b3357389201035054310005c49926135573ca00226ea8004d5d09aba250022350563530573357389201035054310005849926135573ca00226ea8004d5d0a80219a811bae35742a00666a04666aa0a2eb8140d5d0a801181a9aba135744a00446a0a46a60a666ae712401035054310005449926135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d5d1280089aba25001135573ca00226ea8004d5d0a8011919191999ab9a3370ea00290031180d181b9aba135573ca00646666ae68cdc3a801240084603260826ae84d55cf280211999ab9a3370ea00690011180c98161aba135573ca00a46666ae68cdc3a80224000460386eb8d5d09aab9e500623504d35304e3357389201035054310004f49926499264984d55cea80089baa001357426ae8940088d4118d4c11ccd5ce2490350543100048499261047135045353046335738920103505435000474984d55cf280089baa0012212330010030022001222222222212333333333300100b00a00900800700600500400300220012212330010030022001122123300100300212001122123300100300212001122123300100300212001212222300400521222230030052122223002005212222300100520011232230023758002640026aa06a446666aae7c004940388cd4034c010d5d080118019aba200203423232323333573466e1cd55cea801a4000466600e6464646666ae68cdc39aab9d5002480008cc034c0c4d5d0a80119a8098169aba135744a00446a06e6a607066ae712401035054310003949926135573ca00226ea8004d5d0a801999aa805bae500a35742a00466a01eeb8d5d09aba25002235033353034335738921035054310003549926135744a00226aae7940044dd50009110919980080200180110009109198008018011000899aa800bae75a224464460046eac004c8004d540bc88c8cccd55cf80112804919a80419aa81918031aab9d5002300535573ca00460086ae8800c0bc4d5d08008891001091091198008020018900089119191999ab9a3370ea002900011a80418029aba135573ca00646666ae68cdc3a801240044a01046a0546a605666ae712401035054310002c499264984d55cea80089baa001121223002003112200112001232323333573466e1cd55cea8012400046600c600e6ae854008dd69aba135744a00446a0486a604a66ae71241035054310002649926135573ca00226ea80048848cc00400c00880048c8cccd5cd19b8735573aa002900011bae357426aae7940088d4080d4c084cd5ce24810350543100022499261375400224464646666ae68cdc3a800a40084a00e46666ae68cdc3a8012400446a014600c6ae84d55cf280211999ab9a3370ea00690001280511a8119a981219ab9c490103505431000254992649926135573aa00226ea8004484888c00c0104488800844888004480048c8cccd5cd19b8750014800880188cccd5cd19b8750024800080188d406cd4c070cd5ce249035054310001d499264984d55ce9baa0011220021220012001232323232323333573466e1d4005200c200b23333573466e1d4009200a200d23333573466e1d400d200823300b375c6ae854014dd69aba135744a00a46666ae68cdc3a8022400c46601a6eb8d5d0a8039bae357426ae89401c8cccd5cd19b875005480108cc048c050d5d0a8049bae357426ae8940248cccd5cd19b875006480088c050c054d5d09aab9e500b23333573466e1d401d2000230133016357426aae7940308d4080d4c084cd5ce2481035054310002249926499264992649926135573aa00826aae79400c4d55cf280109aab9e500113754002424444444600e01044244444446600c012010424444444600a010244444440082444444400644244444446600401201044244444446600201201040024646464646666ae68cdc3a800a400446660106eb4d5d0a8021bad35742a0066eb4d5d09aba2500323333573466e1d400920002300a300b357426aae7940188d4044d4c048cd5ce2490350543100013499264984d55cea80189aba25001135573ca00226ea80048488c00800c888488ccc00401401000c80048c8c8cccd5cd19b875001480088c018dd71aba135573ca00646666ae68cdc3a80124000460106eb8d5d09aab9e500423500b35300c3357389201035054310000d499264984d55cea80089baa0012122300200321223001003200120011122232323333573466e1cd55cea80124000466aa016600c6ae854008c014d5d09aba25002235007353008335738921035054310000949926135573ca00226ea8004498480048004448848cc00400c008448004448c8c00400488cc00cc0080080041")
                .build();

        //-- Custom guess contract setup
        String mintScriptAddress = AddressProvider.getEntAddress(mintScript, Networks.testnet()).getAddress();
        System.out.println("Mint Script Address: " + mintScriptAddress);

        //-- Setup collateral
        String collateral = "ab44e0f5faf56154cc33e757c9d98a60666346179d5a7a0b9d77734c23c42082";
        int collateralIndex = 0;

        Tuple<String, Integer> collateralTuple = checkCollateral(sender, collateral, collateralIndex);
        if (collateralTuple == null) {
            System.out.println("Collateral cannot be found or created. " + collateral);
            return;
        }
        collateral = collateralTuple._1;
        collateralIndex = collateralTuple._2;

        PlutusData redeemerData = new BigIntPlutusData(BigInteger.valueOf(4444)); //any redeemer .. doesn't matter

        MultiAsset multiAsset = MultiAsset.builder()
                .policyId(mintScript.getPolicyId())
                .assets(Arrays.asList(
                        Asset.builder()
                                .name("ScriptToken")
                                .value(BigInteger.valueOf(2))
                                .build()))
                .build();


        TransactionOutput mintOutput = TransactionOutput
                .builder()
                .address(senderAddress)
                .value(new Value(BigInteger.ZERO, Arrays.asList(multiAsset)))
                .build();

        MessageMetadata metadata = MessageMetadata.create()
                .add("NFT minted by Plutus script");

        ExUnits exUnits = ExUnits.builder()
                .mem(BigInteger.valueOf(989624))
                .steps(BigInteger.valueOf(514842019)).build();

        TxBuilder txBuilder = OutputBuilders.createFromMintOutput(mintOutput)
                .buildInputs(InputBuilders.createFromSender(senderAddress, senderAddress))
                .andThen(CollateralBuilders.collateralFrom(collateral, collateralIndex))
                .andThen(MintCreators.mintCreator(mintScript, multiAsset))
                .andThen(ScriptCallContextProviders.scriptCallContext(mintScript, null, null, redeemerData, RedeemerTag.Mint, exUnits))
                .andThen(AuxDataProviders.metadataProvider(metadata))
                .andThen(BalanceTxBuilders.balanceTx(senderAddress, 1));

        TxSigner signer = SignerProviders.signerFrom(sender);

        Transaction signTxn = TxBuilderContext.init(utxoSupplier, protocolParams)
                .withSerializationEra(Era.Babbage)
                .buildAndSign(txBuilder, signer);

        System.out.println(signTxn);
        Result<String> result = transactionService.submitTransaction(signTxn.serialize());
        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);
    }

    private long getTtl() throws ApiException {
        Block block = blockService.getLatestBlock().getValue();
        long slot = block.getSlot();
        return slot + 2000;
    }

    private Tuple<String, Integer> checkCollateral(Account sender, final String collateralUtxoHash, final int collateralIndex) throws ApiException, AddressExcepion, CborSerializationException {
        List<Utxo> utxos = utxoService.getUtxos(sender.baseAddress(), 100, 1).getValue(); //Check 1st page 100 utxos
        Optional<Utxo> collateralUtxoOption = utxos.stream().filter(utxo -> utxo.getTxHash().equals(collateralUtxoHash))
                .findAny();

        if (collateralUtxoOption.isPresent()) {//Collateral present
            System.out.println("--- Collateral utxo still there");
            return new Tuple(collateralUtxoHash, collateralIndex);
        } else {

            Utxo randomCollateral = getRandomUtxoForCollateral(sender.baseAddress());
            if (randomCollateral != null) {
                System.out.println("Found random collateral ---");
                return new Tuple<>(randomCollateral.getTxHash(), randomCollateral.getOutputIndex());
            } else {
                System.out.println("*** Collateral utxo not found");

                //Transfer to self to create collateral utxo
                BigInteger collateralAmt = BigInteger.valueOf(8000000L);
                transferFund(sender, sender.baseAddress(), collateralAmt, null, null, null);

                //Find collateral utxo again
                utxos = utxoService.getUtxos(sender.baseAddress(), 100, 1).getValue();
                collateralUtxoOption = utxos.stream().filter(utxo -> {
                    if (utxo.getAmount().size() == 1 //Assumption: 1 Amount means, only LOVELACE
                            && LOVELACE.equals(utxo.getAmount().get(0).getUnit())
                            && collateralAmt.equals(utxo.getAmount().get(0).getQuantity()))
                        return true;
                    else
                        return false;
                }).findFirst();

                if (!collateralUtxoOption.isPresent()) {
                    System.out.println("Collateral cannot be created");
                    return null;
                }

                Utxo collateral = collateralUtxoOption.get();
                String colUtxoHash = collateral.getTxHash();
                int colIndex = collateral.getOutputIndex();

                return new Tuple(colUtxoHash, colIndex);
            }
        }
    }

    private boolean transferFund(Account sender, String recevingAddress, BigInteger amount, String datumHash, String collateralUtxoHash, Integer collateralIndex) throws CborSerializationException, AddressExcepion, ApiException {

        //Ignore collateral utxos
        Set ignoreUtxos = new HashSet();
        if (collateralUtxoHash != null) {
            Utxo collateralUtxo = Utxo.builder()
                    .txHash(collateralUtxoHash)
                    .outputIndex(collateralIndex)
                    .build();
            ignoreUtxos.add(collateralUtxo);
        }

        UtxoSelectionStrategy utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
        List<Utxo> utxos = utxoSelectionStrategy.selectUtxos(sender.baseAddress(), LOVELACE, amount, ignoreUtxos);

        PaymentTransaction paymentTransaction =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(recevingAddress)
                        .amount(amount)
                        .unit("lovelace")
                        .datumHash(datumHash)
                        .utxosToInclude(utxos)
                        .build();

        BigInteger fee = feeCalculationService.calculateFee(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), null);
        paymentTransaction.setFee(fee);

        Result<TransactionResult> result = transactionHelperService.transfer(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build());
        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        if (result.isSuccessful()) {
            Result<String> resultWithTxId = Result.success(result.getResponse()).code(result.code())
                    .withValue(result.getValue().getTransactionId());

            waitForTransaction(resultWithTxId);
        } else {
            System.out.println(result);
        }

        return result.isSuccessful();
    }

    private boolean transferToContractAddress(Account sender, String scriptAddress, BigInteger amount, String datumHash,
                                              String collateralTxHash, int collateralIndex) throws CborSerializationException, AddressExcepion, ApiException {

        Utxo collateralUtxo = Utxo.builder()
                .txHash(collateralTxHash)
                .outputIndex(collateralIndex)
                .build();
        Set ignoreUtxos = new HashSet();
        ignoreUtxos.add(collateralUtxo);

        UtxoSelectionStrategy utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
        List<Utxo> utxos = utxoSelectionStrategy.selectUtxos(sender.baseAddress(), LOVELACE, amount.add(adaToLovelace(2)), ignoreUtxos); //2 extra ada for buffer

        PaymentTransaction paymentTransaction =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(scriptAddress)
                        .amount(amount)
                        .unit("lovelace")
                        .datumHash(datumHash)
                        .utxosToInclude(utxos)
                        .build();

        BigInteger fee = feeCalculationService.calculateFee(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), null);
        paymentTransaction.setFee(fee);

        Result<TransactionResult> result = transactionHelperService.transfer(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build());
        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        if (result.isSuccessful()) {
            Result<String> resultWithTxId = Result.success(result.getResponse()).code(result.code())
                    .withValue(result.getValue().getTransactionId());

            waitForTransaction(resultWithTxId);
            checkIfUtxoAvailable(resultWithTxId.getValue(), scriptAddress);
        } else {
            System.out.println(result);
        }

        return result.isSuccessful();
    }


    private Utxo getRandomUtxoForCollateral(String address) throws ApiException {
        UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoSupplier);
        //Find 5 > utxo > 10 ada
        Optional<Utxo> optional = utxoSelector.findFirst(address, u -> {
            if (u.getAmount().size() == 1
                    && u.getAmount().get(0).getQuantity().compareTo(adaToLovelace(5)) == 1
                    && u.getAmount().get(0).getQuantity().compareTo(adaToLovelace(10)) == -1)
                return true;
            else
                return false;
        });

        return optional.orElse(null);
    }

    private void waitForTransaction(Result<String> result) {
        try {
            if (result.isSuccessful()) { //Wait for transaction to be mined
                int count = 0;
                while (count < 60) {
                    Result<TransactionContent> txnResult = backendService.getTransactionService().getTransaction(result.getValue());
                    if (txnResult.isSuccessful()) {
                        System.out.println(JsonUtil.getPrettyJson(txnResult.getValue()));
                        break;
                    } else {
                        System.out.println("Waiting for transaction to be mined ....");
                    }

                    count++;
                    Thread.sleep(2000);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Constr
    static class Guess {
        @PlutusField
        Integer number;

        public Guess(int number) {
            this.number = number;
        }
    }
}
