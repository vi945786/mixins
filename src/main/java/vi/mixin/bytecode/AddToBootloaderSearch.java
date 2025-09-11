package vi.mixin.bytecode;

import vi.mixin.api.MixinFormatException;
import vi.mixin.util.TempFileDeleter;

import java.io.*;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.StreamSupport;
import java.util.zip.Deflater;

import static vi.mixin.bytecode.Agent.agent;

public class AddToBootloaderSearch {

    public static void add() throws IOException, UnmodifiableClassException, ClassNotFoundException {
        List<String> temps = new ArrayList<>(generateBuiltinJars());

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
                        String temp = generateJarFromDir(Path.of(entry));
                        addJar(temp);
                        temps.add(temp);
                    }
                }
            }
        } finally {
            List<String> agentJars = ManagementFactory.getRuntimeMXBean().getInputArguments().stream().filter(arg -> arg.startsWith("-javaagent:")).map(arg -> arg.substring("-javaagent:".length())).toList();
            String cp = "";
            for(String agentJar : agentJars) {
                addJar(agentJar);
                cp += agentJar + ";";
            }
            TempFileDeleter.spawn(cp.substring(0, cp.length() -1), temps);
        }
    }

    private static String generateJarFromDir(Path dir) throws IOException {
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
        Path jarPath = Files.createTempFile("mixin-", ".jar");
        byteArrayOutputStream.writeTo(Files.newOutputStream(jarPath));
        return jarPath.toString();
    }

    private static List<String> generateBuiltinJars() {
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

        List<String> temps = new ArrayList<>();
        try (FileSystem jrtFs = jrtFileSystem) {
            Path modulesRoot = jrtFs.getPath("/modules");

            try (DirectoryStream<Path> moduleDirs = Files.newDirectoryStream(modulesRoot)) {
                StreamSupport.stream(moduleDirs.spliterator(), true).forEach(moduleDir -> {
                    try {
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        try (JarOutputStream jos = new JarOutputStream(byteArrayOutputStream)) {
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
                        Path jarPath = Files.createTempFile("mixin-", ".jar");
                        byteArrayOutputStream.writeTo(Files.newOutputStream(jarPath));
                        temps.add(jarPath.toString());
                        addJar(jarPath.toString(), false);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                 });
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return temps;
    }

    public static void addJar(String jar) {
        addJar(jar, true);
    }

    private static void addJar(String jar, boolean join) {
        AtomicReference<MixinFormatException> mixinFormatException = new AtomicReference<>(null);
        Thread thread = new Thread(() -> {
            try {
                JarFile jarFile = new JarFile(jar);
                agent.appendToBootstrapClassLoaderSearch(jarFile);

                Manifest manifest = jarFile.getManifest();
                if (manifest == null) return;
                String fileName = manifest.getMainAttributes().getValue("Mixin-Classes-File");
                if (fileName == null || fileName.isEmpty()) return;

                String[] mixinClasses;
                try (InputStream inputStream = jarFile.getInputStream(jarFile.getEntry(fileName))) {
                    mixinClasses = new String(inputStream.readAllBytes()).split(System.lineSeparator());
                }

                List<byte[]> bytecodes = new ArrayList<>();
                for (String mixinClass : mixinClasses) {
                    mixinClass = mixinClass.trim();
                    if (mixinClass.isEmpty() || mixinClass.startsWith("//")) continue;

                    try (InputStream inputStream = jarFile.getInputStream(jarFile.getEntry(mixinClass.replace(".", "/") + ".class"))) {
                        bytecodes.add(inputStream.readAllBytes());
                    }
                }
                Mixiner.addClasses(bytecodes);
            } catch (IOException | UnmodifiableClassException | ClassNotFoundException e) {
                throw new RuntimeException("Failed to load jar", e);
            } catch (MixinFormatException e) {
                mixinFormatException.set(e);
            }
        });
        thread.start();
        if(join) {
            try {
                thread.join();
                if(mixinFormatException.get() != null) throw mixinFormatException.get();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
