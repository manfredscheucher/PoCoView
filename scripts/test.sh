#!/usr/bin/env bash
# Run the fast unit tests (JVM). A few seconds once Gradle is warm.
#
#   ./scripts/test.sh
set -euo pipefail
cd "$(dirname "$0")/.."

exec ./gradlew :composeApp:jvmTest "$@"
