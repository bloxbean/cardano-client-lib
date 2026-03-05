package com.test;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.blueprint.type.Pair;
import com.bloxbean.cardano.client.plutus.blueprint.type.Triple;
import com.bloxbean.cardano.client.plutus.blueprint.type.Quartet;
import com.bloxbean.cardano.client.plutus.blueprint.type.Quintet;

import java.math.BigInteger;
import java.util.List;

/**
 * Test POJO with parameterized tuple fields to verify that
 * {@code detectFieldType()} correctly identifies Pair, Triple, Quartet,
 * Quintet as their respective types with generics (not as raw CONSTRUCTOR).
 *
 * <p>Includes fields with complex generic parameters (Constr types, Lists)
 * to verify recursive type detection and serialization code generation.</p>
 */
@Constr
public class TupleFieldModel {
    // Simple primitive-only tuples
    public Pair<byte[], byte[]> pairField;
    public Triple<byte[], BigInteger, String> tripleField;
    public Quartet<byte[], BigInteger, String, Boolean> quartetField;
    public Quintet<byte[], BigInteger, String, Boolean, Long> quintetField;

    // Complex generic parameters: Constr types and nested collections
    public Pair<Model2, List<byte[]>> pairConstrList;
    public Pair<byte[], Model2> pairPrimitiveConstr;
    public Triple<Model2, List<BigInteger>, byte[]> tripleConstrListPrimitive;

    public Pair<byte[], byte[]> getPairField() { return pairField; }
    public void setPairField(Pair<byte[], byte[]> pairField) { this.pairField = pairField; }

    public Triple<byte[], BigInteger, String> getTripleField() { return tripleField; }
    public void setTripleField(Triple<byte[], BigInteger, String> tripleField) { this.tripleField = tripleField; }

    public Quartet<byte[], BigInteger, String, Boolean> getQuartetField() { return quartetField; }
    public void setQuartetField(Quartet<byte[], BigInteger, String, Boolean> quartetField) { this.quartetField = quartetField; }

    public Quintet<byte[], BigInteger, String, Boolean, Long> getQuintetField() { return quintetField; }
    public void setQuintetField(Quintet<byte[], BigInteger, String, Boolean, Long> quintetField) { this.quintetField = quintetField; }

    public Pair<Model2, List<byte[]>> getPairConstrList() { return pairConstrList; }
    public void setPairConstrList(Pair<Model2, List<byte[]>> pairConstrList) { this.pairConstrList = pairConstrList; }

    public Pair<byte[], Model2> getPairPrimitiveConstr() { return pairPrimitiveConstr; }
    public void setPairPrimitiveConstr(Pair<byte[], Model2> pairPrimitiveConstr) { this.pairPrimitiveConstr = pairPrimitiveConstr; }

    public Triple<Model2, List<BigInteger>, byte[]> getTripleConstrListPrimitive() { return tripleConstrListPrimitive; }
    public void setTripleConstrListPrimitive(Triple<Model2, List<BigInteger>, byte[]> tripleConstrListPrimitive) { this.tripleConstrListPrimitive = tripleConstrListPrimitive; }
}
