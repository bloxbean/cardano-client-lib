# Blueprint Code Generation Refactoring Plan

## Current Code Analysis

After analyzing the blueprint annotation processor package, I've identified several **maintainability issues** and **anti-patterns** that make the code complex and hard to maintain:

### **Critical Issues Identified**

## **1. God Class Anti-Pattern**

### **Problem:**
- `FieldSpecProcessor` (395 lines) handles too many responsibilities
- `ValidatorProcessor` (347 lines) also overloaded with multiple concerns
- Methods are doing too much with complex conditional logic

### **Evidence:**
```java
// FieldSpecProcessor.java:42 - Single method doing too much
public void createDatumClass(String ns, BlueprintSchema schema) {
    // 55+ lines of complex conditional logic
    // Multiple responsibilities: validation, enum creation, interface creation, field collection
    if (dataClassName == null || dataClassName.isEmpty()) return;
    if (schema.getDataType() != null && schema.getDataType().isPrimitiveType()) return;
    if ("Option".equals(dataClassName)) { if(isOptionType(schema)) return; }
    if ("Pair".equals(dataClassName)) { if (schema.getDataType() == BlueprintDatatype.pair) return; }
    if(createEnumIfPossible(ns, schema)) return;
    // ... more complex logic
}
```

## **2. Switch Statement Anti-Pattern**

### **Problem:**
```java
// DataTypeProcessUtil.java:354 - Large switch with no polymorphism
switch (schemaType) {
    case bytes: specs.add(dataTypeProcessUtil.processBytesDataType(javaDoc, schema, alternativeName)); break;
    case integer: specs.add(dataTypeProcessUtil.processIntegerDataType(javaDoc, schema, alternativeName)); break;
    case bool: specs.add(dataTypeProcessUtil.processBoolDataType(javaDoc, schema, alternativeName)); break;
    case list: specs.add(dataTypeProcessUtil.processListDataType(ns, javaDoc, schema, alternativeName)); break;
    case map: specs.add(dataTypeProcessUtil.processMapDataType(ns, javaDoc, schema, className, alternativeName)); break;
    case constructor: specs.addAll(dataTypeProcessUtil.processConstructorDataType(ns, javaDoc, schema, className, alternativeName)); break;
    // ... 8 more cases
}
```

## **3. Deep Nesting and Complex Conditionals**

### **Problem:**
```java
// FieldSpecProcessor.java:99 - Complex nested conditions
private boolean isOptionType(BlueprintSchema schema) {
    if(schema.getAnyOf() == null || schema.getAnyOf().size() != 2) return false;
    BlueprintSchema someSchema = schema.getAnyOf().get(0);
    BlueprintSchema noneSchema = schema.getAnyOf().get(1);
    if(someSchema.getTitle() == null || noneSchema.getTitle() == null) return false;
    if(!"Some".equals(someSchema.getTitle()) || !"None".equals(noneSchema.getTitle())) return false;
    if(someSchema.getFields() == null || someSchema.getFields().size() != 1) return false;
    if(noneSchema.getFields() != null && noneSchema.getFields().size() != 0) return false;
    return true;
}
```

## **4. Duplicate Code and Inconsistent Naming**

### **Problem:**
- Similar logic repeated across `DataTypeProcessUtil` methods
- Inconsistent parameter naming (`javaDoc`, `alternativeName`, `ns`)
- Package name calculation duplicated in multiple classes

## **5. Tight Coupling**

### **Problem:**
```java
// FieldSpecProcessor.java:32 - Circular dependency
this.dataTypeProcessUtil = new DataTypeProcessUtil(this, annotation, processingEnv);

// DataTypeProcessUtil.java:219 - Calling back to FieldSpecProcessor
specs.add(fieldSpecProcessor.createDatumFieldSpec(ns, "", field, field.getTitle())._1);
```

## **6. Poor Separation of Concerns**

