# 74 — AI Review and Feedback (A1)

```mermaid
stateDiagram-v2
  [*] --> GENERATED
  GENERATED --> PENDING_REVIEW
  PENDING_REVIEW --> ACCEPTED_AS_NOTE: ACCEPT_AS_NOTE
  PENDING_REVIEW --> EDITED: EDIT
  PENDING_REVIEW --> REJECTED: REJECT
  PENDING_REVIEW --> SUPERSEDED: newer generation compared
  GENERATED --> REJECTED_GROUNDING_FAILURE: grounding fail
  note right of ACCEPTED_AS_NOTE
    Does NOT promote facts
    No ACCEPT_AS_FACT in A1
  end note
```

## Actions

| Action | Effect |
|--------|--------|
| `ACCEPT_AS_NOTE` | Status note only — **not** fact promotion |
| `EDIT` | Persist edited content; still non-authoritative |
| `REJECT` | Reject suggestion |

`ACCEPT_AS_FACT` is rejected. `FACT_CANDIDATE` is design-only (`FactCandidateDesign.ACTIVATED_IN_A1=false`).

## Feedback codes

`USEFUL` | `NOT_USEFUL` | `INCORRECT` | `UNSUPPORTED` | `EDITED_HEAVILY`

Feedback never alters policy automatically.

## Failure fallback

```mermaid
flowchart LR
  Req[AI request] -->|provider down| U[AI_ASSISTANCE_UNAVAILABLE]
  U --> Continue[Policy / Decision / Human / Sanction continue]
  U -.->|no| Demo[DEMO_AI_LOS_URL — not used in A1 flow]
```
