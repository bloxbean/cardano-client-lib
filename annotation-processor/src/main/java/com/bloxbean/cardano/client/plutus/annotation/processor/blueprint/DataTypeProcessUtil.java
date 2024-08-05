package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.JavaFileUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.squareup.javapoet.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype.*;

public class DataTypeProcessUtil {

    private final FieldSpecProcessor fieldSpecProcessor;
    private final Blueprint annotation;
    private final ProcessingEnvironment processingEnv;

    public DataTypeProcessUtil(FieldSpecProcessor fieldSpecProcessor, Blueprint annotation, ProcessingEnvironment processingEnv) {
        this.fieldSpecProcessor = fieldSpecProcessor;
        this.annotation = annotation;
        this.processingEnv = processingEnv;
    }

    /**
     * Creates a FieldSpec for a given integer schema
     * @param javaDoc
     * @param schema
     * @return
     */
    public FieldSpec processIntegerDataType(String javaDoc, BlueprintSchema schema, String alternativeName) {
        if(schema.getDataType() != integer)
            throw new IllegalArgumentException("Schema is not of type integer");
        String title = schema.getTitle() == null ? alternativeName : schema.getTitle();
        return FieldSpec.builder(int.class, title)
                .addModifiers(Modifier.PRIVATE)
                .addJavadoc(javaDoc)
                .build();
    }

    /**
     * Creates a FieldSpec for a given bytes schema
     * @param javaDoc
     * @param schema
     * @return
     */
    public FieldSpec processBytesDataType(String javaDoc, BlueprintSchema schema, String alternativeName) {
        if(schema.getDataType() != bytes)
            throw new IllegalArgumentException("Schema is not of type bytes");
        String title = schema.getTitle() == null ? alternativeName : schema.getTitle();
        return FieldSpec.builder(byte[].class, title)
                .addModifiers(Modifier.PRIVATE)
                .addJavadoc(javaDoc)
                .build();

    }

    /**
     * Creates a FieldSpec for a given list schema
     * @param javaDoc
     * @param schema
     * @return
     */
    public FieldSpec processListDataType(String ns, String javaDoc, BlueprintSchema schema, String alternativeName) {
        if(schema.getDataType() != list)
            throw new IllegalArgumentException("Schema is not of type list");
        String title = schema.getTitle() == null ? alternativeName : schema.getTitle();
        TypeName fieldClass = getTypeNameForListParametrizedType(ns, schema);
        return FieldSpec.builder(fieldClass, title)
                .addModifiers(Modifier.PRIVATE)
                .addJavadoc(javaDoc)
                .build();
    }

    private TypeName getTypeNameForListParametrizedType(String ns, BlueprintSchema field) {
        return ParameterizedTypeName.get(ClassName.get("java.util", "List"), getInnerType(ns, field.getItems()));
    }

    private TypeName getTypeNameForMapParametrizedType(String ns, BlueprintSchema field) {
        return ParameterizedTypeName.get(ClassName.get("java.util", "Map"), getInnerType(ns, field.getKeys()), getInnerType(ns, field.getValues()));
    }

    private TypeName getInnerType(String ns, BlueprintSchema items) {
        if(items.getDataType() == null) // Case for constructor data type
        {
            TypeName typeName = fieldSpecProcessor.getInnerDatumClass(ns, items);
            return typeName;
        }
        switch (items.getDataType()) {
            case bytes:
                return TypeName.get(byte[].class);
            case integer:
                return TypeName.get(Integer.class);
            case string:
                return TypeName.get(String.class);
            case bool:
                return TypeName.get(Boolean.class);
            case list:
                return getTypeNameForListParametrizedType(ns, items);
            case map:
                return getTypeNameForMapParametrizedType(ns, items);
            default:
                return TypeName.get(String.class);
        }
    }