### **Current Responsibilities Mixed:**
- Schema validation
- Type detection (Option, Pair, Enum)
- Code generation
- File I/O operations
- Package name resolution
- JavaPoet template creation

---

## **Proposed Refactoring Solution**

### **Architecture Overview**

```
┌─────────────────────────────────────────────────────────────────┐
│                    BlueprintAnnotationProcessor                 │
│                      (Orchestrator)                            │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      v
┌─────────────────────────────────────────────────────────────────┐
│                   CodeGenerationOrchestrator                   │
│                    (Strategy Coordinator)                      │
└─────────────────────┬───────────────────────────────────────────┘
                      │
        ┌─────────────┼─────────────┐
        v             v             v
┌─────────────┐ ┌─────────────┐ ┌─────────────┐
│ Validator   │ │   Schema    │ │   Type      │
│ Generator   │ │ Analyzer    │ │ Registry    │
└─────────────┘ └─────────────┘ └─────────────┘
        │             │             │
        v             v             v
┌─────────────┐ ┌─────────────┐ ┌─────────────┐
│   Code      │ │ Validation  │ │   Data      │
│ Templates   │ │   Rules     │ │ Type        │
│             │ │             │ │ Handlers    │
└─────────────┘ └─────────────┘ └─────────────┘
```

### **1. Strategy Pattern for Data Type Processing**

```java
// Base interface for all data type processors
public interface DataTypeProcessor {
    boolean canProcess(BlueprintDatatype dataType);
    FieldSpec processDataType(ProcessingContext context);
    int getPriority(); // For ordering processors
}

// Context object to eliminate parameter passing
public class ProcessingContext {
    private final String namespace;
    private final String javaDoc;
    private final BlueprintSchema schema;
    private final String alternativeName;
    private final CodeGenerationConfig config;
    private final TypeRegistry typeRegistry;
    
    // Builder pattern for flexibility
    public static ProcessingContextBuilder builder() { ... }
}

// Concrete implementations
public class BytesDataTypeProcessor implements DataTypeProcessor {
    @Override
    public boolean canProcess(BlueprintDatatype dataType) {
        return dataType == BlueprintDatatype.bytes;
    }
    
    @Override
    public FieldSpec processDataType(ProcessingContext context) {
        return FieldSpec.builder(byte[].class, context.getFieldName())
                .addModifiers(Modifier.PRIVATE)
                .addJavadoc(context.getJavaDoc())
                .build();
    }
    
    @Override
    public int getPriority() { return 100; }
}

// Registry to manage all processors
@Component
public class DataTypeProcessorRegistry {
    private final List<DataTypeProcessor> processors;
    
    public DataTypeProcessorRegistry(List<DataTypeProcessor> processors) {
        this.processors = processors.stream()
            .sorted(Comparator.comparing(DataTypeProcessor::getPriority))
            .collect(Collectors.toList());
    }
    
    public FieldSpec processDataType(ProcessingContext context) {
        return processors.stream()
            .filter(p -> p.canProcess(context.getSchema().getDataType()))
            .findFirst()
            .orElseThrow(() -> new UnsupportedDataTypeException(context.getSchema().getDataType()))
            .processDataType(context);
    }
}
```

### **2. Command Pattern for Schema Operations**

