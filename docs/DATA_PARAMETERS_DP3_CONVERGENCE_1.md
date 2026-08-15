# DATA-PARAMETERS-DP3 — Policy / GACAT / Scorecard Convergence

**Mode:** IMPLEMENT DP-3 ONLY (not DP-4)  
**Source start SHA:** `8d646920fc75cdd128b5093079fc93225d03c312`  
**Flyway:** V133 (after V132)

---

## Architecture contract (implemented)

```
Customer Category (config only)
  → Policy Version
    → Policy Rule Graph (durable, PolicyDsl AST)
      → GACAT canonical parameters
        → optional Scorecard Version (POLICY_WEIGHTED_V2 for new Policy-linked)
          → legacy engines remain transitional runtime
```

- Policy is lender-facing underwriting **authoring** authority (not live UW yet).
- GACAT is sole canonical parameter catalogue.
- Scorecard is subordinate to Policy (factor universe ⊆ Policy parameters).
- `underwriting_rule_sets` / legacy scorecard keys remain transitional.

---

## Part A/B — Policy rule graph + lossless migration

**Authoritative store:** `ci_policy_rule_graph` + `ci_policy_rule_graph_node` + `ci_policy_rule_graph_operand`

Materializer: `PolicyRuleGraphMaterializer`  
Source: `ci_policy_studio_session_snapshot.payload.ruleCandidates` (preserved)

- Exact GACAT ID match only (`CanonicalParameterRegistry.findById`)
- Unresolved operands → `UNRESOLVED_CANONICAL_PARAMETER` + original token retained
- Graph materialization alone does **not** mark Policy ready
- Snapshots unchanged

## Part C/D — Single parameter authority + inventory

- Operand identity = GACAT canonical ID when resolved
- Inventory API: `GET .../dp3/policies/{id}/parameter-inventory`
- Derived from persisted graph (usage type, readiness via DP-1 projection)

## Part E–K — Scorecard

- Policy → optional Scorecard: `ci_policy_document.scorecard_id`
- Modes: `LEGACY_POINTS_V1` (default, bit-identical live path) | `POLICY_WEIGHTED_V2`
- Engine: `PolicyWeightedScorecardEngine` (relative weights; required missing ≠ renormalize; NOT_APPLICABLE may leave denominator)
- Live UW (`ScorecardPolicyEngine`) **always** evaluates via LEGACY_POINTS_V1 formula path (V2 live deferred)
- Factor picker: Policy parameters only (`.../scorecard-factor-picker`)
- No mass legacy→GACAT conversion

## Part L — Live UW

- `PolicyToLegacyUwCompiler` = design/compile-plan only; no live activation

## Part M/N — Policy Test + immutability

- `PolicyGraphPolicyTestService` evaluates persisted graph AST
- Unresolved → fail closed
- `rule_graph_immutable` / graph.immutable for APPROVED-class versions; edit ⇒ new version (process)

## Part O — Customer Category

- Unchanged (config bind only; no live routing; no `selectedCustomerCategoryId`)

## Part P — Readiness

- Inventory/factor picker reuse `GacatParameterReadinessProjection`
- DP-4 Production Ready enforcement **not** implemented

## APIs

| Method | Path |
|---|---|
| POST | `/api/v1/internal/credit-intelligence/dp3/policies/{id}/materialize-graph` |
| POST | `/api/v1/internal/credit-intelligence/dp3/materialize-all-graphs` |
| GET | `/api/v1/internal/credit-intelligence/dp3/policies/{id}/parameter-inventory` |
| GET | `/api/v1/internal/credit-intelligence/dp3/policies/{id}/rule-graph` |
| POST | `/api/v1/internal/credit-intelligence/dp3/policies/{id}/policy-test` |
| GET | `/api/v1/internal/credit-intelligence/dp3/policies/{id}/scorecard-factor-picker` |
| POST | `/api/v1/internal/credit-intelligence/dp3/scorecards/weight-preview` |
| POST | `/api/v1/internal/credit-intelligence/dp3/policies/{id}/link-scorecard/{scorecardId}` |

## UI

- Policy Studio: `PolicyParameterInventoryPanel` (GACAT inventory + readiness)
- Scorecard legacy UI retained; V2 weight preview via DP-3 API

## Goldens

`Dp3ConvergenceGoldensTest` — 12 deterministic cases (roundtrip, OR compound, subset, weights, missing/NA, legacy mode, unresolved).

## Explicit non-goals (still blocked)

- DP-4 fail-closed activation
- Policy as live UW authority
- Customer Category live routing / `selectedCustomerCategoryId`
- Retire `underwriting_rule_sets` / Policy Set tables
- Silent unresolved token mapping / mass scorecard conversion
