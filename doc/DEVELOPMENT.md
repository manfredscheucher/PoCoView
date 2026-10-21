# Development

## Run and test

```
./scripts/runWeb.sh       # browser with hot reload, http://localhost:8607/
./scripts/runJVM.sh       # desktop app
./scripts/test.sh         # unit tests (JVM)
./scripts/buildWeb.sh     # static site in composeApp/build/dist/js/productionExecutable/
```

`runWeb.sh` opens Chrome; `NO_CHROME=1 ./scripts/runWeb.sh` only starts the server. The port 8607
is set in `composeApp/webpack.config.d/watch.js` and in the `PORT=` line of `runWeb.sh`. Change both
if needed.

## Project layout

Everything is in the single module `composeApp`, package `org.bytefred.pocoview`:

- `core/`: geometry, order type properties, file formats, optimizers. No UI code, unit tested in
  `commonTest`.
- `ui/`: Compose UI (app state, canvas, panels, menus, SVG export). Platform entry points are in
  `jsMain` and `jvmMain`.

The coordinate optimizers (`core/CoordOptimizers.kt`, minimize and beautify) and the exit edges are
literal ports of pyotlib2, which in turn took them from the old pyotlib. The qtvis minimizer
(`core/CellHopping.kt`) is a literal port of the qtvis "try to minimize". Keep them literal: when
something looks odd, check the original first.

## Example point sets

Bundled under `composeApp/src/commonMain/composeResources/files/examples/`: one JSON file per set
in a subfolder per group (`singletons/`, `crossing-families/`, `universal/`, `misc/`), listed in
`index.json` (groups in order). Generated families (Horton sets, convex position) are code in
`core/Families.kt` (`PointSetFamily`).

File = the app's JSON point format plus metadata:

```json
{
  "name": "n13-c1-min-convex-3-4-5-holes",
  "n": 13,
  "colors": 1,
  "group": "Singletons",
  "description": "Set of 13 points minimizing the numbers of convex 3-, 4-, and 5-holes.",
  "reference": "original source (paper, or PoSeZo if unpublished)",
  "url": "link or null",
  "source": "where the coordinates were taken from",
  "claim": "optional: what the source states",
  "note": "optional remark",
  "points": [[x, y], [x, y, "color"]]
}
```

Names: `n<N>-c<colors>-<description>`, lowercase words joined by `-`, numbers without leading
zeros; two sets with the same description get their source as a suffix
(`n16-c1-no-convex-6-gon-kalbfleisch-stanton`). The loader checks the name and `n`. To add a set,
add its file and list it in `index.json`.

## Deploy to GitHub Pages

```
./scripts/deployPages.sh
git push origin gh-pages
```

`deployPages.sh` builds the site, copies it into a worktree of the `gh-pages` branch (`.gh-pages/`,
created on first use) and commits it there. It does not push. The site is served from
https://manfredscheucher.github.io/PoCoView/ (one-time setup on github.com: Settings, Pages, deploy
from branch `gh-pages`, root).
