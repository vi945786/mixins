package vi.mixin.bytecode;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
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
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.Deflater;
import java.util.zip.ZipOutputStream;

import static vi.mixin.bytecode.Agent.agent;

public class RegisterJars {

    private final List<String> classEntries = new ArrayList<>();
    private final List<String> transformersEntries = new ArrayList<>();
    private final List<String> mixinClassTypesEntries = new ArrayList<>();
    private final List<byte[]> mixins = new ArrayList<>();
    private final List<byte[]> inners = new ArrayList<>();
    private final Mixiner mixiner = new Mixiner();

    private RegisterJars() {}

    static void registerAll(String args) {
        new RegisterJars().registerAll0(args);
    }

    private void registerAll0(String args) {
        try {
            doRunArgs(args);

            String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            Path tempDir = Files.createTempDirectory("mixin-" + pid + "-");
            generateBuiltinJars(tempDir);

            List<String> agentJars = ManagementFactory.getRuntimeMXBean().getInputArguments().stream().filter(arg -> arg.startsWith("-javaagent:"))
                    .map(arg -> arg.substring("-javaagent:".length()))
                    .map(arg -> arg.split("=")[0])
                    .toList();

            StringBuilder agentJarClasspath = new StringBuilder();
            for (String agentJar : agentJars) {
                addJarWithMixins(agentJar);
                agentJarClasspath.append(agentJar).append(File.pathSeparator);
            }
            agentJarClasspath = new StringBuilder(agentJarClasspath.substring(0, agentJarClasspath.length() - 1));

            String classPath = System.getProperty("java.class.path");
            try {
                for (String entry : classPath.split(File.pathSeparator)) {
                    File entryFile = new File(entry);
                    if (entryFile.isFile()) {
                        if (entryFile.getName().endsWith(".jar")) addJarWithMixins(entryFile.getAbsolutePath());
                    } else {
                        if (entry.endsWith(File.separator + "*")) {
                            for (File file : Objects.requireNonNull(entryFile.getParentFile().listFiles())) {
                                if (file.isFile() && file.getName().endsWith(".jar"))
                                    addJarWithMixins(file.getAbsolutePath());
                            }
                        } else {
                            addJarWithMixins(generateJarFromDir(Path.of(entry), tempDir));
                        }
                    }
                }
            } finally {
                TempFileDeleter.spawn(agentJarClasspath.toString(), tempDir.toString());
            }
        } catch (IOException e) {
            throw new RuntimeException("unable to register jars", e);
        }

        mixiner.addClasses(mixins, inners);
    }

    private void doRunArgs(String args) {
        if(args != null) {
            for (String file : args.split(File.pathSeparator)) {
                try {
                    MixinFile mixinFile = getMixinFile(Files.newBufferedReader(Path.of(file)));
                    mixinClassTypesEntries.addAll(mixinFile.mixinClassTypes);
                    transformersEntries.addAll(mixinFile.transformers);
                    classEntries.addAll(mixinFile.mixinClasses);
                } catch (IOException e) {
                    throw new RuntimeException("failed to load mixin file " + file, e);
                }
            }
        }
    }

