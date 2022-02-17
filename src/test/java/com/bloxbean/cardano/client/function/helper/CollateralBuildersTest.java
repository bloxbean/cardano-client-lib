package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.BaseTest;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CollateralBuildersTest extends BaseTest {
    @Mock
    BackendService backendService;

    @BeforeEach
    public void setup() throws IOException, ApiException {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void collateralFrom() throws ApiException {
        List<Utxo> utxos = List.of(
                Utxo.builder()
                        .txHash("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85")
                        .outputIndex(1).build(),
                Utxo.builder()
                        .txHash("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c")
                        .outputIndex(1).build()
        );

        TxBuilderContext context = new TxBuilderContext(backendService);
        Transaction transaction = new Transaction();

        CollateralBuilders.collateralFrom(utxos)
                .build(context, transaction);

        assertThat(transaction.getBody().getCollateral()).hasSize(2);
        assertThat(transaction.getBody().getCollateral()).contains(
          new TransactionInput("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85", 1),
          new TransactionInput("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c", 1)
        );
    }

    @Test
    void collateralFrom_whenSuppler() throws ApiException {
        List<Utxo> utxos = List.of(
                Utxo.builder()
                        .txHash("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85")
                        .outputIndex(1).build(),
                Utxo.builder()
                        .txHash("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c")
                        .outputIndex(1).build()
        );

        TxBuilderContext context = new TxBuilderContext(backendService);
        Transaction transaction = new Transaction();

        CollateralBuilders.collateralFrom(() -> utxos)
                .build(context, transaction);

        assertThat(transaction.getBody().getCollateral()).hasSize(2);
        assertThat(transaction.getBody().getCollateral()).contains(
                new TransactionInput("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85", 1),
                new TransactionInput("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c", 1)
        );
    }

    @Test
    void collateralFrom_whenTxnHashAndIndex() throws ApiException {

        TxBuilderContext context = new TxBuilderContext(backendService);
        Transaction transaction = new Transaction();

        CollateralBuilders.collateralFrom("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85", 1)
                .andThen(CollateralBuilders.collateralFrom("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c", 1))
                .build(context, transaction);

        assertThat(transaction.getBody().getCollateral()).hasSize(2);
        assertThat(transaction.getBody().getCollateral()).contains(
                new TransactionInput("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85", 1),
                new TransactionInput("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c", 1)
        );
    }
}
