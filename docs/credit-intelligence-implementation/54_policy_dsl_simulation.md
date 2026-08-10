# 54 — Policy DSL Simulation (P0.1)

```mermaid
flowchart TD
  DRAFT[Draft rules + tests] --> SIM[DraftPolicySimulator]
  SIM --> DSL[PolicyDslInterpreterV1]
  DSL --> FIX[CASE_A–E + BRE fixture maps]
  FIX --> RUN[CiPolicySimulationRun]
  RUN --> LABEL[VALIDATION_FIXTURE_SIMULATION]
```

- Label: **VALIDATION_FIXTURE_SIMULATION**
- Not portfolio impact
- Persists `CiPolicySimulationRun` with DSL version + evaluation semantics
- Optional `EvaluationContext` fields / clock for inquiry-month cases
