package com.bloxbean.cardano.client.plutus.annotation.processor.model;

import com.squareup.javapoet.ClassName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

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

