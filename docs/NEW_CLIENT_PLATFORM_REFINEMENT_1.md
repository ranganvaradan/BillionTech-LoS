# NEW-CLIENT-PLATFORM-REFINEMENT-1

## Product story (lender)

Data & Parameters → Workflow → Policy (+ optional Scorecard) → Customer Categories / Lending Propositions → Application → Category selection → exact Policy + Workflow → Requirement Plan → Customer requirements → Acquisition → Completeness → Policy / Scorecard → post-decision Workflow

## Changes (this task)

### Client cleanliness
- Lender list filters hide Day-1 seed Categories and unbound Default Workflows (`lenderConfigVisibility.ts`)
- Linked system Workflows remain visible when bound to lender Categories
- Live UW Rules / Policy Sets remain hidden on Client (prior lock)

### Data & Parameters
- Lender subtitle + Coverage & Gaps; engineering GACAT copy under System Diagnostics
- Single readiness badge; human PT/RT/PR labels on Policy inventory

### Workflow
- Business-stage tabs; Category = route authority; highest-version fallback demoted for Category-routed apps
- Bureau requirement toggles hidden from normal Application tab
- KYC retained; raw JSON under Advanced

### Customer Categories
- Overlap → Also eligible
- “When multiple propositions match” admin (SAFE financial-data-route + display order)
- Incompatible Policy picker under Advanced/diagnostics

### Policy Studio
- Primary tabs: Scope | Rules | **Scorecard** | Test | Versions
- Create / link POLICY_WEIGHTED_V2 scorecard from Policy; factors from Policy inventory only
- Test shows Hard Rules + Scorecard + Final Policy outcome
- Scorecard handoff no longer sends lenders to legacy Live Scorecards as primary path

### Policy A scope root cause
- `PolicyLifecycleService.inferProducts` defaulted to **DIGILEAP** (and treated “BANK” as DigiLeap)
- Scope tab reads session lifecycle applicability — catalogue upsert alone did not fix header display
- Fix: infer `BUSINESS_TERM_LOAN` for STARTER/BANK/CLEAN/TERM names; never default DigiLeap
- Clean UAT script now also `lifecycle/save-draft` with BORROWER + INDIVIDUAL + ₹20k–₹500k

## Architecture preserved
GACAT, W1–W6, DP-3, Category Selection, exact version binds, Bureau certification, legacy scorecard V1 path, no new Workflow/KYC engines
