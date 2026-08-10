# 09 — Phase C1 Validation Report

## Scope completed

- V88/V89 schema consumed (not recreated)
- Bureau domain entities, repos, taxonomy, live classifier, Equifax parser V2 enrichment
- Normalization + metrics + HARD_LIVE_UNSECURED shadow rule
- Wire-in after bureau pull (non-fatal)
- Fact snapshot + adapter + shadow comparison metadata
- Admin APIs under `/api/v1/internal/credit-intelligence/bureau`
- Unit tests under `com.los.core.creditintelligence.**`
- Docs 06–09

## Unit test results (this session)

```text
mvn test -Dtest=com.los.core.creditintelligence.**
```

All suites green (36 tests, 0 failures):

- BureauLiveAccountClassifierTest (6)
- BureauMetricServiceTest (7)
- BureauProductTaxonomyServiceTest (4)
- EquifaxBureauAccountExtractorTest (2)
- LegacyUnderwritingContextAdapterBureauTest (2)
- Phase F foundation suites (ContentHasher, SourceRegistry, Snapshot, Adapter, Shadow, Admin, Foundation)

Also verified `BureauPullStepExecutorTest` after ingestion hook wiring.

## Phase F flags-on validation

Not executed against a live local underwrite dataset in this workspace session. Enable foundation + shadow flags and use:

```bash
curl -s -H "X-Internal-Token: local-dev-token" \
  "http://localhost:8083/api/v1/internal/credit-intelligence/applications/{id}/validation-report"
```

Unit coverage for Phase F remains green.

## Bureau validation matrix (unit / local)

| Scenario | Expected canonical outcome | Covered by |
|----------|---------------------------|------------|
| Missing tradelines / simulated | `DATA_INSUFFICIENT`, value null | BureauMetricServiceTest |
| Valid empty tradelines (`EMPTY`) | count `0`, PASS | BureauMetricServiceTest |
| Ambiguous empty without EMPTY status | `DATA_INSUFFICIENT` | BureauMetricServiceTest |
| Multiple live unsecured | count includes only live unsecured non-dup known | BureauMetricServiceTest |
| Secured / closed / unknown / duplicate excluded | excluded with reasons | BureauMetricServiceTest |
| EMI missing | obligation PARTIAL, not invented | BureauMetricServiceTest |
| DPD without payment history | `DATA_INSUFFICIENT` (aggregate flags in evidence only) | BureauMetricServiceTest |
| HARD_LIVE_UNSECURED threshold | PASS/FAIL/DATA_INSUFFICIENT | CanonicalBureauRuleEvaluator via metric tests |

Live Equifax XML end-to-end validation (real pull → ingest → shadow compare) was **not** run in this session. No production–shadow mismatch rows were recorded against real applications.

## Production unchanged

- `CreditControlService` gap default for `LIVE_UNSECURED_LOAN_COUNT` still applies when canonicalization is off / insufficient
- Authoritative underwrite path does not consume canonical metrics for decisions

## How to validate locally

1. Enable flags for a test tenant/product:

```yaml
credit-intelligence:
  canonicalization:
    bureau:
      enabled: true
      use-for-shadow-rules: true
      persist-tradelines: true
  foundation:
    enabled: true
  shadow-evaluation:
    enabled: true
    async: false
  internal-token: local-dev-token
```

2. Run a bureau pull against real Equifax XML (or parse sample via extractor tests).
3. Confirm simulated / credential-less path yields `DATA_INSUFFICIENT` for live_unsecured (not zero).
4. Inspect:

```bash
curl -s -H "X-Internal-Token: local-dev-token" \
  "http://localhost:8083/api/v1/internal/credit-intelligence/bureau/applications/{id}/metrics"

curl -s -H "X-Internal-Token: local-dev-token" \
  "http://localhost:8083/api/v1/internal/credit-intelligence/bureau/applications/{id}/legacy-vs-canonical"
```

5. Unit tests:

```bash
cd los-core-service
mvn test -Dtest=com.los.core.creditintelligence.**
```

## Recommended Phase C2

**GST / ITR canonicalization** next (or bank statements if income obligation parity is higher priority). Bureau C1 unblocks tradeline-derived live-unsecured; income/obligation still rely on legacy gap defaults and bank/GST sources.

## Known limitations

- Commercial Equifax not implemented
- Inquiry detail nodes often absent → 90d inquiry metric may be DATA_INSUFFICIENT
- Description-based product mapping can misclassify edge product names → UNKNOWN (safe exclusion)
- Live freshness uses report date as as-of when classifying during normalize
