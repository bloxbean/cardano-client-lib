package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.transaction.spec.AuxiliaryData;
import com.bloxbean.cardano.client.transaction.spec.Transaction;

import java.util.function.Supplier;

/**
 * Provides helper methods to transform {@link AuxiliaryData} while creating a {@link Transaction} using {@link TxBuilder}
 */
public class AuxDataProviders {

    /**
     * Function to update metadata in transaction
     *
     * @param metadata
     * @return <code>TxBuilder</code> function
     */
    public static TxBuilder metadataProvider(Metadata metadata) {

        return ((context, transaction) -> {
            updateMetadata(metadata, transaction);
        });
    }

    /**
     * Function to update metadata in transaction
     *
     * @param supplier A supplier function which provides metadata
     * @return <code>TxBuilder</code> function
     */
    public static TxBuilder metadataProvider(Supplier<Metadata> supplier) {

        return ((context, transaction) -> {
            updateMetadata(supplier.get(), transaction);
        });
    }

    private static void updateMetadata(Metadata metadata, Transaction transaction) {
        AuxiliaryData auxiliaryData = transaction.getAuxiliaryData();

        if (auxiliaryData == null) {
            auxiliaryData = new AuxiliaryData();
            transaction.setAuxiliaryData(auxiliaryData);
        }

        if (auxiliaryData.getMetadata() != null) {
            Metadata updatedMetadata = metadata.merge(auxiliaryData.getMetadata());
            auxiliaryData.setMetadata(updatedMetadata);
        } else {
            auxiliaryData.setMetadata(metadata);
        }
    }

}
