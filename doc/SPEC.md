# Point-set explorer: specification

Status: **v1 implemented (web + JVM), awaiting Manfred's manual test (§10).**
Source of truth for requirements, decisions and the sign-off checklist.

## 1. Original request (Manfred, 2026-10-19, paraphrased from German)

> Build a graphical point-set explorer analogous to `../pyotlib2`, as **Kotlin Multiplatform**,
> targets **JVM + web** (and **Android**). Main goal: a hosted **website** where one can
> investigate point sets in the browser:
> - load/save point sets, move points, etc.
> - choose in the GUI which properties are computed/shown
> - several **modes**: `count` (only numbers), `color` (structures are visualized; for k-gons
>   and k-holes the areas are filled), `incident` (only structures incident to the selected
>   point are counted/highlighted)
> - edit workflows and visualization like the original **qtvis**; scrolling/panning, coordinate
>   **minimization**, **beautify**, etc. Check what pyotlib2 has and what not; qtvis had a lot
>   that Manfred liked.
> - `scripts/` folder like `../luckySweep` and the other projects (`runWeb`, `runJVM`, …)
> - **photo mode**: upload jpg/png (or Android camera) of dots, detect contrast blobs, place a
>   point on every blob. Can be simulated (synthetic images) for tests.
> - portrait/landscape auto layout like `../ktoy`
> - optionally improve the pyotlib2 GUI / pyotlib2 itself where something stands out.
> - Photo plumbing exists in MisterManager (but **no barcode scanner** needed).
> - Research which KMP tech is sensible (e.g. Google ML Kit?).

## 2. Findings from the existing projects

### 2.1 qtvis (`pyotlib2/old/qtvis2/qtvis/`, C++/Qt5, 2016)
Worth porting:
- full line arrangement (line through every pair), **lines through the selected point hidden**
  so its cell is visible while dragging
- **Fix OT**: a drag that changes the order type snaps back to the last valid position
- convex 5-/6-holes as translucent filled polygons, **blue if incident to the selected point**
  (= the "incident" mode), separate *count* / *color* checkboxes per property
- recount only when the order type actually changed
- beautify = move every point to the centre of its cell (12-direction bidirectional line search)
- center & scale (shrink coordinates), greedy cell-hopping property minimizer with restarts
- wheel zoom anchored at cursor, `+`/`-`, `Home` fit, `Esc` deselect, `Space` optimize,
  `Enter` minimize
- side tabs: coordinates list, λ-matrix

