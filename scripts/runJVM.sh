#!/usr/bin/env bash
# Run the JVM desktop build via Compose's run task.
#
#   ./scripts/runJVM.sh
set -euo pipefail
cd "$(dirname "$0")/.."

echo "Starting the desktop app (:composeApp:run) …  (Ctrl-C to stop)"
exec ./gradlew :composeApp:run "$@"
