package com.bloxbean.cardano.client.plutus.annotation.processor.model;

import com.squareup.javapoet.ClassName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.plutus.annotation.processor.util.Constant.CONVERTER;
import static com.bloxbean.cardano.client.plutus.annotation.processor.util.Constant.IMPL;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassDefinition {
    private String packageName;
    private String dataClassName;
    private String implClassName;
    private String converterClassName;
    private boolean isAbstract;
    private String objType;
    private int alternative;

    private String converterPackageName;
    private String implPackageName;

    /**
     * When set, the converter for this type is nested inside the named interface.
     * For variant inner classes: the enclosing interface name (e.g., "Credential").
     * For interfaces themselves: the interface's own name (converter nested inside itself).
     * When null, the converter is a separate top-level file in the converter sub-package.
     */
    private String enclosingInterfaceName;

    private boolean hasLombokAnnotation;

    private boolean isEnum;
    private List<String> enumValues;

    private List<Field> fields = new ArrayList<>();

    // --- Static factory methods ---

    /**
     * Creates a ClassDefinition for a variant inner class nested inside an interface.
     * Converter is nested inside the interface, impl name is prefixed with interface name.
     */
    public static ClassDefinition forNestedVariant(String pkg, String interfaceName,
                                                    String variantName, int alternative) {
        ClassDefinition def = new ClassDefinition();
        def.setPackageName(pkg);
        def.setDataClassName(variantName);
        def.setObjType(pkg + "." + interfaceName + "." + variantName);
        def.setAlternative(alternative);
        def.setAbstract(true);
        def.setHasLombokAnnotation(false);
        def.setConverterClassName(variantName + CONVERTER);
        def.setConverterPackageName(pkg);
        def.setEnclosingInterfaceName(interfaceName);
        def.setImplClassName(interfaceName + variantName + IMPL);
        def.setImplPackageName(pkg + ".impl");
        return def;
    }

    /**
     * Creates a ClassDefinition for an interface type (dispatch converter nested inside itself).
     */
    public static ClassDefinition forInterface(String pkg, String interfaceName) {
        ClassDefinition def = new ClassDefinition();
        def.setPackageName(pkg);
        def.setDataClassName(interfaceName);
        def.setObjType(pkg + "." + interfaceName);
        def.setAlternative(0);
        def.setAbstract(false);
        def.setHasLombokAnnotation(false);
        def.setConverterClassName(interfaceName + CONVERTER);
        def.setConverterPackageName(pkg);
        def.setEnclosingInterfaceName(interfaceName);
        def.setImplClassName(interfaceName + IMPL);
        def.setImplPackageName(pkg + ".impl");
        return def;
    }

    /**
     * Creates a ClassDefinition for a regular top-level type.
     * Converter is in the {@code .converter} sub-package.
     */
    public static ClassDefinition forTopLevel(String pkg, String className) {
        ClassDefinition def = new ClassDefinition();
        def.setPackageName(pkg);
        def.setDataClassName(className);
        def.setObjType(pkg + "." + className);
        def.setConverterClassName(className + CONVERTER);
        def.setConverterPackageName(pkg + ".converter");
        def.setImplClassName(className + IMPL);
        def.setImplPackageName(pkg + ".impl");
        return def;
    }

    /**
     * Resolves the converter {@link ClassName}, handling nested converters.
     * When {@code enclosingInterfaceName} is set, the converter is nested inside the interface.
     */
    public ClassName resolveConverterClassName() {
        if (enclosingInterfaceName != null) {
            return ClassName.get(converterPackageName, enclosingInterfaceName, converterClassName);
        }
        return ClassName.get(converterPackageName, converterClassName);
    }
}

