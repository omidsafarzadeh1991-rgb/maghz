#!/usr/bin/env python3
"""Build app/src/main/assets/content.json from an export of the Brain Library database.

Usage:  python3 tools/build_content.py <export_dir>

<export_dir> holds the ArtifactData export layout:
  brains/<brainId>.json                     brain metadata
  brains/<brainId>/lessons/<lessonId>.json  one lesson each

The app reads this file offline (bundled in the APK) and downloads newer copies
of it from GitHub whenever the phone has internet. `rev` must only ever grow.
"""
import json, os, sys, glob, datetime

ORDER = ["frontend", "backend", "developer", "programmer", "security", "network", "it", "automation"]
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "content.json")


def load(path):
    with open(path, encoding="utf-8") as f:
        d = json.load(f)
    return d.get("data", d) if isinstance(d, dict) else d


def main(src):
    brains, lessons = [], []
    for p in glob.glob(os.path.join(src, "brains", "*.json")):
        bid = os.path.splitext(os.path.basename(p))[0]
        b = load(p)
        brains.append({
            "id": bid, "name": b.get("name", bid), "en": b.get("en", ""),
            "color": b.get("color", "#6b4bd8"), "glyph": b.get("glyph", bid[:1]),
            "folders": list(b.get("folders") or []),
        })
        for lp in glob.glob(os.path.join(src, "brains", bid, "lessons", "*.json")):
            l = load(lp)
            body = str(l.get("body") or "").strip()
            if not body:
                continue
            folder = str(l.get("folder") or "عمومی")
            if folder not in brains[-1]["folders"]:
                brains[-1]["folders"].append(folder)
            try:
                ts = int(float(l.get("ts") or 0))
            except ValueError:
                ts = 0
            lessons.append({
                "id": bid + "-" + os.path.splitext(os.path.basename(lp))[0],
                "brain": bid, "folder": folder,
                "title": str(l.get("title") or "درس"),
                "body": body, "source": str(l.get("source") or ""),
                "date": str(l.get("date") or ""), "ts": ts,
            })
    brains.sort(key=lambda b: ORDER.index(b["id"]) if b["id"] in ORDER else 99)
    lessons.sort(key=lambda l: (l["date"], l["ts"], l["id"]))

    prev_rev = 0
    if os.path.exists(OUT):
        try:
            prev_rev = int(load(OUT).get("rev", 0))
        except Exception:
            prev_rev = 0
    now = datetime.datetime.now(datetime.timezone.utc)
    rev = max(int(now.strftime("%Y%m%d%H%M")), prev_rev + 1)

    data = {"rev": rev, "updated": now.strftime("%Y-%m-%d"), "brains": brains, "lessons": lessons}
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    print(f"content.json: rev {rev}, {len(brains)} brains, {len(lessons)} lessons")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
