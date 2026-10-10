package com.sakh.rag;

import com.sakh.support.TestDatabaseSafetyGuard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Policy coverage for {@link TestDatabaseSafetyGuard}: which datasource targets are
 * accepted and which are refused.
 */
class TestDatabaseSafetyGuardTest {

    private static final boolean TEST_PROFILE = true;
    private static final boolean NO_TEST_PROFILE = false;

    /** Convenience: validate as if the test profile were active. */
    private static void validate(String url, String hosts, String databases) {
        TestDatabaseSafetyGuard.validate(url, hosts, databases, TEST_PROFILE);
    }

    // --- allowed ---

    @Test
    void loopbackDevelopmentDatabase_isAllowed() {
        assertDoesNotThrow(() -> validate(
                "jdbc:postgresql://localhost:5432/sakh_db?currentSchema=sakh_test,public", null, null));
    }

    @Test
    void loopbackTestDatabase_isAllowed() {
        assertDoesNotThrow(() -> validate(
                "jdbc:postgresql://127.0.0.1:5432/sakh_test", null, null));
    }

    @Test
    void loopbackIpv6_isAllowed() {
        assertDoesNotThrow(() -> validate(
                "jdbc:postgresql://[::1]:5432/sakh_db", null, null));
    }

    @Test
    void explicitlyConfiguredCiDatabase_isAllowed() {
        assertDoesNotThrow(() -> validate(
                "jdbc:postgresql://ci-db.internal:5432/sakh_test",
                "ci-db.internal",
                "sakh_test"));
    }

    // --- missing URL ---

    @Test
    void missingUrlWithTestProfileActive_failsClosed() {
        // The dangerous case: a full test application with no resolvable datasource must
        // not silently bypass the guard.
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validate(null, null, null));
        assertTrue(ex.getMessage().contains(TestDatabaseSafetyGuard.REJECTION_MARKER));
        assertTrue(ex.getMessage().contains("resolved to nothing"));
    }

    @Test
    void blankUrlWithTestProfileActive_failsClosed() {
        assertThrows(IllegalStateException.class, () -> validate("   ", null, null));
    }

    @Test
    void missingUrlOutsideTestProfile_isIgnored() {
        // A slice or non-test context that genuinely configures no datasource.
        assertDoesNotThrow(() -> TestDatabaseSafetyGuard.validate(null, null, null, NO_TEST_PROFILE));
        assertDoesNotThrow(() -> TestDatabaseSafetyGuard.validate("  ", null, null, NO_TEST_PROFILE));
    }

    // --- rejected: production hosts ---

    @Test
    void supabaseProductionHost_isRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validate("jdbc:postgresql://db.mpqgieodurvgnrfcoipd.supabase.co:5432/postgres?sslmode=require",
                        null, null));
        assertTrue(ex.getMessage().contains(TestDatabaseSafetyGuard.REJECTION_MARKER));
        assertTrue(ex.getMessage().contains("supabase.co"));
    }

    @Test
    void neonProductionHost_isRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validate(
                        "jdbc:postgresql://ep-long-haze-azpp3lxk-pooler.c-3.ap-southeast-1.aws.neon.tech:5432/neondb?sslmode=require",
                        null, null));
        assertTrue(ex.getMessage().contains("neon.tech"));
    }

    @Test
    void productionHost_cannotBeAllowListed() {
        // Even an explicit allow-list entry must not re-enable a managed provider.
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validate("jdbc:postgresql://db.abcdefghij.supabase.co:5432/postgres",
                        "db.abcdefghij.supabase.co",
                        "postgres"));
        assertTrue(ex.getMessage().contains("cannot be allow-listed"));
    }

    @Test
    void aHostMerelyContainingAProviderName_isStillRejected() {
        assertThrows(IllegalStateException.class,
                () -> validate("jdbc:postgresql://supabase.co.attacker.example:5432/postgres", null, null));
    }

    // --- rejected: unknown host or database ---

    @Test
    void unknownHost_isRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validate("jdbc:postgresql://suspicious-host.example:5432/sakh_db", null, null));
        assertTrue(ex.getMessage().contains("not in the test allow-list"));
    }

    @Test
    void unknownDatabaseOnLoopback_isRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validate("jdbc:postgresql://localhost:5432/production_db", null, null));
        assertTrue(ex.getMessage().contains("not in the test allow-list"));
    }

    @Test
    void allowListedHostWithUnlistedDatabase_isStillRejected() {
        assertThrows(IllegalStateException.class,
                () -> validate("jdbc:postgresql://ci-db.internal:5432/should_not_be_here",
                        "ci-db.internal", "sakh_test"));
    }

    @Test
    void unparseableUrl_failsClosed() {
        assertThrows(IllegalStateException.class,
                () -> validate("not-a-jdbc-url", null, null));
    }

    @Test
    void urlWithoutDatabaseName_failsClosed() {
        assertThrows(IllegalStateException.class,
                () -> validate("jdbc:postgresql://localhost:5432/", null, null));
    }
}