package com.bloxbean.cardano.client.plutus.blueprint.registry;

import java.util.Objects;

/**
 * Describes an annotation that a {@link BlueprintTypeRegistry} wants the annotation processor
 * to read from the {@code @Blueprint}-annotated type element.
 *
 * <p>The processor scans annotation mirrors generically using these descriptors,
 * so no framework-specific annotation knowledge lives in the processor itself.</p>
 */
public final class AnnotationHintDescriptor {

    private final String annotationFqn;
    private final String elementName;
    private final String hintKey;
    private final String defaultValue;

    /**
     * @param annotationFqn fully-qualified name of the annotation (e.g. {@code "com.…AikenStdlib"})
     * @param elementName   annotation element to read (e.g. {@code "value"})
     * @param hintKey       key under which the resolved value is placed in {@link LookupContext#hints()}
     * @param defaultValue  value to use when the annotation is absent
     */
    public AnnotationHintDescriptor(String annotationFqn, String elementName,
                                    String hintKey, String defaultValue) {
        this.annotationFqn = Objects.requireNonNull(annotationFqn, "annotationFqn");
        this.elementName = Objects.requireNonNull(elementName, "elementName");
        this.hintKey = Objects.requireNonNull(hintKey, "hintKey");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
    }

    public String annotationFqn() { return annotationFqn; }
    public String elementName()   { return elementName; }
    public String hintKey()       { return hintKey; }
    public String defaultValue()  { return defaultValue; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnnotationHintDescriptor)) return false;
        AnnotationHintDescriptor that = (AnnotationHintDescriptor) o;
        return annotationFqn.equals(that.annotationFqn)
                && elementName.equals(that.elementName)
                && hintKey.equals(that.hintKey)
                && defaultValue.equals(that.defaultValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(annotationFqn, elementName, hintKey, defaultValue);
    }

    @Override
    public String toString() {
        return "AnnotationHintDescriptor{" +
                "annotationFqn='" + annotationFqn + '\'' +
                ", elementName='" + elementName + '\'' +
                ", hintKey='" + hintKey + '\'' +
                ", defaultValue='" + defaultValue + '\'' +
                '}';
    }
}
