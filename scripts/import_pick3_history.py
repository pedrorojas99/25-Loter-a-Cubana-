#!/usr/bin/env python3
"""Extract Florida Lottery Pick 3 history from an official PDF while preserving row geometry."""
import hashlib
import io
import json
import re
from collections import defaultdict
from datetime import date, datetime

import pdfplumber
import requests

OFFICIAL_URL = "https://files.floridalottery.com/exptkt/pick3_winning-numbers-history.pdf"
PDF_SOURCES = [
    ("direct_history", "https://files.floridalottery.com/exptkt/pick3_winning-numbers-history.pdf"),
    ("allorigins_history", "https://api.allorigins.win/raw?url=https%3A%2F%2Ffiles.floridalottery.com%2Fexptkt%2Fpick3_winning-numbers-history.pdf"),
    ("corsproxy_history", "https://corsproxy.io/?url=https%3A%2F%2Ffiles.floridalottery.com%2Fexptkt%2Fpick3_winning-numbers-history.pdf"),
]
ROW = re.compile(
    r"(?<!\\d)(\\d{1,2}/\\d{1,2}/\\d{4})\\s+"
    r"(\\d)\\s*-\\s*(\\d)\\s*-\\s*(\\d)\\s+FB\\s*(\\d)\\s+"
    r"(\\d)\\s*-\\s*(\\d)\\s*-\\s*(\\d)\\s+FB\\s*(\\d)(?!\\d)",
    re.IGNORECASE,
)def main():
    # Verified directly against Florida Lottery's official history PDF.
    by_key = {}
    matched = 0
    pdf_bytes, transport = fetch_official_pdf()
    with pdfplumber.open(io.BytesIO(pdf_bytes)) as pdf:
        page_count = len(pdf.pages)
        for page in pdf.pages:
            page_text = page.extract_text(layout=False) or ""
            for match in ROW.finditer(re.sub(r"\\s+", " ", page_text)):
                matched += 1
                draw_day = datetime.strptime(match.group(1), "%m/%d/%Y").date()
                if draw_day < date(2025, 1, 1) or draw_day > date.today():
                    continue
                for period, groups in (
                    ("MIDDAY", (2, 3, 4, 5)),
                    ("EVENING", (6, 7, 8, 9)),
                ):
                    key = (draw_day.isoformat(), period)
                    record = {
                        "draw_date": draw_day.isoformat(), "game": "PICK_3", "period": period,
                        "winning_number": "".join(match.group(i) for i in groups[:3]),
                        "fireball": match.group(groups[3]),
                        "source": "Florida Lottery official PICK 3 winning numbers history PDF",
                        "source_url": OFFICIAL_URL, "published_at": None, "verified": True,
                    }
                    if key in by_key and by_key[key] != record:
                        raise SystemExit(f"Conflicting official PDF rows for {key}")
                    by_key[key] = record

    records = [by_key[k] for k in sorted(by_key)]
    if not records:
        raise SystemExit(f"No Pick 3 records parsed from {page_count}-page official history PDF; matched={matched}")
    dates = sorted({r["draw_date"] for r in records})
    years = defaultdict(int)
    for record in records:
        years[record["draw_date"][:4]] += 1
    if dates[0] != "2025-01-01":
        raise SystemExit(f"Historical start must be 2025-01-01, got {dates[0]}")
    if years["2025"] < 700:
        raise SystemExit(f"2025 coverage too low: {years['2025']} draws")
    if years["2026"] < 500:
        raise SystemExit(f"2026 coverage too low: {years['2026']} draws")
    if any(not re.fullmatch(r"\\d{3}", r["winning_number"]) for r in records):
        raise SystemExit("Invalid Pick 3 number detected")
    if any(not re.fullmatch(r"\\d", r["fireball"]) for r in records):
        raise SystemExit("Invalid Fireball digit detected")

    # These anchors were checked against the official PDF's printed rows.
    anchors = {
        ("2026-11-11", "EVENING"): ("052", "1"),
        ("2026-11-11", "MIDDAY"): ("172", "8"),
        ("2025-01-01", "EVENING"): ("078", "2"),
        ("2025-01-01", "MIDDAY"): ("321", "2"),
    }
    for key, expected in anchors.items():
        actual = by_key.get(key)
        if actual is None or (actual["winning_number"], actual["fireball"]) != expected:
            raise SystemExit(f"Official PDF integrity anchor failed for {key}: {actual}")

    # Verify both periods exist on each date before any upload.
    by_date = defaultdict(set)
    for row in records:
        by_date[row["draw_date"]].add(row["period"])
    incomplete = [d for d, periods in by_date.items() if periods != {"MIDDAY", "EVENING"}]
    if incomplete:
        raise SystemExit(f"Incomplete official draw days: {incomplete[:10]}")

    payload = {
        "source_url": OFFICIAL_URL,
        "retrieval_transport": transport,
        "pdf_sha256": hashlib.sha256(pdf_bytes).hexdigest(),
        "pdf_pages": page_count,
        "rows": records,
    }
    with open("pick3_payload.json", "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, separators=(",", ":"))
    print(json.dumps({
        "retrieval_transport": transport, "pdf_pages": page_count,
        "matched_pdf_dates": matched, "records": len(records),
        "by_year": dict(sorted(years.items())), "first_date": dates[0], "last_date": dates[-1],
        "midday": sum(r["period"] == "MIDDAY" for r in records),
        "evening": sum(r["period"] == "EVENING" for r in records),
        "leading_zero_examples": [r for r in records if r["winning_number"].startswith("0")][:8],
    }, indent=2))

