package com.n11.identity.user;

import com.n11.identity.address.Address;
import com.n11.identity.address.AddressRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Slice test for the V2 DDL bindings. Uses Testcontainers Postgres (pgvector/pgvector:pg16)
 * so that the partial unique index on addresses(user_id) WHERE is_default can be verified
 * against a real Postgres instance — H2 does not support partial index WHERE clauses.
 *
 * <p>Flyway runs V1-V4 migrations against the containerized Postgres, providing the same
 * schema as production (including the D-11 partial unique index from V2).
 *
 * <p>Plan 03-04 note: {@code @AutoConfigureTestDatabase(replace = NONE)} is the Testcontainers
 * fallback specified in the plan when H2 rejects partial-index WHERE syntax.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserEntityTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("identity_test")
            .withUsername("identity_user")
            .withPassword("identity_test_password");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Flyway needs the schema placeholders for V3__seed_admin.sql and V1 comment
        registry.add("spring.flyway.placeholders.adminSeedEmail", () -> "admin@example.com");
        registry.add("spring.flyway.placeholders.adminSeedPasswordHash",
                () -> "$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWX");
        registry.add("spring.flyway.placeholders.flyway.schema", () -> "public");
        registry.add("spring.flyway.placeholders.mcpAgentSeedHash", () -> "");
        registry.add("spring.flyway.enabled", () -> "true");
        // Use the public schema — no identity schema isolation needed in the test container
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired AddressRepository addressRepository;

    @Test
    void userPersistsWithPasswordHashColumn() {
        // Roles are seeded by V2 migration (INSERT INTO roles VALUES (1, ROLE_USER), (2, ROLE_ADMIN))
        Role userRole = roleRepository.findByName(Role.NAME_USER).orElseThrow();

        User user = new User(
                UUID.randomUUID(),
                "test@example.com",
                "$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWX",  // BCrypt-shaped placeholder
                "Test User",
                Instant.now()
        );
        user.addRole(userRole);
        User saved = userRepository.save(user);

        User loaded = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getPasswordHash()).startsWith("$2a$10$");
        assertThat(loaded.getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void roleManyToManyJoinTableWorks() {
        // Roles seeded by V2 migration
        Role userRole  = roleRepository.findByName(Role.NAME_USER).orElseThrow();
        Role adminRole = roleRepository.findByName(Role.NAME_ADMIN).orElseThrow();

        User user = new User(
                UUID.randomUUID(),
                "admin@example.com",
                "$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWX",
                "Admin",
                Instant.now()
        );
        user.addRole(userRole);
        user.addRole(adminRole);
        User saved = userRepository.save(user);

        User loaded = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getRoles()).hasSize(2);
        assertThat(loaded.getRoles())
                .extracting(Role::getName)
                .containsExactlyInAnyOrder(Role.NAME_USER, Role.NAME_ADMIN);
    }

    @Test
    void partialUniqueDefaultAddressIsEnforced() {
        // D-11: partial unique index idx_addresses_user_default ON addresses(user_id) WHERE is_default
        // is created by V2 migration — enforced by Postgres, not Hibernate.
        //
        // NOTE: In Postgres, once a constraint violation is raised in a transaction, the entire
        // transaction is aborted and subsequent SQL is rejected. The assertThatThrownBy block
        // catches the DataIntegrityViolationException; we do not execute more SQL after it
        // (the @DataJpaTest transaction is rolled back after the test anyway).
        Role userRole = roleRepository.findByName(Role.NAME_USER).orElseThrow();
        User owner = userRepository.save(buildUser("alice@example.com", userRole));

        // First default address — succeeds
        addressRepository.saveAndFlush(buildAddress(owner.getId(), true));

        // Second default address for SAME user — must fail (D-11 partial-unique).
        assertThatThrownBy(() ->
                addressRepository.saveAndFlush(buildAddress(owner.getId(), true))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    // -------- helpers --------

    private static User buildUser(String email, Role role) {
        User user = new User(
                UUID.randomUUID(),
                email,
                "$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWX",
                "Test User",
                Instant.now()
        );
        user.addRole(role);
        return user;
    }

    private static Address buildAddress(UUID userId, boolean isDefault) {
        return new Address(
                UUID.randomUUID(),
                userId,
                "Ev",
                "Recipient",
                "05551234567",
                "Istanbul",
                "Kadikoy",
                null,
                "Muhurdar Cad. No:42",
                "34710",
                isDefault,
                Instant.now()
        );
    }
}
