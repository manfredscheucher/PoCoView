# GUI spec log

Requests and decisions about the GUI. Newest first.

## 2026-11-17: Examples without loading

The Examples dialog shows the sets at once (no "Loading…"): the example files are embedded into the
code at build time (`composeApp/examples/`, `generateExamples`) instead of being read as resources.

## 2026-11-17: Optimize widget simplification (decided, not implemented yet)

Manfred: far too many options and settings. The widget offers only Minimize or Beautify, and
"Keep order type" or "Keep property". If the search finds a configuration that improves the
property, a dialog asks whether to take it. Claude first proposes the exact new UI and checks it
with a reviewer, then implements it.

Minimize, two approaches (Manfred):
1. Minimize the diameter as an integer: going from x to x-1 is a success. The set is squeezed and
   points wander.
2. Minimize the number of bits: success only when the diameter drops below the next power of two,
   i.e. fewer bits are needed. The whole range 0..2^k-1 is used (the set may even be stretched if
   there is room), and the task is to make all LSBs even (or all odd) so that one bit can be
   removed for all coordinates together.

Feedback boxes in ktoy, ksat-chess and the ksat playground: use a question like PoCoView's
("Ideas or general feedback?" + mail) instead of "... is in early development".

## 2026-11-17: Adopt ktoy's look and usability

Request: "take over the look of ktoy; it looks much better there, maybe also usability", with
attention to help texts and click/feedback mechanisms. ktoy's GUI spec: `../ktoy/doc/spec.md`
§11–§38. Code is copied (ktoy is public, kotlin-common private, so no shared module).

Accepted packages (all four):

**A. Look**
- Colour scheme from ktoy (`KtoyColors`/`KtoyTheme` → `PoCoViewTheme`): teal primary, white/grey
  surfaces, no Material pink tint. The canvas palette stays.
- Bundled DejaVu Sans Mono via `ProvideMonoStyle`/`MonoStyle` instead of `FontFamily.Monospace`
  (proportional on the web).
