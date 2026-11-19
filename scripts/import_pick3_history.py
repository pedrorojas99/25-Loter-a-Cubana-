#!/usr/bin/env python3
"""Import Florida Lottery Pick 3 history from the official structured history PDF via Jina Reader."""
import hashlib
import json
import re
from collections import defaultdict
from datetime import date, datetime

import requests

OFFICIAL_URL = "https://files.floridalottery.com/exptkt/pick3_winning-numbers-history.pdf"
READER_URL = "https://r.jina.ai/http://files.floridalottery.com/exptkt/pick3_winning-numbers-history.pdf"
ROW = re.compile(
    r"(?<!\d)(\d{1,2}/\d{1,2}/\d{4})\s+"
    r"(\d)\s*-\s*(\d)\s*-\s*(\d)\s+FB\s*(\d)\s+"
    r"(\d)\s*-\s*(\d)\s*-\s*(\d)\s+FB\s*(\d)(?!\d)",
    re.IGNORECASE,
)

def main():
    response = requests.get(READER_URL, timeout=(20, 120), headers={
        "Accept": "text/plain",
        "X-Return-Format": "markdown",
        "X-No-Cache": "true",
        "User-Agent": "Mozilla/5.0 (compatible; 25LoteriaCubana/1.0)",
    })
    response.raise_for_status()
    source_text = response.text
    if len(source_text) < 10000:
        raise SystemExit(f"Official history reader returned too little text: {len(source_text)} chars")

    records_by_key = {}
    matched_dates = 0
    normalized = re.sub(r"\s+", " ", source_text.replace("\u00a0", " "))
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
        raise SystemExit(f"No rows parsed from official history PDF reader; matched_dates={matched_dates}")
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
        "retrieval_transport": "Jina Reader of official Florida Lottery PDF",
        "source_sha256": hashlib.sha256(source_text.encode("utf-8")).hexdigest(),
        "rows": records,
    }
    with open("pick3_payload.json", "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, separators=(",", ":"))

    print(json.dumps({
        "transport": "Jina Reader official PDF",
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
