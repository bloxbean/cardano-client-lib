package com.bloxbean.cardano.client.plutus.annotation.processor;

import com.bloxbean.cardano.client.plutus.annotation.processor.converter.*;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.ClassDefinition;
import com.squareup.javapoet.TypeSpec;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.List;
import java.util.Optional;

/**
 * Code generator for Plutus Data Converter.
 * Delegates to focused builders for class, interface, and enum converter generation.
 */
@Slf4j
public class ConverterCodeGenerator implements CodeGenerator {

    private final ClassConverterBuilder classBuilder;
    private final InterfaceConverterBuilder interfaceBuilder;
    private final EnumConverterBuilder enumBuilder;

    public ConverterCodeGenerator(ProcessingEnvironment processingEnvironment) {
        SerDeMethodBuilder serDe = new SerDeMethodBuilder();

        this.classBuilder = new ClassConverterBuilder(new FieldCodeGeneratorRegistry(), serDe);
        this.interfaceBuilder = new InterfaceConverterBuilder(serDe);
        this.enumBuilder = new EnumConverterBuilder(serDe);
    }

    @Override
    public TypeSpec generate(ClassDefinition classDef) {
        return classBuilder.build(classDef);
    }

    public TypeSpec generateInterfaceConverter(ClassDefinition classDef, List<ClassDefinition> constructors) {
        return interfaceBuilder.build(classDef, constructors);
    }

    public Optional<TypeSpec> generateEnumConverter(ClassDefinition classDefinition) {
        return enumBuilder.build(classDefinition);
    }

}