### 2.2 pyotlib2 (Python lib + PySide6 editor `pyotlib2/vis/editor/`)
- exact integer orientation, general position required
- representations: SmallLambda `l[i,j]` (#points left of i→j), BigLambda chirotope
- properties: hull, onion layers, crossing number (= convex 4-gons), empty triangles, convex
  k-gons / k-holes (MRSW DP, O(k·n³)), triangulation count, exit edges, natural labeling /
  lex-min canonical form, projective class
- optimizers: coordinate minimization (move-and-halve with exit-triple checks), beautify
  (per-coordinate binary-search bracket → midpoint), property min/max walk
- file formats: `asc`/`psz` (`n`, then `x y [COLOR]`), `json` (`[[x,y],…]` per line),
  `pb08/pb16/pb32` (Graz binary, LE unsigned x1 y1 x2 y2 …), `slt`, `blt`, …
- example data: `data/otypes/otypes03..08.b08`, `data/posezo/*.psz`
- editor bugs found (candidates for fixing in pyotlib2, see §9 Q4):
  1. load rescales/rounds coordinates into [500,9500] and flips y → may change the OT
  2. save writes screen (flipped, rescaled) coordinates; `.b08` save fails for values > 255
  3. optimizer results re-normalized to grid, undoing minimization
  4. Property Min/Max crashes for triangulations / onion layers (ValueError kills worker)
  5. onion-layer count stubbed (returns 1 or 2)
  6. `Esc` selects the last point instead of deselecting
  7. selection marks the document dirty
  8. everything recomputed synchronously on every mouse move (lags for n ≳ 20)
  9. help text lists features that do not exist (double-click add, right-click delete, …)
- missing in both: undo/redo, add/delete points, open dialog, export, k-gon visualization.

### 2.3 KMP house conventions (ktoy, luckySweep, OYS, MM)
- Kotlin 2.3.21, Compose MP 1.12.1, AGP 9.4.0, Gradle 9.8.1, SDK 37/24
- layout like ktoy: `composeApp/` (KMP lib) + `androidApp/` (APK wrapper) + `kotlin-common`
  submodule (`include(":core")` + `projectDir` remap)
- web target `js` in OYS/MM/ktoy (kotlin-common only builds `js`, not `wasmJs`)
- `gradle.properties`: `-Xmx1536M` for Gradle and Kotlin daemons (RAM hygiene)
- scripts: `runWeb.sh` (webpack dev-server, port mirrored in `webpack.config.d/watch.js`),
  `runJVM.sh`, `runAndroidDevice.sh`, `runAndroidEmulator.sh`, `install-hooks.sh`, `testWeb.sh`
- next free web dev port: **8606** (to be registered in `~/github/.dev-ports.md`)
- portrait/landscape: ktoy uses width-based `BoxWithConstraints` + `LayoutMode {AUTO, PORTRAIT,
  LANDSCAPE}` (app-local, not in kotlin-common)
- file load/save on all 3 platforms: ktoy `FileIo*.kt` (JS `<input>`/Blob, JVM `JFileChooser`,
  Android SAF)
- photo plumbing: MM `CameraManager` / `ImagePicker` (app-local), kotlin-common
  `ByteArray.toImageBitmap()`. No raw-pixel access anywhere yet → `ImageBitmap.readPixels()`.
- no project hosts a static website yet (no GitHub Pages); kotlin-common is a private submodule
  (blocks CI-built Pages unless a PAT is configured).

## 3. Photo mode: FUTURE (not in v1)
Decision (Manfred, 2026-10-19): photo mode uses **Google ML Kit**, on **Android + iOS only**.
JVM and browser get no photo mode. Because Android (and iOS) are postponed (§3a), photo mode is
a future TODO.
- Claude raised two concerns beforehand; Manfred decided for ML Kit anyway:
  - ML Kit has no JVM/web support (confirmed: KMP wrappers like MLKit-KMP target Android/iOS).
  - ML Kit has no dot/blob model (barcode, text, faces, generic objects). Detecting small
    dots will likely need ML Kit object detection with a custom TFLite model, or fall back to
    classic blob detection (grayscale → blur → Otsu threshold → connected components →
    centroids). To be re-evaluated when the photo mode is implemented.
- Reusable plumbing when it comes: MisterManager `CameraManager` / `ImagePicker` (app-local),
  Android EXIF rotation. No barcode scanner.

## 3a. Targets
Decision (Manfred, 2026-10-19):
- **web (`js`) is the main target.** Consistent with OYS/MM/ktoy.
- **JVM desktop**: secondary target (from the original request; used for development).
- **Android**: postponed, only documented as TODO (§11). iOS likewise (it was raised together
  with ML Kit).
- **No kotlin-common**: the project stays independent (may be reconsidered later). Code from
  ktoy (`FileIo*.kt`) is ported, not shared.

## 3b. Hosting: GitHub Pages
- `scripts/deployPages.sh` builds `:composeApp:jsBrowserDistribution`, copies
  `composeApp/build/dist/js/productionExecutable/` (+ `.nojekyll`) into a local `gh-pages`
  branch and commits. **Manfred pushes** (`git push origin gh-pages`).
- one-time setup on github.com: Settings → Pages → Deploy from a branch → `gh-pages` / root.
- URL: `https://manfredscheucher.github.io/ordertypes-kotlin/`
- free accounts: Pages requires a public repo.
- no GitHub Actions (no CI, per global rule).
- Interpretation note: Manfred answered "das wär eigentlich ne gute idee" after the Pages
  explanation; read as approval of the local-script route. Not yet confirmed explicitly.

## 4. Domain model (proposed)
- points have **integer coordinates** (`Long`), general position required; collinear triples
  are flagged red (like pyotlib2), properties needing general position show "n/a".
- exact orientation via `Long` determinant; coordinates limited to |x|,|y| ≤ 2^29 so the
  determinant cannot overflow (checked on load; larger inputs rejected with a message).
- no silent rescaling on load or save (fixes pyotlib2 bug 1/2). Screen y-axis flip is
  view-only.
- optional per-point colour token (PoSeZo `B`/`R`) kept on load/save.

## 5. Properties (v1 proposal)
Registry of properties; each has *count* and optionally *structures* (for colour/incident):

| Property | Count | Visualization |
|---|---|---|
| convex hull size | yes | hull polygon |
| onion layers | yes | nested layer polygons |
| crossing number (convex 4-gons) | yes | — (too many) |
| empty triangles (3-holes) | yes | filled triangles |
| convex k-holes, k = 4..7 | yes (MRSW) | filled polygons |
| convex k-gons, k = 4..7 | yes | filled polygons |
| triangulation count | yes (n small) | — |

Each property toggled individually; heavy ones run in a background coroutine with cancel and
"computing…" state, recomputed only when the order type changed.

## 6. Modes
- **count**: numbers only, no polygons
- **color**: all structures of the enabled properties drawn as translucent fills
- **incident**: only structures containing the selected point are counted and drawn
  (no point selected → like color mode, with a hint)

## 7. GUI features (v1 proposal)
- canvas: points with optional labels, line arrangement toggle, hide lines through selected
  point, zoom (wheel/pinch, anchored), pan (drag on empty space / two-finger), fit view
- edit: drag point, add point (click empty space in *add* tool), delete point, **undo/redo**,
  Fix-OT toggle, direct coordinate edit in the coordinates list, random point set (n)
- operations: center & scale, **minimize coordinates**, **beautify**, property min/max walk
  (step / auto with cancel)
- info panel: coordinate list, λ-matrix, OT hash
- files: open/save `asc`/`psz`, `json`, `pb08/16/32`; multi-OT files with prev/next navigation;
  export SVG (proposal)
- photo mode: future, not in v1 (§3)
- layout: landscape = canvas + side panel, portrait = canvas + bottom sheet/tabs (ktoy-style
  width-based AUTO with manual override)

## 8. Development pipeline (global rules)
- never hand over a broken app: compiles, tests green, GUI does not crash
- unit tests for all geometry/properties (cross-checked against pyotlib2 counts on the Graz
  `otypes*.b08` data), photo detection tests on synthetic images, UI tests where sensible
- fresh reviewer after implementing; this doc is updated with every decision
- sign-off checklist (§10): Manfred tests per target (JVM, web, Android)

## 9. Decisions (Manfred, 2026-10-19)
1. Name: **`ordertypes-kotlin`** ("rusty" was wrong, it is Kotlin). Folder renamed
   `rusty-ordertypes` → `ordertypes-kotlin` (done). Package: `org.bytefred.ordertypes` (proposal).
2. Web target: `js`.
3. Hosting: GitHub Pages via local script (§3b).
4. pyotlib2: **fix the editor bugs** listed in §2.2.
5. kotlin-common: **not used**, project independent.
6. Photo mode: ML Kit, Android + iOS only → future (§3).
7. Android: postponed, docs/TODO only.

## 10. Sign-off checklist
Automatic: 40 unit tests green (`./scripts/test.sh`): DP counts vs brute force, enumeration,
Catalan numbers, Graz database (number of order types n=3..8, rectilinear crossing numbers
0,0,1,3,9,19), PoSeZo example sets (5-hole / 6-hole / crossing counts), formats, optimizers,
GUI state (modes, incident counts, undo, fix-OT, multi-set save). Web bundle rendered in headless
Chrome (landscape + portrait), desktop app launched without exceptions.

Manual test by Manfred (open until he gives the OK), per target **web** and **JVM**:
- [ ] web: `./scripts/runWeb.sh`, JVM: `./scripts/runJVM.sh`
- [ ] drag/add/delete points, pan, wheel zoom (pinch on a phone browser), Fit
- [ ] modes count / color / incident (select a point), count/draw checkboxes
- [ ] Fix order type, line arrangement, hide lines through the selected point
- [ ] Open `data/otypes/otypes06.b08` (16 sets, prev/next), `data/posezo/*.psz`; Save as each format
- [ ] Beautify, Minimize (step/auto), property walk (run/stop)
- [ ] Info tab: edit coordinates + Apply, λ matrix
- [ ] portrait vs landscape (narrow/wide browser window, Layout button)
- [ ] GitHub Pages: `./scripts/deployPages.sh`, then Manfred pushes `gh-pages` (one-time Pages setup)

## 10a. Implementation notes and interpretations (v1)
- Package `org.bytefred.ordertypes`; single module `composeApp` (targets `jvm`, `js`).
- Web dev port **8607** (8606 was already taken by luckySweep), registered in `~/github/.dev-ports.md`.
- Exact integer arithmetic with `Long`; |coordinate| <= 2^29 (no overflow); larger inputs are
  rejected on load with a message. No rescaling on load/save; the y flip is view-only.
- Properties (`core/Properties.kt`): convex hull size, onion layers, crossing number (= convex
  4-gons, drawn as the crossing diagonals), 3..7-holes, convex 4..7-gons, triangulations (n <= 16).
  k-gons/k-holes are counted by a DP over the last chain edge (O(k n^4)), with the number of
  structures containing the selected point counted in the same pass. Drawing is limited to the
  first 3000 structures per property (the GUI says so).
- Per property two checkboxes like qtvis: **count** (evaluate) and **draw** (structures; only in
  color/incident mode). Default: count hull, layers, crossings, 3..6-holes, triangulations; draw 5-holes.
- Incident mode (interpretation): counts show "total · incident: x" where x = structures having the
  selected point as a vertex; only those are drawn. Onion layers show the layer of the selected
  point; hull shows 1/0.
- Properties are recomputed only when the labeled order type changes (cached on the triple-sign
  signature) and run in a cancellable background coroutine that yields to the browser.
- Not in general position: collinear points get a red ring, properties show "not in general
  position"; adding a collinear point is refused.
- Beautify = pyotlib2's per-coordinate bracket midpoint; Minimize = move every point to even
  coordinates (beautifying stuck points first), then halve exactly; repeat ("Auto").
  qtvis' "center & scale" is not separately implemented (Minimize covers the goal).
- Property walk accepts moves that do not make the property worse (equal moves allowed, as qtvis).
- Binary Graz files: n from the first 1-2 digit number of the file name (`otypes05.b08`).
- Keyboard shortcuts: see the Edit tab (Esc, Del, Ctrl+Z/Ctrl+Shift+Z, +/-, Home, 1/2/3, V/A/D, F, L,
  B, M, PageUp/PageDown).
- Not yet: arrow-key panning, PNG export (SVG export exists), persisting the session in the browser.
- Fresh review (2026-10-19) found and Claude fixed: beautify pushed hull points to ±2^28 (now
  clipped to the bounding box, never enlarges it); undo crossed set boundaries (history is now per
  set); binary save names could not be read back (now `name_n<k>.b08` when n changed; mixed-n
  "save all" refused); malformed files threw raw exceptions (now messages); JSON parser misread
  floats/pretty-printed input (real array parser); background operations overwrote edits (every
  edit cancels them); errors in a property became uncaught (now "error: …"); stale results of an
  old order type were shown (now "computing…" immediately); cache could grow to hundreds of MB
  (64 entries); shortcuts fired while typing (now only on the focused canvas); pinch→pan jump,
  two-finger pan added, wheel pixel deltas normalized, view no longer refit on every resize;
  `deployPages.sh` needed git ≥ 2.42 (macOS has 2.39).
- Performance: orientations are looked up in one table per evaluation (Kotlin/JS emulates Long);
  dragging inside a cell costs O(n^2) and triggers no recount.

## 12. Decisions on the open questions (Manfred, 2026-10-19: "kein pre commit hook, rest schon bitte, schaltbar")
1. Incident mode is **switchable**: "only incident" (only structures containing the selected point
   are drawn) or "highlight incident" (all drawn faintly, the incident ones strong - qtvis style).
   Counts show total and incident in both. Interpretation: the highlight uses the property's own
   colour (strong vs faint) instead of qtvis' fixed blue, so several properties stay distinguishable.
2. qtvis minimizer **ported** (`core/CellHopping.kt`, from `graphwidget.cpp`
   `tryToMinimizeMeta`/`tryToMinimize`/`lineSearch`/`shuffle`) with the qtvis defaults (trials 3,
   max iterations 6, max fail iterations 2, epsilon 3, alpha 1.05, only last point off). Enter key
   or "Try to minimize" in the Optimize tab, for the property chosen there (qtvis: `to_optimize`).
   Notes on the port: the qtvis canvas [0, max_val) is the bounding box of the start
   configuration; a restart configuration that is not in general position counts as infinitely
   bad; minimize only, as in qtvis (the random walk keeps min/max).
3. GitHub Pages via `scripts/deployPages.sh` + manual push: confirmed.
4. pyotlib2: editor fixes committed (only the editor-related hunks of `cli/commands.py`; Manfred's
   unrelated work in progress stays uncommitted); "maximize" in `walk_points` fixed
   (`maximize=True`, used by the editor's Property Min/Max).
5. No pre-commit hook.
