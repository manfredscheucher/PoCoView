# PoCoView user guide

## Basics

- **Points** have integer coordinates (y axis up). The point set should be in general position:
  points on a common line get a red ring, and the properties then show "not in general position".
- **Line arrangement**: the line through every pair of points. Its cells are exactly the regions in
  which a point can move without changing the order type. "Hide lines through the selected point"
  is on by default (Display widget: "Lines through it: hide") so you can see the cell of the selected
  point; "normal" draws them like the others, "highlight" in their own colour and width.
- **Tools** (toolbar): Move (drag points, click to select), Add (click empty space), Delete (click
  a point). Drag empty space to pan, use the wheel or pinch to zoom.
- **Lock order type** (toolbar chip): points cannot be dragged out of their cell, so the order type
  stays the same.
- Undo/redo are in the Edit menu.

## Widgets

The panels are widgets: Properties, Points, Optimize, Data and Display. The buttons in the toolbar
(also View menu, keys 1 to 5) show or hide them. In landscape each open widget is a column right of
the canvas, in the order they were opened; in portrait they are stacked below the canvas.

At most k widgets are open (Display widget, "Max. open columns", default 2). Opening one more closes
the one that was clicked least recently (opening a widget or clicking into it counts).

The Display widget sets how things are drawn: point size, line width, colour and opacity of the line
arrangement, labels, the selected point (size, colour, lines through it), the number of columns and
the layout (auto/portrait/landscape).

## Properties

The Properties widget lists the properties you picked with View → Select properties…. All of them
depend only on the order type, so they are recomputed only when the order type changes.

| Property | Meaning |
|---|---|
| Convex hull | number of points on the convex hull |
| Onion layers | number of nested convex layers (peel the hull repeatedly) |
| Crossing number | crossings of the complete straight-line graph = number of convex 4-gons |
| Exit edges | edges (a, b) whose witness point c can cross them alone; their orientations determine the order type. With "incident only": the edges that have the selected point as witness |
| k-holes | empty convex k-gons (no point inside), k = 3..7 |
| Convex k-gons | k points in convex position, k = 4..7 |
| Triangulations | number of triangulations (exponential, only up to n = 16) |

Two switches control the view:

- **Highlight**: draw the structures (filled polygons, crossing diagonals) of the properties whose
  switch on the right is on. With Highlight off nothing is drawn; the per-property switches only
  take effect while Highlight is on.
- **Incident only**: count and draw only the structures that contain the selected point.

At most 3000 structures per property are drawn; the panel says so if there are more. The highlight
colour of each property can be changed in "Select properties…".

## Points

Delete the selected point, or generate a random point set in general position (n points on a grid
of the given size).

## Optimize

All optimizers are in the Optimize widget.

Minimize and beautify keep the order type. Both are ports of pyotlib2 (which took them from the old
pyotlib):

- **Minimize coordinates** (M): makes the coordinates as small as possible. Implementations: move
  points to even coordinates and halve, with random restarts (pyotlib2 `minimize_coords`, or the old
  `minimizeIntegerCoordinates` with blow-up, sheering, diameter and symmetric options), or a pattern
  search that perturbs the points towards their centre.
- **Beautify** (B): spreads the points nicely. Beautify 1 moves every coordinate towards the middle
  of its allowed range. Beautify 2 optimizes a quality function (no thin triangles, even distances)
  with Nelder-Mead, hull fixed. Each in the pyotlib2 and in the old pyotlib version.

The property optimizers may change the order type. They use the property chosen in the widget:

- **Minimize w.r.t. property**: the old `minimizeIntegerCoordinates` (its parameters) with
  `ensure_property`: coordinates shrink and the order type may change as long as the property does
  not get worse. A result that is worse anyway (only some triples are checked during the run) is
  undone. With Loop it runs again and again.
- **Search** (ports of the old pyotlib scripts): PointWalk (depth-first search over moves of single
  points into neighbouring cells, optionally simulated annealing), PointWalkLocCr (the same, a move
  must not be worse than the best so far), PointMove (one pass over the neighbouring configurations)
  and BrownianMinimization (random moves, by default keeping the diameter from growing).
- **qtvis minimizer** (Enter, "Try to minimize"): port of the qtvis "try to minimize". Pushes points
  just past their cell walls, looking for a smaller value. Enter always uses the chosen property.

While a property optimizer runs, a "Searching…" dialog shows the progress and has an Abort button.
Configurations with an equal value are taken over, worse ones are not. The search stops at the
first improvement, unless **Loop** is on (in the widget or in the dialog); then it goes on until you
abort.

## Data

The coordinates as text, one `x y` per line. Edit them and press Apply, or Revert to go back. Below
are the λ matrix (λ[i][j] = number of points left of i→j) and the order type hash.

## Files

| Format | Content |
|---|---|
| `.asc`, `.psz`, `.txt` | a line with n, then `x y [color]` per point; several sets may follow |
| `.json` | `[[x,y],...]` per set |
| `.b08`, `.b16`, `.b32` | order type database: unsigned little-endian `x1 y1 x2 y2 ...`; n is read from the file name (`otypes05.b08`, `points_n12.b08`) |

- File → Open… loads a file.
- File → Import (paste)… takes the same formats as text pasted into a dialog; binary formats as a
  hex dump (`00 00 01 fe ...`) together with n.
- File → Save… asks for the format, and whether to save only the current set or all sets of the
  file.
- File → Export SVG saves a picture of the current view.

Files with several sets (e.g. `otypes06.b08`) can be browsed with the arrows in the toolbar or
PageUp/PageDown.

## Keyboard

| Key | Action |
|---|---|
| Esc | deselect |
| Del | delete the selected point |
| Ctrl+Z / Ctrl+Shift+Z | undo / redo |
| + / - | zoom |
| Home | fit |
| V / A / D | tools Move / Add / Delete |
| F | lock order type |
| L | line arrangement |
| H | highlight |
| I | incident only |
| M | minimize coordinates |
| B | beautify |
| Enter | qtvis minimizer |
| PageUp / PageDown | previous / next set |
