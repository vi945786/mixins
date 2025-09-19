package vi.mixin.bytecode;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.classtypes.MixinClassType;
import vi.mixin.api.transformers.TransformerSupplier;
import vi.mixin.util.TempFileDeleter;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.StreamSupport;
import java.util.zip.Deflater;
import java.util.zip.ZipOutputStream;

import static vi.mixin.bytecode.Agent.agent;

public class RegisterJars {

    private static final List<String> classesEntries = new ArrayList<>();
    private static final List<String> transformersEntries = new ArrayList<>();
    private static final List<String> mixinClassTypesEntries = new ArrayList<>();
    public static void add() throws IOException {
        //load these classes before appending to bootstrap
        RegisterJars.class.getDeclaredClasses();

        List<String> agentJars = ManagementFactory.getRuntimeMXBean().getInputArguments().stream().filter(arg -> arg.startsWith("-javaagent:"))
                    .map(arg -> arg.substring("-javaagent:".length()))
                    .map(arg -> arg.split("=")[0])
                    .toList();

        String agentJarClasspath = "";
        for(String agentJar : agentJars) {
            addJar(agentJar);
            agentJarClasspath += agentJar + File.pathSeparator;
        }

        doRunArgs();

        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        Path tempDir = Files.createTempDirectory("mixin-" + pid + "-");
        generateBuiltinJars(tempDir);

        String classPath = System.getProperty("java.class.path");
        try {
            for (String entry : classPath.split(File.pathSeparator)) {
                File entryFile = new File(entry);
                if (entryFile.isFile()) {
                    if (entryFile.getName().endsWith(".jar")) addJar(entryFile.getAbsolutePath());
                } else {
                    if (entry.endsWith(File.separator + "*")) {
                        for (File file : entryFile.getParentFile().listFiles()) {
                            if (file.isFile() && file.getName().endsWith(".jar")) addJar(file.getAbsolutePath());
                        }
                    } else {
                        addJar(generateJarFromDir(Path.of(entry), tempDir));
                    }
                }
            }
        } finally {
            TempFileDeleter.spawn(agentJarClasspath.substring(0, agentJarClasspath.length() -1), tempDir.toString());
        }
    }

    private static void doRunArgs() {
        if(System.getProperty("mixin.files") != null) {
            for (String file : System.getProperty("mixin.files").split(File.pathSeparator)) {
                try {
                    MixinFile mixinFile = getMixinFile(Files.newBufferedReader(Path.of(file)));
                    mixinClassTypesEntries.addAll(mixinFile.mixinClassTypes);
                    transformersEntries.addAll(mixinFile.transformers);
                    classesEntries.addAll(mixinFile.mixinClasses);
                } catch (IOException e) {
                    throw new RuntimeException("failed to load mixin file " + file, e);
                }
            }
        }
    }