    private String generateJarFromDir(Path dir, Path tempDir) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ZipOutputStream jos = new ZipOutputStream(byteArrayOutputStream); Stream<Path> walk = Files.walk(dir)) {
            jos.setLevel(Deflater.NO_COMPRESSION);
            walk.filter(Files::isRegularFile).forEach(file -> {
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
            jos.finish();
        }
        Path jarPath = tempDir.resolve(tempDir + File.separator + dir.toString().replace(":", "-").replace(dir.getFileSystem().getSeparator(), "-").replace("--", "-") + ".jar");
        try (OutputStream os = Files.newOutputStream(jarPath)) {
            byteArrayOutputStream.writeTo(os);
        }
        return jarPath.toString();
    }

    @SuppressWarnings("unchecked")
    private void generateBuiltinJars(Path tempDir) {
        FileSystem jrtFileSystem;
        try {
            Class<?> jrtFileSystemProvider = MixinClassHelper.findClass("jdk.internal.jrtfs.JrtFileSystemProvider");
            assert jrtFileSystemProvider != null;
            agent.redefineModule(jrtFileSystemProvider.getModule(), new HashSet<>(), new HashMap<>(), Map.of(jrtFileSystemProvider.getPackageName(), Set.of(RegisterJars.class.getModule())), new HashSet<>(), new HashMap<>());

            Constructor<FileSystem> jrtFileSystemConstructor = (Constructor<FileSystem>) Objects.requireNonNull(MixinClassHelper.findClass("jdk.internal.jrtfs.JrtFileSystem")).getDeclaredConstructors()[0];
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
                        addJar(generateJarFromDir(moduleDir, tempDir));
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
        try {
            agent.appendToBootstrapClassLoaderSearch(new JarFile(jar));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load jar: " + jar, e);
        }
    }

    private void addJarWithMixins(String jar) {
        try (JarFile jarFile = new JarFile(jar)) {
            agent.appendToBootstrapClassLoaderSearch(jarFile);

            checkStartUpEntries(jarFile);

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
                            byte[] bytecode;
                            try (InputStream inputStream = jarFile.getInputStream(jarFile.getEntry(nameToEntry(mixinClass)))) {
                                bytecode = inputStream.readAllBytes();
                                mixins.add(bytecode);
                            }
                            findAnonymousInnerClasses(jarFile, bytecode);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load jar: " + jar, e);
        }
    }

    private void findAnonymousInnerClasses(JarFile jarFile, byte[] bytecode) {
        ClassReader cr = new ClassReader(bytecode);
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visitInnerClass(String innerName, String outerName, String innerSimpleName, int access) {
                if (innerSimpleName == null || innerName.matches(".*\\$\\d+$")) {

                    String innerPath = innerName + ".class";
                    JarEntry innerEntry = jarFile.getJarEntry(innerPath);
                    if (innerEntry != null) {
                        try (InputStream is = jarFile.getInputStream(innerEntry)) {
                            inners.add(is.readAllBytes());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    private void checkStartUpEntries(JarFile jarFile) throws IOException {
        for(String name : classEntries) {
            JarEntry entry = jarFile.getJarEntry(nameToEntry(name));
            if(entry == null) continue;

            byte[] bytecode;
            try (InputStream inputStream = jarFile.getInputStream(entry)) {
                bytecode = inputStream.readAllBytes();
                mixins.add(bytecode);
            }
            findAnonymousInnerClasses(jarFile, bytecode);
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
    }

    private record MixinFile(List<String> mixinClasses, List<String> transformers, List<String> mixinClassTypes) {}

    private void registerTransformer(String transformer) {
        registerTransformers(List.of(transformer));
    }

    private  void registerTransformers(List<String> transformers) {
        transformers.forEach(mixinTransformer -> {
            Class<?> transformer = MixinClassHelper.findClass(mixinTransformer);

            if(transformer == null) throw new MixinFormatException(mixinTransformer, "transformer supplier class not found");
            if(!TransformerSupplier.class.isAssignableFrom(transformer)) throw new MixinFormatException(mixinTransformer, "transformer does not implement vi.mixin.api.transformers.TransformerSupplier");

            try {
                ((TransformerSupplier) transformer.getConstructor().newInstance()).getBuiltTransformers().forEach(mixiner::addBuiltTransformer);
            } catch (NoSuchMethodException e) {
                throw new MixinFormatException(mixinTransformer, "transformer does not have a public no-args constructor");
            } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void registerMixinClassType(String mixinClassType) {
        registerTransformers(List.of(mixinClassType));
    }

    private void registerMixinClassTypes(List<String> mixinClassTypes) {
        mixinClassTypes.forEach(mixinClassTypeName -> {
            Class<?> mixinClassType = MixinClassHelper.findClass(mixinClassTypeName);

            if(mixinClassType == null) throw new MixinFormatException(mixinClassTypeName, "mixin class type class not found");
            if(!MixinClassType.class.isAssignableFrom(mixinClassType)) throw new MixinFormatException(mixinClassTypeName, "mixin class type does not implement vi.mixin.api.classtypes.MixinClassType");

            try {
                Constructor<?> c = mixinClassType.getDeclaredConstructor();
                c.setAccessible(true);
                mixiner.addMixinClassType((MixinClassType<?, ?, ?, ?, ?>) c.newInstance());

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
