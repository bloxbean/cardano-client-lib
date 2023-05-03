package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultUtxoSupplierIT extends BaseITTest {

    @Test
    public void getTxOutput_whenOutput_exists_with_datum() throws ApiException {
        String txHash = "33383bfecb9e541d32857eda88b18ffc71943372fe8ae7b4792589b72a41e26e";
        int outputIndex = 2;

        UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(getBackendService().getUtxoService());
        Optional<Utxo> result = utxoSupplier.getTxOutput(txHash, outputIndex);

        assertEquals("addr_test1wpu365zw7petuyvhng78y2l3534g4ammuuexldu5nkjf0sgz5xu88", result.get().getAddress());
        if (!backendType.equals(KOIOS)) { //TODO -- Remove after the fix in koios-backend
            assertEquals("4194bb3c4c0fd47485112d09ea85b2dd6ab44fa826b77cbf9ed0f12582b057d9", result.get().getDataHash());
            assertEquals("d87a9fd8799fd8799f581cd1707e481671d473ee5a8d561aaac4a1f4e8c937ce61e5d11fc0611fffd8799fd8799f1a000687241b00000187a8d155a2ffffffff",
                    result.get().getInlineDatum());
        }
        assertEquals(3, result.get().getAmount().size());
        assertEquals(new Amount(LOVELACE, adaToLovelace(2)), result.get().getAmount().get(0));
    }
}
