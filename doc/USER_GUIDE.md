# PoCoView user guide

## Basics

- **Points** have integer coordinates (y axis up). The point set should be in general position:
  points on a common line get a red ring, and the properties then show "not in general position".
- **Line arrangement**: the line through every pair of points. Its cells are exactly the regions in
  which a point can move without changing the order type. "Hide lines through the selected point"
  is on by default (Display widget: "Lines through it: hide") so you can see the cell of the selected
  point; "normal" draws them like the others, "highlight" in their own colour and width.
- **Mode** (toolbar): Move (drag points, click to select), Add (click empty space), Delete (click
  a point). Drag empty space to pan, use the wheel or pinch to zoom. Right-click a point for a small
  menu: Select, Delete.
- **Lock order type** (toolbar chip): points cannot be dragged out of their cell, so the order type
  stays the same.
- Undo/redo are in the Edit menu.
- The **Help** menu has short pages (General, Properties, Optimize, Keyboard shortcuts) and About.
- Info like "Loaded …" or "Saved …" shows in the status line and fades after 3 s; optimizer results
  stay until the next message. A file or text that cannot be read opens an error dialog.

## Widgets

The panels are widgets: Properties, Optimize, Data and Display. The buttons in the toolbar
(also View menu, keys 1 to 4) show or hide them. In landscape each open widget is a column right of
the canvas, in the order they were opened; in portrait they are stacked below the canvas, each with
its own part of the height. The dividers between the canvas and the widgets, and between the widgets,
can be dragged.

At most k widgets are open (Display widget, "Max. widgets", default 2). Opening one more closes
the one that was clicked least recently (opening a widget or clicking into it counts).

The Display widget sets how things are drawn: point size, line width, colour and opacity of the line
arrangement, labels, the selected point (size, colour, lines through it), the number of widgets and
the layout (auto/portrait/landscape). "Reset to defaults" puts all of them and the divider positions
back.

These display settings, the open widgets, the divider positions, and the shown/drawn properties with
their colours are remembered for the next start.

## Properties

The Properties widget lists the properties you picked with View → Select properties…. Most of them
depend only on the order type, so they are recomputed only when the order type changes; the
monochromatic ones also depend on the colours.

| Property | Meaning |
|---|---|
| Convex hull | number of points on the convex hull |
| Onion layers | number of nested convex layers (peel the hull repeatedly) |
| Crossing number | crossings of the complete straight-line graph = number of convex 4-gons |
| Exit edges | edges (a, b) whose witness point c can cross them alone; their orientations determine the order type. With "incident only": the edges that have the selected point as witness |
| k-holes | empty convex k-gons (no point inside), k = 3..7 |
| Convex k-gons | k points in convex position, k = 4..7 |
| Crossing family | largest set of segments that pairwise cross (drawn red and bold; up to n = 50). Aichholzer, Kynčl, Scheucher, Vogtenhuber, Valtr, Comput. Geom. 107 (2022) |
| Triangulations | number of triangulations (exponential, only up to n = 16; off by default) |
| Monochromatic convex k-holes | bicolored sets: empty convex k-gons (no point of any colour inside) with vertices of one colour, k = 3..5 (old pyotlib) |
| Monochromatic general k-holes | the same for simple, not necessarily convex empty polygons, k = 4, 5 |
| Monochromatic k-islands | k points of one colour that are all points of S in their convex hull, k = 4..6; drawn through the hull vertices |

Two switches control the view:

- **Highlight**: draw the structures (filled polygons, crossing diagonals) of the properties whose
  switch on the right is on. With Highlight off nothing is drawn; the per-property switches only
  take effect while Highlight is on.
- **Incident only**: count and draw only the structures that contain the selected point.

At most 3000 structures per property are drawn; the panel says so if there are more. The highlight
colour of each property can be changed in "Select properties…".

## Optimize

The Optimize widget shows the range of the coordinates and has one box per optimizer. Each box has
its own parameters (Reset restores them), a run button and **Repeat**: Repeat runs again and again
until you press Abort in the progress dialog. A whole run is one undo step.

