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
    ./gradlew jacocoTestReport jacocoTestCoverageVerification
    command -v gitleaks >/dev/null 2>&1 && gitleaks detect --no-banner --redact || echo "gitleaks not installed locally; the pull request workflow enforces it"

commits base="origin/main":
    scripts/check-commits.sh {{base}}
