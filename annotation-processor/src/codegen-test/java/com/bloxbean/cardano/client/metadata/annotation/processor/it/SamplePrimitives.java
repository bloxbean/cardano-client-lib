package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Integration test POJO covering all newly supported primitive/boxed types.
 * The annotation processor generates {@code SamplePrimitivesMetadataConverter} from this class.
 *
 * <p>Covered types:
 * <ul>
 *   <li>int / Integer   → BigInteger (DEFAULT)</li>
 *   <li>long / Long     → BigInteger (DEFAULT)</li>
 *   <li>BigInteger      → BigInteger (DEFAULT)</li>
 *   <li>short / Short   → BigInteger (DEFAULT), String (as=STRING)</li>
 *   <li>byte  / Byte    → BigInteger (DEFAULT), String (as=STRING)</li>
 *   <li>boolean / Boolean → BigInteger 0/1 (DEFAULT), "true"/"false" (as=STRING)</li>
 *   <li>double / Double → String via String.valueOf (DEFAULT)</li>
 *   <li>float  / Float  → String via String.valueOf (DEFAULT)</li>
 *   <li>char / Character → String via String.valueOf (DEFAULT)</li>
 *   <li>BigDecimal → String via toPlainString() (DEFAULT)</li>
 * </ul>
 */
@MetadataType
public class SamplePrimitives {

    // --- int ---
    private int intPrimitive;
    private Integer intBoxed;

    // --- long ---
    private long longPrimitive;
    private Long longBoxed;

    // --- BigInteger ---
    private BigInteger bigIntValue;

    // --- short ---
    private short shortPrimitive;
    private Short shortBoxed;

    @MetadataField(key = "shortStr", enc = MetadataFieldType.STRING)
    private short shortAsString;

    // --- byte ---
    private byte bytePrimitive;
    private Byte byteBoxed;

    @MetadataField(key = "byteStr", enc = MetadataFieldType.STRING)
    private byte byteAsString;

    // --- boolean: DEFAULT → 0/1 as BigInteger ---
    private boolean boolPrimitive;
    private Boolean boolBoxed;

    // --- boolean: as=STRING → "true"/"false" ---
    @MetadataField(key = "boolStr", enc = MetadataFieldType.STRING)
    private boolean boolAsString;

    // --- double ---
    private double doublePrimitive;
    private Double doubleBoxed;

    // --- float ---
    private float floatPrimitive;
    private Float floatBoxed;

    // --- char ---
    private char charPrimitive;
    private Character charBoxed;

    // --- BigDecimal ---
    private BigDecimal decimalValue;
    private BigDecimal decimalNull;

    // -------------------------------------------------------------------------
    // Getters / setters
    // Note: boolean fields use isX() convention to exercise the isX getter fix.
    // -------------------------------------------------------------------------

    public int getIntPrimitive() { return intPrimitive; }
    public void setIntPrimitive(int intPrimitive) { this.intPrimitive = intPrimitive; }

    public Integer getIntBoxed() { return intBoxed; }
    public void setIntBoxed(Integer intBoxed) { this.intBoxed = intBoxed; }

    public long getLongPrimitive() { return longPrimitive; }
    public void setLongPrimitive(long longPrimitive) { this.longPrimitive = longPrimitive; }

    public Long getLongBoxed() { return longBoxed; }
    public void setLongBoxed(Long longBoxed) { this.longBoxed = longBoxed; }

    public BigInteger getBigIntValue() { return bigIntValue; }
    public void setBigIntValue(BigInteger bigIntValue) { this.bigIntValue = bigIntValue; }

    public short getShortPrimitive() { return shortPrimitive; }
    public void setShortPrimitive(short shortPrimitive) { this.shortPrimitive = shortPrimitive; }

    public Short getShortBoxed() { return shortBoxed; }
    public void setShortBoxed(Short shortBoxed) { this.shortBoxed = shortBoxed; }

    public short getShortAsString() { return shortAsString; }
    public void setShortAsString(short shortAsString) { this.shortAsString = shortAsString; }

    public byte getBytePrimitive() { return bytePrimitive; }
    public void setBytePrimitive(byte bytePrimitive) { this.bytePrimitive = bytePrimitive; }

    public Byte getByteBoxed() { return byteBoxed; }
    public void setByteBoxed(Byte byteBoxed) { this.byteBoxed = byteBoxed; }

    public byte getByteAsString() { return byteAsString; }
    public void setByteAsString(byte byteAsString) { this.byteAsString = byteAsString; }

    public boolean isBoolPrimitive() { return boolPrimitive; }
    public void setBoolPrimitive(boolean boolPrimitive) { this.boolPrimitive = boolPrimitive; }

    public Boolean isBoolBoxed() { return boolBoxed; }
    public void setBoolBoxed(Boolean boolBoxed) { this.boolBoxed = boolBoxed; }

    public boolean isBoolAsString() { return boolAsString; }
    public void setBoolAsString(boolean boolAsString) { this.boolAsString = boolAsString; }

    public double getDoublePrimitive() { return doublePrimitive; }
    public void setDoublePrimitive(double doublePrimitive) { this.doublePrimitive = doublePrimitive; }

    public Double getDoubleBoxed() { return doubleBoxed; }
    public void setDoubleBoxed(Double doubleBoxed) { this.doubleBoxed = doubleBoxed; }

    public float getFloatPrimitive() { return floatPrimitive; }
    public void setFloatPrimitive(float floatPrimitive) { this.floatPrimitive = floatPrimitive; }

    public Float getFloatBoxed() { return floatBoxed; }
    public void setFloatBoxed(Float floatBoxed) { this.floatBoxed = floatBoxed; }

    public char getCharPrimitive() { return charPrimitive; }
    public void setCharPrimitive(char charPrimitive) { this.charPrimitive = charPrimitive; }

    public Character getCharBoxed() { return charBoxed; }
    public void setCharBoxed(Character charBoxed) { this.charBoxed = charBoxed; }

    public BigDecimal getDecimalValue() { return decimalValue; }
    public void setDecimalValue(BigDecimal decimalValue) { this.decimalValue = decimalValue; }

    public BigDecimal getDecimalNull() { return decimalNull; }
    public void setDecimalNull(BigDecimal decimalNull) { this.decimalNull = decimalNull; }
}
