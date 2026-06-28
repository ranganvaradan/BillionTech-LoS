"""Parse Encore-Loan_products.txt (multiple JSON objects, variable dash separators)."""
import json
import sys
from collections import Counter
from pathlib import Path


def load_all_products(text: str) -> list[dict]:
    dec = json.JSONDecoder()
    idx = 0
    all_results: list[dict] = []
    while idx < len(text):
        i = text.find("{", idx)
        if i == -1:
            break
        try:
            obj, end = dec.raw_decode(text, i)
        except json.JSONDecodeError:
            idx = i + 1
            continue
        if isinstance(obj, dict) and "results" in obj and isinstance(obj["results"], list):
            all_results.extend(obj["results"])
        idx = end
    return all_results


def main(path: str) -> None:
    text = Path(path).read_text(encoding="utf-8", errors="replace")
    all_results = load_all_products(text)
    print("total_products", len(all_results))
    bc = Counter((r.get("branchSetCode") or "") for r in all_results)
    print("branch_sets_top", bc.most_common(15))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else r"C:\Users\User\Downloads\Encore-Loan_products.txt")
