package com.mmo.module.fb.ingest.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards orchestration from depending on provider-owned SPI contracts. */
class ApplicationOrchestrationBoundaryTest {

    private static final Path ORCHESTRATION_ROOT = Path.of(
            "src/main/java/com/mmo/module/fb/ingest/application/orchestration");
    private static final Pattern PROVIDER_SPI_IMPORT = Pattern.compile(
            "^\\\\s*import\\\\s+com\\\\.mmo\\\\.module\\\\.fb\\\\.ingest\\\\.provider\\\\.spi\\\\..*;");

    @Test
    void orchestrationDependsOnlyOnApplicationOwnedContracts() throws IOException {
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(ORCHESTRATION_ROOT)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            List<String> lines = Files.readAllLines(path);
                            for (int i = 0; i < lines.size(); i++) {
                                if (PROVIDER_SPI_IMPORT.matcher(lines.get(i)).find()) {
                                    violations.add(path + ":" + (i + 1) + " -> " + lines.get(i).trim());
                                }
                            }
                        } catch (IOException exception) {
                            throw new IllegalStateException("Cannot inspect orchestration source " + path, exception);
                        }
                    });
        }
        assertTrue(violations.isEmpty(),
                () -> "Application orchestration must not import provider SPI:\\n" + String.join("\\n", violations));
    }
}
