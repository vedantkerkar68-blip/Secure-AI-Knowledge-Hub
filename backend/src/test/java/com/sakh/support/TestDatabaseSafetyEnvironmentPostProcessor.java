package com.sakh.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/**
 * Runs {@link TestDatabaseSafetyGuard} during environment preparation.
 *
 * <h2>Ordering</h2>
 * The guard MUST run after {@link ConfigDataEnvironmentPostProcessor}, because that
 * processor is what loads {@code application.yml} and {@code application-test.yml}.
 * Running any earlier means {@code spring.datasource.url} is still unresolved and the
 * guard would silently skip validation.
 *
 * <p>In Spring Boot 3.5.x that class lives in
 * {@code org.springframework.boot.context.config} and declares
 * {@code ORDER = Ordered.HIGHEST_PRECEDENCE + 10}. Ordering is referenced from the
 * constant itself rather than a magic number, so a Spring Boot change that moves the
 * config-data phase breaks this file loudly at compile time instead of silently
 * disabling the guard.
 *
 * <p>Environment post-processing completes before the application context is created,
 * so no {@code DataSource}, {@code Flyway}, or {@code EntityManagerFactory} bean exists
 * at this point. A rejected target therefore never opens a connection and never reaches
 * a migration.
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
                environment.getProperty(TestDatabaseSafetyGuard.ALLOWED_DATABASES_PROPERTY),
                environment.acceptsProfiles(Profiles.of("test")));
    }

    @Override
    public int getOrder() {
        // Immediately after config data has been loaded into the environment.
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
