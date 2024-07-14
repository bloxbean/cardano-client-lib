package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.quicktx.verifiers.TxVerifiers;
import com.bloxbean.cardano.client.transaction.spec.cert.*;
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
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class StakeTxTest extends QuickTxBaseTest {
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
    void testStakeKeyRegistration_needMultipleUtxos() {
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

        Tx drepRegTx = new Tx()
                .registerStakeAddress(address1)
                .from(address1);

        var transaction = quickTxBuilder.compose(drepRegTx)
                .build();

        assertThat(transaction.getBody().getFee()).isGreaterThan(BigInteger.ZERO);
        assertThat(transaction.getBody().getCerts().size()).isEqualTo(1);
        assertThat(transaction.getBody().getCerts().get(0)).isInstanceOf(StakeRegistration.class);
        assertThat(transaction.getBody().getInputs()).hasSize(2);
        assertThat(transaction.getBody().getOutputs()).hasSize(1);

        var totalOutput = transaction.getBody().getOutputs().get(0).getValue().getCoin()
                .add(transaction.getBody().getFee())
                .add(protocolParams.getDrepDeposit());

        assertThat(totalOutput).isEqualTo(adaToLovelace(51));
        assertThat(transaction.getBody().getOutputs().get(0).getAddress()).isEqualTo(address1);
    }

    @Test
    void testStakeKeyRegistration_needSingleUtxos() {
        given(utxoSupplier.getPage(eq(address2), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(address2)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(5)))
                                .build(),
                        Utxo.builder()
                                .address(address2)
                                .txHash("9a6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(50)))
                                .build()
                )
        );

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var protocolParams = protocolParamsSupplier.getProtocolParams();

        Tx drepRegTx = new Tx()
                .registerStakeAddress(address1)
                .from(address2);

        var transaction = quickTxBuilder.compose(drepRegTx)
                .build();

        assertThat(transaction.getBody().getFee()).isGreaterThan(BigInteger.ZERO);
        assertThat(transaction.getBody().getCerts().size()).isEqualTo(1);
        assertThat(transaction.getBody().getCerts().get(0)).isInstanceOf(StakeRegistration.class);
        assertThat(transaction.getBody().getInputs()).hasSize(1);
        assertThat(transaction.getBody().getOutputs()).hasSize(1);

        var totalOutput = transaction.getBody().getOutputs().get(0).getValue().getCoin()
                .add(transaction.getBody().getFee())
                .add(protocolParams.getDrepDeposit());

        assertThat(totalOutput).isEqualTo(adaToLovelace(5));
        assertThat(transaction.getBody().getOutputs().get(0).getAddress()).isEqualTo(address2);
    }

    @Test
    void testStakeKeyRegistration_withRegularPayments() {
        given(utxoSupplier.getPage(eq(address2), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(address2)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(8)))
                                .build(),
                        Utxo.builder()
                                .address(address2)
                                .txHash("9a6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(50)))
                                .build()
                )
        );

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var protocolParams = protocolParamsSupplier.getProtocolParams();

        Tx drepRegTx = new Tx()
                .registerStakeAddress(address1)
                .payToAddress(address3, Amount.ada(2))
                .from(address2);

        var transaction = quickTxBuilder.compose(drepRegTx)
                .build();

        assertThat(transaction.getBody().getFee()).isGreaterThan(BigInteger.ZERO);
        assertThat(transaction.getBody().getCerts().size()).isEqualTo(1);
        assertThat(transaction.getBody().getCerts().get(0)).isInstanceOf(StakeRegistration.class);
        assertThat(transaction.getBody().getInputs()).hasSize(1);
        assertThat(transaction.getBody().getOutputs()).hasSize(2);

        var totalOutput = transaction.getBody().getOutputs().get(0).getValue().getCoin()
                .add(transaction.getBody().getOutputs().get(1).getValue().getCoin())
                .add(transaction.getBody().getFee())
                .add(protocolParams.getDrepDeposit());

        assertThat(totalOutput).isEqualTo(adaToLovelace(8));
        assertThat(transaction.getBody().getOutputs().get(0).getAddress()).isEqualTo(address3);
        assertThat(transaction.getBody().getOutputs().get(0).getValue().getCoin()).isEqualTo(adaToLovelace(2));
        assertThat(transaction.getBody().getOutputs().get(1).getAddress()).isEqualTo(address2);
    }

    @Test
    void testStakeKeyRegistration_multipleKeyRegistration_withRegularPayments() {
        given(utxoSupplier.getPage(eq(address2), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(address2)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(11)))
                                .build(),
                        Utxo.builder()
                                .address(address2)
                                .txHash("9a6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(50)))
                                .build()
                )
        );

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var protocolParams = protocolParamsSupplier.getProtocolParams();

        Tx drepRegTx = new Tx()
                .registerStakeAddress(address1)
                .registerStakeAddress(address3)
                .payToAddress(address3, Amount.ada(2))
                .payToAddress(address1, Amount.ada(3))
                .from(address2);

        var transaction = quickTxBuilder.compose(drepRegTx)
                .build();

        assertThat(transaction.getBody().getFee()).isGreaterThan(BigInteger.ZERO);
        assertThat(transaction.getBody().getCerts().size()).isEqualTo(2);
        assertThat(transaction.getBody().getCerts().get(0)).isInstanceOf(StakeRegistration.class);
        assertThat(transaction.getBody().getCerts().get(1)).isInstanceOf(StakeRegistration.class);
        assertThat(transaction.getBody().getInputs()).hasSize(1);
        assertThat(transaction.getBody().getOutputs()).hasSize(3);

        var totalOutput = transaction.getBody().getOutputs().get(0).getValue().getCoin()
                .add(transaction.getBody().getOutputs().get(1).getValue().getCoin())
                .add(transaction.getBody().getOutputs().get(2).getValue().getCoin())
                .add(transaction.getBody().getFee())
                .add(protocolParams.getDrepDeposit().multiply(BigInteger.valueOf(2)));

        assertThat(totalOutput).isEqualTo(adaToLovelace(11));
        assertThat(transaction.getBody().getOutputs().get(0).getAddress()).isEqualTo(address3);
        assertThat(transaction.getBody().getOutputs().get(0).getValue().getCoin()).isEqualTo(adaToLovelace(2));
        assertThat(transaction.getBody().getOutputs().get(1).getAddress()).isEqualTo(address1);
        assertThat(transaction.getBody().getOutputs().get(1).getValue().getCoin()).isEqualTo(adaToLovelace(3));
        assertThat(transaction.getBody().getOutputs().get(2).getAddress()).isEqualTo(address2);
    }

    @Test
    void testStakeKeyDeRegistration_needSingleUtxos() {
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

        Tx drepRegTx = new Tx()
                .deregisterStakeAddress(address1)
                .from(address1);

        var transaction = quickTxBuilder.compose(drepRegTx)
                .build();

        assertThat(transaction.getBody().getFee()).isGreaterThan(BigInteger.ZERO);
        assertThat(transaction.getBody().getCerts().size()).isEqualTo(1);
        assertThat(transaction.getBody().getCerts().get(0)).isInstanceOf(StakeDeregistration.class);
        assertThat(transaction.getBody().getInputs()).hasSize(1);
        assertThat(transaction.getBody().getOutputs()).hasSize(1);

        var totalOutput = transaction.getBody().getOutputs().get(0).getValue().getCoin()
                .add(transaction.getBody().getFee());

        assertThat(totalOutput).isEqualTo(adaToLovelace(1 + 2));
        assertThat(transaction.getBody().getOutputs().get(0).getAddress()).isEqualTo(address1);
    }

    @Test
    void testStakeKeyDeRegistration_withPayments() {
        given(utxoSupplier.getPage(eq(address1), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(address1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(2)))
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

        Tx drepRegTx = new Tx()
                .deregisterStakeAddress(address1, address2)
                .from(address1);

        var transaction = quickTxBuilder.compose(drepRegTx)
                .withVerifier(TxVerifiers.outputAmountVerifier(address2, Amount.ada(2), "Output amount verification failed"))
                .build();

        assertThat(transaction.getBody().getFee()).isGreaterThan(BigInteger.ZERO);
        assertThat(transaction.getBody().getCerts().size()).isEqualTo(1);
        assertThat(transaction.getBody().getCerts().get(0)).isInstanceOf(StakeDeregistration.class);
        assertThat(transaction.getBody().getInputs()).hasSize(1);

        assertThat(transaction.getBody().getOutputs()).hasSize(2);
        var totalOutput = transaction.getBody().getOutputs().get(0).getValue().getCoin()
                .add(transaction.getBody().getOutputs().get(1).getValue().getCoin())
                .add(transaction.getBody().getFee());

        assertThat(totalOutput).isEqualTo(adaToLovelace(2 + 2));
        assertThat(transaction.getBody().getOutputs().stream().map(o -> o.getAddress()).collect(Collectors.toList()))
                .contains(address1, address2);

    }

    @Test
    void testPoolRegistration() throws CborDeserializationException {
        given(utxoSupplier.getPage(eq(address1), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(address1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(1000)))
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

        String regCbor = "8a03581ced40b0a319f639a70b1e2a4de00f112c4f7b7d4849f0abd25c4336a45820b95af7a0a58928fbd0e73b03ce81dedd42d4a776685b443cf2016c18438a3b9b1b00000600aea7d0001a1dcd6500d81e820d1903e8581de1f3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a35613481581cf3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a356134838400190bb94436b12923f68400190bb944037dfcb6f68400190bb944343fe1bef6827468747470733a2f2f6769742e696f2f4a7474546c582051700f7e33476a20b6e5a3f681e31d2cf0d8e706393f45912a5dbe3a8d7edd41";
        PoolRegistration poolRegistration = PoolRegistration.deserialize(CborSerializationUtil.deserialize(HexUtil.decodeHexString(regCbor)));

        Tx drepRegTx = new Tx()
                .registerPool(poolRegistration)
                .from(address1);

        var transaction = quickTxBuilder.compose(drepRegTx)
                .build();

        assertThat(transaction.getBody().getFee()).isGreaterThan(BigInteger.ZERO);
        assertThat(transaction.getBody().getCerts().size()).isEqualTo(1);
        assertThat(transaction.getBody().getCerts().get(0)).isInstanceOf(PoolRegistration.class);
        assertThat(transaction.getBody().getInputs()).hasSize(1);
        assertThat(transaction.getBody().getOutputs()).hasSize(1);

        var totalOutput = transaction.getBody().getOutputs().get(0).getValue().getCoin()
                .add(transaction.getBody().getFee())
                .add(new BigInteger(protocolParams.getPoolDeposit()));

        assertThat(totalOutput).isEqualTo(adaToLovelace(1000));
        assertThat(transaction.getBody().getOutputs().get(0).getAddress()).isEqualTo(address1);
    }

    @Test
    void testPoolRetirement() throws CborDeserializationException {
        given(utxoSupplier.getPage(eq(address1), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(address1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(1000)))
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

        String regCbor = "8a03581ced40b0a319f639a70b1e2a4de00f112c4f7b7d4849f0abd25c4336a45820b95af7a0a58928fbd0e73b03ce81dedd42d4a776685b443cf2016c18438a3b9b1b00000600aea7d0001a1dcd6500d81e820d1903e8581de1f3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a35613481581cf3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a356134838400190bb94436b12923f68400190bb944037dfcb6f68400190bb944343fe1bef6827468747470733a2f2f6769742e696f2f4a7474546c582051700f7e33476a20b6e5a3f681e31d2cf0d8e706393f45912a5dbe3a8d7edd41";
        PoolRegistration poolRegistration = PoolRegistration.deserialize(CborSerializationUtil.deserialize(HexUtil.decodeHexString(regCbor)));

        Tx drepRegTx = new Tx()
                .retirePool(poolRegistration.getBech32PoolId(), 421)
                .from(address1);

        var transaction = quickTxBuilder.compose(drepRegTx)
                .build();

        assertThat(transaction.getBody().getFee()).isGreaterThan(BigInteger.ZERO);
        assertThat(transaction.getBody().getCerts().size()).isEqualTo(1);
        assertThat(transaction.getBody().getCerts().get(0)).isInstanceOf(PoolRetirement.class);
        assertThat(transaction.getBody().getInputs()).hasSize(1);
        assertThat(transaction.getBody().getOutputs()).hasSize(1);

        var totalOutput = transaction.getBody().getOutputs().get(0).getValue().getCoin()
                .add(transaction.getBody().getFee())
                .add(new BigInteger(protocolParams.getPoolDeposit()));

        assertThat(totalOutput).isEqualTo(adaToLovelace(1500));
        assertThat(transaction.getBody().getOutputs().get(0).getAddress()).isEqualTo(address1);
    }
}
