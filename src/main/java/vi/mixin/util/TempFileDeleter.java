package vi.mixin.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class TempFileDeleter {

    @SuppressWarnings("all")
    public static void main(String[] args) throws Exception {
        Files.list(Path.of(System.getProperty("java.io.tmpdir"))).filter(Files::isDirectory).filter(path -> path.getFileName().toString().startsWith("mixin-")).forEach(file -> {
            try {
                deleteDirectory(file);
            } catch (IOException ignored) {}
        });

        for (String arg : args) {
            Path path = Paths.get(arg);

            while (Files.exists(path)) {
                try {
                    deleteDirectory(path);
                } catch (IOException e) {
                    Thread.sleep(1000);
                }
            }
        }
    }

    private static void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException ignored) {}
                });
            }
        }
    }

    public static void spawn(String cp, String filesPath) throws IOException {
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";

        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-cp");
        command.add(cp);
        command.add(TempFileDeleter.class.getName());
        command.add(filesPath);

        new ProcessBuilder(command).start();
    }
}
