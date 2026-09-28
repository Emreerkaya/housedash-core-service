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
    ./gradlew jacocoTestReport jacocoTestCoverageVerification
    if command -v gitleaks >/dev/null 2>&1; then
        gitleaks detect --no-banner --redact
    else
        printf 'gitleaks is not installed locally, so secrets were not scanned here; the gitleaks job on the pull request is the enforcing copy\n' >&2
    fi

commits base="origin/main":
    scripts/check-commits.sh {{base}}
