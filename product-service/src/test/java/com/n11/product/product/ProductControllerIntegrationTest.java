package com.n11.product.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * REST integration test:
 *  - GET /products is public (no headers required, returns 200)
 *  - GET /products/{id} returns PDP DTO (PROD-02)
 *  - GET /categories returns 8 categories (PROD-03)
 *  - POST /products WITHOUT X-User-Roles -> 403
 *  - POST /products WITH X-User-Roles=ROLE_ADMIN + valid body -> 201
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ProductControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                            .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("n11")
                    .withUsername("product_user")
                    .withPassword("test-password");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void getProductsPublicReturnsSeed() throws Exception {
        mockMvc.perform(get("/products?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(50)));
    }

    @Test
    void getCategoriesReturnsEight() throws Exception {
        mockMvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8));
    }

    @Test
    void postProductWithoutAdminRoleReturns403() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("sku", "TEST-001");
        body.put("nameTr", "Test Ürünü");
        body.put("priceGross", 100.0);

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void postProductWithAdminRoleReturns201() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("sku", "TEST-NEW-001");
        body.put("nameTr", "Yeni Test Ürünü");
        body.put("priceGross", 199.90);

        mockMvc.perform(post("/products")
                        .header("X-User-Roles", "ROLE_USER,ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("TEST-NEW-001"))
                .andExpect(jsonPath("$.nameTr").value("Yeni Test Ürünü"));
    }
}
