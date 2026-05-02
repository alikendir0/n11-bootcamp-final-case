package com.n11.identity.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.identity.agent.dto.AgentExchangeRequest;
import com.n11.identity.user.User;
import com.n11.identity.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class AgentExchangeControllerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                            .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("n11")
                    .withUsername("identity_user")
                    .withPassword("test-password");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired private WebApplicationContext context;
    @Autowired private AgentApiKeyRepository agentRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private UUID seededUserId;
    private String demoPlaintextKey;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        User u = userRepo.findByEmail("agent-test@n11demo.com").orElseGet(() -> userRepo.save(new User(
                UUID.randomUUID(),
                "agent-test@n11demo.com",
                "$2a$10$abcdefghijklmnopqrstuuvA1234567890abcdefghijklmno",
                "Agent Test User",
                Instant.now()
        )));
        seededUserId = u.getId();

        demoPlaintextKey = "demo-key-" + UUID.randomUUID();
        agentRepo.save(new AgentApiKey(
                AgentExchangeService.sha256Hex(demoPlaintextKey),
                "demo-agent",
                seededUserId,
                Instant.now()));

        String revokedPlaintextKey = "revoked-key-" + UUID.randomUUID();
        AgentApiKey revoked = new AgentApiKey(
                AgentExchangeService.sha256Hex(revokedPlaintextKey),
                "revoked-agent",
                seededUserId,
                Instant.now());
        revoked.revoke(Instant.now());
        agentRepo.save(revoked);
    }

    @Test
    void valid_apiKey_returns_200_and_jwt_bound_to_real_user_id() throws Exception {
        String body = objectMapper.writeValueAsString(new AgentExchangeRequest(demoPlaintextKey));

        var result = mockMvc.perform(post("/agents/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.expiresIn").value(86400))
                .andReturn();

        JsonNode resp = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = resp.get("accessToken").asText();
        String[] parts = accessToken.split("\\.");
        assertThat(parts).hasSize(3);
        byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
        JsonNode claims = objectMapper.readTree(decoded);
        assertThat(claims.get("sub").asText()).isEqualTo(seededUserId.toString());
        assertThat(claims.get("roles").get(0).asText()).isEqualTo("ROLE_USER");
    }

    @Test
    void unknown_apiKey_returns_401() throws Exception {
        String body = objectMapper.writeValueAsString(new AgentExchangeRequest("nope-not-a-real-key"));

        mockMvc.perform(post("/agents/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void empty_apiKey_returns_400() throws Exception {
        mockMvc.perform(post("/agents/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missing_apiKey_returns_400() throws Exception {
        mockMvc.perform(post("/agents/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
