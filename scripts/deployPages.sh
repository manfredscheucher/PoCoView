#!/usr/bin/env bash
# Deploy the website to GitHub Pages (branch gh-pages), WITHOUT pushing.
#
# Builds the production bundle, copies it into a git worktree of the gh-pages
# branch (.gh-pages/, created as an orphan branch on first use), and commits
# it locally. Manfred then publishes it himself:
#
#   ./scripts/deployPages.sh
#   git push origin gh-pages
#
# One-time setup on github.com: Settings -> Pages -> Deploy from a branch ->
# gh-pages / (root). URL: https://manfredscheucher.github.io/PoCoView/
set -euo pipefail
cd "$(dirname "$0")/.."

DIST=composeApp/build/dist/js/productionExecutable
WT=.gh-pages   # outside build/, so a Gradle clean does not delete the worktree

./scripts/buildWeb.sh

if [ ! -d "$WT" ]; then
    if git show-ref --verify --quiet refs/heads/gh-pages; then
        git worktree add "$WT" gh-pages
    else
        # (`git worktree add --orphan` needs git >= 2.42; macOS ships 2.39)
        git worktree add --detach "$WT"
        git -C "$WT" checkout -q --orphan gh-pages
        git -C "$WT" rm -rfq .
    fi
fi

# replace the branch content by the fresh build (keep .git of the worktree)
find "$WT" -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
cp -R "$DIST"/. "$WT"/
touch "$WT/.nojekyll"   # serve files as they are (no Jekyll processing)

SHA=$(git rev-parse --short HEAD 2>/dev/null || echo unknown)
git -C "$WT" add -A
if git -C "$WT" diff --cached --quiet; then
    echo "No changes to deploy."
else
    git -C "$WT" commit -q -m "deploy website ($SHA)"
    echo "Committed to gh-pages. Publish with:  git push origin gh-pages"
fi
