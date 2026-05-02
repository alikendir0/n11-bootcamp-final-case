# Jenkins versus GitHub Actions — Pipeline Comparison

> **Scope:** This document satisfies requirement **DEV-04** (Jenkins comparison — pipeline-logic understanding).  
> **Status:** Illustrative documentation only. No Jenkins runtime is added to the repository; the project builds and deploys using GitHub Actions exclusively.

---

## 1. Pipeline Stages

Both CI/CD systems execute the same logical stages for this project. The table below maps each stage to its purpose and the concrete files it touches.

| Stage | Purpose | GitHub Actions Job | Jenkins Stage |
|-------|---------|-------------------|---------------|
| **Checkout** | Clone the repository at the triggering ref | `actions/checkout@v4` | `checkout scm` |
| **Setup Java 21** | Install Corretto JDK for Gradle builds | `actions/setup-java@v4` (distribution: corretto, java-version: '21') | `tool name: 'jdk-21-corretto', type: 'jdk'` |
| **Setup Node 24** | Install Node.js for the frontend Vite build | `actions/setup-node@v4` (node-version: '24') | `tool name: 'node-24', type: 'jenkins.plugins.nodejs.tools.NodeJSInstallation'` |
| **Build Backend** | Compile, test, and assemble all 13 Spring Boot services via Gradle | `./gradlew build --no-daemon` | `./gradlew build --no-daemon` |
| **Infra Tests** | Run boundary/architecture smoke tests (Testcontainers, ArchUnit, cross-schema deny) | `./gradlew :infra-tests:test --no-daemon` | `./gradlew :infra-tests:test --no-daemon` |
| **Build Frontend** | Install npm deps, build Vite production bundle, run Vitest, lint invariants, Playwright E2E chat smoke | `npm ci && npm run build && npm test && npm run lint:invariants && npm run test:e2e -- chat-assistant.spec.ts` | Same shell steps inside a `node` block |
| **Publish Images on Tag** | On `v*` release tags, build and push 13 Jib images to GHCR with the tag and `latest` | Matrix job: `gradle :<service>:jib` with `docker/login-action@v3` to `ghcr.io` | Parallel stages or a scripted matrix loop over the 13 services, each calling `./gradlew :<service>:jib` after `docker login ghcr.io` |
| **Notify Slack** | Post success/failure message to Slack webhook | Conditional curl POST using `secrets.SLACK_WEBHOOK_URL` | Conditional curl POST using a Jenkins Credentials secret |

---

## 2. GitHub Actions Implementation

The project uses two workflow files:

- `.github/workflows/ci.yml` — triggered on `push` and `pull_request` to `main`, `master`, or `develop`.
- `.github/workflows/release.yml` — triggered on `push` of `v*` tags (and `workflow_dispatch`).

### 2.1 CI Workflow (`ci.yml`)

- **Permissions:** `contents: read` (minimal).
- **Jobs:**
  1. `build` — checkout, setup Java 21, Gradle cache, `./gradlew build`.
  2. `infra-tests` — depends on `build`; runs `./gradlew :infra-tests:test`.
  3. `frontend` — checkout, setup Node 24, `npm ci`, `npm run build`, `npm test`, `npm run lint:invariants`, E2E chat smoke.
  4. `notify` — depends on all three; runs `always()` so it fires even if a previous job failed; posts green/red Slack message.

### 2.2 Release Workflow (`release.yml`)

- **Permissions:** `contents: read`, `packages: write` (required for GHCR push).
- **Strategy:** matrix of 13 services (`eureka-server` through `mcp-server`).
- **Steps:** checkout, setup Java 21, Gradle cache, `docker/login-action@v3` to `ghcr.io`, `./gradlew :<service>:jib` with `-Djib.to.image` set to `ghcr.io/<owner>/<repo>/<service>:<tag>` and `-Djib.to.tags=latest`.
- **Notify:** `notify-release` job depends on `publish`; posts green/red Slack message.

### 2.3 No AWS / No OIDC

