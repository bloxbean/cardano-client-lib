package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.metadata.MetadataList;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldAccessor;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo;

import static com.bloxbean.cardano.client.metadata.annotation.processor.MetadataConstants.*;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataTypeCodeGen;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataTypeCodeGenRegistry;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

import java.util.*;

/**
 * Code generation for {@code List<T>}, {@code Set<T>}, and {@code SortedSet<T>} fields.
 * Provides the fixed skeleton (MetadataList creation, for-loop, null-check) and delegates
 * element-level serialization/deserialization to the registry.
 */
@SuppressWarnings("java:S1192") // JavaPoet format strings are intentionally repeated across similar codegen methods
public class CollectionCodeGen {

    private final MetadataTypeCodeGenRegistry registry;
    private final MetadataFieldAccessor accessor;
    private final EnumCodeGen enumCodeGen;
    private NestedTypeCodeGen nestedCodeGen;

    public CollectionCodeGen(MetadataTypeCodeGenRegistry registry, MetadataFieldAccessor accessor,
                             EnumCodeGen enumCodeGen) {
        this.registry = registry;
        this.accessor = accessor;
        this.enumCodeGen = enumCodeGen;
    }

    public void setNestedCodeGen(NestedTypeCodeGen nestedCodeGen) {
        this.nestedCodeGen = nestedCodeGen;
    }

    // --- Serialization ---

    public void emitSerializeToMap(MethodSpec.Builder builder, MetadataFieldInfo field,
                                   String getExpr) {
        String key = field.getMetadataKey();

        // Composite: List<List<T>>
        if (field.isElementCollectionType()) {
            emitSerializeCollectionOfCollection(builder, field, getExpr, key);
            return;
        }

        // Composite: List<Map<String, V>>
        if (field.isElementMapType()) {
            emitSerializeCollectionOfMap(builder, field, getExpr, key);
            return;
        }

        TypeName elemTypeName = elementTypeName(field);

        builder.addStatement("$T _list = $T.createList()", MetadataList.class, MetadataBuilder.class);
        builder.beginControlFlow("for ($T _el : $L)", elemTypeName, getExpr);
        builder.beginControlFlow("if (_el != null)");

        if (field.isElementNestedType()) {
            nestedCodeGen.emitSerializeToList(builder, field);
        } else if (field.isElementEnumType()) {
            enumCodeGen.emitSerializeToList(builder);
        } else {
            MetadataTypeCodeGen codeGen = registry.get(field.getElementTypeName());
            codeGen.emitSerializeToList(builder, field.getElementTypeName());
        }

        builder.endControlFlow(); // if not null
        builder.endControlFlow(); // for loop
        builder.addStatement("map.put($S, _list)", key);
    }

    private void emitSerializeCollectionOfCollection(MethodSpec.Builder builder, MetadataFieldInfo field,
                                                      String getExpr, String key) {
        TypeName innerElemTypeName = CompositeCodeGenHelper.resolveLeafTypeName(field.getElementElementTypeName(),
                field.isElementElementEnumType(), field.isElementElementNestedType());

        String innerCollKind = field.getElementCollectionKind();
        ClassName innerCollInterface = ClassName.bestGuess(innerCollKind);
        ParameterizedTypeName innerCollType = ParameterizedTypeName.get(innerCollInterface, innerElemTypeName);

        builder.addStatement("$T _list = $T.createList()", MetadataList.class, MetadataBuilder.class);
        builder.beginControlFlow("for ($T _el : $L)", innerCollType, getExpr);
        builder.beginControlFlow("if (_el != null)");

        builder.addStatement("$T _innerList = $T.createList()", MetadataList.class, MetadataBuilder.class);
        builder.beginControlFlow("for ($T _innerEl : _el)", innerElemTypeName);
        builder.beginControlFlow("if (_innerEl != null)");

        CompositeCodeGenHelper.emitAddToList(builder, registry, "_innerList", "_innerEl",
                new CompositeCodeGenHelper.LeafTypeInfo(field.getElementElementTypeName(),
                        field.isElementElementEnumType(), field.isElementElementNestedType(),
                        field.getElementElementConverterFqn()));

        builder.endControlFlow(); // if not null
        builder.endControlFlow(); // inner for
        builder.addStatement("_list.add(_innerList)");

        builder.endControlFlow(); // if not null
        builder.endControlFlow(); // outer for
        builder.addStatement("map.put($S, _list)", key);
    }