```java
// Command interface for schema operations
public interface SchemaCommand {
    boolean canExecute(BlueprintSchema schema);
    CommandResult execute(SchemaProcessingContext context);
}

// Context for schema commands
public class SchemaProcessingContext {
    private final BlueprintSchema schema;
    private final String namespace;
    private final CodeGenerationConfig config;
    private final JavaFileWriter fileWriter;
    
    // ... getters and builder
}

// Concrete commands
public class CreateEnumCommand implements SchemaCommand {
    @Override
    public boolean canExecute(BlueprintSchema schema) {
        return SchemaAnalyzer.isEnumSchema(schema);
    }
    
    @Override
    public CommandResult execute(SchemaProcessingContext context) {
        TypeSpec enumSpec = EnumGenerator.generateEnum(context.getSchema());
        context.getFileWriter().writeJavaFile(enumSpec);
        return CommandResult.success("Enum created: " + context.getSchema().getTitle());
    }
}

public class CreateInterfaceCommand implements SchemaCommand {
    @Override
    public boolean canExecute(BlueprintSchema schema) {
        return SchemaAnalyzer.requiresInterface(schema);
    }
    
    @Override
    public CommandResult execute(SchemaProcessingContext context) {
        TypeSpec interfaceSpec = InterfaceGenerator.generateInterface(context.getSchema());
        context.getFileWriter().writeJavaFile(interfaceSpec);
        return CommandResult.success("Interface created: " + context.getSchema().getTitle());
    }
}
```

### **3. Factory Pattern for Code Generators**

```java
// Abstract factory for different generators
public abstract class CodeGeneratorFactory {
    public abstract ValidatorGenerator createValidatorGenerator();
    public abstract DatumGenerator createDatumGenerator();
    public abstract ConverterGenerator createConverterGenerator();
    
    // Static factory method
    public static CodeGeneratorFactory getInstance(CodeGenerationConfig config) {
        switch (config.getGenerationMode()) {
            case STANDARD: return new StandardCodeGeneratorFactory();
            case OPTIMIZED: return new OptimizedCodeGeneratorFactory();
            case CUSTOM: return new CustomCodeGeneratorFactory(config);
            default: throw new IllegalArgumentException("Unknown generation mode");
        }
    }
}

// Template Method pattern for consistent generation
public abstract class BaseGenerator {
    public final TypeSpec generate(ProcessingContext context) {
        validateInput(context);
        TypeSpec.Builder builder = createBuilder(context);
        addFields(builder, context);
        addMethods(builder, context);
        addAnnotations(builder, context);
        return builder.build();
    }
    
    protected abstract TypeSpec.Builder createBuilder(ProcessingContext context);
    protected abstract void addFields(TypeSpec.Builder builder, ProcessingContext context);
    protected abstract void addMethods(TypeSpec.Builder builder, ProcessingContext context);
    // ... other template methods
}
```

### **4. Visitor Pattern for Schema Analysis**

```java
// Visitor interface for schema traversal
public interface SchemaVisitor<T> {
    T visitConstructor(ConstructorSchema schema);
    T visitList(ListSchema schema);
    T visitMap(MapSchema schema);
    T visitOption(OptionSchema schema);
    T visitPair(PairSchema schema);
    T visitPrimitive(PrimitiveSchema schema);
}

// Schema analysis visitor
public class SchemaAnalysisVisitor implements SchemaVisitor<SchemaInfo> {
    @Override
    public SchemaInfo visitConstructor(ConstructorSchema schema) {
        return SchemaInfo.builder()
            .type(SchemaType.CONSTRUCTOR)
            .complexity(calculateComplexity(schema))
            .requiredImports(collectImports(schema))
            .build();
    }
    
    // ... other visit methods
}

// Usage
public class SchemaAnalyzer {
    public static boolean isEnumSchema(BlueprintSchema schema) {
        SchemaAnalysisVisitor visitor = new SchemaAnalysisVisitor();
        SchemaInfo info = schema.accept(visitor);
        return info.getType() == SchemaType.ENUM;
    }
}
```

### **5. Builder Pattern for Configuration**

```java
// Immutable configuration object
@Value
@Builder
public class CodeGenerationConfig {
    private final String packageName;
    private final NamingStrategy namingStrategy;
    private final boolean generateBuilders;
    private final boolean generateEqualsHashCode;
    private final List<String> customAnnotations;
    private final AccessModifier defaultFieldAccess;
    
    public static CodeGenerationConfigBuilder defaultConfig() {
        return CodeGenerationConfig.builder()
            .namingStrategy(NamingStrategy.CAMEL_CASE)
            .generateBuilders(false)
            .generateEqualsHashCode(false)
            .defaultFieldAccess(AccessModifier.PRIVATE);
    }
}

// Usage in annotation processor
CodeGenerationConfig config = CodeGenerationConfig.defaultConfig()
    .packageName(annotation.packageName())
    .build();
```