The project deploys to the **candidate's local machine** via `docker compose --profile full up`. There is no AWS account, no EKS, no Elastic Beanstalk, and no OIDC provider configuration. Container images are published to **GitHub Container Registry (GHCR)**, which is natively integrated with GitHub Actions via the automatically provided `GITHUB_TOKEN` secret.

---

## 3. Equivalent Jenkinsfile Sketch

The following declarative `Jenkinsfile` illustrates how the same pipeline stages would be expressed in Jenkins. **This file is documentation only and is not part of the repository.**

```groovy
// Jenkinsfile — ILLUSTRATIVE DOCUMENTATION ONLY
// Not executed by this project; included to satisfy DEV-04 pipeline-logic comparison.

pipeline {
    agent any

    tools {
        jdk 'jdk-21-corretto'
        nodejs 'node-24'
    }

    environment {
        GRADLE_OPTS = '-Dorg.gradle.daemon=false'
        // Secrets are injected from Jenkins Credentials — never hardcoded here.
        SLACK_WEBHOOK_URL = credentials('slack-webhook-url')
        GHCR_USER       = credentials('ghcr-user')
        GHCR_PASSWORD   = credentials('ghcr-password')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build Backend') {
            steps {
                sh './gradlew build --no-daemon'
            }
        }

        stage('Infra Tests') {
            steps {
                sh './gradlew :infra-tests:test --no-daemon'
            }
        }

        stage('Build Frontend') {
            steps {
                dir('frontend') {
                    sh 'npm ci'
                    sh 'npm run build'
                    sh 'npm test'
                    sh 'npm run lint:invariants'
                    sh 'npm run test:e2e -- chat-assistant.spec.ts'
                }
            }
        }

        stage('Publish Images on Tag') {
            when {
                expression { env.TAG_NAME ==~ /^v.*/ }
            }
            steps {
                script {
                    def services = [
                        'eureka-server', 'config-server', 'api-gateway',
                        'identity-service', 'product-service', 'inventory-service',
                        'cart-service', 'order-service', 'payment-service',
                        'notification-service', 'search-service', 'ai-service', 'mcp-server'
                    ]
                    sh "echo \"${GHCR_PASSWORD}\" | docker login ghcr.io -u \${GHCR_USER} --password-stdin"
                    services.each { svc ->
                        sh """
                            ./gradlew :${svc}:jib --no-daemon \
                                -Djib.to.image=\"ghcr.io/\${OWNER_REPO}/${svc}:\${TAG_NAME}\" \
                                -Djib.to.tags=latest
                        """
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                def text = currentBuild.result == 'SUCCESS'
                    ? "✅ build green on ${env.BRANCH_NAME ?: env.TAG_NAME}"
                    : "❌ build failed on ${env.BRANCH_NAME ?: env.TAG_NAME}"
                sh """
                    curl -X POST -H 'Content-type: application/json' \
                        --data '{"text":"${text}"}' \
                        "\${SLACK_WEBHOOK_URL}"
                """
            }
        }
    }
}
```

### Key Differences in the Jenkins Sketch

| Concern | GitHub Actions | Jenkins (sketch) |
|---------|---------------|------------------|
| **Agent provisioning** | GitHub-hosted `ubuntu-latest` runners, ephemeral | Requires a Jenkins agent with JDK 21, Node 24, Docker, and Gradle wrapper cache |
| **Parallelism** | Matrix strategy natively supported for 13 services | Requires scripted loop or `parallel` block; more boilerplate |
| **Secrets injection** | `${{ secrets.XXX }}` at workflow or step level | `credentials('xxx')` binding in `environment` or `withCredentials` block |
| **Caching** | `actions/cache@v4` with Gradle and npm paths | Requires manual cache configuration or a Jenkins cache plugin |
| **Registry auth** | `docker/login-action@v3` + automatic `GITHUB_TOKEN` | Manual `docker login` with username/password from Jenkins Credentials |
| **Conditional stages** | `if: always()` / `if: ${{ secrets.XXX != '' }}` / `on: push: tags: ['v*']` | `when` expression + `post { always { ... } }` |

