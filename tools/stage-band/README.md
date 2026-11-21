# Stage band snapshot (spec 018 prototype)

Produces `src/main/resources/assets/arffornia/ui/band/band.json` (read by the native LDLib2 screen) and
`band.html` (shown by the WebGUI screen) from the progression graph data, so both renderers draw the
same layout and neither recomputes it.

1. `python3 make_band_input.py graph-data.json band-input.json header.json`: Mekanism stages 1 to 4 of
   the spec 017 fixture (`graph-data.json` is the web prototype's data: nodes, done set, icons).
2. Substitute `band-input.json` for `__DATA__` in the validated `graph-template.html`, then
   `node extract.mjs template-band.html layout.json shot.png` (Playwright, `CHROME_PATH` set): runs the
   template's ELK layout and reads node positions, link paths and stage columns back from the DOM.
3. `python3 build_band.py <dir with the three JSON files and band.template.html> <fonts dir> <out dir>`:
   writes `band.json` and the self-contained `band.html` (fonts and item icons inlined).

## Bench on a real display

`bench/` drives a full-pack client next to these scripts: `client/` (the pack, plus this jar, LDLib2 2.2.2 and
WebGUI 2.0.0), `mc-home/` (portablemc), `wmc-runtime/`, and `cef-libs/` and `extra-libs/` (Nix symlinkJoins of
the CEF and GLFW libraries listed in the spec 018 report). Run inside `nix shell nixpkgs#portablemc`:

1. `RES=1920x1080 JDK_LIB=... bench/start.sh label`: starts the client, creates or opens the `band-bench` world.
2. `bench/follow.sh label &` then `bench/cmd.sh bench` (both screens) or `bench/cmd.sh benchweb:60,30,15`
   (web frame-rate sweep). Results land in `client/arffornia-band/bench-*.json`; `follow.sh` appends PSS and
   browser CPU per phase to `mem.jsonl`.
3. `bench/cmd.sh shot` takes a screenshot, `bench/kill.sh` stops the client.
