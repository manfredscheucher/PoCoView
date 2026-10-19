# ordertypes-kotlin

Interactive point-set / order type explorer (Kotlin Multiplatform + Compose), successor of
qtvis and the pyotlib2 editor. Main target: a website; also a desktop (JVM) app.

Load and save point sets (asc/psz, json, Graz `.b08/.b16/.b32` databases), move/add/delete points,
and count or visualize convex hull, onion layers, crossings, k-holes, k-gons and triangulations
in the modes *count*, *color* and *incident* (structures containing the selected point).
Beautify and minimize coordinates while keeping the order type.

```
./scripts/runWeb.sh       # browser, http://localhost:8607/
./scripts/runJVM.sh       # desktop app
./scripts/test.sh         # unit tests
./scripts/deployPages.sh  # build + commit to gh-pages (then: git push origin gh-pages)
```

Example data: `data/otypes/` (all order types n=3..8), `data/posezo/`.
Specification, decisions and test checklist: [doc/SPEC.md](doc/SPEC.md).
