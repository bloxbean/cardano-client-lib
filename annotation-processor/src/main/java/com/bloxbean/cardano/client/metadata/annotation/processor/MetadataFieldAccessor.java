package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.squareup.javapoet.MethodSpec;

import java.util.Optional;

/**
 * Consolidates the accessor patterns (setter / direct-field-assignment) used
 * throughout generated metadata converter code.
 */
public class MetadataFieldAccessor {

    /**
     * Emits {@code obj.setX(valueFmt)} or {@code obj.x = valueFmt} where
     * {@code valueFmt} contains a single {@code $T} placeholder resolved to {@code typeArg}.
     */
    public void emitSet(MethodSpec.Builder builder, MetadataFieldInfo field,
                        String valueFmtWithOneT, Object typeArg) {
        if (field.getSetterName() != null) {
            builder.addStatement("obj.$L(" + valueFmtWithOneT + ")", field.getSetterName(), typeArg);
        } else {
            builder.addStatement("obj.$L = " + valueFmtWithOneT, field.getJavaFieldName(), typeArg);
        }
    }

    /**
     * Emits {@code obj.setX(valueExpr)} or {@code obj.x = valueExpr} with a raw
     * expression (no {@code $T} placeholders).
     */
    public void emitSetRaw(MethodSpec.Builder builder, MetadataFieldInfo field,
                           String valueExpr) {
        if (field.getSetterName() != null) {
            builder.addStatement("obj.$L(" + valueExpr + ")", field.getSetterName());
        } else {
            builder.addStatement("obj.$L = " + valueExpr, field.getJavaFieldName());
        }
    }

    /**
     * Emits {@code obj.setX(Optional.of(innerFmt))} where {@code innerFmt} contains
     * a single {@code $T} placeholder.
     */
    public void emitOptionalOfSet(MethodSpec.Builder builder, MetadataFieldInfo field,
                                  String innerFmtWithOneT, Object typeArg) {
        if (field.getSetterName() != null) {
            builder.addStatement("obj.$L($T.of(" + innerFmtWithOneT + "))",
                    field.getSetterName(), Optional.class, typeArg);
        } else {
            builder.addStatement("obj.$L = $T.of(" + innerFmtWithOneT + ")",
                    field.getJavaFieldName(), Optional.class, typeArg);
        }
    }

    /**
     * Emits {@code obj.setX(Optional.of(innerExpr))} with a raw inner expression
     * (no {@code $T} placeholders).
     */
    public void emitOptionalOfSetRaw(MethodSpec.Builder builder, MetadataFieldInfo field,
                                     String innerExpr) {
        if (field.getSetterName() != null) {
            builder.addStatement("obj.$L($T.of(" + innerExpr + "))",
                    field.getSetterName(), Optional.class);
        } else {
            builder.addStatement("obj.$L = $T.of(" + innerExpr + ")",
                    field.getJavaFieldName(), Optional.class);
        }
    }

    /**
     * Emits {@code obj.setX(Optional.empty())}.
     */
    public void emitOptionalEmpty(MethodSpec.Builder builder, MetadataFieldInfo field) {
        if (field.getSetterName() != null) {
            builder.addStatement("obj.$L($T.empty())", field.getSetterName(), Optional.class);
        } else {
            builder.addStatement("obj.$L = $T.empty()", field.getJavaFieldName(), Optional.class);
        }
    }

    /**
     * Emits a setter or assignment with an arbitrary format string containing
     * the given args (varargs for multiple $T etc.).
     */
    public void emitSetFmt(MethodSpec.Builder builder, MetadataFieldInfo field,
                           String valueFmt, Object... args) {
        if (field.getSetterName() != null) {
            Object[] allArgs = new Object[args.length + 1];
            allArgs[0] = field.getSetterName();
            System.arraycopy(args, 0, allArgs, 1, args.length);
            builder.addStatement("obj.$L(" + valueFmt + ")", allArgs);
        } else {
            Object[] allArgs = new Object[args.length + 1];
            allArgs[0] = field.getJavaFieldName();
            System.arraycopy(args, 0, allArgs, 1, args.length);
            builder.addStatement("obj.$L = " + valueFmt, allArgs);
        }
    }

    /**
     * Emits a setter or assignment for Optional.of with an arbitrary format string.
     */
    public void emitOptionalOfSetFmt(MethodSpec.Builder builder, MetadataFieldInfo field,
                                     String innerFmt, Object... args) {
        if (field.getSetterName() != null) {
            Object[] allArgs = new Object[args.length + 2];
            allArgs[0] = field.getSetterName();
            allArgs[1] = Optional.class;
            System.arraycopy(args, 0, allArgs, 2, args.length);
            builder.addStatement("obj.$L($T.of(" + innerFmt + "))", allArgs);
        } else {
            Object[] allArgs = new Object[args.length + 2];
            allArgs[0] = field.getJavaFieldName();
            allArgs[1] = Optional.class;
            System.arraycopy(args, 0, allArgs, 2, args.length);
            builder.addStatement("obj.$L = $T.of(" + innerFmt + ")", allArgs);
        }
    }
}
