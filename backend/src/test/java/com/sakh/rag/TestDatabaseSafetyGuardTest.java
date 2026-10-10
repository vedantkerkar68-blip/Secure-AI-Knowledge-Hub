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

    // --- allowed ---

    @Test
    void loopbackDevelopmentDatabase_isAllowed() {
        assertDoesNotThrow(() -> TestDatabaseSafetyGuard.validate(
                "jdbc:postgresql://localhost:5432/sakh_db?currentSchema=sakh_test,public", null, null));
    }

    @Test
    void loopbackTestDatabase_isAllowed() {
        assertDoesNotThrow(() -> TestDatabaseSafetyGuard.validate(
                "jdbc:postgresql://127.0.0.1:5432/sakh_test", null, null));
    }

    @Test
    void loopbackIpv6_isAllowed() {
        assertDoesNotThrow(() -> TestDatabaseSafetyGuard.validate(
                "jdbc:postgresql://[::1]:5432/sakh_db", null, null));
    }

    @Test
    void explicitlyConfiguredCiDatabase_isAllowed() {
        assertDoesNotThrow(() -> TestDatabaseSafetyGuard.validate(
                "jdbc:postgresql://ci-db.internal:5432/sakh_test",
                "ci-db.internal",
                "sakh_test"));
    }

    @Test
    void blankDatasourceUrl_isIgnored() {
        assertDoesNotThrow(() -> TestDatabaseSafetyGuard.validate(null, null, null));
        assertDoesNotThrow(() -> TestDatabaseSafetyGuard.validate("  ", null, null));
    }

    // --- rejected: production hosts ---

    @Test
    void supabaseProductionHost_isRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> TestDatabaseSafetyGuard.validate(
                        "jdbc:postgresql://db.mpqgieodurvgnrfcoipd.supabase.co:5432/postgres?sslmode=require",
                        null, null));
        assertTrue(ex.getMessage().contains(TestDatabaseSafetyGuard.REJECTION_MARKER));
        assertTrue(ex.getMessage().contains("supabase.co"));
    }

    @Test
    void neonProductionHost_isRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> TestDatabaseSafetyGuard.validate(
                        "jdbc:postgresql://ep-long-haze-azpp3lxk-pooler.c-3.ap-southeast-1.aws.neon.tech:5432/neondb?sslmode=require",
                        null, null));
        assertTrue(ex.getMessage().contains("neon.tech"));
    }

    @Test
    void productionHost_cannotBeAllowListed() {
        // Even an explicit allow-list entry must not re-enable a managed provider.
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> TestDatabaseSafetyGuard.validate(
                        "jdbc:postgresql://db.abcdefghij.supabase.co:5432/postgres",
                        "db.abcdefghij.supabase.co",
                        "postgres"));
        assertTrue(ex.getMessage().contains("cannot be allow-listed"));
    }

    @Test
    void aHostMerelyContainingAProviderName_isStillRejected() {
        assertThrows(IllegalStateException.class,
                () -> TestDatabaseSafetyGuard.validate(
                        "jdbc:postgresql://supabase.co.attacker.example:5432/postgres", null, null));
    }

    // --- rejected: unknown host or database ---

    @Test
    void unknownHost_isRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> TestDatabaseSafetyGuard.validate(
                        "jdbc:postgresql://suspicious-host.example:5432/sakh_db", null, null));
        assertTrue(ex.getMessage().contains("not in the test allow-list"));
    }

    @Test
    void unknownDatabaseOnLoopback_isRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> TestDatabaseSafetyGuard.validate(
                        "jdbc:postgresql://localhost:5432/production_db", null, null));
        assertTrue(ex.getMessage().contains("not in the test allow-list"));
    }

    @Test
    void allowListedHostWithUnlistedDatabase_isStillRejected() {
        assertThrows(IllegalStateException.class,
                () -> TestDatabaseSafetyGuard.validate(
                        "jdbc:postgresql://ci-db.internal:5432/should_not_be_here",
                        "ci-db.internal", "sakh_test"));
    }

    @Test
    void unparseableUrl_failsClosed() {
        assertThrows(IllegalStateException.class,
                () -> TestDatabaseSafetyGuard.validate("not-a-jdbc-url", null, null));
    }

    @Test
    void urlWithoutDatabaseName_failsClosed() {
        assertThrows(IllegalStateException.class,
                () -> TestDatabaseSafetyGuard.validate("jdbc:postgresql://localhost:5432/", null, null));
    }
}
