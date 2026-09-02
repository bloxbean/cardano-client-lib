package com.bloxbean.cardano.client.programmabletoken.intent;

import com.bloxbean.cardano.client.api.model.Amount;
import java.util.List;

final class ProgrammableIntentValidation {
    private ProgrammableIntentValidation() { }

    static void required(String value, String field) {
        if (value == null || value.isBlank())
            throw new IllegalStateException(field + " is required");
    }

    static void amount(Amount amount, String operation) {
        if (amount == null || amount.getUnit() == null || "lovelace".equals(amount.getUnit()))
            throw new IllegalStateException("A programmable-token amount is required");
        if (amount.getQuantity() == null || amount.getQuantity().signum() <= 0)
            throw new IllegalStateException(operation + " quantity must be positive");
    }

    static void assets(List<ProgrammableTokenAsset> assets, String operation) {
        if (assets == null || assets.isEmpty())
            throw new IllegalStateException(operation + " assets are required");
        for (ProgrammableTokenAsset asset : assets) {
            if (asset == null || asset.getQuantity() == null
                    || asset.getQuantity().signum() <= 0)
                throw new IllegalStateException(operation + " asset quantities must be positive");
        }
    }
}
