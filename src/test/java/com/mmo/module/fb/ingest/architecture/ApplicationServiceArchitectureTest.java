package com.mmo.module.fb.ingest.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the application-service and outbound-adapter naming boundaries.
 *
 * <p>An application service is the implementation of an inbound use case, not
 * an additional wrapper around another service. Persistence implementations
 * are outbound adapters and must not be named as services or PortImpl classes.</p>
 */
class ApplicationServiceArchitectureTest {

    private static final Path APPLICATION_SERVICE_ROOT =
            Path.of("src/main/java/com/mmo/module/fb/ingest/application/service");
    private static final Path INGESTION_SERVICE_ROOT = APPLICATION_SERVICE_ROOT.resolve("ingestion");
    private static final Path PERSISTENCE_ADAPTER_ROOT =
            Path.of("src/main/java/com/mmo/module/fb/ingest/adapter/out/persistence");

    @Test
    void ingestionServicesImplementInboundUseCasesDirectly() throws IOException {
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(INGESTION_SERVICE_ROOT)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith("Service.java"))
                    .forEach(path -> {
                        try {
                            String source = Files.readString(path);
                            if (!source.matches("(?s).*\\bimplements\\s+\\w+UseCase\\b.*")) {
                                violations.add(path + " must implement an inbound *UseCase directly");
                            }
                        } catch (IOException exception) {
                            throw new IllegalStateException("Cannot inspect application service " + path, exception);
                        }
                    });
        }
        assertTrue(violations.isEmpty(), () -> String.join("\\n", violations));
    }

    @Test
    void applicationServicePackageContainsNoUseCaseImplWrapperTypes() throws IOException {
        List<String> violations;
        try (var paths = Files.walk(APPLICATION_SERVICE_ROOT)) {
            violations = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("UseCaseImpl.java"))
                    .map(Path::toString)
                    .toList();
        }
        assertTrue(violations.isEmpty(),
                () -> "Use cases must be implemented by their application service, not wrapper classes: "
                        + violations);
    }

    @Test
    void persistenceImplementationsAreNamedAdapters() throws IOException {
        List<String> violations;
        try (var paths = Files.walk(PERSISTENCE_ADAPTER_ROOT)) {
            violations = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith("PortImpl.java") || name.endsWith("Service.java"))
                    .toList();
        }
        assertTrue(violations.isEmpty(),
                () -> "Persistence implementations must be named *Adapter: " + violations);
    }
}
