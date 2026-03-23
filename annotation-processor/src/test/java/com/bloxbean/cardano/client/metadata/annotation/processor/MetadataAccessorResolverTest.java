package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.util.*;
import java.util.function.Consumer;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MetadataAccessorResolver}.
 * Uses compile-testing to get a real {@code ProcessingEnvironment}.
 */
class MetadataAccessorResolverTest {

    // ── Test harness ────────────────────────────────────────────────────

    record AccessorTestResult(Compilation compilation, MetadataAccessorResolver.AccessorResult accessorResult,
                               List<Diagnostic.Kind> diagnosticKinds) {}

    private void resolveAndAssert(Consumer<AccessorTestResult> assertions, String source) {
        resolveAndAssert(assertions, false, JavaFileObjects.forSourceString("com.test.TestClass", source));
    }

    private void resolveAndAssert(Consumer<AccessorTestResult> assertions, boolean simulateLombok, String source) {
        resolveAndAssert(assertions, simulateLombok, JavaFileObjects.forSourceString("com.test.TestClass", source));
    }

    private void resolveAndAssert(Consumer<AccessorTestResult> assertions, boolean simulateLombok, JavaFileObject... sources) {
        AccessorCapturingProcessor processor = new AccessorCapturingProcessor(simulateLombok);
        Compilation compilation = javac()
                .withProcessors(processor)
                .compile(sources);
        List<Diagnostic.Kind> kinds = compilation.diagnostics().stream()
                .map(d -> d.getKind())
                .toList();
        assertions.accept(new AccessorTestResult(compilation, processor.result, kinds));
    }

    @SupportedAnnotationTypes("com.bloxbean.cardano.client.metadata.annotation.MetadataType")
    @SupportedSourceVersion(SourceVersion.RELEASE_17)
    static class AccessorCapturingProcessor extends AbstractProcessor {
        MetadataAccessorResolver.AccessorResult result;
        final boolean simulateLombok;

        AccessorCapturingProcessor(boolean simulateLombok) {
            this.simulateLombok = simulateLombok;
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            for (TypeElement annotation : annotations) {
                for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                    if (!(element instanceof TypeElement typeElement)) continue;
                    MetadataAccessorResolver resolver = new MetadataAccessorResolver(processingEnv, processingEnv.getMessager());

                    for (Element enclosed : typeElement.getEnclosedElements()) {
                        if (!(enclosed instanceof VariableElement ve)) continue;
                        if (enclosed.getKind() != ElementKind.FIELD) continue;
                        if (ve.getSimpleName().toString().startsWith("$")) continue;

                        result = resolver.resolveAccessors(typeElement, ve,
                                ve.getSimpleName().toString(), simulateLombok);
                        return true;
                    }
                }
            }
            return true;
        }
    }

    // ── Standard getters/setters ────────────────────────────────────────

    @Nested
    class StandardAccessors {

        @Test
        void findsGetterAndSetter() {
            resolveAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.accessorResult).isNotNull();
                assertThat(r.accessorResult.getterName()).isEqualTo("getName");
                assertThat(r.accessorResult.setterName()).isEqualTo("setName");
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                @MetadataType
                public class TestClass {
                    private String name;
                    public String getName() { return name; }
                    public void setName(String name) { this.name = name; }
                }
            """);
        }

        @Test
        void findsBooleanIsGetter() {
            resolveAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.accessorResult).isNotNull();
                assertThat(r.accessorResult.getterName()).isEqualTo("isActive");
                assertThat(r.accessorResult.setterName()).isEqualTo("setActive");
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                @MetadataType
                public class TestClass {
                    private boolean active;
                    public boolean isActive() { return active; }
                    public void setActive(boolean active) { this.active = active; }
                }
            """);
        }
    }

    // ── Public field access ─────────────────────────────────────────────

    @Nested
    class DirectAccess {

        @Test
        void publicFieldNeedsNoAccessors() {
            resolveAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.accessorResult).isNotNull();
                assertThat(r.accessorResult.getterName()).isNull();
                assertThat(r.accessorResult.setterName()).isNull();
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                @MetadataType
                public class TestClass {
                    public String name;
                }
            """);
        }
    }

    // ── Lombok simulation ───────────────────────────────────────────────

    @Nested
    class LombokAccessors {

        @Test
        void lombokGeneratesConventionalNames() {
            resolveAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.accessorResult).isNotNull();
                assertThat(r.accessorResult.getterName()).isEqualTo("getName");
                assertThat(r.accessorResult.setterName()).isEqualTo("setName");
            }, true, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                @MetadataType
                public class TestClass {
                    private String name;
                }
            """);
        }
    }

    // ── Missing accessors ───────────────────────────────────────────────

    @Nested
    class MissingAccessors {

        @Test
        void privateFieldWithoutGetterReturnsNull() {
            resolveAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.accessorResult).isNull();
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                @MetadataType
                public class TestClass {
                    private String secret;
                }
            """);
        }

        @Test
        void privateFieldWithGetterButNoSetterReturnsNull() {
            resolveAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.accessorResult).isNull();
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                @MetadataType
                public class TestClass {
                    private String readOnly;
                    public String getReadOnly() { return readOnly; }
                }
            """);
        }
    }
}
