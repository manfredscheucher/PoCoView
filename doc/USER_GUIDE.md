# PoCoView user guide

## Basics

- **Points** have integer coordinates (y axis up). The point set should be in general position:
  points on a common line get a red ring, and the properties then show "not in general position".
- **Line arrangement**: the line through every pair of points. Its cells are exactly the regions in
  which a point can move without changing the order type. Lines through the selected point are
  hidden so you can see its cell (View menu).
- **Tools** (toolbar): Move (drag points, click to select), Add (click empty space), Delete (click
  a point). Drag empty space to pan, use the wheel or pinch to zoom.
- **Lock OT**: points cannot be dragged out of their cell, so the order type stays the same.
- Undo/redo, point size, selected point size and line width are in the Edit and View menus.

## Properties

The Properties tab lists the properties you picked with "Select properties…". All of them depend
only on the order type, so they are recomputed only when the order type changes.

| Property | Meaning |
|---|---|
| Convex hull | number of points on the convex hull |
| Onion layers | number of nested convex layers (peel the hull repeatedly) |
| Crossing number | crossings of the complete straight-line graph = number of convex 4-gons |
| k-holes | empty convex k-gons (no point inside), k = 3..7 |
| Convex k-gons | k points in convex position, k = 4..7 |
| Triangulations | number of triangulations (exponential, only up to n = 16) |

Two switches control the view:

- **Highlight**: draw the structures (filled polygons, crossing diagonals) of every property whose
  switch on the right is on. Off: numbers only.
- **Incident only**: count and draw only the structures that contain the selected point.

At most 3000 structures per property are drawn; the panel says so if there are more.

## Optimize

Coordinate optimizers keep the order type (ports of pyotlib2):

- **Minimize coordinates** (`minimize_coords`): move points to even coordinates and halve, with
  random restarts; makes the coordinates as small as possible.
- **Beautify, gradient descent** (`beautify_coords`): moves every coordinate towards the middle of
  its allowed range.
- **Beautify, Nelder-Mead** (`beautify2_coords`): optimizes a quality function (no thin triangles,
  even distances) with the hull fixed, then minimizes.

Property optimizers may change the order type:

- **Random walk**: random small moves, kept if the chosen property does not get worse (min or max).
- **qtvis minimizer** (Enter): port of the qtvis "try to minimize"; pushes points just past their
  cell walls and stops at the first configuration with a smaller value.

## Files

| Format | Content |
|---|---|
| `.asc`, `.psz`, `.txt` | a line with n, then `x y [color]` per point; several sets may follow |
| `.json` | `[[x,y],...]` per set |
| `.b08`, `.b16`, `.b32` | order type database: unsigned little-endian `x1 y1 x2 y2 ...`; n is read from the file name (`otypes05.b08`, `points_n12.b08`) |

Files with several sets (e.g. `otypes06.b08`) can be browsed with the arrows in the toolbar or
PageUp/PageDown. File → Export SVG saves a picture of the current view.

## Keyboard

Info → Keyboard shortcuts.
