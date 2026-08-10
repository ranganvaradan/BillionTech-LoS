# 02 — Credit Intelligence Workspace

**Route:** `/credit-intelligence/applications` → `/credit-intelligence/workspace/:caseCode`  
**API:** `GET /api/v1/internal/credit-intelligence/staging-demo/cases/{caseCode}/workspace`

Always shows banner: **VALIDATION FIXTURE — NOT REAL BORROWER DATA**

## Sections rendered

1. Application summary (fixture case title / origin)  
2. Data coverage  
3. Evidence strength  
4. Bureau  
5. Banking  
6. GST  
7. ITR / Tax  
8. Turnover triangulation  
9. Obligations  
10. Material reconciliations  
11. Data-quality gaps  
12. Policy result (shadow)  
13. Score / grade  
14. Recommended facility  
15. Conditions  
16. Approval authority  
17. Open investigation questions  
18. AI Underwriter (non-authoritative)

Plus:

- **Decision explanation** — requested amount, candidate limits, binding constraint, recommended amount  
- **Replay frozen evaluation** — original hash / replay hash / MATCH  

Technical UUIDs are behind disclosure. Frontend does not recompute credit logic — it renders API aggregates.
