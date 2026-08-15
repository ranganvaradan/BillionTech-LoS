# Category Selection Implementation 1

**Mode:** Eligibility + disambiguation + selection only  
**Flyway:** V138  
**Start SHA:** `95f20a22c1622f15c27236b99a3128e50b74e849`  
**Does not:** Policy/Scorecard/W4/W5/W6 auto-run, activate Day-1 DRAFT Categories, live UW

## Shipped

- Application pins: Category Version, Policy Version ids, Workflow Version (via W1 fields + `CATEGORY_SELECTION` source)
- `CustomerCategoryEligibilityService` — Role/Entity/Product/Amount/effectivity; identical-dimension Categories retained
- `CategoryDisambiguationService` — SAFE catalogue questions (not a second questionnaire engine)
- `CategorySelectionService` — AUTO / progressive / explicit; workflow conflict fail-closed
- APIs under `/api/v1/applications/{id}/category-selection/**`
- Admin `PUT /api/v1/customer-categories/{id}/proposition-config`
- Staging: `allowDraftSimulation=true` evaluates DRAFT Categories without activating them
- UI: `CategorySelectionPanel` + API client
- Handoff DTO `SelectedApplicationConfiguration` for later W4 (not invoked here)

## Goldens covered in `CategorySelectionGoldensTest`
