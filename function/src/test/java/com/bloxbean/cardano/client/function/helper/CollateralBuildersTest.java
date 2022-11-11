package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.function.BaseTest;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.transaction.spec.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CollateralBuildersTest extends BaseTest {
    @Mock
    UtxoSupplier utxoSupplier;

    ProtocolParams protocolParams;

    @BeforeEach
    public void setup() throws IOException, ApiException {
        MockitoAnnotations.openMocks(this);

        protocolParamJsonFile = "protocol-params.json";
        protocolParams = (ProtocolParams) loadObjectFromJson("protocol-parameters", ProtocolParams.class);
    }

    @Test
    void collateralFrom() {
        List<Utxo> utxos = List.of(
                Utxo.builder()
                        .txHash("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85")
                        .outputIndex(1).build(),
                Utxo.builder()
                        .txHash("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c")
                        .outputIndex(1).build()
        );

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
        Transaction transaction = new Transaction();

        CollateralBuilders.collateralFrom(utxos)
                .apply(context, transaction);

        assertThat(transaction.getBody().getCollateral()).hasSize(2);
        assertThat(transaction.getBody().getCollateral()).contains(
          new TransactionInput("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85", 1),
          new TransactionInput("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c", 1)
        );
    }

    @Test
    void collateralFrom_whenSupplier() {
        List<Utxo> utxos = List.of(
                Utxo.builder()
                        .txHash("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85")
                        .outputIndex(1).build(),
                Utxo.builder()
                        .txHash("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c")
                        .outputIndex(1).build()
        );

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
        Transaction transaction = new Transaction();

        CollateralBuilders.collateralFrom(() -> utxos)
                .apply(context, transaction);

        assertThat(transaction.getBody().getCollateral()).hasSize(2);
        assertThat(transaction.getBody().getCollateral()).contains(
                new TransactionInput("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85", 1),
                new TransactionInput("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c", 1)
        );
    }

    @Test
    void collateralFrom_whenTxnHashAndIndex() {

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
        Transaction transaction = new Transaction();

        CollateralBuilders.collateralFrom("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85", 1)
                .andThen(CollateralBuilders.collateralFrom("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c", 1))
                .apply(context, transaction);

        assertThat(transaction.getBody().getCollateral()).hasSize(2);
        assertThat(transaction.getBody().getCollateral()).contains(
                new TransactionInput("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85", 1),
                new TransactionInput("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c", 1)
        );
    }

    @Test
    void collateralOutputs() {
        List<Utxo> utxos = List.of(
                Utxo.builder()
                        .txHash("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85")
                        .outputIndex(1)
                        .amount(List.of(
                                new Amount(LOVELACE, adaToLovelace(20)),
                                new Amount("606bba5da14fcffd08a8e58217ce6bdc38a6250669db5c285c8d2f8f56657279426967436f696e", BigInteger.valueOf(200000)),
                                new Amount("34250edd1e9836f5378702fbf9416b709bc140e04f668cc3552085184154414441636f696e", BigInteger.valueOf(400))
                        ))
                        .build(),
                Utxo.builder()
                        .txHash("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c")
                        .outputIndex(1)
                        .amount(List.of(
                                new Amount(LOVELACE, adaToLovelace(5)),
                                new Amount("606bba5da14fcffd08a8e58217ce6bdc38a6250669db5c285c8d2f8f56657279426967436f696e", BigInteger.valueOf(100000)),
                                new Amount("66250edd1e9836f5378702fbf9416b709bc140e04f668cc3552085184154414441636f696e", BigInteger.valueOf(500))
                        ))
                        .build()
        );

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
        Transaction transaction = new Transaction();

        String collateralReturnAddress = "addr_test1qpfhnlxuyyk3lp7c30y3pj7fhzryr5pja0sda4nx30z836yvftpkaj3lkyr203savu37m0wx86dc56svvthaeq6u6t6sl33kf6";

        CollateralBuilders.collateralOutputs(collateralReturnAddress, utxos)
                .apply(context, transaction);

        assertThat(transaction.getBody().getCollateral()).hasSize(2);
        assertThat(transaction.getBody().getCollateral()).contains(
                new TransactionInput("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85", 1),
                new TransactionInput("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c", 1)
        );

        assertThat(transaction.getBody().getCollateralReturn().getAddress()).isEqualTo(collateralReturnAddress);
        assertThat(transaction.getBody().getCollateralReturn().getValue().getCoin()).isEqualTo(adaToLovelace(25));
        assertThat(transaction.getBody().getCollateralReturn().getValue().getMultiAssets()).hasSize(3);
        assertThat(transaction.getBody().getCollateralReturn().getValue().getMultiAssets()).hasSameElementsAs(
                List.of(
                        MultiAsset.builder()
                                .policyId("606bba5da14fcffd08a8e58217ce6bdc38a6250669db5c285c8d2f8f")
                                .assets(List.of(new Asset("0x56657279426967436f696e", BigInteger.valueOf(300000))))
                                .build(),
                        MultiAsset.builder()
                                .policyId("34250edd1e9836f5378702fbf9416b709bc140e04f668cc355208518")
                                .assets(List.of(new Asset("0x4154414441636f696e", BigInteger.valueOf(400))))
                                .build(),
                        MultiAsset.builder()
                                .policyId("66250edd1e9836f5378702fbf9416b709bc140e04f668cc355208518")
                                .assets(List.of(new Asset("0x4154414441636f696e", BigInteger.valueOf(500))))
                                .build()
                )
        );
        assertThat(transaction.getBody().getTotalCollateral()).isGreaterThan(BigInteger.ZERO);
    }

    @Test
    void balanceCollateralOutputs() {
        String collateralReturnAddress = "addr_test1qpfhnlxuyyk3lp7c30y3pj7fhzryr5pja0sda4nx30z836yvftpkaj3lkyr203savu37m0wx86dc56svvthaeq6u6t6sl33kf6";
        TransactionBody txBody = TransactionBody.builder()
                .collateral(List.of(
                        new TransactionInput("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85", 1),
                        new TransactionInput("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c", 1)
                ))
                .collateralReturn(
                        TransactionOutput.builder()
                                .address(collateralReturnAddress)
                                .value(Value.builder()
                                        .coin(adaToLovelace(25))
                                        .multiAssets(
                                                List.of(
                                                    MultiAsset.builder()
                                                            .policyId("606bba5da14fcffd08a8e58217ce6bdc38a6250669db5c285c8d2f8f")
                                                            .assets(List.of(new Asset("0x56657279426967436f696e", BigInteger.valueOf(300000))))
                                                            .build(),
                                                    MultiAsset.builder()
                                                            .policyId("34250edd1e9836f5378702fbf9416b709bc140e04f668cc355208518")
                                                            .assets(List.of(new Asset("0x4154414441636f696e", BigInteger.valueOf(400))))
                                                            .build(),
                                                    MultiAsset.builder()
                                                            .policyId("66250edd1e9836f5378702fbf9416b709bc140e04f668cc355208518")
                                                            .assets(List.of(new Asset("0x4154414441636f696e", BigInteger.valueOf(500))))
                                                            .build()
                                                )
                                        ).build()
                                ).build()
                )
                .fee(BigInteger.valueOf(170000)).build();

        Transaction transaction = Transaction.builder()
                        .body(txBody).build();

        TxBuilderContext context = new TxBuilderContext(utxoSupplier, protocolParams);
        CollateralBuilders.balanceCollateralOutputs()
                .apply(context, transaction);

        assertThat(transaction.getBody().getTotalCollateral()).isEqualTo(BigInteger.valueOf(255000));
        assertThat(transaction.getBody().getCollateralReturn().getValue().getCoin()).isEqualTo(BigInteger.valueOf(24745000));
    }
}
