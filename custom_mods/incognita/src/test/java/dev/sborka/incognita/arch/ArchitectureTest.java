package dev.sborka.incognita.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Project-wide structure rules, see docs/conventions.md. */
@AnalyzeClasses(packages = "dev.sborka.incognita", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    private static final int MAX_FILES_PER_PACKAGE = 4;
    /** Packages that predate the rule. Do not add new entries; split the package instead. */
    private static final Set<String> LEGACY = Set.of("mixin/compat", "compass");

    @Test
    void packagesHoldAtMostFourSourceFiles() throws IOException {
        Path root = Path.of(System.getProperty("incognita.projectDir", "."), "src/main/java/dev/sborka/incognita");
        List<String> tooBig = new ArrayList<>();
        try (Stream<Path> dirs = Files.walk(root)) {
            for (Path dir : (Iterable<Path>) dirs.filter(Files::isDirectory)::iterator) {
                String name = root.relativize(dir).toString().replace('\\', '/');
                if (LEGACY.contains(name)) {
                    continue;
                }
                try (Stream<Path> files = Files.list(dir)) {
                    long count = files.filter(f -> f.toString().endsWith(".java")).count();
                    if (count > MAX_FILES_PER_PACKAGE) {
                        tooBig.add(name + " has " + count + " files");
                    }
                }
            }
        }
        assertTrue(tooBig.isEmpty(), "Split these packages into subpackages: " + tooBig);
    }

    /**
     * Server-safe code must not touch client-only classes, or a dedicated server crashes on class load.
     * Client code lives in packages named client, render, gl, sodium, mixin, devtest or in a *Client class.
     */
    @ArchTest
    static final ArchRule commonCodeDoesNotUseClientClasses = noClasses()
        .that().resideOutsideOfPackages("..client..", "..render..", "..gl..", "..sodium..", "..mixin..", "..devtest..")
        .and().haveSimpleNameNotEndingWith("Client")
        .and().haveSimpleNameNotEndingWith("Reload")
        .should().dependOnClassesThat().resideInAnyPackage("net.minecraft.client..", "net.caffeinemc..")
        .allowEmptyShould(true);
}
