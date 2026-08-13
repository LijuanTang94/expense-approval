package com.sandy.expense.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for tests that need the real thing: a Postgres instance, Flyway migrations applied to
 * it, and the full Spring context wired on top.
 *
 * <p>The unit tests elsewhere mock their repositories, which makes them fast but blind to an entire
 * class of failure — a migration that doesn't apply, an entity that no longer matches the schema
 * (`ddl-auto: validate` only speaks up when a real database is present), a malformed {@code @Query},
 * or a {@code @PreAuthorize} expression that doesn't parse. All of those pass a mocked suite and
 * fail on startup in production.
 *
 * <p>One container is shared by every subclass: it is started once per JVM and reused, so the cost
 * is paid a single time rather than per test class.
 *
 * <p>That sharing is why the container is started by hand instead of with {@code @Testcontainers}
 * and {@code @Container}. Those manage the lifecycle <em>per test class</em> — the container is
 * stopped once the first class finishes. Spring, meanwhile, caches the application context and
 * hands the same one to the next class, so the second class would inherit a DataSource pointing at
 * a port nothing is listening on any more, and every query would fail on connection timeout rather
 * than on anything to do with the test. Starting it in a static initialiser and never stopping it
 * keeps the container alive as long as the cached context that refers to it; Testcontainers' Ryuk
 * sidecar removes it when the JVM exits.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