    /**
     * Creates a FieldSpec for a given boolean schema
     * @param javaDoc
     * @param schema
     * @return
     */
    public FieldSpec processBoolDataType(String javaDoc, BlueprintSchema schema, String alternativeName) {
        if(schema.getDataType() != bool)
            throw new IllegalArgumentException("Schema is not of type boolean");
        String title = schema.getTitle() == null ? alternativeName : schema.getTitle();
        return FieldSpec.builder(boolean.class, title)
                .addModifiers(Modifier.PRIVATE)
                .addJavadoc(javaDoc)
                .build();
    }

    /**
     * Creates a FieldSpec for a given string schema
     * @param javaDoc
     * @param schema
     * @return
     */
    public FieldSpec processStringDataType(String javaDoc, BlueprintSchema schema, String alternativeName) {
        if(schema.getDataType() != string)
            throw new IllegalArgumentException("Schema is not of type string");
        String title = schema.getTitle() == null ? alternativeName : schema.getTitle();
        return FieldSpec.builder(String.class, title)
                .addModifiers(Modifier.PRIVATE)
                .addJavadoc(javaDoc)
                .build();
    }

    public List<FieldSpec> processConstructorDataType(String ns, String javaDoc, BlueprintSchema schema, String className, String alternativeName) {
        List<FieldSpec> specs = new ArrayList<>();
        for (BlueprintSchema field : schema.getFields()) {
            if(field.getDataType() != null) {
                javaDoc += " Index: " + field.getIndex() ;
                specs.addAll(fieldSpecProcessor.createFieldSpecForDataTypes(ns, javaDoc,  List.of(field)));
            } else {
                //TODO
                specs.add(fieldSpecProcessor.createDatumFieldSpec(ns, "", field, field.getTitle())._1);
            }
        }
        if(schema.getFields().isEmpty()) { // TODO Fields is empty that's why no mint or burn is generated Enums
            specs.add(createEnumAndAddToFields(ns, schema, className));
        }
        return specs;
    }

    /**
     * TODO need to check how an enum has to be presented
     * @param schema
     * @param className
     * @return
     */
    private FieldSpec createEnumAndAddToFields(String ns, BlueprintSchema schema, String className) {
        AnnotationSpec constrAnnotationBuilder = AnnotationSpec.builder(Constr.class).addMember("alternative", "$L", schema.getIndex()).build();

        String fieldName = JavaFileUtil.firstUpperCase(schema.getTitle());

        TypeSpec enumConstr = TypeSpec.classBuilder(fieldName)
                .addAnnotation(constrAnnotationBuilder)
                .addModifiers(Modifier.PUBLIC)
                .build();

        String pkg = getPackage(ns);

    //TODO --remove    JavaFileUtil.createJavaFile(pkg, enumConstr, enumConstr.name, processingEnv);
        ClassName classIdentifier = ClassName.get(pkg, enumConstr.name);
        return FieldSpec.builder(classIdentifier, fieldName)
                .addModifiers(Modifier.PRIVATE)
                .addJavadoc("Index: " + schema.getIndex())
                .build();
    }

    public FieldSpec processMapDataType(String ns, String javaDoc, BlueprintSchema schema, String className, String alternativeName) {
        if(schema.getDataType() != map)
            throw new IllegalArgumentException("Schema is not of type map");
        String title = schema.getTitle() == null ? alternativeName : schema.getTitle();
        TypeName fieldClass = getTypeNameForMapParametrizedType(ns, schema);
        return FieldSpec.builder(fieldClass, title)
                .addModifiers(Modifier.PRIVATE)
                .addJavadoc(javaDoc)
                .build();
    }

    public FieldSpec processPlutusDataType(String javaDoc, BlueprintSchema schema, String alternativeName) {
        if(schema.getDataType() != null)
            throw new IllegalArgumentException("Schema is not of type plutusdata");
        String title = schema.getTitle() == null ? alternativeName : schema.getTitle();
        return FieldSpec.builder(PlutusData.class, title)
                .addModifiers(Modifier.PRIVATE)
                .addJavadoc(javaDoc)
                .build();
    }

    private String getPackage(String ns) {
        String pkg = (ns != null && !ns.isEmpty())? annotation.packageName() + "." + ns + ".model"
                : annotation.packageName() + ".model";
        return pkg;
    }
}
