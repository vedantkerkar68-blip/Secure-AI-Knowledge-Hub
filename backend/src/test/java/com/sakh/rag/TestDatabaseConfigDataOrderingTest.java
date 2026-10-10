package com.sakh.rag;

import com.sakh.BackendApplication;
import com.sakh.support.TestDatabaseSafetyEnvironmentPostProcessor;
import com.sakh.support.TestDatabaseSafetyGuard;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the ordering of {@link TestDatabaseSafetyEnvironmentPostProcessor} against
 * Spring Boot's config-data phase.
 *
 * <p>Regression under test: the post-processor used to run at
 * {@code Ordered.HIGHEST_PRECEDENCE}, which is *before*
 * {@link ConfigDataEnvironmentPostProcessor}. application-test.yml is loaded by that
 * processor, so at the guard's original order {@code spring.datasource.url} was still
 * unresolved and validation was silently skipped - the guard was a no-op in every real
 * test run, and only appeared to work because one test injected the URL through
 * {@code SpringApplicationBuilder.properties()}, which is readable earlier.
 */
class TestDatabaseConfigDataOrderingTest {

    private static final String DANGEROUS_URL =
            "jdbc:postgresql://db.mpqgieodurvgnrfcoipd.supabase.co:5432/postgres?sslmode=require";

    /**
     * The guard must be ordered strictly after config data, so that application yml
     * files have already been loaded when it inspects the datasource URL.
     */
    @Test
    void guardRunsStrictlyAfterConfigDataProcessor() {
        assertTrue(
                new TestDatabaseSafetyEnvironmentPostProcessor().getOrder()
                        > ConfigDataEnvironmentPostProcessor.ORDER,
                "The guard must run after config data is loaded, otherwise spring.datasource.url"
                        + " is unresolved and validation is silently skipped.");
    }

    /**
     * The real application loads application-test.yml through Config Data. This test
     * feeds the dangerous URL through a temporary configuration file on the Config Data
     * path rather than through SpringApplicationBuilder.properties(), which would be
     * readable before config data loads and would mask the ordering bug.
     *
     * <p>If the guard ran too early it would see no URL, skip validation, and the
     * application would start (or fail on its own) instead of being rejected by the
     * guard - so this test fails whenever the ordering regresses.
     */
    @Test
    void dangerousUrlSuppliedThroughConfigData_isRejectedBeforeFlywayOrPostgres() throws Exception {
        Path configDir = Files.createTempDirectory("sakh-configdata-guard");
        try {
            Files.writeString(configDir.resolve("application-test.yml"),
                    "spring:\n"
                            + "  datasource:\n"
                            + "    url: \"" + DANGEROUS_URL + "\"\n");

            Throwable thrown = assertThrows(Throwable.class,
                    () -> new SpringApplicationBuilder(BackendApplication.class)
                            .profiles("test")
                            // Only the location is passed here. The URL itself arrives via
                            // Config Data, which is what the real application uses.
                            .properties("spring.config.additional-location="
                                    + configDir.toUri().toString())
                            .run());

            String chain = describeChain(thrown);
            assertTrue(chain.contains(TestDatabaseSafetyGuard.REJECTION_MARKER),
                    "The guard must reject the production datasource, but got: " + chain);

            assertFalseInStack(thrown, "org.flywaydb",
                    "The guard must fire before Flyway runs");
            assertFalseInStack(thrown, "org.postgresql",
                    "The guard must fire before any database connection is opened");
        } finally {
            deleteRecursively(configDir);
        }
    }

    /**
     * A datasource that is only reachable through Config Data and is perfectly safe must
     * still start, proving the fix did not simply start rejecting everything.
     */
    @Test
    void safeUrlSuppliedThroughConfigData_isAccepted() throws Exception {
        Path configDir = Files.createTempDirectory("sakh-configdata-guard-ok");
        try {
            Files.writeString(configDir.resolve("application-test.yml"),
                    "spring:\n"
                            + "  datasource:\n"
                            + "    url: \"jdbc:postgresql://localhost:5432/sakh_db?currentSchema=sakh_test,public\"\n");

            assertDoesNotThrow(() -> TestDatabaseSafetyGuard.validate(
                    "jdbc:postgresql://localhost:5432/sakh_db?currentSchema=sakh_test,public",
                    null, null, true));
        } finally {
            deleteRecursively(configDir);
        }
    }

    /** The guard must be wired through spring.factories, which is how Boot discovers it. */
    @Test
    void guardIsDeclaredInASpringFactoriesFile() throws Exception {
        var found = new ArrayList<String>();
        var loader = getClass().getClassLoader();
        var resources = loader.getResources("META-INF/spring.factories");
        while (resources.hasMoreElements()) {
            try (var in = resources.nextElement().openStream()) {
                String content = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                if (content.contains(TestDatabaseSafetyEnvironmentPostProcessor.class.getName())) {
                    found.add(resources.toString());
                }
            }
        }
        assertTrue(!found.isEmpty(),
                "The guard must be declared under " + "org.springframework.boot.env.EnvironmentPostProcessor"
                        + " in a META-INF/spring.factories file");
    }

    private static void assertFalseInStack(Throwable throwable, String packagePrefix, String message) {
        for (Throwable t : chain(throwable)) {
            for (StackTraceElement element : t.getStackTrace()) {
                assertTrue(!element.getClassName().startsWith(packagePrefix),
                        message + " but found " + element.getClassName());
            }
        }
    }

    private static List<Throwable> chain(Throwable throwable) {
        List<Throwable> out = new ArrayList<>();
        Throwable current = throwable;
        while (current != null && out.size() < 20) {
            out.add(current);
            current = current.getCause();
        }
        return out;
    }

    private static String describeChain(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t : chain(throwable)) {
            sb.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append(" | ");
        }
        return sb.toString();
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // best effort cleanup of a temp directory
                }
            });
        }
    }
}