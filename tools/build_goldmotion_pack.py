#!/usr/bin/env python3
import json
import re
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GOLD_DIR = ROOT / "goldmotion" / "GoldMotion-CustomFX-Pack"
README_FILE = GOLD_DIR / "README.txt"
EFFECTS_DIR = GOLD_DIR / "effects"
OUT_PACK = ROOT / "app" / "src" / "main" / "assets" / "packs" / "goldmotion_customfx.effectspack"

def build():
    if not README_FILE.is_file() or not EFFECTS_DIR.is_dir():
        print(f"Missing Gold Motion sources in {GOLD_DIR}")
        return 1

    readme = README_FILE.read_text(encoding="utf-8")
    parts = readme.split("STRONG PRO EXAMPLES")
    main_section = parts[0]
    pro_section = parts[1] if len(parts) > 1 else ""

    category_map = {}
    groups = re.findall(r"^([A-Za-z0-9 &]+) \(\d+\)\n((?:  - .+\n)+)", main_section, re.MULTILINE)
    for group_name, items in groups:
        for fname in re.findall(r"\[([^\]]+)\]", items):
            category_map[fname] = group_name

    pro_matches = re.findall(r"^\s*([A-Za-z0-9 &]+)\s*-\s*([A-Za-z0-9 ]+)\s*\[([^\]]+)\]", pro_section, re.MULTILINE)
    pro_files = []
    for cat_name, eff_name, fname in pro_matches:
        category_map[fname] = cat_name.replace("&", "and").strip()
        pro_files.append(fname)

    pro_set = set(pro_files)
    all_files = sorted([f.name for f in EFFECTS_DIR.glob("*.xml")])
    ordered_files = [f for f in pro_files if f in all_files] + [f for f in all_files if f not in pro_set]

    entries = []
    for fname in ordered_files:
        xml_path = EFFECTS_DIR / fname
        root = ET.fromstring(xml_path.read_text(encoding="utf-8"))
        eff_id = root.attrib.get("id", f"gm.custom.{xml_path.stem}")
        eff_name = root.attrib.get("name", xml_path.stem)
        group = category_map.get(fname, "Custom")
        tags = [t.strip() for t in root.attrib.get("tags", "").split(",") if t.strip()]
        entries.append({
            "id": eff_id,
            "file": f"effects/{fname}",
            "name": eff_name,
            "group": group,
            "tags": tags
        })

    manifest = {
        "formatVersion": 1,
        "id": "goldmotion.customfx",
        "name": "Gold Motion Custom FX",
        "version": "1.0.0",
        "author": "Gold Motion",
        "description": "77 custom effects for Absent Motion Pro (68 originals + 9 Pro)",
        "effects": entries
    }

    OUT_PACK.parent.mkdir(parents=True, exist_ok=True)
    manifest_bytes = json.dumps(manifest, indent=2).encode("utf-8")

    with zipfile.ZipFile(OUT_PACK, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("manifest.json", manifest_bytes)
        for fname in ordered_files:
            xml_bytes = (EFFECTS_DIR / fname).read_bytes()
            zf.writestr(f"effects/{fname}", xml_bytes)

    print(f"Created {OUT_PACK} with {len(entries)} effects (Pro first: {len(pro_files)})")
    return 0

if __name__ == "__main__":
    sys.exit(build())
