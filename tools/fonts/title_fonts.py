#!/usr/bin/env python3
"""Writes the title screen's font definitions: one per family and oversample tier (see TitleFonts.java).

usage: tools/fonts/title_fonts.py   (from the repository root; rewrites src/main/resources/assets/arffornia/font/)
"""
import json
import pathlib
import shutil

FONT_DIR = pathlib.Path("src/main/resources/assets/arffornia/font")
TIERS = [0.5, 0.625, 0.8, 1, 1.25, 1.6, 2, 2.5, 3.2, 4, 5, 6.4, 8, 10, 12.5, 16, 20]
FAMILIES = {
    "display": "chakra_petch_bold.ttf",
    "ui": "chakra_petch_medium.ttf",
    "label": "montserrat_semibold.ttf",
    "body": "montserrat_medium.ttf",
    "strong": "montserrat_bold.ttf",
}


def tier_name(tier: float) -> str:
    return "o" + (str(int(tier)) if float(tier).is_integer() else str(tier).replace(".", "_"))


for family, ttf in FAMILIES.items():
    out = FONT_DIR / family
    shutil.rmtree(out, ignore_errors=True)
    out.mkdir(parents=True)
    for tier in TIERS:
        provider = {"type": "ttf", "file": f"arffornia:{ttf}", "size": 12, "oversample": tier, "shift": [0, 0]}
        doc = {"providers": [provider, {"type": "reference", "id": "minecraft:default"}]}
        (out / f"{tier_name(tier)}.json").write_text(json.dumps(doc) + "\n")