### **6. Chain of Responsibility for Validation**

```java
// Validation chain
public abstract class ValidationRule {
    private ValidationRule next;
    
    public ValidationRule setNext(ValidationRule next) {
        this.next = next;
        return next;
    }
    
    public final ValidationResult validate(BlueprintSchema schema) {
        ValidationResult result = doValidate(schema);
        if (result.isValid() && next != null) {
            result = result.merge(next.validate(schema));
        }
        return result;
    }
    
    protected abstract ValidationResult doValidate(BlueprintSchema schema);
}

// Concrete validation rules
public class RequiredFieldsValidationRule extends ValidationRule {
    @Override
    protected ValidationResult doValidate(BlueprintSchema schema) {
        if (schema.getTitle() == null || schema.getTitle().isEmpty()) {
            return ValidationResult.error("Schema title is required");
        }
        return ValidationResult.success();
    }
}

// Usage
ValidationRule validationChain = new RequiredFieldsValidationRule()
    .setNext(new DataTypeValidationRule())
    .setNext(new ReferenceValidationRule())
    .setNext(new ComplexityValidationRule());

ValidationResult result = validationChain.validate(schema);
```

### **7. New Package Structure**

```
com.bloxbean.cardano.client.plutus.annotation.processor.blueprint/
├── BlueprintAnnotationProcessor.java           (Simplified orchestrator)
├── orchestrator/
│   ├── CodeGenerationOrchestrator.java
│   └── SchemaProcessingOrchestrator.java
├── generator/
│   ├── factory/
│   │   ├── CodeGeneratorFactory.java
│   │   ├── StandardCodeGeneratorFactory.java
│   │   └── OptimizedCodeGeneratorFactory.java
│   ├── validator/
│   │   ├── ValidatorGenerator.java
│   │   └── ValidatorTemplateEngine.java
│   ├── datum/
│   │   ├── DatumGenerator.java
│   │   ├── DatumClassGenerator.java
│   │   └── DatumInterfaceGenerator.java
│   └── converter/
│       ├── ConverterGenerator.java
│       └── ConverterTemplateEngine.java
├── processor/
│   ├── registry/
│   │   ├── DataTypeProcessorRegistry.java
│   │   └── ProcessorConfiguration.java
│   ├── impl/
│   │   ├── BytesDataTypeProcessor.java
│   │   ├── IntegerDataTypeProcessor.java
│   │   ├── ListDataTypeProcessor.java
│   │   ├── MapDataTypeProcessor.java
│   │   ├── ConstructorDataTypeProcessor.java
│   │   └── OptionDataTypeProcessor.java
│   └── context/
│       ├── ProcessingContext.java
│       └── ProcessingContextBuilder.java
├── command/
│   ├── SchemaCommand.java
│   ├── CreateEnumCommand.java
│   ├── CreateInterfaceCommand.java
│   ├── CreateClassCommand.java
│   └── CommandExecutor.java
├── analyzer/
│   ├── SchemaAnalyzer.java
│   ├── SchemaVisitor.java
│   ├── SchemaAnalysisVisitor.java
│   └── SchemaType.java
├── validation/
│   ├── ValidationRule.java
│   ├── ValidationResult.java
│   ├── impl/
│   │   ├── RequiredFieldsValidationRule.java
│   │   ├── DataTypeValidationRule.java
│   │   └── ReferenceValidationRule.java
│   └── ValidationChainBuilder.java
├── config/
│   ├── CodeGenerationConfig.java
│   ├── NamingStrategy.java
│   └── AccessModifier.java
├── template/
│   ├── TemplateEngine.java
│   ├── JavaPoetTemplateEngine.java
│   └── templates/
│       ├── ValidatorTemplate.java
│       ├── DatumTemplate.java
│       └── ConverterTemplate.java
├── util/
│   ├── JavaFileWriter.java
│   ├── PackageNameResolver.java
│   ├── TypeNameResolver.java
│   └── BlueprintUtil.java               (Simplified)
└── exception/
    ├── BlueprintProcessingException.java
    ├── UnsupportedDataTypeException.java
    └── CodeGenerationException.java
```

