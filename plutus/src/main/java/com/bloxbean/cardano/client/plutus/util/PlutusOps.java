package com.bloxbean.cardano.client.plutus.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.stream.Collectors;

public class PlutusOps {
    private static ObjectMapper objectMapper = new ObjectMapper();
    private static List<String> V1_OPS;
    private static List<String> V2_OPS;

    private static String PLUTUS_V1_COSTS = "{\n" +
            "                        \"addInteger-cpu-arguments-intercept\": 205665,\n" +
            "                        \"addInteger-cpu-arguments-slope\": 812,\n" +
            "                        \"addInteger-memory-arguments-intercept\": 1,\n" +
            "                        \"addInteger-memory-arguments-slope\": 1,\n" +
            "                        \"appendByteString-cpu-arguments-intercept\": 1000,\n" +
            "                        \"appendByteString-cpu-arguments-slope\": 571,\n" +
            "                        \"appendByteString-memory-arguments-intercept\": 0,\n" +
            "                        \"appendByteString-memory-arguments-slope\": 1,\n" +
            "                        \"appendString-cpu-arguments-intercept\": 1000,\n" +
            "                        \"appendString-cpu-arguments-slope\": 24177,\n" +
            "                        \"appendString-memory-arguments-intercept\": 4,\n" +
            "                        \"appendString-memory-arguments-slope\": 1,\n" +
            "                        \"bData-cpu-arguments\": 1000,\n" +
            "                        \"bData-memory-arguments\": 32,\n" +
            "                        \"blake2b_256-cpu-arguments-intercept\": 117366,\n" +
            "                        \"blake2b_256-cpu-arguments-slope\": 10475,\n" +
            "                        \"blake2b_256-memory-arguments\": 4,\n" +
            "                        \"cekApplyCost-exBudgetCPU\": 23000,\n" +
            "                        \"cekApplyCost-exBudgetMemory\": 100,\n" +
            "                        \"cekBuiltinCost-exBudgetCPU\": 23000,\n" +
            "                        \"cekBuiltinCost-exBudgetMemory\": 100,\n" +
            "                        \"cekConstCost-exBudgetCPU\": 23000,\n" +
            "                        \"cekConstCost-exBudgetMemory\": 100,\n" +
            "                        \"cekDelayCost-exBudgetCPU\": 23000,\n" +
            "                        \"cekDelayCost-exBudgetMemory\": 100,\n" +
            "                        \"cekForceCost-exBudgetCPU\": 23000,\n" +
            "                        \"cekForceCost-exBudgetMemory\": 100,\n" +
            "                        \"cekLamCost-exBudgetCPU\": 23000,\n" +
            "                        \"cekLamCost-exBudgetMemory\": 100,\n" +
            "                        \"cekStartupCost-exBudgetCPU\": 100,\n" +
            "                        \"cekStartupCost-exBudgetMemory\": 100,\n" +
            "                        \"cekVarCost-exBudgetCPU\": 23000,\n" +
            "                        \"cekVarCost-exBudgetMemory\": 100,\n" +
            "                        \"chooseData-cpu-arguments\": 19537,\n" +
            "                        \"chooseData-memory-arguments\": 32,\n" +
            "                        \"chooseList-cpu-arguments\": 175354,\n" +
            "                        \"chooseList-memory-arguments\": 32,\n" +
            "                        \"chooseUnit-cpu-arguments\": 46417,\n" +
            "                        \"chooseUnit-memory-arguments\": 4,\n" +
            "                        \"consByteString-cpu-arguments-intercept\": 221973,\n" +
            "                        \"consByteString-cpu-arguments-slope\": 511,\n" +
            "                        \"consByteString-memory-arguments-intercept\": 0,\n" +
            "                        \"consByteString-memory-arguments-slope\": 1,\n" +
            "                        \"constrData-cpu-arguments\": 89141,\n" +
            "                        \"constrData-memory-arguments\": 32,\n" +
            "                        \"decodeUtf8-cpu-arguments-intercept\": 497525,\n" +
            "                        \"decodeUtf8-cpu-arguments-slope\": 14068,\n" +
            "                        \"decodeUtf8-memory-arguments-intercept\": 4,\n" +
            "                        \"decodeUtf8-memory-arguments-slope\": 2,\n" +
            "                        \"divideInteger-cpu-arguments-constant\": 196500,\n" +
            "                        \"divideInteger-cpu-arguments-model-arguments-intercept\": 453240,\n" +
            "                        \"divideInteger-cpu-arguments-model-arguments-slope\": 220,\n" +
            "                        \"divideInteger-memory-arguments-intercept\": 0,\n" +
            "                        \"divideInteger-memory-arguments-minimum\": 1,\n" +
            "                        \"divideInteger-memory-arguments-slope\": 1,\n" +
            "                        \"encodeUtf8-cpu-arguments-intercept\": 1000,\n" +
            "                        \"encodeUtf8-cpu-arguments-slope\": 28662,\n" +
            "                        \"encodeUtf8-memory-arguments-intercept\": 4,\n" +
            "                        \"encodeUtf8-memory-arguments-slope\": 2,\n" +
            "                        \"equalsByteString-cpu-arguments-constant\": 245000,\n" +
            "                        \"equalsByteString-cpu-arguments-intercept\": 216773,\n" +
            "                        \"equalsByteString-cpu-arguments-slope\": 62,\n" +
            "                        \"equalsByteString-memory-arguments\": 1,\n" +
            "                        \"equalsData-cpu-arguments-intercept\": 1060367,\n" +
            "                        \"equalsData-cpu-arguments-slope\": 12586,\n" +
            "                        \"equalsData-memory-arguments\": 1,\n" +
            "                        \"equalsInteger-cpu-arguments-intercept\": 208512,\n" +
            "                        \"equalsInteger-cpu-arguments-slope\": 421,\n" +
            "                        \"equalsInteger-memory-arguments\": 1,\n" +
            "                        \"equalsString-cpu-arguments-constant\": 187000,\n" +
            "                        \"equalsString-cpu-arguments-intercept\": 1000,\n" +
            "                        \"equalsString-cpu-arguments-slope\": 52998,\n" +
            "                        \"equalsString-memory-arguments\": 1,\n" +
            "                        \"fstPair-cpu-arguments\": 80436,\n" +
            "                        \"fstPair-memory-arguments\": 32,\n" +
            "                        \"headList-cpu-arguments\": 43249,\n" +
            "                        \"headList-memory-arguments\": 32,\n" +
            "                        \"iData-cpu-arguments\": 1000,\n" +
            "                        \"iData-memory-arguments\": 32,\n" +
            "                        \"ifThenElse-cpu-arguments\": 80556,\n" +
            "                        \"ifThenElse-memory-arguments\": 1,\n" +
            "                        \"indexByteString-cpu-arguments\": 57667,\n" +
            "                        \"indexByteString-memory-arguments\": 4,\n" +
            "                        \"lengthOfByteString-cpu-arguments\": 1000,\n" +
            "                        \"lengthOfByteString-memory-arguments\": 10,\n" +
            "                        \"lessThanByteString-cpu-arguments-intercept\": 197145,\n" +
            "                        \"lessThanByteString-cpu-arguments-slope\": 156,\n" +
            "                        \"lessThanByteString-memory-arguments\": 1,\n" +
            "                        \"lessThanEqualsByteString-cpu-arguments-intercept\": 197145,\n" +
            "                        \"lessThanEqualsByteString-cpu-arguments-slope\": 156,\n" +
            "                        \"lessThanEqualsByteString-memory-arguments\": 1,\n" +
            "                        \"lessThanEqualsInteger-cpu-arguments-intercept\": 204924,\n" +
            "                        \"lessThanEqualsInteger-cpu-arguments-slope\": 473,\n" +
            "                        \"lessThanEqualsInteger-memory-arguments\": 1,\n" +
            "                        \"lessThanInteger-cpu-arguments-intercept\": 208896,\n" +
            "                        \"lessThanInteger-cpu-arguments-slope\": 511,\n" +
            "                        \"lessThanInteger-memory-arguments\": 1,\n" +
            "                        \"listData-cpu-arguments\": 52467,\n" +
            "                        \"listData-memory-arguments\": 32,\n" +
            "                        \"mapData-cpu-arguments\": 64832,\n" +
            "                        \"mapData-memory-arguments\": 32,\n" +
            "                        \"mkCons-cpu-arguments\": 65493,\n" +
            "                        \"mkCons-memory-arguments\": 32,\n" +
            "                        \"mkNilData-cpu-arguments\": 22558,\n" +
            "                        \"mkNilData-memory-arguments\": 32,\n" +
            "                        \"mkNilPairData-cpu-arguments\": 16563,\n" +
            "                        \"mkNilPairData-memory-arguments\": 32,\n" +
            "                        \"mkPairData-cpu-arguments\": 76511,\n" +
            "                        \"mkPairData-memory-arguments\": 32,\n" +
            "                        \"modInteger-cpu-arguments-constant\": 196500,\n" +
            "                        \"modInteger-cpu-arguments-model-arguments-intercept\": 453240,\n" +
            "                        \"modInteger-cpu-arguments-model-arguments-slope\": 220,\n" +
            "                        \"modInteger-memory-arguments-intercept\": 0,\n" +
            "                        \"modInteger-memory-arguments-minimum\": 1,\n" +
            "                        \"modInteger-memory-arguments-slope\": 1,\n" +
            "                        \"multiplyInteger-cpu-arguments-intercept\": 69522,\n" +
            "                        \"multiplyInteger-cpu-arguments-slope\": 11687,\n" +
            "                        \"multiplyInteger-memory-arguments-intercept\": 0,\n" +
            "                        \"multiplyInteger-memory-arguments-slope\": 1,\n" +
            "                        \"nullList-cpu-arguments\": 60091,\n" +
            "                        \"nullList-memory-arguments\": 32,\n" +
            "                        \"quotientInteger-cpu-arguments-constant\": 196500,\n" +
            "                        \"quotientInteger-cpu-arguments-model-arguments-intercept\": 453240,\n" +
            "                        \"quotientInteger-cpu-arguments-model-arguments-slope\": 220,\n" +
            "                        \"quotientInteger-memory-arguments-intercept\": 0,\n" +
            "                        \"quotientInteger-memory-arguments-minimum\": 1,\n" +
            "                        \"quotientInteger-memory-arguments-slope\": 1,\n" +
            "                        \"remainderInteger-cpu-arguments-constant\": 196500,\n" +
            "                        \"remainderInteger-cpu-arguments-model-arguments-intercept\": 453240,\n" +
            "                        \"remainderInteger-cpu-arguments-model-arguments-slope\": 220,\n" +
            "                        \"remainderInteger-memory-arguments-intercept\": 0,\n" +
            "                        \"remainderInteger-memory-arguments-minimum\": 1,\n" +
            "                        \"remainderInteger-memory-arguments-slope\": 1,\n" +
            "                        \"sha2_256-cpu-arguments-intercept\": 806990,\n" +
            "                        \"sha2_256-cpu-arguments-slope\": 30482,\n" +
            "                        \"sha2_256-memory-arguments\": 4,\n" +
            "                        \"sha3_256-cpu-arguments-intercept\": 1927926,\n" +
            "                        \"sha3_256-cpu-arguments-slope\": 82523,\n" +
            "                        \"sha3_256-memory-arguments\": 4,\n" +
            "                        \"sliceByteString-cpu-arguments-intercept\": 265318,\n" +
            "                        \"sliceByteString-cpu-arguments-slope\": 0,\n" +
            "                        \"sliceByteString-memory-arguments-intercept\": 4,\n" +
            "                        \"sliceByteString-memory-arguments-slope\": 0,\n" +
            "                        \"sndPair-cpu-arguments\": 85931,\n" +
            "                        \"sndPair-memory-arguments\": 32,\n" +
            "                        \"subtractInteger-cpu-arguments-intercept\": 205665,\n" +
            "                        \"subtractInteger-cpu-arguments-slope\": 812,\n" +
            "                        \"subtractInteger-memory-arguments-intercept\": 1,\n" +
            "                        \"subtractInteger-memory-arguments-slope\": 1,\n" +
            "                        \"tailList-cpu-arguments\": 41182,\n" +
            "                        \"tailList-memory-arguments\": 32,\n" +
            "                        \"trace-cpu-arguments\": 212342,\n" +
            "                        \"trace-memory-arguments\": 32,\n" +
            "                        \"unBData-cpu-arguments\": 31220,\n" +
            "                        \"unBData-memory-arguments\": 32,\n" +
            "                        \"unConstrData-cpu-arguments\": 32696,\n" +
            "                        \"unConstrData-memory-arguments\": 32,\n" +
            "                        \"unIData-cpu-arguments\": 43357,\n" +
            "                        \"unIData-memory-arguments\": 32,\n" +
            "                        \"unListData-cpu-arguments\": 32247,\n" +
            "                        \"unListData-memory-arguments\": 32,\n" +
            "                        \"unMapData-cpu-arguments\": 38314,\n" +
            "                        \"unMapData-memory-arguments\": 32,\n" +
            "                        \"verifyEd25519Signature-cpu-arguments-intercept\": 57996947,\n" +
            "                        \"verifyEd25519Signature-cpu-arguments-slope\": 18975,\n" +
            "                        \"verifyEd25519Signature-memory-arguments\": 10\n" +
            "                    }";