    private void emitSerializeCollectionOfMap(MethodSpec.Builder builder, MetadataFieldInfo field,
                                               String getExpr, String key) {
        String elemKeyTypeName = field.getElementMapKeyTypeName();
        TypeName elemKeyTN = MapCodeGen.resolveKeyTypeName(elemKeyTypeName);
        TypeName innerValTypeName = CompositeCodeGenHelper.resolveLeafTypeName(field.getElementMapValueTypeName(),
                field.isElementMapValueEnumType(), field.isElementMapValueNestedType());
        ParameterizedTypeName innerMapType = ParameterizedTypeName.get(
                ClassName.get(Map.class), elemKeyTN, innerValTypeName);

        builder.addStatement("$T _list = $T.createList()", MetadataList.class, MetadataBuilder.class);
        builder.beginControlFlow("for ($T _el : $L)", innerMapType, getExpr);
        builder.beginControlFlow("if (_el != null)");

        builder.addStatement("$T _innerMap = $T.createMap()", MetadataMap.class, MetadataBuilder.class);
        builder.beginControlFlow("for ($T<$T, $T> _innerEntry : _el.entrySet())",
                Map.Entry.class, elemKeyTN, innerValTypeName);
        builder.beginControlFlow("if (_innerEntry.getValue() != null)");

        String innerSerKey = MapCodeGen.serKeyExpr(elemKeyTypeName, "_innerEntry");

        CompositeCodeGenHelper.emitPutToMap(builder, registry, "_innerMap", innerSerKey, "_innerEntry.getValue()",
                new CompositeCodeGenHelper.LeafTypeInfo(field.getElementMapValueTypeName(),
                        field.isElementMapValueEnumType(), field.isElementMapValueNestedType(),
                        field.getElementMapValueConverterFqn()));

        builder.endControlFlow(); // if not null
        builder.endControlFlow(); // inner for
        builder.addStatement("_list.add(_innerMap)");

        builder.endControlFlow(); // if not null
        builder.endControlFlow(); // outer for
        builder.addStatement("map.put($S, _list)", key);
    }

    // --- Deserialization ---

    public void emitDeserializeFromMap(MethodSpec.Builder builder, MetadataFieldInfo field) {
        // Composite: List<List<T>>
        if (field.isElementCollectionType()) {
            emitDeserializeCollectionOfCollection(builder, field);
            return;
        }

        // Composite: List<Map<String, V>>
        if (field.isElementMapType()) {
            emitDeserializeCollectionOfMap(builder, field);
            return;
        }

        CollectionTypeInfo typeInfo = resolveCollectionTypeInfo(field);

        TypeName elemTypeName = elementTypeName(field);
        ParameterizedTypeName collectionType =
                ParameterizedTypeName.get(typeInfo.interfaceClass, elemTypeName);

        builder.beginControlFlow("if (v instanceof $T)", MetadataList.class);
        builder.addStatement("$T _rawList = ($T) v", MetadataList.class, MetadataList.class);
        builder.addStatement("$T _result = new $T<>()", collectionType, typeInfo.implClass);
        builder.beginControlFlow("for (int _i = 0; _i < _rawList.size(); _i++)");
        builder.addStatement("$T _el = _rawList.getValueAt(_i)", Object.class);

        if (field.isElementNestedType()) {
            nestedCodeGen.emitDeserializeElement(builder, field);
        } else if (field.isElementEnumType()) {
            enumCodeGen.emitDeserializeElement(builder, field);
        } else {
            MetadataTypeCodeGen codeGen = registry.get(field.getElementTypeName());
            codeGen.emitDeserializeElement(builder, field.getElementTypeName());
        }

        builder.endControlFlow(); // for loop
        accessor.emitSetRaw(builder, field, "_result");
        builder.endControlFlow(); // instanceof MetadataList
    }

    private void emitDeserializeCollectionOfCollection(MethodSpec.Builder builder, MetadataFieldInfo field) {
        CollectionTypeInfo outer = resolveCollectionTypeInfo(field);

        TypeName innerElemTypeName = CompositeCodeGenHelper.resolveLeafTypeName(field.getElementElementTypeName(),
                field.isElementElementEnumType(), field.isElementElementNestedType());

        String innerCollKind = field.getElementCollectionKind();
        ClassName innerCollInterface = ClassName.bestGuess(innerCollKind);
        ClassName innerCollImpl = CompositeCodeGenHelper.collectionImplClass(innerCollKind);
        ParameterizedTypeName innerCollType = ParameterizedTypeName.get(innerCollInterface, innerElemTypeName);

        ParameterizedTypeName outerCollType = ParameterizedTypeName.get(outer.interfaceClass, innerCollType);

        builder.beginControlFlow("if (v instanceof $T)", MetadataList.class);
        builder.addStatement("$T _rawList = ($T) v", MetadataList.class, MetadataList.class);
        builder.addStatement("$T _result = new $T<>()", outerCollType, outer.implClass);
        builder.beginControlFlow("for (int _i = 0; _i < _rawList.size(); _i++)");
        builder.addStatement("$T _el = _rawList.getValueAt(_i)", Object.class);

        builder.beginControlFlow("if (_el instanceof $T)", MetadataList.class);
        builder.addStatement("$T _innerRawList = ($T) _el", MetadataList.class, MetadataList.class);
        builder.addStatement("$T _innerResult = new $T<>()", innerCollType, innerCollImpl);
        builder.beginControlFlow("for (int _j = 0; _j < _innerRawList.size(); _j++)");
        builder.addStatement("$T _innerEl = _innerRawList.getValueAt(_j)", Object.class);

        CompositeCodeGenHelper.emitDeserializeLeafFromRaw(builder, registry, "_innerResult", "_innerEl",
                new CompositeCodeGenHelper.LeafTypeInfo(field.getElementElementTypeName(),
                        field.isElementElementEnumType(), field.isElementElementNestedType(),
                        field.getElementElementConverterFqn()));

        builder.endControlFlow(); // for _j
        builder.addStatement("_result.add(_innerResult)");
        builder.endControlFlow(); // if MetadataList

        builder.endControlFlow(); // for _i
        accessor.emitSetRaw(builder, field, "_result");
        builder.endControlFlow(); // instanceof MetadataList
    }