- Grey menu bar strip; one `LinkText` (#1A4FB0, underlined) for all links.
- Examples tree like ktoy (▸/▾, SemiBold headers, larger rows, dividers).
- `BigDialog` (window minus 16 dp) for Examples, Import/Export, Select properties, Help pages;
  short questions stay AlertDialogs.

**B. Menus / layout**
- ktoy `MenuButton`: one shared open-menu state, one click switches menus, clicking the open
  title closes it, Escape closes, bar scrolls sideways on narrow screens. App shortcuts are
  ignored while a menu is open.
- Bar shows the file name (and "set i/n") on the right instead of "PoCoView" on the left.
- Point Set menu starts with Examples…; "Info" becomes "Help". Radio groups in menus get a grey heading.
- Draggable split between canvas and widget columns (ktoy `SplitPane`).
- ktoy `ScrollbarBox` (vertical and horizontal thumbs).
- Examples dialog state (expanded groups, selection, scroll) survives closing.
- Implemented: dividers between all neighbours (canvas and each widget). Default shares: side by
  side canvas 2 : each widget 1, stacked all equal (replaces the fixed 0.45/0.55). If a side by side
  layout is narrower than 320 dp canvas + 260 dp per widget, the canvas keeps 320 dp and the widget
  columns scroll sideways (as before, now with a ScrollbarBox).

**C. Help / feedback**
- Help menu with pages (General, Properties, Optimize, Keyboard shortcuts) in a BigDialog,
  condensed from `USER_GUIDE.md`.
- Load/parse errors in a modal error dialog; the status line stays for info.
- Main Optimize actions are filled buttons, secondary ones outlined.
- About: license texts generated at build time (incl. DejaVu font).
- Implemented: Help menu = the four pages, then About PoCoView (ktoy order); the old shortcuts
  dialog is the Keyboard shortcuts page. Each paragraph starts with bold lead-in words (on the
  shortcuts page the key). The error dialog is titled "Error" and shows one message
  ("Cannot open X: …", "Cannot import the text: …", "Cannot apply the coordinates: …" for the Data
  widget's Apply, "Cannot load the examples: …"); the examples tree still shows the reason inline.
  Optimizer failures, the random set failure and other notes stay in the status line. Only the run
  buttons are filled; Repeat is outlined, the small Reset stays a text button. License texts:
  PoCoView's LICENSE and the DejaVu font license. As in ktoy, the Kotlin/Compose libraries are not
  listed; pyotlib2 (whose code is ported) has the same author and license, so no extra text.

**D. Remember / safeguard**
- Display settings (layout, max widgets, open widgets, point size, line colour/alpha, selected-point
  style, shown/drawn properties) are remembered across starts. Tests use in-memory prefs.
- Replace confirmation only when something would really be lost: the points differ from the last
  load/save. Wording like ktoy: one sentence, buttons [Replace]/[Cancel].
- Closing the window with unsaved changes asks Save… / Discard / Cancel.
- Save dialog: one button per format ("Save JSON", "Save text").
- Implemented: prefs as in ktoy (`Prefs.kt`, JVM tests `-Dpocoview.prefs=memory`, plus `removePref`).
  Remembered: layout, max. widgets, open widgets, point size, line colour and opacity, selected point
  (size, colour, lines through it, their colour and width), line arrangement and point labels,
  divider shares (side by side and stacked), and per property shown/drawn and highlight colour. Not
  remembered (not in the list): line width, Highlight, Incident only, Lock order type, mode.
  Display settings are written 0.3 s after the last change (one write per slider drag) and on quit.
  A property's switches are stored only when the user changes them, so a property an example switches
  on is not remembered (Triangulations stays off unless the user switched it on). Corrupt or
  out-of-range stored values give the default or are clamped to the slider range. "Reset to defaults"
  forgets the stored values of exactly the settings it resets.
- Implemented: the clean state is all sets of the file at the last load (also Random, Import,
  Examples) or successful Save file; undoing back to it is clean again. SVG/Ipe export and Copy do
  not count as saving. Confirm wording: "Load?" / "Import?" / "Generate?" / "Open?", text
  "<Action> discards your changes to <file name>.", buttons [<Action>] [Cancel].
- Implemented: "Save changes?" (text "<file name> has unsaved changes.") with Save… / Discard /
  Cancel. Save… opens the Save dialog; a successful save quits, a cancelled one (or Cancel in the
  Save dialog) cancels the quit. Window close and the macOS Quit/Cmd+Q go through it (ktoy's
  `closeRequests` + quit handler). Web: ktoy has no beforeunload prompt, so none here.
- Implemented: the Save dialog has no file name line (ktoy has none; the name is suggested by the
  file chooser or used for the download).

Decisions on open points:
- Status messages: pure info ("Saved …", "Loaded …") disappears after 3 s; optimizer results stay
  until the next message. The general-position warning is permanent.
- Right-click on the canvas: a context menu at a point (Select, Delete); right-click no longer
  selects/adds like a left click. No tooltips.
- Menu check items (Manfred, like ktoy): in the View menu a click on a check item (Line arrangement,
  Point labels, the widgets, Layout) keeps the menu open; Lock order type (Edit) closes it.
- Implemented: messages are classified where they are set (`showInfo` / `showNote`). Info, in green
  as in ktoy: Loaded, Saved (also SVG/Ipe), Copied. Everything else stays until the next message
  (optimizer results, aborted, failures, the "not in general position, fix that first" refusal, the
  collinear-point warning). The permanent general-position warning is the "NOT in general position"
  part of the status line, which follows the points. An edit still clears the message.
- Implemented: Delete in the context menu is one undo step like any delete. Right-click on empty
  space does nothing (no pan, no add).

Review fixes (decided with reviewer, following ktoy):
- 12: one "Save changes?" dialog before Load/Examples/Import/Random/Open file and window close/Cmd+Q:
  "Save JSON", "Save text", Discard, Cancel; a save goes on with the action, a failed or cancelled
  save cancels it. Replaces the [Replace]/[Cancel] confirm and the two-step close.
- 13: an unapplied coordinates edit in the Data widget counts as an unsaved change (saving the
  points does not apply it, so the dialog stays until Discard or Cancel).
- 14: the menu stays "Point Set"; only this spec's wording was fixed.
- 15: Help pages are plain paragraphs without bold lead-ins; only the keys on the shortcuts page are bold.
- 16: line width is remembered like the other Display settings.
- 17: "Reset to defaults" also resets the divider positions (and forgets them); property switches and
  colours stay.
- 18: "set i/n" stays in the menu bar and the toolbar.
- Second review: a Data widget draft is never saved implicitly; "Save changes?" shows the format
  buttons only when the points are unsaved (draft alone: Discard / Cancel). Switching sets with a
  draft (PageUp/PageDown, < >) asks the same instead of dropping it.

Other review fixes: a file that cannot be read or written shows the error dialog instead of crashing
(a failed save counts as not saved and does not quit); a canvas click that closes a menu does nothing
else; Escape closes a menu also after a View check item; the point context menu is headed "Point i";
Ctrl+click is a right-click on macOS; a layout switch keeps the zoom. On the web, Compose already
suppresses the browser's context menu.

Not taken over: simulator-specific parts (Step/Run bar, PC highlights, per-layer menus), Android-only
parts (PoCoView has jvm + js), ktoy's portrait window size, File → Empty.

## 2026-11-17: Triangulations off by default

Triangulations (exponential) is no longer shown by default. Example files may list property ids in
`"properties"` to switch them on when loaded (like ktoy's `# io: yes`); no example uses it yet.

## 2026-11-17: Header and status in the toolbar

The menu bar shows "PoCoView" in bold on the right (as in the ksat playground), on the web followed by
"Point configuration viewer". The bottom status bar is gone: the file name (with " · set i/n"), n, the
general position warning and the message moved to the right end of the toolbar row, one line with
ellipsis. The status text keeps a minimum width of 280 dp (Claude's decision, shown to Manfred):
with less room left in its row it wraps to a line of its own instead of shrinking to a few letters. The start set is the
example n13-c1-min-convex-3-4-5-holes, so that is the initial file name (was "points").

## 2026-11-18: Compact number fields

Manfred: integer inputs were huge (Material's text field is at least 280 dp wide and 56 dp high);
make them reasonable everywhere. Every number input (Random n/grid, family n, the Optimize widget's
parameters) is now one shared `NumberField` modelled on ktoy's steps/sec field: "label:" in front, a
low field with a 1 dp grey rounded border, as high as the buttons (40 dp), mono text centred
vertically, as wide as the longest allowed value (digits of the range's maximum, else 9 digits). The
border turns red when the text is not a number or outside the range; the existing error texts stay
below. Typing, validation and ranges are unchanged.

"−"/"+" buttons (step 1, clamped to the range) only on the point counts: Random n (1..200) and a
family's n (1..max n). Not on grid (a size up to 2^29, stepping by 1 is pointless) or the optimizer
parameters (the buttons would double the width of every parameter row).

## 2026-11-18: Order types in the Examples dialog

Manfred: add the order type database for n = 3 to 7 as an Examples group. The group "Order types" has
one entry per n, "n = 7 (135)"; the counts 1, 2, 3, 16, 135 match the files (size / (2n bytes)) and
are tested. The files are copied from data/otypes/ to composeApp/examples/order-types/ (Graz b08) and
embedded at build time by generateExamples (as hex strings, listed under "orderTypes" in index.json),
so there is no loading.

The selected entry shows "order type: − [ 1 ] + of 135" (the compact number field) and the short line
"Order types of 7 points (Graz order type database)". A number that is not in 1..M shows "1 to M" in
red and disables Load. The chosen number is remembered per n like a family's n.

Claude's choice (preferred by Manfred): Load opens the whole file at the chosen order type, so the
toolbar shows "otypes07 · set 17/135" and set navigation (PageUp/PageDown, < >) reaches the others.

## 2026-11-18: Welcome text

Manfred: the Welcome dialog should say briefly what PoCoView is for (you change a point set by
dragging points; the viewer is for exploring and visualising its properties). After several drafts
(one with a list of properties, dropped) the first line is now: "PoCoView is a viewer for point sets
in the plane. Change a point set by dragging points and explore and visualise its combinatorial
properties." The first paragraph of Help → General starts with the same text, followed by what an
order type is. Feedback box and buttons unchanged.

## 2026-11-18: Long press opens the context menu

Manfred: "On the phone a long press is like a right-click, so make the browser do that too." A touch
screen has no right-click, so a long press opens the same context menu as a right-click.

- Ported from ktoy (`EditGestures`): `MenuGestures(rightClick, longPress)` per platform with
  `LocalMenuGestures` for tests. Web: right-click and long press. JVM desktop: right-click only
  (like ktoy).
- A one-finger press held still for the long-press timeout (Compose's `longPressTimeoutMillis`)
  on a point opens the "Point i" menu at the press position. Moving more than the touch slop
  before that is a drag (or pan) as before; after the menu opened, moving and releasing do nothing
  (no tap action).
- Interpretation: a long press on empty space opens nothing (like a right-click there); its release
  then acts as a tap as before (Add adds a point, Move deselects).
- Help (General and Shortcuts) and the user guide say "right-click (long press on touch screens)".

## 2026-11-19: Snap with grid refinement

Manfred: points sit on the grid, and a cell of the line arrangement can be visible without containing
a grid point. Add a setting, off by default; when on, a released drag or an Add takes the 4 grid
points around the release position, and if none is possible, blows up the coordinates and tries
again until the point has room. Plus an info text: everything is computed with exact integers.

- Name: "Snap with grid refinement" (Manfred's suggestion "Allow floats & blow up grid" not taken:
  no float is ever stored). A switch in the Display widget under a new heading "Editing", with a
  round (i) that opens a short popover. Remembered like the other Display settings; "Reset to
  defaults" turns it off.
- Off: unchanged.
- On, at the release of a drag (not during it) or an Add at the real position p: the target cell
  is the cell of p in the line arrangement of the other points. A candidate is possible if it is in
  range, no duplicate, and has the same non-zero orientation as p with every pair of other points
  (so it is in no line through two others, and the order type is the one the user aimed at). Where
  p lies exactly on a line, either side is accepted. The 4 corners of the unit square around p are
  tried nearest first (ties: lower-left, lower-right, upper-left, upper-right).
- None possible: all coordinates and p are multiplied by 2 (exactly 2; the optimizers' internal
  blow-ups are not a separate operation), and the 4 corners are tried again; repeat.
- Lock order type (with no collinear points): the target cell is the point's current cell. A
  release outside it changes nothing (the point stays where the locked drag left it).
- The view zooms out by the same factor, so nothing visibly moves; the coordinates grow.
- The drag (or Add) including its refinement is one undo step. Undo does not change the view, so
  after undoing a refinement the picture is half as large per doubling.
- Limits: coordinates stay within ±2^29 (the app computes with exact Long arithmetic, not BigInt).
  If the next doubling would leave that range, or after the crash guard of 64 doublings, nothing
  changes and the status line says "No room for the point within ±2^29".
- p's signs (only the target) are computed in Double; every candidate is checked with exact
  integer orientations.
- Tests: `GridRefineTest` (cell without grid points needs 2 doublings, no doubling when a corner
  fits, nearest corner, Lock, no room, 2000 random sets and releases never hit the cap) and
  `GridRefineUiTest` (real drags and Add with the setting on and off, the view keeps the picture,
  undo, switch, popover, remembered, reset).

## 2026-11-20: Refit on resize

Request: refit the point set to the canvas when the canvas size changes (window resize, a widget
opens or closes). Manfred: "sounds reasonable, also for PoCoView". Same semantics as ArrView:
auto-fit while the user has not zoomed or panned.

Before: a size change kept the view's centre and scale (only Fit, loading and optimizer results
fitted), so opening a widget cut off points and closing one left the set small.

- Every canvas size change (window, opening/closing a widget, layout switch, dragging a divider)
  refits, unless the user zoomed or panned since the last fit; then the view keeps its centre and
  scale as before.
- Zoom or pan = wheel, pinch, two-finger pan, dragging empty space, +/- keys, Zoom in/out in the
  View menu.
- Reset (auto-fit again): every fit, i.e. Fit (menu, Home, 0), loading a file/example/random set,
  Import, Apply in the Data widget, switching sets, optimizer results.
- Editing does not count as zoom or pan: dragging or adding points, deleting, undo/redo, and the zoom
  out of "Snap with grid refinement" (that one only keeps the picture after doubling coordinates).
  Edits never refit by themselves; the next size change fits the edited set.
- Claude's decision: while a point is being dragged a size change keeps the view (no refit), so the
  picture cannot jump under the pointer; there is no refit after the release either (the next size
  change does it).
- Tests: `RefitOnResizeUiTest` (opening and closing all widgets and portrait keep every point inside
  the canvas and give exactly a fresh fit; after a key zoom, a wheel zoom or a pan the view is kept;
  Fit restores auto-fit; a drag and an Add do not count; a resize during a drag keeps the view).

Open questions:
- Should a size change that happened during a drag refit at the release? (Now: no.)
- Should undo/redo of an edit that moved points out of view refit? (Now: no, only size changes.)

## 2026-11-20: Export shows the drawing

Request: Manfred: "pocoview hat auch export, passt da alles? also nicht nur arrview sondern überall
export sinnvoll" (PoCoView also has export, is everything fine there? make export sensible
everywhere). Interpretation (coordinator, same as in ArrView): the SVG/Ipe picture shows what the
canvas draws for the drawing itself, with the Display settings; no UI chrome. JSON/text exports are
data and stay as they are.

Before: the export used a fixed point radius (6) and fixed structure pens (1.5 / 4), ignored the
line width for structures, drew polygon outlines fully opaque (canvas: 70 %; in Ipe even at the
13 % fill opacity), drew the lines through the selected point even when set to hide, never drew
them highlighted, and crashed on structures with a stale point index (the canvas skips those).

- Point radius = the Display point size; labels sit point size + 2 right/up of the point.
- Structures: pen 1.5 / 3.5 (emphasized) times the line width, polygon outline at 70 %, fill 13 %,
  segments 50 % / 100 % as on the canvas. Ipe: fill and outline are two paths (one opacity each).
- Lines through the selected point: hide = left out of the arrangement, normal = like the others,
  highlight = drawn in the incident colour and width (only while the arrangement is shown, as on
  the canvas). Ipe puts them in their own layer "incident".
- Structures with a vertex outside the point list are skipped.
- Sizes: canvas pixels = SVG units on the 1000-wide page; Ipe = half (500 pt box), the mark size is
  set absolutely (radius = 0.6 x size).
- Unchanged (Claude's reading, as the canvas look without the theme): points without a colour token
  are black on a white page, not the theme's onSurface.
- Open file (desktop): the "All point sets" filter label is built from the accepted extensions and
  now lists .pb08/.pb16/.pb32 too.
- Tests: `ExportTest` (point size, structure pens and outline opacity, hide/normal/highlight with and
  without arrangement, Ipe mark size and layers, stale indices, export from an `AppState`). Visually
  checked: app screenshot vs the SVG in headless Chrome and the Ipe file via iperender.

Open questions:
- Export the current view (zoom/pan crop of the canvas) instead of the whole set fitted to the page?
  (Now: fitted. Recommendation: keep fitted, a picture of the set should not depend on the window.)
- Should the selected point be marked in the export (selected colour, size, ring)? (Now: no.
  Recommendation: no; it is an editing state, but highlighted incident lines already show it.)
- The red rings around collinear points: not exported (a warning, not part of the drawing).
- Point size relative to the picture: the canvas fit leaves 85 % of the canvas, the page 920 of
  1000 units, so on a small canvas the points look larger relative to the set than in the export.
  Scale sizes by page / canvas size? (Now: no, the export does not depend on the window size.)
- Open file (desktop) starts with the JSON-only filter, so .txt/.b08 files are hidden until the user
  picks "All point sets". Start with "All point sets" instead? (Recommendation: yes.)


## 2026-11-21: Keyboard shortcuts

Request (all apps, ported from RotSysView): standard keyboard shortcuts. PoCoView's line widths stay
unchanged (Manfred: only the point items are relevant here).

- Ctrl/Cmd+O = Point Set > Open file… (with "Save changes?" first if there are unsaved changes),
  Ctrl/Cmd+S = Point Set > Save file… (the format dialog). The same actions as the menu entries.
- Both also work while a text field has focus or a menu is open, so the browser does not open its
  own Open/Save page. With Shift they do nothing.
- The menu entries show their keys on the right: Open file Ctrl+O, Save file Ctrl+S, Undo Ctrl+Z,
  Redo Ctrl+Shift+Z, Delete selected point Del, Fit 0, Zoom in +, Zoom out −. The widget items keep
  their "(1)".."(4)" in the label; check items (Lock order type F, Line arrangement L) show no key.
- Help > Keyboard shortcuts and doc/USER_GUIDE.md list Ctrl+O and Ctrl+S.
- macOS: inside a text field Ctrl+O is the system's "insert line break" (as in every Mac text
  field), so there Cmd+O opens; on the canvas both work.
- Web: Compose prevents the browser default only when no text field has focus, so main.kt adds a
  window keydown listener (bubble phase) that calls preventDefault for Ctrl/Cmd+O/S without Shift.
  (In the capture phase a focused text field dropped Cmd+O/S.) Claude's addition beyond the
  RotSysView port, needed for "the browser must not open its own dialogs".
- Tests: `SafeguardUiTest.ctrlOAndCtrlSWorkOnTheCanvasAndWhileTyping` (menu shows the keys, Ctrl+S
  and Cmd+O on the canvas and in the Data widget's coordinates field). Web, headless Chrome
  (scripts/probe/web_keys.py): Ctrl and Cmd with O and S, on the canvas and in the coordinates
  field: keydown defaultPrevented, Save file dialog opens, the file picker is called (or "Save
  changes?" first with an unapplied edit).