- **Minimize** (M) keeps the order type. Methods: *even + halve* (pyotlib2 `minimize_coords`: move
  points to even coordinates and halve, with random restarts and blow-ups) and *perturb* (pattern
  search moving the points towards their centre).
- **Minimize w.r.t. property**: the old `minimizeIntegerCoordinates` with `ensure_property`:
  coordinates shrink and the order type may change as long as the chosen property does not get
  worse. A result that is worse anyway (only some triples are checked during the run) is undone.
- **Beautify** (B) keeps the order type: pyotlib2 `beautify2_coords` optimizes a quality function (no
  thin triangles, even distances) with Nelder-Mead, hull fixed.
- **Search** (ports of the old pyotlib scripts) may change the order type: PointWalk, PointWalkLocCr,
  PointMove and Brownian. Configurations with an equal value are taken over, worse ones not; it
  stops at the first improvement (Repeat: goes on).
- **qtvis minimizer** (Enter): port of the qtvis "try to minimize" for the chosen property.

## Data

The coordinates as text, one `x y` or `x y color` per line. Edit them and press Apply, or Revert to
go back. Below are the λ matrix (λ[i][j] = number of points left of i->j, up to 30 points) and the
order type hash.

## Point Set menu

- **Examples…**: collapsible groups: Families (generated: Horton sets, convex position),
  Singletons (interesting sets with their original sources, in categories) and Links. Select a family
  or set (a family also asks for n), then Load. Please send interesting sets or feedback to
  manfred.scheucher@proton.me (the address is clickable).
- **Import…**: paste one or more point sets; the format is detected:
  - JSON: `{"points": [[x, y], [x, y, "color"], ...]}`, one set per line (a single set may also
    span several lines),
  - text: a line n, then exactly n lines `x y [color]`, sets one after another,
  - text with lines `x y [color]` (one set).
- **Random…**: n random points in general position on a grid.
- **Export…**: all sets as JSON (one line per set) or text (n, then n point lines, per set), to copy.
- **Open file…** reads `.json`, `.txt`/`.asc`/`.psz` (the same text format; files may hold several
  sets) and the order type database formats `.b08`, `.b16`, `.b32` (unsigned little-endian
  `x1 y1 x2 y2 ...`, n from the file name, e.g. `otypes05.b08`).
- **Save file…** writes all sets of the file: Save JSON or Save text (same layout as Export).
- **Export SVG** / **Export Ipe**: a picture with the line arrangement and the highlighted
  structures; Ipe files open in the Ipe editor.

If the points changed since they were loaded or saved, Examples, Import, Random and Open file first
ask "Save changes?": Save JSON or Save text saves and then goes on, Discard goes on without saving,
Cancel stops. An unapplied edit in the Data widget counts as a change. On the desktop, closing the
app asks the same.

Files with several sets can be browsed with the arrows in the toolbar or PageUp/PageDown; after
loading such a file a short note says so (can be switched off).

## Keyboard

| Key | Action |
|---|---|
| Esc | deselect, or close the open menu |
| Del or Backspace | delete the selected point |
| Ctrl+Z | undo |
| Ctrl+Shift+Z or Ctrl+Y | redo |
| + / - | zoom in / out |
| Home or 0 | fit |
| V / A / D | mode Move / Add / Delete |
| F | lock order type |
| L | line arrangement |
| H | highlight |
| I | incident only |
| 1 to 4 | show or hide Properties, Optimize, Data, Display |
| M | minimize |
| B | beautify |
| Enter | qtvis minimizer |
| PageUp or , | previous set of the file |
| PageDown or . | next set of the file |
| Mouse | drag a point to move it, drag empty space to pan, wheel or pinch to zoom, right-click a point for its menu |

On a Mac, Cmd works like Ctrl and Ctrl+click like a right-click. The keys do nothing while you type
in a field or a menu is open (except Esc).
