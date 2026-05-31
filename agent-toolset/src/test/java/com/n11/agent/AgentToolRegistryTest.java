package com.n11.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 0 gate (AI-05): exactly 10 canonical agent tools registered.
 * Names locked by CLAUDE.md Rule #2 -- duplicates or missing tools fail this gate.
 */
@SpringBootTest(classes = AgentToolRegistryTest.TestApp.class)
class AgentToolRegistryTest {

    private static final Set<String> CANONICAL_NAMES = Set.of(
        "search_products", "get_product", "list_categories",
        "add_to_cart", "view_cart", "update_cart_item", "remove_from_cart",
        "create_order", "get_payment_link", "get_order_status",
        "list_addresses"
    );

    private static final Set<String> MUTATING_NAMES = Set.of(
        "add_to_cart", "view_cart", "update_cart_item", "remove_from_cart",
        "create_order", "get_payment_link", "get_order_status",
        "list_addresses"
    );

    @Autowired ToolRegistry registry;

    @Test
    void registry_contains_exactly_11_canonical_tools() {
        List<AgentTool> all = registry.all();
        assertThat(all).hasSize(11);
        assertThat(all).extracting(AgentTool::name).containsExactlyInAnyOrderElementsOf(CANONICAL_NAMES);
    }

    @Test
    void mutating_tools_require_auth() {
        for (AgentTool t : registry.all()) {
            if (MUTATING_NAMES.contains(t.name())) {
                assertThat(t.requiresAuth()).as("%s must require auth (D-04)", t.name()).isTrue();
            } else {
                assertThat(t.requiresAuth()).as("%s must NOT require auth", t.name()).isFalse();
            }
        }
    }

    @Test
    void every_tool_has_a_turkish_description() {
        for (AgentTool t : registry.all()) {
            assertThat(t.descriptionTr())
                .as("%s description must be non-blank", t.name())
                .isNotBlank();
        }
    }

    @Configuration
    @ComponentScan(basePackages = "com.n11.agent")
    static class TestApp { }
}
