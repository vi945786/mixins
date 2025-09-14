package vi.mixin.bytecode;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import sun.misc.Unsafe;
import vi.mixin.util.TempFileDeleter;

import java.io.*;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.StreamSupport;
import java.util.zip.Deflater;

import static vi.mixin.bytecode.Agent.agent;

public class RegisterJars {

    private static final List<String> classesEntryNames = new ArrayList<>();
    public static void add() throws IOException {
        if(System.getProperty("mixin.classes") != null) classesEntryNames.addAll(List.of(System.getProperty("mixin.classes").split(File.pathSeparator)));
        if(System.getProperty("mixin.files") != null) {
            for (String file : System.getProperty("mixin.files").split(File.pathSeparator)) {
                try {
                    classesEntryNames.addAll(getMixinClassesFromFile(Files.readAllLines(Path.of(file))));
                } catch (IOException e) {
                    throw new RuntimeException("failed to load mixin file " + file, e);
                }
            }
        }

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
            List<String> agentJars = ManagementFactory.getRuntimeMXBean().getInputArguments().stream().filter(arg -> arg.startsWith("-javaagent:"))
                    .map(arg -> arg.substring("-javaagent:".length()))
                    .map(arg -> arg.split("=")[0])
                    .toList();

            String cp = "";
            for(String agentJar : agentJars) {
                addJar(agentJar);
                cp += agentJar + File.pathSeparator;
            }
            TempFileDeleter.spawn(cp.substring(0, cp.length() -1), tempDir.toString());
        }
    }

    private static String generateJarFromDir(Path dir, Path tempDir) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (JarOutputStream jos = new JarOutputStream(byteArrayOutputStream)) {
            jos.setLevel(Deflater.NO_COMPRESSION);
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String entryName = dir.relativize(file).toString();

                    try (InputStream in = Files.newInputStream(file)) {
                        jos.putNextEntry(new JarEntry(entryName));
                        in.transferTo(jos);
                        jos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        Path jarPath = tempDir.resolve(tempDir + File.separator + dir.toString().replace(":", ".").replace(dir.getFileSystem().getSeparator(), ".").replace("..", ".") + ".jar");
        byteArrayOutputStream.writeTo(Files.newOutputStream(jarPath));
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
            List<byte[]> bytecodes = new ArrayList<>();
            for(String name : classesEntryNames) {
                JarEntry entry = jarFile.getJarEntry(nameToEntry(name));
                if(entry == null) continue;

                try (InputStream inputStream = jarFile.getInputStream(entry)) {
                    bytecodes.add(inputStream.readAllBytes());
                }
            }

            Manifest manifest = jarFile.getManifest();
            if (manifest != null) {
                String fileName = manifest.getMainAttributes().getValue("Mixin-Classes-File");
                if (fileName != null && !fileName.isEmpty()) {

                    List<String> mixinClasses;
                    try (InputStream in = jarFile.getInputStream(jarFile.getEntry(fileName));
                         BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
                        mixinClasses = getMixinClassesFromFile(br.lines().toList());
                    }

                    for (String mixinClass : mixinClasses) {
                        try (InputStream inputStream = jarFile.getInputStream(jarFile.getEntry(nameToEntry(mixinClass)))) {
                            bytecodes.add(inputStream.readAllBytes());
                        }
                    }
                }
            }
            Mixiner.addClasses(bytecodes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load jar or mixin classes from jar: " + jar, e);
        }
    }

    private static List<String> getMixinClassesFromFile(List<String> lines) {
        List<String> classes = new ArrayList<>();
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() & !line.startsWith("//")) classes.add(line);
        }

        return classes;
    }

    private static String nameToEntry(String name) {
        return name.replace(".", "\\") + ".class";
    }
}
