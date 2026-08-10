# 03 — Policy Versioning (Phase F)

## Identifier

- Package policy identifier: `UNDERWRITE_LEGACY_V1`
- Orchestration: `LEGACY_UNDERWRITE_APPLICATION_V1`
- Merge precedence: `HARD_RULE > SCORECARD > RULES > LEGACY`

## Freeze content

```json
{
  "orchestrationVersion": "LEGACY_UNDERWRITE_APPLICATION_V1",
  "mergePrecedence": "HARD_RULE > SCORECARD > RULES > LEGACY",
  "productCode": "...",
  "borrowerType": "...",
  "ruleSets": [ { "rulesJson": ..., "filters": ... } ],
  "scorecards": [ { "scorecardJson": ..., "thresholdsJson": ..., "hardRulesJson": ... } ]
}
```

Loaded from active `UnderwritingRuleSet` and `UnderwritingScorecard` rows matching borrower type + loan product (priority desc).

## Publishing flow

```mermaid
flowchart TD
  A[Load active rules + scorecards] --> B[Build policy_content map]
  B --> C[ContentHasher.hashMap]
  C --> D{Find package by tenant+product+identifier}
  D -->|missing| E[Create ACTIVE package]
  D -->|exists| F[Lookup PUBLISHED/ACTIVE version by contentHash]
  E --> F
  F -->|same hash| G[Reuse version]
  F -->|new hash| H[Create version N+1 PUBLISHED]
  H --> I[Audit policy.version.published]
```

## Immutability

Published/active versions are never edited in place. Material policy change → new version with new content hash.

## Linkage

Shadow `CiCreditEvaluation.policyVersionId` points at the frozen version used for that run.
