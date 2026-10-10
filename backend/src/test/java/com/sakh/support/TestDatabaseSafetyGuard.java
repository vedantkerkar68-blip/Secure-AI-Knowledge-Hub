package com.sakh.support;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * Fail-fast safety guard for the test database.
 *
 * <p>Schema isolation confines <em>application tables</em> to {@code sakh_test}, but it
 * does not confine <em>database-level</em> operations. Flyway's
 * {@code CREATE EXTENSION IF NOT EXISTS vector} is database-scoped, so pointing the
 * suite at a production database would still mutate that database even though every
 * migration object lands in the isolated schema.
 *
 * <p>This guard therefore validates the effective JDBC host and database name and
 * refuses to let the context start when they point anywhere that is not explicitly a
 * test target. It is wired in ahead of Flyway (see
 * {@link TestDatabaseSafetyEnvironmentPostProcessor}) so a rejection happens before any
 * database connection is opened.
 *
 * <p>Policy, in order:
 * <ol>
 *   <li>Hosts matching a known production provider are always rejected and can never be
 *       allow-listed.</li>
 *   <li>The host must be in the configured allow-list (loopback by default).</li>
 *   <li>The database name must be in the configured allow-list.</li>
 * </ol>
 *
 * <p>Unparseable input is rejected: the guard fails closed.
 */
public final class TestDatabaseSafetyGuard {

    /** Comma-separated hosts that are always accepted. Loopback only. */
    public static final String ALLOWED_HOSTS_PROPERTY = "sakh.test-db.allowed-hosts";

    /** Comma-separated database names that are always accepted. */
    public static final String ALLOWED_DATABASES_PROPERTY = "sakh.test-db.allowed-databases";

    /** Prefix used in every rejection message so it is greppable in CI logs. */
    public static final String REJECTION_MARKER = "Test database safety guard";

    static final List<String> DEFAULT_ALLOWED_HOSTS = List.of("localhost", "127.0.0.1", "::1");

    /**
     * Local development databases. The suite normally runs against {@code sakh_db} using
     * the isolated {@code sakh_test} schema.
     */
    static final List<String> DEFAULT_ALLOWED_DATABASES = List.of("sakh_db", "sakh_test");

    /**
     * Host suffixes of managed production providers. These are rejected unconditionally
     * and are deliberately NOT overridable through {@link #ALLOWED_HOSTS_PROPERTY}, so a
     * copy-pasted CI allow-list entry cannot re-enable a production target.
     */
    static final List<String> PRODUCTION_HOST_SUFFIXES = List.of(
            "supabase.co",
            "neon.tech",
            "onrender.com",
            "railway.app",
            "herokuapp.com",
            "azurewebsites.net",
            "rds.amazonaws.com",
            "elephantsql.com",
            "cockroachlabs.com",
            "planetscale.com");

    private TestDatabaseSafetyGuard() {
    }

    /**
     * Validates the effective JDBC URL.
     *
     * @param jdbcUrl          the resolved {@code spring.datasource.url}
     * @param allowedHosts     comma-separated host allow-list, or {@code null} for the default
     * @param allowedDatabases comma-separated database allow-list, or {@code null} for the default
     * @throws IllegalStateException if the URL does not identify an approved test database
     */
    public static void validate(String jdbcUrl, String allowedHosts, String allowedDatabases) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            // No datasource configured (e.g. a web slice test). Nothing to validate.
            return;
        }

        Target target = Target.parse(jdbcUrl);

        if (target.host == null || target.database == null || target.database.isBlank()) {
            throw reject(jdbcUrl, "could not parse an effective host and database name from it");
        }

        String host = target.host.toLowerCase(Locale.ROOT);

        String productionProvider = matchProductionProvider(host);
        if (productionProvider != null) {
            throw reject(jdbcUrl,
                    "host '" + host + "' belongs to the managed production provider '" + productionProvider
                            + "'; production hosts are rejected unconditionally and cannot be allow-listed");
        }

        List<String> hosts = merge(allowedHosts, DEFAULT_ALLOWED_HOSTS);
        if (!containsIgnoreCase(hosts, host)) {
            throw reject(jdbcUrl, "host '" + host + "' is not in the test allow-list " + hosts
                    + ". A CI test database must be declared explicitly via " + ALLOWED_HOSTS_PROPERTY);
        }

        List<String> databases = merge(allowedDatabases, DEFAULT_ALLOWED_DATABASES);
        if (!containsIgnoreCase(databases, target.database)) {
            throw reject(jdbcUrl, "database '" + target.database + "' is not in the test allow-list " + databases
                    + ". A CI test database must be declared explicitly via " + ALLOWED_DATABASES_PROPERTY);
        }
    }

    private static String matchProductionProvider(String host) {
        for (String suffix : PRODUCTION_HOST_SUFFIXES) {
            if (host.equals(suffix) || host.endsWith("." + suffix)) {
                return suffix;
            }
        }
        return null;
    }

    private static List<String> merge(String configured, List<String> defaults) {
        if (configured == null || configured.isBlank()) {
            return defaults;
        }
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>();
        for (String part : configured.split(",")) {
            String trimmed = part.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                merged.add(trimmed);
            }
        }
        merged.addAll(defaults);
        return List.copyOf(merged);
    }

    private static boolean containsIgnoreCase(List<String> values, String candidate) {
        return values.stream().anyMatch(v -> v.equalsIgnoreCase(candidate));
    }

    private static IllegalStateException reject(String jdbcUrl, String reason) {
        return new IllegalStateException(
                REJECTION_MARKER + " rejected the test datasource before any migration ran: " + reason
                        + " (url=" + jdbcUrl + ")."
                        + " Tests must never target a production database."
                        + " If this is a legitimate isolated CI test database, declare it explicitly via "
                        + ALLOWED_HOSTS_PROPERTY + " and " + ALLOWED_DATABASES_PROPERTY + ".");
    }

    /** Effective host and database extracted from a PostgreSQL JDBC URL. */
    record Target(String host, String database) {

        static Target parse(String jdbcUrl) {
            String url = jdbcUrl.trim();
            if (url.startsWith("jdbc:")) {
                url = url.substring("jdbc:".length());
            }
            try {
                URI uri = URI.create(url);
                String host = uri.getHost();
                if (host == null && uri.getAuthority() != null) {
                    host = uri.getAuthority();
                }
                if (host != null) {
                    // URI keeps IPv6 literals bracketed.
                    if (host.startsWith("[") && host.endsWith("]")) {
                        host = host.substring(1, host.length() - 1);
                    }
                    // Strip any port that leaked into a fallback authority parse.
                    int colon = host.lastIndexOf(':');
                    if (colon > 0 && host.indexOf(':') == colon) {
                        host = host.substring(0, colon);
                    }
                }
                String path = uri.getPath();
                String database = null;
                if (path != null && path.length() > 1) {
                    database = path.substring(1);
                }
                return new Target(host, database);
            } catch (RuntimeException e) {
                return new Target(null, null);
            }
        }
    }
}
