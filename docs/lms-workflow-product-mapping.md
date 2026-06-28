# LMS workflow → Encore product mapping (Phase 1)

**V32 note:** After [`V32__standardize_borrower_type_product_catalog.sql`](../services/los-core-service/src/main/resources/db/migration/V32__standardize_borrower_type_product_catalog.sql), `loan_product` values are **catalog codes** (for example `BUSINESS_TERM_LOAN`, `LOAN_AGAINST_PROPERTY`, `BUSINESS_WC_OD`). The Flyway seed in `V45__workflow_lms_product_mapping.sql` keys the mapping table on those codes, not pre-V32 display strings.

## Sources

| Source | Path |
|--------|------|
| Encore loan products export | `C:\Users\User\Downloads\Encore-Loan_products.txt` (multiple JSON documents; **374** products after merge) |
| LOS workflow seeds | Flyway under [`services/los-core-service/src/main/resources/db/migration/`](../services/los-core-service/src/main/resources/db/migration/) — canonical **borrower_type + loan_product** pairs |
| Parser utility | [`tools/parse_encore_products.py`](../tools/parse_encore_products.py) |

## Encore product catalogue (summary)

- **Total products parsed:** 374  
- **Top `branchSetCode` values:** MGPP (113), FGSB (24), All (20), WIBMO (19), MESP (17), …  
- **Keyword spot-checks** (description / display text, case-insensitive):  
  - **personal** → 4 hits (e.g. `Indiviual_10106` — vendor typo “Indiviual”)  
  - **working** → includes `Working Capital Loan_10106` (MGPP)  
  - **bullet** → 7 hits (e.g. `AVB_10106`)  
  - No clear **LAP** / **gold** / **invoice** string matches in descriptions — those workflows need **business-led** Encore picks or new Encore products.

## LOS workflows extracted from Flyway (regex scan)

Twenty **canonical** `(borrower_type, loan_product)` combinations appear in standard `INSERT ... workflow_configs` seeds (V2/V7 style):

| borrower_type | loan_product |
|----------------|---------------|
| INDIVIDUAL | TERM_LOAN, PERSONAL_LOAN, BUSINESS_LOAN, WORKING_CAPITAL, LAP |
| PROPRIETOR | TERM_LOAN, PERSONAL_LOAN, BUSINESS_LOAN, WORKING_CAPITAL, LAP |
| PARTNERSHIP | TERM_LOAN, PERSONAL_LOAN, BUSINESS_LOAN, WORKING_CAPITAL, LAP |
| COMPANY | TERM_LOAN, PERSONAL_LOAN, BUSINESS_LOAN, WORKING_CAPITAL, LAP |

**Additional** workflows exist in [`V31__seed_secured_individual_workflows.sql`](../services/los-core-service/src/main/resources/db/migration/V31__seed_secured_individual_workflows.sql) (e.g. `Loan Against Property`, `Loan Against Shares`, `LAS`, `Gold Loan`) — same KYC shape, different `loan_product` **labels**. The regex extractor did not capture `INSERT ... SELECT` patterns; treat them as **first-class** rows when seeding `workflow_lms_product_mapping`.

## Recommended mapping table (heuristic — **requires business sign-off**)

Confidence scale: **0.0–1.0** (heuristic); **MANUAL** after ops review.

| borrower_type | loan_product | Suggested Encore `productCode` | Encore `productType` | Branch (hint) | Confidence | Rationale |
|---------------|--------------|------------------------------|----------------------|----------------|------------|-----------|
| * | PERSONAL_LOAN | `Indiviual_10106` | Loans | MGPP | 0.65 | “Product Personal” in vendor display |
| * | WORKING_CAPITAL | `Working Capital Loan_10106` | Loans | MGPP | 0.70 | Explicit working-capital product |
| * | TERM_LOAN | `EE_10106` | Loans | MGPP | 0.55 | Generic MGPP “Prod : MGPP” — default when no better match |
| * | BUSINESS_LOAN | `EE_10106` | Loans | MGPP | 0.50 | No strong keyword; reuse MGPP baseline |
| * | LAP | `EE_10106` | Loans | MGPP | **0.35** | **Weak** — no “LAP” string in sample export; **needs manual product** |
| INDIVIDUAL | Loan Against Property | `EE_10106` | Loans | MGPP | **0.35** | Placeholder — align with secured LAP policy |
| INDIVIDUAL | Loan Against Shares | `EE_10106` | Loans | MGPP | **0.30** | Placeholder |
| INDIVIDUAL | LAS | `EE_10106` | Loans | MGPP | **0.30** | Placeholder |
| INDIVIDUAL | Gold Loan | `EE_10106` | Loans | MGPP | **0.25** | No “gold” hit in export — likely **missing Encore product** |

**Rule:** `PARTNERSHIP` / `COMPANY` rows use the **same** suggested codes as `INDIVIDUAL` for the same `loan_product` unless credit policy mandates different GL products.

## Workflows without a strong LMS match

1. **LAP** (all borrower types) — no unambiguous Encore description match in the export.  
2. **Gold Loan**, **LAS**, **Loan Against Shares** — placeholders only.  
3. **Invoice / dealer OD** style flows — not present in current LOS seeds; if added later, map using `branchSetCode` + `productDescription` (e.g. OD) and re-run [`tools/parse_encore_products.py`](../tools/parse_encore_products.py) keyword analysis.

## Suggested missing / follow-up Encore products

- Dedicated **LAP / mortgage** loan product with correct tenure bands.  
- Dedicated **gold** collateral product if business offers true gold loans.  
- **Invoice financing** product if parity with legacy dealer limits is required.

## Persistence (implemented in Phase 4)

Table **`workflow_lms_product_mapping`** (Flyway `V45`):

- Natural key **`(borrower_type, loan_product)`** aligned with `loan_applications` / `WorkflowEngineServiceImpl#getActiveWorkflow`.  
- Stores `encore_product_code`, optional `branch_set_code`, `mapping_source`, `numeric(3,2)` confidence, `notes`.  
- Keeps **`workflow_configs.steps` JSON untouched** — no workflow engine redesign.

## Next actions

1. Business / credit committee: confirm or replace **HEURISTIC** `productCode` values.  
2. Run SQL seed updates (`UPDATE workflow_lms_product_mapping …`) for high-risk rows (LAP, gold).  
3. Toggle **`los.lms.workflow-mapping.enabled`** if rollback to legacy “`loanProduct` string = Encore code” behaviour is needed.
