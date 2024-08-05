package com.test;
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.PlutusField;
import com.bloxbean.cardano.client.plutus.annotation.PlutusIgnore;

import java.util.*;

@Constr(alternative = 1)
public class NestedListMapModel {
    public List<List<String>> listOfList;
    public List<List<Map<String, List<String>>>> listOfListOfList;
}