### **8. Simplified Main Processor**

```java
@AutoService(Processor.class)
public class BlueprintAnnotationProcessor extends AbstractProcessor {
    
    private CodeGenerationOrchestrator orchestrator;
    
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.orchestrator = new CodeGenerationOrchestrator(processingEnv);
    }
    
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            for (TypeElement typeElement : getTypeElementsWithAnnotations(annotations, roundEnv)) {
                processBlueprint(typeElement);
            }
            return true;
        } catch (Exception e) {
            error("Blueprint processing failed: " + e.getMessage());
            return false;
        }
    }
    
    private void processBlueprint(TypeElement typeElement) {
        Blueprint annotation = typeElement.getAnnotation(Blueprint.class);
        ExtendWith[] extendWiths = typeElement.getAnnotationsByType(ExtendWith.class);
        
        BlueprintProcessingRequest request = BlueprintProcessingRequest.builder()
            .typeElement(typeElement)
            .blueprintAnnotation(annotation)
            .extendWithAnnotations(extendWiths)
            .build();
            
        orchestrator.processBlueprint(request);
    }
}
```

## **Benefits of This Refactoring**

### **1. Maintainability Improvements**
- **Single Responsibility Principle**: Each class has one clear purpose
- **Open/Closed Principle**: Easy to add new data types without modifying existing code
- **Dependency Inversion**: High-level modules don't depend on low-level modules

### **2. Extensibility**
- New data types can be added by implementing `DataTypeProcessor`
- New generators can be added through factory pattern
- New validation rules can be chained easily

### **3. Testability**
- Each component can be unit tested independently
- Mock objects can be easily injected
- Strategy pattern allows testing individual processors

### **4. Readability**
- Clear separation of concerns
- Self-documenting code structure
- Consistent naming conventions

### **5. Performance**
- Registry pattern eliminates switch statements
- Lazy loading of processors
- Caching opportunities in template engine

## **Migration Strategy**

### **Phase 1: Foundation (2 weeks)**
1. Create new package structure
2. Implement base interfaces and abstractions
3. Create configuration system

### **Phase 2: Core Refactoring (3 weeks)**
1. Implement Strategy pattern for data type processors
2. Create Command pattern for schema operations
3. Implement Factory pattern for generators

### **Phase 3: Advanced Features (2 weeks)**
1. Add Visitor pattern for schema analysis
2. Implement validation chain
3. Create template engine

### **Phase 4: Migration and Testing (1 week)**
1. Migrate existing functionality
2. Comprehensive testing
3. Performance optimization

## **Backward Compatibility**

- All existing `@Blueprint` annotations will continue to work
- Generated code structure remains the same
- Default behavior unchanged
- New features are opt-in through configuration

## **Example Usage After Refactoring**

```java
// Simple usage (same as before)
@Blueprint(fileInResources = "plutus.json", packageName = "com.example")
public interface MyBlueprint {}

// Advanced configuration
@Blueprint(
    fileInResources = "plutus.json",
    packageName = "com.example",
    config = @CodeGenConfig(
        generateBuilders = true,
        namingStrategy = NamingStrategy.SNAKE_CASE,
        customProcessors = {CustomDataTypeProcessor.class}
    )
)
public interface AdvancedBlueprint {}
```

This refactoring transforms the complex, tightly-coupled code into a **maintainable, extensible, and testable** architecture that follows **solid design principles** and **proven design patterns**.