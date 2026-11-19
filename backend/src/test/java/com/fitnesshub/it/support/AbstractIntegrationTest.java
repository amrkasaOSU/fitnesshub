package com.fitnesshub.it.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

/**
 * Boots the whole application against a real PostgreSQL and a real Redis, then
 * talks to it over HTTP exactly as the browser does - session cookie, CSRF
 * double-submit and all. Nothing is mocked, so these tests exercise the same
 * Flyway migrations, security filter chain and Redis-backed sessions that
 * production runs.
 *
 * <p>The containers are started once per JVM and shared by every test class
 * (the "singleton container" pattern), and Spring caches the application
 * context across classes, so the suite pays the startup cost a single time.
 * The demo seeder runs on that first boot, which gives every test a coach with
 * five clients to act on; tests that need isolation register their own users.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    public static final String DEMO_PASSWORD = "FitnessHub!2024";
    public static final String COACH_EMAIL = "coach@fitnesshub.local";

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("fitnesshub")
                    .withUsername("fitnesshub")
                    .withPassword("fitnesshub_test_password");

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.url",
                () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        // No provider key: the AI endpoints must degrade to "not configured".
        registry.add("fitnesshub.ai.openai.api-key", () -> "");
    }

    @LocalServerPort
    protected int port;

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    /** A browser that has never signed in. */
    protected ApiSession anonymous() {
        return new ApiSession(baseUrl());
    }

    protected ApiSession loginAs(String email, String password) {
        ApiSession session = new ApiSession(baseUrl());
        ApiResult result = session.login(email, password);
        if (result.status() != 200) {
            throw new AssertionError("login as " + email + " failed: " + result);
        }
        return session;
    }

    protected ApiSession demoCoach() {
        return loginAs(COACH_EMAIL, DEMO_PASSWORD);
    }

    /** One of the five seeded clients, {@code client1}..{@code client5}. */
    protected ApiSession demoClient(int n) {
        return loginAs("client" + n + "@fitnesshub.local", DEMO_PASSWORD);
    }

    /** Registers a brand-new coach with no clients, and returns their signed-in session. */
    protected ApiSession registerCoach() {
        ApiSession session = new ApiSession(baseUrl());
        ApiResult result = session.register(uniqueEmail("coach"), DEMO_PASSWORD, "Test", "Coach", "COACH", null);
        if (result.status() != 201) {
            throw new AssertionError("coach registration failed: " + result);
        }
        return session;
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 12) + "@it.fitnesshub.local";
    }
}