    private static String generateJarFromDir(Path dir, Path tempDir) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ZipOutputStream jos = new ZipOutputStream(byteArrayOutputStream)) {
            jos.setLevel(Deflater.NO_COMPRESSION);
            Files.walk(dir).filter(Files::isRegularFile).forEach(file -> {
                try {
                    String entryName = dir.relativize(file).toString().replace(File.separatorChar, '/');

                    try (InputStream in = Files.newInputStream(file)) {
                        jos.putNextEntry(new JarEntry(entryName));
                        in.transferTo(jos);
                        jos.closeEntry();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        Path jarPath = tempDir.resolve(tempDir + File.separator + dir.toString().replace(":", "-").replace(dir.getFileSystem().getSeparator(), "-").replace("--", "-") + ".jar");
        try (OutputStream os = Files.newOutputStream(jarPath)) {
            byteArrayOutputStream.writeTo(os);
        }
        return jarPath.toString();
    }

    private static void generateBuiltinJars(Path tempDir) {
        FileSystem jrtFileSystem;
        try {
            Class<?> jrtFileSystemProvider = MixinClassHelper.findClass("jdk.internal.jrtfs.JrtFileSystemProvider");
            agent.redefineModule(jrtFileSystemProvider.getModule(), new HashSet<>(), new HashMap<>(), Map.of(jrtFileSystemProvider.getPackageName(), Set.of(RegisterJars.class.getModule())), new HashSet<>(), new HashMap<>());

            Constructor<FileSystem> jrtFileSystemConstructor = (Constructor<FileSystem>) MixinClassHelper.findClass("jdk.internal.jrtfs.JrtFileSystem").getDeclaredConstructors()[0];
            jrtFileSystemConstructor.setAccessible(true);
            jrtFileSystem = jrtFileSystemConstructor.newInstance(jrtFileSystemProvider.getConstructors()[0].newInstance(), Map.of());
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        try (FileSystem jrtFs = jrtFileSystem) {
            Path modulesRoot = jrtFs.getPath("/modules");

            try (DirectoryStream<Path> moduleDirs = Files.newDirectoryStream(modulesRoot)) {
                StreamSupport.stream(moduleDirs.spliterator(), true).forEach(moduleDir -> {
                    try {
                        addJar(generateJarFromDir(moduleDir, tempDir), false);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                 });
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void addJar(String jar) {
        addJar(jar, true);
    }

    public static void addJar(String jar, boolean checkMixins) {
        try (JarFile jarFile = new JarFile(jar)) {
            agent.appendToBootstrapClassLoaderSearch(jarFile);

            if(!checkMixins) return;
            List<byte[]> bytecodes = checkStartUpEntries(jarFile);

            Manifest manifest = jarFile.getManifest();
            if (manifest != null) {
                String fileName = manifest.getMainAttributes().getValue("Mixin-File");
                if (fileName != null && !fileName.isEmpty()) {

                    try (InputStream in = jarFile.getInputStream(jarFile.getEntry(fileName));
                         Reader reader = new InputStreamReader(in)) {
                        MixinFile mixinFile = getMixinFile(reader);
                        registerMixinClassTypes(mixinFile.mixinClassTypes);
                        registerTransformers(mixinFile.transformers);

                        for (String mixinClass : mixinFile.mixinClasses) {
                            try (InputStream inputStream = jarFile.getInputStream(jarFile.getEntry(nameToEntry(mixinClass)))) {
                                bytecodes.add(inputStream.readAllBytes());
                            }
                        }
                    }
                }
            }
            if(bytecodes.isEmpty()) return;
            Mixiner.addClasses(bytecodes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load jar or mixin classes from jar: " + jar, e);
        }
    }

    private static List<byte[]> checkStartUpEntries(JarFile jarFile) throws IOException {
        List<byte[]> bytecodes = new ArrayList<>();

        for(String name : classesEntries) {
            JarEntry entry = jarFile.getJarEntry(nameToEntry(name));
            if(entry == null) continue;

            try (InputStream inputStream = jarFile.getInputStream(entry)) {
                bytecodes.add(inputStream.readAllBytes());
            }
        }
        for(String name : transformersEntries) {
            JarEntry entry = jarFile.getJarEntry(nameToEntry(name));
            if(entry == null) continue;

            registerTransformer(name);
        }
        for(String name : mixinClassTypesEntries) {
            JarEntry entry = jarFile.getJarEntry(nameToEntry(name));
            if(entry == null) continue;

            registerMixinClassType(name);
        }

        return bytecodes;
    }

    private record MixinFile(List<String> mixinClasses, List<String> transformers, List<String> mixinClassTypes) {}

    private static void registerTransformer(String transformer) {
        registerTransformers(List.of(transformer));
    }

    private static void registerTransformers(List<String> transformers) {
        transformers.forEach(mixinTransformer -> {
            Class<?> transformer = MixinClassHelper.findClass(mixinTransformer);

            if(transformer == null) throw new MixinFormatException(mixinTransformer, "transformer supplier class not found");
            if(!TransformerSupplier.class.isAssignableFrom(transformer)) throw new MixinFormatException(mixinTransformer, "transformer does not implement vi.mixin.api.transformers.TransformerSupplier");

            try {
                ((TransformerSupplier) transformer.getConstructor().newInstance()).getBuiltTransformers().forEach(Mixiner::addBuiltTransformer);
            } catch (NoSuchMethodException e) {
                throw new MixinFormatException(mixinTransformer, "transformer does not have a public no-args constructor");
            } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void registerMixinClassType(String mixinClassType) {
        registerTransformers(List.of(mixinClassType));
    }

    private static void registerMixinClassTypes(List<String> mixinClassTypes) {
        mixinClassTypes.forEach(mixinClassTypeName -> {
            Class<?> mixinClassType = MixinClassHelper.findClass(mixinClassTypeName);

            if(mixinClassType == null) throw new MixinFormatException(mixinClassTypeName, "mixin class type supplier class not found");
            if(!MixinClassType.class.isAssignableFrom(mixinClassType)) throw new MixinFormatException(mixinClassTypeName, "mixin class type does not implement vi.mixin.api.classtypes.MixinClassType");

            try {
                Mixiner.addMixinClassType((MixinClassType<?, ?, ?, ?, ?>) mixinClassType.getConstructor().newInstance());
            } catch (NoSuchMethodException e) {
                throw new MixinFormatException(mixinClassTypeName, "mixin class type does not have a public no-args constructor");
            } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static MixinFile getMixinFile(Reader reader) throws IOException {
        JsonValue value = Json.parse(reader);
        List<String> mixinClassTypes = new ArrayList<>();
        List<String> transformers = new ArrayList<>();
        List<String> mixins = new ArrayList<>();
        if(!(value instanceof JsonObject mixinFile)) throw new MixinFormatException("mixin file", "invalid mixin file");
        if(mixinFile.get("mixinClassTypes") instanceof JsonArray fileMixinClassTypes) {
            for(JsonValue jsonValue : fileMixinClassTypes.values())  {
                if(!jsonValue.isString()) throw new MixinFormatException("mixin file", "mixinClassTypes entry is invalid");
                mixinClassTypes.add(jsonValue.asString());
            }
        }
        if(mixinFile.get("transformers") instanceof JsonArray fileTransfomers) {
            for(JsonValue jsonValue : fileTransfomers.values())  {
                if(!jsonValue.isString()) throw new MixinFormatException("mixin file", "transformers entry is invalid");
                transformers.add(jsonValue.asString());
            }
        }
        if(mixinFile.get("mixins") instanceof JsonArray mixinClasses) {
            for(JsonValue jsonValue : mixinClasses.values())  {
                if(!jsonValue.isString()) throw new MixinFormatException("mixin file", "mixins entry is invalid");
                mixins.add(jsonValue.asString());
            }
        }

        return new MixinFile(mixins, transformers, mixinClassTypes);
    }

    private static String nameToEntry(String name) {
        return name.replace(".", "/") + ".class";
    }
}
