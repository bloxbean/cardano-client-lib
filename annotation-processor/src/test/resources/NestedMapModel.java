package com.test;
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.PlutusField;
import com.bloxbean.cardano.client.plutus.annotation.PlutusIgnore;

import java.math.BigInteger;
import java.util.*;

@Constr(alternative = 1)
public class NestedMapModel {
    Map<String, String> map1;
    Map<String, Map<String, BigInteger>> map2;
}
