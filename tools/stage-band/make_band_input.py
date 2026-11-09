import json, sys
src, out_data, out_header = sys.argv[1:4]
d = json.load(open(src))
band = [n for n in d["nodes"] if n["g"] == "mekanism" and n["s"] <= 4]
keys = {n["k"] for n in band}
needed = {n["i"] for n in band if n["i"]} | {e["i"] for e in d["elevator"]}
sub = dict(d, graphs=[g for g in d["graphs"] if g["k"] == "mekanism"], nodes=band,
           icons={k: v for k, v in d["icons"].items() if k in needed})
json.dump(sub, open(out_data, "w"))
done = set(d["done"]); cur = d["current"]
stage_nodes = [n for n in d["nodes"] if n["s"] == cur]
stage = next(s for s in d["stages"] if s["n"] == cur)
header = {"stage": {"n": cur, "name": stage["name"], "done": sum(n["k"] in done for n in stage_nodes),
                    "total": len(stage_nodes), "threshold": stage["th"]},
          "elevator": {"phase": cur, "phases": len(d["stages"]), "deliveries": d["elevator"]}}
json.dump(header, open(out_header, "w"), indent=1)
print(len(band), "nodes", header)
