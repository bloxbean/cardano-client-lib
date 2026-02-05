package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.processor.ConstrAnnotationProcessor;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

class BlueprintAnnotationProcessorGenerationTest {

    @Test
    void blueprintProcessorShouldGenerateExpectedValidatorArtifacts() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "com.test.multiple.MultipleValidatorsBlueprint",
                "package com.test.multiple;\n" +
                        "import com.bloxbean.cardano.client.plutus.annotation.Blueprint;\n" +
                        "import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;\n" +
                        "import com.bloxbean.cardano.client.quicktx.blueprint.extender.LockUnlockValidatorExtender;\n" +
                        "@Blueprint(fileInResources = \"blueprint/multiple_validators_aiken_v1_0_29_alpha_16fb02e.json\", packageName = \"com.test.multiple\")\n" +
                        "@ExtendWith(LockUnlockValidatorExtender.class)\n" +
                        "public interface MultipleValidatorsBlueprint { }\n");

        Compilation compilation = Compiler.javac()
                .withProcessors(new BlueprintAnnotationProcessor(), new ConstrAnnotationProcessor())
                .withClasspathFrom(ClassLoader.getSystemClassLoader())
                .compile(source);

        assertThat(compilation).succeeded();

        JavaFileObject validatorFile = compilation.generatedSourceFile("com.test.multiple.demo.HelloWorldValidator").orElseThrow();
        String validatorSource = validatorFile.getCharContent(true).toString();
        assertThat(validatorSource).contains("package com.test.multiple.demo;");
        assertThat(validatorSource).contains("public class HelloWorldValidator");
        assertThat(validatorSource).contains("public static final String COMPILED_CODE");
        assertThat(validatorSource).contains("private String scriptAddress;");

        JavaFileObject datumFile = compilation.generatedSourceFile("com.test.multiple.demo.model.MyDatum").orElseThrow();
        String datumSource = datumFile.getCharContent(true).toString();
        assertThat(datumSource).contains("package com.test.multiple.demo.model;");
        assertThat(datumSource).contains("public abstract class MyDatum");
        assertThat(datumSource).contains("private byte[] owner;");
        assertThat(datumSource).contains("@Constr");

        String expectedSnapshot = readResource("/snapshots/HelloWorldValidator.java");
        assertThat(normalize(validatorSource)).isEqualTo(normalize(expectedSnapshot));
    }

    private String readResource(String path) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Resource not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String normalize(String value) {
        return value.replace("\r\n", "\n").trim();
    }
}
