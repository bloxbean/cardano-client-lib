package com.bloxbean.cardano.client.metadata.annotation.processor;

import lombok.experimental.UtilityClass;

/**
 * Shared constants for the metadata annotation processor.
 * <p>
 * Fully-qualified type name strings used for type detection, code generation,
 * and switch expressions throughout the processor pipeline.
 */
@UtilityClass
public class MetadataConstants {

    // ── Collections & containers ─────────────────────────────────────────
    public static final String COLLECTION_LIST       = "java.util.List";
    public static final String COLLECTION_SET        = "java.util.Set";
    public static final String COLLECTION_SORTED_SET = "java.util.SortedSet";
    public static final String MAP                   = "java.util.Map";
    public static final String OPTIONAL              = "java.util.Optional";

    // ── Primitives ────────────────────────────────────────────────────────
    public static final String PRIM_BOOLEAN = "boolean";
    public static final String PRIM_BYTE    = "byte";
    public static final String PRIM_SHORT   = "short";
    public static final String PRIM_INT     = "int";
    public static final String PRIM_LONG    = "long";
    public static final String PRIM_FLOAT   = "float";
    public static final String PRIM_DOUBLE  = "double";
    public static final String PRIM_CHAR    = "char";
    public static final String BYTE_ARRAY   = "byte[]";

    // ── Core types ────────────────────────────────────────────────────────
    public static final String OBJECT = "java.lang.Object";

    // ── Primitive wrappers ───────────────────────────────────────────────
    public static final String BOOLEAN   = "java.lang.Boolean";
    public static final String BYTE      = "java.lang.Byte";
    public static final String SHORT     = "java.lang.Short";
    public static final String INTEGER   = "java.lang.Integer";
    public static final String LONG      = "java.lang.Long";
    public static final String FLOAT     = "java.lang.Float";
    public static final String DOUBLE    = "java.lang.Double";
    public static final String CHARACTER = "java.lang.Character";
    public static final String STRING    = "java.lang.String";

    // ── Math types ───────────────────────────────────────────────────────
    public static final String BIG_INTEGER = "java.math.BigInteger";
    public static final String BIG_DECIMAL = "java.math.BigDecimal";

    // ── Net types ────────────────────────────────────────────────────────
    public static final String URI = "java.net.URI";
    public static final String URL = "java.net.URL";

    // ── Util types ───────────────────────────────────────────────────────
    public static final String UUID     = "java.util.UUID";
    public static final String CURRENCY = "java.util.Currency";
    public static final String LOCALE   = "java.util.Locale";
    public static final String DATE     = "java.util.Date";

    // ── Time types ───────────────────────────────────────────────────────
    public static final String INSTANT        = "java.time.Instant";
    public static final String LOCAL_DATE     = "java.time.LocalDate";
    public static final String LOCAL_DATETIME = "java.time.LocalDateTime";
}
