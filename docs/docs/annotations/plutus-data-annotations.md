---
description: Plutus Data Annotations
sidebar_label: Plutus Data Annotations
sidebar_position: 2
---

# Overview

This document provides an overview of the Plutus Data Annotations, which can be used to automatically generate serialization and deserialization code 
for custom POJO classes that can be used as Datum and Redeemer in Plutus scripts.

Code generation is performed by an annotation processor during compile time.

# Annotations

- **@Constr**
- **@PlutusIgnore**

### @Constr

This is a class-level annotation used to represent `ConstrPlutusData`. It takes an optional argument `alternative`.

```java
@Constr
public class Person {
    private String name;
    private String publicKey;
    private int age;

    //Getters and Setters
}
```

The annotation processor generates a Converter class for the annotated class with the necessary serialization and deserialization methods.

```java
Person person = new Person();
person.setName("Alice");
person.setPublicKey("6dcf4915b05a1358d86e87d352f2fa7392fa6c092b337af705b577822d06d17e");
person.setAge(25);

//Generated Person Converter class
PersonConverter converter = new PersonConverter();

//Convert to Plutus data
ConstrPlutusData plutusData = converter.toPlutusData(person);

//Convert back to Person
Person deserPerson = converter.deserialize(converter.serializeToHex(person));
```

### @PlutusIgnore

This is a field-level annotation used to exclude a field from serialization and deserialization.
