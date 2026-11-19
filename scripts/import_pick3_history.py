#!/usr/bin/env python3
"""Parse the official Florida Lottery Pick 3 PDF through Jina's HTML table view."""
import hashlib
import json
import re
from collections import defaultdict
from datetime import date, datetime

import requests
from bs4 import BeautifulSoup

OFFICIAL_URL = "https://files.floridalottery.com/exptkt/p3.pdf"
READER_URL = "https://r.jina.ai/http://files.floridalottery.com/exptkt/p3.pdf"
ROW = re.compile(
    r"(?<!\d)(\d{1,2}/\d{1,2}/\d{2})\s+([ME])\s+"
    r"(\d)\s*-\s*(\d)\s*-\s*(\d)\s+FB\s+(\d)(?!\d)",
    re.IGNORECASE,
)

def main():
    # Grammar smoke test: the same row shape used in official PDF table cells.
    assert ROW.search("11/11/26 E 0 - 5 - 2 FB 1 10/16/26 M 9 - 5 - 2 FB 7")

    response = requests.get(
        READER_URL,
        timeout=(30, 180),
        headers={
            "X-Respond-With": "html",
            "X-No-Cache": "true",
            "Accept": "text/html",
            "User-Agent": "25-Loteria-Cubana history importer/1.0",
        },
    )
    response.raise_for_status()
    document = response.text
    if len(document) < 10_000:
        raise SystemExit(f"Official PDF HTML extraction too short: {len(document)} chars; body={document[:500]!r}")

    soup = BeautifulSoup(document, "html.parser")
    table_rows = soup.find_all("tr")
    if len(table_rows) < 500:
        raise SystemExit(
            f"Reader did not preserve PDF table rows (found {len(table_rows)} tr, "
            f"{len(document)} chars); refusing to parse flattened text"
        )

    by_key = {}
    matched_rows = 0
    for row_index, table_row in enumerate(table_rows, start=1):
        cells = table_row.find_all(["td", "th"])
        row_text = " ".join(cell.get_text(" ", strip=True) for cell in cells)
        for match in ROW.finditer(re.sub(r"\s+", " ", row_text)):
            matched_rows += 1
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
                raise SystemExit(f"Conflicting official PDF rows for {key} at HTML table row {row_index}")
            by_key[key] = record

    records = [by_key[k] for k in sorted(by_key)]
    if not records:
        raise SystemExit(f"No Pick 3 rows parsed from {len(table_rows)} HTML table rows")
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
    if dates[-1] < "2026-01-01":
        raise SystemExit(f"Latest date unexpectedly old: {dates[-1]}")
    if any(not re.fullmatch(r"\d{3}", r["winning_number"]) for r in records):
        raise SystemExit("Invalid Pick 3 number detected")
    if any(not re.fullmatch(r"\d", r["fireball"]) for r in records):
        raise SystemExit("Invalid Fireball digit detected")

    payload = {
        "source_url": OFFICIAL_URL,
        "reader_url": READER_URL,
        "reader_content_sha256": hashlib.sha256(document.encode("utf-8")).hexdigest(),
        "reader_table_rows": len(table_rows),
        "rows": records,
    }
    with open("pick3_payload.json", "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, separators=(",", ":"))
    print(json.dumps({
        "reader_table_rows": len(table_rows),
        "matched_pdf_rows": matched_rows,
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