    private void emitDeserializeCollectionOfMap(MethodSpec.Builder builder, MetadataFieldInfo field) {
        CollectionTypeInfo outer = resolveCollectionTypeInfo(field);
        String elemKeyTypeName = field.getElementMapKeyTypeName();
        TypeName elemKeyTN = MapCodeGen.resolveKeyTypeName(elemKeyTypeName);
        Class<?> elemKeyChain = MapCodeGen.keyOnChainClass(elemKeyTypeName);

        TypeName innerValTypeName = CompositeCodeGenHelper.resolveLeafTypeName(field.getElementMapValueTypeName(),
                field.isElementMapValueEnumType(), field.isElementMapValueNestedType());
        ParameterizedTypeName innerMapType = ParameterizedTypeName.get(
                ClassName.get(Map.class), elemKeyTN, innerValTypeName);
        ParameterizedTypeName outerCollType = ParameterizedTypeName.get(outer.interfaceClass, innerMapType);

        builder.beginControlFlow("if (v instanceof $T)", MetadataList.class);
        builder.addStatement("$T _rawList = ($T) v", MetadataList.class, MetadataList.class);
        builder.addStatement("$T _result = new $T<>()", outerCollType, outer.implClass);
        builder.beginControlFlow("for (int _i = 0; _i < _rawList.size(); _i++)");
        builder.addStatement("$T _el = _rawList.getValueAt(_i)", Object.class);

        builder.beginControlFlow("if (_el instanceof $T)", MetadataMap.class);
        builder.addStatement("$T _innerRawMap = ($T) _el", MetadataMap.class, MetadataMap.class);
        builder.addStatement("$T _innerResult = new $T<>()", innerMapType, LinkedHashMap.class);
        builder.beginControlFlow("for ($T _innerK : _innerRawMap.keys())", Object.class);
        builder.beginControlFlow("if (_innerK instanceof $T)", elemKeyChain);
        builder.addStatement("$T _innerVal = _innerRawMap.get(($T) _innerK)", Object.class, elemKeyChain);

        String innerDkExpr = MapCodeGen.deserKeyExpr(elemKeyTypeName, "_innerK");
        CompositeCodeGenHelper.emitDeserializeLeafFromRawToMap(builder, registry, "_innerResult", innerDkExpr, "_innerVal",
                new CompositeCodeGenHelper.LeafTypeInfo(field.getElementMapValueTypeName(),
                        field.isElementMapValueEnumType(), field.isElementMapValueNestedType(),
                        field.getElementMapValueConverterFqn()));

        builder.endControlFlow(); // if _innerK instanceof
        builder.endControlFlow(); // for _innerK
        builder.addStatement("_result.add(_innerResult)");
        builder.endControlFlow(); // if MetadataMap

        builder.endControlFlow(); // for _i
        accessor.emitSetRaw(builder, field, "_result");
        builder.endControlFlow(); // instanceof MetadataList
    }

    // --- Type helpers ---

    private record CollectionTypeInfo(ClassName interfaceClass, ClassName implClass) {}

    private CollectionTypeInfo resolveCollectionTypeInfo(MetadataFieldInfo field) {
        String kind = field.getCollectionKind();
        return switch (kind) {
            case COLLECTION_LIST -> new CollectionTypeInfo(
                    ClassName.get("java.util", "List"),
                    ClassName.get("java.util", "ArrayList"));
            case COLLECTION_SET -> new CollectionTypeInfo(
                    ClassName.get("java.util", "Set"),
                    ClassName.get("java.util", "LinkedHashSet"));
            default -> new CollectionTypeInfo(
                    ClassName.get("java.util", "SortedSet"),
                    ClassName.get("java.util", "TreeSet"));
        };
    }

    private TypeName elementTypeName(MetadataFieldInfo field) {
        if (field.isElementEnumType() || field.isElementNestedType()) {
            return ClassName.bestGuess(field.getElementTypeName());
        }
        return CompositeCodeGenHelper.scalarTypeName(field.getElementTypeName());
    }
}
