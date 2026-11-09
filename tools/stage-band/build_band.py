import base64
import json
import re
import sys
from pathlib import Path

band_dir, fonts_dir, out_dir = map(Path, sys.argv[1:4])
data = json.loads((band_dir / "band-input.json").read_text())
layout = json.loads((band_dir / "layout.json").read_text())
header = json.loads((band_dir / "header.json").read_text())
by_key = {n["k"]: n for n in data["nodes"]}
NODE = 44


def flatten(d: str) -> list[list[float]]:
    tokens = re.findall(r"[MLQ]|-?[\d.]+(?:e-?\d+)?", d)
    pts: list[list[float]] = []
    i = 0
    while i < len(tokens):
        cmd = tokens[i]
        if cmd in ("M", "L"):
            pts.append([float(tokens[i + 1]), float(tokens[i + 2])])
            i += 3
        elif cmd == "Q":
            cx, cy, x, y = map(float, tokens[i + 1:i + 5])
            x0, y0 = pts[-1]
            for s in range(1, 7):
                t = s / 6
                pts.append([(1 - t) ** 2 * x0 + 2 * (1 - t) * t * cx + t * t * x,
                            (1 - t) ** 2 * y0 + 2 * (1 - t) * t * cy + t * t * y])
            i += 5
        else:
            raise ValueError(f"unsupported path command {cmd}")
    return [[round(x, 2), round(y, 2)] for x, y in pts]


cols = layout["cols"]
band = {
    "schema": "arffornia.stage-band/0",
    "graph": {"k": "mekanism", "n": "Mekanism"},
    "stages": [1, 2, 3, 4],
    "source": "spec 017 fixture current-tree-2026-07-31, ELK layout from graph-template.html (wide view)",
    "header": header,
    "node": NODE,
    "bounds": {"x0": min(c["x"] for c in cols), "y0": min(c["y"] for c in cols),
               "x1": max(c["x"] + c["w"] for c in cols), "y1": max(c["y"] + c["h"] for c in cols)},
    "cols": cols,
    "nodes": [{"k": n["k"], "t": by_key[n["k"]]["t"], "caption": n["caption"], "icon": by_key[n["k"]]["i"],
               "state": n["state"], "x": n["x"], "y": n["y"]} for n in layout["nodes"]],
    "edges": [{"from": e["from"], "to": e["to"], "done": e["done"], "d": e["d"], "pts": flatten(e["d"])}
              for e in layout["edges"]],
    "junctions": layout["junctions"],
}
out_dir.mkdir(parents=True, exist_ok=True)
(out_dir / "band.json").write_text(json.dumps(band, separators=(",", ":")))

font = lambda name: base64.b64encode((fonts_dir / name).read_bytes()).decode()
icons = {k: v for k, v in data["icons"].items()}
html = (band_dir / "band.template.html").read_text()
html = (html.replace("__MONTSERRAT__", font("Montserrat-400.woff2"))
        .replace("__CHAKRA700__", font("ChakraPetch-Bold.ttf"))
        .replace("__BAND__", json.dumps(band, separators=(",", ":")))
        .replace("__ICONS__", json.dumps(icons, separators=(",", ":"))))
(out_dir / "band.html").write_text(html)
print("band.json", (out_dir / "band.json").stat().st_size, "band.html", (out_dir / "band.html").stat().st_size)
