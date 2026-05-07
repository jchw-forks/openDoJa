package opendoja.host;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class JamNamedModuleResourceBridgeProbe {
    private JamNamedModuleResourceBridgeProbe() {
    }

    public static void main(String[] args) throws Exception {
        reexecWithAddOpensIfNeeded();
        verifyBridgeMakesBootstrapLookupSeeGameResource();
        System.out.println("Jam named-module resource bridge probe OK");
    }

    private static void reexecWithAddOpensIfNeeded() throws Exception {
        if (JamNamedModuleResourceBridge.hasRequiredAddOpens()) {
            return;
        }
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add(JamNamedModuleResourceBridge.requiredAddOpensArgument());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(JamNamedModuleResourceBridgeProbe.class.getName());
        Process process = new ProcessBuilder(command)
                .inheritIO()
                .start();
        int exit = process.waitFor();
        System.exit(exit);
    }

    private static void verifyBridgeMakesBootstrapLookupSeeGameResource() throws Exception {
        Path root = Files.createTempDirectory("jam-named-module-resource-bridge-probe");
        Path sourceDir = Files.createDirectories(root.resolve("src"));
        Path classesDir = Files.createDirectories(root.resolve("classes"));
        Path gameJar = root.resolve("Game.jar");
        Path source = sourceDir.resolve("Sample.java");
        String resourceName = "probe-" + System.nanoTime() + ".bin";
        byte[] expected = "bootstrap-resource".getBytes(StandardCharsets.ISO_8859_1);

        Files.writeString(source, "public final class Sample {}", StandardCharsets.UTF_8);
        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        check(compiler != null, "system Java compiler should be available");
        int exit = compiler.run(null, null, null, "-d", classesDir.toString(), source.toString());
        check(exit == 0, "sample source should compile");

        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(gameJar))) {
            output.putNextEntry(new ZipEntry("Sample.class"));
            output.write(Files.readAllBytes(classesDir.resolve("Sample.class")));
            output.closeEntry();
            output.putNextEntry(new ZipEntry(resourceName));
            output.write(expected);
            output.closeEntry();
        }

        check(String.class.getResource("/" + resourceName) == null,
                "bootstrap lookup should not see the game resource before the bridge");
        check(String.class.getResourceAsStream("/" + resourceName) == null,
                "bootstrap stream lookup should not see the game resource before the bridge");

        check(JamNamedModuleResourceBridge.install(gameJar), "bridge installation should succeed");
        check(String.class.getResource("/" + resourceName) != null,
                "bootstrap URL lookup should see the bridged game resource");
        try (InputStream stream = String.class.getResourceAsStream("/" + resourceName)) {
            check(stream != null, "bootstrap stream lookup should see the bridged game resource");
            check(java.util.Arrays.equals(expected, stream.readAllBytes()),
                    "bridged resource bytes should match the game jar");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
