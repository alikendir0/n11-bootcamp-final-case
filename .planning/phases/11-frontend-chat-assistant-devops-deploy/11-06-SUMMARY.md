---
phase: 11-frontend-chat-assistant-devops-deploy
plan: 06
subsystem: docs
tags: [jenkins, github-actions, devops, ci-cd, documentation, pipeline-comparison]

requires:
  - phase: 11-04
    provides: GitHub Actions CI/release workflow files (ci.yml, release.yml)

provides:
  - Jenkins versus GitHub Actions pipeline-stage comparison document
  - Equivalent declarative Jenkinsfile sketch (illustrative only)
  - Explicit secrets/credential handling table mapping GitHub Actions secrets, Jenkins Credentials, and local .env
  - Local-host deploy model explanation

affects:
  - 11-05

tech-stack:
  added: []
  patterns:
    - "Documentation-only Jenkinsfile sketch — no second CI system added"
    - "Secrets mapping table: GitHub Actions secrets ↔ Jenkins Credentials ↔ local .env"

key-files:
  created:
    - docs/devops-pipeline-comparison.md
  modified:
    - .gitignore

key-decisions:
  - "GitHub Actions chosen over Jenkins for this project: zero additional infrastructure, native GHCR integration, tight PR integration, first-class matrix jobs for 13 services"
  - "Jenkinsfile sketch labeled as illustrative documentation only — DEV-04 asks for comparison, not a second CI system"
  - "No AWS/OIDC in scope: deploy target is local docker-compose on candidate's machine with Cloudflare Tunnel/ngrok"

requirements-completed: [DEV-04]

duration: 1min
completed: 2026-05-02
---

# Phase 11 Plan 06: Jenkins versus GitHub Actions comparison Summary

**Comprehensive CI/CD pipeline comparison document mapping every Phase 11 stage to an equivalent Jenkinsfile sketch, with explicit credential-handling rules and local-host deploy model explanation.**

## Performance

- **Duration:** 1 min
- **Started:** 2026-05-02T16:55:07Z
- **Completed:** 2026-05-02T16:56:44Z
- **Tasks:** 1
- **Files modified:** 2

## Accomplishments

- Created `docs/devops-pipeline-comparison.md` satisfying DEV-04 with six sections: Pipeline stages, GitHub Actions implementation, Equivalent Jenkinsfile sketch, Secrets and credentials, Why GitHub Actions for this project, and Local-host deploy model.
- Mapped every relevant CI stage: checkout, setup Java 21, setup Node 24, Gradle build/test, frontend build/test, infra-tests, Slack notification, release-tag GHCR Jib publish, and the no-AWS/OIDC posture.
- Included a fenced declarative `Jenkinsfile` sketch with stages: Build Backend, Build Frontend, Infra Tests, Publish Images on Tag, and Notify Slack.
- Documented explicit credential handling: `SLACK_WEBHOOK_URL`, `GITHUB_TOKEN`/registry credentials, `CLOUDFLARE_TUNNEL_TOKEN`, and `NGROK_AUTHTOKEN` belong in GitHub Actions secrets, Jenkins Credentials, or local `.env` only — never in source.

## Task Commits

Each task was committed atomically:

1. **task 1: document Jenkins versus GitHub Actions pipeline logic** - `18ab2c9` (docs)

**Plan metadata:** (to be committed with SUMMARY.md)

## Files Created/Modified

- `docs/devops-pipeline-comparison.md` — Jenkins/GitHub Actions comparison document covering pipeline-stage mapping, Jenkinsfile sketch, secrets table, and deploy model (229 lines)
- `.gitignore` — Added `node_modules/` and `test-results/` to prevent accidental commits of generated files

## Decisions Made

- Followed the plan exactly: GitHub Actions is the project's sole CI system; the Jenkinsfile is illustrative documentation only.
- GHCR chosen as the release registry over Docker Hub because `GITHUB_TOKEN` is natively available in GitHub Actions, eliminating extra secret rotation.
- No OIDC configuration is needed because there is no AWS or cloud-provider integration; the deploy target is the candidate's local machine.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added node_modules/ and test-results/ to .gitignore**
- **Found during:** task 1 (post-commit untracked-file check)
- **Issue:** `git status` showed untracked `node_modules/` and `test-results/` directories at the repository root and under `frontend/`. These are generated/runtime outputs. If accidentally committed, they would bloat the repository and could leak build artifacts.
- **Fix:** Added `node_modules/` and `test-results/` as generic patterns to `.gitignore`.
- **Files modified:** `.gitignore`
- **Verification:** `git status --short | grep '^??'` returns empty after the fix.
- **Committed in:** `18ab2c9` (task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 missing critical)
**Impact on plan:** Hygiene fix only. No scope creep or behavior change.

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required for this documentation-only plan.

## Next Phase Readiness

- DEV-04 is now complete.
- Phase 11 has 2 remaining plans: 11-05 (docker compose full-profile posture, env examples, root README demo runbook, and public tunnel proof) and 11-06 is now done.
- After 11-05, Phase 11 will be complete and the project will be ready for final demo posture.

## Self-Check: PASSED

- [x] `docs/devops-pipeline-comparison.md` exists and contains required strings
- [x] Task commit `18ab2c9` exists in git history
- [x] Metadata commit `84d01ff` exists in git history
- [x] All 5 acceptance criteria grep checks pass
- [x] STATE.md updated (plan advanced to 5/6, progress 98%)
- [x] ROADMAP.md updated (Phase 11: 5/6 summaries)
- [x] REQUIREMENTS.md updated (DEV-04 marked complete)

---
*Phase: 11-frontend-chat-assistant-devops-deploy*
*Completed: 2026-05-02*
