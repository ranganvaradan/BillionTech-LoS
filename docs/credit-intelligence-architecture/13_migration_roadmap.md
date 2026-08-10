# 13 — Migration Roadmap

Codebase is **pre-production development baseline** — optimize for target architecture, not old prod compatibility. Still use **shadow** to avoid big-bang risk.

## Phases

### Phase F — Foundation

**Scope:** Source registry (minimal), fact snapshots, policy versions, standard result schema, shadow evaluation  
**Deps:** None  
**Risks:** Perf on underwrite; snapshot size  
**Strategy:** Feature flag; dual-write; production decisions unchanged  
**Accept:** Replay shadow = stable; prod regression green  
**Rollback:** Flag off  

### Phase C — Canonicalization

**Scope:** Canonical paths, provider adapters → Source Engine, metric registry, DATA_INSUFFICIENT, evidence links, tradeline facts for bureau metrics  
**Deps:** F  
**Risks:** Adapter bugs; SCF policy breakage  
**Strategy:** Migrate one source family at a time (bureau → GST → bank)  
**Accept:** No silent gap defaults on shadow; LIVE_UNSECURED from tradelines  
**Rollback:** Adapter feature flags  

### Phase P — Policy modernization

**Scope:** Rule DSL, declarative orchestration, lifecycle, simulation, back-test  
**Deps:** C  
**Risks:** DSL expressiveness gaps  
**Strategy:** Compile existing hardRules/scorecards into DSL via adapter  
**Accept:** Package freeze; sim API; stage order config-driven  
**Rollback:** Keep Java engines behind façade  

### Phase D — Decision modernization

**Scope:** Recommendation engine, pricing, limit structuring, deviations, authority matrix  
**Deps:** P  
**Risks:** Role matrix politics  
**Strategy:** Encode current CAM/sanction roles first  
**Accept:** Single recommendation object; authority enforced  
**Rollback:** Façade to old approve APIs  

### Phase I — Intelligence

**Scope:** AI assistive loop, anomaly tasks, narratives, fact candidates  
**Deps:** F (min), C preferred  
**Risks:** PII leakage; over-trust AI  
**Strategy:** Replace ingest deep-link; no AI decision authority  
**Accept:** Accept/reject audited; underwrite works offline from AI  
**Rollback:** Disable AI requests  

### Phase M — Monitoring

**Scope:** Post-disbursement signals, EWS, portfolio review  
**Deps:** C, P  
**Risks:** Alert fatigue  
**Strategy:** Start with AA + GST + LMS events  
**Accept:** Signals → LOS tasks; human actions  
**Rollback:** Disable schedulers  

## Eliminate

Silent gap defaults on policy path · demo AI URL as credit UX · parallel CreditRulesEngine as authority · provider-coupled rule keys · no-exp JWT  

## Preserve during migration

`underwriting_evaluations` history · application workflow statuses · CAM/sanction · existing admin UIs (wrap publish)
