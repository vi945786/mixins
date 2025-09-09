package vi.mixin.bytecode;

import vi.mixin.util.TempFileDeleter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
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

public class AddToBootloaderSearch {

    public static void add() throws IOException, UnmodifiableClassException, ClassNotFoundException {
        List<String> temps = new ArrayList<>();

        FileSystem jrtFileSystem;
        try {
            Class<?> jrtFileSystemProvider = MixinClassHelper.findClass("jdk.internal.jrtfs.JrtFileSystemProvider");
            agent.redefineModule(jrtFileSystemProvider.getModule(), new HashSet<>(), new HashMap<>(), Map.of(jrtFileSystemProvider.getPackageName(), Set.of(AddToBootloaderSearch.class.getModule())), new HashSet<>(), new HashMap<>());

            Constructor<FileSystem> jrtFileSystemConstructor = (Constructor<FileSystem>) MixinClassHelper.findClass("jdk.internal.jrtfs.JrtFileSystem").getDeclaredConstructors()[0];
            jrtFileSystemConstructor.setAccessible(true);
            jrtFileSystem = jrtFileSystemConstructor.newInstance(jrtFileSystemProvider.getConstructors()[0].newInstance(), Map.of());
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        long time1 = System.nanoTime();
        try (FileSystem jrtFs = jrtFileSystem) {
            Path modulesRoot = jrtFs.getPath("/modules");

            try (DirectoryStream<Path> moduleDirs = Files.newDirectoryStream(modulesRoot)) {
                StreamSupport.stream(moduleDirs.spliterator(), true)
                             .forEach(moduleDir -> {
                    String moduleName = moduleDir.getFileName().toString();
                    try {
                        Path jarPath = Files.createTempFile("mixin-" + moduleName + "-", ".jar");
                        temps.add(jarPath.toString());
                        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath))) {
                            jos.setLevel(Deflater.NO_COMPRESSION);
                            Files.walkFileTree(moduleDir, new SimpleFileVisitor<>() {
                                @Override
                                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                    String entryName = moduleDir.relativize(file).toString();
                                    try (InputStream in = Files.newInputStream(file)) {
                                        jos.putNextEntry(new JarEntry(entryName));
                                        in.transferTo(jos);
                                        jos.closeEntry();
                                    }
                                    return FileVisitResult.CONTINUE;
                                }
                            });
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }
        long time2 = System.nanoTime();
        System.out.println((time2 - time1) / 1_000_000 + "ms");

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
                        String temp = generateJarFromDir(entry);
                        temps.add(temp);
                    }
                }
            }
            for (String temp : temps)  addJar(temp);
        } finally {
            List<String> agentJars = ManagementFactory.getRuntimeMXBean().getInputArguments().stream().filter(arg -> arg.startsWith("-javaagent:")).map(arg -> arg.substring("-javaagent:".length())).toList();
            String cp = "";
            for(String agentJar : agentJars) {
                addJar(agentJar);
                cp += agentJar + ";";
            }
            TempFileDeleter.spawn(cp.substring(0, cp.length() -1), temps.toArray(String[]::new));
        }
    }

    private static String generateJarFromDir(String dir) throws IOException {
        Path tempFile = Files.createTempFile("mixin-", ".jar");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(tempFile))) {
            Files.walk(Path.of(dir))
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    String entryName = Path.of(dir).relativize(path).toString().replace("\\", "/");
                    JarEntry jarEntry = new JarEntry(entryName);
                    try {
                        jos.putNextEntry(jarEntry);
                        Files.copy(path, jos);
                        jos.closeEntry();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
        }
        return tempFile.toString();
    }

    public static void addJar(String jar) throws IOException, UnmodifiableClassException, ClassNotFoundException {
        JarFile jarFile = new JarFile(jar);
        agent.appendToBootstrapClassLoaderSearch(jarFile);

        Manifest manifest = jarFile.getManifest();
        if(manifest == null) return;
        String fileName = manifest.getMainAttributes().getValue("Mixin-Classes-File");
        if (fileName == null || fileName.equals("")) return;

        String[] mixinClasses;
        try (InputStream inputStream = jarFile.getInputStream(jarFile.getEntry(fileName))) {
            mixinClasses = new String(inputStream.readAllBytes()).split(System.lineSeparator());
        }
        for(String mixinClass : mixinClasses) {
            mixinClass = mixinClass.trim();
            if(mixinClass.isEmpty() || mixinClass.startsWith("//")) continue;

            byte[] bytecode;
            try (InputStream inputStream = jarFile.getInputStream(jarFile.getEntry(mixinClass.replace(".", "/") + ".class"))) {
                bytecode = inputStream.readAllBytes();
            }
            Mixiner.addClasses(bytecode);
        }
    }
}
