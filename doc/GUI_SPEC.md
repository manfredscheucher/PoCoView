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

**D. Remember / safeguard**
- Display settings (layout, max widgets, open widgets, point size, line colour/alpha, selected-point
  style, shown/drawn properties) are remembered across starts. Tests use in-memory prefs.
- Replace confirmation only when something would really be lost: the points differ from the last
  load/save. Wording like ktoy: one sentence, buttons [Replace]/[Cancel].
- Closing the window with unsaved changes asks Save… / Discard / Cancel.
- Save dialog: one button per format ("Save JSON", "Save text").

Decisions on open points:
- Status messages: pure info ("Saved …", "Loaded …") disappears after 3 s; optimizer results stay
  until the next message. The general-position warning is permanent.
- Right-click on the canvas: a context menu at a point (Select, Delete); right-click no longer
  selects/adds like a left click. No tooltips.

Not taken over: simulator-specific parts (Step/Run bar, PC highlights, per-layer menus), Android-only
parts (PoCoView has jvm + js), ktoy's portrait window size, File → Empty.

## 2026-11-17: Triangulations off by default

Triangulations (exponential) is no longer shown by default. Example files may list property ids in
`"properties"` to switch them on when loaded (like ktoy's `# io: yes`); no example uses it yet.
