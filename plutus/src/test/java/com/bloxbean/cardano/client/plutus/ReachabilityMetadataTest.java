package com.bloxbean.cardano.client.plutus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the GraalVM (Native Image / Web Image) reachability metadata shipped in this module's jar. Every class of the
 * Jackson-bound packages must be registered with its constructors, fields and bindable methods (bean accessors and
 * Jackson-annotated methods). Without an entry, the class would only fail at run time in a native or WebAssembly image.
 */
class ReachabilityMetadataTest {

    private static final String METADATA =
            "/META-INF/native-image/com.bloxbean.cardano/cardano-client-plutus/reachability-metadata.json";
    /** Packages (including sub-packages) whose classes Jackson binds reflectively. */
    private static final List<String> RECURSIVE_PACKAGES = List.of("com.bloxbean.cardano.client.plutus.spec");
    /** Packages (without sub-packages) whose classes Jackson binds reflectively. */
    private static final List<String> PACKAGES = List.of("com.bloxbean.cardano.client.plutus.blueprint.model");
    private static final Pattern NOT_BOUND = Pattern.compile("\\$(\\d+|[A-Za-z0-9]*Builder(Impl)?)$|package-info$");

    @Test
    void registersJacksonBoundTypesWithTheirBindableMethods() throws Exception {
        Map<String, JsonNode> entries = new TreeMap<>();
        for (JsonNode entry : metadata().get("reflection")) {
            entries.put(entry.get("type").asText(), entry);
        }
        Set<String> types = new TreeSet<>(entries.keySet());
        for (String pkg : RECURSIVE_PACKAGES) types.addAll(mainClasses(pkg, true));
        for (String pkg : PACKAGES) types.addAll(mainClasses(pkg, false));

        List<String> problems = new ArrayList<>();
        for (String type : types) {
            Class<?> clazz;
            try {
                clazz = load(type);
            } catch (ClassNotFoundException e) {
                problems.add("registered type does not exist: " + type);
                continue;
            }
            JsonNode entry = entries.get(type);
            if (entry == null) {
                problems.add("missing entry for " + type + " with methods " + bindableMethods(clazz));
                continue;
            }
            String condition = entry.path("condition").path("typeReached").asText();
            try {
                load(condition);
            } catch (ClassNotFoundException e) {
                problems.add(type + ": typeReached condition class does not exist: " + condition);
            }
            if (!entry.path("allDeclaredConstructors").asBoolean() || !entry.path("allDeclaredFields").asBoolean()) {
                problems.add(type + ": allDeclaredConstructors and allDeclaredFields must be true");
            }
            Set<String> listed = new TreeSet<>();
            for (JsonNode method : entry.path("methods")) {
                List<String> params = new ArrayList<>();
                method.path("parameterTypes").forEach(p -> params.add(p.asText()));
                listed.add(method.get("name").asText() + "(" + String.join(",", params) + ")");
            }
            Set<String> missing = new TreeSet<>(bindableMethods(clazz));
            missing.removeAll(listed);
            if (!missing.isEmpty()) problems.add(type + ": missing methods " + missing);
        }
        assertThat(problems).as("reachability metadata %s is out of date", METADATA).isEmpty();
    }

    /** Methods Jackson may invoke: public bean accessors declared by CCL types, and Jackson-annotated methods. */
    private static Set<String> bindableMethods(Class<?> clazz) {
        Set<String> methods = new TreeSet<>();
        for (Method m : clazz.getMethods()) {
            if (m.isSynthetic() || m.isBridge() || !m.getDeclaringClass().getName().startsWith("com.bloxbean.cardano")) continue;
            boolean accessor = (m.getName().startsWith("get") && m.getParameterCount() == 0)
                    || (m.getName().startsWith("is") && m.getParameterCount() == 0)
                    || (m.getName().startsWith("set") && m.getParameterCount() == 1);
            if (accessor || jacksonAnnotated(m)) methods.add(signature(m));
        }
        for (Method m : clazz.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers()) && !m.isSynthetic() && jacksonAnnotated(m)) methods.add(signature(m));
        }
        return methods;
    }

    private static boolean jacksonAnnotated(Method method) {
        return Arrays.stream(method.getAnnotations()).map(Annotation::annotationType)
                .anyMatch(a -> a.getName().startsWith("com.fasterxml.jackson"));
    }

    private static String signature(Method method) {
        return method.getName() + "(" + Arrays.stream(method.getParameterTypes()).map(Class::getTypeName)
                .collect(Collectors.joining(",")) + ")";
    }

    private Class<?> load(String type) throws ClassNotFoundException {
        return Class.forName(type, false, getClass().getClassLoader());
    }

    private JsonNode metadata() throws IOException {
        try (InputStream in = getClass().getResourceAsStream(METADATA)) {
            assertThat(in).as(METADATA).isNotNull();
            return new ObjectMapper().readTree(in);
        }
    }

    /** Classes of {@code pkg} from this module's main output directory. */
    private Set<String> mainClasses(String pkg, boolean recursive) throws IOException, URISyntaxException {
        String path = pkg.replace('.', '/');
        Set<String> classes = new TreeSet<>();
        for (URL url : Collections.list(getClass().getClassLoader().getResources(path))) {
            if (!"file".equals(url.getProtocol()) || url.getPath().contains("/test/")) continue;
            Path root = Paths.get(url.toURI());
            try (Stream<Path> files = recursive ? Files.walk(root) : Files.list(root)) {
                files.map(Path::toString)
                        .filter(f -> f.endsWith(".class"))
                        .map(f -> pkg + "." + root.relativize(Paths.get(f)).toString()
                                .replace(".class", "").replace('/', '.').replace('\\', '.'))
                        .filter(c -> !NOT_BOUND.matcher(c).find())
                        .forEach(classes::add);
            }
        }
        assertThat(classes).as("classes of %s", pkg).isNotEmpty();
        return classes;
    }
}
