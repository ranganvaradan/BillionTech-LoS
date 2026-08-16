# WAVE 8 EXIT REPORT — Production Certification Authority

One durable certification ledger. Separate from capability / value / GACAT flags.
Target-live gate available; **legacy live authority unchanged**.

## Freeze

| Field | Value |
|-------|-------|
| SOURCE_START_SHA | `2d5f5d31eff75fa209df034c18684f5b7255e6a9` |
| SOURCE_FINAL_SHA | `3a9e56c1dbbcacc306f44501ce1999baca8828f2` |
| GITHUB_REMOTE_SHA | `e1934552e1cc57ea8fe32a026e501bee839c05f3` |

## CERTIFICATION_DOMAIN_MODEL

Exact artifact/version/scope → UNCERTIFIED / CERTIFIED / REVOKED (+ PENDING_REVIEW operational)

**Types:** CANONICAL_PARAMETER_PRODUCER, AUTHORED_CALCULATION_DEFINITION, POLICY_VERSION, SCORECARD_VERSION, SOURCE_INTEGRATION

**Scopes:** PLATFORM, TENANT, PRODUCT, PROGRAM (tenant-first, then PLATFORM; no cross-tenant leak)

## CERTIFICATION_TABLES / MIGRATION

`V142__production_certification_ledger.sql`  
Tables: `ci_production_certification`, `ci_production_certification_event` (append-only)  
Empty on install — no inferred historical certifications.

## CERTIFICATION_SERVICE

`ProductionCertificationService` (+ `InMemoryProductionCertificationLedger` for tests; JPA entity `CiProductionCertification` for durable store)  
Admin: `/api/v1/internal/credit-intelligence/production-certification`

## Axes

| Question | Field |
|----------|-------|
| EXECUTABLE? | capability |
| VALUE AVAILABLE? | valueAvailable / status |
| CERTIFIED FOR LIVE? | certificationStatus |

**CAPABILITY_AND_CERTIFICATION_SEPARATE = YES**  
**VALUE_AND_CERTIFICATION_SEPARATE = YES**  
GACAT implemented/production_ready / definition TESTED / policy ACTIVE = **NOT** certification

## TARGET_CANONICAL_LIVE_GATE

`CanonicalUnderwritingOrchestration.assembleTargetLive` + `DecisionOwnershipFlags.targetLiveCertificationGateEnabled` (default **false**)

UNCERTIFIED → `LIVE_BLOCKED` (operational, **not** credit REJECT)  
CERTIFIED closure → credit outcome from Policy+Scorecard

## Flags

LIVE_DECISION_AUTHORITY_CHANGED = **NO**  
FROZEN_RETIRED = **NO**  
LIVE_DATA_AUTO_CERTIFIED = **NO**  
CERTIFICATION_FAILURE_CAUSES_CREDIT_REJECT = **NO**

## Capability

PT 40→40 · W6 30→30 · UW 40→40

## Counts (fixture ledger; not live Client seed)

Isolated test certs only — live ledger starts empty.

## VIKASAM

Not mutated / not auto-certified. Axes independent on projection.

## Regression

Wave0–7 PASS · Wave8 PASS

## WAVE_8_EXIT_CRITERIA = PASS

STOP. Do not start Wave 9.
