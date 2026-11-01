package com.bidv.coretraining;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Source boundary checks also catch fully qualified references that bypass imports. */
class LayerArchitectureTest {
    private static final Path ROOT = Path.of("src/main/java/com/bidv/coretraining");

    @Test
    void controllersDoNotAccessPersistence() throws IOException {
        assertNoReferences("controller", List.of("com.bidv.coretraining.repository", "jakarta.persistence",
                "org.springframework.jdbc"));
    }

    @Test
    void servicesDoNotExecuteDatabaseOperations() throws IOException {
        assertNoReferences("service", List.of("jakarta.persistence.EntityManager", "jakarta.persistence.criteria",
                "org.springframework.jdbc", "createNativeQuery(", "createQuery("));
    }

    @Test
    void repositoriesDoNotDependOnServicesOrControllers() throws IOException {
        assertNoReferences("repository", List.of("com.bidv.coretraining.service", "com.bidv.coretraining.controller"));
    }

    @Test
    void businessServiceImplementationsLiveInImpl() throws IOException {
        List<String> violations = new ArrayList<>();
        try (var files = Files.list(ROOT.resolve("service"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (source.matches("(?s).*@(Service|org\\.springframework\\.stereotype\\.Service)\\b.*"))
                    violations.add(file.getFileName().toString());
            }
        }
        assertThat(violations).as("Concrete Spring services belong in service.impl").isEmpty();
    }

    private static void assertNoReferences(String folder, List<String> forbidden) throws IOException {
        List<String> violations = new ArrayList<>();
        try (var files = Files.walk(ROOT.resolve(folder))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String reference : forbidden)
                    if (source.contains(reference)) violations.add(ROOT.relativize(file) + " -> " + reference);
            }
        }
        assertThat(violations).as("Forbidden layer dependencies").isEmpty();
    }
}
