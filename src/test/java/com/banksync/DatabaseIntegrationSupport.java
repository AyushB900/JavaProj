package com.banksync;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
/** Optional PostgreSQL override shared by all database integration tests. */
abstract class DatabaseIntegrationSupport {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("TEST_DB_URL");
        if (url != null && !url.isBlank()) {
            properties.add("spring.datasource.url", () -> url);
            properties.add("spring.datasource.username", () -> System.getenv("TEST_DB_USER"));
            properties.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
            properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        }
    }
}
