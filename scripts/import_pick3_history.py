#!/usr/bin/env python3
"""Extract Florida Lottery Pick 3 history from the official PDF by visual row."""
import hashlib
import io
import json
import re
import sys
from collections import defaultdict
from datetime import date, datetime

import pdfplumber
import requests

OFFICIAL_URL = "https://files.floridalottery.com/exptkt/p3.pdf"
ROW = re.compile(
    r"(?<!\d)(\d{1,2}/\d{1,2}/\d{2})\s+([ME])\s+"
    r"(\d)\s*-\s*(\d)\s*-\s*(\d)\s+FB\s+(\d)(?!\d)",
    re.IGNORECASE,
)

def visual_lines(page):
    words = page.extract_words(x_tolerance=2, y_tolerance=2, keep_blank_chars=False)
    words.sort(key=lambda w: (float(w["top"]), float(w["x0"])))
    groups = []
    for word in words:
        top = float(word["top"])
        if not groups or abs(top - groups[-1]["top"]) > 2.2:
            groups.append({"top": top, "words": [word]})
        else:
            groups[-1]["words"].append(word)
            groups[-1]["top"] = sum(float(w["top"]) for w in groups[-1]["words"]) / len(groups[-1]["words"])
    for group in groups:
        yield " ".join(w["text"] for w in sorted(group["words"], key=lambda w: float(w["x0"])))

def main():
    response = requests.get(
        OFFICIAL_URL,
        timeout=(30, 180),
        headers={"User-Agent": "25-Loteria-Cubana historical results importer/1.0", "Accept": "application/pdf"},
    )
    response.raise_for_status()
    pdf_bytes = response.content
    if not pdf_bytes.startswith(b"%PDF-") or len(pdf_bytes) < 100_000:
        raise SystemExit(f"Official endpoint did not return a valid PDF (bytes={len(pdf_bytes)}, type={response.headers.get('content-type')})")

    by_key = {}
    matched = 0
    page_count = 0
    with pdfplumber.open(io.BytesIO(pdf_bytes)) as pdf:
        page_count = len(pdf.pages)
        for page_number, page in enumerate(pdf.pages, start=1):
            for line in visual_lines(page):
                for match in ROW.finditer(line):
                    matched += 1
                    draw_day = datetime.strptime(match.group(1), "%m/%d/%y").date()
                    if draw_day < date(2025, 1, 1) or draw_day > date.today():
                        continue
                    period = "MIDDAY" if match.group(2).upper() == "M" else "EVENING"
                    key = (draw_day.isoformat(), period)
                    record = {
                        "draw_date": draw_day.isoformat(),
                        "game": "PICK_3",
                        "period": period,
                        "winning_number": "".join(match.group(i) for i in (3, 4, 5)),
                        "fireball": match.group(6),
                        "source": "Florida Lottery official PICK 3 history PDF",
                        "source_url": OFFICIAL_URL,
                        "published_at": None,
                        "verified": True,
                    }
                    previous = by_key.get(key)
                    if previous is not None and previous != record:
                        raise SystemExit(f"Conflicting duplicate result: {key}")
                    by_key[key] = record

    records = [by_key[k] for k in sorted(by_key)]
    if not records:
        raise SystemExit(f"No records parsed from {page_count}-page official PDF; refusing import")
    dates = sorted({r["draw_date"] for r in records})
    years = defaultdict(int)
    for row in records:
        years[row["draw_date"][:4]] += 1
    if dates[0] != "2025-01-01":
        raise SystemExit(f"Historical start must be 2025-01-01, got {dates[0]}")
    if years["2025"] < 700:
        raise SystemExit(f"2025 coverage too low: {years['2025']} draws")
    if years["2026"] < 500:
        raise SystemExit(f"2026 coverage too low: {years['2026']} draws")
    if dates[-1] < "2026-01-01":
        raise SystemExit(f"Latest date unexpectedly old: {dates[-1]}")
    if any(not re.fullmatch(r"\d{3}", r["winning_number"]) for r in records):
        raise SystemExit("Invalid Pick 3 number detected")
    if any(not re.fullmatch(r"\d", r["fireball"]) for r in records):
        raise SystemExit("Invalid Fireball digit detected")
    if len(records) != len(by_key):
        raise SystemExit("Duplicate-key validation failed")

    payload = {
        "source_url": OFFICIAL_URL,
        "pdf_sha256": hashlib.sha256(pdf_bytes).hexdigest(),
        "pdf_bytes": len(pdf_bytes),
        "pdf_pages": page_count,
        "rows": records,
    }
    with open("pick3_payload.json", "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, separators=(",", ":"))
    print(json.dumps({
        "pdf_bytes": len(pdf_bytes),
        "pdf_pages": page_count,
        "matched_pdf_rows": matched,
        "records": len(records),
        "by_year": dict(sorted(years.items())),
        "first_date": dates[0],
        "last_date": dates[-1],
        "midday": sum(r["period"] == "MIDDAY" for r in records),
        "evening": sum(r["period"] == "EVENING" for r in records),
        "leading_zero_examples": [r for r in records if r["winning_number"].startswith("0")][:8],
    }, indent=2))

if __name__ == "__main__":
    main()
