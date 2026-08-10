# 85 — Cutover Mismatch Review (G0.1)

```mermaid
flowchart TD
  Cmp[ci_cutover_comparison] --> Class{comparison_class}
  Class -->|EXACT / NON_MATERIAL| Ok[No review required]
  Class -->|MATERIAL_*| Rev[Human review]
  Class -->|CANONICAL_MORE_PERMISSIVE| Rev
  Class -->|BUG disposition| Block[Blocks certification]
  Rev -->|EXPECTED_CANONICAL / CANONICAL_CORRECT| Pass[Explained]
  Rev -->|PENDING / NEEDS_INVESTIGATION| Block
```

## Thresholds (validation)

- unexplained material mismatch = **0**
- BUG-classified mismatch = **0**
- unexplained canonical-more-permissive = **0**
- replay mismatch = **0**
- critical binding failure = **0**

Canonical stricter after silent-default removal may be intentional when reviewed.
