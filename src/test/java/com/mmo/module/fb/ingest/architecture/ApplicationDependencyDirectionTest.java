package com.mmo.module.fb.ingest.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source-level guard for the application-to-adapter dependency rule. */
class ApplicationDependencyDirectionTest {

    private static final Path APPLICATION_ROOT =
            Path.of("src/main/java/com/mmo/module/fb/ingest/application");

    private static final Pattern FORBIDDEN_IMPORT = Pattern.compile(
            "^\\s*import\\s+com\\.mmo\\.module\\.fb\\.ingest\\.(?:adapter\\.|repository\\.|entity\\.|service\\.|provider\\.spi\\.)"
                    + "|^\\s*import\\s+com\\.mmo\\.module\\.fb\\.ingest\\.provider\\."
                    + "(?:apifootball|footballdata|footballdatauk|sofascore|understats)\\."
                    + "(?:client|dto|mapper)(?:\\.|;)");

    @Test
    void applicationMayDependOnPortsAndNeutralModelsButNotAdaptersOrProviderInternals() throws IOException {
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(APPLICATION_ROOT)) {
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
                            throw new IllegalStateException("Cannot inspect application source " + path, exception);
                        }
                    });
        }
        assertTrue(violations.isEmpty(),
                () -> "Application dependency direction was violated:\n" + String.join("\n", violations));
    }
}
