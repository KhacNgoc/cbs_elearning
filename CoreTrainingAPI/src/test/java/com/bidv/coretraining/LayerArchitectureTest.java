package com.bidv.coretraining;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source boundary checks also catch fully qualified references that bypass imports.
 * Packages are laid out as {@code <domain>/<layer>}, e.g. {@code course/service/impl}.
 */
class LayerArchitectureTest {
    private static final Path ROOT = Path.of("src/main/java/com/bidv/coretraining");

    @Test
    void controllersDoNotAccessPersistence() throws IOException {
        assertNoReferences("controller", List.of(layerPackage("repository"), literal("jakarta.persistence"),
                literal("org.springframework.jdbc")));
    }

    @Test
    void servicesDoNotExecuteDatabaseOperations() throws IOException {
        assertNoReferences("service", List.of(literal("jakarta.persistence.EntityManager"),
                literal("jakarta.persistence.criteria"), literal("org.springframework.jdbc"),
                literal("createNativeQuery("), literal("createQuery(")));
    }

    @Test
    void repositoriesDoNotDependOnServicesOrControllers() throws IOException {
        assertNoReferences("repository", List.of(layerPackage("service"), layerPackage("controller")));
    }

    @Test
    void businessServiceImplementationsLiveInImpl() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : layerFiles("service")) {
            if (file.getParent().getFileName().toString().equals("impl")) continue;
            String source = Files.readString(file);
            if (source.matches("(?s).*@(Service|org\\.springframework\\.stereotype\\.Service)\\b.*"))
                violations.add(file.getFileName().toString());
        }
        assertThat(violations).as("Concrete Spring services belong in <domain>.service.impl").isEmpty();
    }

    private static Pattern layerPackage(String layer) {
        return Pattern.compile("com\\.bidv\\.coretraining\\.\\w+\\." + layer + "\\b");
    }

    private static Pattern literal(String text) {
        return Pattern.compile(Pattern.quote(text));
    }

    /** Java files under {@code <domain>/<layer>}; an empty result means the layout changed and the checks are void. */
    private static List<Path> layerFiles(String layer) throws IOException {
        try (var files = Files.walk(ROOT)) {
            List<Path> found = files.filter(p -> p.toString().endsWith(".java")).filter(p -> {
                Path relative = ROOT.relativize(p);
                return relative.getNameCount() > 2 && relative.getName(1).toString().equals(layer);
            }).toList();
            assertThat(found).as("Java files in <domain>/" + layer).isNotEmpty();
            return found;
        }
    }

    private static void assertNoReferences(String layer, List<Pattern> forbidden) throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : layerFiles(layer)) {
            String source = Files.readString(file);
            for (Pattern reference : forbidden)
                if (reference.matcher(source).find()) violations.add(ROOT.relativize(file) + " -> " + reference);
        }
        assertThat(violations).as("Forbidden layer dependencies").isEmpty();
    }
}
