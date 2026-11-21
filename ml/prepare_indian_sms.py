#!/usr/bin/env python3
"""Import and conservatively triage the MIT-licensed Indian spam/ham corpus.

The output is a review queue, NOT a ready-to-train five-class dataset.
"""
from __future__ import annotations
import argparse, csv, re, urllib.request
from pathlib import Path

URL = "https://raw.githubusercontent.com/junioralive/india-spam-sms-classification/main/dataset/spam_ham_india.csv"
PROMO = re.compile(r"\\b(offer|discount|sale|free|win|winner|congratulations|claim|buy now|limited time|cashback|coupon|promo|subscribe|upgrade|apply now|hurry|shop|deal|% off|save rs|save ₹|recharge offer|exclusive offer|flat \\d+|enjoy unlimited|plans starting|valid for \\d+ days)\\b", re.I)
SCAM = re.compile(r"\\b(kyc|aadhaar|pan card|blocked|suspend|verify now|click here|urgent|otp|pin|password|account will|won a prize|lottery|pay.*fee|refund.*link|update.*kyc|credentials of unknown|high returns)\\b", re.I)
PERSONAL = re.compile(r"\\b(hi|hello|hey|how are you|thank you|thanks|please|will you|can you|i am|i'm|we are|we'll|tomorrow|today|come|call me|reached|on my way|sorry|happy birthday|good morning|good night|i will|i have|not sure|let me|see you)\\b", re.I)
EXCLUDE_PERSONAL = re.compile(r"\\b(upi|imps|neft|rtgs|debited|credited|transaction|account balance|ticket booked|appointment confirmed|delivery|shipment)\\b", re.I)
def scrub(text: str) -> str:
    text = re.sub(r"\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b", "[EMAIL]", text, flags=re.I)
    text = re.sub(r"https?://\\S+|www\\.\\S+|\\b[a-z0-9.-]+\\.(?:in|com|co|net|org|ly|io)/\\S*", "[LINK]", text, flags=re.I)
    text = re.sub(r"\\b(?:\\+?91[- ]?)?[6-9]\\d{9}\\b", "[PHONE]", text)
    text = re.sub(r"\\b\\d{10,18}\\b", "[ID]", text)
    text = re.sub(r"\\b(?:XX|x{2,})\\d{2,6}\\b", "[ACCOUNT]", text, flags=re.I)
    return re.sub(r"\\s+", " ", text).strip()
def main():
    p=argparse.ArgumentParser()
    p.add_argument("--input", type=Path)
    p.add_argument("--output", type=Path, default=Path("ml/data/india_spam_ham_candidates.csv"))
    a=p.parse_args()
    raw=a.input.read_bytes() if a.input else urllib.request.urlopen(URL, timeout=30).read()
    rows=csv.DictReader(raw.decode("utf-8-sig", errors="replace").splitlines())
    seen=set(); counts={}
    a.output.parent.mkdir(parents=True, exist_ok=True)
    fields=["text","label","source","source_label","label_method","review_status"]
    with a.output.open("w", newline="", encoding="utf-8") as f:
        w=csv.DictWriter(f, fieldnames=fields); w.writeheader()
        for row in rows:
            text=(row.get("Msg") or "").strip(); original=(row.get("Label") or "").strip().lower()
            if not text or original not in {"spam","ham"}: continue
            if original=="spam" and not SCAM.search(text) and PROMO.search(text):
                label,method="PROMOTIONS_CANDIDATE","spam_label_plus_promotion_cues"
            elif original=="ham" and PERSONAL.search(text) and not EXCLUDE_PERSONAL.search(text):
                label,method="PERSONAL_CANDIDATE","ham_label_plus_conversational_cues"
            else: continue
            clean=scrub(text); key=re.sub(r"[^a-z0-9]+"," ",clean.lower()).strip()
            if not clean or key in seen: continue
            seen.add(key); counts[label]=counts.get(label,0)+1
            w.writerow(dict(text=clean,label=label,source="india_spam_ham_mit",source_label=original,label_method=method,review_status="requires_manual_review"))
    print({"candidate_counts":counts,"unique_candidates":len(seen),"ready_for_training":False})
if __name__=="__main__": main()
