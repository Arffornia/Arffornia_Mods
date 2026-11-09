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
