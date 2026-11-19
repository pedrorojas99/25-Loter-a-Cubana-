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

OFFICIAL_URL = "https://files.floridalottery.com/exptkt/p3.pdf"
PDF_SOURCES = [
    ("direct", "https://files.floridalottery.com/exptkt/p3.pdf"),
    ("allorigins", "https://api.allorigins.win/raw?url=https%3A%2F%2Ffiles.floridalottery.com%2Fexptkt%2Fp3.pdf"),
    ("corsproxy", "https://corsproxy.io/?url=https%3A%2F%2Ffiles.floridalottery.com%2Fexptkt%2Fp3.pdf"),
    ("codetabs", "https://api.codetabs.com/v1/proxy?quest=https%3A%2F%2Ffiles.floridalottery.com%2Fexptkt%2Fp3.pdf"),
]
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

def fetch_official_pdf():
    failures = []
    session = requests.Session()
    for transport, url in PDF_SOURCES:
        try:
            response = session.get(url, timeout=(15, 35), headers={"User-Agent": "Mozilla/5.0 (compatible; 25LoteriaCubana/1.0)", "Accept": "application/pdf,*/*"})
            response.raise_for_status()
            data = response.content
            if data.startswith(b"%PDF-") and len(data) > 100_000:
                return data, transport
            failures.append(f"{transport}: non-PDF response ({len(data)} bytes, {response.headers.get('content-type')})")
        except Exception as exc:
            failures.append(f"{transport}: {type(exc).__name__}: {str(exc)[:240]}")
    raise SystemExit("Could not retrieve official Florida Lottery PDF: " + " | ".join(failures))

def fetch_ocr_records():
    reader_url = "https://r.jina.ai/http://files.floridalottery.com/exptkt/p3.pdf"
    rows = {}
    matched = 0
    session = requests.Session()
    for page_number in range(1, 10):
        response = session.get(reader_url, timeout=(15, 90), headers={
            "X-Respond-With": "jina-ocr-v1",
            "X-Page": str(page_number),
            "X-No-Cache": "true",
            "Accept": "text/plain",
        })
        response.raise_for_status()
        page_text = response.text
        if len(page_text) < 500:
            raise SystemExit(f"Jina OCR page {page_number} returned too little text: {len(page_text)} chars")
        for match in ROW.finditer(re.sub(r"\s+", " ", page_text)):
            matched += 1
            draw_day = datetime.strptime(match.group(1), "%m/%d/%y").date()
            if draw_day < date(2025, 1, 1) or draw_day > date.today():
                continue
            period = "MIDDAY" if match.group(2).upper() == "M" else "EVENING"
            key = (draw_day.isoformat(), period)
            record = {
                "draw_date": draw_day.isoformat(), "game": "PICK_3", "period": period,
                "winning_number": "".join(match.group(i) for i in (3, 4, 5)),
                "fireball": match.group(6), "source": "Florida Lottery official PICK 3 history PDF",
                "source_url": OFFICIAL_URL, "published_at": None, "verified": True,
            }
            if key in rows and rows[key] != record:
                raise SystemExit(f"Conflicting OCR records for {key}")
            rows[key] = record
    return list(rows.values()), matched

def main():
    assert ROW.search("11/11/26 E 0 - 5 - 2 FB 1 10/16/26 M 9 - 5 - 2 FB 7")
    by_key = {}
    matched = 0
    try:
        pdf_bytes, transport = fetch_official_pdf()
        with pdfplumber.open(io.BytesIO(pdf_bytes)) as pdf:
            page_count = len(pdf.pages)
            for page in pdf.pages:
                for line in visual_lines(page):
                    for match in ROW.finditer(line):
                        matched += 1
                        draw_day = datetime.strptime(match.group(1), "%m/%d/%y").date()
                        if draw_day < date(2025, 1, 1) or draw_day > date.today():
                            continue
                        period = "MIDDAY" if match.group(2).upper() == "M" else "EVENING"
                        key = (draw_day.isoformat(), period)
                        record = {
                            "draw_date": draw_day.isoformat(), "game": "PICK_3", "period": period,
                            "winning_number": "".join(match.group(i) for i in (3, 4, 5)),
                            "fireball": match.group(6), "source": "Florida Lottery official PICK 3 history PDF",
                            "source_url": OFFICIAL_URL, "published_at": None, "verified": True,
                        }
                        if key in by_key and by_key[key] != record:
                            raise SystemExit(f"Conflicting official PDF rows for {key}")
                        by_key[key] = record
    except SystemExit as pdf_error:
        print(f"Binary PDF retrieval unavailable; trying page-by-page official PDF OCR: {pdf_error}")
        ocr_records, matched = fetch_ocr_records()
        for record in ocr_records:
            key = (record["draw_date"], record["period"])
            if key in by_key and by_key[key] != record:
                raise SystemExit(f"Conflicting OCR records for {key}")
            by_key[key] = record
        transport = "Jina OCR pages 1-9"
        pdf_bytes = b""
        page_count = 9

    records = [by_key[k] for k in sorted(by_key)]
    if not records:
        raise SystemExit(f"No Pick 3 records parsed from {page_count}-page official PDF; matched={matched}")
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
    if any(not re.fullmatch(r"\d{3}", r["winning_number"]) for r in records):
        raise SystemExit("Invalid Pick 3 number detected")
    if any(not re.fullmatch(r"\d", r["fireball"]) for r in records):
        raise SystemExit("Invalid Fireball digit detected")
    anchors = {
        ("2026-11-11", "EVENING"): ("052", "1"),
        ("2026-11-11", "MIDDAY"): ("172", "8"),
        ("2025-01-01", "EVENING"): ("078", "2"),
        ("2025-01-01", "MIDDAY"): ("321", "2"),
    }
    for key, expected in anchors.items():
        actual = by_key.get(key)
        if actual is None or (actual["winning_number"], actual["fireball"]) != expected:
            raise SystemExit(f"Official PDF integrity anchor failed for {key}")

    payload = {
        "source_url": OFFICIAL_URL,
        "retrieval_transport": transport,
        "pdf_sha256": hashlib.sha256(pdf_bytes).hexdigest() if pdf_bytes else None,
        "pdf_pages": page_count,
        "rows": records,
    }
    with open("pick3_payload.json", "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, separators=(",", ":"))
    print(json.dumps({
        "retrieval_transport": transport,
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