    private static String PLUTUS_V2_COSTS = " {\n" +
            "                                    \"addInteger-cpu-arguments-intercept\": 205665,\n" +
            "                                    \"addInteger-cpu-arguments-slope\": 812,\n" +
            "                                    \"addInteger-memory-arguments-intercept\": 1,\n" +
            "                                    \"addInteger-memory-arguments-slope\": 1,\n" +
            "                                    \"appendByteString-cpu-arguments-intercept\": 1000,\n" +
            "                                    \"appendByteString-cpu-arguments-slope\": 571,\n" +
            "                                    \"appendByteString-memory-arguments-intercept\": 0,\n" +
            "                                    \"appendByteString-memory-arguments-slope\": 1,\n" +
            "                                    \"appendString-cpu-arguments-intercept\": 1000,\n" +
            "                                    \"appendString-cpu-arguments-slope\": 24177,\n" +
            "                                    \"appendString-memory-arguments-intercept\": 4,\n" +
            "                                    \"appendString-memory-arguments-slope\": 1,\n" +
            "                                    \"bData-cpu-arguments\": 1000,\n" +
            "                                    \"bData-memory-arguments\": 32,\n" +
            "                                    \"blake2b_256-cpu-arguments-intercept\": 117366,\n" +
            "                                    \"blake2b_256-cpu-arguments-slope\": 10475,\n" +
            "                                    \"blake2b_256-memory-arguments\": 4,\n" +
            "                                    \"cekApplyCost-exBudgetCPU\": 23000,\n" +
            "                                    \"cekApplyCost-exBudgetMemory\": 100,\n" +
            "                                    \"cekBuiltinCost-exBudgetCPU\": 23000,\n" +
            "                                    \"cekBuiltinCost-exBudgetMemory\": 100,\n" +
            "                                    \"cekConstCost-exBudgetCPU\": 23000,\n" +
            "                                    \"cekConstCost-exBudgetMemory\": 100,\n" +
            "                                    \"cekDelayCost-exBudgetCPU\": 23000,\n" +
            "                                    \"cekDelayCost-exBudgetMemory\": 100,\n" +
            "                                    \"cekForceCost-exBudgetCPU\": 23000,\n" +
            "                                    \"cekForceCost-exBudgetMemory\": 100,\n" +
            "                                    \"cekLamCost-exBudgetCPU\": 23000,\n" +
            "                                    \"cekLamCost-exBudgetMemory\": 100,\n" +
            "                                    \"cekStartupCost-exBudgetCPU\": 100,\n" +
            "                                    \"cekStartupCost-exBudgetMemory\": 100,\n" +
            "                                    \"cekVarCost-exBudgetCPU\": 23000,\n" +
            "                                    \"cekVarCost-exBudgetMemory\": 100,\n" +
            "                                    \"chooseData-cpu-arguments\": 19537,\n" +
            "                                    \"chooseData-memory-arguments\": 32,\n" +
            "                                    \"chooseList-cpu-arguments\": 175354,\n" +
            "                                    \"chooseList-memory-arguments\": 32,\n" +
            "                                    \"chooseUnit-cpu-arguments\": 46417,\n" +
            "                                    \"chooseUnit-memory-arguments\": 4,\n" +
            "                                    \"consByteString-cpu-arguments-intercept\": 221973,\n" +
            "                                    \"consByteString-cpu-arguments-slope\": 511,\n" +
            "                                    \"consByteString-memory-arguments-intercept\": 0,\n" +
            "                                    \"consByteString-memory-arguments-slope\": 1,\n" +
            "                                    \"constrData-cpu-arguments\": 89141,\n" +
            "                                    \"constrData-memory-arguments\": 32,\n" +
            "                                    \"decodeUtf8-cpu-arguments-intercept\": 497525,\n" +
            "                                    \"decodeUtf8-cpu-arguments-slope\": 14068,\n" +
            "                                    \"decodeUtf8-memory-arguments-intercept\": 4,\n" +
            "                                    \"decodeUtf8-memory-arguments-slope\": 2,\n" +
            "                                    \"divideInteger-cpu-arguments-constant\": 196500,\n" +
            "                                    \"divideInteger-cpu-arguments-model-arguments-intercept\": 453240,\n" +
            "                                    \"divideInteger-cpu-arguments-model-arguments-slope\": 220,\n" +
            "                                    \"divideInteger-memory-arguments-intercept\": 0,\n" +
            "                                    \"divideInteger-memory-arguments-minimum\": 1,\n" +
            "                                    \"divideInteger-memory-arguments-slope\": 1,\n" +
            "                                    \"encodeUtf8-cpu-arguments-intercept\": 1000,\n" +
            "                                    \"encodeUtf8-cpu-arguments-slope\": 28662,\n" +
            "                                    \"encodeUtf8-memory-arguments-intercept\": 4,\n" +
            "                                    \"encodeUtf8-memory-arguments-slope\": 2,\n" +
            "                                    \"equalsByteString-cpu-arguments-constant\": 245000,\n" +
            "                                    \"equalsByteString-cpu-arguments-intercept\": 216773,\n" +
            "                                    \"equalsByteString-cpu-arguments-slope\": 62,\n" +
            "                                    \"equalsByteString-memory-arguments\": 1,\n" +
            "                                    \"equalsData-cpu-arguments-intercept\": 1060367,\n" +
            "                                    \"equalsData-cpu-arguments-slope\": 12586,\n" +
            "                                    \"equalsData-memory-arguments\": 1,\n" +
            "                                    \"equalsInteger-cpu-arguments-intercept\": 208512,\n" +
            "                                    \"equalsInteger-cpu-arguments-slope\": 421,\n" +
            "                                    \"equalsInteger-memory-arguments\": 1,\n" +
            "                                    \"equalsString-cpu-arguments-constant\": 187000,\n" +
            "                                    \"equalsString-cpu-arguments-intercept\": 1000,\n" +
            "                                    \"equalsString-cpu-arguments-slope\": 52998,\n" +
            "                                    \"equalsString-memory-arguments\": 1,\n" +
            "                                    \"fstPair-cpu-arguments\": 80436,\n" +
            "                                    \"fstPair-memory-arguments\": 32,\n" +
            "                                    \"headList-cpu-arguments\": 43249,\n" +
            "                                    \"headList-memory-arguments\": 32,\n" +
            "                                    \"iData-cpu-arguments\": 1000,\n" +
            "                                    \"iData-memory-arguments\": 32,\n" +
            "                                    \"ifThenElse-cpu-arguments\": 80556,\n" +
            "                                    \"ifThenElse-memory-arguments\": 1,\n" +
            "                                    \"indexByteString-cpu-arguments\": 57667,\n" +
            "                                    \"indexByteString-memory-arguments\": 4,\n" +
            "                                    \"lengthOfByteString-cpu-arguments\": 1000,\n" +
            "                                    \"lengthOfByteString-memory-arguments\": 10,\n" +
            "                                    \"lessThanByteString-cpu-arguments-intercept\": 197145,\n" +
            "                                    \"lessThanByteString-cpu-arguments-slope\": 156,\n" +
            "                                    \"lessThanByteString-memory-arguments\": 1,\n" +
            "                                    \"lessThanEqualsByteString-cpu-arguments-intercept\": 197145,\n" +
            "                                    \"lessThanEqualsByteString-cpu-arguments-slope\": 156,\n" +
            "                                    \"lessThanEqualsByteString-memory-arguments\": 1,\n" +
            "                                    \"lessThanEqualsInteger-cpu-arguments-intercept\": 204924,\n" +
            "                                    \"lessThanEqualsInteger-cpu-arguments-slope\": 473,\n" +
            "                                    \"lessThanEqualsInteger-memory-arguments\": 1,\n" +
            "                                    \"lessThanInteger-cpu-arguments-intercept\": 208896,\n" +
            "                                    \"lessThanInteger-cpu-arguments-slope\": 511,\n" +
            "                                    \"lessThanInteger-memory-arguments\": 1,\n" +
            "                                    \"listData-cpu-arguments\": 52467,\n" +
            "                                    \"listData-memory-arguments\": 32,\n" +
            "                                    \"mapData-cpu-arguments\": 64832,\n" +
            "                                    \"mapData-memory-arguments\": 32,\n" +
            "                                    \"mkCons-cpu-arguments\": 65493,\n" +
            "                                    \"mkCons-memory-arguments\": 32,\n" +
            "                                    \"mkNilData-cpu-arguments\": 22558,\n" +
            "                                    \"mkNilData-memory-arguments\": 32,\n" +
            "                                    \"mkNilPairData-cpu-arguments\": 16563,\n" +
            "                                    \"mkNilPairData-memory-arguments\": 32,\n" +
            "                                    \"mkPairData-cpu-arguments\": 76511,\n" +
            "                                    \"mkPairData-memory-arguments\": 32,\n" +
            "                                    \"modInteger-cpu-arguments-constant\": 196500,\n" +
            "                                    \"modInteger-cpu-arguments-model-arguments-intercept\": 453240,\n" +
            "                                    \"modInteger-cpu-arguments-model-arguments-slope\": 220,\n" +
            "                                    \"modInteger-memory-arguments-intercept\": 0,\n" +
            "                                    \"modInteger-memory-arguments-minimum\": 1,\n" +
            "                                    \"modInteger-memory-arguments-slope\": 1,\n" +
            "                                    \"multiplyInteger-cpu-arguments-intercept\": 69522,\n" +
            "                                    \"multiplyInteger-cpu-arguments-slope\": 11687,\n" +
            "                                    \"multiplyInteger-memory-arguments-intercept\": 0,\n" +
            "                                    \"multiplyInteger-memory-arguments-slope\": 1,\n" +
            "                                    \"nullList-cpu-arguments\": 60091,\n" +
            "                                    \"nullList-memory-arguments\": 32,\n" +
            "                                    \"quotientInteger-cpu-arguments-constant\": 196500,\n" +
            "                                    \"quotientInteger-cpu-arguments-model-arguments-intercept\": 453240,\n" +
            "                                    \"quotientInteger-cpu-arguments-model-arguments-slope\": 220,\n" +
            "                                    \"quotientInteger-memory-arguments-intercept\": 0,\n" +
            "                                    \"quotientInteger-memory-arguments-minimum\": 1,\n" +
            "                                    \"quotientInteger-memory-arguments-slope\": 1,\n" +
            "                                    \"remainderInteger-cpu-arguments-constant\": 196500,\n" +
            "                                    \"remainderInteger-cpu-arguments-model-arguments-intercept\": 453240,\n" +
            "                                    \"remainderInteger-cpu-arguments-model-arguments-slope\": 220,\n" +
            "                                    \"remainderInteger-memory-arguments-intercept\": 0,\n" +
            "                                    \"remainderInteger-memory-arguments-minimum\": 1,\n" +
            "                                    \"remainderInteger-memory-arguments-slope\": 1,\n" +
            "                                    \"serialiseData-cpu-arguments-intercept\": 1159724,\n" +
            "                                    \"serialiseData-cpu-arguments-slope\": 392670,\n" +
            "                                    \"serialiseData-memory-arguments-intercept\": 0,\n" +
            "                                    \"serialiseData-memory-arguments-slope\": 2,\n" +
            "                                    \"sha2_256-cpu-arguments-intercept\": 806990,\n" +
            "                                    \"sha2_256-cpu-arguments-slope\": 30482,\n" +
            "                                    \"sha2_256-memory-arguments\": 4,\n" +
            "                                    \"sha3_256-cpu-arguments-intercept\": 1927926,\n" +
            "                                    \"sha3_256-cpu-arguments-slope\": 82523,\n" +
            "                                    \"sha3_256-memory-arguments\": 4,\n" +
            "                                    \"sliceByteString-cpu-arguments-intercept\": 265318,\n" +
            "                                    \"sliceByteString-cpu-arguments-slope\": 0,\n" +
            "                                    \"sliceByteString-memory-arguments-intercept\": 4,\n" +
            "                                    \"sliceByteString-memory-arguments-slope\": 0,\n" +
            "                                    \"sndPair-cpu-arguments\": 85931,\n" +
            "                                    \"sndPair-memory-arguments\": 32,\n" +
            "                                    \"subtractInteger-cpu-arguments-intercept\": 205665,\n" +
            "                                    \"subtractInteger-cpu-arguments-slope\": 812,\n" +
            "                                    \"subtractInteger-memory-arguments-intercept\": 1,\n" +
            "                                    \"subtractInteger-memory-arguments-slope\": 1,\n" +
            "                                    \"tailList-cpu-arguments\": 41182,\n" +
            "                                    \"tailList-memory-arguments\": 32,\n" +
            "                                    \"trace-cpu-arguments\": 212342,\n" +
            "                                    \"trace-memory-arguments\": 32,\n" +
            "                                    \"unBData-cpu-arguments\": 31220,\n" +
            "                                    \"unBData-memory-arguments\": 32,\n" +
            "                                    \"unConstrData-cpu-arguments\": 32696,\n" +
            "                                    \"unConstrData-memory-arguments\": 32,\n" +
            "                                    \"unIData-cpu-arguments\": 43357,\n" +
            "                                    \"unIData-memory-arguments\": 32,\n" +
            "                                    \"unListData-cpu-arguments\": 32247,\n" +
            "                                    \"unListData-memory-arguments\": 32,\n" +
            "                                    \"unMapData-cpu-arguments\": 38314,\n" +
            "                                    \"unMapData-memory-arguments\": 32,\n" +
            "                                    \"verifyEcdsaSecp256k1Signature-cpu-arguments\": 35892428,\n" +
            "                                    \"verifyEcdsaSecp256k1Signature-memory-arguments\": 10,\n" +
            "                                    \"verifyEd25519Signature-cpu-arguments-intercept\": 57996947,\n" +
            "                                    \"verifyEd25519Signature-cpu-arguments-slope\": 18975,\n" +
            "                                    \"verifyEd25519Signature-memory-arguments\": 10,\n" +
            "                                    \"verifySchnorrSecp256k1Signature-cpu-arguments-intercept\": 38887044,\n" +
            "                                    \"verifySchnorrSecp256k1Signature-cpu-arguments-slope\": 32947,\n" +
            "                                    \"verifySchnorrSecp256k1Signature-memory-arguments\": 10\n" +
            "                                    }";


    /**
     * Get the list of operations for a given  plutus version
     * @param version 1 or 2
     * @return list of operations
     */
    public static List<String> getOperations(int version) {
        if (V1_OPS == null || V2_OPS == null) {
            initOps();
        }

        if (version == 1)
            return V1_OPS;
        else if (version == 2)
            return V2_OPS;
        else
            throw new IllegalArgumentException("Invalid version: " + version);
    }

    private static void initOps() {

        try {
            Map<String, Long> map1 = objectMapper.readValue(PLUTUS_V1_COSTS, new TypeReference<SortedMap<String, Long>>() {
            });
            Set<String> keys = map1.keySet();
            V1_OPS = keys.stream().sorted().collect(Collectors.toList());

            Map<String, Long> map2 = objectMapper.readValue(PLUTUS_V2_COSTS, new TypeReference<SortedMap<String, Long>>() {
            });
            keys = map2.keySet();
            V2_OPS = keys.stream().sorted().collect(Collectors.toList());

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
