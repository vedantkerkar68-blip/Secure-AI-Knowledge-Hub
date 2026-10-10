package com.sakh.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Runs {@link TestDatabaseSafetyGuard} during environment preparation.
 *
 * <p>{@code EnvironmentPostProcessor} executes on
 * {@code ApplicationEnvironmentPreparedEvent}, which is the earliest application
 * callback: no {@code DataSource}, no {@code Flyway}, and no JPA
 * {@code EntityManagerFactory} exists yet. That ordering is the whole point - it
 * guarantees a rejected datasource cannot have caused a migration, and cannot even
 * cause a database connection to be opened.
 *
 * <p>Registered through {@code META-INF/spring.factories} on the test classpath only,
 * so the production runtime is unaffected.
 */
public class TestDatabaseSafetyEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        TestDatabaseSafetyGuard.validate(
                environment.getProperty("spring.datasource.url"),
                environment.getProperty(TestDatabaseSafetyGuard.ALLOWED_HOSTS_PROPERTY),
                environment.getProperty(TestDatabaseSafetyGuard.ALLOWED_DATABASES_PROPERTY));
    }

    @Override
    public int getOrder() {
        // Run as early as possible within the environment-preparation phase.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
