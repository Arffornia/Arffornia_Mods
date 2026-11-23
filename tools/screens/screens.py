#!/usr/bin/env python3
"""Spec 007 screen tests: compare screenshots with approved baselines, or approve new ones.

usage: screens.py compare <report-dir> <shot-dir>...   exit 1 when a shot differs, 2 when one is missing
       screens.py approve <shot-dir>...                 copy the shots into the baselines

Baselines are stored at half the shot size to keep the repository small; shots are reduced the same
way before the comparison. A pixel differs when a channel moves more than PIXEL_TOL (Mesa/llvmpipe
rounding); a shot fails when more than MAX_RATIO of its unmasked pixels differ (spec 007 item 8: 1 %).
masks.json: {"<baseline name>": [[x, y, w, h], ...]} in baseline pixels, for text that changes on
purpose (the mod version).
"""
import json
import pathlib
import shutil
import sys

import numpy as np
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASELINES = ROOT / "src/test/screens/baselines"
MASKS = json.loads((ROOT / "tools/screens/masks.json").read_text())
PIXEL_TOL = 24
MAX_RATIO = 0.01


def reduced(path: pathlib.Path) -> Image.Image:
    im = Image.open(path).convert("RGB")
    return im.resize((im.width // 2, im.height // 2), Image.LANCZOS)


def shots(dirs):
    for d in dirs:
        yield from sorted(pathlib.Path(d).glob("*.png"))


def compare(report: pathlib.Path, dirs) -> int:
    report.mkdir(parents=True, exist_ok=True)
    worst = 0
    seen = set()
    tiles = []
    for shot in shots(dirs):
        seen.add(shot.name)
        base_path = BASELINES / shot.name
        act = reduced(shot)
        tiles.append(act)
        if not base_path.exists():
            print(f"NEW     {shot.name}: no baseline (run make screens-approve if it is right)")
            worst = max(worst, 2)
            continue
        a = np.asarray(act, dtype=np.int16)
        b = np.asarray(Image.open(base_path).convert("RGB"), dtype=np.int16)
        if a.shape != b.shape:
            print(f"SIZE    {shot.name}: {a.shape[1]}x{a.shape[0]} vs baseline {b.shape[1]}x{b.shape[0]}")
            worst = max(worst, 1)
            continue
        keep = np.ones(a.shape[:2], dtype=bool)
        for x, y, w, h in MASKS.get(shot.name, []):
            keep[y:y + h, x:x + w] = False
        diff = (np.abs(a - b).max(axis=2) > PIXEL_TOL) & keep
        ratio = diff.sum() / max(1, keep.sum())
        out = (b * 0.35).astype(np.uint8)
        out[~keep] = (out[~keep] * 0.5 + np.array([40, 80, 200]) * 0.5).astype(np.uint8)
        out[diff] = [255, 40, 40]
        Image.fromarray(out).save(report / f"diff.{shot.name}")
        ok = ratio <= MAX_RATIO
        print(f"{'PASS' if ok else 'FAIL'}    {shot.name}: {ratio * 100:.3f}% of pixels differ (limit {MAX_RATIO * 100:.1f}%)")
        if not ok:
            worst = max(worst, 1)
    for missing in sorted(p.name for p in BASELINES.glob("*.png") if p.name not in seen):
        print(f"MISSING {missing}: baseline without a shot")
        worst = max(worst, 2)
    if tiles:
        w = max(t.width for t in tiles)
        sheet = Image.new("RGB", (w, sum(t.height for t in tiles)), "black")
        y = 0
        for t in tiles:
            sheet.paste(t, (0, y))
            y += t.height
        sheet.save(report / "contact-sheet.png")
    return worst


def approve(dirs) -> int:
    BASELINES.mkdir(parents=True, exist_ok=True)
    for shot in shots(dirs):
        reduced(shot).save(BASELINES / shot.name, optimize=True)
        print(f"approved {shot.name}")
    return 0


if __name__ == "__main__":
    if len(sys.argv) >= 4 and sys.argv[1] == "compare":
        sys.exit(compare(pathlib.Path(sys.argv[2]), sys.argv[3:]))
    if len(sys.argv) >= 3 and sys.argv[1] == "approve":
        sys.exit(approve(sys.argv[2:]))
    print(__doc__)
    sys.exit(2)
