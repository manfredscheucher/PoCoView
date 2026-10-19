#!/usr/bin/env bash
# Run the web (Kotlin/JS) build in a browser via the Gradle webpack dev-server
# (hot reload).
#
#   ./scripts/runWeb.sh               # serve on :8607, open Chrome
#   NO_CHROME=1 ./scripts/runWeb.sh   # serve only, open the URL yourself
#
# Port: 8607 (set in composeApp/webpack.config.d/watch.js; see ~/github/.dev-ports.md).
# Change it in BOTH that file and the PORT= line below if ever needed.
set -euo pipefail
cd "$(dirname "$0")/.."

PORT=8607
URL="http://localhost:$PORT/"
CHROME="${CHROME_PATH:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
TASK=":composeApp:jsBrowserDevelopmentRun"
LOG="/tmp/ordertypes-kotlin-runweb.log"

echo "Starting Gradle dev-server ($TASK) …  logs: $LOG   (Ctrl-C to stop)"
./gradlew "$TASK" >"$LOG" 2>&1 &
GRADLE_PID=$!
trap 'kill "$GRADLE_PID" 2>/dev/null || true' EXIT

# Wait for the dev-server to accept connections (cold Gradle can take a while).
echo -n "waiting for $URL "
UP=""
for _ in $(seq 1 600); do
    if curl -sf -o /dev/null "$URL"; then echo " up"; UP=1; break; fi
    if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
        echo; echo "Gradle exited early — see $LOG" >&2; tail -20 "$LOG" >&2; exit 1
    fi
    echo -n .; sleep 0.5
done
if [ -z "$UP" ]; then
    echo; echo "Dev-server not up after 5 min — see $LOG" >&2; exit 1
fi

if [ -z "${NO_CHROME:-}" ]; then
    if [ -x "$CHROME" ]; then
        "$CHROME" --new-window "$URL" >/dev/null 2>&1 &
    elif command -v google-chrome >/dev/null 2>&1; then
        google-chrome --new-window "$URL" >/dev/null 2>&1 &
    else
        echo "Chrome not found — open manually: $URL" >&2
    fi
fi

wait "$GRADLE_PID"
