package vi.mixin.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TempFileDeleter {

    public static void main(String[] args) throws Exception {
        for (String arg : args) {
            Path path = Paths.get(arg);

            while (Files.exists(path)) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    Thread.sleep(1000);
                }
            }
        }
    }

    public static void spawn(String cp, String... filePaths) throws IOException {
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";

        String[] command = new String[4 + filePaths.length];
        command[0] = javaBin;
        command[1] = "-cp";
        command[2] = cp;
        command[3] = TempFileDeleter.class.getName();
        System.arraycopy(filePaths, 0, command, 4, filePaths.length);

        new ProcessBuilder(command).start();
    }
}
