package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

/**
 * Integration test POJO covering {@code Optional<T>} field support.
 * The annotation processor generates {@code SampleOptionalMetadataConverter} from this class.
 *
 * <p>Covered element types: String, BigInteger, Long, Integer, Short, Byte,
 * Boolean, Double, Float, Character, BigDecimal, byte[].
 */
@MetadataType
public class SampleOptional {

    private Optional<String> label;
    private Optional<BigInteger> amount;
    private Optional<Long> counter;
    private Optional<Integer> count;
    private Optional<Short> code;
    private Optional<Byte> byteValue;
    private Optional<Boolean> active;
    private Optional<Double> rate;
    private Optional<Float> factor;
    private Optional<Character> letter;
    private Optional<BigDecimal> price;
    private Optional<byte[]> payload;

    public Optional<String> getLabel() { return label; }
    public void setLabel(Optional<String> label) { this.label = label; }

    public Optional<BigInteger> getAmount() { return amount; }
    public void setAmount(Optional<BigInteger> amount) { this.amount = amount; }

    public Optional<Long> getCounter() { return counter; }
    public void setCounter(Optional<Long> counter) { this.counter = counter; }

    public Optional<Integer> getCount() { return count; }
    public void setCount(Optional<Integer> count) { this.count = count; }

    public Optional<Short> getCode() { return code; }
    public void setCode(Optional<Short> code) { this.code = code; }

    public Optional<Byte> getByteValue() { return byteValue; }
    public void setByteValue(Optional<Byte> byteValue) { this.byteValue = byteValue; }

    public Optional<Boolean> getActive() { return active; }
    public void setActive(Optional<Boolean> active) { this.active = active; }

    public Optional<Double> getRate() { return rate; }
    public void setRate(Optional<Double> rate) { this.rate = rate; }

    public Optional<Float> getFactor() { return factor; }
    public void setFactor(Optional<Float> factor) { this.factor = factor; }

    public Optional<Character> getLetter() { return letter; }
    public void setLetter(Optional<Character> letter) { this.letter = letter; }

    public Optional<BigDecimal> getPrice() { return price; }
    public void setPrice(Optional<BigDecimal> price) { this.price = price; }

    public Optional<byte[]> getPayload() { return payload; }
    public void setPayload(Optional<byte[]> payload) { this.payload = payload; }
}
