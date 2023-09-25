package com.test;
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.PlutusField;
import com.bloxbean.cardano.client.plutus.annotation.PlutusIgnore;

import java.util.*;

@Constr(alternative = 1)
public class NestedListMapModel {
    List<List<String>> listOfList;
    List<List<Map<String, List<String>>>> listOfListOfList;
}
