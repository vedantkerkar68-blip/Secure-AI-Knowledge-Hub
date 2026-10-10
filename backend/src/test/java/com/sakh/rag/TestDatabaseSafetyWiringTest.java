package com.sakh.rag;

import com.sakh.BackendApplication;
import com.sakh.support.TestDatabaseSafetyGuard;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the guard validates the datasource the application will actually use, rather
 * than a value supplied out-of-band.
 */
class TestDatabaseSafetyWiringTest {

    /**
     * Precedence matters here.
     *
     * <p>{@code SpringApplicationBuilder.properties(...)} is registered as
     * {@code defaultProperties}, which is the LOWEST-precedence property source. A
     * {@code spring.datasource.url} passed that way is overwritten by the
     * {@code application-test.yml} that Config Data loads afterwards.
     *
     * <p>This test pins that behaviour down deliberately: the guard must judge the
     * config-data value (localhost, allowed), so startup succeeds. An earlier version
     * of this test asserted the opposite - it expected a production URL injected through
     * {@code properties()} to be rejected, which only happened because the guard ran
     * before Config Data and therefore inspected a value the application never uses.
     */
    @Test
    void guardValidatesTheConfigDataValueNotAnOutOfBandDefaultProperty() {
        ConfigurableApplicationContext context = null;
        try {
            context = new SpringApplicationBuilder(BackendApplication.class)
                    .profiles("test")
                    .properties("spring.datasource.url=jdbc:postgresql://db.mpqgieodurvgnrfcoipd.supabase.co:5432/postgres")
                    .run();

            // Startup succeeded, which means the guard evaluated the config-data URL
            // (localhost/sakh_db, allowed) and not the injected default property.
            assertTrue(context.isRunning(),
                    "The guard must judge the resolved config-data datasource URL");
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }

    @Test
    void explicitlyAllowedCiDatasourceUrl_isNotRejectedByTheGuard() {
        // The guard itself accepts it; this must not throw. The context is not started
        // here because no such CI host exists locally.
        assertDoesNotThrow(() -> TestDatabaseSafetyGuard.validate(
                "jdbc:postgresql://ci-db.internal:5432/sakh_test",
                "ci-db.internal",
                "sakh_test",
                true));
    }

    /**
     * Covers the missing-datasource fail-closed path, not a rejected target: a
     * non-existent {@code spring.config.name} means no application-test.yml is
     * loaded, so the {@code test} profile has no {@code spring.datasource.url} at
     * all. That must abort startup rather than silently skip validation.
     */
    @Test
    void missingDatasourceUrlUnderTestProfile_failsClosedBeforeAnyDatabaseAccess() {
        Throwable thrown = org.junit.jupiter.api.Assertions.assertThrows(Throwable.class,
                () -> new SpringApplicationBuilder(BackendApplication.class)
                        .profiles("test")
                        .properties("spring.config.name=definitely-not-a-real-config-file")
                        .run());

        // With no application-test.yml loaded the 'test' profile has no datasource URL,
        // which must fail closed rather than silently skip validation.
        assertTrue(stackMentions(thrown, TestDatabaseSafetyGuard.REJECTION_MARKER),
                "A missing datasource under the test profile must fail closed, but got: " + thrown);
        assertFalse(stackMentions(thrown, "org.flywaydb"),
                "The guard must fire before Flyway runs");
        assertFalse(stackMentions(thrown, "org.postgresql"),
                "The guard must fire before any database connection is opened");
    }

    private static boolean stackMentions(Throwable throwable, String needle) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(needle)) {
                return true;
            }
            for (StackTraceElement element : t.getStackTrace()) {
                if (element.getClassName().startsWith(needle)) {
                    return true;
                }
            }
        }
        return false;
    }
}