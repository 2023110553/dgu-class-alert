"""Generate the bundled DGU course-code dictionary from the official catalog PDF."""

from __future__ import annotations

import argparse
import json
import re
from collections import defaultdict
from pathlib import Path

from pypdf import PdfReader


ROW = re.compile(
    r"^(?P<code>[A-Z]{2,}[A-Z0-9]*\d{4})\s+"
    r"(?P<name>.+?)\s+"
    r"\d+(?:\.\d+)?\s+\d+(?:\.\d+)?\s+\d+(?:\.\d+)?(?:\s|$)"
)
BAD_NAME = re.compile(r"^(학수번호|교과목명|전공|구분)$")


def normalize_name(value: str) -> str:
    return re.sub(r"\s+", " ", value).strip(" -\t")


def extract(pdf: Path) -> tuple[dict[str, str], dict[str, list[str]]]:
    candidates: dict[str, set[str]] = defaultdict(set)
    reader = PdfReader(str(pdf))
    for page in reader.pages:
        for raw_line in (page.extract_text() or "").splitlines():
            line = re.sub(r"\s+", " ", raw_line).strip()
            match = ROW.match(line)
            if not match:
                continue
            code = match.group("code")
            name = normalize_name(match.group("name"))
            if name and len(name) <= 100 and not BAD_NAME.match(name):
                candidates[code].add(name)

    resolved: dict[str, str] = {}
    conflicts: dict[str, list[str]] = {}
    for code, names in sorted(candidates.items()):
        ordered = sorted(names, key=lambda name: (len(name), name))
        if len(ordered) > 1:
            conflicts[code] = ordered
        # Spacing differences are equivalent; genuinely different names are
        # ambiguous and must fall back to displaying the course code.
        semantic_names = {"".join(name.split()) for name in ordered}
        if len(semantic_names) == 1:
            resolved[code] = ordered[0]
    return resolved, conflicts


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("pdf", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--conflicts", type=Path)
    args = parser.parse_args()

    courses, conflicts = extract(args.pdf)
    if len(courses) < 500:
        raise SystemExit(f"Extraction produced too few courses: {len(courses)}")
    payload = {
        "year": 2026,
        "source": "https://www.dongguk.edu/page/137",
        "courses": courses,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    if args.conflicts:
        args.conflicts.write_text(json.dumps(conflicts, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"courses": len(courses), "conflicts": len(conflicts)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
