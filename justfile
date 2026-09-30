set shell := ["bash", "-uc"]

default:
    @just --list

build:
    ./gradlew assemble

lint:
    ./gradlew verifyNoSuppressions ktlintCheck detektMain detektTest detektIntegrationTest

test:
    ./gradlew verifyIntegrationTestSourceSetNotEmpty test integrationTest

sonar:
    #!/usr/bin/env bash
    set -euo pipefail
    ./gradlew detektMain detektTest detektIntegrationTest jacocoTestReport jacocoTestCoverageVerification
    if command -v gitleaks >/dev/null 2>&1; then
        gitleaks detect --no-banner --redact
    else
        printf 'gitleaks is not installed locally, so secrets were not scanned here; the gitleaks job on the pull request is the enforcing copy\n' >&2
    fi

commits base="origin/main":
    scripts/check-commits.sh {{base}}

gate-test:
    scripts/agent-review-test.sh

db-local-up:
    docker compose up -d --wait postgres

db-local-down:
    docker compose down

supabase-verify:
    scripts/supabase-verify.sh

image:
    docker build -t housedash-core-service:local .

deploy-cloud-run project region service db_host db_user password_secret secret_version="latest":
    scripts/deploy-cloud-run.sh {{project}} {{region}} {{service}} {{db_host}} {{db_user}} {{password_secret}} {{secret_version}}
