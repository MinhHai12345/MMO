package com.mmo.module.fb.ingest.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protects the dependency boundary of the complete application layer.
 *
 * <p>Ports describe capabilities required by the application. They must not
 * expose persistence/framework APIs or provider implementation details.</p>
 */
class IngestionPortContractArchitectureTest {

    private static final Path APPLICATION_ROOT =
            Path.of("src/main/java/com/mmo/module/fb/ingest/application");

    private static final Pattern FORBIDDEN_IMPORT = Pattern.compile(
            "^\\s*import\\s+(?:"
                    + "org\\.springframework\\.data\\.|"
                    + "jakarta\\.persistence\\.|"
                    + "javax\\.persistence\\.|"
                    + "com\\.mmo\\.module\\.fb\\.ingest\\.adapter\\.|"
                    + "com\\.mmo\\.module\\.fb\\.domain\\.[^;]*\\.repository\\.|"
                    + "com\\.mmo\\.module\\.fb\\.ingest\\.provider\\.model\\."
                    + "|com\\.mmo\\.module\\.fb\\.ingest\\.provider\\."
                    + "(?:apifootball|footballdata|footballdatauk|sofascore|understats)\\."
                    + "(?:client|dto|mapper)(?:\\.|;)"
                    + ").*");

    @Test
    void applicationLayerMustNotDependOnInfrastructureOrProviderImplementationTypes() throws IOException {
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(PORT_ROOT)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            List<String> lines = Files.readAllLines(path);
                            for (int i = 0; i < lines.size(); i++) {
                                if (FORBIDDEN_IMPORT.matcher(lines.get(i)).find()) {
                                    violations.add(path + ":" + (i + 1) + " -> " + lines.get(i).trim());
                                }
                            }
                        } catch (IOException exception) {
                            throw new IllegalStateException("Cannot inspect application port " + path, exception);
                        }
                    });
        }

        assertTrue(violations.isEmpty(),
                () -> "Application layer must remain independent from persistence and provider implementation details:\\n"
                        + String.join("\\n", violations));
    }
}
