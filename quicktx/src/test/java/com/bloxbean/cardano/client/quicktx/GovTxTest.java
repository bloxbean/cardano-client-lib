package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.transaction.spec.cert.RegDRepCert;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
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
public class GovTxTest extends QuickTxBaseTest {
    @Mock
    private UtxoSupplier utxoSupplier;
    private ProtocolParamsSupplier protocolParamsSupplier;
    @Mock
    private TransactionProcessor transactionProcessor;

    Account account1 = new Account();
    Account account2 = new Account();
    Account account3 = new Account();

    String address1 = account1.baseAddress();
    String address2 = account2.baseAddress();
    String address3 = account3.baseAddress();

    @BeforeEach
    @SneakyThrows
    public void setup() {
        MockitoAnnotations.openMocks(this);
        protocolParamJsonFile = "protocol-params.json";
        ProtocolParams protocolParams = (ProtocolParams) loadObjectFromJson("protocol-parameters", ProtocolParams.class);
        protocolParamsSupplier = () -> protocolParams;
    }

    @Test
    void drepRegistration() {
        given(utxoSupplier.getPage(eq(address1), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(address1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(1)))
                                .build(),
                        Utxo.builder()
                                .address(address1)
                                .txHash("9a6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(50)))
                                .build()
                )
        );

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var protocolParams = protocolParamsSupplier.getProtocolParams();

        var anchor = new Anchor("https://test.com/test.json",
                HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        Tx drepRegTx = new Tx()
                .registerDRep(account1, anchor)
                .from(address1);

        var transaction = quickTxBuilder.compose(drepRegTx)
                .build();

        assertThat(transaction.getBody().getFee()).isGreaterThan(BigInteger.ZERO);
        assertThat(transaction.getBody().getCerts().size()).isEqualTo(1);
        assertThat(transaction.getBody().getCerts().get(0)).isInstanceOf(RegDRepCert.class);
        assertThat(transaction.getBody().getInputs()).hasSize(2);
        assertThat(transaction.getBody().getOutputs()).hasSize(1);

        var totalOutput = transaction.getBody().getOutputs().get(0).getValue().getCoin()
                .add(transaction.getBody().getFee())
                .add(protocolParams.getDrepDeposit());

        assertThat(totalOutput).isEqualTo(adaToLovelace(51));
        assertThat(transaction.getBody().getOutputs().get(0).getAddress()).isEqualTo(address1);
    }

    @Test
    void multiple_drepRegistration() {
        given(utxoSupplier.getPage(eq(address1), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(address1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(1)))
                                .build(),
                        Utxo.builder()
                                .address(address1)
                                .txHash("9a6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(50)))
                                .build()
                )
        );

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var protocolParams = protocolParamsSupplier.getProtocolParams();

        var anchor = new Anchor("https://test.com/test.json",
                HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        Tx drepRegTx = new Tx()
                .registerDRep(account2, anchor)
                .registerDRep(account3, anchor)
                .from(address1);

        var transaction = quickTxBuilder.compose(drepRegTx)
                .build();

        assertThat(transaction.getBody().getFee()).isGreaterThan(BigInteger.ZERO);
        assertThat(transaction.getBody().getCerts().size()).isEqualTo(2);
        assertThat(transaction.getBody().getCerts().get(0)).isInstanceOf(RegDRepCert.class);
        assertThat(transaction.getBody().getInputs()).hasSize(2);
        assertThat(transaction.getBody().getOutputs()).hasSize(1);

        var totalOutput = transaction.getBody().getOutputs().get(0).getValue().getCoin()
                .add(transaction.getBody().getFee())
                .add(protocolParams.getDrepDeposit().multiply(BigInteger.valueOf(2)));

        assertThat(totalOutput).isEqualTo(adaToLovelace(51));
        assertThat(transaction.getBody().getOutputs().get(0).getAddress()).isEqualTo(address1);
    }

    @Test
    void drepRegistration_enoughValueInUtxo1() {
        given(utxoSupplier.getPage(eq(address1), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(address1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build(),
                        Utxo.builder()
                                .address(address1)
                                .txHash("9a6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(50)))
                                .build()
                )
        );

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var protocolParams = protocolParamsSupplier.getProtocolParams();

        var anchor = new Anchor("https://test.com/test.json",
                HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        Tx drepRegTx = new Tx()
                .registerDRep(account1, anchor)
                .from(address1);

        var transaction = quickTxBuilder.compose(drepRegTx)
                .build();

        assertThat(transaction.getBody().getFee()).isGreaterThan(BigInteger.ZERO);
        assertThat(transaction.getBody().getCerts().size()).isEqualTo(1);
        assertThat(transaction.getBody().getCerts().get(0)).isInstanceOf(RegDRepCert.class);
        assertThat(transaction.getBody().getInputs()).hasSize(1);
        assertThat(transaction.getBody().getOutputs()).hasSize(1);

        var totalOutput = transaction.getBody().getOutputs().get(0).getValue().getCoin()
                .add(transaction.getBody().getFee())
                .add(protocolParams.getDrepDeposit());

        assertThat(totalOutput).isEqualTo(adaToLovelace(20));
        assertThat(transaction.getBody().getOutputs().get(0).getAddress()).isEqualTo(address1);
    }

    @Test
    void drepRegistration_withRegularPayment() {
        given(utxoSupplier.getPage(eq(address1), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(address1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build(),
                        Utxo.builder()
                                .address(address1)
                                .txHash("9a6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(50)))
                                .build()
                )
        );

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var protocolParams = protocolParamsSupplier.getProtocolParams();

        var anchor = new Anchor("https://test.com/test.json",
                HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        Tx drepRegTx = new Tx()
                .registerDRep(account1, anchor)
                .payToAddress(address2, Amount.ada(10))
                .from(address1);

        var transaction = quickTxBuilder.compose(drepRegTx)
                .build();

        assertThat(transaction.getBody().getFee()).isGreaterThan(BigInteger.ZERO);
        assertThat(transaction.getBody().getCerts().size()).isEqualTo(1);
        assertThat(transaction.getBody().getCerts().get(0)).isInstanceOf(RegDRepCert.class);
        assertThat(transaction.getBody().getInputs()).hasSize(1);
        assertThat(transaction.getBody().getOutputs()).hasSize(2);

        var totalOutput = transaction.getBody().getOutputs().get(0).getValue().getCoin()
                .add(transaction.getBody().getOutputs().get(1).getValue().getCoin())
                .add(transaction.getBody().getFee())
                .add(protocolParams.getDrepDeposit());

        assertThat(totalOutput).isEqualTo(adaToLovelace(20));
        assertThat(transaction.getBody().getOutputs().get(0).getAddress()).isEqualTo(address2);
        assertThat(transaction.getBody().getOutputs().get(0).getValue().getCoin()).isEqualTo(adaToLovelace(10));
        assertThat(transaction.getBody().getOutputs().get(1).getAddress()).isEqualTo(address1);
    }

}
