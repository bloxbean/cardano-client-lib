package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

/**
 * Integration test POJO covering {@code Optional<T>} field support.
 * The annotation processor generates {@code SampleOptionalMetadataConverter} from this class.
 */
@MetadataType
public class SampleOptional {

    private Optional<String> label;
    private Optional<BigInteger> amount;
    private Optional<Integer> count;
    private Optional<Boolean> active;
    private Optional<BigDecimal> price;
    private Optional<byte[]> payload;

    public Optional<String> getLabel() { return label; }
    public void setLabel(Optional<String> label) { this.label = label; }

    public Optional<BigInteger> getAmount() { return amount; }
    public void setAmount(Optional<BigInteger> amount) { this.amount = amount; }

    public Optional<Integer> getCount() { return count; }
    public void setCount(Optional<Integer> count) { this.count = count; }

    public Optional<Boolean> getActive() { return active; }
    public void setActive(Optional<Boolean> active) { this.active = active; }

    public Optional<BigDecimal> getPrice() { return price; }
    public void setPrice(Optional<BigDecimal> price) { this.price = price; }

    public Optional<byte[]> getPayload() { return payload; }
    public void setPayload(Optional<byte[]> payload) { this.payload = payload; }
}
