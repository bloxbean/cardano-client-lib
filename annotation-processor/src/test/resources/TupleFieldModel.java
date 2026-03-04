package com.test;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.blueprint.type.Pair;
import com.bloxbean.cardano.client.plutus.blueprint.type.Triple;
import com.bloxbean.cardano.client.plutus.blueprint.type.Quartet;
import com.bloxbean.cardano.client.plutus.blueprint.type.Quintet;

import java.math.BigInteger;

/**
 * Test POJO with parameterized tuple fields to verify that
 * {@code detectFieldType()} correctly identifies Pair, Triple, Quartet,
 * Quintet as their respective types with generics (not as raw CONSTRUCTOR).
 */
@Constr
public class TupleFieldModel {
    public Pair<byte[], byte[]> pairField;
    public Triple<byte[], BigInteger, String> tripleField;
    public Quartet<byte[], BigInteger, String, Boolean> quartetField;
    public Quintet<byte[], BigInteger, String, Boolean, Long> quintetField;

    public Pair<byte[], byte[]> getPairField() { return pairField; }
    public void setPairField(Pair<byte[], byte[]> pairField) { this.pairField = pairField; }

    public Triple<byte[], BigInteger, String> getTripleField() { return tripleField; }
    public void setTripleField(Triple<byte[], BigInteger, String> tripleField) { this.tripleField = tripleField; }

    public Quartet<byte[], BigInteger, String, Boolean> getQuartetField() { return quartetField; }
    public void setQuartetField(Quartet<byte[], BigInteger, String, Boolean> quartetField) { this.quartetField = quartetField; }

    public Quintet<byte[], BigInteger, String, Boolean, Long> getQuintetField() { return quintetField; }
    public void setQuintetField(Quintet<byte[], BigInteger, String, Boolean, Long> quintetField) { this.quintetField = quintetField; }
}
