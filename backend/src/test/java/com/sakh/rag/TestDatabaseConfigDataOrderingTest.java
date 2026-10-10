package com.sakh.rag;

import com.sakh.BackendApplication;
import com.sakh.support.TestDatabaseSafetyEnvironmentPostProcessor;
import com.sakh.support.TestDatabaseSafetyGuard;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
     * A safe datasource supplied through a temporary Config Data file must be accepted
     * by the guard, and the decision must demonstrably come from that file.
     *
     * <p>The file declares both a database name that is NOT in the default allow-list
     * and an explicit allow-list entry for it, so startup can only succeed if the guard
     * read the URL <em>and</em> the allow-list from Config Data. The companion test
     * {@link #safeUrlWithoutAnAllowListEntry_isRejectedFromConfigData()} proves the URL
     * in that file is genuinely the value being judged.
     *
     * <p>A minimal application source is used instead of the real one so no
     * DataSource, Flyway, or JPA bean is created: the guard runs during environment
     * preparation regardless, and no database has to be available for the test to be
     * meaningful. This keeps the test about the guard rather than about whether the
     * whole application can boot.
     */
    @Test
    void safeUrlSuppliedThroughConfigData_isAccepted() throws Exception {
        Path configDir = Files.createTempDirectory("sakh-configdata-guard-ok");
        try {
            Files.writeString(configDir.resolve("application-test.yml"),
                    "spring:\n"
                            + "  datasource:\n"
                            + "    url: \"jdbc:postgresql://localhost:5432/ci_isolated_test_db\"\n"
                            + "sakh:\n"
                            + "  test-db:\n"
                            + "    allowed-databases: \"ci_isolated_test_db\"\n");

            ConfigurableApplicationContext context = null;
            try {
                context = new SpringApplicationBuilder(GuardProbeApp.class)
                        .web(WebApplicationType.NONE)
                        .profiles("test")
                        .properties("spring.config.additional-location=" + configDir.toUri())
                        .run();

                assertTrue(context.isRunning(),
                        "The guard must accept a safe, explicitly allow-listed datasource from Config Data");
                assertTrue(context.getEnvironment()
                                .getProperty("spring.datasource.url")
                                .contains("ci_isolated_test_db"),
                        "The temporary Config Data file must be the source of the datasource URL");
            } finally {
                if (context != null) {
                    context.close();
                }
            }
        } finally {
            deleteRecursively(configDir);
        }
    }

    /**
     * Negative control for {@link #safeUrlSuppliedThroughConfigData_isAccepted()}: the
     * same file shape, minus the allow-list entry, must be rejected. This is what proves
     * the guard is judging the URL from the temporary Config Data file rather than some
     * default that happens to be acceptable.
     */
    @Test
    void safeUrlWithoutAnAllowListEntry_isRejectedFromConfigData() throws Exception {
        Path configDir = Files.createTempDirectory("sakh-configdata-guard-unlisted");
        try {
            Files.writeString(configDir.resolve("application-test.yml"),
                    "spring:\n"
                            + "  datasource:\n"
                            + "    url: \"jdbc:postgresql://localhost:5432/ci_isolated_test_db\"\n");

            Throwable thrown = assertThrows(Throwable.class,
                    () -> new SpringApplicationBuilder(GuardProbeApp.class)
                            .web(WebApplicationType.NONE)
                            .profiles("test")
                            .properties("spring.config.additional-location=" + configDir.toUri())
                            .run());

            assertTrue(describeChain(thrown).contains(TestDatabaseSafetyGuard.REJECTION_MARKER),
                    "The guard must reject a database that is not allow-listed, but got: "
                            + describeChain(thrown));
            assertTrue(describeChain(thrown).contains("ci_isolated_test_db"),
                    "The rejection must name the database read from the Config Data file: "
                            + describeChain(thrown));
        } finally {
            deleteRecursively(configDir);
        }
    }

    /**
     * Minimal application source. It deliberately has no auto-configuration, so the
     * context contains no DataSource, Flyway, or JPA beans and needs no database - while
     * the environment post-processors, including the guard, still run.
     */
    @org.springframework.context.annotation.Configuration
    static class GuardProbeApp {
    }

    /** The guard must be wired through spring.factories, which is how Boot discovers it. */
    @Test
    void guardIsDeclaredUnderTheEnvironmentPostProcessorFactoryKey() throws Exception {
        String guardClassName = TestDatabaseSafetyEnvironmentPostProcessor.class.getName();
        String factoryKey = "org.springframework.boot.env.EnvironmentPostProcessor";

        var declarations = new ArrayList<String>();
        var loader = getClass().getClassLoader();
        var resources = loader.getResources("META-INF/spring.factories");
        while (resources.hasMoreElements()) {
            var url = resources.nextElement();
            try (var in = url.openStream()) {
                String content = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                if (content.contains(guardClassName)) {
                    declarations.add(url + " -> " + content.strip());
                }
            }
        }

        assertTrue(!declarations.isEmpty(),
                "The guard must be declared in a META-INF/spring.factories file, but found: " + declarations);

        // Declaring the class name is not enough: Spring Boot only instantiates it when it
        // is listed under the EnvironmentPostProcessor factory key.
        for (String declaration : declarations) {
            assertTrue(declaration.contains(factoryKey),
                    "The guard must be declared under the '" + factoryKey
                            + "' factory key, but the declaration was: " + declaration);
        }
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