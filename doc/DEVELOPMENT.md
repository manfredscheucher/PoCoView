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

Source files in `composeApp/examples/`: one JSON file per set in a subfolder per group
(`singletons/holes/`, `singletons/crossing-families/`, …), the order type database as Graz b08 files
(`order-types/`). `index.json` lists them as a two-level tree, supergroups and groups, in display order:

```json
{
  "supergroups": [
    {
      "id": "database",
      "title": "Database",
      "description": "All order types of 3 to 7 points.",
      "links": [{"title": "Order type database", "url": "http://...", "description": "..."}],
      "groups": [
        {"id": "order-types", "title": "Order types", "files": ["order-types/otypes03.b08", "..."]}
      ]
    }
  ],
  "links": [{"title": "PoSeZo (Point Set Zoo)", "url": "https://...", "description": "..."}]
}
```

`id`, `title` and `groups`/`files` are required; `description` and `links` are optional (on
supergroups, groups and the top level). Supergroup ids are unique, group ids unique within their
supergroup; they make the dialog's tree keys (`SG/<id>`, `G/<supergroup id>/<id>`). In a group's
`files`, `.json` files are single sets, all others are order type files (read with the file formats
of Open file). The top-level `links` show under Links in the dialog. The sister apps ArrView and
RotSysView use the same tree (supergroup, group).

The `generateExamples` Gradle task embeds `index.json` and every listed file into the generated
`GeneratedExamples.kt`, so the app has them at once (no resource reads); `core/Examples.kt` parses
them at first use. Generated families (Horton sets, convex position) are code in `core/Families.kt`
(`PointSetFamily`).

File = the app's JSON point format plus metadata:

```json
{
  "name": "n13-c1-min-convex-3-4-5-holes",
  "n": 13,
  "colors": 1,
  "group": "Holes and convex gons",
  "description": "Set of 13 points minimizing the numbers of convex 3-, 4-, and 5-holes.",
  "reference": "original source (paper, or PoSeZo if unpublished)",
  "url": "link or null",
  "source": "where the coordinates were taken from",
  "claim": "optional: what the source states",
  "note": "optional remark",
  "properties": ["triangulations"],
  "points": [[x, y], [x, y, "color"]]
}
```

Names: `n<N>-c<colors>-<description>`, lowercase words joined by `-`, numbers without leading
zeros; two sets with the same description get their source as a suffix
(`n16-c1-no-convex-6-gon-kalbfleisch-stanton`). The loader checks the name and `n` (`ExamplesTest`
runs it over all sets). To add a set, add its file under `composeApp/examples/` and list it in
`index.json`; the next build embeds it.

`properties` (optional) lists property ids that are switched on when the example is loaded, for
properties that are off by default but essential for that set (e.g. `triangulations`, which is
exponential and therefore off by default). Unknown ids are rejected by the loader. Loading an example
only switches properties on, never off.

## Deploy to GitHub Pages

```
./scripts/deployPages.sh
git push origin gh-pages
```

`deployPages.sh` builds the site, copies it into a worktree of the `gh-pages` branch (`.gh-pages/`,
created on first use) and commits it there. It does not push. The site is served from
https://scheucher.dev/PoCoView/ (one-time setup on github.com: Settings, Pages, deploy
from branch `gh-pages`, root).
