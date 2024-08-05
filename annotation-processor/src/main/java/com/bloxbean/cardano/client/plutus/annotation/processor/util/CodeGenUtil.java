package com.bloxbean.cardano.client.plutus.annotation.processor.util;

import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.bloxbean.cardano.client.plutus.annotation.processor.util.JavaFileUtil.firstUpperCase;

public class CodeGenUtil {

    public static Collection<MethodSpec> createMethodSpecsForGetterSetters(List<FieldSpec> fields, boolean onlyGetter) {
        //Generate getter and setter methods. Also check if Boolean then generate isXXX method
        List<MethodSpec> methods = new ArrayList<>();

        for(FieldSpec field : fields) {
            String fieldName = field.name;

            if(field.type == TypeName.BOOLEAN) {
                String methodName = "is" + firstUpperCase(fieldName);
                MethodSpec isMethod = MethodSpec.methodBuilder(methodName)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(TypeName.BOOLEAN)
                        .addStatement("return this.$N", fieldName)
                        .build();
                methods.add(isMethod);
            } else {
                String methodName = "get" + firstUpperCase(fieldName);
                MethodSpec getter = MethodSpec.methodBuilder(methodName)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(field.type)
                        .addStatement("return this.$N", fieldName)
                        .build();
                methods.add(getter);
            }

            if (!onlyGetter) {
                String methodName = "set" + firstUpperCase(fieldName);
                MethodSpec setter = MethodSpec.methodBuilder(methodName)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(TypeName.VOID)
                        .addParameter(field.type, fieldName)
                        .addStatement("this.$N = $N", fieldName, fieldName)
                        .build();
                methods.add(setter);
            }
        }

        return methods;
    }
}
