package com.sakh.rag;

import com.sakh.BackendApplication;
import com.sakh.support.TestDatabaseSafetyGuard;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the guard is actually wired into Spring Boot startup and that it rejects a
 * dangerous datasource <em>before</em> any migration or connection can happen.
 */
class TestDatabaseSafetyWiringTest {

    @Test
    void productionDatasourceUrl_abortsStartupBeforeAnyDatabaseWork() {
        Throwable thrown = assertThrows(Throwable.class, () -> new SpringApplicationBuilder(BackendApplication.class)
                .profiles("test")
                .properties("spring.datasource.url=jdbc:postgresql://db.mpqgieodurvgnrfcoipd.supabase.co:5432/postgres")
                .run());

        String chain = describeChain(thrown);
        assertTrue(chain.contains(TestDatabaseSafetyGuard.REJECTION_MARKER),
                "Startup must be aborted by the test database safety guard, but got: " + chain);

        // Ordering proof: if the guard runs during environment preparation, no
        // Flyway frame can appear in the originating stack trace.
        assertFalse(originatingStackContains(thrown, "org.flywaydb"),
                "The guard must fire before Flyway executes, but Flyway appears in the failure stack");

        // And no datasource could have been opened either.
        assertFalse(originatingStackContains(thrown, "org.postgresql"),
                "The guard must fire before a database connection is opened");
    }

    @Test
    void explicitlyAllowedCiDatasourceUrl_isNotRejectedByTheGuard() {
        // The guard itself accepts it; this must not throw. The context is not started
        // here because no such CI host exists locally.
        assertDoesNotThrow(() -> TestDatabaseSafetyGuard.validate(
                "jdbc:postgresql://ci-db.internal:5432/sakh_test",
                "ci-db.internal",
                "sakh_test"));
    }

    private static boolean originatingStackContains(Throwable throwable, String packagePrefix) {
        for (Throwable t : throwableChain(throwable)) {
            for (StackTraceElement element : t.getStackTrace()) {
                if (element.getClassName().startsWith(packagePrefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Deque<Throwable> throwableChain(Throwable throwable) {
        Deque<Throwable> chain = new ArrayDeque<>();
        Throwable current = throwable;
        while (current != null && chain.size() < 20) {
            chain.addLast(current);
            current = current.getCause();
        }
        return chain;
    }

    private static String describeChain(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t : throwableChain(throwable)) {
            sb.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append(" | ");
        }
        return sb.toString();
    }
}
