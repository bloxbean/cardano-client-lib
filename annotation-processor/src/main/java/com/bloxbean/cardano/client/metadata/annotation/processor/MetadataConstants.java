package com.bloxbean.cardano.client.metadata.annotation.processor;

/**
 * Shared constants for the metadata annotation processor.
 * <p>
 * Fully-qualified type name strings used for type detection, code generation,
 * and switch expressions throughout the processor pipeline.
 */
public interface MetadataConstants {

    // ── Collections & containers ─────────────────────────────────────────
    String COLLECTION_LIST       = "java.util.List";
    String COLLECTION_SET        = "java.util.Set";
    String COLLECTION_SORTED_SET = "java.util.SortedSet";
    String MAP                   = "java.util.Map";
    String OPTIONAL              = "java.util.Optional";

    // ── Primitives ────────────────────────────────────────────────────────
    String PRIM_BOOLEAN = "boolean";
    String PRIM_BYTE    = "byte";
    String PRIM_SHORT   = "short";
    String PRIM_INT     = "int";
    String PRIM_LONG    = "long";
    String PRIM_FLOAT   = "float";
    String PRIM_DOUBLE  = "double";
    String PRIM_CHAR    = "char";
    String BYTE_ARRAY   = "byte[]";

    // ── Core types ────────────────────────────────────────────────────────
    String OBJECT = "java.lang.Object";

    // ── Primitive wrappers ───────────────────────────────────────────────
    String BOOLEAN   = "java.lang.Boolean";
    String BYTE      = "java.lang.Byte";
    String SHORT     = "java.lang.Short";
    String INTEGER   = "java.lang.Integer";
    String LONG      = "java.lang.Long";
    String FLOAT     = "java.lang.Float";
    String DOUBLE    = "java.lang.Double";
    String CHARACTER = "java.lang.Character";
    String STRING    = "java.lang.String";

    // ── Math types ───────────────────────────────────────────────────────
    String BIG_INTEGER = "java.math.BigInteger";
    String BIG_DECIMAL = "java.math.BigDecimal";

    // ── Net types ────────────────────────────────────────────────────────
    String URI = "java.net.URI";
    String URL = "java.net.URL";

    // ── Util types ───────────────────────────────────────────────────────
    String UUID     = "java.util.UUID";
    String CURRENCY = "java.util.Currency";
    String LOCALE   = "java.util.Locale";
    String DATE     = "java.util.Date";

    // ── Time types ───────────────────────────────────────────────────────
    String INSTANT        = "java.time.Instant";
    String LOCAL_DATE     = "java.time.LocalDate";
    String LOCAL_DATETIME = "java.time.LocalDateTime";
}
