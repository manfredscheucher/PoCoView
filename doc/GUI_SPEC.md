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