---

## 4. Secrets and Credentials

**Rule:** Secrets live in the native credential store of the CI system, or in a local `.env` file for local development. They are **never committed to source control**.

| Secret / Credential | GitHub Actions | Jenkins | Local Dev |
|---------------------|--------------|---------|-----------|
| `SLACK_WEBHOOK_URL` | Repository secret (`secrets.SLACK_WEBHOOK_URL`) | Jenkins Credentials (`slack-webhook-url`) | `.env` file (ignored by `.gitignore`) |
| `GITHUB_TOKEN` | Automatically provided by GitHub Actions; used by `docker/login-action@v3` for GHCR | A Personal Access Token (PAT) or GitHub App token stored in Jenkins Credentials (`ghcr-password`) | A PAT in `.env` if manually pushing images |
| `CLOUDFLARE_TUNNEL_TOKEN` | Not needed in CI (tunnel runs on the candidate's machine) | Not needed in CI | `.env` file on the candidate's machine |
| `NGROK_AUTHTOKEN` | Not needed in CI (fallback tunnel runs locally) | Not needed in CI | `.env` file on the candidate's machine |

### Threat Mitigation

- **No values in source:** Every reference above is a placeholder name. Real URLs, tokens, and passwords are injected at runtime by the CI environment or read from a local `.env` that is excluded by `.gitignore`.
- **No OIDC:** Because there is no AWS or cloud provider integration, OIDC token exchange is not in scope. GHCR authentication uses the built-in `GITHUB_TOKEN` (GitHub Actions) or a manually managed PAT (Jenkins sketch).
- **gitleaks CI gate:** The existing `.github/workflows/security.yml` runs `gitleaks detect` to catch accidental secret commits.

---

## 5. Why GitHub Actions for This Project

1. **Zero additional infrastructure:** GitHub Actions runners are provided by GitHub; no Jenkins controller or agent fleet needs to be provisioned, secured, or maintained.
2. **Native GHCR integration:** Publishing to `ghcr.io/<owner>/<repo>/<service>` uses the automatically injected `GITHUB_TOKEN` — no extra secret rotation.
3. **Tight PR integration:** Checks appear directly on pull requests with no webhook configuration.
4. **Matrix jobs are first-class:** The 13-service Jib publish matrix is three lines of YAML; the equivalent Jenkins scripted loop is more verbose and harder to read.
5. **Scope fit:** The project is a single-repository, 6-day bootcamp case. The operational overhead of a Jenkins instance (plugin management, agent scaling, backup) is disproportionate to the deliverable.

---

## 6. Local-Host Deploy Model

Both CI systems produce the same artifact: **OCI images published to GHCR**. The actual "deployment" happens on the candidate's machine, not inside the CI runner.

### Deploy Flow (CI-agnostic)

1. **Build images locally** (or pull pre-built release images):
   ```bash
   # Local build
   ./gradlew jibDockerBuild
   # Or pull a release tag
   docker compose --profile full pull
   ```
2. **Start the full stack:**
   ```bash
   docker compose --profile full up -d
   ```
3. **Expose the gateway publicly** via Cloudflare Tunnel (preferred) or ngrok (fallback):
   ```bash
   # Cloudflare Tunnel
   cloudflared tunnel --no-autoupdate run --token "$CLOUDFLARE_TUNNEL_TOKEN"

   # ngrok fallback
   ngrok http 8080 --authtoken "$NGROK_AUTHTOKEN"
   ```
4. **Verify externally:**
   ```bash
   curl https://<tunnel-hostname>/api/v1/products
   ```

Because the deploy target is the candidate's machine, the CI pipeline's responsibility ends at **image build + test + publish**. The tunnel is started manually during the interview/demo window; it is not part of the CI pipeline itself.

---

*Document created for DEV-04. Jenkinsfile sketch is illustrative only; GitHub Actions is the project's sole CI system.*
