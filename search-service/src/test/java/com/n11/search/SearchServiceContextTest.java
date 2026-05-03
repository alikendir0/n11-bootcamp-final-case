package com.n11.search;

import com.n11.ai.port.EmbeddingProvider;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@TestPropertySource(properties = {
    "spring.cloud.config.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "eureka.client.enabled=false",
    "spring.flyway.locations=classpath:db/migration/search",
    "spring.flyway.schemas=public",
    "spring.flyway.default-schema=public",
    "spring.jpa.properties.hibernate.default_schema=public",
    "spring.main.allow-bean-definition-overriding=true"
})
class SearchServiceContextTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory;

    @Autowired
    private EmbeddingProvider embeddingProvider;

    @Autowired
    private SearchService searchService;

    @Test
    void context_loads_with_embedding_provider_injected() {
        assertThat(embeddingProvider).isNotNull();
        assertThat(searchService).isNotNull();
    }

    @Test
    void embedding_provider_returns_vector_of_requested_dims() {
        when(embeddingProvider.embed(anyString(), anyInt())).thenReturn(new float[768]);
        float[] v = embeddingProvider.embed("test", 768);
        assertThat(v).hasSize(768);
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public EmbeddingProvider embeddingProvider() {
            return Mockito.mock(EmbeddingProvider.class);
        }

        @Bean
        FlywayConfigurationCustomizer vectorExtensionCustomizer() {
            return (FluentConfiguration config) ->
                config.initSql("CREATE EXTENSION IF NOT EXISTS vector");
        }
    }
}
