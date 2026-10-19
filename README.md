# PoCoView

Point Configuration Viewer: look at, edit and analyze planar point sets and their order types,
right in the browser.

**Web app:** https://manfredscheucher.github.io/PoCoView/

Drag points around and watch how convex hull, onion layers, crossing number, k-holes, k-gons and the
number of triangulations change. Lock the order type to move points only inside their cell of the
line arrangement, restrict everything to the structures through one point, and minimize or beautify
coordinates without changing the order type. Reads and writes the usual formats, including the
order type database files (`.b08`, `.b16`).

PoCoView is the successor of qtvis and the pyotlib2 editor. It is written in Kotlin Multiplatform
(Compose) and also runs as a desktop app.

A short explanation of all features: [doc/USER_GUIDE.md](doc/USER_GUIDE.md).

## Run it locally

```
./scripts/runWeb.sh       # browser, http://localhost:8607/
./scripts/runJVM.sh       # desktop app
./scripts/test.sh         # unit tests
```

Deploy to GitHub Pages: `./scripts/deployPages.sh` builds the site and commits it to the
`gh-pages` branch; then `git push origin gh-pages`.

Example data: `data/otypes/` (all order types for n = 3..8), `data/posezo/`.

## License

MIT, see [LICENSE](LICENSE).
