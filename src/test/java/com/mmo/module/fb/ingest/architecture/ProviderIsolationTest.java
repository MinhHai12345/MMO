package com.mmo.module.fb.ingest.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prevents a provider implementation from coupling to another provider's internals. */
class ProviderIsolationTest {

    private static final Path PROVIDER_ROOT =
            Path.of("src/main/java/com/mmo/module/fb/ingest/adapter/out/provider");
    private static final List<String> PROVIDERS =
            List.of("apifootball", "footballdata", "footballdatauk", "sofascore", "understats");
    private static final Pattern PROVIDER_IMPORT = Pattern.compile(
            "^\\s*import\\s+com\\.mmo\\.module\\.fb\\.ingest\\.adapter\\.out\\.provider\\."
                    + "(apifootball|footballdata|footballdatauk|sofascore|understats)(?:\\.|;).*");

    @Test
    void providerImplementationsMustNotImportSiblingProviderInternals() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String owner : PROVIDERS) {
            Path ownerRoot = PROVIDER_ROOT.resolve(owner);
            if (!Files.exists(ownerRoot)) continue;
            try (var paths = Files.walk(ownerRoot)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> {
                            try {
                                List<String> lines = Files.readAllLines(path);
                                for (int i = 0; i < lines.size(); i++) {
                                    Matcher matcher = PROVIDER_IMPORT.matcher(lines.get(i));
                                    if (matcher.find() && !owner.equals(matcher.group(1))) {
                                        violations.add(path + ":" + (i + 1) + " (" + owner + " -> "
                                                + matcher.group(1) + "): " + lines.get(i).trim());
                                    }
                                }
                            } catch (IOException exception) {
                                throw new IllegalStateException("Cannot inspect provider source " + path, exception);
                            }
                        });
            }
        }
        assertTrue(violations.isEmpty(),
                () -> "Provider isolation was violated:\n" + String.join("\n", violations));
    }
}
