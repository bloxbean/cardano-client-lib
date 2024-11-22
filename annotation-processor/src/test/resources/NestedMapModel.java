package com.test;
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.PlutusField;
import com.bloxbean.cardano.client.plutus.annotation.PlutusIgnore;
import lombok.Data;

import java.math.BigInteger;
import java.util.*;

@Constr(alternative = 1)
public class NestedMapModel {
    public Map<String, String> map1;
    public Map<String, Map<String, BigInteger>> map2;
}
