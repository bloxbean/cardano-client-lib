package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.impl.StaticTransactionEvaluator;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.transaction.spec.ProtocolParamUpdate;
import com.bloxbean.cardano.client.transaction.spec.cert.*;
import com.bloxbean.cardano.client.transaction.spec.governance.*;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovActionId;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.ParameterChangeAction;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class GovTxScriptTest extends QuickTxBaseTest {
    @Mock
    private UtxoSupplier utxoSupplier;
    private ProtocolParamsSupplier protocolParamsSupplier;
    @Mock
    private TransactionProcessor transactionProcessor;

    String sender1 = new Account().baseAddress();
    String receiver1 = new Account().baseAddress();

    @BeforeEach
    @SneakyThrows
    public void setup() {
        MockitoAnnotations.openMocks(this);
        protocolParamJsonFile = "protocol-params.json";
        ProtocolParams protocolParams = (ProtocolParams) loadObjectFromJson("protocol-parameters", ProtocolParams.class);
        protocolParamsSupplier = () -> protocolParams;
    }

    @Test
    void drepRegistration() throws CborSerializationException {
        given(utxoSupplier.getPage(eq(sender1), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build(),
                        Utxo.builder()
                                .address(sender1)
                                .txHash("9a6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(50)))
                                .build()
                )
        );

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var protocolParams = protocolParamsSupplier.getProtocolParams();

        PlutusV3Script plutusScript = PlutusV3Script.builder()
                .type("PlutusScriptV3")
                .cborHex("49480100002221200101")
                .build();

        var scriptHash = plutusScript.getScriptHash();
        var scriptCredential = Credential.fromScript(scriptHash);

        var anchor = new Anchor("https://test.com/test.json",
                HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        ScriptTx drepRegTx = new ScriptTx(protocolParams)
                .registerDRep(scriptCredential, anchor, BigIntPlutusData.of(1))
                .attachCertificateValidator(plutusScript);

        var transaction = quickTxBuilder.compose(drepRegTx)
                .feePayer(sender1)
                .withTxEvaluator(new StaticTransactionEvaluator(List.of(ExUnits.builder()
                        .mem(BigInteger.valueOf(800))
                        .steps(BigInteger.valueOf(1000000))
                        .build())
                ))
                .build();

        assertThat(transaction.getBody().getFee()).isGreaterThan(BigInteger.ZERO);
        assertThat(transaction.getBody().getCerts().size()).isEqualTo(1);
        assertThat(transaction.getBody().getCerts().get(0)).isInstanceOf(RegDRepCert.class);
        assertThat(transaction.getWitnessSet().getPlutusV3Scripts()).contains(plutusScript);
        assertThat(transaction.getWitnessSet().getRedeemers()).hasSize(1);
        assertThat(transaction.getBody().getInputs()).hasSize(1);
        assertThat(transaction.getBody().getOutputs()).hasSize(1);
    }

    @Test
    void drepUnRegistration() throws CborSerializationException {
        given(utxoSupplier.getPage(eq(sender1), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build(),
                        Utxo.builder()
                                .address(sender1)
                                .txHash("9a6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(50)))
                                .build()
                )
        );

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var protocolParams = protocolParamsSupplier.getProtocolParams();

        PlutusV3Script plutusScript = PlutusV3Script.builder()
                .type("PlutusScriptV3")
                .cborHex("49480100002221200101")
                .build();

        var scriptHash = plutusScript.getScriptHash();
        var scriptCredential = Credential.fromScript(scriptHash);

        var anchor = new Anchor("https://test.com/test.json",
                HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        ScriptTx drepUnRegisTx = new ScriptTx(protocolParams)
                .unRegisterDRep(scriptCredential, receiver1, BigInteger.valueOf(1000), BigIntPlutusData.of(1))
                .attachCertificateValidator(plutusScript);

        var transaction = quickTxBuilder.compose(drepUnRegisTx)
                .feePayer(sender1)
                .withTxEvaluator(new StaticTransactionEvaluator(List.of(ExUnits.builder()
                        .mem(BigInteger.valueOf(800))
                        .steps(BigInteger.valueOf(1000000))
                        .build())
                ))
                .build();

        assertThat(transaction.getBody().getFee()).isGreaterThan(BigInteger.ZERO);
        assertThat(transaction.getBody().getCerts().size()).isEqualTo(1);
        assertThat(transaction.getBody().getCerts().get(0)).isInstanceOf(UnregDRepCert.class);
        assertThat(transaction.getWitnessSet().getPlutusV3Scripts()).contains(plutusScript);
        assertThat(transaction.getWitnessSet().getRedeemers()).hasSize(1);
        assertThat(transaction.getBody().getInputs()).hasSize(1);
        assertThat(transaction.getBody().getOutputs()).hasSize(2);
        assertThat(transaction.getBody().getOutputs().get(1).getAddress()).isEqualTo(receiver1);
        assertThat(transaction.getBody().getOutputs().get(1).getValue().getCoin()).isEqualTo(BigInteger.valueOf(1000));
    }

    @Test
    void updateDRep() throws CborSerializationException {
        given(utxoSupplier.getPage(eq(sender1), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build(),
                        Utxo.builder()
                                .address(sender1)
                                .txHash("9a6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(50)))
                                .build()
                )
        );

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var protocolParams = protocolParamsSupplier.getProtocolParams();

        PlutusV3Script plutusScript = PlutusV3Script.builder()
                .type("PlutusScriptV3")
                .cborHex("49480100002221200101")
                .build();

        var scriptHash = plutusScript.getScriptHash();
        var scriptCredential = Credential.fromScript(scriptHash);

        var anchor = new Anchor("https://test.com/test.json",
                HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        ScriptTx drepUpdateTx = new ScriptTx(protocolParams)
                .updateDRep(scriptCredential, anchor, BigIntPlutusData.of(1))
                .attachCertificateValidator(plutusScript);

        var transaction = quickTxBuilder.compose(drepUpdateTx)
                .feePayer(sender1)
                .withTxEvaluator(new StaticTransactionEvaluator(List.of(ExUnits.builder()
                        .mem(BigInteger.valueOf(800))
                        .steps(BigInteger.valueOf(1000000))
                        .build())
                ))
                .build();

        assertThat(transaction.getBody().getFee()).isGreaterThan(BigInteger.ZERO);
        assertThat(transaction.getBody().getCerts().size()).isEqualTo(1);
        assertThat(transaction.getBody().getCerts().get(0)).isInstanceOf(UpdateDRepCert.class);
        assertThat(transaction.getWitnessSet().getPlutusV3Scripts()).contains(plutusScript);
        assertThat(transaction.getWitnessSet().getRedeemers()).hasSize(1);
        assertThat(transaction.getBody().getInputs()).hasSize(1);
        assertThat(transaction.getBody().getOutputs()).hasSize(1);
    }

    @Test
    void voteDelegation() throws CborSerializationException {
        given(utxoSupplier.getPage(eq(sender1), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build(),
                        Utxo.builder()
                                .address(sender1)
                                .txHash("9a6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(50)))
                                .build()
                )
        );

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var protocolParams = protocolParamsSupplier.getProtocolParams();

        PlutusV3Script plutusScript = PlutusV3Script.builder()
                .type("PlutusScriptV3")
                .cborHex("49480100002221200101")
                .build();
        var scriptHash = plutusScript.getScriptHash();

        ScriptTx voteDelgTx = new ScriptTx(protocolParams)
                .delegateVotingPowerTo(new Address(sender1), DRep.scriptHash(scriptHash), BigIntPlutusData.of(1))
                .attachCertificateValidator(plutusScript);

        var transaction = quickTxBuilder.compose(voteDelgTx)
                .feePayer(sender1)
                .withTxEvaluator(new StaticTransactionEvaluator(List.of(ExUnits.builder()
                        .mem(BigInteger.valueOf(800))
                        .steps(BigInteger.valueOf(1000000))
                        .build())
                ))
                .build();

        assertThat(transaction.getBody().getFee()).isGreaterThan(BigInteger.ZERO);
        assertThat(transaction.getBody().getCerts().size()).isEqualTo(1);
        assertThat(transaction.getBody().getCerts().get(0)).isInstanceOf(VoteDelegCert.class);
        assertThat(transaction.getWitnessSet().getPlutusV3Scripts()).contains(plutusScript);
        assertThat(transaction.getWitnessSet().getRedeemers()).hasSize(1);
        assertThat(transaction.getBody().getInputs()).hasSize(1);
        assertThat(transaction.getBody().getOutputs()).hasSize(1);
    }

    @Test
    void createProposal() throws CborSerializationException {
        given(utxoSupplier.getPage(eq(sender1), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(2000)))
                                .build(),
                        Utxo.builder()
                                .address(sender1)
                                .txHash("9a6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(500)))
                                .build()
                )
        );

        var protocolParams = protocolParamsSupplier.getProtocolParams();
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);

        PlutusV3Script plutusScript = PlutusV3Script.builder()
                .type("PlutusScriptV3")
                .cborHex("49480100002221200101")
                .build();

        var refundStakeAddr = "stake_test1uqu38aqpakmk3mlquyja0jpl2aaay2jyu8qc2f3s9ehvdrsg0hudt";

        var parameterChange = new ParameterChangeAction();
        parameterChange.setProtocolParamUpdate(ProtocolParamUpdate.builder()
                .minPoolCost(adaToLovelace(100))
                .build()
        );
        parameterChange.setPolicyHash(plutusScript.getScriptHash());
        var anchor = new Anchor("https://xyz.com",
                HexUtil.decodeHexString("cafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        ScriptTx proposalTx = new ScriptTx(protocolParams)
                .createProposal(parameterChange, refundStakeAddr, anchor, BigIntPlutusData.of(1))
                .attachProposingValidator(plutusScript);

        var transaction = quickTxBuilder.compose(proposalTx)
                .feePayer(sender1)
                .withTxEvaluator(new StaticTransactionEvaluator(List.of(ExUnits.builder()
                        .mem(BigInteger.valueOf(800))
                        .steps(BigInteger.valueOf(1000000))
                        .build())
                ))
                .build();

        assertThat(transaction.getBody().getFee()).isGreaterThan(BigInteger.ZERO);
        assertThat(transaction.getBody().getCerts().size()).isEqualTo(0);
        assertThat(transaction.getWitnessSet().getPlutusV3Scripts()).contains(plutusScript);
        assertThat(transaction.getWitnessSet().getRedeemers()).hasSize(1);
        assertThat(transaction.getBody().getProposalProcedures()).hasSize(1);
        assertThat(transaction.getBody().getProposalProcedures().get(0).getGovAction()).isEqualTo(parameterChange);
        assertThat(transaction.getBody().getProposalProcedures().get(0).getRewardAccount()).isEqualTo(refundStakeAddr);
        assertThat(transaction.getBody().getProposalProcedures().get(0).getAnchor()).isEqualTo(anchor);
        assertThat(transaction.getBody().getProposalProcedures().get(0).getDeposit()).isEqualTo(BigInteger.valueOf(1000000000));

        assertThat(transaction.getBody().getInputs()).hasSize(1);
        assertThat(transaction.getBody().getOutputs()).hasSize(1);
    }

    @Test
    void votePropsal() throws CborSerializationException {
        given(utxoSupplier.getPage(eq(sender1), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(10)))
                                .build(),
                        Utxo.builder()
                                .address(sender1)
                                .txHash("9a6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(50)))
                                .build()
                )
        );

        var protocolParams = protocolParamsSupplier.getProtocolParams();
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);

        PlutusV3Script plutusScript = PlutusV3Script.builder()
                .type("PlutusScriptV3")
                .cborHex("49480100002221200101")
                .build();

        var anchor = new Anchor("https://xyz.com",
                HexUtil.decodeHexString("cafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        var voter = Voter.builder()
                .credential(Credential.fromScript(plutusScript.getScriptHash()))
                .type(VoterType.DREP_SCRIPT_HASH)
                .build();

        ScriptTx proposalTx = new ScriptTx(protocolParams)
                .createVote(voter, new GovActionId("12745f09b138d4d0a11a560b4591ebb830cf12336347606d2edbbf1893d395c6", 0), Vote.YES, anchor, BigIntPlutusData.of(1))
                .attachVotingValidator(plutusScript);

        var transaction = quickTxBuilder.compose(proposalTx)
                .feePayer(sender1)
                .withTxEvaluator(new StaticTransactionEvaluator(List.of(ExUnits.builder()
                        .mem(BigInteger.valueOf(800))
                        .steps(BigInteger.valueOf(1000000))
                        .build())
                ))
                .build();

        assertThat(transaction.getBody().getFee()).isGreaterThan(BigInteger.ZERO);
        assertThat(transaction.getBody().getCerts().size()).isEqualTo(0);
        assertThat(transaction.getWitnessSet().getPlutusV3Scripts()).contains(plutusScript);
        assertThat(transaction.getWitnessSet().getRedeemers()).hasSize(1);
        assertThat(transaction.getBody().getVotingProcedures().getVoting().size()).isEqualTo(1);
        assertThat(transaction.getBody().getVotingProcedures().getVoting().get(voter)).isNotNull();

        assertThat(transaction.getBody().getInputs()).hasSize(1);
        assertThat(transaction.getBody().getOutputs()).hasSize(1);
    }
}
