"""One-off: builds composeApp/src/commonMain/composeResources/files/examples/ from the research scratch data."""
import json, os, sys

S = sys.argv[1]  # scratchpad dir
OUT = sys.argv[2]  # .../composeResources/files/examples
COLOR = {"B": "B", "BLUE": "B", "R": "R", "RED": "R"}

def read_psz(path):
    lines = [l.strip() for l in open(path) if l.strip()]
    n = int(lines[0]); pts = []
    for l in lines[1:]:
        t = l.split()
        p = [int(t[0]), int(t[1])]
        if len(t) > 2: p.append(COLOR[t[2].upper()])
        pts.append(p)
    assert len(pts) == n, path
    return pts

def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    keys = [k for k in obj if k != "points"]
    with open(path, "w") as f:
        f.write("{\n")
        for k in keys: f.write(f"  {json.dumps(k)}: {json.dumps(obj[k], ensure_ascii=False)},\n")
        f.write('  "points": [\n' + ",\n".join("    " + json.dumps(p) for p in obj["points"]) + "\n  ]\n}\n")

groups = []
def group(title, note, folder, entries):
    files = []
    for e in entries:
        e = dict(e, group=title)
        if "claim" in e and e["claim"] is None: del e["claim"]
        rel = f"{folder}/{e['name']}.json"
        dump(os.path.join(OUT, rel), e); files.append(rel)
    groups.append({"title": title, "note": note, "files": files})

# 1. Singletons
sing = []
for e in json.load(open(f"{S}/posezo_singletons.json")):
    if not e["file"]: print("skipped (no file):", e["name"]); continue
    sing.append({"name": e["name"], "description": e["description"], "reference": e["reference"],
                 "url": e["referenceUrl"], "source": e["url"], "points": read_psz(e["file"])})
group("Singletons", "Collected on PoSeZo (https://www.eurogiga-compose.eu/posezo.php)", "singletons", sing)

# 2. Crossing families
CF_REF = ("O. Aichholzer, J. Kynčl, M. Scheucher, B. Vogtenhuber, P. Valtr, On crossing-families in planar "
          "point sets, Comput. Geom. 107 (2022) 101899, arXiv:2109.10705")
K = {2: 4, 5: 5, 6: 6, 7: 9}
cf = []
for e in json.load(open(f"{S}/crossfam/sets.json")):
    n, fig = e["n"], e["figure"]; k = K[fig]
    assert f"no {k}-crossing family" in e["claim"]
    cf.append({"name": f"n{n}-no-{k}-crossing-family", "description": f"{n} points without a {k}-crossing family (Fig. {fig})",
               "reference": CF_REF, "url": "https://arxiv.org/abs/2109.10705", "source": e["source"],
               "claim": e["claim"], "points": e["points"]})
group("Crossing families", None, "crossing-families", cf)

# 3. Universal point sets
j = json.load(open(f"{S}/jgaa/sets.json"))
UR = ("J. Cardinal, M. Hoffmann, V. Kusters, On universal point sets for planar graphs, "
      "J. Graph Algorithms Appl. 19(1) (2015) 529-547, doi:10.7155/jgaa.00374")
us = []
for e in j["sets"]:
    n = e["n"]
    us.append({"name": f"n{n}-universal", "description": f"{n}-universal set of {n} points (every planar graph on {n} vertices embeds on it) (Fig. 5)",
               "reference": UR, "url": "https://jgaa.info/index.php/jgaa/article/view/paper374/2614",
               "source": f"Figure 5 ({e['subfigure']}) of the paper, coordinates from the PDF text layer ({j['source']['pdf_url']})",
               "claim": j["source"]["claim"], "points": e["points"]})
group("Universal point sets", None, "universal", us)

# 4. Miscellaneous
MR = "M. Scheucher, H. Schrezenmaier, R. Steiner, A note on universal point sets for planar graphs, GD 2019, arXiv:1811.06482"
L2 = [(612,666),(754,635),(415,709),(884,597),(596,695),(890,977),(384,716),(834,609),(424,707),(974,10),(890,962),(306,805),(301,810),(4,736),(0,735),(975,6),(980,0)]
L1 = [(214,0),(0,13),(2,16),(9,26),(124,12),(133,11),(148,9),(213,1),(211,4),(210,6),(116,179),(122,197)]
misc = [
    {"name": "n17-universal-4-connected-k11-17",
     "description": "17 points p1..p17: {p1..pk} is universal for 4-connected planar graphs on k vertices, k = 11..17 (Listing 2)",
     "reference": MR, "url": "https://arxiv.org/abs/1811.06482", "source": "Listing 2 of arXiv:1811.06482v3",
     "points": [list(p) for p in L2]},
    {"name": "n12-11-universal", "description": "11-universal set of 12 points (Listing 1)",
     "reference": MR, "url": "https://arxiv.org/abs/1811.06482", "source": "Listing 1 of arXiv:1811.06482v3",
     "points": [list(p) for p in L1]},
]
group("Miscellaneous", None, "misc", misc)

with open(os.path.join(OUT, "index.json"), "w") as f:
    json.dump({"groups": groups}, f, indent=2, ensure_ascii=False); f.write("\n")
for g in groups: print(g["title"], len(g["files"]))
