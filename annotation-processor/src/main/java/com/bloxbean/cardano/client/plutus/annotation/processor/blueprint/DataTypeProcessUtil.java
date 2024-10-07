package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.JavaFileUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.squareup.javapoet.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.plutus.annotation.processor.util.JavaFileUtil.toPackageNameFormat;
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

        title = JavaFileUtil.firstLowerCase(JavaFileUtil.toCamelCase(title));
        return FieldSpec.builder(BigInteger.class, title)
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

        title = JavaFileUtil.firstLowerCase(JavaFileUtil.toCamelCase(title));
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

        title = JavaFileUtil.firstLowerCase(JavaFileUtil.toCamelCase(title));
        return FieldSpec.builder(fieldClass, title)
                .addModifiers(Modifier.PRIVATE)
                .addJavadoc(javaDoc)
                .build();
    }

    public FieldSpec processOptionDataType(String ns, String javaDoc, BlueprintSchema schema, String alternativeName) {
        if(schema.getDataType() != option)
            throw new IllegalArgumentException("Schema is not of type option");
        String title = schema.getTitle() == null ? alternativeName : schema.getTitle();
        TypeName fieldClass = getTypeNameForOptionParametrizedType(ns, schema);

        title = JavaFileUtil.firstLowerCase(JavaFileUtil.toCamelCase(title));
        return FieldSpec.builder(fieldClass, title)
                .addModifiers(Modifier.PRIVATE)
                .addJavadoc(javaDoc)
                .build();
    }

    public FieldSpec processPairDataType(String ns, String javaDoc, BlueprintSchema schema, String alternativeName) {
        if(schema.getDataType() != pair)
            throw new IllegalArgumentException("Schema is not of type pair");
        String title = schema.getTitle() == null ? alternativeName : schema.getTitle();
        TypeName fieldClass = getTypeNameForPairParametrizedType(ns, schema);

        title = JavaFileUtil.firstLowerCase(JavaFileUtil.toCamelCase(title));
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

    private TypeName getTypeNameForOptionParametrizedType(String ns, BlueprintSchema field) {
        var anyOfs = field.getAnyOf();
        if (anyOfs == null || anyOfs.size() != 2)
            throw new IllegalArgumentException("Option type should have 2 anyOfs");

        //Get the first anyof with Some title
        BlueprintSchema someSchema = anyOfs.stream().filter(s -> s.getTitle().equals("Some")).findFirst().orElse(null);
        if(someSchema == null)
            throw new IllegalArgumentException("Option type should have a Some type");

        if (someSchema.getFields().size() > 1)
            throw new IllegalArgumentException("Option type should have only one field in Some type");

        return ParameterizedTypeName.get(ClassName.get("java.util", "Optional"), getInnerType(ns, someSchema.getFields().get(0)));
    }

    private TypeName getTypeNameForPairParametrizedType(String ns, BlueprintSchema schema) {
        var left = schema.getLeft();
        var right = schema.getRight();

        if(left == null || right == null)
            throw new IllegalArgumentException("Pair type should have left and right fields");

        return ParameterizedTypeName.get(ClassName.get("com.bloxbean.cardano.client.plutus.blueprint.type", "Pair"), getInnerType(ns, left), getInnerType(ns, right));
    }

    private TypeName getInnerType(String ns, BlueprintSchema items) {
        if(items.getDataType() == null) // Case for constructor data type
        {
            TypeName typeName = fieldSpecProcessor.getInnerDatumClass(ns, items);
            return typeName;
        }
        var dataType = items.getDataType();
        switch (dataType) {
            case bytes:
                return TypeName.get(byte[].class);
            case integer:
                return TypeName.get(BigInteger.class);
            case string:
                return TypeName.get(String.class);
            case bool:
                return TypeName.get(Boolean.class);
            case list:
                return getTypeNameForListParametrizedType(ns, items);
            case map:
                return getTypeNameForMapParametrizedType(ns, items);
            case option:
                return getTypeNameForOptionParametrizedType(ns, items);
            case pair:
                return getTypeNameForPairParametrizedType(ns, items);
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

        title = JavaFileUtil.firstLowerCase(JavaFileUtil.toCamelCase(title));
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

        title = JavaFileUtil.firstLowerCase(JavaFileUtil.toCamelCase(title));
        return FieldSpec.builder(String.class, title)
                .addModifiers(Modifier.PRIVATE)
                .addJavadoc(javaDoc)
                .build();
    }

    public List<FieldSpec> processConstructorDataType(String ns, String javaDoc, BlueprintSchema schema, String className, String alternativeName) {
        List<FieldSpec> specs = new ArrayList<>();
        for (BlueprintSchema field : schema.getFields()) {
            if(field.getDataType() != null) {
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

        title = JavaFileUtil.firstLowerCase(JavaFileUtil.toCamelCase(title));
        return FieldSpec.builder(fieldClass, title)
                .addModifiers(Modifier.PRIVATE)
                .addJavadoc(javaDoc)
                .build();
    }

    public FieldSpec processPlutusDataType(String javaDoc, BlueprintSchema schema, String alternativeName) {
        if(schema.getDataType() != null)
            throw new IllegalArgumentException("Schema is not of type plutusdata");
        String title = schema.getTitle() == null ? alternativeName : schema.getTitle();

        title = JavaFileUtil.firstLowerCase(JavaFileUtil.toCamelCase(title));
        return FieldSpec.builder(PlutusData.class, title)
                .addModifiers(Modifier.PRIVATE)
                .addJavadoc(javaDoc)
                .build();
    }

    private String getPackage(String ns) {
        String pkg = (ns != null && !ns.isEmpty())? annotation.packageName() + "." + ns + ".model"
                : annotation.packageName() + ".model";

        return toPackageNameFormat(pkg);
    }
}
