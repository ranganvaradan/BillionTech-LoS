"""Scan Flyway SQL for workflow_configs INSERT rows (uuid, name, borrower_type, loan_product)."""
import re
import sys
from pathlib import Path


def extract_rows(migration_dir: Path) -> set[tuple[str, str, str, str]]:
    # Matches: ('uuid', 'name', 'BORROWER', 'loan_product', ... steps start ' or '[
    pat = re.compile(
        r"\('([a-f0-9-]{36})'\s*,\s*'([^']*)'\s*,\s*'([^']+)'\s*,\s*'([^']*)'\s*,\s*(?:'(?:\\.|[^'])*'|\[)",
        re.IGNORECASE,
    )
    rows: set[tuple[str, str, str, str]] = set()
    for f in sorted(migration_dir.glob("V*.sql")):
        text = f.read_text(encoding="utf-8", errors="replace")
        for m in pat.finditer(text):
            wf_id, name, btype, loan_prod = m.group(1), m.group(2), m.group(3), m.group(4)
            rows.add((btype.strip(), loan_prod.strip(), name.strip(), wf_id))
    return rows


def main() -> None:
    root = Path(__file__).resolve().parents[1] / "services" / "los-core-service" / "src" / "main" / "resources" / "db" / "migration"
    if len(sys.argv) > 1:
        root = Path(sys.argv[1])
    rows = extract_rows(root)
    print("workflow_seed_rows", len(rows))
    for b, lp, name, _ in sorted(rows, key=lambda r: (r[0], r[1])):
        print(f"{b}\t{lp}\t{name}")


if __name__ == "__main__":
    main()
