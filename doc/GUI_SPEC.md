# GUI spec log

Requests and decisions about the GUI. Newest first.

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
- File menu starts with Examples…; "Info" becomes "Help". Radio groups in menus get a grey heading.
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

Not taken over: simulator-specific parts (Step/Run bar, PC highlights, per-layer menus), Android-only
parts (PoCoView has jvm + js), ktoy's portrait window size, File → Empty.

## 2026-11-17: Triangulations off by default

Triangulations (exponential) is no longer shown by default. Example files may list property ids in
`"properties"` to switch them on when loaded (like ktoy's `# io: yes`); no example uses it yet.
