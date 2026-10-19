#!/usr/bin/env bash
# Build the static production website (Kotlin/JS bundle + index.html).
# Output: composeApp/build/dist/js/productionExecutable/
#
#   ./scripts/buildWeb.sh
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew :composeApp:jsBrowserDistribution
echo "Website built: composeApp/build/dist/js/productionExecutable/"
