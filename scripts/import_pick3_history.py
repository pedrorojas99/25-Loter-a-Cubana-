#!/usr/bin/env python3
"""Import verified Florida Lottery Pick 3 history from its official history PDF."""
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
    ("direct", OFFICIAL_URL),
    ("allorigins", "https://api.allorigins.win/raw?url=https%3A%2F%2Ffiles.floridalottery.com%2Fexptkt%2Fpick3_winning-numbers-history.pdf"),
    ("corsproxy", "https://corsproxy.io/?url=https%3A%2F%2Ffiles.floridalottery.com%2Fexptkt%2Fpick3_winning-numbers-history.pdf"),
]
ROW = re.compile(
    r"(?<!\d)(\d{1,2}/\d{1,2}/\d{4})\s+"
    r"(\d)\s*-\s*(\d)\s*-\s*(\d)\s+FB\s*(\d)\s+"
    r"(\d)\s*-\s*(\d)\s*-\s*(\d)\s+FB\s*(\d)(?!\d)",
    re.IGNORECASE,
)

def fetch_pdf():
    errors = []
    session = requests.Session()
    for name, url in PDF_SOURCES:
        try:
            response = session.get(url, timeout=(15, 45), headers={
                "User-Agent": "Mozilla/5.0 (compatible; 25LoteriaCubana/1.0)",
                "Accept": "application/pdf,*/*",
            })
            response.raise_for_status()
            data = response.content
            if data.startswith(b"%PDF-") and len(data) > 100_000:
                return data, name
            errors.append(f"{name}: invalid PDF ({len(data)} bytes)")
        except Exception as exc:
            errors.append(f"{name}: {type(exc).__name__}: {str(exc)[:180]}")
    raise SystemExit("Could not download official Florida Lottery history PDF: " + " | ".join(errors))

def main():
    pdf_bytes, transport = fetch_pdf()
    records_by_key = {}
    matched_dates = 0
    with pdfplumber.open(io.BytesIO(pdf_bytes)) as pdf:
        page_count = len(pdf.pages)
        for page in pdf.pages:
            page_text = page.extract_text(layout=False) or ""
            normalized = re.sub(r"\s+", " ", page_text)
            for match in ROW.finditer(normalized):
                matched_dates += 1
                draw_date = datetime.strptime(match.group(1), "%m/%d/%Y").date()
                if draw_date < date(2025, 1, 1) or draw_date > date.today():
                    continue
                for period, digit_groups, fireball_group in (
                    ("MIDDAY", (2, 3, 4), 5),
                    ("EVENING", (6, 7, 8), 9),
                ):
                    row = {
                        "draw_date": draw_date.isoformat(),
                        "game": "PICK_3",
                        "period": period,
                        "winning_number": "".join(match.group(i) for i in digit_groups),
                        "fireball": match.group(fireball_group),
                        "source": "Florida Lottery official PICK 3 winning numbers history PDF",
                        "source_url": OFFICIAL_URL,
                        "published_at": None,
                        "verified": True,
                    }
                    key = (row["draw_date"], period)
                    if key in records_by_key and records_by_key[key] != row:
                        raise SystemExit(f"Conflicting official PDF rows for {key}")
                    records_by_key[key] = row

    records = [records_by_key[k] for k in sorted(records_by_key)]
    if not records:
        raise SystemExit(f"No rows parsed from official PDF: pages={page_count}, matched_dates={matched_dates}")
    dates = sorted({r["draw_date"] for r in records})
    counts_by_year = defaultdict(int)
    periods_by_date = defaultdict(set)
    for row in records:
        counts_by_year[row["draw_date"][:4]] += 1
        periods_by_date[row["draw_date"]].add(row["period"])

    if dates[0] != "2025-01-01":
        raise SystemExit(f"Expected first date 2025-01-01; got {dates[0]}")
    if counts_by_year["2025"] < 700:
        raise SystemExit(f"2025 coverage too low: {counts_by_year['2025']}")
    if counts_by_year["2026"] < 500:
        raise SystemExit(f"2026 coverage too low: {counts_by_year['2026']}")
    if any(periods != {"MIDDAY", "EVENING"} for periods in periods_by_date.values()):
        raise SystemExit("At least one date is missing Midday or Evening")
    if any(not re.fullmatch(r"\d{3}", row["winning_number"]) for row in records):
        raise SystemExit("Invalid three-digit Pick 3 number")
    if any(not re.fullmatch(r"\d", row["fireball"]) for row in records):
        raise SystemExit("Invalid Fireball digit")

    anchors = {
        ("2026-11-11", "MIDDAY"): ("172", "8"),
        ("2026-11-11", "EVENING"): ("052", "1"),
        ("2025-01-01", "MIDDAY"): ("321", "2"),
        ("2025-01-01", "EVENING"): ("078", "2"),
    }
    for key, expected in anchors.items():
        actual = records_by_key.get(key)
        if actual is None or (actual["winning_number"], actual["fireball"]) != expected:
            raise SystemExit(f"Official PDF anchor failed for {key}: {actual}")

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
        "transport": transport,
        "pdf_pages": page_count,
        "matched_date_rows": matched_dates,
        "records": len(records),
        "by_year": dict(sorted(counts_by_year.items())),
        "first_date": dates[0],
        "last_date": dates[-1],
        "midday": sum(r["period"] == "MIDDAY" for r in records),
        "evening": sum(r["period"] == "EVENING" for r in records),
        "leading_zero_examples": [r for r in records if r["winning_number"].startswith("0")][:8],
    }, indent=2))

if __name__ == "__main__":
    main()
