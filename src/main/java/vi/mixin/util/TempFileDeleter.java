package vi.mixin.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class TempFileDeleter {

    public static void main(String[] args) throws Exception {
        Files.list(Path.of(System.getProperty("java.io.tmpdir"))).filter(Files::isRegularFile).filter(path -> path.getFileName().toString().startsWith("mixin-")).toList().forEach(file -> {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        for (String arg : args) {
            Path path = Paths.get(arg);

            while (Files.exists(path)) {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    Thread.sleep(1000);
                }
            }
        }
    }

    public static void spawn(String cp, List<String> filePaths) throws IOException {
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";

        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-cp");
        command.add(cp);
        command.add(TempFileDeleter.class.getName());
        command.addAll(filePaths);

        new ProcessBuilder(command).inheritIO().start();
    }
}
