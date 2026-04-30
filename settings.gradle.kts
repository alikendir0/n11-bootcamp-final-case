rootProject.name = "n11-clone"

include(
    "eureka-server",
    "config-server",
    "api-gateway",
    "common-error",
    "common-logging",
    "common-events",
    "common-outbox",
    "identity-service",
    "product-service",
    "inventory-service",
    "service-template",
    "infra-tests"
)

// Gradle 8.x auto-detects gradle/libs.versions.toml at the conventional path and
// creates the `libs` version catalog automatically. An explicit
// versionCatalogs { create("libs") { from(files("gradle/libs.versions.toml")) } }
// triggers "you can only call the 'from' method a single time" because Gradle has
// already imported it once via convention. Keeping this block convention-only.
dependencyResolutionManagement {
    repositories { mavenCentral() }
}
