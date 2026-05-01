package com.n11.search;

import com.n11.ai.port.EmbeddingProvider;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QUAL-08 / D-09 gate: search-service Spring context loads + EmbeddingProvider
 * is wired. No /search endpoint test in v1 (skeleton only).
 *
 * Uses pgvector/pgvector:pg16 image so the V1 'vector(768)' migration applies.
 * FlywayVectorCustomizer ensures CREATE EXTENSION vector runs before migrations
 * (pgvector image ships the extension but does not enable it in the test DB).
 */
@SpringBootTest(classes = {SearchServiceApplication.class, SearchServiceContextTest.FlywayVectorConfig.class},
                webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@TestPropertySource(properties = {
    "spring.cloud.config.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "eureka.client.enabled=false",
    "spring.flyway.locations=classpath:db/migration/search",
    "spring.flyway.schemas=public",
    "spring.flyway.default-schema=public",
    "spring.jpa.properties.hibernate.default_schema=public"
})
class SearchServiceContextTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired EmbeddingProvider embeddingProvider;
    @Autowired SearchService searchService;

    @Test
    void context_loads_with_embedding_provider_injected() {
        assertThat(embeddingProvider).isNotNull();
        assertThat(searchService).isNotNull();
    }

    @Test
    void embedding_provider_returns_vector_of_requested_dims() {
        float[] v = embeddingProvider.embed("test", 768);
        assertThat(v).hasSize(768);
    }

    /**
     * Ensures the pgvector extension is enabled before Flyway runs the V1 migration
     * that references the 'vector' type. pgvector/pgvector:pg16 ships the extension
     * but it must be activated per-database.
     */
    @TestConfiguration
    static class FlywayVectorConfig {
        @Bean
        FlywayConfigurationCustomizer vectorExtensionCustomizer() {
            return (FluentConfiguration config) ->
                config.initSql("CREATE EXTENSION IF NOT EXISTS vector");
        }
    }
}
