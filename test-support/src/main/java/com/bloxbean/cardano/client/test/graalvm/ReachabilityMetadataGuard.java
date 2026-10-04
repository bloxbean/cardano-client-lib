package com.bloxbean.cardano.client.test.graalvm;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Checks the GraalVM (Native Image / Web Image) reachability metadata a CCL module ships in
 * {@code META-INF/native-image/com.bloxbean.cardano/<artifactId>/reachability-metadata.json}.
 * <p>
 * The rules follow how GraalVM and Jackson use the metadata:
 * <ul>
 *     <li>every class of the configured Jackson-bound packages, plus any extra types, has an entry, unless it is
 *     excluded as never bound by Jackson</li>
 *     <li>every entry registers all declared constructors and fields, and lists the bindable methods (bean accessors
 *     and Jackson-annotated methods) <em>declared by that type</em>. GraalVM resolves listed methods with
 *     {@code getDeclaredMethod}, so a method inherited from a supertype must be listed on the supertype's entry</li>
 *     <li>every listed method is declared by the type, so a renamed or removed method is reported</li>
 *     <li>every CCL type Jackson can reach from a registered type (supertypes, field and getter types including
 *     generic arguments, {@code @JsonSerialize}/{@code @JsonDeserialize} classes) is registered in some CCL
 *     module's metadata</li>
 * </ul>
 * Tests assert that {@link #problems()} is empty; each problem says what to add or remove.
 */
public final class ReachabilityMetadataGuard {
    private static final String METADATA_DIR = "META-INF/native-image/com.bloxbean.cardano/";
    private static final String METADATA_FILE = "reachability-metadata.json";
    private static final String CCL_PREFIX = "com.bloxbean.cardano.";
    /** Anonymous classes, Lombok builders and package-info are never bound by Jackson. */
    private static final Pattern NOT_BOUND = Pattern.compile("\\$(\\d+|[A-Za-z0-9]*Builder(Impl)?)$|package-info$");

    private final Class<?> anchor;
    private final String artifactId;
    private final List<String> packages = new ArrayList<>();
    private final List<String> recursivePackages = new ArrayList<>();
    private final List<String> types = new ArrayList<>();
    private final List<String> excluded = new ArrayList<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Set<String>> registeredByLocation = new HashMap<>();

    private ReachabilityMetadataGuard(Class<?> anchor, String artifactId) {
        this.anchor = anchor;
        this.artifactId = artifactId;
    }

    /**
     * @param anchor     any main class of the module under test, used to locate its classes and class loader
     * @param artifactId the module's artifact id, which names its metadata directory
     */
    public static ReachabilityMetadataGuard of(Class<?> anchor, String artifactId) {
        return new ReachabilityMetadataGuard(anchor, artifactId);
    }

    /** Packages (without sub-packages) whose classes Jackson binds reflectively. */
    public ReachabilityMetadataGuard packages(String... names) {
        packages.addAll(Arrays.asList(names));
        return this;
    }

    /** Packages (including sub-packages) whose classes Jackson binds reflectively. */
    public ReachabilityMetadataGuard recursivePackages(String... names) {
        recursivePackages.addAll(Arrays.asList(names));
        return this;
    }

    /** Jackson-bound types outside the configured packages. */
    public ReachabilityMetadataGuard types(String... names) {
        types.addAll(Arrays.asList(names));
        return this;
    }

    /** Top-level classes (with their nested classes) of the configured packages that Jackson never binds. */
    public ReachabilityMetadataGuard exclude(String... topLevelClassNames) {
        excluded.addAll(Arrays.asList(topLevelClassNames));
        return this;
    }

    /** The module's metadata file. */
    public String metadataPath() {
        return METADATA_DIR + artifactId + "/" + METADATA_FILE;
    }

    /** The module's parsed metadata. */
    public JsonNode metadata() {
        try (InputStream in = anchor.getClassLoader().getResourceAsStream(metadataPath())) {
            if (in == null)
                throw new IllegalStateException(metadataPath() + " not found on the class path");
            return mapper.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Problems with the module's metadata; empty when it is up to date. */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        Map<String, JsonNode> entries = new TreeMap<>();
        for (JsonNode entry : metadata().path("reflection"))
            entries.put(entry.get("type").asText(), entry);

        Set<String> expected = new TreeSet<>(types);
        for (String pkg : recursivePackages) expected.addAll(mainClasses(pkg, true, problems));
        for (String pkg : packages) expected.addAll(mainClasses(pkg, false, problems));

        Set<String> all = new TreeSet<>(entries.keySet());
        all.addAll(expected);
        List<Class<?>> registered = new ArrayList<>();
        for (String type : all) {
            Class<?> clazz = load(type);
            if (clazz == null) {
                problems.add("registered type does not exist: " + type);
                continue;
            }
            JsonNode entry = entries.get(type);
            if (entry != null && isExcluded(type)) {
                problems.add("excluded type is registered (remove its entry): " + type);
                continue;
            }
            if (entry == null) {
                problems.add("missing entry for " + type + " with methods " + bindableMethods(clazz));
                continue;
            }
            registered.add(clazz);
            checkEntry(clazz, entry, problems);
        }
        for (Class<?> clazz : registered)
            checkReferencedTypes(clazz, entries.keySet(), problems);
        return problems;
    }

    private void checkEntry(Class<?> clazz, JsonNode entry, List<String> problems) {
        String type = clazz.getName();
        String condition = entry.path("condition").path("typeReached").asText();
        if (load(condition) == null)
            problems.add(type + ": typeReached condition class does not exist: " + condition);
        if (!entry.path("allDeclaredConstructors").asBoolean() || !entry.path("allDeclaredFields").asBoolean())
            problems.add(type + ": allDeclaredConstructors and allDeclaredFields must be true");

        Set<String> listed = new TreeSet<>();
        for (JsonNode method : entry.path("methods")) {
            List<String> params = new ArrayList<>();
            method.path("parameterTypes").forEach(p -> params.add(p.asText()));
            listed.add(method.get("name").asText() + "(" + String.join(",", params) + ")");
        }
        Set<String> missing = new TreeSet<>(bindableMethods(clazz));
        missing.removeAll(listed);
        if (!missing.isEmpty())
            problems.add(type + ": missing methods " + missing);

        Set<String> undeclared = new TreeSet<>(listed);
        undeclared.removeAll(declaredMethods(clazz));
        if (!undeclared.isEmpty())
            problems.add(type + ": listed methods not declared by the type (GraalVM resolves them with getDeclaredMethod;"
                    + " list them on the declaring type, or remove them if they no longer exist) " + undeclared);
    }

    /** CCL types Jackson can reach from {@code clazz} must be registered in some CCL module's metadata. */
    private void checkReferencedTypes(Class<?> clazz, Set<String> ownEntries, List<String> problems) {
        Map<Class<?>, String> referenced = new LinkedHashMap<>();
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null) referenced.putIfAbsent(superclass, "supertype");
        // An interface only matters to Jackson when it carries Jackson annotations (e.g. @JsonTypeInfo).
        for (Class<?> iface : clazz.getInterfaces()) {
            if (jacksonAnnotated(iface) || Arrays.stream(iface.getDeclaredMethods()).anyMatch(ReachabilityMetadataGuard::jacksonAnnotated))
                referenced.putIfAbsent(iface, "supertype");
        }

        addSerializers(clazz, "class", referenced);
        // A class with its own serializer and deserializer is not introspected as a bean.
        JsonSerialize serialize = clazz.getAnnotation(JsonSerialize.class);
        JsonDeserialize deserialize = clazz.getAnnotation(JsonDeserialize.class);
        boolean customBinding = serialize != null && serialize.using() != JsonSerializer.None.class
                && deserialize != null && deserialize.using() != JsonDeserializer.None.class;

        if (!customBinding) {
            Set<String> ignoredProperties = new HashSet<>();
            JsonIgnoreProperties ignoreProperties = clazz.getAnnotation(JsonIgnoreProperties.class);
            if (ignoreProperties != null) ignoredProperties.addAll(Arrays.asList(ignoreProperties.value()));

            for (Field field : clazz.getDeclaredFields()) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers()))
                    continue;
                if (ignoredProperties.contains(field.getName()) || ignored(clazz, field.getName()))
                    continue;
                addSerializers(field, "field " + field.getName(), referenced);
                // A field with its own (de)serializer is not introspected as a bean; the serializer is checked above.
                if (!customSerializer(field))
                    for (Class<?> c : rawTypes(field.getGenericType()))
                        referenced.putIfAbsent(c, "field " + field.getName());
            }
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isSynthetic() || method.isBridge() || Modifier.isStatic(method.getModifiers())
                        || !Modifier.isPublic(method.getModifiers()) || !isGetter(method))
                    continue;
                String property = propertyName(method);
                if (property.isEmpty() || ignoredProperties.contains(property) || ignored(clazz, property))
                    continue;
                addSerializers(method, "getter " + method.getName() + "()", referenced);
                Field field = declaredField(clazz, property);
                if (!customSerializer(method) && (field == null || !customSerializer(field)))
                    for (Class<?> c : rawTypes(method.getGenericReturnType()))
                        referenced.putIfAbsent(c, "getter " + method.getName() + "()");
            }
        }

        for (Map.Entry<Class<?>, String> ref : referenced.entrySet()) {
            Class<?> c = ref.getKey();
            if (!c.getName().startsWith(CCL_PREFIX) || c.isAnonymousClass() || c.isLocalClass()
                    || NOT_BOUND.matcher(c.getName()).find() || isExcluded(c.getName()))
                continue;
            if (!ownEntries.contains(c.getName()) && !registeredAt(c).contains(c.getName()))
                problems.add(clazz.getName() + " reaches " + c.getName() + " (" + ref.getValue()
                        + "), which is not registered in any CCL reachability metadata");
        }
    }

    /** Serializer and deserializer classes Jackson instantiates for an annotated class, field or getter. */
    private static void addSerializers(AnnotatedElement element, String where, Map<Class<?>, String> referenced) {
        JsonSerialize serialize = element.getAnnotation(JsonSerialize.class);
        if (serialize != null) {
            referenced.putIfAbsent(serialize.using(), "@JsonSerialize on " + where);
            referenced.putIfAbsent(serialize.contentUsing(), "@JsonSerialize on " + where);
            referenced.putIfAbsent(serialize.keyUsing(), "@JsonSerialize on " + where);
        }
        JsonDeserialize deserialize = element.getAnnotation(JsonDeserialize.class);
        if (deserialize != null) {
            referenced.putIfAbsent(deserialize.using(), "@JsonDeserialize on " + where);
            referenced.putIfAbsent(deserialize.contentUsing(), "@JsonDeserialize on " + where);
            referenced.putIfAbsent(deserialize.keyUsing(), "@JsonDeserialize on " + where);
        }
    }

    /** Jackson ignores the whole property when its field or its getter has {@code @JsonIgnore}. */
    private static boolean ignored(Class<?> clazz, String property) {
        Field field = declaredField(clazz, property);
        if (field != null && field.isAnnotationPresent(JsonIgnore.class))
            return true;
        String suffix = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        for (String name : List.of("get" + suffix, "is" + suffix)) {
            try {
                if (clazz.getDeclaredMethod(name).isAnnotationPresent(JsonIgnore.class))
                    return true;
            } catch (NoSuchMethodException e) {
                // no such getter
            }
        }
        return false;
    }

    private static boolean customSerializer(AnnotatedElement element) {
        JsonSerialize serialize = element.getAnnotation(JsonSerialize.class);
        JsonDeserialize deserialize = element.getAnnotation(JsonDeserialize.class);
        return (serialize != null && serialize.using() != JsonSerializer.None.class)
                || (deserialize != null && deserialize.using() != JsonDeserializer.None.class);
    }

    private static Field declaredField(Class<?> clazz, String name) {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static String propertyName(Method getter) {
        String name = getter.getName();
        String bare = name.startsWith("is") ? name.substring(2) : name.substring(3);
        return bare.isEmpty() ? bare : Character.toLowerCase(bare.charAt(0)) + bare.substring(1);
    }

    private static boolean isGetter(Method m) {
        return m.getParameterCount() == 0 && m.getReturnType() != void.class
                && (m.getName().startsWith("get") || m.getName().startsWith("is"));
    }

    /** Raw classes in a generic type: the type itself, type arguments, bounds and array components. */
    private static Set<Class<?>> rawTypes(Type type) {
        Set<Class<?>> result = new LinkedHashSet<>();
        collectRawTypes(type, result, new HashSet<>());
        return result;
    }

    private static void collectRawTypes(Type type, Set<Class<?>> result, Set<Type> seen) {
        if (type == null || !seen.add(type))
            return;
        if (type instanceof Class<?> c) {
            if (c.isArray()) collectRawTypes(c.getComponentType(), result, seen);
            else if (!c.isPrimitive()) result.add(c);
        } else if (type instanceof ParameterizedType p) {
            collectRawTypes(p.getRawType(), result, seen);
            for (Type arg : p.getActualTypeArguments()) collectRawTypes(arg, result, seen);
        } else if (type instanceof WildcardType w) {
            for (Type bound : w.getUpperBounds()) collectRawTypes(bound, result, seen);
            for (Type bound : w.getLowerBounds()) collectRawTypes(bound, result, seen);
        } else if (type instanceof TypeVariable<?> v) {
            for (Type bound : v.getBounds()) collectRawTypes(bound, result, seen);
        } else if (type instanceof GenericArrayType g) {
            collectRawTypes(g.getGenericComponentType(), result, seen);
        }
    }

    /** Methods Jackson may invoke that {@code clazz} declares: public bean accessors and Jackson-annotated methods. */
    private static Set<String> bindableMethods(Class<?> clazz) {
        Set<String> methods = new TreeSet<>();
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.isSynthetic() || m.isBridge()) continue;
            boolean accessor = (m.getName().startsWith("get") && m.getParameterCount() == 0)
                    || (m.getName().startsWith("is") && m.getParameterCount() == 0)
                    || (m.getName().startsWith("set") && m.getParameterCount() == 1);
            if ((Modifier.isPublic(m.getModifiers()) && accessor) || jacksonAnnotated(m))
                methods.add(signature(m));
        }
        return methods;
    }

    private static Set<String> declaredMethods(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredMethods()).filter(m -> !m.isSynthetic())
                .map(ReachabilityMetadataGuard::signature).collect(Collectors.toCollection(TreeSet::new));
    }

    private static boolean jacksonAnnotated(AnnotatedElement element) {
        return Arrays.stream(element.getAnnotations()).map(Annotation::annotationType)
                .anyMatch(a -> a.getName().startsWith("com.fasterxml.jackson"));
    }

    private static String signature(Method method) {
        return method.getName() + "(" + Arrays.stream(method.getParameterTypes()).map(Class::getTypeName)
                .collect(Collectors.joining(",")) + ")";
    }

    private boolean isExcluded(String className) {
        return excluded.stream().anyMatch(e -> className.equals(e) || className.startsWith(e + "$"));
    }

    private Class<?> load(String type) {
        try {
            return Class.forName(type, false, anchor.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    /** Classes of {@code pkg} in the anchor's own code source (its main output directory or jar). */
    private Set<String> mainClasses(String pkg, boolean recursive, List<String> problems) {
        String path = pkg.replace('.', '/');
        Set<String> classes = new TreeSet<>();
        Path location = location(anchor);
        try {
            if (Files.isDirectory(location)) {
                Path root = location.resolve(path);
                if (Files.isDirectory(root)) {
                    try (Stream<Path> files = recursive ? Files.walk(root) : Files.list(root)) {
                        files.filter(f -> f.toString().endsWith(".class"))
                                .map(f -> location.relativize(f).toString())
                                .forEach(f -> classes.add(className(f)));
                    }
                }
            } else {
                try (JarFile jar = new JarFile(location.toFile())) {
                    Enumeration<JarEntry> jarEntries = jar.entries();
                    while (jarEntries.hasMoreElements()) {
                        String name = jarEntries.nextElement().getName();
                        if (!name.endsWith(".class") || !name.startsWith(path + "/")) continue;
                        if (!recursive && name.substring(path.length() + 1).contains("/")) continue;
                        classes.add(className(name));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        classes.removeIf(c -> NOT_BOUND.matcher(c).find() || isExcluded(c));
        if (classes.isEmpty())
            problems.add("no classes found for package " + pkg + " in " + location);
        return classes;
    }

    private static String className(String relativePath) {
        return relativePath.substring(0, relativePath.length() - ".class".length()).replace('/', '.').replace('\\', '.');
    }

    /** Types registered by the CCL metadata files in the code source of {@code clazz}. */
    private Set<String> registeredAt(Class<?> clazz) {
        Path location = location(clazz);
        return registeredByLocation.computeIfAbsent(location.toString(), key -> {
            Set<String> registered = new TreeSet<>();
            try {
                if (Files.isDirectory(location)) {
                    // IDE output keeps resources next to classes; Gradle keeps them in build/resources/main.
                    List<Path> roots = new ArrayList<>(List.of(location));
                    if (location.endsWith(Paths.get("classes", "java", "main")))
                        roots.add(location.getParent().getParent().getParent().resolve(Paths.get("resources", "main")));
                    for (Path root : roots) {
                        Path dir = root.resolve(METADATA_DIR);
                        if (!Files.isDirectory(dir)) continue;
                        try (Stream<Path> files = Files.walk(dir)) {
                            for (Path f : files.filter(f -> f.endsWith(METADATA_FILE)).collect(Collectors.toList())) {
                                try (InputStream in = Files.newInputStream(f)) {
                                    addTypes(mapper.readTree(in), registered);
                                }
                            }
                        }
                    }
                } else if (Files.isRegularFile(location)) {
                    try (JarFile jar = new JarFile(location.toFile())) {
                        Enumeration<JarEntry> jarEntries = jar.entries();
                        while (jarEntries.hasMoreElements()) {
                            JarEntry e = jarEntries.nextElement();
                            if (e.getName().startsWith(METADATA_DIR) && e.getName().endsWith(METADATA_FILE)) {
                                try (InputStream in = jar.getInputStream(e)) {
                                    addTypes(mapper.readTree(in), registered);
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return registered;
        });
    }

    private static void addTypes(JsonNode metadata, Set<String> registered) {
        for (JsonNode entry : metadata.path("reflection"))
            registered.add(entry.get("type").asText());
    }

    private static Path location(Class<?> clazz) {
        CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null)
            throw new IllegalStateException("No code source for " + clazz.getName());
        try {
            return Paths.get(codeSource.getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
